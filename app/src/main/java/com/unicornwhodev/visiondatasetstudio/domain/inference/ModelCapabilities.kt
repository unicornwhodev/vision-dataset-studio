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
        val tasks = activeTasksCsv.split(',').map(String::trim).filter(String::isNotEmpty).map(String::uppercase).toSet()
        return tasks.isNotEmpty() && tasks.all { task -> when(task) {
            "DETECTION" -> ModelCapability.DETECTION in values
            "POINTING", "POINTING_MULTI" -> ModelCapability.POINTING in values
            "SEGMENTATION" -> ModelCapability.SEGMENTATION in values
            "CLASSIFICATION" -> ModelCapability.CLASSIFICATION in values
            "CAPTIONING" -> ModelCapability.CAPTIONING in values
            else -> false
        } }
    }

    companion object {
        private val annotationCapabilities = setOf(ModelCapability.DETECTION, ModelCapability.POINTING, ModelCapability.SEGMENTATION, ModelCapability.CLASSIFICATION, ModelCapability.CAPTIONING)
        /** Theoretical capabilities come from the parsed contract; qualification remains independent evidence. */
        fun fromConfig(config:ModelConfig,qualification:QualificationStatus=QualificationStatus.UNTESTED):ModelCapabilities {
            val outputs=ModelContract.outputTypes(config)
            val values=buildSet {
                if("box" in outputs)add(ModelCapability.DETECTION)
                if("point" in outputs)add(ModelCapability.POINTING)
                if("mask" in outputs)add(ModelCapability.SEGMENTATION)
                if("tag" in outputs)add(ModelCapability.CLASSIFICATION)
                if("caption" in outputs)add(ModelCapability.CAPTIONING)
                if(ModelContract.adapter(config)=="embedding" || config.embeddingOutputIndex>=0)add(ModelCapability.EMBEDDING)
                if(config.bundleKind=="tinyclip")add(ModelCapability.SIMILARITY)
                if(config.bundleKind=="efficientvit_sam")add(ModelCapability.INTERACTIVE_SEGMENTATION)
                if(config.training!=null)add(ModelCapability.TRAINING)
                if(isEmpty())add(ModelCapability.INSPECTION_ONLY)
            }
            return ModelCapabilities(values,qualification)
        }
    }
}
