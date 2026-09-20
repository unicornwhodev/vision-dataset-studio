package com.unicornwhodev.visiondatasetstudio.domain.inference

import android.content.Context
import android.util.AtomicFile
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import java.io.File
import kotlin.math.sqrt

@JsonClass(generateAdapter=true)
data class ImageEmbedding(val sampleId:String,val imageSha:String,val modelSha:String,val values:List<Float>)
data class SimilarImage(val sampleId:String,val cosine:Float)

/** Bounded private index, isolated by project and exact model weights. */
class EmbeddingIndex(context:Context,projectId:Long) {
    private val root=File(context.filesDir,"embeddings/$projectId").apply{mkdirs()}
    private val adapter=StudioJson.moshi.adapter(ImageEmbedding::class.java)
    fun put(sampleId:String,imageSha:String,modelSha:String,values:FloatArray):ImageEmbedding {
        require(values.size in 1..8192 && values.all(Float::isFinite))
        val norm=sqrt(values.sumOf{it.toDouble()*it}).toFloat();require(norm>1e-9)
        val row=ImageEmbedding(sampleId,imageSha,modelSha,values.map{it/norm})
        val file=File(root,AdaptiveCorrection.hash(sampleId)+".json");val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try{stream.write(adapter.toJson(row).toByteArray());atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw e}
        root.listFiles{f->f.extension=="json"}?.sortedByDescending{it.lastModified()}?.drop(1000)?.forEach{it.delete()}
        return row
    }
    fun get(sampleId:String):ImageEmbedding? {
        val file=File(root,AdaptiveCorrection.hash(sampleId)+".json")
        return if(file.isFile)adapter.fromJson(AtomicFile(file).openRead().bufferedReader().use{it.readText()}) else null
    }
    fun nearest(query:ImageEmbedding,limit:Int=12):List<SimilarImage> {
        require(limit in 1..100)
        return root.listFiles{f->f.extension=="json" && f.length()<=512*1024}?.mapNotNull{file->
            val row=adapter.fromJson(AtomicFile(file).openRead().bufferedReader().use{it.readText()}) ?: return@mapNotNull null
            if(row.sampleId==query.sampleId || row.modelSha!=query.modelSha || row.values.size!=query.values.size)return@mapNotNull null
            val cosine=row.values.indices.sumOf{row.values[it].toDouble()*query.values[it]}.toFloat()
            SimilarImage(row.sampleId,cosine.coerceIn(-1f,1f))
        }?.sortedByDescending{it.cosine}?.take(limit) ?: emptyList()
    }
}
