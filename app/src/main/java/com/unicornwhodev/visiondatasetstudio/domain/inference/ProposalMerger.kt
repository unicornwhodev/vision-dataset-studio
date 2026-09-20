package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.model.*
import java.util.UUID

/** Shared by single-image and batch execution. No human acceptance is used as a training label. */
object ProposalMerger {
    private fun id()=UUID.randomUUID().toString()
    private fun machine(source:String)=source.startsWith("model_")
    fun merge(a:SampleAnnotations,proposals:List<ModelProposal>,tasks:String,language:String="fr",replaceTypes:Set<String> = proposals.map{it.type}.toSet()):SampleAnnotations {
        val enabled=tasks.split(',').toSet()
        val references=(a.groundings.flatMap{it.boxIds+it.pointIds}+a.vqaList.flatMap{it.targetIds}+a.counts.flatMap{it.linkedInstanceIds}).toSet()
        fun retain(verified:Boolean,source:String,id:String)=verified || !machine(source) || id in references
        var points=a.points;var boxes=a.boxes;var tags=a.tags;var captions=a.captions;var counts=a.counts;var qa=a.vqaList
        if("point" in replaceTypes && ("POINTING" in enabled || "POINTING_MULTI" in enabled)) {
            val keep=points.filter{retain(it.isHumanVerified,it.sourceProvenance,it.id) || it.explicitlyAdjusted}
            val limit=if("POINTING_MULTI" in enabled)1000 else (1-keep.size).coerceAtLeast(0)
            points=keep+proposals.filter{it.type=="point"}.sortedByDescending{it.score}.take(limit).map{p->PointTarget(id(),p.pointX,p.pointY,p.label,modelScore=p.score,sourceProvenance=p.source,modelX=p.modelX,modelY=p.modelY,boxWidth=p.boxWidth,boxHeight=p.boxHeight,modelLabel=p.label,correctionGeneration=p.correctionGeneration)}
        }
        if("box" in replaceTypes && "DETECTION" in enabled) boxes=boxes.filter{retain(it.isHumanVerified,it.sourceProvenance,it.id) || it.explicitlyAdjusted}+proposals.filter{it.type=="box"}.map{p->BoxTarget(id(),p.xmin,p.ymin,p.xmax,p.ymax,p.label,modelScore=p.score,sourceProvenance=p.source,modelXmin=p.modelXmin ?: p.xmin,modelYmin=p.modelYmin ?: p.ymin,modelXmax=p.modelXmax ?: p.xmax,modelYmax=p.modelYmax ?: p.ymax,correctionGeneration=p.correctionGeneration,modelCoordinatesVersion=1)}
        if("tag" in replaceTypes && "CLASSIFICATION" in enabled) tags=tags.filter{retain(it.isHumanVerified,it.sourceProvenance,it.id)}+proposals.filter{it.type=="tag"}.map{p->TagTarget(id(),p.label,sourceProvenance=p.source)}
        if("caption" in replaceTypes && "CAPTIONING" in enabled) captions=captions.filter{retain(it.isHumanVerified,it.sourceProvenance,it.id)}+proposals.filter{it.type=="caption" && it.text.isNotBlank()}.map{p->CaptionTarget(id(),p.text,language,sourceProvenance=p.source)}
        if("vqa" in replaceTypes && "VQA" in enabled) qa=qa.filter{retain(it.isHumanVerified,it.sourceProvenance,it.id)}+proposals.filter{it.type=="vqa" && it.question.isNotBlank() && it.text.isNotBlank()}.map{p->VqaTarget(id(),p.question,p.text,sourceProvenance=p.source)}
        if("count" in replaceTypes && "COUNTING" in enabled) counts=counts.filter{retain(it.isHumanVerified,it.sourceProvenance,it.id)}+proposals.filter{it.type=="count"}.map{p->CountingTarget(id(),p.label,p.count,isExhaustive=false,sourceProvenance=p.source)}
        return a.copy(points=points,boxes=boxes,tags=tags,captions=captions,vqaList=qa,counts=counts)
    }
    fun draft(a:SampleAnnotations)=a.copy(
        points=a.points.map{it.copy(isHumanVerified=false,sourceProvenance="import",explicitlyAdjusted=false)},
        boxes=a.boxes.map{it.copy(isHumanVerified=false,sourceProvenance="import",explicitlyAdjusted=false,modelXmin=null,modelYmin=null,modelXmax=null,modelYmax=null,correctionGeneration=null)},
        captions=a.captions.map{it.copy(isHumanVerified=false,sourceProvenance="import")},
        tags=a.tags.map{it.copy(isHumanVerified=false,sourceProvenance="import")},
        groundings=a.groundings.map{it.copy(isHumanVerified=false)},vqaList=a.vqaList.map{it.copy(isHumanVerified=false,sourceProvenance="import")},
        counts=a.counts.map{it.copy(isHumanVerified=false,sourceProvenance="import")},quality=QualityAuditTarget(auditNotes="Annotations importées à relire; aucun état de validation importé."))
}
