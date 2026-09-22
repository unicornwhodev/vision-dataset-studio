package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.squareup.moshi.JsonClass
import java.security.MessageDigest
import kotlin.math.abs

@JsonClass(generateAdapter=true)
data class CorrectionExample(val key:String,val imageKey:String,val x:Double,val y:Double,val width:Double,val height:Double,val confidence:Double,
    val correctedX:Double,val correctedY:Double,val explicitlyAdjusted:Boolean,val humanVerified:Boolean,
    val kind:String="point",val correctedWidth:Double?=null,val correctedHeight:Double?=null) {
    fun features()=doubleArrayOf(1.0,x-.5,y-.5,width,height,confidence-.5)
    fun delta()=if(kind=="box") doubleArrayOf(correctedX-x,correctedY-y,(correctedWidth ?: width)-width,(correctedHeight ?: height)-height) else doubleArrayOf(correctedX-x,correctedY-y)
}
@JsonClass(generateAdapter=true)
data class CorrectionHead(val weights:List<List<Double>>,val generation:Int,val kind:String="point")
@JsonClass(generateAdapter=true)
data class CorrectionGroup(val key:String,val examples:List<CorrectionExample> = emptyList(),val head:CorrectionHead?=null,val report:String=tr("Aucune correction supervisée", "No supervised corrections"))
@JsonClass(generateAdapter=true)
data class CorrectionLedger(val schema:Int=1,val groups:List<CorrectionGroup> = emptyList())
data class CorrectionEvaluation(val group:CorrectionGroup,val trainImages:Set<String>,val holdoutImages:Set<String>,val promoted:Boolean)

/** Six-feature ridge residual, not training the visual backbone. Explicit human moves only. */
object AdaptiveCorrection {
    const val CAPACITY=2048
    fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun holdout(image:String)=hash(image).take(8).toLong(16)%5L==0L
    fun groupKey(modelSource:String,label:String,kind:String="point")=if(kind=="point")hash("$modelSource\u0000$label") else hash("$modelSource\u0000$label\u0000$kind")
    private fun clamp(v:Double)=v.coerceIn(-.08,.08)
    private fun eligible(e:CorrectionExample)=e.key.isNotBlank() && e.imageKey.isNotBlank() && e.explicitlyAdjusted && e.humanVerified &&
        listOfNotNull(e.x,e.y,e.width,e.height,e.confidence,e.correctedX,e.correctedY,e.correctedWidth,e.correctedHeight).all{it.isFinite() && it in 0.0..1.0} && e.delta().all{abs(it)<=.15} && e.delta().any{abs(it)>1e-8}
    fun add(group:CorrectionGroup,entries:List<CorrectionExample>):CorrectionGroup {
        val byKey=(group.examples+entries).filter(::eligible).associateBy{it.key}
        return group.copy(examples=byKey.values.sortedBy{hash(it.key)}.take(CAPACITY))
    }
    fun predict(head:CorrectionHead?,features:DoubleArray):DoubleArray? {
        if(head==null)return null
        require(features.size==6 && features.all(Double::isFinite))
        require(head.weights.size in setOf(2,4) && head.weights.all{it.size==6 && it.all(Double::isFinite)})
        return DoubleArray(head.weights.size){axis->clamp(head.weights[axis].zip(features.toList()).sumOf{it.first*it.second})}
    }
    private fun solve(examples:List<CorrectionExample>):List<List<Double>> {
        val axes=examples.firstOrNull()?.delta()?.size ?: 2
        require(axes in setOf(2,4) && examples.all{it.delta().size==axes})
        val normal=Array(6){DoubleArray(6)};val rhs=Array(axes){DoubleArray(6)}
        for(e in examples){val x=e.features();val y=e.delta();for(i in 0..5){for(j in 0..5)normal[i][j]+=x[i]*x[j];for(axis in 0 until axes)rhs[axis][i]+=x[i]*y[axis]}}
        for(i in 0..5)normal[i][i]+=.5
        return (0 until axes).map{axis->
            val m=Array(6){i->DoubleArray(7){j->if(j==6)rhs[axis][i] else normal[i][j]}}
            for(col in 0..5){val pivot=(col..5).maxBy{abs(m[it][col])};val swap=m[col];m[col]=m[pivot];m[pivot]=swap
                val divisor=m[col][col];require(abs(divisor)>1e-12);for(j in col..6)m[col][j]/=divisor
                for(row in 0..5)if(row!=col){val factor=m[row][col];for(j in col..6)m[row][j]-=factor*m[col][j]}}
            (0..5).map{m[it][6]}
        }
    }
    private fun errorByImage(examples:List<CorrectionExample>,predict:(CorrectionExample)->DoubleArray):Double = examples.groupBy{it.imageKey}.values.map{rows->
        rows.map{e->val actual=e.delta();val predicted=predict(e);actual.indices.sumOf{axis->val d=predicted[axis]-actual[axis];d*d}/actual.size}.average()
    }.average()
    fun train(group:CorrectionGroup):CorrectionEvaluation {
        val clean=group.examples.filter(::eligible);val train=clean.filterNot{holdout(it.imageKey)};val test=clean.filter{holdout(it.imageKey)}
        val trainIds=train.map{it.imageKey}.toSet();val testIds=test.map{it.imageKey}.toSet()
        if(trainIds.size<32 || testIds.size<8)return CorrectionEvaluation(group.copy(report=tr("En attente : ${trainIds.size}/32 images d’apprentissage, ${testIds.size}/8 de contrôle", "Waiting: ${trainIds.size}/32 training images, ${testIds.size}/8 validation images")),trainIds,testIds,false)
        val kind=train.first().kind
        require(train.all{it.kind==kind} && test.all{it.kind==kind})
        val axes=train.first().delta().size
        val candidate=CorrectionHead(solve(train),(group.head?.generation ?: 0)+1,kind)
        val baseline=DoubleArray(axes){axis->clamp(train.sumOf{it.delta()[axis]}/(train.size+10))}
        val raw=errorByImage(test){DoubleArray(axes)}
        val calibration=errorByImage(test){baseline}
        val prior=errorByImage(test){predict(group.head,it.features()) ?: baseline}
        val score=errorByImage(test){predict(candidate,it.features())!!}
        val promoted=score+1e-12<minOf(raw,calibration,prior)*.95
        val report=tr("${if(promoted)"Activé" else "Précédent conservé"} · ${trainIds.size} images train / ${testIds.size} contrôle · MSE brute=$raw calibration=$calibration précédent=$prior candidat=$score. Contrôle réutilisé, pas une évaluation indépendante.", "${if(promoted)"Activated" else "Previous retained"} · ${trainIds.size} training / ${testIds.size} validation images · raw MSE=$raw calibration=$calibration previous=$prior candidate=$score. Reused validation set, not an independent evaluation.")
        return CorrectionEvaluation(group.copy(head=if(promoted)candidate else group.head,report=report),trainIds,testIds,promoted)
    }
    fun apply(proposals:List<ModelProposal>,ledger:CorrectionLedger):List<ModelProposal> {
        require(ledger.schema==1)
        val groups=ledger.groups.associateBy{it.key}
        return proposals.map{p->
            if(!p.source.startsWith("model_litert:")) p else when(p.type) {
                "point" -> {
                    val head=groups[groupKey(p.source,p.label)]?.head
                    val x=(p.modelX ?: p.pointX).toDouble();val y=(p.modelY ?: p.pointY).toDouble()
                    val delta=predict(head,doubleArrayOf(1.0,x-.5,y-.5,p.boxWidth.toDouble(),p.boxHeight.toDouble(),p.score-.5))
                    if(delta==null || delta.size<2)p else p.copy(pointX=(x+delta[0]).coerceIn(0.0,1.0).toFloat(),pointY=(y+delta[1]).coerceIn(0.0,1.0).toFloat(),modelX=x.toFloat(),modelY=y.toFloat(),correctionGeneration=head?.generation)
                }
                "box" -> {
                    val head=groups[groupKey(p.source,p.label,"box")]?.head
                    val x1=p.modelXmin ?: p.xmin;val y1=p.modelYmin ?: p.ymin
                    val x2=p.modelXmax ?: p.xmax;val y2=p.modelYmax ?: p.ymax
                    val cx=(x1+x2)/2.0;val cy=(y1+y2)/2.0;val w=(x2-x1).toDouble();val h=(y2-y1).toDouble()
                    val delta=predict(head,doubleArrayOf(1.0,cx-.5,cy-.5,w,h,p.score-.5))
                    if(delta==null || delta.size<4)p else {
                        val ncx=(cx+delta[0]).coerceIn(0.0,1.0);val ncy=(cy+delta[1]).coerceIn(0.0,1.0)
                        val nw=(w+delta[2]).coerceIn(.001,1.0);val nh=(h+delta[3]).coerceIn(.001,1.0)
                        p.copy(xmin=(ncx-nw/2).coerceIn(0.0,1.0).toFloat(),ymin=(ncy-nh/2).coerceIn(0.0,1.0).toFloat(),xmax=(ncx+nw/2).coerceIn(0.0,1.0).toFloat(),ymax=(ncy+nh/2).coerceIn(0.0,1.0).toFloat(),correctionGeneration=head?.generation,
                            modelXmin=x1,modelYmin=y1,modelXmax=x2,modelYmax=y2)
                    }
                }
                else -> p
            }
        }
    }
}
