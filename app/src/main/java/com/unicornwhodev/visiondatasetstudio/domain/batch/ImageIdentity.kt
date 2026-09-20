package com.unicornwhodev.visiondatasetstudio.domain.batch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import androidx.room.withTransaction
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.model.ImageIdentityEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Exact decoded pixels, without lossy resizing or perceptual thresholds. Memory bounded to 64 rows. */
object ImageIdentity {
    @Suppress("DEPRECATION")
    suspend fun pixelSha256(file:File):String {
        val decoder=BitmapRegionDecoder.newInstance(file.path,false) ?: error("Image non décodable")
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            digest.update(ByteBuffer.allocate(8).putInt(decoder.width).putInt(decoder.height).array())
            val options=BitmapFactory.Options().apply {
                inPreferredConfig=Bitmap.Config.ARGB_8888
                inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
                inScaled=false
            }
            val pixels=IntArray(decoder.width * minOf(64,decoder.height))
            val bytes=ByteArray(pixels.size*4)
            for(y in 0 until decoder.height step 64) {
                coroutineContext.ensureActive()
                val height=minOf(64,decoder.height-y)
                val strip=decoder.decodeRegion(Rect(0,y,decoder.width,y+height),options) ?: error("Pixels non décodables")
                try { strip.getPixels(pixels,0,decoder.width,0,0,decoder.width,height) } finally { strip.recycle() }
                val count=decoder.width*height
                for(i in 0 until count) {
                    val p=pixels[i];val j=i*4
                    bytes[j]=(p ushr 24).toByte();bytes[j+1]=(p ushr 16).toByte()
                    bytes[j+2]=(p ushr 8).toByte();bytes[j+3]=p.toByte()
                }
                digest.update(bytes,0,count*4)
            }
            return digest.digest().joinToString(""){"%02x".format(it)}
        } finally { decoder.recycle() }
    }

    /** Ownership and availability commit together. Duplicate fingerprints also resolve to the original owner. */
    suspend fun accept(db:AppDatabase,sample:SampleEntity,pixelHash:String):ImageIdentityEntity? = db.withTransaction {
        val keys=(listOfNotNull(sample.sha256,sample.sourceSha256).distinct().map{"file_sha256" to it} + ("pixels_sha256" to pixelHash))
        val owners=keys.mapNotNull { (kind,hash)->db.imageIdentityDao().owner(sample.projectId,kind,hash) }
        val duplicate=owners.filter{it.firstSampleId!=sample.sampleId}.minWithOrNull(compareBy({it.firstBatchNumber},{it.firstSampleId}))
        val ownerId=duplicate?.firstSampleId ?: sample.sampleId
        val ownerBatch=duplicate?.firstBatchNumber ?: sample.batchNumber
        // Existing human decisions are never silently converted to duplicates during an upgrade.
        check(duplicate==null || sample.annotationStatus in setOf("PENDING","PROPOSALS_AVAILABLE","DUPLICATE")) {
            "Image déjà présente au lot ${duplicate?.firstBatchNumber}; décision humaine conservée, résolution requise"
        }
        for((kind,hash) in keys) if(db.imageIdentityDao().owner(sample.projectId,kind,hash)==null)
            db.imageIdentityDao().insert(ImageIdentityEntity(sample.projectId,kind,hash,ownerId,ownerBatch))
        db.sampleDao().updateSample(if(duplicate==null)sample else sample.copy(
            localImagePath=null,acquisitionStatus="DUPLICATE",annotationStatus="DUPLICATE",
            auditReason="Déjà importée : lot ${duplicate.firstBatchNumber}, ${duplicate.firstSampleId}"))
        duplicate
    }

    suspend fun requireCanonical(db:AppDatabase,samples:List<SampleEntity>) {
        check(samples.map{it.sha256}.distinct().size==samples.size){"Images identiques dans cet export"}
        for(sample in samples) {
            val hash=sample.sha256 ?: error("Empreinte image absente")
            val owner=db.imageIdentityDao().owner(sample.projectId,"file_sha256",hash)
            check(owner?.firstSampleId==sample.sampleId){"Image déjà traitée ou identité non vérifiée : ${sample.assetId}"}
        }
    }
}
