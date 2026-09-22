package com.unicornwhodev.visiondatasetstudio

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnnotationPreservationTest {
    @Test fun automaticPreannotationNeverTouchesExistingRecordsEvenWithStalePendingStatus()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val storage=StorageManager(context);val hf=HfApiClient{null};val runtime=LiteRtEngine()
        val engine=BatchEngine(db,storage,hf,runtime,DatasetExporters(storage,hf))
        val projectId=998822L
        try {
            db.projectDao().saveProject(ProjectEntity(id=projectId,activeTasksCsv="CLASSIFICATION"))
            db.batchDao().insertOrReplace(BatchEntity(projectId,1,"IN_PROGRESS"))
            // No weights and intentionally nonexistent image paths: reaching inference is a failure.
            // Legacy/imported records are protected even if empty, malformed or still PENDING.
            val samples=listOf("imported","empty","malformed","already-proposed","empty-result","reviewed").mapIndexed { i,id ->
                SampleEntity(id,projectId,1,id,i.toLong(),sourceFileUrl=null,localImagePath="/never-decode-existing-$id.png",
                    acquisitionStatus="AVAILABLE",annotationStatus=when(id){"already-proposed"->"PROPOSALS_AVAILABLE";"empty-result"->"IN_PROGRESS";"reviewed"->"VALIDATED";else->"PENDING"},syncStatus="NOT_EXPORTED")
            }
            db.sampleDao().insertSamples(samples)
            val records=listOf(
                AnnotationRecord("imported","""{"tags":[{"id":"agent-tag","label":"cat","isHumanVerified":false,"sourceProvenance":"model_litert:old:prompt"}]}"""),
                AnnotationRecord("empty","{}"),AnnotationRecord("malformed","legacy-unreadable-record"),
                AnnotationRecord("already-proposed","{}"),AnnotationRecord("reviewed","{}"))
            records.forEach{db.annotationDao().insertOrReplace(it)}
            val receipts=InferenceReceiptStore(context.filesDir).list(projectId)
            for(config in listOf(ModelConfig.defaultClassifierPreset(),ModelConfig.defaultClassifierPreset(listOf("changed")).copy(threshold=.91f,topK=1,threads=1))) {
                assertEquals(0,engine.runBatchInference(projectId,1,config))
                for(record in records)assertEquals(record,db.annotationDao().getAnnotationSync(record.sampleId))
                for(sample in samples)assertEquals(sample,db.sampleDao().getSampleSync(sample.sampleId))
                assertEquals(receipts,InferenceReceiptStore(context.filesDir).list(projectId))
                assertTrue(db.auditDao().getLogsForBatch(1,projectId).first().isEmpty())
            }
        } finally {runtime.close();db.close()}
    }
}
