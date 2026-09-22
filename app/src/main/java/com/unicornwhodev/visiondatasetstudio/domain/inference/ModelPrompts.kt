package com.unicornwhodev.visiondatasetstudio.domain.inference

/** Text prompts are supported only by graphs/servers with an actual text input. */
object ModelPrompts {
    fun supportsText(config: ModelConfig) = config.runtime == "local_http" || config.bundleKind in setOf("tinyclip", "florence2")

    fun clipCandidate(template: String, label: String): String = when {
        template.isBlank() -> "a photo of $label"
        "{label}" in template -> template.replace("{label}", label)
        else -> "${template.trim()} $label"
    }
}
