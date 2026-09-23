package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Separate two-process test on a dedicated installation. Its synthetic data is retained. */
class InstalledDataPreservationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun marker():File {
        val id=requireNotNull(InstrumentationRegistry.getArguments().getString("preservationCase"))
        require(id.matches(Regex("[a-f0-9]{12}")))
        return File(context.filesDir,"qa-evidence/preservation/$id.json")
    }

    @Test fun preparePersistentHumanAnnotation()=runBlocking {
        val marker=marker();check(!marker.exists()) { "Never replace an existing preservation case" }
        val db=AppDatabase.getInstance(context);val storage=StorageManager(context)
        val id=System.currentTimeMillis();val sampleId="preservation-$id"
        check(db.projectDao().getProjectSync(id)==null)
        val project=ProjectEntity(id=id,name="QA retained human annotation",lastRowCursor=3,activeTasksCsv="CLASSIFICATION",classesCsv="synthetic")
        db.projectDao().saveProject(project)
        db.batchDao().insertOrReplace(BatchEntity(id,1,"IN_PROGRESS",totalCases=1))
        val image=storage.getImageFile(sampleId,"png")
        Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.MAGENTA)}.let{bitmap->
            image.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
        db.sampleDao().insertSamples(listOf(SampleEntity(sampleId,id,1,sampleId,0,sourceFileUrl=null,
            localImagePath=image.path,imageWidth=16,imageHeight=16,sha256=HashUtils.computeSha256(image),
            acquisitionStatus="AVAILABLE",annotationStatus="IN_PROGRESS",syncStatus="NOT_EXPORTED")))
        val hf=HfApiClient{null};val runtime=LiteRtEngine()
        try {
            val engine=BatchEngine(db,storage,hf,runtime,DatasetExporters(storage,hf))
            engine.saveSampleAnnotations(sampleId,SampleAnnotations(tags=listOf(TagTarget("retained-human","synthetic",isHumanVerified=true))))
            val record=db.annotationDao().getAnnotationSync(sampleId)!!
            val expected=JSONObject().put("project_id",id).put("sample_id",sampleId).put("cursor",3)
                .put("annotation_json",record.dataJson).put("image_sha256",HashUtils.computeSha256(image))
            marker.parentFile!!.mkdirs();marker.writeText(expected.toString(2))
        } finally {runtime.close()}
    }

    @Test fun verifyAfterProcessDeathAndPackageReplacement()=runBlocking {
        val expected=JSONObject(marker().readText());val db=AppDatabase.getInstance(context)
        val id=expected.getLong("project_id");val sampleId=expected.getString("sample_id")
        assertEquals(expected.getLong("cursor"),db.projectDao().getProjectSync(id)!!.lastRowCursor)
        assertEquals(expected.getString("annotation_json"),db.annotationDao().getAnnotationSync(sampleId)!!.dataJson)
        val sample=db.sampleDao().getSampleSync(sampleId)!!
        assertEquals("IN_PROGRESS",sample.annotationStatus)
        assertEquals("NOT_EXPORTED",sample.syncStatus)
        assertEquals(expected.getString("image_sha256"),HashUtils.computeSha256(File(sample.localImagePath!!)))
    }
}
