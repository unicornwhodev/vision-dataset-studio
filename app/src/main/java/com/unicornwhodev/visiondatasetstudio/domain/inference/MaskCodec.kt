package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.model.MaskTarget
import kotlin.math.*

/** Canonical RLE is row-major, starts with background, and carries its own raster dimensions. */
object MaskCodec {
    const val MAX_PIXELS = 4_194_304
    fun validate(m: MaskTarget) {
        require(m.width > 0 && m.height > 0 && m.width.toLong()*m.height <= MAX_PIXELS)
        require(m.runs.isNotEmpty() && m.runs.size <= m.width*m.height+1 && m.runs.all { it >= 0 })
        require(m.runs.sumOf { it.toLong() } == m.width.toLong()*m.height)
    }
    fun encode(pixels: BooleanArray): List<Int> {
        require(pixels.isNotEmpty() && pixels.size <= MAX_PIXELS)
        val runs = mutableListOf<Int>(); var value = false; var count = 0
        for (pixel in pixels) { if (pixel != value) { runs.add(count); count=0; value=pixel }; count++ }
        runs.add(count); return runs
    }
    fun decode(m: MaskTarget): BooleanArray {
        validate(m); val pixels=BooleanArray(m.width*m.height); var cursor=0
        m.runs.forEachIndexed { index, count -> if(index%2==1) pixels.fill(true,cursor,cursor+count); cursor+=count }
        return pixels
    }
    fun stroke(m: MaskTarget, x0: Float, y0: Float, x1: Float, y1: Float, radius: Float, erase: Boolean): MaskTarget {
        require(listOf(x0,y0,x1,y1,radius).all(Float::isFinite) && radius in .001f..0.25f)
        val pixels=decode(m); val ax=x0*m.width; val ay=y0*m.height; val bx=x1*m.width; val by=y1*m.height
        val r=radius*min(m.width,m.height); val dx=bx-ax; val dy=by-ay; val length=dx*dx+dy*dy
        for(y in floor(min(ay,by)-r).toInt().coerceAtLeast(0)..ceil(max(ay,by)+r).toInt().coerceAtMost(m.height-1))
            for(x in floor(min(ax,bx)-r).toInt().coerceAtLeast(0)..ceil(max(ax,bx)+r).toInt().coerceAtMost(m.width-1)) {
                val t=if(length==0f)0f else (((x+.5f-ax)*dx+(y+.5f-ay)*dy)/length).coerceIn(0f,1f)
                if((x+.5f-ax-t*dx).pow(2)+(y+.5f-ay-t*dy).pow(2)<=r*r) pixels[y*m.width+x]=!erase
            }
        return m.copy(runs=encode(pixels),isHumanVerified=true,explicitlyAdjusted=true)
    }
    fun resize(m: MaskTarget, width: Int, height: Int): BooleanArray {
        require(width>0 && height>0 && width.toLong()*height<=MAX_PIXELS)
        val source=decode(m)
        return BooleanArray(width*height) { i -> source[((i/width+.5)*m.height/height).toInt().coerceAtMost(m.height-1)*m.width+((i%width+.5)*m.width/width).toInt().coerceAtMost(m.width-1)] }
    }
    data class CocoProjection(val segmentation: Map<String, Any>, val area: Int, val bbox: List<Int>)
    /** Stream original-resolution, column-major RLE without allocating a full camera-size raster. */
    fun projectCoco(m: MaskTarget, width: Int, height: Int): CocoProjection {
        require(width > 0 && height > 0 && width.toLong()*height <= 100_000_000) { "Image trop grande pour l’export COCO" }
        val source=decode(m); val runs=mutableListOf<Int>()
        var value=false; var count=0; var area=0
        var minX=width; var minY=height; var maxX=-1; var maxY=-1
        for(x in 0 until width) {
            val sx=((x+.5)*m.width/width).toInt().coerceAtMost(m.width-1)
            for(y in 0 until height) {
                val sy=((y+.5)*m.height/height).toInt().coerceAtMost(m.height-1)
                val pixel=source[sy*m.width+sx]
                if(pixel!=value) {
                    require(runs.size <= MAX_PIXELS) { "Masque trop complexe pour l’export COCO" }
                    runs.add(count); count=0; value=pixel
                }
                count++
                if(pixel) { area++; minX=min(minX,x); minY=min(minY,y); maxX=max(maxX,x); maxY=max(maxY,y) }
            }
        }
        runs.add(count)
        return CocoProjection(mapOf("size" to listOf(height,width),"counts" to runs),area,
            if(area==0) listOf(0,0,0,0) else listOf(minX,minY,maxX-minX+1,maxY-minY+1))
    }
    /** COCO uncompressed RLE uses column-major ordering, unlike the canonical record. */
    fun coco(m: MaskTarget, width: Int, height: Int): Map<String, Any> = projectCoco(m,width,height).segmentation
}
