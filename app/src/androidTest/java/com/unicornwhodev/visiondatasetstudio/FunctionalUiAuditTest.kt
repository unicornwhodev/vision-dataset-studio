package com.unicornwhodev.visiondatasetstudio

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.StudioPreferenceStore
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.theme.VisionDatasetStudioTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class FunctionalUiAuditTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun englishScreensPersistModelSettingsAndIsolateMultipleProjects()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val previousLocale=Locale.getDefault();val store=ViewModelStore()
        Locale.setDefault(Locale.ENGLISH)
        val englishConfig=Configuration(app.resources.configuration).apply{setLocale(Locale.ENGLISH)}
        val previousActivityConfig=Configuration(rule.activity.resources.configuration)
        withContext(Dispatchers.Main){rule.activity.resources.updateConfiguration(englishConfig,rule.activity.resources.displayMetrics)}
        val english=app.createConfigurationContext(englishConfig)
        val vm=withContext(Dispatchers.Main){ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java]}
        val originalProject=vm.preferenceStore.activeProjectId;val previousPreferences=vm.preferences.value
        val created=mutableListOf<Long>()
        suspend fun act(block:MainViewModel.()->Unit) {
            withContext(Dispatchers.Main){vm.block()}
            withTimeout(30_000){while(vm.isBusy.value || vm.editorBusy.value)delay(25)}
            withTimeout(10_000){while(vm.projectFlow.value?.id!=vm.activeProjectId.value)delay(25)}
        }
        // Dialogs/popups can have a second Compose root, especially on API 35.
        // Inspect every root so localization checks also cover those surfaces.
        fun screenTree():String {
            val roots=rule.onAllNodes(isRoot(),useUnmergedTree=true)
            return roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() }
        }
        fun englishScreen(title:String) {
            rule.waitForIdle()
            rule.onAllNodesWithText(title,useUnmergedTree=true).onFirst().assertExists()
            val tree=screenTree()
            assertFalse("French application text on $title",Regex("Réglages|Réinitialiser|Télécharger|Apprentissage|Supprimer|Données locales|Légendes|Modèles").containsMatchIn(tree))
            val folder=File(app.filesDir,"qa-evidence/english").apply{mkdirs()}
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { image ->
                File(folder,title.replace(Regex("[^A-Za-z0-9]"),"_")+".png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            }
        }
        try {
            act { createProject("QA English A") };val first=vm.activeProjectId.value;created+=first
            rule.setStudioTestContent { val registry=requireNotNull(LocalActivityResultRegistryOwner.current); CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry,LocalContext provides english,LocalConfiguration provides englishConfig) { VisionDatasetStudioTheme(darkTheme=true) { StudioRoot(vm) } } }
            englishScreen("Workspace")
            // Create through the actual form, rather than replacing Room with a mock.
            rule.onNodeWithText(english.getString(R.string.controls_new_project_name)).performScrollTo().performTextInput("QA English B")
            // Wait for IME dismissal before scrolling/clicking: its opening
            // animation can move the button outside the viewport after lookup.
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.waitForIdle()
            rule.onNodeWithText(english.getString(R.string.controls_create_project)).performScrollTo().assertIsDisplayed().performClick()
            rule.waitUntil(30_000){vm.activeProjectId.value!=first && !vm.isBusy.value}
            val second=vm.activeProjectId.value;created+=second
            assertEquals("QA English B",vm.db.projectDao().getProjectSync(second)!!.name)
            act { selectProject(first) }
            val config=ModelConfig(task="classification",adapter="tinyclip",bundleKind="tinyclip",labels=listOf("cat","dog"),prompt="a photo of {label}",threshold=0f)
            act { saveModelConfig(StudioJson.moshi.adapter(ModelConfig::class.java).toJson(config)) }
            act { navigateTo(Screen.Models) };englishScreen("Models")
            rule.onNodeWithText("Model prompt").performScrollTo().performTextReplacement("a drawing of {label}")
            rule.onNodeWithText("Save model settings").performScrollTo().performClick()
            rule.waitUntil(10_000){!vm.isBusy.value && vm.projectFlow.value?.modelConfigJson?.contains("a drawing of") == true}
            val stored=StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(vm.db.projectDao().getProjectSync(first)!!.modelConfigJson!!)!!
            assertEquals("a drawing of {label}",stored.prompt)
            act { startWorkflow("manual","Review every proposed cat before accepting") }
            act { selectProject(second) }
            assertNull(vm.workflow.value);assertNull(vm.currentSample.value);assertNull(vm.db.projectDao().getProjectSync(second)!!.modelConfigJson)
            act { selectProject(first) }
            act { navigateTo(Screen.Preferences) };englishScreen("Settings")
            val prior=vm.preferences.value.showGuidance
            rule.onNodeWithText("Show guidance").performScrollTo().performClick()
            assertEquals(!prior,vm.preferences.value.showGuidance)
            assertEquals(!prior,StudioPreferenceStore(app).state.value.showGuidance)
            for((screen,title) in listOf(Screen.Home to "QA English A",Screen.Setup to "Project",Screen.BatchGrid to "Batch 01",Screen.Publication to "Export",Screen.QualityDashboard to "Quality",Screen.Workflow to "Workflow",Screen.Training to "Training",Screen.Similarity to "Similar images")) {
                act { navigateTo(screen) };englishScreen(title)
            }
            // Persisted model proposals must be discoverable without opening an overflow menu.
            val image=vm.storageManager.getImageFile("ui-audit-$first","png")
            Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}.let{bitmap->image.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            val sample=SampleEntity("ui-audit-$first",first,1,"UI fixture",0,sourceFileUrl=null,localImagePath=image.path,imageWidth=48,imageHeight=32,acquisitionStatus="AVAILABLE",annotationStatus="PROPOSALS_AVAILABLE",syncStatus="NOT_EXPORTED")
            vm.db.projectDao().saveProject(vm.db.projectDao().getProjectSync(first)!!.copy(activeTasksCsv="CLASSIFICATION",classesCsv="cat,dog"))
            vm.db.batchDao().insertOrReplace(BatchEntity(first,1,"IN_PROGRESS",totalCases=1));vm.db.sampleDao().insertSamples(listOf(sample))
            vm.db.annotationDao().insertOrReplace(AnnotationRecord(sample.sampleId,StudioJson.moshi.adapter(SampleAnnotations::class.java).toJson(SampleAnnotations(tags=listOf(TagTarget("model-tag","cat",sourceProvenance="model_litert"))))))
            act { openSampleInEditor(sample.sampleId) }
            rule.onNodeWithTag("annotation_proposals").assertIsDisplayed().performClick()
            rule.onNodeWithText("cat").assertIsDisplayed()
            rule.onNodeWithContentDescription("Proposal to review").assertExists()
            rule.onNodeWithContentDescription("Workflow instructions").performClick()
            rule.onNodeWithText("Review every proposed cat before accepting").assertIsDisplayed()
            rule.onNodeWithText(english.getString(R.string.action_close)).performClick()
            englishScreen("Annotations")
            act { selectProject(second) };assertNull(vm.currentSample.value);assertTrue(vm.currentAnnotations.value.tags.isEmpty())
            act { deleteCurrentProject() };created.remove(second)
            assertNull(vm.db.projectDao().getProjectSync(second));assertNotNull(vm.db.projectDao().getProjectSync(first))
        } catch(failure:Throwable) {
            val folder=File(app.filesDir,"qa-evidence/english").apply{mkdirs()}
            File(folder,"failure.txt").writeText(failure.toString()+"\noperation="+vm.operationProgress.value+"\nactive="+vm.activeProjectId.value+"\n"+runCatching{screenTree()}.getOrDefault("no tree"))
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->File(folder,"failure.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            throw failure
        } finally {
            for(id in created) { act { selectProject(id) };act { deleteCurrentProject() } }
            if(vm.db.projectDao().getProjectSync(originalProject)!=null)act { selectProject(originalProject) }
            vm.updatePreferences(previousPreferences)
            withContext(Dispatchers.Main){store.clear();rule.activity.resources.updateConfiguration(previousActivityConfig,rule.activity.resources.displayMetrics)};Locale.setDefault(previousLocale)
        }
    }
}
