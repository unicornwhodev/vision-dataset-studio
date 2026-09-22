package com.unicornwhodev.visiondatasetstudio.core.i18n

import java.util.Locale

/** Localize application text only. User labels, prompts, annotations and server replies stay intact. */
fun tr(french: String, english: String): String = if (Locale.getDefault().language == "fr") french else english
