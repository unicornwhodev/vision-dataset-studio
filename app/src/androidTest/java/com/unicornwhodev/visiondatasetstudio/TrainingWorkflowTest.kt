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
        val fixtureModel=File(fixture,"trainable-vision-fixture.tflite")
        assumeTrue("Stage the untrained fixture",fixtureModel.isFile)
        val json=File(fixture,"model_config.json").readText()
        val projectId=System.currentTimeMillis()
        val model=File(context.filesDir,"models/original-qa-$projectId/model.tflite").apply{parentFile!!.mkdirs()}
        fixtureModel.copyTo(model)
        File(fixture,"runtime_contract.json").takeIf{it.isFile}?.copyTo(File(model.parentFile,"runtime_contract.json"))
        val originalSha=HashUtils.computeSha256(model)
        val storage=com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager(context)
        val project=ProjectEntity(id=projectId,name="QA apprentissage Android",classesCsv="rouge,bleu",activeTasksCsv="CLASSIFICATION",modelPath=model.path,modelConfigJson=json,diskBudgetMb=8192)
        val db=AppDatabase.getInstance(context);db.projectDao().saveProject(project)
        val importedProfile=ModelProfileEntity("original-qa-$projectId","Original QA",model.path,originalSha,json,"Synthetic imported model")
        db.modelProfileDao().save(importedProfile)
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
        try {store.prepare(project,1,epochs=3,learningRate=.05f)}catch(e:IllegalArgumentException){refused=true}
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
        val preflight=store.inspectPreparation(project,1)
        val abandoned=store.prepare(project,1,epochs=3,learningRate=.05f)
        assertTrue(runCatching{store.abandon(projectId,"stale-run-id")}.isFailure)
        assertEquals("queued",store.read(projectId)!!.phase)
        store.cancel(projectId)
        assertEquals("cancelled",store.read(projectId)!!.phase)
        assertTrue(runCatching{store.requireCleanupAllowed(project,1,true,db.batchDao().getBatchSync(projectId,1)!!.archiveSnapshot)}.isFailure)
        assertTrue(runCatching{store.abandon(projectId,"stale-run-id")}.isFailure)
        store.abandon(projectId,abandoned.id)
        assertEquals("abandoned",store.readBatch(projectId,1)!!.phase)
        val enabledProject=project.copy(settingsJson=com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.write(
            com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings(continuousTraining=true)))
        store.requireCleanupAllowed(enabledProject,1,true,db.batchDao().getBatchSync(projectId,1)!!.archiveSnapshot)
        assertTrue(abandoned.samples.all{File(it.image).isFile})
        assertTrue(accepted.all{File(it.localImagePath!!).isFile})
        assertEquals(model.path,db.projectDao().getProjectSync(projectId)!!.modelPath)
        assertTrue(runCatching{store.resume(projectId)}.isFailure)
        // Discarding the private training snapshot is a separate cleanup action.
        store.releaseBatchImages(projectId,1)
        assertTrue(abandoned.samples.all{!File(it.image).exists()})
        assertTrue(accepted.all{File(it.localImagePath!!).isFile})
        // A new explicitly requested run can still train the same verified batch.
        val run=store.prepare(project,1,epochs=3,learningRate=.05f)
        assertNotEquals(abandoned.id,run.id)
        assertEquals(preflight.samples.map{it.sha256},run.samples.map{it.sha256})
        assertEquals(preflight.trainCount,run.samples.count{!it.validation})
        assertEquals(preflight.validationCount,run.samples.count{it.validation})
        assertEquals(run.id,store.readBatch(projectId,1)!!.id)
        assertEquals(1,run.sourceBatchNumber);assertTrue(run.exportProof.isNotBlank())
        var cleanupRefused=false
        try {store.requireCleanupAllowed(project,1,true,db.batchDao().getBatchSync(projectId,1)!!.archiveSnapshot)}catch(_:IllegalStateException){cleanupRefused=true}
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
        assertEquals(originalSha,HashUtils.computeSha256(model))
        assertNotEquals(model.canonicalPath,File(done.modelFile).canonicalPath)
        val learnedProfileId="trained-${done.lineageId}"
        val originalProfile=db.modelProfileDao().get(importedProfile.id)!!
        assertEquals("Existing original profile is retained unchanged",importedProfile,originalProfile)
        assertEquals("Android /data/data and /data/user aliases must not duplicate the original profile",1,
            db.modelProfileDao().getAllSync().count{it.modelPath.isNotBlank() && File(it.modelPath).canonicalFile==model.canonicalFile})
        assertEquals(originalSha,originalProfile.sha256)
        assertEquals(done.modelFile,db.modelProfileDao().get(learnedProfileId)!!.modelPath)
        store.requireCleanupAllowed(project,1,true,db.batchDao().getBatchSync(projectId,1)!!.archiveSnapshot)
        assertTrue("A previous export cannot satisfy enabled training for a changed export",runCatching{store.requireCleanupAllowed(enabledProject,1,true,"changed-export")}.isFailure)
        val config=done.config.copy(trainingCheckpoint=OnDeviceTraining.saveCheckpointReceipt(File(done.modelFile),done.checkpoint!!))
        val image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
        try{LiteRtEngine().use{engine->assertTrue(engine.loadModel(File(done.modelFile)));assertEquals("rouge",engine.runInference(image,config).orThrow().maxBy{it.score}.label)}}finally{image.recycle()}
        withTimeout(10000){while(work.getWorkInfosForUniqueWork("vds-training-$projectId").get().any{!it.state.isFinished})delay(100)}
        assertEquals(96,engine.purgeReviewedBatch(projectId,1,true))
        assertTrue("Training images are cleaned only after the run completes",done.samples.all{!File(it.image).exists()})
        assertTrue("The unrelated batch survives cleanup",otherFile.isFile)
        assertTrue("Learned checkpoint survives cleanup",File(done.checkpoint!!.prefix).parentFile!!.listFiles()!!.any{it.isFile})
        // A distinct exported batch must continue the learned copy while inference still uses the original.
        db.batchDao().insertOrReplace(BatchEntity(projectId=projectId,batchNumber=3,status="IN_PROGRESS",totalCases=96))
        repeat(96) { n ->
            val image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            image.eraseColor(if(n%2==0)Color.rgb(160+n,15,25)else Color.rgb(15,25,160+n))
            image.setPixel(0,0,Color.rgb(n,80,90))
            val id="continued-qa-$projectId-$n";val file=storage.getImageFile(id,"png")
            try{file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
            val row=SampleEntity(sampleId=id,projectId=projectId,batchNumber=3,assetId=id,sourceRowIndex=n.toLong(),sourceFileUrl=null,localImagePath=file.path,imageWidth=32,imageHeight=32,sha256=HashUtils.computeSha256(file),acquisitionStatus="AVAILABLE",annotationStatus="VALIDATED",syncStatus="NOT_EXPORTED")
            db.sampleDao().insertNewSamples(listOf(row))
            db.annotationDao().insertOrReplace(AnnotationRecord(id,StudioJson.moshi.adapter(SampleAnnotations::class.java).toJson(SampleAnnotations(tags=listOf(TagTarget("continued-tag-$n",if(n%2==0)"rouge" else "bleu",isHumanVerified=true))))))
            com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity.accept(db,row,com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity.pixelSha256(file))
        }
        val nextRows=db.sampleDao().getSamplesForBatchSync(projectId,3)
        val nextArchive=exporter.packageBatchToLocalZip(project,3,nextRows.map{it to engine.getSampleAnnotations(it.sampleId)},false,true,false,false,false)
        assertTrue(nextArchive.error,nextArchive.success)
        engine.recordLocalArchive(projectId,3,nextArchive.zipFile!!)
        val nextUri=android.net.Uri.parse("content://${InstrumentationRegistry.getInstrumentation().context.packageName}.documents/continued-training-archive")
        com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives.copyVerified(context.contentResolver,nextArchive.zipFile!!,nextUri)
        engine.verifyLocalArchive(projectId,3,nextUri.toString())
        val reopened=OnDeviceTraining(context)
        // Missing learned bytes must fail closed, never start again from the original.
        val checkpointFile=File(File(done.checkpoint!!.prefix).parentFile,done.checkpoint.files.keys.first())
        val held=File(checkpointFile.path+".qa-held")
        assertTrue(checkpointFile.renameTo(held))
        try{assertTrue(runCatching{reopened.inspectPreparation(project,3)}.isFailure)}finally{assertTrue(held.renameTo(checkpointFile))}
        val continued=reopened.prepare(project,3,epochs=1,learningRate=.05f)
        assertEquals(done.lineageId,continued.lineageId)
        assertEquals(done.id,continued.parentRunId)
        assertEquals(2,continued.generation)
        LiteRtTrainingSession(File(continued.modelFile),continued.config).use { session ->
            session.restore(continued.checkpoint!!)
            assertEquals("The next run starts from learned internal weights",done.finalWeightProbe,session.weightProbe())
        }
        reopened.enqueue(projectId)
        val second=terminal()
        assertEquals(second.error,"completed",second.phase)
        assertEquals(done.finalWeightProbe,second.initialWeightProbe)
        assertNotEquals(second.initialWeightProbe,second.finalWeightProbe)
        assertEquals("Inference activation remains explicit",model.path,db.projectDao().getProjectSync(projectId)!!.modelPath)
        assertEquals("One learned profile advances across generations",second.modelFile,db.modelProfileDao().get(learnedProfileId)!!.modelPath)
        assertEquals(originalProfile,db.modelProfileDao().get(originalProfile.id))
        val activated=reopened.activate(projectId,3)
        assertEquals(second.id,activated.id)
        assertEquals(second.modelFile,db.projectDao().getProjectSync(projectId)!!.modelPath)
        assertEquals(second.id,TrainingLineageStore(context).resolve(projectId,model,StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(json)!!).learned!!.runId)
        assertEquals(originalSha,HashUtils.computeSha256(model))
        withTimeout(10000){while(work.getWorkInfosForUniqueWork("vds-training-$projectId").get().any{!it.state.isFinished})delay(100)}
        assertEquals(96,engine.purgeReviewedBatch(projectId,3,true))
        reopened.cleanupOrphanedCandidates(db)
        assertTrue(model.isFile)
        assertTrue(File(second.checkpoint!!.prefix).parentFile!!.listFiles()!!.any{it.isFile})
        File(fixture,"android-workflow-evidence.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(mapOf("project_id" to projectId,"validated_images" to run.samples.size,"excluded_rejected" to 8,"exported_batch_only" to true,"cleanup_waited" to true,"explicit_abandonment_unblocks_cleanup" to true,"abandonment_never_activates_weights" to true,"restart_after_abandonment" to true,"cleanup_after_training" to true,"steps_before_cancel" to stopped.completedSteps,"steps_after_resume" to done.completedSteps,"initial_loss" to done.initialLoss,"final_loss" to done.validationLoss,"internal_weights_changed" to (done.initialWeightProbe!=done.finalWeightProbe),"source_model_preserved" to true,"original_sha256_before" to originalSha,"original_sha256_after" to HashUtils.computeSha256(model),"lineage_id" to done.lineageId,"first_final_weights" to done.finalWeightProbe,"second_initial_weights" to second.initialWeightProbe,"second_final_weights" to second.finalWeightProbe,"second_initial_loss" to second.initialLoss,"second_final_loss" to second.validationLoss,"continued_without_activation" to true,"learned_profile_id" to learnedProfileId,"missing_checkpoint_refused" to true,"runtime" to "Android WorkManager / LiteRT CPU")))
    }
}
