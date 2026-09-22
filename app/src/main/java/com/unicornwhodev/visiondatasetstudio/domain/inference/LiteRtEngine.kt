package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import android.graphics.*
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Interpreter CPU backend. CompiledModel/GPU/NPU are not advertised as tested integrations. */
class LiteRtEngine : AutoCloseable {
    var lastResult:InferenceResult?=null
        private set
    var lastError: String? = null
        private set
    var lastNativeDurationNanos:Long?=null
        private set
    var lastEmbedding: FloatArray? = null
        private set
    var lastPatches: TensorValues? = null
        private set
    var lastTransform: InputTransform? = null
        private set
    var embeddingSpaceHash: String = ""
        private set
    private var bundle:LiteRtBundle?=null
    var lastNote:String=""
        private set
    val lastMask:Bitmap? get()=bundle?.mask
    private var trainingSession:LiteRtTrainingSession?=null
    private var trainingConfig:ModelConfig?=null
    private var interpreter: Interpreter?=null
    private var currentModelPath: String?=null
    private var modelHash=""
    private var originalModelHash=""
    private var currentThreads=2
    private var lastInputShape:List<Int> = emptyList()
    private var lastInputDtype=""
    private var lastOutputShapes:List<List<Int>> = emptyList()
    private var lastOutputIndices:List<Int> = emptyList()
    private var lastOutputDtypes:List<String> = emptyList()
    private val lock=Any()
    private val localClient=LocalModelClient()
    fun loadModel(file: File, threads: Int=2):Boolean = synchronized(lock) {
        lastError=null
        try {
            require(file.isFile && file.length()>8 && threads in 1..8)
            if(currentModelPath==file.absolutePath && interpreter!=null && currentThreads==threads) return@synchronized true
            close()
            if(file.extension=="json")bundle=LiteRtBundle(file) else interpreter=Interpreter(file,LiteRtOptions.forFile(file,threads).setCancellable(true))
            currentModelPath=file.absolutePath;currentThreads=threads;modelHash=HashUtils.computeSha256(file);originalModelHash=modelHash
            true
        } catch(e:Exception) { lastError=e.message ?: tr("Modèle non chargeable", "Model could not be loaded");close();false }
    }
    fun tensorReport():String = synchronized(lock) {
        bundle?.let{return@synchronized tr("Bundle ${it.manifest.kind} · ${it.manifest.revision} · ${it.manifest.files.size} fichiers vérifiés", "Bundle ${it.manifest.kind} · ${it.manifest.revision} · ${it.manifest.files.size} verified files")}
        val i=interpreter ?: error(tr("Aucun modèle chargé", "No model loaded"))
        buildString {
            appendLine("Runtime CPU · $currentThreads threads · SHA-256 $modelHash")
            appendLine("Signatures : ${i.signatureKeys.joinToString()}")
            for(n in 0 until i.inputTensorCount) { val t=i.getInputTensor(n);appendLine("input[$n] ${t.name()} ${t.shape().toList()} ${t.dataType()} q=${t.quantizationParams().scale}/${t.quantizationParams().zeroPoint}") }
            for(n in 0 until i.outputTensorCount) { val t=i.getOutputTensor(n);appendLine("output[$n] ${t.name()} ${t.shape().toList()} ${t.dataType()} q=${t.quantizationParams().scale}/${t.quantizationParams().zeroPoint}") }
        }
    }
    suspend fun runInference(bitmap:Bitmap,config:ModelConfig):InferenceResult {
        val proposals = runInferenceProposals(bitmap, config)
        val configHash=java.security.MessageDigest.getInstance("SHA-256").digest(config.toString().toByteArray()).joinToString(""){"%02x".format(it)}
        val diagnostics=InferenceDiagnostics(modelHash,ModelContract.adapter(config),config.task,
            lastInputShape.ifEmpty { if(config.inputLayout=="NHWC")listOf(1,config.inputHeight,config.inputWidth,config.inputChannels)else listOf(1,config.inputChannels,config.inputHeight,config.inputWidth) },
            outputShapes=lastOutputShapes,threshold=config.threshold,proposalCount=proposals.size,
            emptyReason=if(proposals.isEmpty() && lastError==null) (lastNote.ifBlank { tr("Aucune proposition au-dessus du seuil", "No proposals above the threshold") }) else null,error=lastError,
            inputLayout=config.inputLayout,inputDtype=lastInputDtype.ifBlank{config.inputType},outputIndices=lastOutputIndices,
            outputDtypes=lastOutputDtypes,nativeDurationNanos=lastNativeDurationNanos,configSha256=configHash,
            runtime=config.runtime,outputTypes=proposals.map{it.type}.distinct(),bundleType=config.bundleKind.takeIf(String::isNotBlank),
            executedComponents=when(config.bundleKind){"tinyclip"->listOf("image_encoder","text_encoder");"efficientvit_sam"->listOf("image_encoder",if(config.promptPoint.size==2)"decoder_point" else "decoder_box");"florence2"->listOf("image_encoder","multimodal_encoder","decoder");else->emptyList()},
            endpoint=config.endpoint.takeIf{config.runtime=="local_http"}?.let{java.net.URI(it).let{uri->"${uri.scheme}://${uri.host}:${uri.port}${uri.path}"}})
        return when {
            lastError!=null -> InferenceResult.Failure(requireNotNull(lastError),diagnostics)
            proposals.isEmpty() -> InferenceResult.Empty(diagnostics.emptyReason!!,diagnostics)
            else -> InferenceResult.Success(proposals,diagnostics)
        }.also{lastResult=it}
    }
    private suspend fun runInferenceProposals(bitmap:Bitmap,config:ModelConfig):List<ModelProposal> = withContext(Dispatchers.Default) {
        lastError=null;lastNativeDurationNanos=null;lastNote="";lastEmbedding=null;lastPatches=null;lastTransform=null
        lastInputShape=emptyList();lastInputDtype="";lastOutputShapes=emptyList();lastOutputIndices=emptyList();lastOutputDtypes=emptyList()
        val started=System.nanoTime()
        try {
            ModelContract.validate(config)
            embeddingSpaceHash=AdaptiveCorrection.hash(modelHash+config.toString())
            if(config.runtime=="local_http") return@withContext localClient.run(bitmap,config).also{lastNativeDurationNanos=System.nanoTime()-started;lastInputShape=listOf(bitmap.height,bitmap.width,3);lastInputDtype="image"}
            if(config.bundleKind.isNotBlank()) {
                val runtime=requireNotNull(bundle){tr("Bundle non chargé", "Bundle not loaded")};require(runtime.manifest.kind==config.bundleKind)
                val result=runtime.run(bitmap,config);lastEmbedding=runtime.embedding;lastNote=runtime.note
                lastNativeDurationNanos=System.nanoTime()-started;lastInputShape=listOf(1,config.inputHeight,config.inputWidth,config.inputChannels);lastInputDtype=config.inputType
                lastOutputDtypes=result.map{"proposal:${it.type}"}.distinct()
                return@withContext result.map{it.copy(source="model_litert:$modelHash:${config.bundleKind}")}
            }
            synchronized(lock) {
                if(config.training!=null) {
                    if(trainingSession==null || trainingConfig!=config) {
                        val file=File(requireNotNull(currentModelPath))
                        trainingSession?.close();trainingSession=null;trainingConfig=null
                        interpreter?.close();interpreter=null
                        val session=LiteRtTrainingSession(file,config)
                        try {
                            modelHash=originalModelHash
                            if(config.trainingCheckpoint.isNotBlank()) {
                                val receipt=com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining.readCheckpoint(file,config.trainingCheckpoint)
                                session.restore(receipt)
                                modelHash=AdaptiveCorrection.hash(originalModelHash+receipt.files.toSortedMap().toString())
                            }
                            trainingSession=session;trainingConfig=config
                        } catch(e:Throwable) { session.close();throw e }
                    }
                    val tensors=trainingSession!!.infer(bitmap).mapIndexed{i,v->i to v}.toMap()
                    lastInputShape=if(config.inputLayout=="NHWC")listOf(1,config.inputHeight,config.inputWidth,config.inputChannels)else listOf(1,config.inputChannels,config.inputHeight,config.inputWidth)
                    lastInputDtype=config.inputType;lastOutputIndices=tensors.keys.sorted();lastOutputShapes=lastOutputIndices.map{tensors.getValue(it).shape};lastOutputDtypes=lastOutputIndices.map{"FLOAT32"};lastNativeDurationNanos=System.nanoTime()-started
                    val transform=InputTransform.create(bitmap.width,bitmap.height,config.inputWidth,config.inputHeight,ModelContract.resize(config),config.cropFraction)
                    return@synchronized ModelAdapters.decode(tensors,config.signatureConfig(),transform).map{it.copy(source="model_litert:$modelHash:trained")}
                }
                val i=interpreter ?: error(tr("Aucun modèle chargé", "No model loaded"))
                require(i.inputTensorCount==1+config.extraIntInputs.size) { tr("Nombre d’entrées différent du contrat", "Input count differs from the contract") }
                val expected=if(config.inputLayout=="NHWC") intArrayOf(1,config.inputHeight,config.inputWidth,config.inputChannels) else intArrayOf(1,config.inputChannels,config.inputHeight,config.inputWidth)
                if(!i.getInputTensor(0).shape().contentEquals(expected)) {
                    require(config.inputWidth in config.dynamicMinSize..config.dynamicMaxSize && config.inputHeight in config.dynamicMinSize..config.dynamicMaxSize)
                    require(config.inputWidth%config.dynamicStride==0 && config.inputHeight%config.dynamicStride==0)
                    // Defer allocation to invocation. The Java wrapper refreshes dynamic output
                    // shapes after run only when it performed that allocation itself.
                    i.resizeInput(0,expected,true)
                }
                val input=i.getInputTensor(0)
                require(input.shape().contentEquals(expected) && input.dataType().name==config.inputType) { tr("Forme/type d’entrée différents du contrat. Inspectez les tenseurs.", "Input shape/type differs from the contract. Inspect the tensors.") }
                lastInputShape=input.shape().toList();lastInputDtype=input.dataType().name
                val t=InputTransform.create(bitmap.width,bitmap.height,config.inputWidth,config.inputHeight,ModelContract.resize(config),config.cropFraction)
                lastTransform=t
                val fitted=Bitmap.createBitmap(config.inputWidth,config.inputHeight,Bitmap.Config.ARGB_8888)
                val pixels=IntArray(config.inputWidth*config.inputHeight)
                try {
                    val canvas=Canvas(fitted);canvas.drawColor(Color.rgb(config.padValue,config.padValue,config.padValue))
                    canvas.drawBitmap(bitmap,null,Rect(t.left,t.top,t.left+t.fittedWidth,t.top+t.fittedHeight),Paint(Paint.FILTER_BITMAP_FLAG))
                    fitted.getPixels(pixels,0,config.inputWidth,0,0,config.inputWidth,config.inputHeight)
                } finally { fitted.recycle() }
                val buffer=TensorCodec.encode(pixels,config,input.quantizationParams().scale,input.quantizationParams().zeroPoint)
                val baseIndices=when(ModelContract.adapter(config)) {
                    "ssd" -> listOf(config.outputIndexBoxes,config.outputIndexClasses,config.outputIndexScores,config.outputIndexCount).filter{it>=0}
                    "rfdetr" -> listOf(config.outputIndexBoxes,config.outputIndexScores)
                    "rtmdet" -> config.featureStrides.indices.toList()
                    "fireviewer_dinov3_multitask" -> config.namedOutputIndices.values.toList()
                    else -> listOf(config.outputIndex)
                }
                val indices=(baseIndices+listOf(config.embeddingOutputIndex,config.patchOutputIndex).filter{it>=0}).distinct()
                require(indices.distinct().size==indices.size) { tr("Indices de sorties dupliqués", "Duplicate output indices") }
                require(indices.all{it<i.outputTensorCount}) { tr("Sortie demandée absente du modèle", "Requested output missing from the model") }
                val bytes=indices.sumOf{i.getOutputTensor(it).numBytes().toLong()}
                require(bytes<=64L*1024*1024) { tr("Sorties trop volumineuses pour le budget mémoire de l’adaptateur", "Outputs exceed the adapter's memory budget") }
                // Some Flex/dynamic outputs keep a placeholder shape until the first invocation.
                val outputObjects=indices.associateWith { null as Any? }.toMutableMap()
                val inputs=Array<Any>(i.inputTensorCount){index->
                    if(index==0) buffer else {
                        val values=config.extraIntInputs[index.toString()] ?: error(tr("Entrée auxiliaire $index absente", "Auxiliary input $index missing"))
                        val tensor=i.getInputTensor(index)
                        require(tensor.dataType().name=="INT32" && tensor.numElements()==values.size)
                        ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).apply { values.forEach(::putInt);rewind() }
                    }
                }
                i.runForMultipleInputsOutputs(inputs,outputObjects)
                lastNativeDurationNanos=i.lastNativeInferenceDurationNanoseconds
                require(indices.sumOf { i.getOutputTensor(it).numBytes().toLong() } <= 64L*1024*1024) {
                    tr("Sorties trop volumineuses pour le budget mémoire de l’adaptateur", "Outputs exceed the adapter's memory budget")
                }
                val tensors=indices.associateWith { idx ->
                    val out=i.getOutputTensor(idx);val shape=out.shape().toList()
                    TensorValues(shape,TensorCodec.decode(out.asReadOnlyBuffer(),out.dataType().name,out.numElements(),out.quantizationParams().scale,out.quantizationParams().zeroPoint))
                }
                lastOutputIndices=indices
                lastOutputShapes=indices.map{i.getOutputTensor(it).shape().toList()}
                lastOutputDtypes=indices.map{i.getOutputTensor(it).dataType().name}
                val embeddingIndex=if(config.embeddingOutputIndex>=0)config.embeddingOutputIndex else if(ModelContract.adapter(config)=="embedding")config.outputIndex else -1
                if(embeddingIndex>=0)lastEmbedding=tensors.getValue(embeddingIndex).values.copyOf().also { values->
                    require(values.size in 1..8192 && values.all(Float::isFinite))
                    val norm=kotlin.math.sqrt(values.sumOf{it.toDouble()*it}).toFloat()
                    require(norm>1e-9f);values.indices.forEach{values[it]/=norm}
                }
                if(config.patchOutputIndex>=0)lastPatches=tensors.getValue(config.patchOutputIndex)
                val configHash=java.security.MessageDigest.getInstance("SHA-256").digest(config.toString().toByteArray()).joinToString(""){"%02x".format(it)}.take(16)
                ModelAdapters.get(ModelContract.adapter(config)).decode(tensors,config,t).map{it.copy(source="model_litert:$modelHash:$configHash")}
            }
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) { lastError=e.message ?: tr("Inférence échouée", "Inference failed");emptyList() }
    }
    suspend fun dryRun(bitmap:Bitmap,config:ModelConfig):DryRunResult {
        val start=System.nanoTime();val result=runInference(bitmap,config)
        return DryRunResult(result !is InferenceResult.Failure,if(config.runtime=="local_http") tr("HTTP loopback · serveur utilisateur", "HTTP loopback · user-provided server") else "LiteRT Interpreter CPU · $currentThreads threads",(System.nanoTime()-start)/1_000_000,runCatching{result.orThrow()}.getOrDefault(emptyList()),(result as? InferenceResult.Failure)?.error)
    }
    override fun close() = synchronized(lock) {
        bundle?.close();bundle=null;trainingSession?.close();trainingSession=null;trainingConfig=null;interpreter?.close();interpreter=null;currentModelPath=null
    }
}
