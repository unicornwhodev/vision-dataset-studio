package com.unicornwhodev.visiondatasetstudio

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.unicornwhodev.visiondatasetstudio.data.model.AnnotationStatus
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import com.unicornwhodev.visiondatasetstudio.ui.screens.SampleThumbnailCard
import com.unicornwhodev.visiondatasetstudio.ui.theme.VisionDatasetStudioTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class StudioScreenshotTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun sample_thumbnail_screenshot() {
    val sample = SampleEntity(
      sampleId = "sample_test_001",
      batchNumber = 1,
      assetId = "row_001",
      sourceRowIndex = 42L,
      sourceFileUrl = null,
      localImagePath = null,
      acquisitionStatus = "AVAILABLE",
      annotationStatus = AnnotationStatus.VALIDATED.name,
      syncStatus = "NOT_EXPORTED"
    )

    composeTestRule.runOnUiThread {
      composeTestRule.activity.setContent(parent = null) {
        VisionDatasetStudioTheme {
          SampleThumbnailCard(sample = sample, onClick = {})
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
