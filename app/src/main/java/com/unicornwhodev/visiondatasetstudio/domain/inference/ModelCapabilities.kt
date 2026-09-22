package com.unicornwhodev.visiondatasetstudio.domain.inference

enum class ModelCapability { DETECTION, POINTING, SEGMENTATION, CLASSIFICATION, CAPTIONING, EMBEDDING, SIMILARITY, INTERACTIVE_SEGMENTATION, TRAINING, INSPECTION_ONLY }
enum class QualificationStatus { QUALIFIED, PARTIALLY_QUALIFIED, INFERENCE_ONLY, TRAINING_QUALIFIED, FAILED, UNTESTED }

data class ModelCapabilities(
    val values: Set<ModelCapability>,
    val qualification: QualificationStatus,
    val experimental: Boolean = qualification != QualificationStatus.QUALIFIED && qualification != QualificationStatus.TRAINING_QUALIFIED
) {
    val producesAnnotations get() = values.any { it in annotationCapabilities }
    val requiresInteraction get() = ModelCapability.INTERACTIVE_SEGMENTATION in values
    val encoderOnly get() = ModelCapability.EMBEDDING in values && !producesAnnotations
    val trainable get() = ModelCapability.TRAINING in values

    fun supports(activeTasksCsv: String): Boolean {
        val tasks = activeTasksCsv.split(',').map(String::trim).map(String::uppercase).toSet()
        return ("DETECTION" in tasks && ModelCapability.DETECTION in values) ||
            ("POINTING" in tasks && ModelCapability.POINTING in values) ||
            ("SEGMENTATION" in tasks && ModelCapability.SEGMENTATION in values) ||
            ("CLASSIFICATION" in tasks && ModelCapability.CLASSIFICATION in values) ||
            ("CAPTIONING" in tasks && ModelCapability.CAPTIONING in values)
    }

    companion object {
        private val annotationCapabilities = setOf(ModelCapability.DETECTION, ModelCapability.POINTING, ModelCapability.SEGMENTATION, ModelCapability.CLASSIFICATION, ModelCapability.CAPTIONING)
    }
}
