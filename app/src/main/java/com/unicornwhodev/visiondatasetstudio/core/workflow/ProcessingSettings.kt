package com.unicornwhodev.visiondatasetstudio.core.workflow

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.squareup.moshi.JsonClass

/** Versioned, domain-independent policy. Only settings with an implemented execution path are exposed. */
@JsonClass(generateAdapter = true)
data class ProcessingSettings(
    val schemaVersion: Int = 1,
    val batchSize: Int = 100,
    val sourceMode: String = "HF_VIEWER", // HF_VIEWER, HF_MANIFEST, LOCAL_INDEX
    val sourceRevision: String = "main",
    val resolvedSourceRevision: String? = null,
    val manifestPath: String = "metadata.jsonl",
    val sourceIndexReady: Boolean = false,
    val localSourceLabel: String = "",
    val localTreeUri: String? = null,
    val importAnnotations: Boolean = false,
    val filterExpression: String = "",
    val orderBy: String = "",
    val downloadConcurrency: Int = 2,
    val retryCount: Int = 2,
    val timeoutSeconds: Int = 60,
    val maxImageMb: Int = 32,
    val reserveFreeMb: Int = 64,
    val allowMetered: Boolean = true,
    val keepVerifiedBatches: Boolean = false,
    val normalizeExif: Boolean = true,
    val sourceMaxPixels: Long = 80_000_000,
    val destBranch: String = "main",
    val destPrefix: String = "studio",
    val hfWebDataset: Boolean = true,
    val hfCoco: Boolean = false,
    val hfYolo: Boolean = false,
    val hfVl: Boolean = true,
    val autoPreannotate: Boolean = true,
    val adaptiveCorrection: Boolean = false,
    val continuousTraining: Boolean = false,
    // Optional cooperative claiming for several people processing the same HF-backed corpus.
    // Claims are stored in the destination dataset repo using optimistic Hub commits.
    val collaborationEnabled: Boolean = false,
    val collaborationWorkerId: String = "",
    val claimLeaseMinutes: Int = 720
) {
    fun validate(): ProcessingSettings {
        require(schemaVersion == 1) { tr("Version des réglages inconnue", "Unknown settings version") }
        require(batchSize in 1..1000) { tr("Lot : 1 à 1 000 cas", "Batch: 1 to 1,000 samples") }
        require(sourceMode in setOf("HF_VIEWER", "HF_MANIFEST", "LOCAL_INDEX"))
        require(downloadConcurrency in 1..4 && retryCount in 0..5)
        require(timeoutSeconds in 10..300 && maxImageMb in 1..256 && reserveFreeMb in 32..4096)
        require(sourceMaxPixels in 1_000_000..100_000_000)
        require(validRevision(destBranch)) { tr("Branche de destination invalide", "Invalid destination branch") }
        require(validRevision(sourceRevision)) { tr("Révision source invalide", "Invalid source revision") }
        require(safeRelativePath(destPrefix)) { tr("Préfixe de destination invalide", "Invalid destination prefix") }
        require(safeRelativePath(manifestPath)) { tr("Chemin du manifeste invalide", "Invalid manifest path") }
        require(filterExpression.length <= 4000 && orderBy.length <= 500)
        require(claimLeaseMinutes in 15..4320) { tr("Bail partagé : 15 minutes à 72 heures", "Shared lease: 15 minutes to 72 hours") }
        if (collaborationEnabled) require(collaborationWorkerId.matches(Regex("[A-Za-z0-9._-]{3,64}"))) {
            tr("Identifiant collaborateur : 3 à 64 caractères (lettres, chiffres, . _ -)", "Collaborator ID: 3 to 64 characters (letters, digits, . _ -)")
        }
        return this
    }
    companion object {
        fun validRevision(s: String): Boolean = s.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}")) &&
            !s.contains("..") && !s.contains("//") && !s.endsWith('/') && !s.endsWith('.') &&
            s.split('/').none { it.endsWith(".lock") || it.startsWith('.') }
        fun safeRelativePath(s: String): Boolean = s.isNotBlank() && s.length <= 500 &&
            !s.startsWith('/') && !s.contains('\\') && !s.contains('%') &&
            s.split('/').all { it.isNotBlank() && it != "." && it != ".." && it.none { c -> c.code < 32 || c in "?#:" } }
        fun pageSizes(total: Int): List<Int> {
            require(total in 1..1000)
            return List(total / 100) { 100 } + if (total % 100 > 0) listOf(total % 100) else emptyList()
        }
    }
}


/** JSON parsers may decode numbers as Double: refuse identifiers that could already have lost precision. */
object SourceIdentity {
    fun string(value: Any?): String? = when(value) {
        null -> null
        is String -> value.also { require(it.isNotBlank() && it.length<=4096) { tr("Identifiant vide ou trop long", "Empty or excessive identifier") } }
        is Byte, is Short, is Int, is Long -> value.toString()
        is Number -> value.toDouble().let { n ->
            require(n.isFinite() && kotlin.math.abs(n)<=9_007_199_254_740_991.0 && n%1.0==0.0) {
                tr("Identifiant numérique ambigu : utilisez une chaîne JSON, notamment au-delà de 2^53-1", "Ambiguous numeric ID: use a JSON string, especially above 2^53-1")
            }; n.toLong().toString()
        }
        else -> error(tr("Identifiant non scalaire : une chaîne JSON est attendue", "Non-scalar ID: expected a JSON string"))
    }
}

/** Pure claim-state rule shared by the HF coordinator and portable regression tests. */
object SharedClaimPolicy {
    fun unavailable(state: String, owner: String, expiresAt: Long, currentWorker: String, now: Long): Boolean = when {
        state == "DONE" -> true
        state == "CLAIMED" && expiresAt > now && owner != currentWorker -> true
        else -> false
    }
    fun reusableByCurrentWorker(state: String, owner: String, expiresAt: Long, currentWorker: String, now: Long): Boolean =
        state == "CLAIMED" && owner == currentWorker && expiresAt > now
}
