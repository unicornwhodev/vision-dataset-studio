package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ModelConfig(
    val task: String = "object_detection", // Output task; see ModelContract for implemented adapters.
    val inputWidth: Int = 300,
    val inputHeight: Int = 300,
    val inputChannels: Int = 3,
    val inputType: String = "FLOAT32", // FLOAT32, UINT8, INT8 (explicit tensor quantization)
    val mean: Float = 127.5f,
    val std: Float = 127.5f,
    val isRgb: Boolean = true,
    val labels: List<String> = emptyList(),
    val threshold: Float = 0.35f,
    val boxFormat: String = "ymin_xmin_ymax_xmax", // "ymin_xmin_ymax_xmax", "xmin_ymin_xmax_ymax" or normalized top-left "xywh"
    val outputIndexBoxes: Int = 0,
    val outputIndexClasses: Int = 1,
    val outputIndexScores: Int = 2,
    val outputIndexCount: Int = 3,
    val adapter: String = "auto",
    val runtime: String = "litert_interpreter",
    val threads: Int = 2,
    val inputLayout: String = "NHWC",
    val resizeMode: String = "auto", // letterbox, stretch, center_crop
    val padValue: Int = 114,
    val channelMean: List<Float> = emptyList(),
    val channelStd: List<Float> = emptyList(),
    val quantizationMode: String = "raw", // raw preserves V2 UINT8. tensor quantizes normalized real values.
    val outputIndex: Int = 0,
    val outputLayout: String = "BCN", // YOLO: BCN or BNC; heatmaps: NHWC or NCHW
    val coordinates: String = "normalized", // normalized or pixels in model-input frame
    val yoloObjectness: Boolean = false,
    val classOffset: Int = 0,
    val scoreActivation: String = "none", // none, sigmoid, softmax
    val nmsIou: Float = 0.45f,
    val maxDetections: Int = 100,
    val topK: Int = 5,
    val outputMode: String = "boxes", // boxes, points, both
    val pointAnchor: String = "center", // center, bottom_center, top_center
    val deriveCounts: Boolean = false,
    val endpoint: String = "http://127.0.0.1:8080/predict",
    val requestTemplate: String = "",
    val responsePath: String = "predictions",
    val httpOutputMode: String = "proposals",
    val httpModel: String = "",
    val prompt: String = "",
    val captionLanguage: String = "fr",
    val httpTimeoutSeconds: Int = 60,
    val schemaVersion: Int = 1
) {
    companion object {
        fun defaultDetectionPreset(labels: List<String> = listOf("person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light")): ModelConfig {
            return ModelConfig(
                task = "object_detection",
                inputWidth = 300,
                inputHeight = 300,
                inputChannels = 3,
                inputType = "FLOAT32",
                mean = 127.5f,
                std = 127.5f,
                labels = labels,
                threshold = 0.40f
            )
        }

        fun defaultClassifierPreset(labels: List<String> = listOf("nature", "vehicle", "urban", "indoor", "portrait", "document")): ModelConfig {
            return ModelConfig(
                task = "classification",
                inputWidth = 224,
                inputHeight = 224,
                inputChannels = 3,
                inputType = "FLOAT32",
                mean = 127.5f,
                std = 127.5f,
                labels = labels,
                threshold = 0.30f
            )
        }
    }
}

data class ModelProposal(
    val type: String, // box, point, tag, caption, vqa, count
    val label: String,
    val score: Float,
    val xmin: Float = 0f,
    val ymin: Float = 0f,
    val xmax: Float = 0f,
    val ymax: Float = 0f,
    val pointX: Float = 0f,
    val pointY: Float = 0f,
    val text: String = "",
    val question: String = "",
    val count: Int = 0,
    val source: String = "model_litert",
    val modelX: Float? = null,
    val modelY: Float? = null,
    val boxWidth: Float = 0f,
    val boxHeight: Float = 0f,
    val correctionGeneration: Int? = null
)

data class DryRunResult(
    val success: Boolean,
    val backend: String,
    val latencyMs: Long,
    val proposals: List<ModelProposal>,
    val error: String? = null
)
