package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TrainingWorkflowTest {
    @Test fun validatedCorrectionsTrainThroughWorkManagerAndResume()= runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixture=File(context.filesDir,"training-fixture")
        val model=File(fixture,"trainable-vision-fixture.tflite")
        assumeTrue("Stage the untrained fixture",model.isFile)
        val json=File(fixture,"model_config.json").readText()
        val projectId=System.currentTimeMillis()
        val storage=com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager(context)
        val project=ProjectEntity(id=projectId,name="QA apprentissage Android",classesCsv="rouge,bleu",activeTasksCsv="CLASSIFICATION",modelPath=model.path,modelConfigJson=json,diskBudgetMb=8192)
        val db=AppDatabase.getInstance(context);db.projectDao().saveProject(project)
        db.batchDao().insertOrReplace(BatchEntity(projectId=projectId,batchNumber=1,status="IN_PROGRESS",totalCases=96))
        repeat(96) { n ->
            val image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            image.eraseColor(if(n%2==0)Color.rgb(160+n,15,25)else Color.rgb(15,25,160+n))
            val file=storage.getImageFile("train-qa-$projectId-$n","png");file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            val id="train-qa-$projectId-$n"
            val annotations=SampleAnnotations(tags=listOf(TagTarget("tag-$n",if(n%2==0)"rouge" else "bleu",isHumanVerified=true)))
            db.sampleDao().insertSamples(listOf(SampleEntity(sampleId=id,projectId=projectId,batchNumber=1,assetId=id,sourceRowIndex=n.toLong(),sourceFileUrl=null,localImagePath=file.path,imageWidth=32,imageHeight=32,sha256=HashUtils.computeSha256(file),acquisitionStatus="AVAILABLE",annotationStatus=if(n<88)"VALIDATED" else "REJECTED",syncStatus="NOT_EXPORTED")))
            db.annotationDao().insertOrReplace(AnnotationRecord(id,StudioJson.moshi.adapter(SampleAnnotations::class.java).toJson(annotations)))
        }
        val store=OnDeviceTraining(context)
        var refused=false
        try {store.prepare(project,1,epochs=3,learningRate=.05f)}catch(e:IllegalArgumentException){refused=e.message?.contains("Exportez")==true}
        assertTrue("A corrected but unexported batch cannot train",refused)
        val hf=com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient{null}
        val exporter=com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters(storage,hf)
        val engine=com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine(db,storage,hf,LiteRtEngine(),exporter)
        val rows=db.sampleDao().getSamplesForBatchSync(projectId,1)
        rows.forEach{row->com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity.accept(db,row,com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity.pixelSha256(File(row.localImagePath!!)))}
        val accepted=rows.filter{it.annotationStatus=="VALIDATED"}
        val pairs=accepted.map{it to engine.getSampleAnnotations(it.sampleId)}
        val archive=exporter.packageBatchToLocalZip(project,1,pairs,false,true,false,false,false)
        assertTrue(archive.error,archive.success)
        engine.recordLocalArchive(projectId,1,archive.zipFile!!)
        val uri=android.net.Uri.parse("content://${InstrumentationRegistry.getInstrumentation().context.packageName}.documents/training-archive")
        com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives.copyVerified(context.contentResolver,archive.zipFile!!,uri)
        engine.verifyLocalArchive(projectId,1,uri.toString())
        // An accepted image from another batch must not leak into this run.
        val otherFile=storage.getImageFile("other-batch-$projectId","png")
        val otherBitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)}
        try{otherFile.outputStream().use{otherBitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{otherBitmap.recycle()}
        val other=accepted.first().copy(sampleId="other-batch-$projectId",batchNumber=2,localImagePath=otherFile.path,sha256=HashUtils.computeSha256(otherFile))
        db.sampleDao().insertNewSamples(listOf(other));db.annotationDao().insertOrReplace(AnnotationRecord(other.sampleId,db.annotationDao().getAnnotationSync(accepted.first().sampleId)!!.dataJson))
        val run=store.prepare(project,1,epochs=3,learningRate=.05f)
        assertEquals(run.id,store.readBatch(projectId,1)!!.id)
        assertEquals(1,run.sourceBatchNumber);assertTrue(run.exportProof.isNotBlank())
        var cleanupRefused=false
        try {store.requireCleanupAllowed(project,1,true)}catch(_:IllegalStateException){cleanupRefused=true}
        assertTrue("Cleanup waits for optional learning to finish",cleanupRefused)
        assertEquals(88,run.samples.size)
        assertTrue(run.samples.count{it.validation}>=8)
        assertTrue(run.samples.count{!it.validation}>=32)
        assertTrue(run.samples.filter{it.validation}.map{it.sha256}.intersect(run.samples.filterNot{it.validation}.map{it.sha256}.toSet()).isEmpty())
        val work=WorkManager.getInstance(context)
        store.enqueue(projectId)
        suspend fun terminal():DeviceTrainingRun=withTimeout(180000) {
            while(true){val state=store.read(projectId)!!;if(state.phase in setOf("completed","rejected","failed","cancelled"))return@withTimeout state;delay(200)}
            error("unreachable")
        }
        withTimeout(30000){while(store.read(projectId)!!.completedSteps<8){val state=store.read(projectId)!!;check(state.phase!="failed"){state.error.orEmpty()};delay(100)}}
        store.cancel(projectId)
        val stopped=store.read(projectId)!!
        assertEquals("cancelled",stopped.phase)
        assertNotNull(stopped.checkpoint)
        assertTrue(stopped.completedSteps in 8 until run.totalSteps)
        store.resume(projectId)
        val done=terminal()
        assertEquals(done.error,"completed",done.phase)
        assertEquals(run.totalSteps,done.completedSteps)
        assertTrue(done.validationLoss!!<done.initialLoss!!*.99)
        assertNotEquals(done.initialWeightProbe,done.finalWeightProbe)
        assertEquals("Training never silently activates a model",model.path,db.projectDao().getProjectSync(projectId)!!.modelPath)
        store.requireCleanupAllowed(project,1,true)
        val config=done.config.copy(trainingCheckpoint=OnDeviceTraining.saveCheckpointReceipt(File(done.modelFile),done.checkpoint!!))
        val image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
        try{LiteRtEngine().use{engine->assertTrue(engine.loadModel(File(done.modelFile)));assertEquals("rouge",engine.runInference(image,config).orThrow().maxBy{it.score}.label)}}finally{image.recycle()}
        withTimeout(10000){while(work.getWorkInfosForUniqueWork("vds-training-$projectId").get().any{!it.state.isFinished})delay(100)}
        assertEquals(96,engine.purgeReviewedBatch(projectId,1,true))
        assertTrue("Training images are cleaned only after the run completes",done.samples.all{!File(it.image).exists()})
        assertTrue("The unrelated batch survives cleanup",otherFile.isFile)
        assertTrue("Learned checkpoint survives cleanup",File(done.checkpoint!!.prefix).parentFile!!.listFiles()!!.any{it.isFile})
        File(fixture,"android-workflow-evidence.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(mapOf("project_id" to projectId,"validated_images" to run.samples.size,"excluded_rejected" to 8,"exported_batch_only" to true,"cleanup_waited" to true,"cleanup_after_training" to true,"steps_before_cancel" to stopped.completedSteps,"steps_after_resume" to done.completedSteps,"initial_loss" to done.initialLoss,"final_loss" to done.validationLoss,"internal_weights_changed" to (done.initialWeightProbe!=done.finalWeightProbe),"source_model_preserved" to true,"runtime" to "Android WorkManager / LiteRT CPU")))
    }
}
