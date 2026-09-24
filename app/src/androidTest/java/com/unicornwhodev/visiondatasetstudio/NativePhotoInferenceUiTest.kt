package com.unicornwhodev.visiondatasetstudio

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.theme.VisionDatasetStudioTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Opt-in public photo smoke test. Small examples do not establish dataset accuracy. */
class NativePhotoInferenceUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun realTinyclipPromptsChangeNativeOutputsAndProposalsAppearInTheEditor()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realPhotoAudit")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val root=File(app.filesDir,"conversion-qualification/Charlbi-tinyclip")
        val model=File(root,"bundle.json");val image=File(app.filesDir,"functional-audit-fixture/coco_sample.png")
        assertTrue(model.isFile);assertEquals("cf6f3c4befa148732c7453e0de5afab00f682427435fead2d88b07a9615cdac2",HashUtils.computeSha256(image))
        val entry=CommunityModelCatalog.entries.single{it.id=="tinyclip"}
        val item=CommunityModelCatalog.Availability(entry,File(root,"revision.txt").readText().trim(),emptyList(),true,true,"QA")
        val config=CommunityModelCatalog.suggestedConfig(item,model).copy(labels=listOf("cats","dogs","bicycles"),prompt="a photo of {label}",threshold=0f,topK=3)
        val store=ViewModelStore();val oldLocale=Locale.getDefault();Locale.setDefault(Locale.ENGLISH)
        val vm=withContext(Dispatchers.Main){ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java]}
        val previous=vm.activeProjectId.value;var projectId:Long?=null
        val previousActivityConfig=Configuration(rule.activity.resources.configuration)
        suspend fun act(block:MainViewModel.()->Unit) {
            withContext(Dispatchers.Main){vm.clearOperationProgress();vm.block()}
            withTimeout(900_000){while(vm.isBusy.value || vm.editorBusy.value)delay(50)}
            assertFalse(vm.operationProgress.value.toString(),vm.operationProgress.value?.isError==true)
        }
        try {
            act{createProject("QA native photo")};val id=vm.activeProjectId.value;projectId=id
            val adapter=StudioJson.moshi.adapter(ModelConfig::class.java)
            vm.db.projectDao().saveProject(vm.db.projectDao().getProjectSync(id)!!.copy(modelPath=model.path,modelConfigJson=adapter.toJson(config),classesCsv="cats,dogs,bicycles,curated-label",activeTasksCsv="CLASSIFICATION"))
            vm.db.batchDao().insertOrReplace(BatchEntity(id,1,"IN_PROGRESS",totalCases=1))
            val local=vm.storageManager.getImageFile("native-photo-$id","png");image.copyTo(local,true)
            val sample=SampleEntity("native-photo-$id",id,1,"public cats photo",0,sourceFileUrl=null,localImagePath=local.path,imageWidth=640,imageHeight=480,sha256=HashUtils.computeSha256(local),acquisitionStatus="AVAILABLE",annotationStatus="PENDING",syncStatus="NOT_EXPORTED")
            vm.db.sampleDao().insertSamples(listOf(sample))
            val englishConfig=Configuration(app.resources.configuration).apply{setLocale(Locale.ENGLISH)}
            withContext(Dispatchers.Main){rule.activity.resources.updateConfiguration(englishConfig,rule.activity.resources.displayMetrics)}
            val english=app.createConfigurationContext(englishConfig)
            rule.setStudioTestContent{val registry=requireNotNull(LocalActivityResultRegistryOwner.current);CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry,LocalContext provides english,LocalConfiguration provides englishConfig){VisionDatasetStudioTheme(darkTheme=true){StudioRoot(vm)}}}
            act{openSampleInEditor(sample.sampleId)};act{runLiteRtOnCurrentSample()}
            val first=vm.liteRtEngine.lastResult!!.orThrow()
            assertEquals(3,first.size);assertEquals("cats",first.maxBy{it.score}.label)
            assertEquals(3,vm.currentAnnotations.value.tags.size);assertTrue(vm.currentAnnotations.value.tags.none{it.isHumanVerified})
            rule.onNodeWithTag("annotation_proposals").assertIsDisplayed().performClick()
            rule.onNodeWithText("cats").assertIsDisplayed()
            rule.onAllNodesWithContentDescription("Proposal to review").assertCountEquals(3)
            val directory=File(app.filesDir,"qa-evidence/native-photo").apply{mkdirs()}
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{bitmap->File(directory,"tinyclip-proposals.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            withContext(Dispatchers.Main){vm.updateAnnotations(vm.currentAnnotations.value.copy(tags=vm.currentAnnotations.value.tags+TagTarget("human-kept","curated-label",isHumanVerified=true)))}
            val changed=config.copy(prompt="a drawing of {label}",topK=2)
            act{openSampleInEditor(sample.sampleId)} // Flush the human correction before snapshotting.
            val annotationsBefore=vm.db.annotationDao().getAnnotationSync(sample.sampleId)!!
            val sampleBefore=vm.db.sampleDao().getSampleSync(sample.sampleId)!!
            val receiptsBefore=InferenceReceiptStore(app.filesDir).list(id)
            act{saveModelConfig(adapter.toJson(changed))}
            act{preannotateActiveBatch()} // Ordinary batch preannotation must not rerun existing proposals.
            act{openSampleInEditor(sample.sampleId)}
            assertEquals(annotationsBefore,vm.db.annotationDao().getAnnotationSync(sample.sampleId))
            assertEquals(sampleBefore,vm.db.sampleDao().getSampleSync(sample.sampleId))
            assertEquals(receiptsBefore,InferenceReceiptStore(app.filesDir).list(id))
            assertEquals(4,vm.currentAnnotations.value.tags.size)
            act{runLiteRtOnCurrentSample()} // Explicit user action is the only replacement trigger.
            val second=vm.liteRtEngine.lastResult!!.orThrow()
            assertEquals(2,second.size)
            assertTrue("A custom prompt must change native scores",second.any{out->kotlin.math.abs(out.score-first.single{it.label==out.label}.score)>1e-6f})
            assertEquals(1,vm.currentAnnotations.value.tags.count{it.id=="human-kept" && it.isHumanVerified})
            act{openSampleInEditor(sample.sampleId)} // Flush and reload from Room.
            assertEquals(3,vm.currentAnnotations.value.tags.size)
            assertEquals("PROPOSALS_AVAILABLE",vm.db.sampleDao().getSampleSync(sample.sampleId)!!.annotationStatus)
            File(directory,"result.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(mapOf(
                "runtime" to "Android LiteRT CPU","photo_sha256" to HashUtils.computeSha256(image),"first_prompt" to config.prompt,"second_prompt" to changed.prompt,
                "first_outputs" to first.map{mapOf("label" to it.label,"score" to it.score)},"second_outputs" to second.map{mapOf("label" to it.label,"score" to it.score)},
                "settings_change_preserved_existing_annotations" to true,"ordinary_preannotation_preserved_existing_annotations" to true,"visible_in_editor" to true,"persisted_in_room" to true,"human_correction_preserved" to true,"accuracy_evaluated" to false)))
        } finally {
            projectId?.let{act{selectProject(it)};act{deleteCurrentProject()}}
            if(vm.db.projectDao().getProjectSync(previous)!=null)act{selectProject(previous)}
            withContext(Dispatchers.Main){store.clear();rule.activity.resources.updateConfiguration(previousActivityConfig,rule.activity.resources.displayMetrics)};Locale.setDefault(oldLocale)
        }
    }
}
