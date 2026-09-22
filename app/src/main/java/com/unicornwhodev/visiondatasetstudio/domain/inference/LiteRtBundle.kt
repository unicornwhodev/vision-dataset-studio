package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import android.graphics.Bitmap
import android.graphics.Color
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.*

@JsonClass(generateAdapter=true)
data class BundleManifest(val schema:Int=1,val kind:String,val revision:String="",val files:Map<String,String> = emptyMap())

/** All graph execution and tokenization happens on Android; no HTTP inference fallback. */
class LiteRtBundle(private val manifestFile:File) {
    private val root=manifestFile.parentFile!!
    val manifest=StudioJson.moshi.adapter(BundleManifest::class.java).fromJson(manifestFile.readText()) ?: error(tr("Manifeste bundle invalide", "Invalid bundle manifest"))
    var embedding:FloatArray?=null
        private set
    var mask:Bitmap?=null
        private set
    var note:String=""
        private set
    init {
        require(manifest.schema==1 && manifest.kind in setOf("tinyclip","efficientvit_sam","florence2"))
        // Check the pipeline's actual inputs, never an obsolete checksum inventory or documentation.
        val required=when(manifest.kind) {
            "tinyclip" -> listOf("image_encoder.tflite","text_encoder.tflite","pipeline.json","processor/tokenizer.json")
            "efficientvit_sam" -> listOf("image_encoder.tflite","decoder_point.tflite","decoder_box.tflite")
            else -> listOf("image_encoder.tflite","multimodal_encoder.tflite","decoder.tflite","processor/tokenizer.json")
        }
        required.forEach { file(it) }
    }
    fun file(relative:String):File=File(root,relative).also{require(it.canonicalPath.startsWith(root.canonicalPath+File.separator) && it.isFile)}
    private fun graph(name:String,inputs:List<LiteRtGraph.Input>,threads:Int)=LiteRtGraph(file(name),threads).use{it.run(inputs)}
    suspend fun run(bitmap:Bitmap,c:ModelConfig):List<ModelProposal> {
        embedding=null;mask?.recycle();mask=null;note=""
        return when(manifest.kind){"tinyclip"->clip(bitmap,c);"efficientvit_sam"->sam(bitmap,c);"florence2"->florence(bitmap,c);else->error(tr("Bundle inconnu", "Unknown bundle"))}
    }
    private suspend fun clip(bitmap:Bitmap,c:ModelConfig):List<ModelProposal> {
        require(c.labels.isNotEmpty() && c.labels.size<=100){tr("TinyCLIP : configurez les classes candidates", "TinyCLIP: configure candidate classes")}
        val vector=graph("image_encoder.tflite",listOf(LiteRtGraph.image(bitmap,c)),c.threads).single().values
        require(vector.size==512);embedding=vector.copyOf()
        val tokenizer=BytePairTokenizer(file("processor/tokenizer.json"))
        val metadata=StudioJson.moshi.adapter(Any::class.java).fromJson(file("pipeline.json").readText()) as Map<*,*>
        val scale=(metadata["logit_scale"] as Number).toFloat()
        val scores=LiteRtGraph(file("text_encoder.tflite"),c.threads).use { text ->
            c.labels.map { label ->
                currentCoroutineContext().ensureActive()
                val prompt=ModelPrompts.clipCandidate(c.prompt,label)
                val (ids,mask)=tokenizer.encode(prompt,77,49407)
                val values=text.run(listOf(LiteRtGraph.ints(listOf(1,77),ids),LiteRtGraph.ints(listOf(1,77),mask))).single().values
                require(values.size==vector.size);values.indices.sumOf{values[it].toDouble()*vector[it]}*scale
            }
        }
        val max=scores.max();val exps=scores.map{exp(it-max)};val total=exps.sum()
        return c.labels.mapIndexed{i,label->ModelProposal("tag",label,(exps[i]/total).toFloat())}.filter{it.score>=c.threshold}.sortedByDescending{it.score}.take(c.topK)
    }
    private fun sam(bitmap:Bitmap,c:ModelConfig):List<ModelProposal> {
        require((c.promptPoint.size==2) xor (c.promptBox.size==4)){tr("SAM : sélectionnez un point ou une boîte avant l’inférence", "SAM: select a point or box before inference")}
        require((c.promptPoint+c.promptBox).all{it.isFinite() && it in 0f..1f})
        val scale=512f/max(bitmap.width,bitmap.height);val w=(bitmap.width*scale).roundToInt();val h=(bitmap.height*scale).roundToInt()
        val scaled=Bitmap.createScaledBitmap(bitmap,w,h,true);val pixels=IntArray(w*h);scaled.getPixels(pixels,0,w,0,0,w,h);if(scaled!==bitmap)scaled.recycle()
        val input=FloatArray(3*512*512);val means=floatArrayOf(123.675f,116.28f,103.53f);val std=floatArrayOf(58.395f,57.12f,57.375f)
        for(y in 0 until h)for(x in 0 until w){val p=pixels[y*w+x];val channels=intArrayOf(Color.red(p),Color.green(p),Color.blue(p));for(k in 0..2)input[k*512*512+y*512+x]=(channels[k]-means[k])/std[k]}
        val encoded=graph("image_encoder.tflite",listOf(LiteRtGraph.floats(listOf(1,3,512,512),input)),c.threads).single()
        val referenceScale=1024f/max(bitmap.width,bitmap.height)
        val result=if(c.promptPoint.size==2)graph("decoder_point.tflite",listOf(LiteRtGraph.tensor(encoded),LiteRtGraph.floats(listOf(1,1,2),floatArrayOf(c.promptPoint[0]*bitmap.width*referenceScale,c.promptPoint[1]*bitmap.height*referenceScale)),LiteRtGraph.ints(listOf(1,1),intArrayOf(1))),c.threads)
        else graph("decoder_box.tflite",listOf(LiteRtGraph.tensor(encoded),LiteRtGraph.floats(listOf(1,4),FloatArray(4){c.promptBox[it]*(if(it%2==0)bitmap.width else bitmap.height)*referenceScale})),c.threads)
        require(result[0].shape==listOf(1,1,256,256) && result[1].values.size==1)
        val logits=result[0].values;var x1=bitmap.width;var y1=bitmap.height;var x2=-1;var y2=-1
        require(bitmap.width.toLong()*bitmap.height<=4_194_304){tr("Réduisez l’image avant segmentation", "Reduce image size before segmentation")}
        val maskPixels=IntArray(bitmap.width*bitmap.height)
        // Bilinear inverse mask transform, cropped to the unpadded encoder image.
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){
            val fx=((x+.5f)/bitmap.width*w/2-.5f).coerceIn(0f,255f);val fy=((y+.5f)/bitmap.height*h/2-.5f).coerceIn(0f,255f)
            val ix=floor(fx).toInt();val iy=floor(fy).toInt();val ax=fx-ix;val ay=fy-iy
            val value=logits[iy*256+ix]*(1-ax)*(1-ay)+logits[iy*256+min(ix+1,255)]*ax*(1-ay)+logits[min(iy+1,255)*256+ix]*(1-ax)*ay+logits[min(iy+1,255)*256+min(ix+1,255)]*ax*ay
            if(value>0){maskPixels[y*bitmap.width+x]=Color.WHITE;x1=min(x1,x);y1=min(y1,y);x2=max(x2,x);y2=max(y2,y)}
        }
        mask=Bitmap.createBitmap(maskPixels,bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
        note=tr("Masque proposé · correction et validation requises.", "Proposed mask · correction and approval required.")
        val score=result[1].values[0].coerceIn(0f,1f)
        return if(x2<x1 || y2<y1 || score<c.threshold)emptyList() else listOf(
            ModelProposal("mask",c.labels.firstOrNull() ?: "object",score,mask=com.unicornwhodev.visiondatasetstudio.data.model.MaskTarget("proposal",c.labels.firstOrNull() ?: "object",bitmap.width,bitmap.height,MaskCodec.encode(BooleanArray(maskPixels.size){maskPixels[it]!=0}))),
            ModelProposal("box",c.labels.firstOrNull() ?: "object",score,x1.toFloat()/bitmap.width,y1.toFloat()/bitmap.height,(x2+1f)/bitmap.width,(y2+1f)/bitmap.height))
    }
    private suspend fun florence(bitmap:Bitmap,c:ModelConfig):List<ModelProposal> {
        val prompts=mapOf("<CAPTION>" to "What does the image describe?","<DETAILED_CAPTION>" to "Describe in detail what is shown in the image.","<MORE_DETAILED_CAPTION>" to "Describe with a paragraph what is shown in the image.","<OD>" to "Locate the objects with category name in the image.","<OCR>" to "What is the text in the image?")
        val prompt=c.prompt.ifBlank{"<CAPTION>"};val text=prompts[prompt] ?: prompt
        val tokenizer=BytePairTokenizer(file("processor/tokenizer.json"));val (ids,attention)=tokenizer.encode(text,64,1)
        val image=graph("image_encoder.tflite",listOf(LiteRtGraph.image(bitmap,c)),c.threads).single()
        currentCoroutineContext().ensureActive()
        val encoded=graph("multimodal_encoder.tflite",listOf(LiteRtGraph.tensor(image),LiteRtGraph.ints(listOf(1,64),ids),LiteRtGraph.ints(listOf(1,64),attention)),c.threads).single()
        val encoderAttention=IntArray(641){if(it<577)1 else attention[it-577]}
        val prefix=IntArray(128){1};prefix[0]=2;val tokens=mutableListOf<Int>();var complete=false
        LiteRtGraph(file("decoder.tflite"),c.threads).use { decoder ->
            for(position in 0 until 128){
                currentCoroutineContext().ensureActive()
                val next=if(position==0)0 else {
                    val out=decoder.run(listOf(LiteRtGraph.tensor(encoded),LiteRtGraph.ints(listOf(1,641),encoderAttention),LiteRtGraph.ints(listOf(1,128),prefix),LiteRtGraph.ints(listOf(1),intArrayOf(position)))).single()
                    require(out.shape==listOf(1,51289));out.values.indices.maxBy{out.values[it]}
                }
                tokens.add(next);if(next==2){complete=true;break};if(position+1<128)prefix[position+1]=next
            }
        }
        if(!complete){note=tr("Génération tronquée à 128 jetons ; aucune annotation produite.", "Generation truncated at 128 tokens; no annotation produced.");return emptyList()}
        val raw=tokenizer.decode(tokens);note=tr("Génération locale complète ; score non calibré, validation humaine requise.", "Local generation complete; uncalibrated score, human review required.")
        if(prompt=="<OD>")return Regex("([^<>]+)<loc_(\\d+)><loc_(\\d+)><loc_(\\d+)><loc_(\\d+)>").findAll(raw).mapNotNull{m->
            val v=(2..5).map{(m.groupValues[it].toInt()+.5f)/1000f}
            if(v.any{it !in 0f..1f} || v[0]>=v[2] || v[1]>=v[3])null else ModelProposal("box",m.groupValues[1].trim(),1f,v[0],v[1],v[2],v[3])
        }.toList()
        val clean=tokenizer.decode(tokens,true).trim()
        return if(clean.isEmpty())emptyList() else listOf(ModelProposal("caption","",1f,text=clean))
    }
    fun close(){mask?.recycle();mask=null}
}
