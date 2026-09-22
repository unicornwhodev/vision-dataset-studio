package com.unicornwhodev.visiondatasetstudio.core.storage

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

object ImageNormalizer {
    /** Work on the cache copy, never the selected source document. No implicit downsampling. */
    fun normalize(file:File,enabled:Boolean,hasImportedAnnotations:Boolean):String {
        val orientation=runCatching{ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        if(orientation in 0..1)return "identity"
        require(enabled){tr("Normalisation EXIF désactivée; image orientée à normaliser en amont", "EXIF normalization disabled; normalize the oriented image upstream")}
        require(!hasImportedAnnotations){tr("EXIF + annotations importées : repère ambigu, normalisez le corpus en amont", "EXIF + imported annotations: ambiguous coordinate frame, normalize the corpus upstream")}
        val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,b)
        require(b.outWidth.toLong()*b.outHeight<=8_000_000){tr("Normalisation EXIF limitée à 8 MP pour borner la RAM; préparez cette image en amont", "EXIF normalization limited to 8 MP to bound RAM; prepare this image upstream")}
        val source=BitmapFactory.decodeFile(file.path) ?: error(tr("Image non décodable", "Image could not be decoded"))
        val m=Matrix()
        when(orientation){2->m.setScale(-1f,1f);3->m.setRotate(180f);4->m.setScale(1f,-1f);5->{m.setRotate(90f);m.postScale(-1f,1f)};6->m.setRotate(90f);7->{m.setRotate(-90f);m.postScale(-1f,1f)};8->m.setRotate(-90f);else->error(tr("Orientation EXIF inconnue", "Unknown EXIF orientation"))}
        var rotated:Bitmap?=null;val tmp=File(file.parentFile,file.name+".normalize")
        try {
            rotated=Bitmap.createBitmap(source,0,0,source.width,source.height,m,true)
            val format=if(b.outMimeType=="image/jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            tmp.outputStream().use{check(rotated!!.compress(format,95,it))}
            check(tmp.renameTo(file)) { tr("Normalisation non enregistrée", "Normalization not saved") }
            return "exif_$orientation;reencoded_"+if(format==Bitmap.CompressFormat.JPEG) "jpeg95" else "png"
        } finally { if(rotated!==source)rotated?.recycle();source.recycle();tmp.delete() }
    }
}
