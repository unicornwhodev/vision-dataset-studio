package com.unicornwhodev.visiondatasetstudio.data.preferences

import android.content.Context
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Non-sensitive display preferences only. The HF credential remains in KeystoreManager. */
class StudioPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("studio_ui_v2", Context.MODE_PRIVATE)
    private fun theme() = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", "SYSTEM") } ?: ThemeMode.SYSTEM
    private fun density() = GridDensity.entries.firstOrNull { it.name == prefs.getString("density", "COMFORTABLE") } ?: GridDensity.COMFORTABLE
    private val mutable = MutableStateFlow(StudioPreferences(
        theme = theme(), gridDensity = density(), autoAdvance = prefs.getBoolean("auto_advance", true),
        showGuidance = prefs.getBoolean("guidance", true), leftHanded = prefs.getBoolean("left_handed", false),
        showCanvasLabels = prefs.getBoolean("canvas_labels", true), captionLanguage = prefs.getString("caption_language", "fr") ?: "fr"
    ))
    val state = mutable.asStateFlow()
    fun update(value: StudioPreferences) {
        prefs.edit().putString("theme", value.theme.name).putString("density", value.gridDensity.name)
            .putBoolean("auto_advance", value.autoAdvance).putBoolean("guidance", value.showGuidance)
            .putBoolean("left_handed", value.leftHanded).putBoolean("canvas_labels", value.showCanvasLabels)
            .putString("caption_language", value.captionLanguage).apply()
        mutable.value = value
    }
    var activeProjectId: Long
        get() = prefs.getLong("active_project", 1L)
        set(value) { prefs.edit().putLong("active_project", value).apply() }
    var lastSampleId: String?
        get() = prefs.getString("last_sample_$activeProjectId", if(activeProjectId==1L) prefs.getString("last_sample",null) else null)
        set(value) { prefs.edit().putString("last_sample_$activeProjectId", value).apply() }
    var lastBatch: Int
        get() = prefs.getInt("last_batch_$activeProjectId", if(activeProjectId==1L) prefs.getInt("last_batch",1) else 1).coerceAtLeast(1)
        set(value) { prefs.edit().putInt("last_batch_$activeProjectId", value.coerceAtLeast(1)).apply() }
}
