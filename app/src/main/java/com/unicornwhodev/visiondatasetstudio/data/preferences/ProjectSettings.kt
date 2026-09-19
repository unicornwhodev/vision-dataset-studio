package com.unicornwhodev.visiondatasetstudio.data.preferences

import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object ProjectSettings {
    private val adapter = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(ProcessingSettings::class.java)
    fun read(project: ProjectEntity): ProcessingSettings = (adapter.fromJson(project.settingsJson) ?: ProcessingSettings()).validate()
    fun write(settings: ProcessingSettings): String = adapter.toJson(settings.validate())
}
