package com.unicornwhodev.visiondatasetstudio.domain.batch

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import android.content.Context
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.core.workflow.SharedClaimPolicy
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SourceEntryEntity
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import java.io.File
import java.security.MessageDigest

@JsonClass(generateAdapter = true)
data class RemoteWorkClaim(
    val schema: Int = 1,
    val state: String,
    val owner: String,
    val sourceKey: String,
    val assetId: String,
    val sourceOrdinal: Long,
    val claimedAt: Long,
    val expiresAt: Long,
    val completedAt: Long? = null
)

data class ClaimSelection(val entries: List<SourceEntryEntity>, val consumed: Int, val skipped: Int)

/**
 * Cooperative reservation using deterministic files in the destination dataset repository.
 * Hub parent-commit conflicts are the lock: two clients cannot successfully claim the same parent concurrently.
 */
class WorkClaimCoordinator internal constructor(private val cacheRoot: File, private val hf: HfApiClient) {
    constructor(context: Context, hf: HfApiClient) : this(context.cacheDir, hf)
    private val adapter = StudioJson.moshi.adapter(RemoteWorkClaim::class.java).failOnUnknown()
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun sourceKey(p: ProjectEntity, s: ProcessingSettings): String = hash(listOf(
        p.hfSourceRepo, p.sourceConfig, p.sourceSplit, s.resolvedSourceRevision ?: s.sourceRevision,
        s.filterExpression, s.orderBy, p.idColumn
    ).joinToString("\u0000")).take(24)
    private fun claimPath(p: ProjectEntity, s: ProcessingSettings, assetId: String, ordinal: Long): String =
        ".vision-dataset-studio/claims/${sourceKey(p,s)}/${hash("$assetId\u0000$ordinal").take(40)}.json"

    private suspend fun readClaim(p:ProjectEntity,s:ProcessingSettings,revision:String,assetId:String,ordinal:Long):RemoteWorkClaim? {
        val text=hf.readDatasetText(p.hfDestRepo,revision,claimPath(p,s,assetId,ordinal)) ?: return null
        val value=adapter.fromJson(text) ?: error(tr("Réservation distante vide", "Empty remote reservation"))
        require(value.schema==1 && value.state in setOf("CLAIMED","DONE") && value.owner.isNotBlank() &&
            value.sourceKey==sourceKey(p,s) && value.assetId==assetId && value.sourceOrdinal==ordinal &&
            value.claimedAt>=0 && value.expiresAt>=value.claimedAt) {
            tr("Réservation distante invalide : aucune réécriture automatique", "Invalid remote reservation: no automatic overwrite")
        }
        return value
    }

    suspend fun claim(project: ProjectEntity, settings: ProcessingSettings, candidates: List<SourceEntryEntity>, desired: Int): ClaimSelection {
        require(settings.collaborationEnabled && desired in 1..1000)
        require(settings.sourceMode != "LOCAL_INDEX") { tr("La réservation partagée exige une source HF", "Shared reservations require an HF source") }
        require(project.hfDestRepo.isNotBlank()) { tr("Configurez le dépôt HF de destination pour activer le travail partagé", "Configure the destination HF repository to enable shared work") }
        val repo = project.hfDestRepo
        val now = System.currentTimeMillis()
        repeat(4) { attempt ->
            // Read and commit one immutable snapshot; resolving after the reads can steal a concurrent claim.
            val parent = hf.resolveRevision(repo, settings.destBranch)
            val chosen = mutableListOf<SourceEntryEntity>()
            val toWrite = mutableListOf<Pair<String, RemoteWorkClaim>>()
            var consumed = 0
            var skipped = 0
            for (entry in candidates) {
                if (chosen.size >= desired) break
                consumed++
                val path = claimPath(project, settings, entry.assetId, entry.ordinal)
                val existing = readClaim(project, settings, parent, entry.assetId, entry.ordinal)
                val unavailable = existing != null && SharedClaimPolicy.unavailable(
                    existing.state, existing.owner, existing.expiresAt, settings.collaborationWorkerId, now
                )
                if (unavailable) { skipped++; continue }
                chosen += entry
                if (existing == null || !SharedClaimPolicy.reusableByCurrentWorker(
                        existing.state, existing.owner, existing.expiresAt, settings.collaborationWorkerId, now
                    )) {
                    toWrite += path to RemoteWorkClaim(
                        state="CLAIMED", owner=settings.collaborationWorkerId, sourceKey=sourceKey(project,settings), assetId=entry.assetId,
                        sourceOrdinal=entry.ordinal, claimedAt=now, expiresAt=now + settings.claimLeaseMinutes * 60_000L
                    )
                }
            }
            if (chosen.isEmpty()) return ClaimSelection(emptyList(), consumed, skipped)
            if (toWrite.isEmpty()) return ClaimSelection(chosen, consumed, skipped)
            val tempRoot = File(cacheRoot, "claims-${System.nanoTime()}").apply { mkdirs() }
            try {
                val files = toWrite.mapIndexed { index, (path, claim) ->
                    val file = File(tempRoot, "$index.json"); file.writeText(adapter.toJson(claim)); path to file
                }
                val result = hf.uploadBatchFiles(repo, settings.destBranch, "Reserve ${files.size} Vision Dataset Studio cases", files, parent)
                if (result.success) return ClaimSelection(chosen, consumed, skipped)
                if (!result.conflict) error(result.message.ifBlank { tr("Réservation HF refusée", "HF reservation refused") })
            } finally { tempRoot.deleteRecursively() }
            if (attempt == 3) error(tr("Le dépôt de coordination change trop vite. Réessayez dans quelques secondes.", "The coordination repository is changing too quickly. Retry in a few seconds."))
        }
        error(tr("Réservation impossible", "Reservation unavailable"))
    }

    suspend fun markDone(project: ProjectEntity, settings: ProcessingSettings, samples: List<SampleEntity>) {
        if (!settings.collaborationEnabled || project.hfDestRepo.isBlank() || samples.isEmpty()) return
        val now = System.currentTimeMillis()
        repeat(3) { attempt ->
            val parent = hf.resolveRevision(project.hfDestRepo, settings.destBranch)
            val root = File(cacheRoot, "claims-done-${System.nanoTime()}").apply { mkdirs() }
            try {
                val files = samples.mapIndexedNotNull { index, sample ->
                    val ordinal = sample.sourceOrdinal ?: sample.sourceRowIndex
                    val path = claimPath(project, settings, sample.assetId, ordinal)
                    val existing=readClaim(project,settings,parent,sample.assetId,ordinal)
                    if(existing?.state=="DONE")return@mapIndexedNotNull null
                    check(existing?.owner==settings.collaborationWorkerId) {
                        tr("Réservation absente ou détenue par un autre collaborateur : décision distante non modifiée", "Reservation missing or owned by another worker: remote decision unchanged")
                    }
                    val claim = RemoteWorkClaim(1,"DONE",settings.collaborationWorkerId,sourceKey(project,settings),sample.assetId,ordinal,now,Long.MAX_VALUE,now)
                    val f = File(root,"$index.json"); f.writeText(adapter.toJson(claim)); path to f
                }
                if(files.isEmpty())return
                val result = hf.uploadBatchFiles(project.hfDestRepo, settings.destBranch, "Complete ${files.size} Vision Dataset Studio cases", files, parent)
                if (result.success) return
                if (!result.conflict) error(result.message.ifBlank { tr("Synchronisation des décisions refusée", "Decision synchronization refused") })
            } finally { root.deleteRecursively() }
            if (attempt == 2) error(tr("Conflits répétés pendant la synchronisation des décisions", "Repeated conflicts while synchronizing decisions"))
        }
    }
}
