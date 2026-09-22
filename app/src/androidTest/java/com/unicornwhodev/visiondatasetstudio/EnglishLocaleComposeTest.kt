package com.unicornwhodev.visiondatasetstudio

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class EnglishLocaleComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun modelAndTrainingPrimaryActionsUseEnglishResources() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val configuration=Configuration(base.resources.configuration).apply{setLocale(Locale.ENGLISH)}
        val english=base.createConfigurationContext(configuration)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides english,LocalConfiguration provides configuration) {
                Text(listOf(
                    stringResource(R.string.models_tab_explore),
                    stringResource(R.string.models_choose_file),
                    stringResource(R.string.training_start),
                    stringResource(R.string.training_activate),
                    stringResource(R.string.training_scope_classification),
                    stringResource(R.string.qualification_training),
                    stringResource(R.string.export_format_coco)
                ).joinToString(" · "))
            }
        }
        rule.onNodeWithText("Explore · Choose a .tflite file · Train exported batch · Activate trained weights · Classification head · frozen encoder · Training qualified · COCO — boxes + masks").assertIsDisplayed()
    }
}
