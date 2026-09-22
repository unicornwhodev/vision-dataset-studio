package com.unicornwhodev.visiondatasetstudio

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.ProjectMaintenance
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProjectMaintenanceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

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
}
