package com.unicornwhodev.visiondatasetstudio.domain.inference

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
    var lastError: String? = null
        private set
    var lastNativeDurationNanos:Long?=null
        private set
    private var interpreter: Interpreter?=null
    private var currentModelPath: String?=null
    private var modelHash=""
    private var currentThreads=2
    private val lock=Any()
    private val localClient=LocalModelClient()
    fun loadModel(file: File, threads: Int=2):Boolean = synchronized(lock) {
        lastError=null
        try {
            require(file.isFile && file.length()>8 && threads in 1..8)
            if(currentModelPath==file.absolutePath && interpreter!=null && currentThreads==threads) return@synchronized true
            close()
            interpreter=Interpreter(file,Interpreter.Options().setNumThreads(threads).setCancellable(true))
            currentModelPath=file.absolutePath;currentThreads=threads;modelHash=HashUtils.computeSha256(file)
            true
        } catch(e:Exception) { lastError=e.message ?: "Modèle non chargeable";close();false }
    }
    fun tensorReport():String = synchronized(lock) {
        val i=interpreter ?: error("Aucun modèle chargé")
        buildString {
            appendLine("Runtime CPU · $currentThreads threads · SHA-256 $modelHash")
            for(n in 0 until i.inputTensorCount) { val t=i.getInputTensor(n);appendLine("input[$n] ${t.name()} ${t.shape().toList()} ${t.dataType()} q=${t.quantizationParams().scale}/${t.quantizationParams().zeroPoint}") }
            for(n in 0 until i.outputTensorCount) { val t=i.getOutputTensor(n);appendLine("output[$n] ${t.name()} ${t.shape().toList()} ${t.dataType()} q=${t.quantizationParams().scale}/${t.quantizationParams().zeroPoint}") }
        }
    }
    suspend fun runInference(bitmap:Bitmap,config:ModelConfig):List<ModelProposal> = withContext(Dispatchers.Default) {
        lastError=null;lastNativeDurationNanos=null
        try {
            ModelContract.validate(config)
            if(config.runtime=="local_http") return@withContext localClient.run(bitmap,config)
            synchronized(lock) {
                val i=interpreter ?: error("Aucun modèle chargé")
                require(i.inputTensorCount==1) { "Cet adaptateur prend une seule entrée image; modèle multi-entrée non adapté" }
                val expected=if(config.inputLayout=="NHWC") intArrayOf(1,config.inputHeight,config.inputWidth,config.inputChannels) else intArrayOf(1,config.inputChannels,config.inputHeight,config.inputWidth)
                val input=i.getInputTensor(0)
                require(input.shape().contentEquals(expected) && input.dataType().name==config.inputType) { "Forme/type d’entrée différents du contrat. Inspectez les tenseurs." }
                val t=InputTransform.create(bitmap.width,bitmap.height,config.inputWidth,config.inputHeight,ModelContract.resize(config))
                val fitted=Bitmap.createBitmap(config.inputWidth,config.inputHeight,Bitmap.Config.ARGB_8888)
                val pixels=IntArray(config.inputWidth*config.inputHeight)
                try {
                    val canvas=Canvas(fitted);canvas.drawColor(Color.rgb(config.padValue,config.padValue,config.padValue))
                    canvas.drawBitmap(bitmap,null,Rect(t.left,t.top,t.left+t.fittedWidth,t.top+t.fittedHeight),Paint(Paint.FILTER_BITMAP_FLAG))
                    fitted.getPixels(pixels,0,config.inputWidth,0,0,config.inputWidth,config.inputHeight)
                } finally { fitted.recycle() }
                val buffer=TensorCodec.encode(pixels,config,input.quantizationParams().scale,input.quantizationParams().zeroPoint)
                val indices=if(ModelContract.adapter(config)=="ssd") listOf(config.outputIndexBoxes,config.outputIndexClasses,config.outputIndexScores,config.outputIndexCount).filter{it>=0} else listOf(config.outputIndex)
                require(indices.distinct().size==indices.size) { "Indices de sorties dupliqués" }
                require(indices.all{it<i.outputTensorCount}) { "Sortie demandée absente du modèle" }
                val bytes=indices.sumOf{i.getOutputTensor(it).numBytes().toLong()}
                require(bytes<=64L*1024*1024) { "Sorties trop volumineuses pour le budget mémoire de l’adaptateur" }
                val outputs=indices.associateWith { ByteBuffer.allocateDirect(i.getOutputTensor(it).numBytes()).order(ByteOrder.nativeOrder()) }
                val outputObjects=outputs.mapValues{it.value as Any}.toMutableMap()
                i.runForMultipleInputsOutputs(arrayOf(buffer),outputObjects)
                lastNativeDurationNanos=i.lastNativeInferenceDurationNanoseconds
                val tensors=outputs.mapValues { (idx,buf) ->
                    val out=i.getOutputTensor(idx);val shape=out.shape().toList()
                    TensorValues(shape,TensorCodec.decode(buf,out.dataType().name,shape.fold(1){a,b->a*b},out.quantizationParams().scale,out.quantizationParams().zeroPoint))
                }
                val configHash=java.security.MessageDigest.getInstance("SHA-256").digest(config.toString().toByteArray()).joinToString(""){"%02x".format(it)}.take(16)
                ModelAdapters.get(ModelContract.adapter(config)).decode(tensors,config,t).map{it.copy(source="model_litert:$modelHash:$configHash")}
            }
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) { lastError=e.message ?: "Inférence échouée";emptyList() }
    }
    suspend fun dryRun(bitmap:Bitmap,config:ModelConfig):DryRunResult {
        val start=System.nanoTime();val proposals=runInference(bitmap,config)
        return DryRunResult(lastError==null,if(config.runtime=="local_http") "HTTP loopback · serveur utilisateur" else "LiteRT Interpreter CPU · $currentThreads threads",(System.nanoTime()-start)/1_000_000,proposals,lastError)
    }
    override fun close() = synchronized(lock) {
        interpreter?.close();interpreter=null;currentModelPath=null
    }
}
