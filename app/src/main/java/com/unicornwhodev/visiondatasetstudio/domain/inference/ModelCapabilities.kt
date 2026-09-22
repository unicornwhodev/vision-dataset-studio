package com.unicornwhodev.visiondatasetstudio.domain.inference

enum class ModelCapability { DETECTION, POINTING, SEGMENTATION, CLASSIFICATION, CAPTIONING, VQA, COUNTING, GROUNDING, EMBEDDING, SIMILARITY, INTERACTIVE_SEGMENTATION, TRAINING, INSPECTION_ONLY }
enum class ModelAction { PREANNOTATE, PREANNOTATE_PARTIALLY, COMPUTE_REPRESENTATION, INTERACTIVE_SEGMENTATION, INSPECT, NONE }
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

    fun supports(activeTasksCsv: String): Boolean = values.any { capability ->
        capability.name in activeTasksCsv.split(',').map(String::trim).map { if(it=="POINTING_MULTI") "POINTING" else it }
    }

    companion object {
        private val annotationCapabilities = setOf(ModelCapability.DETECTION, ModelCapability.POINTING, ModelCapability.SEGMENTATION, ModelCapability.CLASSIFICATION, ModelCapability.CAPTIONING,ModelCapability.VQA,ModelCapability.COUNTING,ModelCapability.GROUNDING)
        /** Theoretical capabilities come from the parsed contract; qualification remains independent evidence. */
        fun fromConfig(config:ModelConfig,qualification:QualificationStatus=QualificationStatus.UNTESTED):ModelCapabilities {
            val outputs=ModelContract.outputTypes(config)
            val values=buildSet {
                if("box" in outputs)add(ModelCapability.DETECTION)
                if("point" in outputs)add(ModelCapability.POINTING)
                if("mask" in outputs)add(ModelCapability.SEGMENTATION)
                if("tag" in outputs)add(ModelCapability.CLASSIFICATION)
                if("caption" in outputs)add(ModelCapability.CAPTIONING)
                if("vqa" in outputs)add(ModelCapability.VQA)
                if("count" in outputs)add(ModelCapability.COUNTING)
                if("grounding" in outputs)add(ModelCapability.GROUNDING)
                if(ModelContract.adapter(config)=="embedding" || config.embeddingOutputIndex>=0)add(ModelCapability.EMBEDDING)
                if(config.bundleKind=="tinyclip")add(ModelCapability.SIMILARITY)
                if(config.bundleKind=="efficientvit_sam")add(ModelCapability.INTERACTIVE_SEGMENTATION)
                if(config.training!=null)add(ModelCapability.TRAINING)
                if(isEmpty())add(ModelCapability.INSPECTION_ONLY)
            }
            return ModelCapabilities(values,qualification)
        }
        fun action(config:ModelConfig,activeTasksCsv:String):ModelAction {
            val capabilities=fromConfig(config)
            val compatibility=ModelContract.compatibility(config,activeTasksCsv)
            return when {
                capabilities.requiresInteraction -> ModelAction.INTERACTIVE_SEGMENTATION
                compatibility.canRun -> if(compatibility.fullyCovered) ModelAction.PREANNOTATE else ModelAction.PREANNOTATE_PARTIALLY
                capabilities.encoderOnly || ModelCapability.SIMILARITY in capabilities.values -> ModelAction.COMPUTE_REPRESENTATION
                ModelCapability.INSPECTION_ONLY in capabilities.values -> ModelAction.INSPECT
                else -> ModelAction.NONE
            }
        }
    }
}
