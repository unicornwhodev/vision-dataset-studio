package com.unicornwhodev.visiondatasetstudio
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioComposeV4Test {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun controlsAndCatalogueRenderWithoutDownloadingModels() {
        rule.waitUntil(10000) { rule.onAllNodesWithTag("home_primary").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("controls_shortcut").assertIsDisplayed()
        rule.onNodeWithTag("controls_shortcut").performClick()
        rule.onNodeWithText("Mon espace").assertIsDisplayed()
        rule.onNodeWithText("Modèles").performClick()
        rule.onNodeWithText("Catalogue public téléchargeable").assertExists()
        rule.onNodeWithText("SSD MobileNet V1").assertExists()
    }
    @Test fun compactNavigationKeepsImportAndExportAccessible() {
        rule.waitUntil(10000) { rule.onAllNodesWithTag("nav_Models").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("nav_Models").performClick()
        rule.onNodeWithText("Importer", useUnmergedTree = true).assertIsDisplayed().performClick()
        rule.onNodeWithText("Choisir un .tflite").assertIsDisplayed()
        rule.onNodeWithTag("nav_Publication").performClick()
        rule.onNodeWithText("Archive locale").assertExists()
        rule.onNodeWithTag("nav_QualityDashboard").performClick()
        rule.onNodeWithTag("nav_QualityDashboard").assertIsSelected()
        rule.onNodeWithText("Stockage").assertExists()
        rule.onNodeWithTag("nav_Home").performClick()
        // The workspace command must now be immediately available on both layouts.
        rule.onNodeWithTag("home_primary").assertIsDisplayed().assertHasClickAction()
    }
}
