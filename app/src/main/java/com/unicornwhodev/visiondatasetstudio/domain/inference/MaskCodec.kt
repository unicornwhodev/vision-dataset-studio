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
    fun polygon(m:MaskTarget,vertices:List<Pair<Float,Float>>,erase:Boolean=false):MaskTarget {
        require(vertices.size in 3..10_000 && vertices.all{it.first.isFinite()&&it.second.isFinite()&&it.first in 0f..1f&&it.second in 0f..1f})
        val pixels=decode(m)
        for(y in 0 until m.height)for(x in 0 until m.width) {
            val px=(x+.5f)/m.width;val py=(y+.5f)/m.height;var inside=false;var j=vertices.lastIndex
            for(i in vertices.indices){val a=vertices[i];val b=vertices[j]
                if((a.second>py)!=(b.second>py) && px<(b.first-a.first)*(py-a.second)/(b.second-a.second)+a.first)inside=!inside
                j=i
            }
            if(inside)pixels[y*m.width+x]=!erase
        }
        return m.copy(runs=encode(pixels),isHumanVerified=true,explicitlyAdjusted=true)
    }
    fun fill(m:MaskTarget,x:Float,y:Float,erase:Boolean=false):MaskTarget {
        require(x in 0f..1f&&y in 0f..1f);val pixels=decode(m);val sx=(x*m.width).toInt().coerceAtMost(m.width-1);val sy=(y*m.height).toInt().coerceAtMost(m.height-1)
        val from=pixels[sy*m.width+sx];val to=!erase
        if(from==to)return m
        val queue=ArrayDeque<Int>();queue.add(sy*m.width+sx);pixels[sy*m.width+sx]=to
        while(queue.isNotEmpty()){val p=queue.removeFirst();val px=p%m.width;val py=p/m.width
            for(n in intArrayOf(if(px>0)p-1 else -1,if(px<m.width-1)p+1 else -1,if(py>0)p-m.width else -1,if(py<m.height-1)p+m.width else -1))
                if(n>=0&&pixels[n]==from){pixels[n]=to;queue.add(n)}
        }
        return m.copy(runs=encode(pixels),isHumanVerified=true,explicitlyAdjusted=true)
    }
    fun merge(masks:List<MaskTarget>,id:String,label:String):MaskTarget {
        require(masks.size>=2&&masks.all{it.width==masks[0].width&&it.height==masks[0].height})
        val pixels=BooleanArray(masks[0].width*masks[0].height);masks.forEach{m->decode(m).forEachIndexed{i,v->pixels[i]=pixels[i]||v}}
        return MaskTarget(id,label,masks[0].width,masks[0].height,encode(pixels),true,explicitlyAdjusted=true)
    }
    fun split(m:MaskTarget,id:(Int)->String):List<MaskTarget> {
        val source=decode(m);val visited=BooleanArray(source.size);val result=mutableListOf<MaskTarget>()
        for(seed in source.indices)if(source[seed]&&!visited[seed]){val component=BooleanArray(source.size);val q=ArrayDeque<Int>();q.add(seed);visited[seed]=true
            while(q.isNotEmpty()){val p=q.removeFirst();component[p]=true;val x=p%m.width;val y=p/m.width
                for(n in intArrayOf(if(x>0)p-1 else -1,if(x<m.width-1)p+1 else -1,if(y>0)p-m.width else -1,if(y<m.height-1)p+m.width else -1))if(n>=0&&source[n]&&!visited[n]){visited[n]=true;q.add(n)}
            }
            result+=m.copy(id=id(result.size),runs=encode(component),isHumanVerified=true,explicitlyAdjusted=true)
        }
        return result
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
