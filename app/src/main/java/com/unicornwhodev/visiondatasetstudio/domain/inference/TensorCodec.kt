package com.unicornwhodev.visiondatasetstudio.domain.inference

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Exact tensor layout and quantization. Runtime supplies quantization scale/zero point. */
object TensorCodec {
    fun encode(pixels: IntArray, c: ModelConfig, scale: Float = 0f, zeroPoint: Int = 0): ByteBuffer {
        require(pixels.size == c.inputWidth*c.inputHeight)
        val channels=c.inputChannels
        if(c.inputType!="FLOAT32" && c.quantizationMode=="tensor") require(scale.isFinite() && scale>0f) { "Échelle de quantification invalide" }
        val out=ByteBuffer.allocateDirect(pixels.size*channels*if(c.inputType=="FLOAT32") 4 else 1).order(ByteOrder.nativeOrder())
        fun value(pixel:Int,channel:Int):Float {
            val r=(pixel shr 16) and 255;val g=(pixel shr 8) and 255;val b=pixel and 255
            return if(channels==1) .299f*r+.587f*g+.114f*b else when(channel){0->if(c.isRgb)r.toFloat() else b.toFloat();1->g.toFloat();else->if(c.isRgb)b.toFloat() else r.toFloat()}
        }
        fun write(pixel:Int,channel:Int) {
            val raw=value(pixel,channel)
            val normalized=(raw-(c.channelMean.getOrNull(channel) ?: c.mean))/(c.channelStd.getOrNull(channel) ?: c.std)
            if(c.inputType=="FLOAT32") out.putFloat(normalized)
            else {
                val quant=if(c.quantizationMode=="raw") raw.roundToInt() else (normalized/scale+zeroPoint).roundToInt()
                out.put((if(c.inputType=="UINT8") quant.coerceIn(0,255) else quant.coerceIn(-128,127)).toByte())
            }
        }
        if(c.inputLayout=="NCHW") for(ch in 0 until channels) for(pixel in pixels) write(pixel,ch)
        else for(pixel in pixels) for(ch in 0 until channels) write(pixel,ch)
        out.rewind();return out
    }
    fun decode(buffer: ByteBuffer, type: String, count: Int, scale: Float=0f, zeroPoint: Int=0): FloatArray {
        buffer.order(ByteOrder.nativeOrder());buffer.rewind()
        return FloatArray(count) {
            when(type){"FLOAT32"->buffer.float;"INT32"->buffer.int.toFloat();"UINT8","INT8"->{require(scale.isFinite() && scale>0f);val b=buffer.get().toInt();((if(type=="UINT8") b and 255 else b)-zeroPoint)*scale};else->error("Type de sortie non pris en charge : $type")}
        }
    }
}
