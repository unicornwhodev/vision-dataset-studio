package com.unicornwhodev.visiondatasetstudio.domain.validation

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioTask
import com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations
import com.unicornwhodev.visiondatasetstudio.data.model.PointLocalizationState
import com.unicornwhodev.visiondatasetstudio.data.model.InstanceLinks

/** No heuristically inferred empty annotation, no automatic acceptance of model suggestions. */
object AnnotationReview {
    fun problems(a: SampleAnnotations, tasks: Set<StudioTask>): List<String> = buildList {
        fun valid(v: Float) = v.isFinite() && v in 0f..1f
        val visiblePoints = a.points.filter { it.effectiveLocalizationState==PointLocalizationState.LOCALIZED }
        if (a.points.any { !valid(it.x) || !valid(it.y) || it.label.isBlank() || (it.localizationState==null && it.isAbsent && it.isAbstained) })
            add(tr("Un point contient des coordonnées, une classe ou un état invalide.", "A point has invalid coordinates, class or status."))
        if (a.boxes.any { !valid(it.xmin) || !valid(it.ymin) || !valid(it.xmax) || !valid(it.ymax) || it.xmin >= it.xmax || it.ymin >= it.ymax || it.label.isBlank() })
            add(tr("Une boîte est vide, hors image ou sans classe.", "A box is empty, outside the image or has no class."))
        if (StudioTask.POINTING in tasks && a.points.size > 1) add(tr("Le profil Point unique n’autorise qu’une cible. Choisissez Points multiples pour ce cas.", "Single-point mode allows only one target. Choose Multiple points for this sample."))
        if ((a.points.any { !it.isHumanVerified }) || a.boxes.any { !it.isHumanVerified } || a.tags.any { !it.isHumanVerified } || a.captions.any { !it.isHumanVerified && it.sourceProvenance != "human" } || a.vqaList.any { !it.isHumanVerified && it.sourceProvenance != "human" } || a.counts.any { !it.isHumanVerified && it.sourceProvenance != "human" } || a.groundings.any { !it.isHumanVerified })
            add(tr("Des propositions restent à relire. Acceptez-les explicitement ou supprimez-les.", "Some proposals still need review. Explicitly accept or delete them."))
        if(a.masks.any { it.label.isBlank() || runCatching { com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.validate(it) }.isFailure }) add(tr("Un masque est invalide.", "A mask is invalid."))
        if(a.masks.any { !it.isHumanVerified }) add(tr("Des masques restent à relire.", "Some masks still need review."))
        if(StudioTask.SEGMENTATION in tasks && a.masks.none { it.runs.withIndex().any { (i,n) -> i%2==1 && n>0 } } && a.quality.verifiedNegativeQueries.isEmpty()) add(tr("Dessinez un masque ou vérifiez son absence.", "Draw a mask or verify absence."))
        val located = a.masks.any { it.runs.withIndex().any { (i,n) -> i%2==1 && n>0 } } || a.boxes.isNotEmpty() || visiblePoints.isNotEmpty()
        val absent = a.quality.verifiedNegativeQueries.isNotEmpty() || a.quality.isHardNegative
        if (absent && located) add(tr("Une absence globale ne peut pas coexister avec une cible localisée. Précisez les annotations de ce cas.", "Global absence cannot coexist with a located target. Clarify this sample's annotations."))
        if (absent && a.quality.isUnlocalizablePresent) add(tr("Une cible ne peut pas être à la fois absente et présente non localisable.", "A target cannot be both absent and present but unlocatable."))
        if (a.quality.isUncertain) add(tr("L’incertitude doit être résolue avant validation; utilisez Différer pour conserver ce cas à revoir.", "Resolve uncertainty before approval; use Defer to keep this sample for review."))
        if(a.points.any{it.effectiveLocalizationState==PointLocalizationState.UNCERTAIN}) add(tr("L’incertitude d’une cible doit être résolue avant validation; utilisez Différer pour la conserver.", "Resolve target uncertainty before approval; use Defer to preserve it."))
        val allIds = a.boxes.map { it.id } + a.points.map { it.id } + a.masks.map { it.id }
        if (allIds.any { it.isBlank() } || allIds.distinct().size != allIds.size) add(tr("Les identifiants de régions doivent être renseignés et uniques.", "Region identifiers must be populated and unique."))
        addAll(InstanceLinks.problems(a))
        if (a.captions.any { it.text.isBlank() || it.language.isBlank() }) add(tr("Une légende est vide ou sans langue.", "A caption is empty or has no language."))
        if (a.tags.any { it.label.isBlank() }) add(tr("Un tag est vide.", "A tag is empty."))
        val explicitSpatialState = absent || a.quality.isUnlocalizablePresent || a.points.any{it.effectiveLocalizationState in setOf(PointLocalizationState.ABSENT,PointLocalizationState.UNLOCALIZABLE)}
        if ((StudioTask.POINTING in tasks || StudioTask.POINTING_MULTI in tasks) && a.points.isEmpty() && !explicitSpatialState)
            add(tr("Placez une cible ou indiquez explicitement son absence / sa non-localisabilité dans Qualité.", "Place a target or explicitly mark absence / unlocatability in Quality."))
        if (StudioTask.DETECTION in tasks && a.boxes.isEmpty() && !explicitSpatialState)
            add(tr("Dessinez une boîte ou renseignez un état explicite dans Qualité.", "Draw a box or set an explicit state in Quality."))
        if (StudioTask.CAPTIONING in tasks && a.captions.none { it.text.isNotBlank() }) add(tr("Ajoutez au moins une légende.", "Add at least one caption."))
        if (StudioTask.CLASSIFICATION in tasks && a.tags.none { it.label.isNotBlank() }) add(tr("Ajoutez au moins un tag.", "Add at least one tag."))
        if (StudioTask.VQA in tasks && a.vqaList.none { it.question.isNotBlank() && (it.answer.isNotBlank() || it.isAbstained) }) add(tr("Ajoutez une question avec réponse ou abstention.", "Add a question with an answer or abstention."))
        if (a.vqaList.any { it.question.isBlank() || (!it.isAbstained && it.answer.isBlank()) }) add(tr("Une paire question-réponse est incomplète.", "A question-answer pair is incomplete."))
        if (StudioTask.COUNTING in tasks && a.counts.isEmpty()) add(tr("Renseignez un comptage (zéro est une valeur valide).", "Enter a count (zero is valid)."))
        if (a.counts.any { it.count < 0 || it.label.isBlank() }) add(tr("Un comptage est négatif ou sans classe.", "A count is negative or has no class."))
        val ids = a.boxes.map { it.id }.toSet() + a.points.map { it.id }
        if (StudioTask.GROUNDING in tasks && a.groundings.isEmpty()) add(tr("Reliez une expression à au moins une région.", "Link an expression to at least one region."))
        if (a.groundings.any { it.phrase.isBlank() || (it.boxIds + it.pointIds).isEmpty() || !(it.boxIds + it.pointIds).all(ids::contains) })
            add(tr("Une expression de grounding est vide ou référence une région supprimée.", "A grounding expression is empty or references a deleted region."))
        if (a.counts.any { !it.linkedInstanceIds.all(ids::contains) } || a.vqaList.any { !it.targetIds.all(ids::contains) })
            add(tr("Une question ou un comptage référence une région supprimée.", "A question or count references a deleted region."))
        if (StudioTask.NEGATIVE in tasks && !absent && !a.quality.isUnlocalizablePresent && !a.quality.isUncertain && a.quality.auditNotes.isBlank() && a.tags.isEmpty())
            add(tr("Ajoutez une décision qualité, un tag ou une note d’audit.", "Add a quality decision, tag or audit note."))
    }.distinct()
}
