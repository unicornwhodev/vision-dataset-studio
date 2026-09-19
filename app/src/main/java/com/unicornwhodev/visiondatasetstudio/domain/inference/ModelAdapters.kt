package com.unicornwhodev.visiondatasetstudio.domain.inference

import kotlin.math.*

/** Pure numerical part of the model contract; testable without Android or model weights. */
data class TensorValues(val shape: List<Int>, val values: FloatArray) {
    init { require(shape.isNotEmpty() && shape.all { it > 0 } && shape.fold(1L) { a, b -> a * b } == values.size.toLong()) }
}

data class InputTransform(val imageWidth: Int, val imageHeight: Int, val width: Int, val height: Int,
                          val fittedWidth: Int, val fittedHeight: Int, val left: Int, val top: Int) {
    fun point(x: Float, y: Float, normalized: Boolean): Pair<Float, Float> {
        val px = if (normalized) x * width else x
        val py = if (normalized) y * height else y
        return ((px - left) / fittedWidth) to ((py - top) / fittedHeight)
    }
    companion object {
        fun create(iw: Int, ih: Int, w: Int, h: Int, mode: String): InputTransform {
            require(listOf(iw, ih, w, h).all { it > 0 })
            if (mode == "stretch") return InputTransform(iw, ih, w, h, w, h, 0, 0)
            require(mode in setOf("letterbox", "center_crop"))
            val scale = if (mode == "letterbox") min(w.toDouble()/iw, h.toDouble()/ih) else max(w.toDouble()/iw, h.toDouble()/ih)
            val fw = max(1, (iw * scale).roundToInt()); val fh = max(1, (ih * scale).roundToInt())
            return InputTransform(iw, ih, w, h, fw, fh, (w-fw)/2, (h-fh)/2)
        }
    }
}

object ModelContract {
    val adapters = listOf("ssd", "yolo", "xyxy_score_class", "classification", "points", "heatmap", "embedding", "inspect_only")
    fun adapter(c: ModelConfig) = if (c.adapter == "auto") when(c.task) {
        "object_detection" -> "ssd"; "classification" -> "classification"; "pointing" -> "points"; else -> c.task
    } else c.adapter
    fun resize(c: ModelConfig) = if (c.resizeMode == "auto") if (adapter(c) == "classification") "stretch" else "letterbox" else c.resizeMode
    fun outputTypes(c: ModelConfig): Set<String> {
        if(c.runtime=="local_http") return when(c.httpOutputMode) { "caption_text"->setOf("caption");else-> when(c.task){
            "classification"->setOf("tag");"captioning"->setOf("caption");"vqa"->setOf("vqa");"pointing"->setOf("point");"counting"->setOf("count");"multitask"->setOf("point","box","tag","caption","vqa","count");else->setOf("box")}}
        return when(adapter(c)) { "classification"->setOf("tag");"points","heatmap"->setOf("point");"embedding","inspect_only"->emptySet();else->when(c.outputMode){"points"->setOf("point");"both"->setOf("point","box");else->setOf("box")} } + if(c.deriveCounts) setOf("count") else emptySet()
    }
    fun validate(c: ModelConfig) {
        require(c.schemaVersion == 1) { "Version de contrat modèle non prise en charge" }
        require(c.runtime in setOf("litert_interpreter", "local_http")) { "Runtime non implémenté" }
        require(c.threshold.isFinite() && c.threshold in 0f..1f)
        require(c.nmsIou.isFinite() && c.nmsIou in 0f..1f)
        require(c.maxDetections in 1..1000 && c.topK in 1..1000)
        require(c.labels.none { it.isBlank() } && c.labels.distinct().size == c.labels.size)
        if (c.runtime == "local_http") { LocalCallContract.validate(c); return }
        require(adapter(c) in adapters) { "Adaptateur inconnu : ${adapter(c)}. Un encodeur visuel seul ne définit pas une tête de pointing." }
        require(c.inputWidth in 1..2048 && c.inputHeight in 1..2048 && c.inputChannels in setOf(1,3))
        require(c.inputType in setOf("FLOAT32", "UINT8", "INT8"))
        require(c.inputLayout in setOf("NHWC", "NCHW"))
        require(resize(c) in setOf("letterbox", "stretch", "center_crop"))
        require(c.quantizationMode in setOf("raw", "tensor"))
        require(c.inputType != "INT8" || c.quantizationMode == "tensor") { "INT8 exige la quantification du tenseur" }
        require(c.mean.isFinite() && c.std.isFinite() && c.std > 0f)
        require(c.channelMean.isEmpty() || (c.channelMean.size == c.inputChannels && c.channelMean.all(Float::isFinite)))
        require(c.channelStd.isEmpty() || (c.channelStd.size == c.inputChannels && c.channelStd.all { it.isFinite() && it > 0f }))
        require(c.padValue in 0..255 && c.threads in 1..8)
        require(c.coordinates in setOf("normalized", "pixels"))
        require(c.scoreActivation in setOf("none", "sigmoid", "softmax"))
        require(c.outputMode in setOf("boxes", "points", "both"))
        require(c.pointAnchor in setOf("center", "bottom_center", "top_center"))
        if (adapter(c) !in setOf("embedding", "inspect_only")) require(c.labels.isNotEmpty()) { "Définissez les classes du contrat" }
        require(listOf(c.outputIndex, c.outputIndexBoxes, c.outputIndexClasses, c.outputIndexScores).all { it in 0..128 })
        require(c.outputIndexCount in -1..128)
        if (adapter(c) == "yolo") require(c.outputLayout in setOf("BCN", "BNC"))
        if (adapter(c) == "heatmap") require(c.outputLayout in setOf("NHWC", "NCHW"))
        if (adapter(c) == "ssd") require(c.boxFormat in setOf("ymin_xmin_ymax_xmax", "xmin_ymin_xmax_ymax", "xywh"))
    }
}

interface ModelAdapter {
    val id: String
    fun decode(outputs: Map<Int, TensorValues>, config: ModelConfig, transform: InputTransform): List<ModelProposal>
}

/** No shape guessing: every supported output layout is selected in the contract. */
object ModelAdapters {
    private fun scores(values: FloatArray, activation: String): FloatArray {
        require(values.all(Float::isFinite)) { "Scores non finis" }
        return when(activation) {
            "softmax" -> { val max = values.maxOrNull() ?: 0f; val exp = values.map { exp((it-max).toDouble()) }; val total = exp.sum(); FloatArray(values.size) { (exp[it]/total).toFloat() } }
            "sigmoid" -> FloatArray(values.size) { (1.0/(1.0 + exp(-values[it].toDouble()))).toFloat() }
            else -> values.also { require(it.all { score -> score in 0f..1f }) { "Scores hors [0,1] : précisez leur activation" } }
        }
    }
    private fun label(c: ModelConfig, raw: Int): String = c.labels.getOrNull(raw-c.classOffset) ?: error("Indice classe $raw absent du vocabulaire")
    private fun box(c: ModelConfig, t: InputTransform, x1: Float, y1: Float, x2: Float, y2: Float, score: Float, label: String): ModelProposal? {
        if (!listOf(x1,y1,x2,y2,score).all(Float::isFinite) || x2<=x1 || y2<=y1 || score<c.threshold) return null
        val a=t.point(x1,y1,c.coordinates=="normalized"); val b=t.point(x2,y2,c.coordinates=="normalized")
        if (b.first<=0f || b.second<=0f || a.first>=1f || a.second>=1f) return null
        return ModelProposal("box", label, score, a.first.coerceIn(0f,1f), a.second.coerceIn(0f,1f), b.first.coerceIn(0f,1f), b.second.coerceIn(0f,1f))
    }
    private fun iou(a: ModelProposal,b: ModelProposal): Float {
        val intersection=max(0f,min(a.xmax,b.xmax)-max(a.xmin,b.xmin))*max(0f,min(a.ymax,b.ymax)-max(a.ymin,b.ymin))
        return intersection/max(1e-9f,(a.xmax-a.xmin)*(a.ymax-a.ymin)+(b.xmax-b.xmin)*(b.ymax-b.ymin)-intersection)
    }
    fun nms(boxes: List<ModelProposal>, c: ModelConfig): List<ModelProposal> {
        val kept=mutableListOf<ModelProposal>()
        for (b in boxes.sortedByDescending { it.score }) {
            if (kept.none { it.label==b.label && iou(it,b)>c.nmsIou }) kept.add(b)
            if (kept.size>=c.maxDetections) break
        }; return kept
    }
    fun decode(outputs: Map<Int,TensorValues>, c: ModelConfig,t: InputTransform): List<ModelProposal> {
        ModelContract.validate(c)
        val a = ModelContract.adapter(c)
        val result = when(a) {
            "classification" -> {
                val out=outputs.getValue(c.outputIndex)
                require(out.shape == listOf(c.labels.size) || out.shape == listOf(1,c.labels.size)) { "Sortie classification incompatible" }
                scores(out.values,c.scoreActivation).mapIndexed { i,s -> ModelProposal("tag",c.labels[i],s) }.filter { it.score>=c.threshold }.sortedByDescending { it.score }.take(c.topK)
            }
            "yolo" -> {
                val out=outputs.getValue(c.outputIndex); val sh=out.shape
                val channels=4+c.labels.size+if(c.yoloObjectness) 1 else 0
                require(sh.size==3 && sh[0]==1 && (if(c.outputLayout=="BCN") sh[1] else sh[2])==channels) { "Tenseur YOLO incompatible avec classes/layout/objectness" }
                val n=if(c.outputLayout=="BCN") sh[2] else sh[1]; require(n<=100_000)
                fun v(i:Int,k:Int)=out.values[if(c.outputLayout=="BCN") k*n+i else i*channels+k]
                val all=mutableListOf<ModelProposal>()
                for(i in 0 until n) {
                    val start=if(c.yoloObjectness) 5 else 4
                    val prob=scores(FloatArray(c.labels.size) { v(i,start+it) },c.scoreActivation)
                    val cls=prob.indices.maxByOrNull { prob[it] } ?: continue
                    val obj=if(c.yoloObjectness) scores(floatArrayOf(v(i,4)),if(c.scoreActivation=="sigmoid") "sigmoid" else "none")[0] else 1f
                    val score=prob[cls]*obj
                    if(score<c.threshold) continue
                    val x=v(i,0);val y=v(i,1);val w=v(i,2);val h=v(i,3)
                    box(c,t,x-w/2,y-h/2,x+w/2,y+h/2,score,c.labels[cls])?.let(all::add)
                }; nms(all,c)
            }
            "ssd" -> {
                val boxes=outputs.getValue(c.outputIndexBoxes);val cls=outputs.getValue(c.outputIndexClasses);val score=outputs.getValue(c.outputIndexScores)
                require(boxes.shape.size==3 && boxes.shape[0]==1 && boxes.shape[2]==4)
                val n=boxes.shape[1];require(cls.values.size==n && score.values.size==n)
                val count=if(c.outputIndexCount<0) n else {
                    val value=outputs.getValue(c.outputIndexCount).values.single();require(value.isFinite() && value>=0f && value==floor(value));value.toInt().coerceAtMost(n)
                }
                val prob=scores(score.values,c.scoreActivation)
                (0 until count).mapNotNull { i ->
                    if(prob[i]<c.threshold) return@mapNotNull null
                    val b=boxes.values.copyOfRange(i*4,i*4+4)
                    val clsValue=cls.values[i];require(clsValue.isFinite() && clsValue==floor(clsValue))
                    val r=when(c.boxFormat){"ymin_xmin_ymax_xmax"->floatArrayOf(b[1],b[0],b[3],b[2]);"xywh"->floatArrayOf(b[0],b[1],b[0]+b[2],b[1]+b[3]);else->b}
                    box(c,t,r[0],r[1],r[2],r[3],prob[i],label(c,clsValue.toInt()))
                }.sortedByDescending{it.score}.take(c.maxDetections)
            }
            "xyxy_score_class" -> {
                val out=outputs.getValue(c.outputIndex);val sh=out.shape
                require(sh.size==3 && sh[0]==1 && sh[2]==6)
                (0 until sh[1]).mapNotNull { i ->
                    val b=out.values.copyOfRange(i*6,i*6+6);val score=scores(floatArrayOf(b[4]),c.scoreActivation)[0]
                    if(score<c.threshold) null else {require(b[5].isFinite() && b[5]==floor(b[5]));box(c,t,b[0],b[1],b[2],b[3],score,label(c,b[5].toInt()))}
                }.sortedByDescending{it.score}.take(c.maxDetections)
            }
            "points" -> {
                val out=outputs.getValue(c.outputIndex);val sh=out.shape
                require(sh.size==3 && sh[0]==1 && sh[2] in 2..3)
                require(c.labels.size==1 || c.labels.size==sh[1]) { "Points : une classe globale ou une classe par point" }
                (0 until sh[1]).mapNotNull { i ->
                    val base=i*sh[2];val p=t.point(out.values[base],out.values[base+1],c.coordinates=="normalized")
                    val score=if(sh[2]==3) scores(floatArrayOf(out.values[base+2]),c.scoreActivation)[0] else 1f
                    if(!p.first.isFinite() || !p.second.isFinite() || p.first !in 0f..1f || p.second !in 0f..1f || score<c.threshold) null
                    else ModelProposal("point",if(c.labels.size==1)c.labels[0] else c.labels[i],score,pointX=p.first,pointY=p.second,modelX=p.first,modelY=p.second)
                }.take(c.maxDetections)
            }
            "heatmap" -> {
                val out=outputs.getValue(c.outputIndex);val sh=out.shape
                require(sh.size==4 && sh[0]==1)
                val h=if(c.outputLayout=="NHWC") sh[1] else sh[2];val w=if(c.outputLayout=="NHWC") sh[2] else sh[3]
                val classes=if(c.outputLayout=="NHWC") sh[3] else sh[1];require(classes==c.labels.size)
                require(c.scoreActivation!="softmax") { "Heatmap : utilisez none ou sigmoid" }
                c.labels.mapIndexedNotNull { k,label ->
                    val vals=FloatArray(h*w) { i -> out.values[if(c.outputLayout=="NHWC") i*classes+k else k*h*w+i] }
                    val prob=scores(vals,c.scoreActivation);val i=prob.indices.maxByOrNull{prob[it]} ?: return@mapIndexedNotNull null
                    val p=t.point(((i%w)+.5f)/w,((i/w)+.5f)/h,true)
                    if(prob[i]<c.threshold || p.first !in 0f..1f || p.second !in 0f..1f) null else ModelProposal("point",label,prob[i],pointX=p.first,pointY=p.second,modelX=p.first,modelY=p.second)
                }
            }
            "embedding" -> {
                val out=outputs.getValue(c.outputIndex)
                require(out.values.isNotEmpty() && out.values.size<=4_000_000 && out.values.all(Float::isFinite)) { "Embedding invalide" }
                emptyList()
            }
            "inspect_only" -> {
                val out=outputs.getValue(c.outputIndex)
                require(out.values.isNotEmpty() && out.values.size<=4_000_000 && out.values.all(Float::isFinite)) { "Sortie inspectée invalide" }
                emptyList()
            }
            else -> error("Adaptateur absent")
        }
        val boxes=result.filter { it.type=="box" }
        val converted=if(c.outputMode in setOf("points","both") && boxes.isNotEmpty()) {
            val points=boxes.map { b ->
                val x=(b.xmin+b.xmax)/2;val y=when(c.pointAnchor){"bottom_center"->b.ymax;"top_center"->b.ymin;else->(b.ymin+b.ymax)/2}
                ModelProposal("point",b.label,b.score,pointX=x,pointY=y,modelX=x,modelY=y,boxWidth=b.xmax-b.xmin,boxHeight=b.ymax-b.ymin)
            }; (if(c.outputMode=="both") result else result.filter{it.type!="box"})+points
        } else result
        // Counts are proposals, never an assertion of exhaustive detection.
        return converted + if(c.deriveCounts) boxes.groupBy{it.label}.map { (label,items)->ModelProposal("count",label,items.minOf{it.score},count=items.size) } else emptyList()
    }
    fun get(id: String): ModelAdapter {
        require(id in ModelContract.adapters)
        return object : ModelAdapter {
            override val id = id
            override fun decode(outputs: Map<Int, TensorValues>, config: ModelConfig, transform: InputTransform) =
                ModelAdapters.decode(outputs,config.copy(adapter=id),transform)
        }
    }
}

object LocalCallContract {
    fun validate(c: ModelConfig) {
        val uri=java.net.URI(c.endpoint)
        require(uri.scheme=="http" && uri.host in setOf("127.0.0.1","localhost") && uri.userInfo==null && uri.fragment==null) {
            "Le runtime local HTTP est limité au loopback de cet appareil. Aucun envoi Internet implicite."
        }
        require(c.httpTimeoutSeconds in 5..300 && c.requestTemplate.length<=32_000 && c.prompt.length<=16_000)
        require(c.httpOutputMode in setOf("proposals","caption_text"))
        require(c.task in setOf("object_detection","pointing","classification","captioning","vqa","counting","multitask")) { "Tâche locale non implémentée" }
        require(c.responsePath.length<=500)
    }
    /** Field/index traversal only, not an executable expression or full JSONPath engine. */
    fun select(root: Any?, path: String): Any? = path.split('.').filter(String::isNotBlank).fold(root) { value,key ->
        when(value) { is Map<*,*> -> value[key]; is List<*> -> key.toIntOrNull()?.let{value.getOrNull(it)}; else -> null }
    }
    fun replace(value: Any?, vars: Map<String,Any>): Any? = when(value) {
        is String -> vars[value] ?: value
        is Map<*,*> -> value.entries.associate { it.key.toString() to replace(it.value,vars) }
        is List<*> -> value.map{replace(it,vars)}
        else -> value
    }
}
