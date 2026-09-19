package com.squareup.moshi
/** Compile-only annotation for SDK-free policy tests; no Moshi serialization is simulated or tested. */
@Target(AnnotationTarget.CLASS)
annotation class JsonClass(val generateAdapter: Boolean = false)
