package com.unicornwhodev.visiondatasetstudio.data.json

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Generated adapters first, Kotlin reflection only as a fallback for legacy non-generated DTOs. */
object StudioJson {
    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
}
