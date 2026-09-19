package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem
import org.tensorflow.lite.Interpreter
import java.io.File

/**
 * Catalogue hosted by the project, but weights stay opt-in downloads from Hugging Face.
 * Availability is discovered from the remote tree; this file does not pretend unpublished conversions exist.
 */
object CommunityModelCatalog {
    const val repoId = "Charlbi/Lite_rt_prepared_for_android_dataset_builder"

    data class Entry(
        val id: String,
        val title: String,
        val purpose: String,
        val upstreamLicense: String,
        val expectedFiles: List<String>,
        val adapterStatus: String,
        val accent: String
    )
    data class Availability(
        val entry: Entry,
        val repoSha: String,
        val files: List<HfTreeItem>,
        val available: Boolean,
        val installableNow: Boolean,
        val note: String
    )

    val entries = listOf(
        Entry("tinyclip", "TinyCLIP ViT-8M/16", "Embeddings image-texte, tags zero-shot et similarité", "MIT", listOf("image_encoder.tflite", "text_encoder.tflite"), "bundle", "Vision-language"),
        Entry("dinov2", "DINOv2 Small", "Embeddings visuels, clustering, doublons et active learning", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("vitpose", "ViTPose+ Small", "Heatmaps de points-clés humains", "Apache-2.0", listOf("model.tflite"), "heatmap", "Keypoints"),
        Entry("efficientvit_sam", "EfficientViT-SAM L0", "Segmentation interactive guidée par point ou boîte", "Apache-2.0", listOf("image_encoder.tflite", "decoder_point.tflite", "decoder_box.tflite"), "bundle", "Segmentation"),
        Entry("rfdetr", "RF-DETR Base", "Détection d’objets", "Apache-2.0", listOf("model.tflite"), "inspect", "Détection"),
        Entry("florence2", "Florence-2 Base", "Captioning, OCR, grounding et tâches VL", "MIT", listOf("image_encoder.tflite", "multimodal_encoder.tflite", "decoder.tflite"), "bundle", "Vision-language"),
        Entry("grounding_dino_base", "Grounding DINO Base", "Détection open-vocabulary guidée par texte", "Apache-2.0", listOf("model.tflite"), "multi_input", "Open-vocabulary"),
        Entry("owlv2_base_patch16", "OWLv2 Base Patch16", "Détection zero-shot guidée par texte", "Apache-2.0", listOf("model.tflite"), "multi_input", "Open-vocabulary"),
        Entry("depth_anything_v2_small", "Depth Anything V2 Small", "Estimation de profondeur monoculaire", "Apache-2.0", listOf("model.tflite"), "inspect", "Profondeur"),
        Entry("rtmdet_tiny", "RTMDet Tiny", "Détection d’objets légère", "Apache-2.0", listOf("model.tflite"), "inspect", "Détection"),
        Entry("efficientformer_l1", "EfficientFormer L1", "Classification et embeddings visuels", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("repvit_m1", "RepViT M1", "Classification et embeddings visuels mobiles", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("edgenext_xx_small", "EdgeNeXt XX-Small", "Classification et embeddings visuels compacts", "MIT", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("edgenext_x_small", "EdgeNeXt X-Small", "Classification et embeddings visuels compacts", "MIT", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("edgenext_small_usi", "EdgeNeXt Small USI", "Embeddings visuels et classification", "MIT", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("hgnetv2_b0", "HGNetV2 B0", "Embeddings visuels et classification", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("caformer_s18", "CAFormer S18", "Embeddings visuels hybrides", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("convformer_s18", "ConvFormer S18", "Embeddings visuels convolutionnels", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation"),
        Entry("picodet_layout_1x", "PicoDet Layout 1x", "Détection de régions de documents", "Apache-2.0", listOf("model.tflite"), "inspect", "Document"),
        Entry("picodet_l_layout_3cls", "PicoDet-L Layout 3cls", "Détection image/table/table-rotated", "Apache-2.0", listOf("model.tflite"), "inspect", "Document"),
        Entry("table_transformer_detection", "Table Transformer Detection", "Détection de tableaux", "MIT", listOf("model.tflite"), "inspect", "Document"),
        Entry("table_transformer_structure", "Table Transformer Structure", "Reconnaissance de structure de tableaux", "MIT", listOf("model.tflite"), "inspect", "Document")
    )

    suspend fun discover(hf: HfApiClient): List<Availability> {
        val sha = hf.resolveModelRevision(repoId, "main")
        val tree = hf.listModelTree(repoId, sha, "models")
        val result = entries.map { spec ->
            val prefix = "models/${spec.id}/"
            val files = tree.filter { it.type != "directory" && it.path.startsWith(prefix) }
            val names = files.map { it.path.removePrefix(prefix) }.toSet()
            val available = spec.expectedFiles.all(names::contains)
            val installable = available && spec.expectedFiles.size == 1 && spec.adapterStatus in setOf("embedding", "heatmap", "inspect")
            Availability(spec, sha, files, available, installable, when {
                !available -> "Conversion non disponible dans le dépôt pour le moment"
                spec.expectedFiles.size > 1 -> "Pack multi-fichiers détecté · runtime bundle à intégrer avant activation"
                spec.adapterStatus == "inspect" -> "Téléchargeable pour inspection · contrat de sortie à confirmer avant préannotation"
                spec.adapterStatus == "embedding" -> "Encodeur utilisable pour inspection/représentation · aucune annotation n’est inventée sans tête adaptée"
                else -> "Téléchargeable et contrat initial proposé automatiquement"
            })
        }
        // Surface new single-file conversions added to the repository without requiring an app release.
        val known = entries.map { it.id }.toSet()
        val folders = tree.mapNotNull { item ->
            val rest = item.path.removePrefix("models/")
            rest.substringBefore('/').takeIf { rest.contains('/') && it.isNotBlank() }
        }.distinct().filterNot(known::contains)
        val dynamic = folders.map { id ->
            val prefix = "models/$id/"
            val files = tree.filter { it.type != "directory" && it.path.startsWith(prefix) }
            val tflites = files.filter { it.path.endsWith(".tflite", true) }
            val e = Entry(id, id.replace('_',' ').replaceFirstChar { it.uppercase() }, "Conversion LiteRT publiée dans le catalogue", "Voir model card", tflites.map { it.path.removePrefix(prefix) }, "inspect", "Nouveau")
            Availability(e, sha, files, tflites.isNotEmpty(), tflites.size == 1, if (tflites.size == 1) "Nouveau modèle · téléchargement pour inspection" else "Bundle ou conversion sans fichier LiteRT unique")
        }
        return result + dynamic
    }

    /** Build only contracts we can justify from the serialized tensor shapes. Unknown layouts stay inspect-only. */
    fun suggestedConfig(item: Availability, file: File): ModelConfig {
        Interpreter(file, Interpreter.Options().setNumThreads(1)).use { i ->
            require(i.inputTensorCount == 1) { "Le runtime intégré exige actuellement une entrée image unique" }
            val input = i.getInputTensor(0)
            val shape = input.shape().toList()
            require(shape.size == 4 && shape[0] == 1) { "Entrée image 4D batch=1 attendue" }
            val nchw = shape[1] in setOf(1,3)
            val channels = if (nchw) shape[1] else shape[3]
            require(channels in setOf(1,3))
            val height = if (nchw) shape[2] else shape[1]
            val width = if (nchw) shape[3] else shape[2]
            val common = ModelConfig(
                task = "inspection", adapter = "inspect_only", inputWidth = width, inputHeight = height, inputChannels = channels,
                inputType = input.dataType().name, inputLayout = if (nchw) "NCHW" else "NHWC", resizeMode = "center_crop",
                mean = 0f, std = 1f, channelMean = if (channels == 3) listOf(123.675f,116.28f,103.53f) else emptyList(),
                channelStd = if (channels == 3) listOf(58.395f,57.12f,57.375f) else emptyList(), threshold = .1f
            )
            return when(item.entry.id) {
                "dinov2" -> common.copy(task="embedding", adapter="embedding", outputIndex=0)
                "vitpose" -> {
                    require(i.outputTensorCount >= 1)
                    val out = i.getOutputTensor(0).shape().toList()
                    val labels = listOf("nose","left_eye","right_eye","left_ear","right_ear","left_shoulder","right_shoulder","left_elbow","right_elbow","left_wrist","right_wrist","left_hip","right_hip","left_knee","right_knee","left_ankle","right_ankle")
                    val layout = when {
                        out.size == 4 && out[1] == labels.size -> "NCHW"
                        out.size == 4 && out[3] == labels.size -> "NHWC"
                        else -> error("Sortie ViTPose inattendue : $out")
                    }
                    common.copy(task="pointing",adapter="heatmap",labels=labels,outputIndex=0,outputLayout=layout,scoreActivation="none",threshold=.05f,resizeMode="stretch")
                }
                else -> common
            }.also(ModelContract::validate)
        }
    }
}
