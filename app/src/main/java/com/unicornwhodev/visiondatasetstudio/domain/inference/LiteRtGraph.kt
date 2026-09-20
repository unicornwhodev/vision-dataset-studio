package com.unicornwhodev.visiondatasetstudio.domain.inference

import android.graphics.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Multi-input graph runner with explicit shape/type contracts and bounded output allocation. */
class LiteRtGraph(file:File,threads:Int=2):AutoCloseable {
    private val interpreter=Interpreter(file,LiteRtOptions.forFile(file,threads))
    data class Input(val shape:List<Int>,val type:String,val bytes:ByteBuffer)
    fun run(inputs:List<Input>):List<TensorValues> {
        require(inputs.size==interpreter.inputTensorCount)
        inputs.forEachIndexed { index,value ->
            val tensor=interpreter.getInputTensor(index)
            require(tensor.dataType().name==value.type)
            if(!tensor.shape().contentEquals(value.shape.toIntArray()))interpreter.resizeInput(index,value.shape.toIntArray(),true)
        }
        interpreter.allocateTensors()
        inputs.forEachIndexed { i,input->require(interpreter.getInputTensor(i).numBytes()==input.bytes.capacity());input.bytes.rewind() }
        val size=(0 until interpreter.outputTensorCount).sumOf{interpreter.getOutputTensor(it).numBytes().toLong()}
        require(size<=128L*1024*1024){"Sorties du bundle au-delà du budget mémoire"}
        val outputs=(0 until interpreter.outputTensorCount).associateWith { null as Any? }.toMutableMap()
        interpreter.runForMultipleInputsOutputs(inputs.map{it.bytes as Any}.toTypedArray(),outputs)
        require((0 until interpreter.outputTensorCount).sumOf{interpreter.getOutputTensor(it).numBytes().toLong()}<=128L*1024*1024){"Sorties du bundle au-delà du budget mémoire"}
        return outputs.keys.map{i->val t=interpreter.getOutputTensor(i);TensorValues(t.shape().toList(),TensorCodec.decode(t.asReadOnlyBuffer(),t.dataType().name,t.numElements(),t.quantizationParams().scale,t.quantizationParams().zeroPoint)).also{require(it.values.all(Float::isFinite))}}
    }
    override fun close()=interpreter.close()
    companion object {
        fun floats(shape:List<Int>,values:FloatArray)=Input(shape,"FLOAT32",ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).apply{asFloatBuffer().put(values)})
        fun ints(shape:List<Int>,values:IntArray)=Input(shape,"INT32",ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).apply{asIntBuffer().put(values)})
        fun tensor(value:TensorValues)=floats(value.shape,value.values)
        fun image(bitmap:Bitmap,c:ModelConfig):Input {
            val t=InputTransform.create(bitmap.width,bitmap.height,c.inputWidth,c.inputHeight,ModelContract.resize(c),c.cropFraction)
            val fitted=Bitmap.createBitmap(c.inputWidth,c.inputHeight,Bitmap.Config.ARGB_8888)
            val pixels=IntArray(c.inputWidth*c.inputHeight)
            try {
                val canvas=Canvas(fitted);canvas.drawColor(Color.rgb(c.padValue,c.padValue,c.padValue))
                canvas.drawBitmap(bitmap,null,Rect(t.left,t.top,t.left+t.fittedWidth,t.top+t.fittedHeight),Paint(Paint.FILTER_BITMAP_FLAG))
                fitted.getPixels(pixels,0,c.inputWidth,0,0,c.inputWidth,c.inputHeight)
            } finally {fitted.recycle()}
            val shape=if(c.inputLayout=="NCHW")listOf(1,c.inputChannels,c.inputHeight,c.inputWidth) else listOf(1,c.inputHeight,c.inputWidth,c.inputChannels)
            return Input(shape,c.inputType,TensorCodec.encode(pixels,c,0f,0))
        }
    }
}
