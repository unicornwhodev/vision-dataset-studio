package com.unicornwhodev.visiondatasetstudio.domain.batch

import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.hf.RemoteReceipt
import com.unicornwhodev.visiondatasetstudio.data.hf.RemoteFileDigest
import com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety
import com.unicornwhodev.visiondatasetstudio.core.workflow.ResumeDecision
import com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles
import com.unicornwhodev.visiondatasetstudio.data.model.AcquisitionStatus
import com.unicornwhodev.visiondatasetstudio.data.model.AnnotationRecord
import com.unicornwhodev.visiondatasetstudio.data.model.AnnotationStatus
import com.unicornwhodev.visiondatasetstudio.data.model.AuditLogEntity
import com.unicornwhodev.visiondatasetstudio.data.model.BatchEntity
import com.unicornwhodev.visiondatasetstudio.data.model.BoxTarget
import com.unicornwhodev.visiondatasetstudio.data.model.PointTarget
import com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SourceEntryEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SyncStatus
import com.unicornwhodev.visiondatasetstudio.data.model.TagTarget
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.data.source.SourceCatalog
import com.unicornwhodev.visiondatasetstudio.core.storage.ImageNormalizer
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BatchEngine(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val hfApiClient: HfApiClient,
    private val liteRtEngine: LiteRtEngine,
    private val exporters: DatasetExporters
) {
    companion object { private val discoveryMutex=Mutex(); private val acquisitionMutex=Mutex(); private val preparationMutex=Mutex() }
    private val moshi = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi
    private val annotAdapter = moshi.adapter(SampleAnnotations::class.java)
    val sourceCatalog=SourceCatalog(storageManager.context,db,hfApiClient)
    private val workClaims=WorkClaimCoordinator(storageManager.context,hfApiClient)
    fun checkNetwork(settings:ProcessingSettings) {
        if(!settings.allowMetered) {
            val cm=storageManager.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            check(!cm.isActiveNetworkMetered) { "Réseau limité refusé par vos réglages. Connectez un réseau non facturé ou autorisez les données mobiles." }
        }
    }

    fun observeBatchSamples(projectId: Long, batchNumber: Int): Flow<List<SampleEntity>> {
        return db.sampleDao().getSamplesForBatch(projectId, batchNumber)
    }

    suspend fun getBatchEntity(projectId: Long, batchNumber: Int): BatchEntity? {
        return db.batchDao().getBatchSync(projectId, batchNumber)
    }

    /**
     * Discovers a configurable batch from the selected source and persists its metadata transactionally.
     */
    suspend fun discoverViewerBatch(requestedProject:ProjectEntity,batchNumber:Int,count:Int=100,append:Boolean=false):BatchDiscoveryResult = discoveryMutex.withLock { withContext(Dispatchers.IO) {
        try {
            require(count in 1..1000)
            val project=db.projectDao().getProjectSync(requestedProject.id) ?: error("Projet absent")
            val batch=db.batchDao().getBatchSync(project.id,batchNumber)
            check(if(append)batch!=null && batch.status !in PublicationSafety.lockedStates else batch==null) { "Lot déjà découvert ou verrouillé" }
            val settings=ProjectSettings.read(project)
            if(settings.sourceMode=="HF_VIEWER")checkNetwork(settings)
            hfApiClient.configureTimeout(settings.timeoutSeconds)
            val claimSelection=if(settings.collaborationEnabled) {
                val selected=mutableListOf<SourceEntryEntity>();var consumed=0;var skipped=0;var rounds=0
                while(selected.size<count && rounds++<12) {
                    val wanted=count-selected.size
                    val scanCount=minOf(1000,maxOf(wanted,wanted*5))
                    val candidates=sourceCatalog.page(project,project.lastRowCursor+consumed,scanCount)
                    if(candidates.isEmpty())break
                    val part=workClaims.claim(project,settings,candidates,wanted)
                    selected+=part.entries;skipped+=part.skipped
                    // If the whole window was unavailable, advance over it so a shared project can reach later free rows.
                    val advance=if(part.consumed>0)part.consumed else candidates.size
                    consumed+=advance
                }
                ClaimSelection(selected,consumed,skipped)
            } else {
                val candidates=sourceCatalog.page(project,project.lastRowCursor,count)
                ClaimSelection(candidates.take(count),candidates.take(count).size,0)
            }
            val entries=claimSelection.entries
            if(entries.isEmpty() && claimSelection.consumed==0) return@withContext BatchDiscoveryResult(true,endOfSource=true)
            val samples=entries.map { row ->
                val digest=UUID.nameUUIDFromBytes(row.assetId.toByteArray()).toString().take(12)
                SampleEntity(sampleId="p${project.id}_b${batchNumber}_r${row.ordinal}_$digest",projectId=project.id,batchNumber=batchNumber,
                    assetId=row.assetId,sourceRowIndex=row.sourceRowIndex ?: row.ordinal,sourceOrdinal=row.ordinal,sourceFileUrl=row.imageRef,localImagePath=null,
                    groupId=row.groupId,split=project.targetSplit,acquisitionStatus="DISCOVERED",annotationStatus=if(row.annotationJson==null) "PENDING" else "PROPOSALS_AVAILABLE",syncStatus="NOT_EXPORTED")
            }
            db.withTransaction {
                val current=db.projectDao().getProjectSync(project.id) ?: error("Projet absent")
                check(current.lastRowCursor==project.lastRowCursor){"La source a avancé; relancez l’import"}
                if(batch==null)db.batchDao().insertOrReplace(BatchEntity(project.id,batchNumber,"DISCOVERED",samples.size))
                else db.batchDao().updateBatch(batch.copy(totalCases=batch.totalCases+samples.size))
                db.sampleDao().insertNewSamples(samples)
                samples.zip(entries).forEach { (sample,row)->row.annotationJson?.let{db.annotationDao().insertOrReplace(AnnotationRecord(sample.sampleId,it))} }
                db.projectDao().saveProject(current.copy(lastRowCursor=project.lastRowCursor+claimSelection.consumed,updatedAt=System.currentTimeMillis()))
                db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,action="DISCOVER_BATCH",details="${entries.size} cas; source ${settings.sourceMode}; curseur ${project.lastRowCursor}; coordination=${settings.collaborationEnabled}; ignorés=${claimSelection.skipped}",projectId=project.id))
            }
            BatchDiscoveryResult(true,samples.size)
        } catch(e:CancellationException){throw e} catch(e:Exception){BatchDiscoveryResult(false,error=e.message)}
    } }

    /**
     * Downloads images for discovered cases with bounded concurrency and disk checking.
     */
    suspend fun acquireBatchImages(projectId:Long,batchNumber:Int,onProgress:(Int,Int)->Unit):Boolean = acquisitionMutex.withLock { withContext(Dispatchers.IO) {
        val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
        check(db.batchDao().getBatchSync(projectId,batchNumber)?.status !in PublicationSafety.lockedStates) { "Lot verrouillé; récupération refusée" }
        val settings=ProjectSettings.read(project);hfApiClient.configureTimeout(settings.timeoutSeconds)
        val samples=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)
        db.batchDao().updateStatus(projectId,batchNumber,"DOWNLOADING")
        val decodePermit=Semaphore(1)
        val permits=Semaphore(settings.downloadConcurrency);val completed=AtomicInteger();val failures=AtomicInteger()
        val budgetLock=Any();var reserved=0L
        coroutineScope {
            samples.map { original->async {
                permits.withPermit {
                    if(original.localImagePath?.let{path -> val file=storageManager.ownedImage(path); file.isFile && original.sha256!=null && HashUtils.computeSha256(file)==original.sha256}==true || original.annotationStatus in setOf("REJECTED","DUPLICATE") || original.syncStatus in setOf("VERIFIED","PURGED")) {
                        onProgress(completed.incrementAndGet(),samples.size);return@withPermit
                    }
                    var sample=original;var reservation=0L;var actual:File?=null
                    try {
                        coroutineContext.ensureActive()
                        reservation=synchronized(budgetLock) {
                            val available=minOf(project.diskBudgetMb*1048576-storageManager.getUsedSpaceBytes()-reserved,
                                storageManager.getFreeSpaceBytes()-settings.reserveFreeMb*1048576L-reserved)
                            val bytes=minOf(settings.maxImageMb*1048576L,available/2)
                            check(bytes>0) { "Budget disque atteint; les autres lots et modèles restent conservés" }
                            reserved+=bytes*2;bytes*2
                        }
                        val file=storageManager.getImageFile(sample.sampleId)
                        db.sampleDao().updateSample(sample.copy(acquisitionStatus="DOWNLOADING"))
                        var ok=false;var lastFailure:String?=null
                        for(attempt in 0..settings.retryCount) {
                            coroutineContext.ensureActive()
                            try {
                                val ref=sample.sourceFileUrl ?: error("Référence image absente")
                                if(ref.startsWith("https://"))checkNetwork(settings)
                                ok=sourceCatalog.copyAsset(ref,file,reservation/2)
                                if(ok)break
                            } catch(e:CancellationException){throw e} catch(e:Exception){lastFailure=e.message}
                            if(attempt<settings.retryCount) {
                                if(settings.sourceMode=="HF_VIEWER") {
                                    val fresh=sourceCatalog.page(project,sample.sourceOrdinal ?: sample.sourceRowIndex,1).singleOrNull()
                                    check(fresh==null || fresh.assetId==sample.assetId){"La source Viewer a changé; reprise suspendue pour préserver la provenance"}
                                    if(fresh!=null)sample=sample.copy(sourceFileUrl=fresh.imageRef)
                                }
                                delay(500L*(1L shl attempt))
                            }
                        }
                        check(ok && file.length()>0){lastFailure ?: "Acquisition échouée ou limite de taille dépassée"}
                        decodePermit.withPermit {
                        val before=storageManager.readImageMetadata(file)
                        require(before.width>0 && before.height>0 && before.width.toLong()*before.height<=settings.sourceMaxPixels){"Dimensions image invalides ou plafond pixels dépassé"}
                        val sourceHash=HashUtils.computeSha256(file)
                        val transform=ImageNormalizer.normalize(file,settings.normalizeExif,db.annotationDao().getAnnotationSync(sample.sampleId)!=null)
                        check(file.length()<=reservation){"Image normalisée trop volumineuse"}
                        val meta=storageManager.readImageMetadata(file)
                        val ext=when(meta.mimeType){"image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp";else->error("Format image non pris en charge")}
                        actual=storageManager.getImageFile(sample.sampleId,ext)
                        if(actual!=file)check(file.renameTo(actual))
                        var factor=1;while(maxOf(meta.width,meta.height)/factor>256)factor*=2
                        val bitmap=BitmapFactory.decodeFile(actual!!.path,BitmapFactory.Options().apply{inSampleSize=factor}) ?: error("Image non décodable")
                        val dhash=try{HashUtils.computeDHash(bitmap)}finally{bitmap.recycle()}
                        val accepted=sample.copy(localImagePath=actual!!.path,imageWidth=meta.width,imageHeight=meta.height,
                            sha256=HashUtils.computeSha256(actual!!),phash=dhash,sourceSha256=sourceHash,imageTransform=transform,acquisitionStatus="AVAILABLE",auditReason=null)
                        val duplicate=ImageIdentity.accept(db,accepted,ImageIdentity.pixelSha256(actual!!))
                        if(duplicate!=null) {
                            check(actual!!.delete()) { "Nettoyage du doublon interrompu" }
                            db.auditDao().insertLog(AuditLogEntity(sampleId=sample.sampleId,batchNumber=batchNumber,projectId=projectId,
                                action="DUPLICATE_SKIPPED",details="Lot original ${duplicate.firstBatchNumber}; cas ${duplicate.firstSampleId}"))
                        }
                        }
                    } catch(e:CancellationException) {
                        withContext(NonCancellable){if(db.sampleDao().getSampleSync(sample.sampleId)?.annotationStatus!="DUPLICATE")db.sampleDao().updateSample(sample.copy(acquisitionStatus="ERROR_RETRYABLE",auditReason="Opération arrêtée; reprise disponible"))}
                        throw e
                    } catch(e:Exception) {
                        failures.incrementAndGet();actual?.delete();storageManager.getImageFile(sample.sampleId).delete()
                        if(db.sampleDao().getSampleSync(sample.sampleId)?.annotationStatus!="DUPLICATE")db.sampleDao().updateSample(sample.copy(acquisitionStatus="ERROR_RETRYABLE",auditReason=e.message ?: "Acquisition interrompue"))
                    } finally { synchronized(budgetLock){reserved-=reservation};onProgress(completed.incrementAndGet(),samples.size) }
                }
            } }.awaitAll()
        }
        val batch=db.batchDao().getBatchSync(projectId,batchNumber) ?: error("Lot absent")
        val unique=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber).count{it.annotationStatus!="DUPLICATE"}
        db.batchDao().updateBatch(batch.copy(status="READY",totalCases=unique))
        failures.get()==0
    } }

    /** Fills slots with unique images. Network failures stay visible and retryable, never skipped. */
    suspend fun prepareUniqueBatch(projectId:Long,batchNumber:Int,count:Int,onProgress:(Int,Int)->Unit):Boolean = preparationMutex.withLock {
        require(count in 1..1000)
        var exhausted=false
        var allAcquired=true
        // Bound one action for duplicate-heavy sources; cursor and exclusions persist for the next retry.
        repeat(20) {
            val existing=db.batchDao().getBatchSync(projectId,batchNumber)
            if(existing!=null) {
                allAcquired=acquireBatchImages(projectId,batchNumber,onProgress)
                val size=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber).count{it.annotationStatus!="DUPLICATE"}
                if(size>=count || !allAcquired || exhausted) return@withLock exhausted
            }
            val size=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber).count{it.annotationStatus!="DUPLICATE"}
            val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
            val discovered=discoverViewerBatch(project,batchNumber,count-size,append=existing!=null)
            check(discovered.success){discovered.error ?: "Import interrompu"}
            if(discovered.endOfSource) {
                exhausted=true
                check(existing!=null){"Fin de la source : aucune nouvelle image"}
                if(size==0)db.batchDao().updateStatus(projectId,batchNumber,"EMPTY")
                return@withLock true
            }
        }
        // Last appended window must be acquired before exposing its images.
        if(db.batchDao().getBatchSync(projectId,batchNumber)!=null)acquireBatchImages(projectId,batchNumber,onProgress)
        exhausted
    }

    suspend fun requireUniqueExport(samples:List<SampleEntity>)=ImageIdentity.requireCanonical(db,samples)

    /**
     * Executes LiteRT pre-annotations on all available images in the batch if configured.
     */
    /** Processes one bounded bitmap at a time; never validates a case or overwrites human work. */
    suspend fun runBatchInference(
        projectId: Long,
        batchNumber: Int,
        config: ModelConfig,
        freshOnly: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.Default) {
        val projectPolicy=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
        // This guard deliberately precedes sample selection and every mutation. UI, automatic
        // preparation and workflows therefore share exactly the same fail-closed behaviour.
        ModelContract.requireTaskCompatibility(config,projectPolicy.activeTasksCsv)
        check(db.batchDao().getBatchSync(projectId, batchNumber)?.status !in PublicationSafety.lockedStates) {
            "Ce lot est verrouillé par sa publication."
        }
        val samples = db.sampleDao().getSamplesForBatchSync(projectId, batchNumber).filter {
            com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow.canEdit(it.acquisitionStatus, it.syncStatus, it.localImagePath != null) &&
                (if(freshOnly) it.annotationStatus=="PENDING" else com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow.isPending(it.annotationStatus))
        }
        val ledger=if(ProjectSettings.read(projectPolicy).adaptiveCorrection)AdaptiveCorrectionStore(storageManager.context).read(projectId) else CorrectionLedger()
        var processed = 0
        onProgress(0, samples.size)
        for (sample in samples) {
            coroutineContext.ensureActive()
            val path = sample.localImagePath ?: continue
            check(File(path).isFile) { "Image locale absente : ${sample.assetId}" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image non décodable : ${sample.assetId}" }
            var factor = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / factor > 2048) factor *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = factor })
                ?: error("Image non décodable : ${sample.assetId}")
            val inference = try { liteRtEngine.runInference(bitmap, config) } finally { bitmap.recycle() }
            InferenceReceiptStore(storageManager.context.filesDir).write(projectId,sample.sampleId,batchNumber,inference)
            val proposals = AdaptiveCorrection.apply(when(inference) {
                is InferenceResult.Failure -> error("Inférence interrompue à ${sample.assetId} : ${inference.error}. Les résultats précédents sont conservés.")
                else -> inference.orThrow()
            },ledger)
            liteRtEngine.lastEmbedding?.let { vector ->
                com.unicornwhodev.visiondatasetstudio.domain.inference.EmbeddingIndex(storageManager.context,projectId).put(sample.sampleId,sample.sha256 ?: com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(File(path)),liteRtEngine.embeddingSpaceHash,vector)
            }
            val existing = getSampleAnnotations(sample.sampleId)
            val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
            val updated=ProposalMerger.merge(existing,proposals,project.activeTasksCsv,config.captionLanguage,ModelContract.outputTypes(config))
            val status=if(proposals.isEmpty()) AnnotationStatus.IN_PROGRESS.name else AnnotationStatus.PROPOSALS_AVAILABLE.name
            db.withTransaction {
                saveSampleAnnotations(sample.sampleId, updated)
                db.sampleDao().updateSample(sample.copy(annotationStatus = status, updatedAt = System.currentTimeMillis()))
                db.auditDao().insertLog(AuditLogEntity(sampleId = sample.sampleId, batchNumber = batchNumber,
                    projectId=projectId, action = "MODEL_PREANNOTATION", details = "${proposals.size} proposition(s); aucune validation automatique."))
            }
            processed++
            onProgress(processed, samples.size)
        }
        processed
    }

    /**
     * Saves user modifications for a single sample.
     */
    suspend fun saveSampleAnnotations(sampleId: String, annotations: SampleAnnotations) = withContext(Dispatchers.IO) {
        val s=db.sampleDao().getSampleSync(sampleId) ?: error("Cas absent")
        check(db.batchDao().getBatchSync(s.projectId,s.batchNumber)?.status !in PublicationSafety.lockedStates) { "Lot verrouillé : annotations conservées inchangées" }
        db.annotationDao().insertOrReplace(
            AnnotationRecord(
                sampleId = sampleId,
                dataJson = annotAdapter.toJson(annotations)
            )
        )
    }

    suspend fun getSampleAnnotations(sampleId: String): SampleAnnotations = withContext(Dispatchers.IO) {
        val record = db.annotationDao().getAnnotationSync(sampleId)
        if (record != null) {
            annotAdapter.fromJson(record.dataJson) ?: SampleAnnotations()
        } else {
            SampleAnnotations()
        }
    }

    suspend fun validateSample(sampleId: String, batchNumber: Int) = withContext(Dispatchers.IO) {
        val sample = db.sampleDao().getSampleSync(sampleId) ?: return@withContext
        check(db.batchDao().getBatchSync(sample.projectId,sample.batchNumber)?.status !in PublicationSafety.lockedStates) { "Lot verrouillé; décision conservée" }
        db.sampleDao().updateSample(
            sample.copy(
                annotationStatus = AnnotationStatus.VALIDATED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
        updateBatchCounts(sample.projectId, batchNumber)
        db.auditDao().insertLog(
            AuditLogEntity(sampleId = sampleId, batchNumber = batchNumber, projectId=sample.projectId, action = "VALIDATE", details = "Cas validé")
        )
    }

    suspend fun rejectSample(sampleId: String, batchNumber: Int, reason: String) = withContext(Dispatchers.IO) {
        val sample = db.sampleDao().getSampleSync(sampleId) ?: return@withContext
        check(db.batchDao().getBatchSync(sample.projectId,sample.batchNumber)?.status !in PublicationSafety.lockedStates) { "Lot verrouillé; décision conservée" }
        db.sampleDao().updateSample(
            sample.copy(
                annotationStatus = AnnotationStatus.REJECTED.name,
                auditReason = reason,
                updatedAt = System.currentTimeMillis()
            )
        )
        updateBatchCounts(sample.projectId, batchNumber)
        db.auditDao().insertLog(
            AuditLogEntity(sampleId = sampleId, batchNumber = batchNumber, projectId=sample.projectId, action = "REJECT", details = "Motif: $reason")
        )
    }

    suspend fun deferSample(sampleId: String, batchNumber: Int) = withContext(Dispatchers.IO) {
        val sample = db.sampleDao().getSampleSync(sampleId) ?: return@withContext
        check(db.batchDao().getBatchSync(sample.projectId,sample.batchNumber)?.status !in PublicationSafety.lockedStates) { "Lot verrouillé; décision conservée" }
        db.sampleDao().updateSample(
            sample.copy(
                annotationStatus = AnnotationStatus.DEFERRED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
        updateBatchCounts(sample.projectId, batchNumber)
        db.auditDao().insertLog(
            AuditLogEntity(sampleId = sampleId, batchNumber = batchNumber, projectId=sample.projectId, action = "DEFER", details = "Cas différé")
        )
    }

    private suspend fun updateBatchCounts(projectId: Long, batchNumber: Int) {
        val validated = db.sampleDao().countByAnnotationStatus(projectId, batchNumber, AnnotationStatus.VALIDATED.name)
        val rejected = db.sampleDao().countByAnnotationStatus(projectId, batchNumber, AnnotationStatus.REJECTED.name)
        val deferred = db.sampleDao().countByAnnotationStatus(projectId, batchNumber, AnnotationStatus.DEFERRED.name)

        val batch = db.batchDao().getBatchSync(projectId, batchNumber) ?: return
        db.batchDao().updateBatch(
            batch.copy(
                validatedCases = validated,
                rejectedCases = rejected,
                deferredCases = deferred,
                status = if (validated + rejected >= batch.totalCases) "VALIDATED" else "IN_PROGRESS",
                updatedAt = System.currentTimeMillis()
            )
        )
        if (validated + rejected >= batch.totalCases) {
            val project=db.projectDao().getProjectSync(projectId)
            if(project!=null) {
                val settings=ProjectSettings.read(project)
                if(settings.collaborationEnabled) {
                    runCatching { workClaims.markDone(project,settings,db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)) }
                        .onFailure { db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,projectId=projectId,action="COORDINATION_SYNC_FAILED",details=it.message ?: "Erreur inconnue")) }
                }
            }
        }
    }

    suspend fun snapshot(projectId:Long,batchNumber:Int):String = BatchSnapshot.compute(db,projectId,batchNumber)
    suspend fun recordLocalArchive(projectId:Long,batchNumber:Int,file:File) {
        val b=db.batchDao().getBatchSync(projectId,batchNumber) ?: error("Lot absent")
        check(b.status !in PublicationSafety.lockedStates) { "Lot verrouillé" }
        db.batchDao().updateBatch(b.copy(archivePath=file.path,archiveSizeBytes=file.length(),archiveSnapshot=snapshot(projectId,batchNumber),verifiedArchiveUri=null,verifiedArchiveSha256=null,verificationKind=null))
    }
    suspend fun verifyLocalArchive(projectId:Long,batchNumber:Int,uri:String) = withContext(Dispatchers.IO) {
        val b=db.batchDao().getBatchSync(projectId,batchNumber) ?: error("Lot absent")
        check(b.status !in setOf("PREPARED","PUBLISHING","PUBLISHED","CONFLICT","PURGING","PURGED")){"Terminez le transfert incertain avant de clôturer une copie locale"}
        check(b.archiveSnapshot==snapshot(projectId,batchNumber)){"Les annotations ont changé depuis l’export. Préparez une nouvelle archive."}
        val file=b.archivePath?.let(::File) ?: error("Archive absente")
        check(file.isFile)
        val expected=HashUtils.computeSha256(file)
        check(hashUri(uri,file.length())==expected) { "La copie externe ne correspond pas à l’archive" }
        val samples=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)
        check(samples.all{it.annotationStatus in setOf("VALIDATED","REJECTED","DUPLICATE")}) { "Copie vérifiée, mais lot incomplet : terminez les cas avant de clôturer le lot" }
        db.withTransaction {
            db.batchDao().updateBatch(b.copy(status="VERIFIED",verificationKind=if(b.hfCommitSha==null)"local" else "both",verifiedArchiveUri=uri,verifiedArchiveSha256=expected,archiveSizeBytes=file.length()))
            samples.filter{it.annotationStatus=="VALIDATED"}.forEach{db.sampleDao().updateSample(it.copy(syncStatus="VERIFIED"))}
            db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,action="LOCAL_ARCHIVE_VERIFIED",details="Archive relue et SHA-256 comparé; URI conservée en base, pas dans l’export",projectId=projectId))
        }
    }
    private fun hashUri(uri:String,maxBytes:Long):String {
        check(maxBytes > 0) { "Taille de copie inconnue; préparez et vérifiez à nouveau une archive" }
        return storageManager.context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
            DurableFiles.hash(input,maxBytes)
        } ?: error("La copie externe n’est plus accessible; purge refusée")
    }
    private val receiptAdapter = moshi.adapter(RemoteReceipt::class.java)
    private fun receipt(files:List<Pair<String,File>>) = RemoteReceipt(files=files.map{(path,file)->
        RemoteFileDigest(path,file.length(),HashUtils.computeSha256(file))
    })
    private fun manifestHash(root:File):String {
        check(exporters.verifyPreparedPackage(root)) { "Paquet incomplet ou altéré; aucune reconstruction automatique d’un envoi incertain" }
        return HashUtils.computeSha256(root.walkTopDown().filter{it.isFile && it.name=="manifest.json"}.single())
    }
    private fun remoteFiles(projectId:Long,batchNumber:Int,prefix:String):List<Pair<String,File>> {
        val root=storageManager.batchExportDir(projectId,batchNumber)
        return root.walkTopDown().filter{it.isFile}.map{"$prefix/${it.relativeTo(root).invariantSeparatorsPath}" to it}.sortedBy{it.first}.toList()
    }
    /** Durable upload intent. A lost response never changes parent, repo, paths or bytes. */
    suspend fun publishAndVerifyBatch(projectId:Long,batchNumber:Int):BatchPublishResult = withContext(Dispatchers.IO) {
        val project=db.projectDao().getProjectSync(projectId) ?: return@withContext BatchPublishResult(false,"Projet absent")
        var batch=db.batchDao().getBatchSync(projectId,batchNumber) ?: return@withContext BatchPublishResult(false,"Lot absent")
        try {
            val settings=ProjectSettings.read(project); checkNetwork(settings); hfApiClient.configureTimeout(settings.timeoutSeconds)
            val samples=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)
            check(samples.isNotEmpty() && samples.all{it.annotationStatus in setOf("VALIDATED","REJECTED","DUPLICATE")}) { "Terminez ou rejetez chaque cas avant publication" }
            requireUniqueExport(samples.filter{it.annotationStatus=="VALIDATED"})
            if(settings.collaborationEnabled) workClaims.markDone(project,settings,samples)
            check(batch.status !in setOf("PURGING","PURGED")) { "Les médias de ce lot sont en cours de purge ou déjà purgés" }
            val pairs=samples.filter{it.annotationStatus=="VALIDATED"}.map{it to getSampleAnnotations(it.sampleId)}
            check(pairs.isNotEmpty()){ "Aucun cas validé à publier" }
            val currentSnapshot=snapshot(projectId,batchNumber)
            val root=storageManager.batchExportDir(projectId,batchNumber)
            if(batch.remoteParentCommit==null && batch.hfCommitSha==null) {
                check(batch.status !in setOf("PUBLISHING","PREPARED","CONFLICT")) { "Ancien transfert sans parent connu. Choisissez explicitement un nouvel emplacement isolé." }
                if(batch.verificationKind=="local") check(batch.archiveSnapshot==currentSnapshot) { "Copie locale obsolète" }
                val repo=com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow.normalizeRepo(project.hfDestRepo,destination=true) ?: error("Destination HF invalide")
                val prefix=batch.remotePrefix ?: "${settings.destPrefix}/${storageManager.publicationNamespace(projectId)}"
                val packaged=exporters.packageBatchForHf(project,batchNumber,pairs,settings.hfWebDataset,true,settings.hfCoco,settings.hfYolo,settings.hfVl)
                check(packaged.success){packaged.error ?: "Préparation impossible"}
                val files=remoteFiles(projectId,batchNumber,prefix)
                val parent=hfApiClient.resolveRevision(repo,settings.destBranch)
                hfApiClient.requirePathsAbsent(repo,parent,files.map{it.first})
                batch=batch.copy(status="PREPARED",remotePrefix=prefix,remoteBranch=settings.destBranch,remoteRepoId=repo,
                    remoteParentCommit=parent,archiveSnapshot=currentSnapshot,preparedManifestSha256=manifestHash(root),
                    remoteReceiptJson=receiptAdapter.toJson(receipt(files)),lastTransferError=null)
                // Commit intent before the first external write (including LFS).
                db.batchDao().updateBatch(batch)
            }
            check(batch.archiveSnapshot==currentSnapshot) { "Annotations modifiées après préparation; transfert suspendu" }
            val repo=batch.remoteRepoId ?: error("Reçu ancien sans dépôt épinglé; vérification manuelle nécessaire")
            val prefix=batch.remotePrefix ?: error("Préfixe absent")
            val expected=batch.remoteParentCommit
            val recorded=batch.remoteReceiptJson?.let(receiptAdapter::fromJson) ?: error("Reçu des fichiers absent")
            check(batch.preparedManifestSha256==manifestHash(root)) { "Manifest modifié après préparation" }
            val files=remoteFiles(projectId,batchNumber,prefix)
            check(receipt(files)==recorded) { "Contenus modifiés après préparation" }
            val knownCommit=batch.hfCommitSha
            val sha=if(knownCommit!=null) knownCommit else {
                check(expected!=null) { "Parent absent" }
                val head=hfApiClient.resolveRevision(repo,batch.remoteBranch ?: error("Branche absente"))
                when(PublicationSafety.decide(expected,head,hfApiClient.verifyRemoteDigests(repo,head,recorded.files))) {
                    ResumeDecision.COMMITTED -> head
                    ResumeDecision.CONFLICT -> {
                        batch=batch.copy(status="CONFLICT",lastTransferError="La branche a changé; aucun rebase ou écrasement automatique")
                        db.batchDao().updateBatch(batch);error(batch.lastTransferError!!)
                    }
                    ResumeDecision.RETRY_SAME_PARENT -> {
                        batch=batch.copy(status="PUBLISHING",lastTransferError=null);db.batchDao().updateBatch(batch)
                        val result=hfApiClient.uploadBatchFiles(repo,batch.remoteBranch!!,
                            "Studio project $projectId / batch $batchNumber (${pairs.size} reviewed)",files,expected)
                        if(result.conflict) { batch=batch.copy(status="CONFLICT",lastTransferError=result.message);db.batchDao().updateBatch(batch) }
                        check(result.success){result.message}
                        result.commitSha ?: error("Réponse sans SHA; relancez la réconciliation")
                    }
                }
            }
            batch=batch.copy(status="PUBLISHED",hfCommitSha=sha,lastTransferError=null)
            // Persist receipt even if the following remote reads fail or process dies.
            db.batchDao().updateBatch(batch)
            check(hfApiClient.verifyRemoteDigests(repo,sha,recorded.files)){ "Commit reçu; vérification incomplète. Les copies locales restent conservées." }
            db.withTransaction {
                db.batchDao().updateBatch(batch.copy(status="VERIFIED",verificationKind=if(batch.verificationKind in setOf("local","both"))"both" else "hf"))
                pairs.forEach{(sample,_)->db.sampleDao().updateSample(sample.copy(syncStatus="VERIFIED"))}
                db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,action="REMOTE_CONTENT_VERIFIED",details="SHA $sha; purge distincte",projectId=projectId))
            }
            BatchPublishResult(true,"Contenus vérifiés. Conservation ou purge explicite disponible.",sha)
        } catch(e:CancellationException) {
            withContext(NonCancellable){ db.batchDao().getBatchSync(projectId,batchNumber)?.let {
                db.batchDao().updateBatch(it.copy(lastTransferError="Interruption; réconciliation de l’intention persistée requise"))
            } };throw e
        } catch(e:Exception) {
            db.batchDao().getBatchSync(projectId,batchNumber)?.let { db.batchDao().updateBatch(it.copy(lastTransferError=e.message?.take(1000))) }
            BatchPublishResult(false,e.message ?: "Transfert interrompu; copies conservées")
        }
    }

    /** Explicit conflict recovery changes only the NEW destination; never deletes or overwrites the old one. */
    suspend fun isolateConflictedUpload(projectId:Long,batchNumber:Int) = withContext(Dispatchers.IO) {
        val batch=db.batchDao().getBatchSync(projectId,batchNumber) ?: error("Lot absent")
        check(batch.status=="CONFLICT" && batch.hfCommitSha==null) { "Seul un conflit sans commit confirmé peut être réémis" }
        val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
        val prefix="${ProjectSettings.read(project).destPrefix}/${storageManager.publicationNamespace(projectId)}/retry-${UUID.randomUUID()}"
        db.withTransaction {
            db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,projectId=projectId,
                action="CONFLICT_ISOLATED",details="Ancienne intention conservée pour audit (aucun nettoyage distant): repo=${batch.remoteRepoId}; branch=${batch.remoteBranch}; parent=${batch.remoteParentCommit}; prefix=${batch.remotePrefix}"))
            db.batchDao().updateBatch(batch.copy(status="VALIDATED",remoteRepoId=null,remoteParentCommit=null,remoteBranch=null,
                remotePrefix=prefix,preparedManifestSha256=null,remoteReceiptJson=null,lastTransferError=null))
        }
    }
    suspend fun closeRejectedBatch(projectId:Long,batchNumber:Int) {
        val batch=db.batchDao().getBatchSync(projectId,batchNumber) ?: error("Lot absent")
        check(batch.status !in PublicationSafety.lockedStates) { "Lot déjà clôturé ou en transfert" }
        val samples=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)
        check(samples.isNotEmpty() && samples.all{it.annotationStatus in setOf("REJECTED","DUPLICATE")}) { "La clôture sans copie est réservée aux lots intégralement rejetés explicitement" }
        db.withTransaction {
            db.batchDao().updateBatch(batch.copy(status="VERIFIED",verificationKind="rejection_only",archiveSnapshot=snapshot(projectId,batchNumber)))
            db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,action="REJECTED_BATCH_CLOSED",details="Tous les cas ont été rejetés explicitement. Aucune donnée acceptée à sauvegarder; purge distincte.",projectId=projectId))
        }
    }
    suspend fun purgeReviewedBatch(projectId:Long,batchNumber:Int,discardRejectedConfirmed:Boolean):Int = withContext(Dispatchers.IO) {
        val batch=db.batchDao().getBatchSync(projectId,batchNumber) ?: error("Lot absent")
        val samples=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)
        check(batch.status in setOf("VERIFIED","PURGING")) { "Copie vérifiée requise" }
        val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
        val learning=com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining(storageManager.context)
        learning.requireCleanupAllowed(project,batchNumber,samples.any{it.annotationStatus=="VALIDATED"})
        check(batch.archiveSnapshot!=null && batch.archiveSnapshot==snapshot(projectId,batchNumber)) { "Copie obsolète; purge refusée" }
        check(samples.all{(it.annotationStatus=="VALIDATED" && it.syncStatus in setOf("VERIFIED","PURGED")) || (it.annotationStatus=="REJECTED" && discardRejectedConfirmed) || it.annotationStatus=="DUPLICATE"}) { "Des cas restent à traiter" }
        // A corrupt/untrusted DB path must not delete any source, model or other project's file.
        for(sample in samples) sample.localImagePath?.let { path ->
            val owned=storageManager.ownedImage(path)
            check(owned.name in listOf("jpg","png","webp").map{storageManager.getImageFile(sample.sampleId,it).name}) { "Chemin média étranger au cas" }
        }
        var proofValid=batch.verificationKind=="rejection_only" && samples.all{it.annotationStatus in setOf("REJECTED","DUPLICATE")}
        if(batch.verificationKind in setOf("local","both")) {
            proofValid=try {
                val limit=batch.archiveSizeBytes ?: batch.archivePath?.let(::File)?.takeIf{it.isFile}?.length() ?: 0
                hashUri(batch.verifiedArchiveUri ?: error("URI absente"),limit)==batch.verifiedArchiveSha256
            } catch(e:CancellationException){throw e} catch(_:Exception){false}
        }
        if(!proofValid && batch.verificationKind in setOf("hf","both")) {
            val repo=batch.remoteRepoId ?: error("Ancien reçu sans dépôt vérifiable; originaux conservés")
            val commit=batch.hfCommitSha ?: error("Commit absent")
            val record=batch.remoteReceiptJson?.let(receiptAdapter::fromJson) ?: error("Reçu distant absent")
            proofValid=hfApiClient.verifyRemoteDigests(repo,commit,record.files)
        }
        check(proofValid) { "Copie de sauvegarde indisponible ou altérée; purge refusée" }
        db.batchDao().updateStatus(projectId,batchNumber,"PURGING")
        var deleted=0
        for(sample in samples) {
            coroutineContext.ensureActive()
            val file=sample.localImagePath?.let(storageManager::ownedImage)
            if(file?.exists()==true) { check(file.delete()) { "Suppression impossible; reprise de purge disponible" };deleted++ }
            storageManager.removeImageCheckpoints(sample.sampleId)
            db.sampleDao().updateSample(sample.copy(syncStatus="PURGED",localImagePath=null))
        }
        val root=storageManager.batchExportDir(projectId,batchNumber)
        val paths=mutableListOf(root,storageManager.batchArchiveFile(projectId,batchNumber),File(root.path+".building"),File(root.path+".previous"))
        if(projectId==1L) { val legacy="batch-%06d".format(java.util.Locale.US,batchNumber)
            paths+=File(storageManager.exportsDir,legacy);paths+=File(storageManager.exportsDir,"$legacy.zip") }
        for(path in paths) {
            val owned=DurableFiles.ownedFile(storageManager.exportsDir,path.path)
            check(!owned.exists() || owned.deleteRecursively()) { "Nettoyage incomplet; relancez la purge" }
        }
        learning.releaseBatchImages(projectId,batchNumber)
        // PURGED only after ALL deletions succeed. Repeated calls are safe after a crash between steps.
        db.withTransaction {
            db.batchDao().updateStatus(projectId,batchNumber,"PURGED")
            db.auditDao().insertLog(AuditLogEntity(sampleId=null,batchNumber=batchNumber,action="EXPLICIT_PURGE",details="$deleted copies cache supprimées; sources intactes; reçus et annotations conservés",projectId=projectId))
        }
        deleted
    }




}

data class BatchDiscoveryResult(
    val success: Boolean,
    val totalDiscovered: Int = 0,
    val error: String? = null,
    val endOfSource: Boolean = false
)

data class BatchPublishResult(
    val success: Boolean,
    val message: String,
    val commitSha: String? = null
)
