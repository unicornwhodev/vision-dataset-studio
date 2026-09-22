package com.unicornwhodev.visiondatasetstudio

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.ProjectMaintenance
import com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity
import com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProjectMaintenanceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun discardedBatchRetainsCursorAndDeduplicationAfterInterruptedCleanup()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();val storage=StorageManager(context);val id=88775L
        val sample=SampleEntity("discard-old",id,1,"same",0,sourceOrdinal=0,sourceFileUrl=null,localImagePath=null,sha256="discard-sha",acquisitionStatus="AVAILABLE",annotationStatus="PENDING",syncStatus="NOT_EXPORTED")
        try {
            db.projectDao().saveProject(ProjectEntity(id=id,lastRowCursor=2))
            db.batchDao().insertOrReplace(BatchEntity(id,1,"READY"));db.sampleDao().insertSamples(listOf(sample))
            assertNull(ImageIdentity.accept(db,sample,"discard-pixels"))
            val workflow=java.io.File(context.filesDir,"workflows/$id-1.json").apply{parentFile!!.mkdirs();writeText("old workflow")}
            try { ProjectMaintenance(context,db,storage){error("simulated process death")}.discardBatch(id,1);fail("interruption expected") } catch(_:IllegalStateException) {}
            assertEquals("DISCARDED",db.batchDao().getBatchSync(id,1)!!.status)
            assertEquals(2,db.projectDao().getProjectSync(id)!!.lastRowCursor)
            assertNull(db.sampleDao().getSampleSync(sample.sampleId))
            assertNotNull(db.imageIdentityDao().owner(id,"file_sha256","discard-sha"))
            ProjectMaintenance(context,db,storage).resumePending()
            assertFalse(workflow.exists());assertFalse(java.io.File(context.filesDir,"maintenance/pending.json").exists())
            db.batchDao().insertOrReplace(BatchEntity(id,2,"READY"))
            val duplicate=sample.copy(sampleId="discard-duplicate",batchNumber=2,sourceOrdinal=2)
            db.sampleDao().insertSamples(listOf(duplicate))
            assertNotNull(ImageIdentity.accept(db,duplicate,"discard-pixels"))
            assertEquals("DUPLICATE",db.sampleDao().getSampleSync(duplicate.sampleId)!!.annotationStatus)
            assertTrue(com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates.contains("DISCARDED"))
        } finally { java.io.File(context.filesDir,"maintenance/pending.json").delete();db.close() }
    }

    @Test fun resetBatchPreservesProjectConfigurationAndSharedModelProfile()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();val storage=StorageManager(context)
        val project=ProjectEntity(id=88771,name="maintenance",classesCsv="cat,dog",activeTasksCsv="DETECTION",modelPath="/shared/model.tflite",lastRowCursor=12)
        val sample=SampleEntity("maintenance-sample",projectId=project.id,batchNumber=3,assetId="a",sourceRowIndex=7,sourceOrdinal=7,sourceFileUrl=null,localImagePath=null,acquisitionStatus="AVAILABLE",annotationStatus="IN_PROGRESS",syncStatus="NOT_EXPORTED")
        try {
            db.projectDao().saveProject(project);db.batchDao().insertOrReplace(BatchEntity(project.id,3,"IN_PROGRESS"));db.sampleDao().insertSamples(listOf(sample))
            db.annotationDao().insertOrReplace(AnnotationRecord(sample.sampleId,"{}"));db.modelProfileDao().save(ModelProfileEntity("shared","shared","/shared/model.tflite","sha","{}","report"))
            InferenceReceiptStore(context.filesDir).write(project.id,sample.sampleId,3,InferenceResult.Empty("none",InferenceDiagnostics("sha","ssd","object_detection",listOf(1,3,300,300),threshold=.5f,proposalCount=0)))
            ProjectMaintenance(context,db,storage).resetBatch(project.id,3)
            val kept=db.projectDao().getProjectSync(project.id)!!
            assertEquals("cat,dog",kept.classesCsv);assertEquals("/shared/model.tflite",kept.modelPath);assertEquals(7,kept.lastRowCursor)
            assertNull(db.batchDao().getBatchSync(project.id,3));assertNull(db.sampleDao().getSampleSync(sample.sampleId));assertNull(db.annotationDao().getAnnotationSync(sample.sampleId))
            assertNotNull(db.modelProfileDao().get("shared"))
            assertTrue(InferenceReceiptStore(context.filesDir).list(project.id).isNotEmpty())
        } finally { db.close() }
    }

    @Test fun interruptedCleanupResumesFromDurableReceipt()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();val storage=StorageManager(context);val id=88772L
        try {
            db.projectDao().saveProject(ProjectEntity(id=id));db.batchDao().insertOrReplace(BatchEntity(id,4,"IN_PROGRESS"))
            db.sampleDao().insertSamples(listOf(SampleEntity("interrupt-sample",projectId=id,batchNumber=4,assetId="a",sourceRowIndex=0,sourceFileUrl=null,localImagePath=null,acquisitionStatus="AVAILABLE",annotationStatus="IN_PROGRESS",syncStatus="NOT_EXPORTED")))
            try { ProjectMaintenance(context,db,storage){if(it=="after_database")error("simulated process death")}.resetBatch(id,4);fail("interruption expected") } catch(_:IllegalStateException) {}
            assertNull(db.batchDao().getBatchSync(id,4));assertTrue(java.io.File(context.filesDir,"maintenance/pending.json").isFile)
            ProjectMaintenance(context,db,storage).resumePending()
            assertFalse(java.io.File(context.filesDir,"maintenance/pending.json").exists())
        } finally { db.close() }
    }

    @Test fun resetProjectForgetsIdentityAndAllowsTheSameCorpusAgain()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();val storage=StorageManager(context);val id=88773L
        try {
            db.projectDao().saveProject(ProjectEntity(id=id,activeTasksCsv="DETECTION"))
            db.batchDao().insertOrReplace(BatchEntity(id,1,"READY"))
            val first=SampleEntity("old-sample",id,1,"same-image",0,sourceFileUrl=null,localImagePath=null,sha256="same-file-sha",acquisitionStatus="AVAILABLE",annotationStatus="PENDING",syncStatus="NOT_EXPORTED")
            db.sampleDao().insertSamples(listOf(first))
            assertNull(ImageIdentity.accept(db,first,"same-pixel-sha"))
            assertEquals("old-sample",db.imageIdentityDao().owner(id,"file_sha256","same-file-sha")!!.firstSampleId)

            ProjectMaintenance(context,db,storage).resetProject(id)
            assertNull(db.imageIdentityDao().owner(id,"file_sha256","same-file-sha"))
            db.batchDao().insertOrReplace(BatchEntity(id,1,"READY"))
            val importedAgain=first.copy(sampleId="new-sample")
            db.sampleDao().insertSamples(listOf(importedAgain))
            assertNull(ImageIdentity.accept(db,importedAgain,"same-pixel-sha"))
            assertEquals("new-sample",db.imageIdentityDao().owner(id,"file_sha256","same-file-sha")!!.firstSampleId)
            assertEquals("AVAILABLE",db.sampleDao().getSampleSync("new-sample")!!.acquisitionStatus)
        } finally { db.close() }
    }

    @Test fun orphanTrainingCleanupPreservesActiveAndSharedModels()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val models=java.io.File(context.filesDir,"models").apply{mkdirs()}
        fun candidate(id:String)=java.io.File(models,"training-$id").apply{mkdirs()}.let{java.io.File(it,"model.tflite").apply{writeText("model")}}
        val active=candidate("11111111-1111-1111-1111-111111111111")
        val shared=candidate("22222222-2222-2222-2222-222222222222")
        val orphan=candidate("33333333-3333-3333-3333-333333333333")
        try {
            db.projectDao().saveProject(ProjectEntity(id=88774,modelPath=active.path))
            db.modelProfileDao().save(ModelProfileEntity("shared-cleanup","shared",shared.path,"sha","{}",""))
            assertEquals(1,OnDeviceTraining(context).cleanupOrphanedCandidates(db))
            assertTrue(active.isFile);assertTrue(shared.isFile);assertFalse(orphan.parentFile!!.exists())
        } finally { active.parentFile?.deleteRecursively();shared.parentFile?.deleteRecursively();orphan.parentFile?.deleteRecursively();db.close() }
    }
}
