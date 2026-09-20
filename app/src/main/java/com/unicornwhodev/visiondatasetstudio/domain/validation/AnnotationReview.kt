package com.unicornwhodev.visiondatasetstudio.domain.validation

import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioTask
import com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations

/** No heuristically inferred empty annotation, no automatic acceptance of model suggestions. */
object AnnotationReview {
    fun problems(a: SampleAnnotations, tasks: Set<StudioTask>): List<String> = buildList {
        fun valid(v: Float) = v.isFinite() && v in 0f..1f
        val visiblePoints = a.points.filterNot { it.isAbsent || it.isAbstained }
        if (a.points.any { !valid(it.x) || !valid(it.y) || it.label.isBlank() || (it.isAbsent && it.isAbstained) })
            add("Un point contient des coordonnées, une classe ou un état invalide.")
        if (a.boxes.any { !valid(it.xmin) || !valid(it.ymin) || !valid(it.xmax) || !valid(it.ymax) || it.xmin >= it.xmax || it.ymin >= it.ymax || it.label.isBlank() })
            add("Une boîte est vide, hors image ou sans classe.")
        if (StudioTask.POINTING in tasks && a.points.size > 1) add("Le profil Point unique n’autorise qu’une cible. Choisissez Points multiples pour ce cas.")
        if ((a.points.any { !it.isHumanVerified }) || a.boxes.any { !it.isHumanVerified } || a.tags.any { !it.isHumanVerified } || a.captions.any { !it.isHumanVerified && it.sourceProvenance != "human" } || a.vqaList.any { !it.isHumanVerified && it.sourceProvenance != "human" } || a.counts.any { !it.isHumanVerified && it.sourceProvenance != "human" })
            add("Des propositions restent à relire. Acceptez-les explicitement ou supprimez-les.")
        if(a.masks.any { it.label.isBlank() || runCatching { com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.validate(it) }.isFailure }) add("Un masque est invalide.")
        if(a.masks.any { !it.isHumanVerified }) add("Des masques restent à relire.")
        if(StudioTask.SEGMENTATION in tasks && a.masks.none { it.runs.withIndex().any { (i,n) -> i%2==1 && n>0 } } && a.quality.verifiedNegativeQueries.isEmpty()) add("Dessinez un masque ou vérifiez son absence.")
        val located = a.masks.any { it.runs.withIndex().any { (i,n) -> i%2==1 && n>0 } } || a.boxes.isNotEmpty() || visiblePoints.isNotEmpty()
        val absent = a.quality.verifiedNegativeQueries.isNotEmpty() || a.quality.isHardNegative
        if (absent && located) add("Une absence globale ne peut pas coexister avec une cible localisée. Précisez les annotations de ce cas.")
        if (absent && a.quality.isUnlocalizablePresent) add("Une cible ne peut pas être à la fois absente et présente non localisable.")
        if (a.quality.isUncertain) add("L’incertitude doit être résolue avant validation; utilisez Différer pour conserver ce cas à revoir.")
        val allIds = a.boxes.map { it.id } + a.points.map { it.id } + a.masks.map { it.id }
        if (allIds.any { it.isBlank() } || allIds.distinct().size != allIds.size) add("Les identifiants de régions doivent être renseignés et uniques.")
        if (a.captions.any { it.text.isBlank() || it.language.isBlank() }) add("Une légende est vide ou sans langue.")
        if (a.tags.any { it.label.isBlank() }) add("Un tag est vide.")
        val explicitSpatialState = absent || a.quality.isUnlocalizablePresent
        if ((StudioTask.POINTING in tasks || StudioTask.POINTING_MULTI in tasks) && a.points.isEmpty() && !explicitSpatialState)
            add("Placez une cible ou indiquez explicitement son absence / sa non-localisabilité dans Qualité.")
        if (StudioTask.DETECTION in tasks && a.boxes.isEmpty() && !explicitSpatialState)
            add("Dessinez une boîte ou renseignez un état explicite dans Qualité.")
        if (StudioTask.CAPTIONING in tasks && a.captions.none { it.text.isNotBlank() }) add("Ajoutez au moins une légende.")
        if (StudioTask.CLASSIFICATION in tasks && a.tags.none { it.label.isNotBlank() }) add("Ajoutez au moins un tag.")
        if (StudioTask.VQA in tasks && a.vqaList.none { it.question.isNotBlank() && (it.answer.isNotBlank() || it.isAbstained) }) add("Ajoutez une question avec réponse ou abstention.")
        if (a.vqaList.any { it.question.isBlank() || (!it.isAbstained && it.answer.isBlank()) }) add("Une paire question-réponse est incomplète.")
        if (StudioTask.COUNTING in tasks && a.counts.isEmpty()) add("Renseignez un comptage (zéro est une valeur valide).")
        if (a.counts.any { it.count < 0 || it.label.isBlank() }) add("Un comptage est négatif ou sans classe.")
        val ids = a.boxes.map { it.id }.toSet() + a.points.map { it.id }
        if (StudioTask.GROUNDING in tasks && a.groundings.isEmpty()) add("Reliez une expression à au moins une région.")
        if (a.groundings.any { it.phrase.isBlank() || (it.boxIds + it.pointIds).isEmpty() || !(it.boxIds + it.pointIds).all(ids::contains) })
            add("Une expression de grounding est vide ou référence une région supprimée.")
        if (a.counts.any { !it.linkedInstanceIds.all(ids::contains) } || a.vqaList.any { !it.targetIds.all(ids::contains) })
            add("Une question ou un comptage référence une région supprimée.")
        if (StudioTask.NEGATIVE in tasks && !absent && !a.quality.isUnlocalizablePresent && !a.quality.isUncertain && a.quality.auditNotes.isBlank() && a.tags.isEmpty())
            add("Ajoutez une décision qualité, un tag ou une note d’audit.")
    }.distinct()
}
