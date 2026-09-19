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
        rule.waitUntil(10000) { rule.onAllNodesWithText("Projets, sources, lots et modèles").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Projets, sources, lots et modèles").performScrollTo().performClick()
        rule.onNodeWithText("Moteur et transferts").assertIsDisplayed()
        rule.onNodeWithText("Modèles").performScrollTo().performClick()
        rule.onNodeWithText("Catalogue public téléchargeable").assertExists()
        rule.onNodeWithText("SSD MobileNet V1").assertExists()
    }
}
