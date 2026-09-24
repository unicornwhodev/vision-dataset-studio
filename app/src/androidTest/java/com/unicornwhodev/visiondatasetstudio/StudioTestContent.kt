package com.unicornwhodev.visiondatasetstudio

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/** Host component assertions in the real activity, also present in Release.
 * The activity already installed StudioRoot in onCreate, so replace that
 * composition on the UI thread; ActivityScenario still owns its lifecycle.
 */
fun AndroidComposeTestRule<*, MainActivity>.setStudioTestContent(content: @Composable () -> Unit) {
    runOnUiThread { activity.setContent(parent = null, content = content) }
    waitForIdle()
}
