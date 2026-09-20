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
        val signatures=interpreter.signatureKeys.toSet()
        require(listOf(contract.trainSignature,contract.inferSignature,contract.saveSignature,contract.restoreSignature).all{it in signatures}){
            "Conversion limitée à l’inférence : signatures train/infer/save/restore manquantes"
        }
        require(contract.inferOutputs.isNotEmpty()){"Ordre des sorties d’inférence absent du contrat"}
        require(interpreter.getSignatureInputs(contract.trainSignature).toSet()==setOf(contract.imageInput,contract.targetInput)+if(contract.learningRateInput.isBlank())emptySet() else setOf(contract.learningRateInput)) { "Entrées train différentes du contrat" }
        require(interpreter.getSignatureInputs(contract.inferSignature).toSet()==setOf(contract.imageInput)) { "Entrées infer différentes du contrat" }
        require(interpreter.getSignatureInputs(contract.saveSignature).toSet()==setOf(contract.checkpointInput))
        require(interpreter.getSignatureInputs(contract.restoreSignature).toSet()==setOf(contract.checkpointInput))
        val target=interpreter.getInputTensorFromSignature(contract.targetInput,contract.trainSignature)
        require(target.shape().toList()==contract.targetShape && target.dataType().name=="FLOAT32") { "Forme/type des cibles d’apprentissage différents du contrat" }
        require(contract.targetShape.fold(1L){a,b->a*b} in 1..1_000_000)
    }
    fun infer(bitmap:Bitmap):List<TensorValues> {
        val image=LiteRtGraph.image(bitmap,config)
        val expected=interpreter.getInputTensorFromSignature(contract.imageInput,contract.inferSignature)
        require(expected.shape().toList()==image.shape && expected.dataType().name==image.type)
        val outputs=contract.inferOutputs.associateWith { name ->
            val tensor=interpreter.getOutputTensorFromSignature(name,contract.inferSignature)
            require(tensor.numBytes()<=64*1024*1024)
            ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        }
        interpreter.runSignature(mapOf(contract.imageInput to image.bytes),outputs.mapValues{it.value as Any}.toMutableMap(),contract.inferSignature)
        return outputs.map{(name,buffer)->
            val t=interpreter.getOutputTensorFromSignature(name,contract.inferSignature)
            TensorValues(t.shape().toList(),TensorCodec.decode(buffer,t.dataType().name,t.numElements(),t.quantizationParams().scale,t.quantizationParams().zeroPoint)).also{require(it.values.all(Float::isFinite))}
        }
    }
    fun train(bitmap:Bitmap,targets:FloatArray,learningRate:Float):Float {
        require(learningRate.isFinite() && learningRate in .000001f..1f)
        require(targets.size.toLong()==contract.targetShape.fold(1L){a,b->a*b} && targets.all(Float::isFinite))
        val image=LiteRtGraph.image(bitmap,config)
        val expected=interpreter.getInputTensorFromSignature(contract.imageInput,contract.trainSignature)
        require(expected.shape().toList()==image.shape && expected.dataType().name==image.type)
        val inputs=mutableMapOf<String,Any>(contract.imageInput to image.bytes,contract.targetInput to LiteRtGraph.floats(contract.targetShape,targets).bytes)
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
