package com.unicornwhodev.visiondatasetstudio

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.FileObserver
import android.os.Process
import android.system.Os
import android.system.OsConstants
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** A real process kill between physical media deletions. The verified synthetic backup
 * is retained outside the purge directory. SAF availability is qualified separately. */
class PurgeProcessDeathTest {
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val case get()=requireNotNull(InstrumentationRegistry.getArguments().getString("faultCase")).also {require(it.matches(Regex("[a-f0-9]{12}")))}
    private val root get()=File(target.filesDir,"qa-evidence/purge-death/$case")
    private val context get()=object:ContextWrapper(target) {
        override fun getFilesDir()=File(root,"studio/files").apply{mkdirs()}
        override fun getCacheDir()=File(root,"studio/cache").apply{mkdirs()}
    }
    private fun open()=Room.databaseBuilder(target,AppDatabase::class.java,"qa-purge-$case.db").build()
    private fun proof(name:String,value:JSONObject)=File(root,"$name.json").writeText(value.toString(2))

    @Test fun prepareAndPurgeUntilHostKillsStoppedProcess()=runBlocking {
        check(!root.exists());root.mkdirs()
        val db=open();val storage=StorageManager(context);val hf=HfApiClient{null};val runtime=LiteRtEngine()
        try {
            val project=ProjectEntity(id=730001,name="Synthetic purge interruption",classesCsv="synthetic",activeTasksCsv="CLASSIFICATION")
            db.projectDao().saveProject(project);db.batchDao().insertOrReplace(BatchEntity(project.id,1,"IN_PROGRESS",totalCases=256))
            val exporters=DatasetExporters(storage,hf);val engine=BatchEngine(db,storage,hf,runtime,exporters)
            repeat(256) { index ->
                val id="purge-$case-${index.toString().padStart(4,'0')}";val image=storage.getImageFile(id,"png")
                Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(0xff000000.toInt() or index)}.let { bitmap ->
                    image.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
                }
                val sample=SampleEntity(id,project.id,1,id,index.toLong(),sourceFileUrl=null,localImagePath=image.path,
                    imageWidth=8,imageHeight=8,sha256=HashUtils.computeSha256(image),acquisitionStatus="AVAILABLE",
                    annotationStatus=if(index<2) "IN_PROGRESS" else "REJECTED",syncStatus="NOT_EXPORTED")
                db.sampleDao().insertSamples(listOf(sample))
                if(index<2) {
                    assertNull(ImageIdentity.accept(db,sample,ImageIdentity.pixelSha256(image)))
                    engine.saveSampleAnnotations(id,SampleAnnotations(tags=listOf(TagTarget("human-$index","synthetic",isHumanVerified=true))))
                    engine.validateSample(id,1)
                }
            }
            val accepted=db.sampleDao().getSamplesForBatchSync(project.id,1).filter{it.annotationStatus=="VALIDATED"}
            val result=exporters.packageBatchToLocalZip(project,1,accepted.map{it to engine.getSampleAnnotations(it.sampleId)},false,true,false,false,false)
            assertTrue(result.error,result.success)
            val backup=result.zipFile!!.copyTo(File(root,"verified-backup.zip"))
            engine.recordLocalArchive(project.id,1,result.zipFile!!)
            engine.verifyLocalArchive(project.id,1,Uri.fromFile(backup).toString())
            val annotations=JSONObject()
            accepted.forEach{annotations.put(it.sampleId,db.annotationDao().getAnnotationSync(it.sampleId)!!.dataJson)}
            proof("expected",JSONObject().put("annotations",annotations).put("backup_sha256",HashUtils.computeSha256(backup)).put("total",256))
            val directory=storage.getImageFile("probe").parentFile!!
            val stopped=AtomicBoolean(false)
            @Suppress("DEPRECATION")
            val observer=object:FileObserver(directory.path,DELETE) {
                override fun onEvent(event:Int,path:String?) {
                    if(event and DELETE!=0 && stopped.compareAndSet(false,true)) {
                        proof("purge-cut",JSONObject().put("first_deleted",path).put("remaining_images",directory.listFiles()!!.count{it.extension=="png"}))
                        Os.kill(Process.myPid(),OsConstants.SIGSTOP)
                    }
                }
            }
            observer.startWatching()
            try {engine.purgeReviewedBatch(project.id,1,true);fail("Host must kill the process while purge is incomplete")}
            finally {observer.stopWatching()}
        } finally {runtime.close();db.close()}
    }

    @Test fun resumePurgeRetainsHumanAnnotationsAndBackupReceipt()=runBlocking {
        val expected=JSONObject(File(root,"expected.json").readText());val cut=JSONObject(File(root,"purge-cut.json").readText())
        assertTrue(cut.getInt("remaining_images") in 1 until expected.getInt("total"))
        val db=open();val storage=StorageManager(context);val hf=HfApiClient{null};val runtime=LiteRtEngine()
        try {
            assertEquals("PURGING",db.batchDao().getBatchSync(730001,1)!!.status)
            BatchEngine(db,storage,hf,runtime,DatasetExporters(storage,hf)).purgeReviewedBatch(730001,1,true)
            val batch=db.batchDao().getBatchSync(730001,1)!!
            assertEquals("PURGED",batch.status);assertNotNull(batch.verifiedArchiveUri);assertNotNull(batch.verifiedArchiveSha256)
            val rows=db.sampleDao().getSamplesForBatchSync(730001,1)
            assertEquals(256,rows.size);assertTrue(rows.all{it.localImagePath==null && it.syncStatus=="PURGED"})
            val annotations=expected.getJSONObject("annotations")
            annotations.keys().forEach { id ->assertEquals(annotations.getString(id),db.annotationDao().getAnnotationSync(id)!!.dataJson) }
            assertEquals(expected.getString("backup_sha256"),HashUtils.computeSha256(File(root,"verified-backup.zip")))
            proof("resumed",JSONObject().put("real_process_death",true).put("all_256_media_purged",true)
                .put("two_human_annotations_retained",true).put("verified_backup_and_receipt_retained",true))
        } finally {runtime.close();db.close()}
    }
}
