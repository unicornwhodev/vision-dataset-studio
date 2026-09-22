package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
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
    "rtmdet_tiny" to QualificationStatus.INFERENCE_ONLY,
    "hgnetv2_b0" to QualificationStatus.INFERENCE_ONLY,
    "repvit_m1_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "tinyclip_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "efficientformer_l1_learning" to QualificationStatus.TRAINING_QUALIFIED,
    "dinov2" to QualificationStatus.INFERENCE_ONLY,
    "tinyclip" to QualificationStatus.INFERENCE_ONLY
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
    return ModelCapabilities(values,QualificationStatus.UNTESTED)
}

/**
 * Catalogue hosted by the project, but weights stay opt-in downloads from Hugging Face.
 * Availability is discovered from the remote tree; this file does not pretend unpublished conversions exist.
 */
object CommunityModelCatalog {
    val cocoSlots = listOf("background","person","bicycle","car","motorcycle","airplane","bus","train","truck","boat","traffic light","fire hydrant","unused_12","stop sign","parking meter","bench","bird","cat","dog","horse","sheep","cow","elephant","bear","zebra","giraffe","unused_26","backpack","umbrella","unused_29","unused_30","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball","kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket","bottle","unused_45","wine glass","cup","fork","knife","spoon","bowl","banana","apple","sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair","couch","potted plant","bed","unused_66","dining table","unused_68","unused_69","toilet","unused_71","tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven","toaster","sink","refrigerator","unused_83","book","clock","vase","scissors","teddy bear","hair drier","toothbrush")
    const val repoId = "Charlbi/Lite_rt_prepared_for_android_dataset_builder"
    // The second revision changes documentation only: all 118 runtime artifact hashes were
    // reverified in test-results/functional-audit-20260922/public-revision-equivalence.json.
    private val qualifiedRevisions=setOf("1244117f490e36ce321d70baa672753caeaef028","36026262693de56b2cf45a6337a405297bfcfff6")
    private fun qualification(repository:String,revision:String,id:String)=
        if(repository==repoId && revision in qualifiedRevisions)
            executedQualifications[id] ?: QualificationStatus.UNTESTED else QualificationStatus.UNTESTED

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

    val entries get() = listOf(
        Entry("tinyclip", "TinyCLIP ViT-8M/16", tr("Embeddings image-texte, tags zero-shot et similarité", "Image-text embeddings, zero-shot tags and similarity"), "MIT", listOf("image_encoder.tflite", "text_encoder.tflite"), "bundle", "Vision-language", ModelCapabilities(setOf(ModelCapability.EMBEDDING, ModelCapability.SIMILARITY, ModelCapability.CLASSIFICATION), QualificationStatus.UNTESTED)),
        Entry("dinov2", "DINOv2 Small", tr("Embeddings visuels, clustering, doublons et active learning", "Visual embeddings, clustering, duplicates and active learning"), "Apache-2.0", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding"), ModelCapabilities(setOf(ModelCapability.EMBEDDING, ModelCapability.SIMILARITY), QualificationStatus.UNTESTED)),
        Entry("vitpose", "ViTPose+ Small", tr("Heatmaps de points-clés humains", "Human keypoint heatmaps"), "Apache-2.0", listOf("model.tflite"), "heatmap", "Keypoints", ModelCapabilities(setOf(ModelCapability.POINTING), QualificationStatus.UNTESTED)),
        Entry("efficientvit_sam", "EfficientViT-SAM L0", tr("Segmentation interactive guidée par point ou boîte", "Interactive segmentation guided by a point or box"), "Apache-2.0", listOf("image_encoder.tflite", "decoder_point.tflite", "decoder_box.tflite"), "bundle", "Segmentation"),
        Entry("rfdetr", "RF-DETR Base", tr("Détection d’objets", "Object detection"), "Apache-2.0", listOf("model.tflite"), "rfdetr", tr("Détection", "Detection"), ModelCapabilities(setOf(ModelCapability.DETECTION), QualificationStatus.UNTESTED)),
        Entry("florence2", "Florence-2 Base", tr("Captioning, OCR, grounding et tâches VL", "Captioning, OCR, grounding and VL tasks"), "MIT", listOf("image_encoder.tflite", "multimodal_encoder.tflite", "decoder.tflite"), "bundle", "Vision-language"),
        Entry("grounding_dino_base", "Grounding DINO Base", tr("Détection open-vocabulary guidée par texte", "Text-guided open-vocabulary detection"), "Apache-2.0", listOf("model.tflite"), "multi_input", "Open-vocabulary"),
        Entry("owlv2_base_patch16", "OWLv2 Base Patch16", tr("Détection zero-shot guidée par texte", "Text-guided zero-shot detection"), "Apache-2.0", listOf("model.tflite"), "multi_input", "Open-vocabulary"),
        Entry("depth_anything_v2_small", "Depth Anything V2 Small", tr("Estimation de profondeur monoculaire", "Monocular depth estimation"), "Apache-2.0", listOf("model.tflite"), "inspect", tr("Profondeur", "Depth")),
        Entry("rtmdet_tiny", "RTMDet Tiny", tr("Détection d’objets légère", "Lightweight object detection"), "Apache-2.0", listOf("model.tflite"), "rtmdet", tr("Détection", "Detection"), ModelCapabilities(setOf(ModelCapability.DETECTION), QualificationStatus.UNTESTED)),
        Entry("efficientformer_l1", "EfficientFormer L1", tr("Classification et embeddings visuels", "Classification and visual embeddings"), "Apache-2.0", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("repvit_m1", "RepViT M1", tr("Classification et embeddings visuels mobiles", "Mobile classification and visual embeddings"), "Apache-2.0", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("edgenext_xx_small", "EdgeNeXt XX-Small", tr("Classification et embeddings visuels compacts", "Compact classification and visual embeddings"), "MIT", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("edgenext_x_small", "EdgeNeXt X-Small", tr("Classification et embeddings visuels compacts", "Compact classification and visual embeddings"), "MIT", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("edgenext_small_usi", "EdgeNeXt Small USI", tr("Embeddings visuels et classification", "Visual embeddings and classification"), "MIT", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("hgnetv2_b0", "HGNetV2 B0", tr("Embeddings visuels et classification", "Visual embeddings and classification"), "Apache-2.0", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("caformer_s18", "CAFormer S18", tr("Embeddings visuels hybrides", "Hybrid visual embeddings"), "Apache-2.0", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("convformer_s18", "ConvFormer S18", tr("Embeddings visuels convolutionnels", "Convolutional visual embeddings"), "Apache-2.0", listOf("model.tflite"), "embedding", tr("Représentation", "Embedding")),
        Entry("picodet_layout_1x", "PicoDet Layout 1x", tr("Détection de régions de documents", "Document region detection"), "Apache-2.0", listOf("model.tflite"), "inspect", "Document"),
        Entry("picodet_l_layout_3cls", "PicoDet-L Layout 3cls", tr("Détection image/table/table-rotated", "Image/table/table-rotated detection"), "Apache-2.0", listOf("model.tflite"), "inspect", "Document"),
        Entry("table_transformer_detection", "Table Transformer Detection", tr("Détection de tableaux", "Table detection"), "MIT", listOf("model.tflite"), "inspect", "Document"),
        Entry("table_transformer_structure", "Table Transformer Structure", tr("Reconnaissance de structure de tableaux", "Table structure recognition"), "MIT", listOf("model.tflite"), "inspect", "Document")
    )

    data class Source(val repository: String = repoId, val revision: String = "main", val folder: String = "models") {
        fun validate() {
            require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*"))) { tr("Dépôt attendu : compte/modèle", "Expected repository: account/model") }
            require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.validRevision(revision))
            require(folder.isEmpty() || com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(folder))
        }
    }

    suspend fun discover(hf: HfApiClient, source: Source = Source()): List<Availability> = withContext(Dispatchers.IO) {
        source.validate()
        val sha = hf.resolveModelRevision(source.repository, source.revision)
        val discovered=fromTree(source, sha, hf.listModelTree(source.repository, sha, source.folder))
        discovered.map { item ->
            val contract=listOf("android_model_config.json","model_config.json","runtime_contract.json").firstNotNullOfOrNull { name ->
                item.files.firstOrNull{it.path.removePrefix(item.sourcePrefix)==name}
            } ?: return@map item
            val temp=File.createTempFile("vds-model-contract-",".json")
            try {
                val result=runCatching {
                    check(hf.downloadModelFile(item.sourceRepo,item.repoSha,contract.path,temp,1024L*1024)){tr("Téléchargement du contrat interrompu", "Contract download interrupted")}
                    val config=if(contract.path.substringAfterLast('/')=="runtime_contract.json") {
                        val metadata=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(temp.readText()) as? Map<*,*> ?: error(tr("Contrat vide", "Empty contract"))
                        if(!RuntimeModelContracts.isDynamicYolo(metadata)) return@map item
                        val labels=item.files.singleOrNull{it.path==item.sourcePrefix+"labels.json"} ?: error(tr("Classes YOLO absentes", "YOLO labels missing"))
                        val labelFile=File(temp.path+".labels")
                        check(hf.downloadModelFile(item.sourceRepo,item.repoSha,labels.path,labelFile,1024L*1024)) { tr("Téléchargement des classes interrompu", "Label download interrupted") }
                        val vocabulary=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(labelFile.readText()) as? Map<*,*> ?: error(tr("Classes YOLO invalides", "Invalid YOLO labels"))
                        RuntimeModelContracts.dynamicYolo(metadata,vocabulary)
                    } else com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(temp.readText()) ?: error(tr("Contrat vide", "Empty contract"))
                    ModelContract.validate(config)
                    val qualification=qualification(item.sourceRepo,item.repoSha,item.entry.id)
                    item.copy(entry=item.entry.copy(capabilities=ModelCapabilities.fromConfig(config,qualification)),note=tr("Contrat validé · essai sur image requis", "Contract validated · image trial required"))
                }
                result.getOrElse{item.copy(installableNow=false,note=tr("Contrat invalide ou inaccessible : ${it.message ?: "erreur inconnue"}", "Invalid or inaccessible contract: ${it.message ?: "unknown error"}"))}
            } finally { temp.delete();File(temp.path+".labels").delete() }
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
            val spec = known ?: Entry(id, id.replace('_', ' '), tr("Conversion LiteRT", "LiteRT conversion"), tr("Voir la model card", "See the model card"), graphs.map { it.path.removePrefix(prefix) }, if (explicit) "contract" else "inspect", tr("Dépôt HF", "HF repository"))
            val installable = graphs.size == 1 || known?.adapterStatus == "bundle"
            val qualified=spec.copy(capabilities=ModelCapabilities(spec.capabilities.values,qualification(source.repository,sha,id)))
            Availability(qualified, sha, files, true, installable, when {
                !installable -> tr("Bundle sans pipeline déclaré", "Bundle has no declared pipeline")
                explicit -> tr("Contrat fourni · compatibilité vérifiée à l’installation, essai sur image requis", "Contract provided · compatibility checked on installation, image trial required")
                graphs.size > 1 -> tr("Pipeline local · essai sur appareil requis", "Local pipeline · device trial required")
                else -> tr("Inspection des tenseurs · aucun label déduit du nom", "Tensor inspection · labels are never inferred from the name")
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
                "florence2" -> common.copy(task="multitask",adapter="florence2",inputWidth=768,inputHeight=768,resizeMode="stretch",prompt="<CAPTION>",captionLanguage="en")
                else -> error(tr("Bundle non pris en charge", "Unsupported bundle"))
            }.also(ModelContract::validate)
        }
        val explicit = listOf("android_model_config.json", "model_config.json").map { File(file.parentFile, it) }.firstOrNull { it.isFile }
        if (explicit != null) {
            val c = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(explicit.readText()) ?: error(tr("Contrat Android invalide", "Invalid Android contract"))
            ModelContract.validate(c)
            if (c.training != null) LiteRtTrainingSession(file, c).use { }
            return c
        }
        Interpreter(file, LiteRtOptions.forFile(file,1)).use { i ->
            require(i.inputTensorCount == 1 || (item.entry.id == "vitpose" && i.inputTensorCount == 2)) { tr("Entrées auxiliaires non décrites pour cette conversion", "Auxiliary inputs not described for this conversion") }
            val dynamicContract=File(file.parentFile,"runtime_contract.json").takeIf{it.isFile}?.let{
                com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(it.readText()) as? Map<*,*>
            }
            if(dynamicContract!=null && RuntimeModelContracts.isDynamicYolo(dynamicContract)) {
                val labelsFile=File(file.parentFile,"labels.json")
                require(labelsFile.isFile){tr("Classes YOLO absentes", "YOLO labels missing")}
                val labelMap=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(labelsFile.readText()) as? Map<*,*> ?: error(tr("Classes YOLO invalides", "Invalid YOLO labels"))
                val config=RuntimeModelContracts.dynamicYolo(dynamicContract,labelMap)
                require(i.getInputTensor(0).dataType().name=="FLOAT32" && i.getInputTensor(0).shapeSignature().toList()==listOf(-1,3,-1,-1)) { tr("Entrée YOLO inattendue", "Unexpected YOLO input") }
                require(i.outputTensorCount==1)
                val output=i.getOutputTensor(0).shapeSignature().toList()
                require(output.size==3 && output[1] in setOf(-1,config.labels.size+4)) { tr("Sortie YOLO inattendue : $output", "Unexpected YOLO output: $output") }
                return config
            }
            val dynamic=i.getInputTensor(0).shapeSignature().any{it<0}
            if(dynamic) {
                require(dynamicContract!=null){tr("Dimensions dynamiques : runtime_contract.json requis", "Dynamic dimensions: runtime_contract.json required")}
                require(dynamicContract["task"] in setOf("classification + visual embeddings","object detection"))
                val side=if(item.entry.id=="rtmdet_tiny")320 else 224
                i.resizeInput(0,intArrayOf(1,3,side,side),true);i.allocateTensors()
            }
            val input = i.getInputTensor(0)
            val shape = input.shape().toList()
            require(shape.size == 4 && shape[0] == 1) { tr("Entrée image 4D batch=1 attendue", "Expected 4D image input with batch=1") }
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
                require(sourceConfig.isFile()){tr("Prétraitement upstream absent", "Upstream preprocessing missing")}
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
                        else -> error(tr("Sortie ViTPose inattendue : $out", "Unexpected ViTPose output: $out"))
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
