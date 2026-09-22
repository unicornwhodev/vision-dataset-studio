package com.unicornwhodev.visiondatasetstudio.data.model

import com.squareup.moshi.JsonClass

enum class TaskType {
    POINTING,
    POINTING_MULTI,
    DETECTION,
    SEGMENTATION,
    CAPTIONING,
    CLASSIFICATION,
    GROUNDING,
    VQA,
    COUNTING,
    NEGATIVE
}

enum class AcquisitionStatus {
    DISCOVERED,
    DOWNLOADING,
    AVAILABLE,
    ERROR_RETRYABLE,
    ERROR_FATAL,
    DUPLICATE
}

enum class AnnotationStatus {
    PENDING,
    PROPOSALS_AVAILABLE,
    IN_PROGRESS,
    VALIDATED,
    REJECTED,
    DEFERRED,
    DUPLICATE
}

enum class SyncStatus {
    NOT_EXPORTED,
    PREPARED,
    PUBLISHING,
    PUBLISHED,
    VERIFIED,
    PURGED
}

enum class QualityFlag {
    VERIFIED_NEGATIVE,
    HARD_NEGATIVE,
    UNLOCALIZABLE,
    UNCERTAIN,
    OUT_OF_SCOPE,
    CORRUPTED
}

enum class PointLocalizationState { LOCALIZED, ABSENT, UNLOCALIZABLE, UNCERTAIN }

@JsonClass(generateAdapter = true)
data class PointTarget(
    val id: String,
    val x: Float,
    val y: Float,
    val label: String,
    val isAbsent: Boolean = false,
    val isAbstained: Boolean = false,
    /** Canonical per-target state. Null means legacy JSON; old booleans remain readable. */
    val localizationState: PointLocalizationState? = null,
    val isHumanVerified: Boolean = false,
    val modelScore: Float? = null,
    val sourceProvenance: String = "human",
    val modelX: Float? = null,
    val modelY: Float? = null,
    val boxWidth: Float = 0f,
    val boxHeight: Float = 0f,
    val explicitlyAdjusted: Boolean = false,
    val modelLabel: String? = null,
    val correctionGeneration: Int? = null,
    val instanceId: String? = null
) {
    val effectiveLocalizationState:PointLocalizationState get() = localizationState ?: when {
        isAbsent -> PointLocalizationState.ABSENT
        isAbstained -> PointLocalizationState.UNLOCALIZABLE
        else -> PointLocalizationState.LOCALIZED
    }
    val isLocalized get()=effectiveLocalizationState==PointLocalizationState.LOCALIZED
    val canProvideCoordinates get()=isLocalized
    val requiresDeferral get()=effectiveLocalizationState==PointLocalizationState.UNCERTAIN
    fun withLocalizationState(state:PointLocalizationState)=copy(localizationState=state,
        isAbsent=state==PointLocalizationState.ABSENT,
        isAbstained=state==PointLocalizationState.UNLOCALIZABLE||state==PointLocalizationState.UNCERTAIN)
}

@JsonClass(generateAdapter = true)
data class BoxTarget(
    val id: String,
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val label: String,
    val isHumanVerified: Boolean = false,
    val modelScore: Float? = null,
    val sourceProvenance: String = "human",
    val modelXmin: Float? = null,
    val modelYmin: Float? = null,
    val modelXmax: Float? = null,
    val modelYmax: Float? = null,
    val explicitlyAdjusted: Boolean = false,
    val correctionGeneration: Int? = null,
    val modelCoordinatesVersion: Int = 0,
    val instanceId: String? = null
)

@JsonClass(generateAdapter = true)
data class CaptionTarget(
    val id: String,
    val text: String,
    val language: String = "fr",
    val isDetailed: Boolean = false,
    val isHumanVerified: Boolean = false,
    val sourceProvenance: String = "human"
)

@JsonClass(generateAdapter = true)
data class TagTarget(
    val id: String,
    val label: String,
    val isMultiLabel: Boolean = true,
    val isHumanVerified: Boolean = false,
    val sourceProvenance: String = "human"
)

@JsonClass(generateAdapter = true)
data class GroundingTarget(
    val id: String,
    val phrase: String,
    val boxIds: List<String> = emptyList(),
    val pointIds: List<String> = emptyList(),
    val isHumanVerified: Boolean = false,
    val sourceProvenance: String = "human"
)

@JsonClass(generateAdapter = true)
data class VqaTarget(
    val id: String,
    val question: String,
    val answer: String,
    val isAbstained: Boolean = false,
    val targetIds: List<String> = emptyList(),
    val isHumanVerified: Boolean = false,
    val sourceProvenance: String = "human"
)

@JsonClass(generateAdapter = true)
data class CountingTarget(
    val id: String,
    val label: String,
    val count: Int,
    val isExhaustive: Boolean = true,
    val linkedInstanceIds: List<String> = emptyList(),
    val isHumanVerified: Boolean = false,
    val sourceProvenance: String = "human"
)

@JsonClass(generateAdapter = true)
data class QualityAuditTarget(
    val verifiedNegativeQueries: List<String> = emptyList(),
    val isHardNegative: Boolean = false,
    val isUnlocalizablePresent: Boolean = false,
    val isUncertain: Boolean = false,
    val rejectedReason: String? = null,
    val auditNotes: String = ""
)

@JsonClass(generateAdapter = true)
data class MaskTarget(
    val id: String, val label: String, val width: Int, val height: Int, val runs: List<Int>,
    val isHumanVerified: Boolean = false, val sourceProvenance: String = "human",
    val modelScore: Float? = null, val explicitlyAdjusted: Boolean = false, val instanceId: String? = null
)

/**
 * Full state bundle of annotations for a single sample.
 */
@JsonClass(generateAdapter = true)
data class SampleAnnotations(
    val points: List<PointTarget> = emptyList(),
    val boxes: List<BoxTarget> = emptyList(),
    val captions: List<CaptionTarget> = emptyList(),
    val tags: List<TagTarget> = emptyList(),
    val groundings: List<GroundingTarget> = emptyList(),
    val vqaList: List<VqaTarget> = emptyList(),
    val counts: List<CountingTarget> = emptyList(),
    val masks: List<MaskTarget> = emptyList(),
    val quality: QualityAuditTarget = QualityAuditTarget()
)

/** Explicit instance relations only: geometry and labels never create links implicitly. */
object InstanceLinks {
    private data class Member(val id:String,val kind:String,val label:String,val instanceId:String?)
    private fun members(a:SampleAnnotations)=
        a.boxes.map{Member(it.id,"box",it.label,it.instanceId)}+
        a.masks.map{Member(it.id,"mask",it.label,it.instanceId)}+
        a.points.map{Member(it.id,"point",it.label,it.instanceId)}

    fun instanceId(a:SampleAnnotations,targetId:String)=members(a).firstOrNull{it.id==targetId}?.instanceId
    fun compatibleTargets(a:SampleAnnotations,targetId:String):List<String> {
        val source=members(a).singleOrNull{it.id==targetId} ?: return emptyList()
        val occupiedKinds=source.instanceId?.let{id->members(a).filter{it.instanceId==id}.map{it.kind}.toSet()}.orEmpty()
        return members(a).filter{it.id!=targetId&&it.kind!=source.kind&&it.label==source.label&&(it.kind !in occupiedKinds||it.instanceId==source.instanceId)}.map{it.id}
    }
    fun unlink(a:SampleAnnotations,targetId:String)=a.copy(
        boxes=a.boxes.map{if(it.id==targetId)it.copy(instanceId=null)else it},
        masks=a.masks.map{if(it.id==targetId)it.copy(instanceId=null)else it},
        points=a.points.map{if(it.id==targetId)it.copy(instanceId=null)else it})
    fun link(a:SampleAnnotations,targetIds:Set<String>,instanceId:String):SampleAnnotations {
        require(instanceId.isNotBlank()&&targetIds.isNotEmpty())
        val selected=members(a).filter{it.id in targetIds}
        require(selected.size==targetIds.size&&selected.map{it.kind}.distinct().size==selected.size)
        require(selected.map{it.label}.distinct().size==1){"Une instance liée doit conserver la même classe"}
        val cleared=targetIds.fold(a){state,id->unlink(state,id)}
        return cleared.copy(
            boxes=cleared.boxes.map{if(it.id in targetIds)it.copy(instanceId=instanceId)else it},
            masks=cleared.masks.map{if(it.id in targetIds)it.copy(instanceId=instanceId)else it},
            points=cleared.points.map{if(it.id in targetIds)it.copy(instanceId=instanceId)else it})
    }
    fun problems(a:SampleAnnotations):List<String> = buildList {
        val linked=members(a).filter{it.instanceId!=null}
        if(linked.any{it.instanceId!!.isBlank()})add("Un identifiant d’instance est vide.")
        linked.filter{!it.instanceId.isNullOrBlank()}.groupBy{it.instanceId}.forEach{(_,group)->
            if(group.groupBy{it.kind}.any{it.value.size>1})add("Une instance contient plusieurs régions du même type.")
            if(group.map{it.label}.distinct().size>1)add("Une instance contient des classes contradictoires.")
        }
    }.distinct()
}

/**
 * Master Canonical JSON Schema for exports (spec v1.0.0).
 */
@JsonClass(generateAdapter = true)
data class CanonicalDatasetSample(
    val sample_id: String,
    val asset_id: String,
    val dataset_source: String,
    val source_revision: String,
    val source_config: String,
    val source_split: String,
    val source_row_index: Long,
    val group_id: String?,
    val split: String,
    val media: CanonicalMediaInfo,
    val annotations: SampleAnnotations,
    val review_status: String,
    val audit: CanonicalAuditInfo
)

@JsonClass(generateAdapter = true)
data class CanonicalMediaInfo(
    val filename: String,
    val width: Int,
    val height: Int,
    val mime_type: String,
    val sha256: String,
    val phash: String?,
    val original_url: String?,
    val source_sha256: String? = null,
    val transform: String = "identity"
)

@JsonClass(generateAdapter = true)
data class CanonicalAuditInfo(
    val created_at: Long,
    val updated_at: Long,
    val validated_by: String = "human_curator",
    val model_assist_used: Boolean = false
)
