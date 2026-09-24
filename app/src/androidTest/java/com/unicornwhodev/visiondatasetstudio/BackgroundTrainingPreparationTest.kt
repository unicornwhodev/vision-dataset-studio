package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.StudioPreferenceStore
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit preparation only. The subsequent Release run must occur without instrumentation. */
class BackgroundTrainingPreparationTest {
    @Test fun prepareSyntheticExportForLongReleaseTraining()=runBlocking {
        check(InstrumentationRegistry.getArguments().getString("prepareLongTraining")=="true")
        val imageCount=InstrumentationRegistry.getArguments().getString("trainingImages")?.toInt() ?: 96
        require(imageCount in 96..256)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixture=File(context.filesDir,"long-training-fixture")
        val source=File(fixture,"trainable-vision-fixture.tflite");check(source.isFile)
        val configJson=File(fixture,"model_config.json").readText()
        val config=StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(configJson)!!
        val id=System.currentTimeMillis()
        val model=File(context.filesDir,"models/original-background-$id/model.tflite").apply{parentFile!!.mkdirs()}
        source.copyTo(model)
        val project=ProjectEntity(id=id,name="QA background $id",classesCsv="rouge,bleu",activeTasksCsv="CLASSIFICATION",modelPath=model.path,modelConfigJson=configJson,diskBudgetMb=8192)
        val db=AppDatabase.getInstance(context);db.projectDao().saveProject(project)
        db.batchDao().insertOrReplace(BatchEntity(id,1,"IN_PROGRESS",totalCases=imageCount))
        val storage=StorageManager(context)
        repeat(imageCount) { n ->
            val image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply {
                eraseColor(if(n%2==0)Color.rgb(160+n%90,15,25)else Color.rgb(15,25,160+n%90))
                setPixel(0,0,Color.rgb(n,80,90))
            }
            val name="background-$id-$n";val file=storage.getImageFile(name,"png")
            try{file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
            db.sampleDao().insertSamples(listOf(SampleEntity(name,id,1,name,n.toLong(),sourceFileUrl=null,localImagePath=file.path,imageWidth=32,imageHeight=32,sha256=HashUtils.computeSha256(file),acquisitionStatus="AVAILABLE",annotationStatus="VALIDATED",syncStatus="NOT_EXPORTED")))
            db.annotationDao().insertOrReplace(AnnotationRecord(name,StudioJson.moshi.adapter(SampleAnnotations::class.java).toJson(SampleAnnotations(tags=listOf(TagTarget("tag-$n",if(n%2==0)"rouge" else "bleu",isHumanVerified=true))))))
        }
        val hf=HfApiClient{null};val exporter=DatasetExporters(storage,hf)
        val engine=BatchEngine(db,storage,hf,LiteRtEngine(),exporter)
        val rows=db.sampleDao().getSamplesForBatchSync(id,1)
        val archive=exporter.packageBatchToLocalZip(project,1,rows.map{it to engine.getSampleAnnotations(it.sampleId)},false,true,false,false,false)
        assertTrue(archive.error,archive.success);engine.recordLocalArchive(id,1,archive.zipFile!!)
        val documentId=id.toString().map{'a'+it.digitToInt()}.joinToString("")
        val uri=android.net.Uri.parse("content://${InstrumentationRegistry.getInstrumentation().context.packageName}.documents/background-$documentId")
        SafArchives.copyVerified(context.contentResolver,archive.zipFile!!,uri);engine.verifyLocalArchive(id,1,uri.toString())
        val store=OnDeviceTraining(context)
        val run=store.prepare(project,1,epochs=30,learningRate=.00001f)
        store.cancel(id);assertEquals("cancelled",store.read(id)!!.phase)
        val image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
        val elapsed=try{LiteRtTrainingSession(model,config).use { session ->
            val start=android.os.SystemClock.elapsedRealtime();session.train(image,floatArrayOf(1f,0f),.00001f)
            android.os.SystemClock.elapsedRealtime()-start
        }}finally{image.recycle()}
        StudioPreferenceStore(context).apply{activeProjectId=id;lastBatch=1}
        val evidence=mapOf("project_id" to id,"run_id" to run.id,"total_steps" to run.totalSteps,"one_signature_ms" to elapsed,
            "original_model" to model.path,"original_sha256" to HashUtils.computeSha256(model),"images" to imageCount,
            "background_test_executed" to false,"fixture" to "synthetic; bounded real optimizer loop")
        File(fixture,"preparation.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(evidence))
        println("Long training preparation: $evidence")
    }
}
