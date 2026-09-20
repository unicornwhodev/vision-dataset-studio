package com.unicornwhodev.visiondatasetstudio

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.workflow.*
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WorkflowExecutionTest {
    @Test fun reviewGateRejectionAndFinishedBatchesSurviveResume() = runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val store=ViewModelStore()
        val vm=withContext(Dispatchers.Main) { ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java] }
        suspend fun act(block:MainViewModel.()->Unit) {
            withContext(Dispatchers.Main) { vm.block() }
            withTimeout(30_000) { while(vm.isBusy.value)delay(30) }
        }
        try {
            act { createProject("QA workflow ${System.nanoTime()}") }
            val id=vm.activeProjectId.value
            vm.db.batchDao().insertOrReplace(BatchEntity(projectId=id,batchNumber=1,status="IN_PROGRESS",totalCases=1))
            val image=vm.storageManager.getImageFile("workflow-$id","png").apply { writeBytes(byteArrayOf(1,2,3)) }
            val sample=SampleEntity("workflow-$id",id,1,"one",0,sourceFileUrl=null,localImagePath=image.path,
                acquisitionStatus="AVAILABLE",annotationStatus="PENDING",syncStatus="NOT_EXPORTED")
            vm.db.sampleDao().insertSamples(listOf(sample))
            act { startWorkflow("review_export","Ne jamais valider sans ma relecture") }
            act { resumeWorkflow() }
            val paused=WorkflowJournal(app).read(id,1)!!
            assertEquals("waiting",paused.phase)
            assertEquals("review",WorkflowTools.template(paused.template).steps[paused.cursor])
            assertEquals("PENDING",vm.db.sampleDao().getSampleSync(sample.sampleId)!!.annotationStatus)
            assertTrue(image.isFile)
            vm.db.sampleDao().updateSample(sample.copy(annotationStatus="REJECTED",auditReason="Rejet explicite de recette"))
            act { resumeWorkflow() }
            val cleanup=WorkflowJournal(app).read(id,1)!!
            assertEquals("waiting",cleanup.phase)
            assertEquals("cleanup",WorkflowTools.template(cleanup.template).steps[cleanup.cursor])
            assertEquals("rejection_only",vm.db.batchDao().getBatchSync(id,1)!!.verificationKind)
            assertTrue("Workflow must wait for the human cleanup action",image.isFile)
            // A persisted final state is resumed without attempting another import or export.
            vm.db.batchDao().insertOrReplace(BatchEntity(projectId=id,batchNumber=2,status="PURGED"))
            withContext(Dispatchers.Main) { vm.loadBatch(2) }
            act { startWorkflow("manual","") }; act { resumeWorkflow() }
            assertEquals("completed",WorkflowJournal(app).read(id,2)!!.phase)
            assertTrue(vm.db.sampleDao().getSamplesForBatchSync(id,2).isEmpty())
            vm.db.batchDao().insertOrReplace(BatchEntity(projectId=id,batchNumber=3,status="EMPTY"))
            withContext(Dispatchers.Main) { vm.loadBatch(3) }
            act { startWorkflow("assisted","") }; act { resumeWorkflow() }
            assertEquals("completed",WorkflowJournal(app).read(id,3)!!.phase)
            assertTrue(vm.db.sampleDao().getSamplesForBatchSync(id,3).isEmpty())
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun journalAndPlannerRejectUnrecognizedToolsAndRemoteEndpoints() = runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val journal=WorkflowJournal(app)
        val id=System.nanoTime()
        journal.write(WorkflowRun(id,1,"manual",cursor=999))
        assertTrue(runCatching { journal.read(id,1) }.isFailure)
        for(endpoint in listOf("https://127.0.0.1/plan","http://example.com/plan","http://name:secret@localhost/plan")) {
            assertTrue(runCatching { LocalWorkflowAgent.propose(endpoint,"",emptyMap()) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { WorkflowTools.template("publish_without_review") }.isFailure)
    }
}
