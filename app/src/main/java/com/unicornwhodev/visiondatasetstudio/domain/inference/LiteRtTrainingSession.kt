package com.unicornwhodev.visiondatasetstudio.domain.inference

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils

/** Executes the converter's real mutable-variable signatures on this Android device. */
class LiteRtTrainingSession(private val file:File,private val config:ModelConfig):AutoCloseable {
    private val contract=requireNotNull(config.training){"Ce modèle n’expose pas de contrat d’apprentissage"}
    private val flex=org.tensorflow.lite.flex.FlexDelegate()
    private val interpreter=try { Interpreter(file,LiteRtOptions.forFile(file,config.threads).addDelegate(flex)) }
        catch(e:Throwable){flex.close();throw e}
    init {
        try {
        val signatures=interpreter.signatureKeys.toSet()
        require(listOf(contract.trainSignature,contract.inferSignature,contract.saveSignature,contract.restoreSignature).all{it in signatures}){
            "Conversion limitée à l’inférence : signatures train/infer/save/restore manquantes"
        }
        require(contract.inferOutputs.isNotEmpty()){"Ordre des sorties d’inférence absent du contrat"}
        require(interpreter.getSignatureInputs(contract.trainSignature).toSet()==(setOf(contract.imageInput,contract.targetInput)+contract.auxiliaryTargets.keys+(if(contract.learningRateInput.isBlank())emptySet() else setOf(contract.learningRateInput)))) { "Entrées train différentes du contrat" }
        require(interpreter.getSignatureInputs(contract.inferSignature).toSet()==setOf(contract.imageInput)) { "Entrées infer différentes du contrat" }
        require(interpreter.getSignatureInputs(contract.saveSignature).toSet()==setOf(contract.checkpointInput))
        require(interpreter.getSignatureInputs(contract.restoreSignature).toSet()==setOf(contract.checkpointInput))
        val target=interpreter.getInputTensorFromSignature(contract.targetInput,contract.trainSignature)
        require(target.shape().toList()==contract.targetShape && target.dataType().name=="FLOAT32") { "Forme/type des cibles d’apprentissage différents du contrat" }
        require(contract.targetShape.fold(1L){a,b->a*b} in 1..1_000_000)
        contract.auxiliaryTargets.forEach { (name,spec) ->
            val tensor=interpreter.getInputTensorFromSignature(name,contract.trainSignature)
            require(tensor.shape().toList()==spec.shape && tensor.dataType().name=="FLOAT32") { "Cible auxiliaire incompatible : $name" }
        }
        } catch(e:Throwable) { try { interpreter.close() } finally { flex.close() }; throw e }
    }
    fun infer(bitmap:Bitmap):List<TensorValues> {
        val image=LiteRtGraph.image(bitmap,config)
        // Java only resizes signature inputs from shaped arrays, never from flat ByteBuffers.
        // Read dynamic outputs after invocation; their serialized size may still be one element.
        val input=signatureImage(image,contract.inferSignature)
        interpreter.runSignature(mapOf(contract.imageInput to input),contract.inferOutputs.associateWith { null as Any? }.toMutableMap(),contract.inferSignature)
        val bytes=contract.inferOutputs.sumOf { interpreter.getOutputTensorFromSignature(it,contract.inferSignature).numBytes().toLong() }
        require(bytes in 1..64L*1024*1024) { "Sorties d’inférence au-delà du budget mémoire" }
        return contract.inferOutputs.map{name->
            val t=interpreter.getOutputTensorFromSignature(name,contract.inferSignature)
            TensorValues(t.shape().toList(),TensorCodec.decode(t.asReadOnlyBuffer(),t.dataType().name,t.numElements(),t.quantizationParams().scale,t.quantizationParams().zeroPoint)).also{require(it.values.all(Float::isFinite))}
        }
    }
    private fun signatureImage(image:LiteRtGraph.Input,signature:String):Any {
        val tensor=interpreter.getInputTensorFromSignature(contract.imageInput,signature)
        require(tensor.dataType().name==image.type) { "Type image différent du contrat de signature" }
        image.bytes.rewind()
        if(tensor.shape().toList()==image.shape)return image.bytes
        val dynamic=tensor.shapeSignature()
        require(image.type=="FLOAT32" && dynamic.size==image.shape.size && dynamic.indices.all { dynamic[it]<0 || dynamic[it]==image.shape[it] }) {
            "Dimensions image incompatibles avec la signature $signature"
        }
        val array=java.lang.reflect.Array.newInstance(java.lang.Float.TYPE,*image.shape.toIntArray())
        val values=image.bytes.order(ByteOrder.nativeOrder()).asFloatBuffer()
        fun fill(part:Any) {
            if(part is FloatArray)values.get(part)
            else for(i in 0 until java.lang.reflect.Array.getLength(part))fill(java.lang.reflect.Array.get(part,i))
        }
        fill(array);require(!values.hasRemaining());return array
    }
    fun train(bitmap:Bitmap,targets:FloatArray,learningRate:Float,auxiliary:Map<String,FloatArray> = emptyMap()):Float {
        require(learningRate.isFinite() && learningRate in .000001f..1f)
        require(targets.size.toLong()==contract.targetShape.fold(1L){a,b->a*b} && targets.all(Float::isFinite))
        val image=LiteRtGraph.image(bitmap,config)
        val inputs=mutableMapOf<String,Any>(contract.imageInput to signatureImage(image,contract.trainSignature),contract.targetInput to LiteRtGraph.floats(contract.targetShape,targets).bytes)
        require(auxiliary.keys==contract.auxiliaryTargets.keys) { "Supervision auxiliaire absente" }
        auxiliary.forEach { (name,values) ->
            val spec=contract.auxiliaryTargets.getValue(name)
            require(values.all(Float::isFinite) && values.size.toLong()==spec.shape.fold(1L){a,b->a*b})
            inputs[name]=LiteRtGraph.floats(spec.shape,values).bytes
        }
        if(contract.learningRateInput.isNotBlank())inputs[contract.learningRateInput]=LiteRtGraph.floats(emptyList(),floatArrayOf(learningRate)).bytes
        val loss=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        require(interpreter.getOutputTensorFromSignature(contract.lossOutput,contract.trainSignature).numElements()==1)
        interpreter.runSignature(inputs,mutableMapOf<String,Any>(contract.lossOutput to loss),contract.trainSignature)
        return loss.getFloat(0).also{require(it.isFinite()){"Perte d’apprentissage non finie"}}
    }
    fun save(directory:File):CheckpointReceipt {
        require(!directory.exists()){"Un checkpoint existant ne peut pas être écrasé"}
        directory.mkdirs();val prefix=File(directory,"weights")
        interpreter.runSignature(mapOf(contract.checkpointInput to prefix.absolutePath),mutableMapOf(),contract.saveSignature)
        val files=directory.walkTopDown().filter{it.isFile}.toList()
        require(files.isNotEmpty() && files.all{it.length()>0} && files.sumOf{it.length()}<=2L*1024*1024*1024){"La signature save n’a pas produit de checkpoint valide"}
        return CheckpointReceipt(prefix.absolutePath,files.associate{it.relativeTo(directory).invariantSeparatorsPath to HashUtils.computeSha256(it)})
    }
    fun restore(receipt:CheckpointReceipt) {
        val directory=File(receipt.prefix).parentFile!!
        require(receipt.files.isNotEmpty())
        receipt.files.forEach{(path,sha)->
            val f=File(directory,path);require(f.canonicalPath.startsWith(directory.canonicalPath+File.separator))
            require(f.isFile && HashUtils.computeSha256(f)==sha){"Checkpoint absent ou altéré"}
        }
        interpreter.runSignature(mapOf(contract.checkpointInput to receipt.prefix),mutableMapOf(),contract.restoreSignature)
    }
    /** Optional converter probe proves that an internal layer, not only an output, changed. */
    fun weightProbe():String? {
        if(contract.weightProbeSignature.isBlank())return null
        val t=interpreter.getOutputTensorFromSignature(contract.weightProbeOutput,contract.weightProbeSignature)
        require(t.numBytes()<=128*1024*1024)
        val buffer=ByteBuffer.allocateDirect(t.numBytes()).order(ByteOrder.nativeOrder())
        val input=interpreter.getInputTensorFromSignature(contract.weightProbeInput,contract.weightProbeSignature)
        require(input.numElements()==1 && input.dataType().name=="FLOAT32")
        interpreter.runSignature(mapOf(contract.weightProbeInput to LiteRtGraph.floats(emptyList(),floatArrayOf(0f)).bytes),mutableMapOf<String,Any>(contract.weightProbeOutput to buffer),contract.weightProbeSignature)
        buffer.rewind();val bytes=ByteArray(buffer.remaining());buffer.get(bytes)
        return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    }
    override fun close(){try{interpreter.close()}finally{flex.close()}}
}

@com.squareup.moshi.JsonClass(generateAdapter=true)
data class CheckpointReceipt(val prefix:String,val files:Map<String,String>)
