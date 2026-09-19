package com.unicornwhodev.visiondatasetstudio.core.workflow

/** HTTP byte ranges are accepted only with a strong validator and a known complete size. */
object RangeSafety {
    data class ContentRange(val start: Long, val end: Long, val total: Long)
    fun strongEtag(value: String?): Boolean = value != null && value.length in 2..1024 &&
        value.startsWith('"') && value.endsWith('"') && !value.contains('\n') && !value.contains('\r')
    fun parse(value: String?): ContentRange? {
        val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(value ?: return null) ?: return null
        val values = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
        val (start, end, total) = values
        if (total <= 0 || start > end || end >= total) return null
        return ContentRange(start, end, total)
    }
    fun validResume(offset: Long, oldEtag: String?, newEtag: String?, range: ContentRange?, maxBytes: Long): Boolean =
        offset > 0 && strongEtag(oldEtag) && newEtag == oldEtag && range != null &&
        range.start == offset && range.end == range.total - 1 && range.total <= maxBytes
}

enum class ResumeDecision { COMMITTED, RETRY_SAME_PARENT, CONFLICT }
object PublicationSafety {
    /** Never silently rebase an uncertain commit, even when the new head looks unrelated. */
    fun decide(expectedParent: String, currentHead: String, allFilesMatch: Boolean): ResumeDecision {
        require(expectedParent.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")))
        require(currentHead.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")))
        return when {
            allFilesMatch -> ResumeDecision.COMMITTED
            currentHead == expectedParent -> ResumeDecision.RETRY_SAME_PARENT
            else -> ResumeDecision.CONFLICT
        }
    }
    val lockedStates = setOf("PREPARED", "PUBLISHING", "PUBLISHED", "CONFLICT", "VERIFIED", "PURGING", "PURGED")
}

object PerformanceStats {
    fun percentile(values: List<Double>, percentile: Double): Double {
        require(values.isNotEmpty() && values.all { it.isFinite() && it >= 0.0 })
        require(percentile in 0.0..1.0)
        val sorted = values.sorted()
        val index = (kotlin.math.ceil(percentile * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
