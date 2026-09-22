package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem
import org.tensorflow.lite.Interpreter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val executedQualifications=mapOf(
    "repvit_m1" to QualificationStatus.INFERENCE_ONLY,
    "edgenext_xx_small_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "edgenext_x_small_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "rtmdet_tiny_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "edgenext_small_usi_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "rtmdet_tiny" to QualificationStatus.FAILED
)
private fun declaredCapabilities(id:String,adapter:String):ModelCapabilities {
    val values=when {
        id=="tinyclip"->setOf(ModelCapability.EMBEDDING,ModelCapability.SIMILARITY,ModelCapability.CLASSIFICATION)
        id=="efficientvit_sam"->setOf(ModelCapability.SEGMENTATION,ModelCapability.INTERACTIVE_SEGMENTATION)
        id=="florence2"->setOf(ModelCapability.CAPTIONING,ModelCapability.DETECTION)
        adapter in setOf("rfdetr","rtmdet")->setOf(ModelCapability.DETECTION)
        adapter=="heatmap"->setOf(ModelCapability.POINTING)
        adapter=="embedding"->setOf(ModelCapability.EMBEDDING,ModelCapability.SIMILARITY)
        else->setOf(ModelCapability.INSPECTION_ONLY)
    }
    // Only campaigns recorded in docs/LITERT_QUALIFICATION.md may enter this table.
    val qualification=executedQualifications[id] ?: QualificationStatus.UNTESTED
    return ModelCapabilities(values,qualification)
}

/**
 * Catalogue hosted by the project, but weights stay opt-in downloads from Hugging Face.
 * Availability is discovered from the remote tree; this file does not pretend unpublished conversions exist.
 */
object CommunityModelCatalog {
    val cocoSlots = listOf("background","person","bicycle","car","motorcycle","airplane","bus","train","truck","boat","traffic light","fire hydrant","unused_12","stop sign","parking meter","bench","bird","cat","dog","horse","sheep","cow","elephant","bear","zebra","giraffe","unused_26","backpack","umbrella","unused_29","unused_30","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball","kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket","bottle","unused_45","wine glass","cup","fork","knife","spoon","bowl","banana","apple","sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair","couch","potted plant","bed","unused_66","dining table","unused_68","unused_69","toilet","unused_71","tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven","toaster","sink","refrigerator","unused_83","book","clock","vase","scissors","teddy bear","hair drier","toothbrush")
    const val repoId = "Charlbi/Lite_rt_prepared_for_android_dataset_builder"

    data class Entry(
        val id: String,
        val title: String,
        val purpose: String,
        val upstreamLicense: String,
        val expectedFiles: List<String>,
        val adapterStatus: String,
        val accent: String,
        val capabilities: ModelCapabilities = declaredCapabilities(id,adapterStatus)
    )
    data class Availability(
        val entry: Entry,
        val repoSha: String,
        val files: List<HfTreeItem>,
        val available: Boolean,
        val installableNow: Boolean,
        val note: String,
        val sourceRepo: String = repoId,
        val sourcePrefix: String = "models/${entry.id}/"
    )

    val entries = listOf(
        Entry("tinyclip", "TinyCLIP ViT-8M/16", "Embeddings image-texte, tags zero-shot et similarité", "MIT", listOf("image_encoder.tflite", "text_encoder.tflite"), "bundle", "Vision-language", ModelCapabilities(setOf(ModelCapability.EMBEDDING, ModelCapability.SIMILARITY, ModelCapability.CLASSIFICATION), QualificationStatus.UNTESTED)),
        Entry("dinov2", "DINOv2 Small", "Embeddings visuels, clustering, doublons et active learning", "Apache-2.0", listOf("model.tflite"), "embedding", "Représentation", ModelCapabilities(setOf(ModelCapability.EMBEDDING, ModelCapability.SIMILARITY), QualificationStatus.UNTESTED)),
        Entry("vitpose", "ViTPose+ Small", "Heatmaps de points-clés humains", "Apache-2.0", listOf("model.tflite"), "heatmap", "Keypoints", ModelCapabilities(setOf(ModelCapability.POINTING), QualificationStatus.UNTESTED)),
        Entry("efficientvit_sam", "EfficientViT-SAM L0", "Segmentation interactive guidée par point ou boîte", "Apache-2.0", listOf("image_encoder.tflite", "decoder_point.tflite", "decoder_box.tflite"), "bundle", "Segmentation"),
        Entry("rfdetr", "RF-DETR Base", "Détection d’objets", "Apache-2.0", listOf("model.tflite"), "rfdetr", "Détection", ModelCapabilities(setOf(ModelCapability.DETECTION), QualificationStatus.UNTESTED)),
        Entry("florence2", "Florence-2 Base", "Captioning, OCR, grounding et tâches VL", "MIT", listOf("image_encoder.tflite", "multimodal_encoder.tflite", "decoder.tflite"), "bundle", "Vision-language"),
        Entry("grounding_dino_base", "Grounding DINO Base", "Détection open-vocabulary guidée par texte", "Apache-2.0", listOf("model.tflite"), "multi_input", "Open-vocabulary"),
        Entry("owlv2_base_patch16", "OWLv2 Base Patch16", "Détection zero-shot guidée par texte", "Apache-2.0", listOf("model.tflite"), "multi_input", "Open-vocabulary"),
        Entry("depth_anything_v2_small", "Depth Anything V2 Small", "Estimation de profondeur monoculaire", "Apache-2.0", listOf("model.tflite"), "inspect", "Profondeur"),
        Entry("rtmdet_tiny", "RTMDet Tiny", "Détection d’objets légère", "Apache-2.0", listOf("model.tflite"), "rtmdet", "Détection", ModelCapabilities(setOf(ModelCapability.DETECTION), QualificationStatus.FAILED)),
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

    data class Source(val repository: String = repoId, val revision: String = "main", val folder: String = "models") {
        fun validate() {
            require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*"))) { "Dépôt attendu : compte/modèle" }
            require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.validRevision(revision))
            require(folder.isEmpty() || com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(folder))
        }
    }

    suspend fun discover(hf: HfApiClient, source: Source = Source()): List<Availability> = withContext(Dispatchers.IO) {
        source.validate()
        val sha = hf.resolveModelRevision(source.repository, source.revision)
        val discovered=fromTree(source, sha, hf.listModelTree(source.repository, sha, source.folder))
        discovered.map { item ->
            val contract=item.files.firstOrNull{it.path.removePrefix(item.sourcePrefix) in setOf("android_model_config.json","model_config.json")}
                ?: return@map item
            val temp=File.createTempFile("vds-model-contract-",".json")
            try {
                val result=runCatching {
                    check(hf.downloadModelFile(item.sourceRepo,item.repoSha,contract.path,temp,1024L*1024)){"Téléchargement du contrat interrompu"}
                    val config=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(temp.readText()) ?: error("Contrat vide")
                    ModelContract.validate(config)
                    val qualification=executedQualifications[item.entry.id] ?: QualificationStatus.UNTESTED
                    item.copy(entry=item.entry.copy(capabilities=ModelCapabilities.fromConfig(config,qualification)),note="Contrat validé · essai sur image requis")
                }
                result.getOrElse{item.copy(installableNow=false,note="Contrat invalide ou inaccessible : ${it.message ?: "erreur inconnue"}")}
            } finally { temp.delete() }
        }
    }

    /** Metadata advertises a download, never a successful inference or training run. */
    fun fromTree(source: Source, sha: String, tree: List<HfTreeItem>): List<Availability> {
        source.validate()
        val root = if (source.folder.isBlank()) "" else source.folder.trimEnd('/') + "/"
        val weights = tree.filter { it.type != "directory" && it.path.startsWith(root) && it.path.endsWith(".tflite", true) }
        return weights.groupBy { it.path.substringBeforeLast('/', "") }.toSortedMap().map { (folder, graphs) ->
            val prefix = if (folder.isBlank()) "" else "$folder/"
            val id = folder.removePrefix(root).ifBlank { source.repository.substringAfter('/') }
            val files = tree.filter { it.type != "directory" && it.path.startsWith(prefix) }
            val names = files.map { it.path.removePrefix(prefix) }.toSet()
            val explicit = "android_model_config.json" in names || "model_config.json" in names
            val known = if (source.repository == repoId) entries.firstOrNull { it.id == id } else null
            val spec = known ?: Entry(id, id.replace('_', ' '), "Conversion LiteRT", "Voir la model card", graphs.map { it.path.removePrefix(prefix) }, if (explicit) "contract" else "inspect", "Dépôt HF")
            val installable = "artifact_manifest.json" in names && (graphs.size == 1 || known?.adapterStatus == "bundle")
            Availability(spec, sha, files, true, installable, when {
                "artifact_manifest.json" !in names -> "Manifeste SHA-256 manquant"
                !installable -> "Bundle sans pipeline déclaré"
                explicit -> "Contrat fourni · compatibilité vérifiée à l’installation, essai sur image requis"
                graphs.size > 1 -> "Pipeline local · essai sur appareil requis"
                else -> "Inspection des tenseurs · aucun label déduit du nom"
            }, source.repository, prefix)
        }
    }

    /** Build only contracts we can justify from the serialized tensor shapes. Unknown layouts stay inspect-only. */
    fun suggestedConfig(item: Availability, file: File): ModelConfig {
        if(file.extension=="json") {
            val common=ModelConfig(inputLayout="NCHW",inputType="FLOAT32",mean=0f,std=1f,
                channelMean=listOf(123.675f,116.28f,103.53f),channelStd=listOf(58.395f,57.12f,57.375f),bundleKind=item.entry.id)
            return when(item.entry.id) {
                "tinyclip" -> common.copy(task="classification",adapter="tinyclip",inputWidth=224,inputHeight=224,resizeMode="center_crop",labels=listOf("object","background"),channelMean=listOf(122.77094f,116.74601f,104.09374f),channelStd=listOf(68.50053f,66.63216f,70.32316f))
                "efficientvit_sam" -> common.copy(task="object_detection",adapter="sam_box",inputWidth=512,inputHeight=512,resizeMode="stretch",labels=listOf("object"))
                "florence2" -> common.copy(task="multitask",adapter="florence2",inputWidth=768,inputHeight=768,resizeMode="stretch",prompt="<CAPTION>")
                else -> error("Bundle non pris en charge")
            }.also(ModelContract::validate)
        }
        val explicit = listOf("android_model_config.json", "model_config.json").map { File(file.parentFile, it) }.firstOrNull { it.isFile }
        if (explicit != null) {
            val c = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(explicit.readText()) ?: error("Contrat Android invalide")
            ModelContract.validate(c)
            if (c.training != null) LiteRtTrainingSession(file, c).use { }
            return c
        }
        Interpreter(file, LiteRtOptions.forFile(file,1)).use { i ->
            require(i.inputTensorCount == 1 || (item.entry.id == "vitpose" && i.inputTensorCount == 2)) { "Entrées auxiliaires non décrites pour cette conversion" }
            val dynamicContract=File(file.parentFile,"runtime_contract.json").takeIf{it.isFile}?.let{
                com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(it.readText()) as? Map<*,*>
            }
            val dynamic=i.getInputTensor(0).shapeSignature().any{it<0}
            if(dynamic) {
                require(dynamicContract!=null){"Dimensions dynamiques : runtime_contract.json requis"}
                require(dynamicContract["task"] in setOf("classification + visual embeddings","object detection"))
                val side=if(item.entry.id=="rtmdet_tiny")320 else 224
                i.resizeInput(0,intArrayOf(1,3,side,side),true);i.allocateTensors()
            }
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
            if(dynamic && dynamicContract!!["task"]=="classification + visual embeddings") {
                val sourceConfig=File(file.parentFile,"config.json")
                require(sourceConfig.isFile()){"Prétraitement upstream absent"}
                val metadata=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(sourceConfig.readText()) as Map<*,*>
                val prep=metadata["pretrained_cfg"] as Map<*,*>
                val bounds=(dynamicContract!!["report"] as Map<*,*>)["spatial_bounds"] as Map<*,*>
                require(i.outputTensorCount>=2 && i.getOutputTensor(1).shape().size==2)
                return common.copy(task="embedding",adapter="embedding",outputIndex=1,embeddingOutputIndex=1,
                    channelMean=(prep["mean"] as List<*>).map{(it as Number).toFloat()*255},channelStd=(prep["std"] as List<*>).map{(it as Number).toFloat()*255},
                    cropFraction=(prep["crop_pct"] as Number).toFloat(),dynamicMinSize=(bounds["min"] as Number).toInt(),dynamicMaxSize=(bounds["max"] as Number).toInt(),dynamicStride=(bounds["stride"] as Number).toInt()).also(ModelContract::validate)
            }
            return when(item.entry.id) {
                "dinov2" -> common.copy(task="embedding", adapter="embedding", outputIndex=0,embeddingOutputIndex=0,patchOutputIndex=1,cropFraction=.875f)
                "vitpose" -> {
                    require(i.outputTensorCount >= 1)
                    val out = i.getOutputTensor(0).shape().toList()
                    val labels = listOf("nose","left_eye","right_eye","left_ear","right_ear","left_shoulder","right_shoulder","left_elbow","right_elbow","left_wrist","right_wrist","left_hip","right_hip","left_knee","right_knee","left_ankle","right_ankle")
                    val layout = when {
                        out.size == 4 && out[1] == labels.size -> "NCHW"
                        out.size == 4 && out[3] == labels.size -> "NHWC"
                        else -> error("Sortie ViTPose inattendue : $out")
                    }
                    common.copy(task="pointing",adapter="heatmap",labels=labels,outputIndex=0,outputLayout=layout,scoreActivation="clamp",threshold=.05f,resizeMode="stretch",extraIntInputs=if(i.inputTensorCount==2)mapOf("1" to listOf(0)) else emptyMap())
                }
                "rtmdet_tiny" -> common.copy(task="object_detection",adapter="rtmdet",inputWidth=320,inputHeight=320,
                    labels=cocoSlots.filterIndexed { index,label -> index!=0 && !label.startsWith("unused_") },isRgb=false,
                    channelMean=listOf(103.53f,116.28f,123.675f),channelStd=listOf(57.375f,57.12f,58.395f),
                    resizeMode="letterbox",outputLayout="NHWC",coordinates="pixels",scoreActivation="sigmoid",dynamicMinSize=32,dynamicMaxSize=640,dynamicStride=32)
                "rfdetr" -> {
                    require(i.outputTensorCount==2 && i.getOutputTensor(0).shape().toList()==listOf(1,300,4) && i.getOutputTensor(1).shape().toList()==listOf(1,300,91))
                    common.copy(task="object_detection",adapter="rfdetr",resizeMode="stretch",labels=cocoSlots,outputIndexBoxes=0,outputIndexScores=1,threshold=.35f)
                }
                else -> common
            }.also(ModelContract::validate)
        }
    }
}
