package com.unicornwhodev.visiondatasetstudio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.TrainingPolicy
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Uses the public MobileNet download staged by FeatureImplementationAuditTest. */
class InferenceOnlyModelTest {
    @Test fun legacyBundleDigestsDoNotBlockLoading() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"bundle-digest-"+System.nanoTime()).apply{mkdirs()}
        try {
            listOf("image_encoder.tflite","text_encoder.tflite","pipeline.json","processor/tokenizer.json").forEach { name ->
                File(directory,name).apply{parentFile!!.mkdirs();writeText("runtime presence fixture")}
            }
            val manifest=File(directory,"bundle.json")
            manifest.writeText(StudioJson.moshi.adapter(BundleManifest::class.java).toJson(
                BundleManifest(kind="tinyclip",revision="main",files=mapOf("image_encoder.tflite" to "obsolete-digest","README.md" to "obsolete-doc-digest"))))
            LiteRtBundle(manifest).close()
            manifest.writeText("""{"kind":"tinyclip"}""")
            LiteRtBundle(manifest).close()
            File(directory,"text_encoder.tflite").delete()
            assertTrue(runCatching{LiteRtBundle(manifest)}.isFailure)
        } finally { directory.deleteRecursively() }
    }

    @Test fun importsSelectsAndInfersWithoutTrainingAndDoesNotBlockCleanup()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("inferenceOnlyAudit")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val testContext=InstrumentationRegistry.getInstrumentation().context
        val file=File(app.filesDir,"feature-audit/mobilenet-v1-classification.tflite")
        val entry=PublicModelCatalog.entries.single{it.id=="mobilenet-v1-classification"}
        val config=PublicModelCatalog.inspect(entry,file).config.copy(threshold=0f,topK=3)
        assertNull(config.training)
        org.tensorflow.lite.Interpreter(file).use { assertFalse("Fixture must actually lack a train signature",it.signatureKeys.contains("train")) }
        val store=ViewModelStore()
        val vm=withContext(Dispatchers.Main){ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java]}
        val previous=vm.activeProjectId.value;var projectId:Long?=null
        val profilesBefore=vm.db.modelProfileDao().observe().first().map{it.id}.toSet()
        suspend fun act(block:MainViewModel.()->Unit) {
            withContext(Dispatchers.Main){vm.clearOperationProgress();vm.block()}
            withTimeout(180_000){while(vm.isBusy.value || vm.editorBusy.value)delay(25)}
            assertFalse(vm.operationProgress.value.toString(),vm.operationProgress.value?.isError==true)
        }
        try {
            act{createProject("QA inference only")};val id=vm.activeProjectId.value;projectId=id
            val project=vm.db.projectDao().getProjectSync(id)!!.copy(activeTasksCsv="CLASSIFICATION",classesCsv=config.labels.joinToString(","),settingsJson=ProjectSettings.write(ProcessingSettings(continuousTraining=true)))
            vm.db.projectDao().saveProject(project)
            // Exercise the production content-URI import path, without a host optimizer.
            val uri=Uri.parse("content://${testContext.packageName}.documents/inference-only-weights")
            app.contentResolver.openOutputStream(uri)!!.use{out->file.inputStream().use{it.copyTo(out)}}
            act{importModel(uri)}
            val imported=vm.db.projectDao().getProjectSync(id)!!
            val adapter=StudioJson.moshi.adapter(ModelConfig::class.java)
            assertNull(adapter.fromJson(imported.modelConfigJson!!)!!.training)
            assertFalse(ProjectSettings.read(imported).continuousTraining)
            val profileName="QA inference profile $id"
            act{saveModelConfig(adapter.toJson(config))};act{saveActiveModelProfile(profileName)}
            val profile=vm.db.modelProfileDao().observe().first().single{it.id !in profilesBefore && it.name==profileName}
            vm.db.projectDao().saveProject(vm.db.projectDao().getProjectSync(id)!!.copy(settingsJson=project.settingsJson))
            // A stale stored digest must never prevent selecting a usable model.
            vm.db.modelProfileDao().save(profile.copy(sha256="obsolete-digest"))
            act{selectModelProfile(profile.id)}
            assertFalse(TrainingPolicy.enabled(vm.db.projectDao().getProjectSync(id)!!))
            val image=vm.storageManager.getImageFile("inference-only-$id","png")
            Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}.let{bitmap->image.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            vm.db.batchDao().insertOrReplace(BatchEntity(id,1,"IN_PROGRESS",totalCases=1))
            val sample=SampleEntity("inference-only-$id",id,1,"fixture",0,sourceFileUrl=null,localImagePath=image.path,imageWidth=32,imageHeight=32,acquisitionStatus="AVAILABLE",annotationStatus="PENDING",syncStatus="NOT_EXPORTED")
            vm.db.sampleDao().insertSamples(listOf(sample))
            act{openSampleInEditor(sample.sampleId)};act{runLiteRtOnCurrentSample()}
            assertEquals(3,vm.currentAnnotations.value.tags.size)
            assertTrue(vm.currentAnnotations.value.tags.all{it.sourceProvenance.startsWith("model_litert:")})
            assertNull(vm.deviceTraining.readBatch(id,1))
            // Legacy stale preference must not gate cleanup when the active model cannot train.
            val stale=vm.db.projectDao().getProjectSync(id)!!.copy(settingsJson=project.settingsJson)
            assertFalse(TrainingPolicy.enabled(stale));vm.deviceTraining.requireCleanupAllowed(stale,1,true,null)
            val evidence=File(app.filesDir,"qa-evidence/inference-only/result.json").apply{parentFile!!.mkdirs()}
            evidence.writeText("""{"imported_via_content_uri":true,"profile_selected":true,"stale_digest_blocks_selection":false,"native_proposals":3,"has_train_signature":false,"training_started":false,"stale_training_option_blocks_cleanup":false,"accuracy_evaluated":false}""")
        } finally {
            projectId?.let{act{selectProject(it)};act{deleteCurrentProject()}}
            for(profile in vm.db.modelProfileDao().observe().first().filter{it.id !in profilesBefore})act{removeModelProfile(profile.id)}
            if(vm.db.projectDao().getProjectSync(previous)!=null)act{selectProject(previous)}
            withContext(Dispatchers.Main){store.clear()}
        }
    }
}
