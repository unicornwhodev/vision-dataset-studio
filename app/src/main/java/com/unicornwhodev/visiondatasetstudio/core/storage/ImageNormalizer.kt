package com.unicornwhodev.visiondatasetstudio.core.storage

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
        require(enabled){"Normalisation EXIF désactivée; image orientée à normaliser en amont"}
        require(!hasImportedAnnotations){"EXIF + annotations importées : repère ambigu, normalisez le corpus en amont"}
        val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,b)
        require(b.outWidth.toLong()*b.outHeight<=8_000_000){"Normalisation EXIF limitée à 8 MP pour borner la RAM; préparez cette image en amont"}
        val source=BitmapFactory.decodeFile(file.path) ?: error("Image non décodable")
        val m=Matrix()
        when(orientation){2->m.setScale(-1f,1f);3->m.setRotate(180f);4->m.setScale(1f,-1f);5->{m.setRotate(90f);m.postScale(-1f,1f)};6->m.setRotate(90f);7->{m.setRotate(-90f);m.postScale(-1f,1f)};8->m.setRotate(-90f);else->error("Orientation EXIF inconnue")}
        var rotated:Bitmap?=null;val tmp=File(file.parentFile,file.name+".normalize")
        try {
            rotated=Bitmap.createBitmap(source,0,0,source.width,source.height,m,true)
            val format=if(b.outMimeType=="image/jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            tmp.outputStream().use{check(rotated!!.compress(format,95,it))}
            check(tmp.renameTo(file)) { "Normalisation non enregistrée" }
            return "exif_$orientation;reencoded_"+if(format==Bitmap.CompressFormat.JPEG) "jpeg95" else "png"
        } finally { if(rotated!==source)rotated?.recycle();source.recycle();tmp.delete() }
    }
}
