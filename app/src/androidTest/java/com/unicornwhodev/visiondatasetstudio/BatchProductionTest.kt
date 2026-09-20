package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.storage.*
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.batch.*
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class BatchProductionTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext=InstrumentationRegistry.getInstrumentation().context
    private fun uri(key:String)=Uri.parse("content://${testContext.packageName}.documents/$key")
    private fun writeImage(key:String,color:Int) {
        val bitmap=Bitmap.createBitmap(37,29,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color)
        try { context.contentResolver.openOutputStream(uri(key))!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        finally {bitmap.recycle()}
    }
    @Test fun uniqueTwoPlusOneSurvivesExportPurgeReopenAndRenamedCopies()=runBlocking {
        val name="production-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        var db=open()
        val storage=StorageManager(context);val hf=HfApiClient{null};val inference=LiteRtEngine()
        val exporters=DatasetExporters(storage,hf)
        fun engine()=BatchEngine(db,storage,hf,inference,exporters)
        assertFalse(ProcessingSettings().continuousTraining)
        val id=System.currentTimeMillis()
        val project=ProjectEntity(id=id,name="QA production lots",diskBudgetMb=8192,classesCsv="objet",activeTasksCsv="CAPTIONING",
            settingsJson=ProjectSettings.write(ProcessingSettings(batchSize=2,sourceMode="LOCAL_INDEX",sourceIndexReady=true)))
        val files=mutableListOf<File>()
        try {
            writeImage("cycle-a",Color.RED);writeImage("cycle-b",Color.GREEN);writeImage("cycle-c",Color.BLUE)
            // Same decoded pixels in a different file: valid PNG with a different trailing payload.
            val a=context.contentResolver.openInputStream(uri("cycle-a"))!!.use{it.readBytes()}
            context.contentResolver.openOutputStream(uri("cycle-renamed"))!!.use{it.write(a);it.write("different-container".toByteArray())}
            writeImage("cycle-byte-copy",Color.GREEN)
            db.projectDao().saveProject(project)
            val keys=listOf("cycle-a","cycle-b","cycle-renamed","cycle-byte-copy","cycle-c")
            db.sourceEntryDao().insert(keys.mapIndexed { n,key->SourceEntryEntity(id,n.toLong(),key,uri(key).toString()) })
            assertFalse(engine().prepareUniqueBatch(id,1,2){_,_->})
            val first=db.sampleDao().getSamplesForBatchSync(id,1)
            assertEquals(2,first.size);assertTrue(first.all{it.acquisitionStatus=="AVAILABLE"})
            first.forEach { s->
                files+=File(s.localImagePath!!)
                engine().saveSampleAnnotations(s.sampleId,SampleAnnotations())
                engine().validateSample(s.sampleId,1)
            }
            val accepted=db.sampleDao().getSamplesForBatchSync(id,1)
            engine().requireUniqueExport(accepted)
            val zip=exporters.packageBatchToLocalZip(project,1,accepted.map{it to SampleAnnotations()},false,true,false,false,false)
            assertTrue(zip.error,zip.success);assertEquals(2,zip.sampleCount)
            files+=zip.zipFile!!
            engine().recordLocalArchive(id,1,zip.zipFile!!)
            // Actual provider write and readback, followed by the engine's second independent hash check.
            SafArchives.copyVerified(context.contentResolver,zip.zipFile!!,uri("cycle-archive"))
            engine().verifyLocalArchive(id,1,uri("cycle-archive").toString())
            assertEquals(2,engine().purgeReviewedBatch(id,1,true))
            assertTrue(first.all{!File(it.localImagePath!!).exists()})
            val firstHash=first.first().sha256!!
            db.close();db=open()
            assertEquals(first.first().sampleId,db.imageIdentityDao().owner(id,"file_sha256",firstHash)!!.firstSampleId)
            assertTrue(engine().prepareUniqueBatch(id,2,2){_,_->}) // two copies skipped, one unique remains at EOF
            val second=db.sampleDao().getSamplesForBatchSync(id,2)
            val unique=second.filter{it.acquisitionStatus=="AVAILABLE"}
            assertEquals(1,unique.size);assertEquals("cycle-c",unique.single().assetId)
            assertEquals(2,second.count{it.annotationStatus=="DUPLICATE"})
            assertEquals(1,db.batchDao().getBatchSync(id,2)!!.totalCases)
            assertEquals(5L,db.projectDao().getProjectSync(id)!!.lastRowCursor)
            assertTrue(second.filter{it.annotationStatus=="DUPLICATE"}.all{it.localImagePath==null})
            val copied=second.single{it.assetId=="cycle-renamed"}
            assertNotEquals(firstHash,copied.sha256) // byte hash alone could not detect this duplicate
            assertEquals("PURGED",db.sampleDao().getSampleSync(first.first().sampleId)!!.syncStatus)
            assertNotNull(db.annotationDao().getAnnotationSync(first.first().sampleId))
            // A stale project argument must use the persisted cursor and cannot move old samples into a new batch.
            val stale=engine().discoverViewerBatch(project,3,2)
            assertTrue(stale.success);assertTrue(stale.endOfSource)
            assertEquals(2,db.sampleDao().getSamplesForBatchSync(id,1).size)
            unique.forEach { s->files+=File(s.localImagePath!!);engine().validateSample(s.sampleId,2) }
            val last=db.sampleDao().getSamplesForBatchSync(id,2).filter{it.annotationStatus=="VALIDATED"}
            engine().requireUniqueExport(last)
            val zip2=exporters.packageBatchToLocalZip(project,2,last.map{it to SampleAnnotations()},false,true,false,false,false)
            assertTrue(zip2.error,zip2.success);files+=zip2.zipFile!!
            ZipFile(zip2.zipFile!!).use { archive->assertEquals(1,archive.entries().asSequence().count{it.name.contains("/images/") && !it.isDirectory}) }
            File(context.filesDir,"batch-production-evidence.json").writeText("""{"unique_batches":[2,1],"duplicates_skipped":2,"purge_reopen":true,"stale_cursor_preserved":true,"weights_required":false}""")
        } finally {
            inference.close();files.forEach{it.delete()};storage.batchExportDir(id,1).deleteRecursively();storage.batchExportDir(id,2).deleteRecursively()
            db.close();context.deleteDatabase(name)
        }
    }

    @Test fun concurrentClaimsHaveOneOwnerAndDoNotMergeDifferentImages()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            fun sample(id:String,hash:String)=SampleEntity(id,7,1,id,0,null,null,null,sha256=hash,acquisitionStatus="AVAILABLE",annotationStatus="PENDING",syncStatus="NOT_EXPORTED")
            val a=sample("a","same");val b=sample("b","same");val c=sample("c","distinct")
            db.sampleDao().insertNewSamples(listOf(a,b,c))
            val results=listOf(a,b,c).map { s->async(Dispatchers.IO){ImageIdentity.accept(db,s,if(s.sampleId=="c")"pixels-c" else "pixels-ab")} }.awaitAll()
            assertEquals(1,results.count{it!=null})
            assertEquals(2,db.sampleDao().getAllSamples(7).count{it.acquisitionStatus=="AVAILABLE"})
            assertNotNull(db.imageIdentityDao().owner(7,"pixels_sha256","pixels-c"))
        } finally {db.close()}
    }
}
