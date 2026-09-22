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

    @Test fun manualAndAssistedTemplatesImportReviewExportCleanAndContinue()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val testContext=InstrumentationRegistry.getInstrumentation().context
        val model=java.io.File(app.filesDir,"training-fixture/trainable-vision-fixture.tflite")
        assertTrue("Stage the documented untrained Android fixture",model.isFile)
        val adapter=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig::class.java)
        val config=adapter.fromJson(java.io.File(model.parentFile,"model_config.json").readText())!!.copy(threshold=0f)
        val store=ViewModelStore()
        val vm=withContext(Dispatchers.Main){ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java]}
        val previous=vm.activeProjectId.value;val created=mutableListOf<Long>()
        suspend fun act(block:MainViewModel.()->Unit) {
            withContext(Dispatchers.Main){vm.block()}
            withTimeout(60_000){while(vm.isBusy.value || vm.editorBusy.value)delay(25)}
            check(vm.operationProgress.value?.isError!=true){vm.operationProgress.value.toString()}
        }
        try {
            for(template in listOf("manual","assisted")) {
                act{createProject("QA actual workflow $template")};val id=vm.activeProjectId.value;created+=id
                val project=vm.db.projectDao().getProjectSync(id)!!.copy(classesCsv="rouge,bleu",activeTasksCsv="CLASSIFICATION",modelPath=model.path,modelConfigJson=adapter.toJson(config),diskBudgetMb=8192,
                    settingsJson=com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.write(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings(batchSize=2,sourceMode="LOCAL_INDEX",sourceIndexReady=true)))
                vm.db.projectDao().saveProject(project)
                val keys=listOf("workflow-$template-red","workflow-$template-blue","workflow-$template-green")
                keys.forEachIndexed { index,key ->
                    val uri=android.net.Uri.parse("content://${testContext.packageName}.documents/$key")
                    val bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(listOf(android.graphics.Color.RED,android.graphics.Color.BLUE,android.graphics.Color.GREEN)[index])
                    try{app.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
                    vm.db.sourceEntryDao().insert(listOf(SourceEntryEntity(id,index.toLong(),key,uri.toString())))
                }
                act{startWorkflow(template,"Keep every human correction")};act{resumeWorkflow()}
                val review=WorkflowJournal(app).read(id,1)!!
                assertEquals("review",WorkflowTools.template(template).steps[review.cursor]);assertEquals("waiting",review.phase)
                val imported=vm.db.sampleDao().getSamplesForBatchSync(id,1)
                assertEquals(2,imported.size)
                if(template=="assisted") {
                    val records=imported.map{vm.db.annotationDao().getAnnotationSync(it.sampleId)!!}
                    val receipts=com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceReceiptStore(app.filesDir).list(id)
                    act{saveModelConfig(adapter.toJson(config.copy(threshold=.99f,topK=1)))}
                    // Replay the preannotation step after settings changed, as after a process restart.
                    val journal=WorkflowJournal(app)
                    journal.write(review.copy(cursor=WorkflowTools.template(template).steps.indexOf("preannotate"),phase="ready"))
                    act{resumeWorkflow()}
                    for(record in records)assertEquals(record,vm.db.annotationDao().getAnnotationSync(record.sampleId))
                    assertEquals(receipts,com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceReceiptStore(app.filesDir).list(id))
                    assertEquals("review",WorkflowTools.template(template).steps[journal.read(id,1)!!.cursor])
                }
                for(row in imported) {
                    val proposed=vm.batchEngine.getSampleAnnotations(row.sampleId)
                    assertEquals(template=="assisted",proposed.tags.isNotEmpty())
                    assertTrue(proposed.tags.none{it.isHumanVerified})
                    act{openSampleInEditor(row.sampleId)}
                    withContext(Dispatchers.Main){vm.updateAnnotations(SampleAnnotations(tags=listOf(TagTarget("human-${row.sampleId}","rouge",isHumanVerified=true))))}
                    act{validateCurrentAndNext()}
                    assertEquals("VALIDATED",vm.db.sampleDao().getSampleSync(row.sampleId)!!.annotationStatus)
                }
                act{resumeWorkflow()}
                val exportWait=WorkflowJournal(app).read(id,1)!!
                assertEquals("verify_export",WorkflowTools.template(template).steps[exportWait.cursor])
                val archive=java.io.File(vm.db.batchDao().getBatchSync(id,1)!!.archivePath!!)
                val uri=android.net.Uri.parse("content://${testContext.packageName}.documents/workflow-$template-archive")
                com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives.copyVerified(app.contentResolver,archive,uri)
                vm.batchEngine.verifyLocalArchive(id,1,uri.toString())
                act{resumeWorkflow()}
                assertNull(vm.deviceTraining.readBatch(id,1)) // Training stays optional.
                val cleanup=WorkflowJournal(app).read(id,1)!!
                assertEquals("cleanup",WorkflowTools.template(template).steps[cleanup.cursor])
                assertTrue(imported.all{java.io.File(it.localImagePath!!).isFile})
                act{purgeActiveBatch()};act{resumeWorkflow()}
                assertEquals("completed",WorkflowJournal(app).read(id,1)!!.phase)
                assertTrue(imported.none{java.io.File(it.localImagePath!!).exists()})
                act{nextBatch()}
                val next=vm.db.sampleDao().getSamplesForBatchSync(id,2)
                assertEquals(1,next.size);assertFalse(imported.map{it.sha256}.contains(next.single().sha256))
            }
        } finally {
            for(id in created){act{selectProject(id)};act{deleteCurrentProject()}}
            if(vm.db.projectDao().getProjectSync(previous)!=null)act{selectProject(previous)}
            withContext(Dispatchers.Main){store.clear()}
        }
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
