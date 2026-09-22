package com.unicornwhodev.visiondatasetstudio.domain.inference

/** Canonical wire contract for phrase-to-region model responses. */
object GroundingProposalContract {
    private val regionTypes = setOf("box", "point")

    fun validateLinks(proposals: List<ModelProposal>) {
        val groundings = proposals.filter { it.type == "grounding" }
        if (groundings.isEmpty()) return
        val regions = proposals.filter { it.type in regionTypes }
        val regionIds = regions.map { it.proposalId }
        require(regionIds.none(String::isBlank) && regionIds.distinct().size == regionIds.size) {
            "Le grounding exige des identifiants de régions renseignés et uniques"
        }
        groundings.forEach { grounding ->
            require(grounding.text.isNotBlank()) { "Une expression de grounding est vide" }
            require(grounding.linkedProposalIds.isNotEmpty() && grounding.linkedProposalIds.distinct().size == grounding.linkedProposalIds.size) {
                "Grounding sans lien explicite vers une région"
            }
            require(grounding.linkedProposalIds.all(regionIds.toSet()::contains)) {
                "Grounding lié à une région absente de la réponse"
            }
        }
    }

    fun validateAndFilter(proposals: List<ModelProposal>, allowedTypes: Set<String>, threshold: Float): List<ModelProposal> {
        require(proposals.size <= 1000) { "Trop de propositions dans la réponse locale" }
        proposals.forEach { p ->
            require(p.type in allowedTypes) { "Type de proposition non déclaré dans la tâche du contrat" }
            if (p.type in setOf("point", "box", "tag", "count")) require(p.label.isNotBlank())
            if (p.type in setOf("caption", "vqa", "grounding")) require(p.text.isNotBlank())
            if (p.type == "vqa") require(p.question.isNotBlank())
            require(p.score.isFinite() && p.score in 0f..1f && p.text.length <= 32_000 && p.question.length <= 8000 && p.count >= 0)
            if (p.type == "point") require(p.pointX.isFinite() && p.pointY.isFinite() && p.pointX in 0f..1f && p.pointY in 0f..1f)
            if (p.type == "box") require(listOf(p.xmin,p.ymin,p.xmax,p.ymax).all { it.isFinite() && it in 0f..1f } && p.xmax > p.xmin && p.ymax > p.ymin)
        }
        validateLinks(proposals)
        val accepted = proposals.filter { it.score >= threshold }
        val acceptedRegions = accepted.filter { it.type in regionTypes }.map { it.proposalId }.toSet()
        require(accepted.filter { it.type == "grounding" }.all { it.linkedProposalIds.all(acceptedRegions::contains) }) {
            "Une région référencée par le grounding est sous le seuil"
        }
        return accepted
    }
}
