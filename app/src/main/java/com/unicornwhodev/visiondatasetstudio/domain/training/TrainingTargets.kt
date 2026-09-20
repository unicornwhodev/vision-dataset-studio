package com.unicornwhodev.visiondatasetstudio.domain.training

import com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import kotlin.math.*

/** Target encodings are contracts, never guessed from a family name. */
object TrainingTargets {
    fun encode(a:SampleAnnotations,c:ModelConfig,iw:Int,ih:Int):FloatArray {
        val spec=requireNotNull(c.training);val shape=spec.targetShape
        require(shape.isNotEmpty() && shape[0]==1 && shape.all{it>0})
        val out=FloatArray(shape.fold(1){x,y->Math.multiplyExact(x,y)})
        val transform=InputTransform.create(iw,ih,c.inputWidth,c.inputHeight,ModelContract.resize(c),c.cropFraction)
        fun xy(x:Float,y:Float)=((x*transform.fittedWidth+transform.left)/transform.width) to ((y*transform.fittedHeight+transform.top)/transform.height)
        fun cls(label:String)=c.labels.indexOf(label).also{require(it>=0){"Classe corrigée absente du modèle : $label"}}
        when(spec.targetEncoding) {
            "one_hot","multi_hot" -> {
                require(shape==listOf(1,c.labels.size))
                require(a.tags.all{it.isHumanVerified})
                val tags=a.tags.map{cls(it.label)}.distinct()
                if(spec.targetEncoding=="one_hot")require(tags.size==1){"Ce modèle exige exactement une classe validée par image"}
                else require(c.labels.withIndex().all{(index,label)->index in tags || label in a.quality.verifiedNegativeQueries}){"Présence ou absence humaine requise pour chaque classe multilabel"}
                tags.forEach{out[it]=1f}
            }
            "points_xyv" -> {
                require(shape==listOf(1,c.labels.size,3));require(a.points.all{it.isHumanVerified && !it.isAbstained})
                val present=a.points.filterNot{it.isAbsent};require(a.points.map{it.label}.distinct().size==a.points.size){"Une cible par classe attendue"}
                present.forEach{p->val k=cls(p.label);val point=xy(p.x,p.y);require(point.first in 0f..1f && point.second in 0f..1f){"La cible est hors du recadrage du modèle"};out[k*3]=point.first;out[k*3+1]=point.second;out[k*3+2]=1f}
                require(c.labels.all{label->a.points.any{it.label==label} || label in a.quality.verifiedNegativeQueries}){"Présence/absence à vérifier pour chaque classe"}
            }
            "heatmap_nchw" -> {
                require(shape.size==4 && shape[1]==c.labels.size);val h=shape[2];val w=shape[3]
                require(a.points.all{it.isHumanVerified && !it.isAbstained})
                a.points.filterNot{it.isAbsent}.forEach{p->val k=cls(p.label);val point=xy(p.x,p.y);require(point.first in 0f..1f && point.second in 0f..1f)
                    val x=point.first*(w-1);val y=point.second*(h-1)
                    for(dy in -3..3)for(dx in -3..3){val xx=x.roundToInt()+dx;val yy=y.roundToInt()+dy;if(xx in 0 until w && yy in 0 until h){val index=k*h*w+yy*w+xx;out[index]=max(out[index],exp(-((xx-x).pow(2)+(yy-y).pow(2))/2))}}
                }
                require(c.labels.all{label->a.points.any{it.label==label} || label in a.quality.verifiedNegativeQueries})
            }
            "boxes_xyxy_class_mask" -> {
                require(shape.size==3 && shape[2]==6 && a.boxes.size<=shape[1]);require(a.boxes.all{it.isHumanVerified})
                require(a.boxes.isNotEmpty() || c.labels.all{it in a.quality.verifiedNegativeQueries})
                a.boxes.forEachIndexed{index,b->val p=xy(b.xmin,b.ymin);val q=xy(b.xmax,b.ymax);require(listOf(p.first,p.second,q.first,q.second).all{it in 0f..1f}){"Boîte hors du recadrage"}
                    floatArrayOf(p.first,p.second,q.first,q.second,cls(b.label).toFloat(),1f).copyInto(out,index*6)
                }
            }
            else -> error("Encodage d’apprentissage non implémenté : ${spec.targetEncoding}")
        }
        return out
    }
    fun validationLoss(outputs:List<TensorValues>,target:FloatArray,c:ModelConfig):Double {
        val spec=requireNotNull(c.training)
        return when(spec.targetEncoding) {
            "one_hot","multi_hot" -> {
                val output=outputs[c.outputIndex].values;require(output.size==target.size && output.all(Float::isFinite))
                val scores=when(c.scoreActivation){"softmax"->{val max=output.max();val e=output.map{exp((it-max).toDouble())};e.map{it/e.sum()}};"sigmoid"->output.map{1.0/(1+exp(-it.toDouble()))};else->output.map{require(it in 0f..1f);it.toDouble()}}
                if(spec.targetEncoding=="one_hot") -target.indices.sumOf{target[it]*ln(scores[it].coerceIn(1e-7,1.0))}
                else -target.indices.sumOf{target[it]*ln(scores[it].coerceIn(1e-7,1.0))+(1-target[it])*ln((1-scores[it]).coerceIn(1e-7,1.0))}/target.size
            }
            "heatmap_nchw","points_xyv" -> {
                val output=outputs[c.outputIndex].values;require(output.size==target.size && output.all(Float::isFinite))
                output.indices.sumOf{(output[it]-target[it]).toDouble().pow(2)}/target.size
            }
            "boxes_xyxy_class_mask" -> {
                val predictions=ModelAdapters.decode(outputs.mapIndexed{i,v->i to v}.toMap(),c.copy(threshold=0f),InputTransform.create(c.inputWidth,c.inputHeight,c.inputWidth,c.inputHeight,"stretch")).filter{it.type=="box"}
                val rows=target.toList().chunked(6).filter{it[5]>0}
                if(rows.isEmpty())predictions.maxOfOrNull{it.score.toDouble()} ?: 0.0
                else rows.map { b ->
                    predictions.filter{it.label==c.labels[b[4].toInt()]}.minOfOrNull { p ->
                        val intersection=max(0f,min(b[2],p.xmax)-max(b[0],p.xmin))*max(0f,min(b[3],p.ymax)-max(b[1],p.ymin))
                        val union=(b[2]-b[0])*(b[3]-b[1])+(p.xmax-p.xmin)*(p.ymax-p.ymin)-intersection
                        1.0-intersection/max(union,1e-7f)+.1*(1-p.score)
                    } ?: 1.1
                }.average()
            }
            else -> error("Métrique de validation non implémentée")
        }.also{require(it.isFinite())}
    }
}
