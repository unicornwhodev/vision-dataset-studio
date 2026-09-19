package com.unicornwhodev.visiondatasetstudio.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.unicornwhodev.visiondatasetstudio.core.crypto.KeystoreManager
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.*
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.StudioPreferenceStore
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import android.content.Intent
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.validation.AnnotationReview
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

sealed class Screen {
    object Home : Screen()
    object Setup : Screen()
    object BatchGrid : Screen()
    data class AnnotationEditor(val sampleId: String) : Screen()
    object Publication : Screen()
    object QualityDashboard : Screen()
    object Preferences : Screen()
    object Controls : Screen()
    object Models : Screen()
}

private sealed class EditCommand {
    data class Save(val sampleId: String, val annotations: SampleAnnotations) : EditCommand()
    data class Barrier(val completion: CompletableDeferred<Unit>) : EditCommand()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val keystoreManager = KeystoreManager(application)
    val storageManager = StorageManager(application)
    val db = AppDatabase.getInstance(application)
    val hfApiClient = HfApiClient { keystoreManager.getToken() }
    val liteRtEngine = LiteRtEngine()
    val exporters = DatasetExporters(storageManager, hfApiClient)
    val batchEngine = BatchEngine(db, storageManager, hfApiClient, liteRtEngine, exporters)
    val preferenceStore = StudioPreferenceStore(application)
    val preferences = preferenceStore.state
    private val _activeProjectId = MutableStateFlow(preferenceStore.activeProjectId)
    val activeProjectId = _activeProjectId.asStateFlow()
    val projects = db.projectDao().getProjects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projectFlow = _activeProjectId.flatMapLatest { db.projectDao().getProject(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val batches = _activeProjectId.flatMapLatest { db.batchDao().getBatches(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val modelProfiles = db.modelProfileDao().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _communityModels = MutableStateFlow<List<CommunityModelCatalog.Availability>>(emptyList())
    val communityModels = _communityModels.asStateFlow()
    private val _modelDiagnostics = MutableStateFlow("")
    val modelDiagnostics = _modelDiagnostics.asStateFlow()
    private var operationJob: Job? = null
    private val correctionStore=AdaptiveCorrectionStore(application)
    private val _correctionReport=MutableStateFlow("Aucune correction locale inspectée")
    val correctionReport=_correctionReport.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen = _currentScreen.asStateFlow()
    private val _activeBatchNumber = MutableStateFlow(1)
    val activeBatchNumber = _activeBatchNumber.asStateFlow()
    private val _batchSamples = MutableStateFlow<List<SampleEntity>>(emptyList())
    val batchSamples = _batchSamples.asStateFlow()
    private val _currentSample = MutableStateFlow<SampleEntity?>(null)
    val currentSample = _currentSample.asStateFlow()
    private val _currentAnnotations = MutableStateFlow(SampleAnnotations())
    val currentAnnotations = _currentAnnotations.asStateFlow()
    private val _authStatus = MutableStateFlow<HfWhoAmIResult?>(null)
    val authStatus = _authStatus.asStateFlow()
    private val _sourceInspection = MutableStateFlow(HfSourceInspectionState())
    val sourceInspection = _sourceInspection.asStateFlow()
    private val _destRepoStatus = MutableStateFlow<HfRepoAccessResult?>(null)
    val destRepoStatus = _destRepoStatus.asStateFlow()
    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress = _operationProgress.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()
    private val _editorBusy = MutableStateFlow(false)
    val editorBusy = _editorBusy.asStateFlow()
    private val _saveState = MutableStateFlow("Enregistré sur cet appareil")
    val saveState = _saveState.asStateFlow()
    private val _undoAvailable = MutableStateFlow(false)
    val undoAvailable = _undoAvailable.asStateFlow()
    private val _redoAvailable = MutableStateFlow(false)
    val redoAvailable = _redoAvailable.asStateFlow()
    private val _editorIssues = MutableStateFlow<List<String>>(emptyList())
    val editorIssues = _editorIssues.asStateFlow()
    private val _dryRunResult = MutableStateFlow<DryRunResult?>(null)
    val dryRunResult = _dryRunResult.asStateFlow()
    private val _lastExportedZip = MutableStateFlow<File?>(null)
    val lastExportedZip = _lastExportedZip.asStateFlow()
    private val _previewSnippet = MutableStateFlow<String?>(null)
    val previewSnippet = _previewSnippet.asStateFlow()
    private val undoStack = mutableListOf<SampleAnnotations>()
    private val redoStack = mutableListOf<SampleAnnotations>()
    private val edits = Channel<EditCommand>(Channel.UNLIMITED)
    private val failedSaves = mutableSetOf<String>()
    private var queuedSaves = 0
    private var batchObserver: Job? = null
    private val moshi = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi

    init {
        // A single writer prevents an older keystroke from overwriting a newer edit.
        viewModelScope.launch {
            for (command in edits) when (command) {
                is EditCommand.Barrier -> command.completion.complete(Unit)
                is EditCommand.Save -> {
                    try {
                        db.withTransaction {
                            val sample = db.sampleDao().getSampleSync(command.sampleId)
                            check(sample != null && db.batchDao().getBatchSync(sample.projectId, sample.batchNumber)?.status !in com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates) { "Lot verrouillé : correction non écrite" }
                            db.sampleDao().updateSample(sample.copy(annotationStatus = if (command.annotations.boxes.any { !it.isHumanVerified } || command.annotations.points.any { !it.isHumanVerified } || command.annotations.tags.any { !it.isHumanVerified }) "PROPOSALS_AVAILABLE" else "IN_PROGRESS", updatedAt = System.currentTimeMillis()))
                            batchEngine.saveSampleAnnotations(command.sampleId, command.annotations)
                        }
                        failedSaves.remove(command.sampleId)
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { failedSaves.add(command.sampleId)
                    } finally {
                        queuedSaves--
                        _saveState.value = when {
                            failedSaves.isNotEmpty() -> "Échec d’enregistrement — réessayez avant de quitter"
                            queuedSaves > 0 -> "Enregistrement…"
                            else -> "Enregistré sur cet appareil"
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            if (db.projectDao().getProjectSync(_activeProjectId.value) == null) db.projectDao().saveProject(ProjectEntity(
                id = _activeProjectId.value, name = "Mon atelier", hfSourceRepo = "", hfDestRepo = "", classesCsv = "object", activeTasksCsv = "DETECTION"
            ))
            val latest = db.batchDao().getLatestBatchSync(_activeProjectId.value)
            val previous = db.batchDao().getBatchSync(_activeProjectId.value, preferenceStore.lastBatch)
            loadBatch(previous?.batchNumber ?: latest?.batchNumber ?: 1)
        }
        checkTokenStatus()
        viewModelScope.launch { runCatching { _communityModels.value=CommunityModelCatalog.discover(hfApiClient) } }
    }

    fun updatePreferences(value: StudioPreferences) = preferenceStore.update(value)
    fun reportError(message: String) { _operationProgress.value = OperationProgress(message, 0, 1, true) }
    fun clearOperationProgress() { if (!_isBusy.value) _operationProgress.value = null }
    fun clearEditorIssues() { _editorIssues.value = emptyList() }

    private fun operation(block: suspend () -> Unit) {
        if (_isBusy.value || _editorBusy.value) return
        _isBusy.value = true
        operationJob = viewModelScope.launch {
            try { flushEdits(); block() }
            catch (e: CancellationException) { reportError("Opération interrompue. Les corrections et les copies non vérifiées sont conservées."); throw e }
            catch (e: Exception) { reportError(e.message ?: "L’opération a échoué. Aucune validation n’a été inventée.") }
            finally { _isBusy.value = false }
        }
    }
    private suspend fun flushEdits() {
        val done = CompletableDeferred<Unit>()
        edits.send(EditCommand.Barrier(done))
        done.await()
        check(failedSaves.isEmpty()) { "Une correction n’a pas été enregistrée. Utilisez Réessayer dans l’éditeur." }
    }
    private fun editorAction(block: suspend () -> Unit) {
        if (_editorBusy.value || _isBusy.value) return
        _editorBusy.value = true
        viewModelScope.launch {
            try { flushEdits(); block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _editorIssues.value = listOf(e.message ?: "Action impossible") }
            finally { _editorBusy.value = false }
        }
    }
    fun navigateTo(screen: Screen) {
        if (_isBusy.value || _editorBusy.value) return
        if (_currentScreen.value is Screen.AnnotationEditor) editorAction { _currentScreen.value = screen }
        else _currentScreen.value = screen
    }
    fun back() = navigateTo(if (_currentScreen.value is Screen.AnnotationEditor) Screen.BatchGrid else Screen.Home)

    fun checkTokenStatus() {
        viewModelScope.launch {
            _authStatus.value = if (keystoreManager.getToken().isNullOrBlank()) HfWhoAmIResult(false, error = "Mode public — publication non connectée") else hfApiClient.verifyToken()
        }
    }
    fun saveToken(token: String) {
        try { keystoreManager.saveToken(token.trim()); checkTokenStatus() }
        catch (_: Exception) { reportError("Impossible de chiffrer le jeton sur cet appareil.") }
    }

    fun saveSetup(name: String, sourceRepo: String, destRepo: String, sourceConfig: String, sourceSplit: String,
                  imageColumn: String, classesCsv: String, diskBudgetMb: Long, tasks: Set<StudioTask>, startBatch: Boolean = false) = operation {
        val old = db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val local = ProjectSettings.read(old).sourceMode == "LOCAL_INDEX"
        val source = if (local && sourceRepo.isBlank()) "" else StudioWorkflow.normalizeRepo(sourceRepo) ?: error("Dataset source invalide. Utilisez namespace/dataset ou une URL HF de dataset.")
        val destination = if (destRepo.isBlank()) "" else StudioWorkflow.normalizeRepo(destRepo, true) ?: error("Destination invalide : utilisateur/dataset attendu.")
        check(destination.isBlank() || source != destination) { "Source et destination doivent être différentes." }
        check(classesCsv.split(',').any { it.isNotBlank() }) { "Définissez au moins une classe." }
        val p = old
        check(db.batchDao().getBatches(p.id).first().none{it.status in setOf("PUBLISHING","PUBLISHED")}) { "Terminez le transfert interrompu avant de modifier le projet" }
        val hasBatches = db.batchDao().getLatestBatchSync(_activeProjectId.value) != null || ProjectSettings.read(p).sourceIndexReady
        val published = db.batchDao().getBatches(_activeProjectId.value).first().any { it.hfCommitSha != null || it.remotePrefix != null }
        check(!published || destination == p.hfDestRepo) { "La destination d’un atelier déjà publié est verrouillée pour préserver les preuves de publication." }
        check(!hasBatches || (p.hfSourceRepo == source && p.sourceConfig == sourceConfig.trim() && p.sourceSplit == sourceSplit.trim() && p.imageColumn == imageColumn.trim())) {
            "La source d’un atelier déjà alimenté est verrouillée pour préserver la provenance. Créez un autre projet dans Moteur et transferts pour changer de source."
        }
        val updated = p.copy(name = name.trim().ifBlank { "Mon atelier" }, hfSourceRepo = source, hfDestRepo = destination,
            sourceConfig = sourceConfig.trim().ifBlank { "default" }, sourceSplit = sourceSplit.trim().ifBlank { "train" },
            imageColumn = imageColumn.trim().ifBlank { "image" }, classesCsv = classesCsv.split(',').map(String::trim).filter(String::isNotBlank).distinct().joinToString(","),
            diskBudgetMb = diskBudgetMb.coerceIn(128L, 65536L), activeTasksCsv = StudioWorkflow.tasksCsv(tasks), updatedAt = System.currentTimeMillis())
        db.projectDao().saveProject(updated)
        _operationProgress.value = null
        if (startBatch) prepareBatch(updated, _activeBatchNumber.value) else _currentScreen.value = Screen.Home
    }

    fun updateProjectSettings(sourceRepo: String, destRepo: String, sourceConfig: String, sourceSplit: String, imageColumn: String, classesCsv: String, diskBudgetMb: Long) {
        val p = projectFlow.value
        saveSetup(p?.name ?: "Mon atelier", sourceRepo, destRepo, sourceConfig, sourceSplit, imageColumn, classesCsv, diskBudgetMb,
            StudioWorkflow.parseTasks(p?.activeTasksCsv ?: "DETECTION"))
    }
    fun updateTasks(tasks: Set<StudioTask>) = operation {
        val p = db.projectDao().getProjectSync(_activeProjectId.value) ?: return@operation
        check(db.batchDao().getBatches(p.id).first().none{it.status in setOf("PUBLISHING","PUBLISHED")}) { "Transfert en attente : profil verrouillé" }
        db.projectDao().saveProject(p.copy(activeTasksCsv = StudioWorkflow.tasksCsv(tasks), updatedAt = System.currentTimeMillis()))
        _operationProgress.value = null
    }
    fun inspectSourceDataset(repoId: String, config: String? = null, split: String? = null) = operation {
        val clean = StudioWorkflow.normalizeRepo(repoId) ?: error("Lien HF invalide.")
        _operationProgress.value = OperationProgress("Inspection de la source…", 0, 1)
        val result = hfApiClient.fetchViewerSplits(clean)
        val chosen = result.splits.firstOrNull { it.config == config && (split == null || it.split == split) } ?: result.splits.firstOrNull()
        val cfg = chosen?.config ?: config ?: "default"
        val sp = chosen?.split ?: split ?: "train"
        val rows = hfApiClient.fetchViewerRows(clean, cfg, sp, 0, 3)
        _sourceInspection.value = HfSourceInspectionState(rows.success, clean, result.splits, cfg, sp,
            rows.columns, rows.columns.firstOrNull { it.contains("image", true) } ?: "image", rows.rows,
            if (!rows.success) rows.error ?: result.error else null)
        _operationProgress.value = if (rows.success) null else OperationProgress(rows.error ?: "Dataset Viewer indisponible pour cette source.", 0, 1, true)
    }
    fun checkDestinationRepo(destRepo: String) = operation {
        _destRepoStatus.value = hfApiClient.checkDatasetAccess(StudioWorkflow.normalizeRepo(destRepo, true) ?: error("Destination invalide."))
    }
    fun createDestinationRepo(destRepo: String) = operation {
        val clean = StudioWorkflow.normalizeRepo(destRepo, true) ?: error("Destination invalide.")
        check(hfApiClient.createDatasetRepo(clean, true)) { "Création refusée. Vérifiez les droits d’écriture du jeton." }
        _destRepoStatus.value = hfApiClient.checkDatasetAccess(clean)
    }

    fun loadBatch(batchNumber: Int) {
        _activeBatchNumber.value = batchNumber
        _lastExportedZip.value = null
        preferenceStore.lastBatch = batchNumber
        batchObserver?.cancel()
        val projectId = _activeProjectId.value
        _batchSamples.value = emptyList()
        batchObserver = viewModelScope.launch {
            _lastExportedZip.value = db.batchDao().getBatchSync(projectId,batchNumber)?.archivePath?.let(::File)?.takeIf { it.isFile }
            db.sampleDao().getSamplesForBatch(projectId, batchNumber).collect { _batchSamples.value = it }
        }
    }
    fun fetchAndPrepareBatch(batchNumber: Int) = operation {
        prepareBatch(db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Configurez d’abord l’atelier."), batchNumber)
    }
    fun nextBatch() = operation {
        val latest = db.batchDao().getLatestBatchSync(_activeProjectId.value)
        val policy = ProjectSettings.read(db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent"))
        check(latest == null || latest.status == "PURGED" || (latest.status == "VERIFIED" && policy.keepVerifiedBatches)) { "Vérifiez une copie locale ou HF, puis purgez le cache ou activez la conservation des lots vérifiés." }
        prepareBatch(db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Atelier absent"), (latest?.batchNumber ?: 0) + 1)
    }
    private suspend fun prepareBatch(project: ProjectEntity, batchNumber: Int) {
        val policy = ProjectSettings.read(project)
        check(policy.sourceMode == "LOCAL_INDEX" || project.hfSourceRepo.isNotBlank()) { "Renseignez la source dans la configuration." }
        check(policy.sourceMode == "HF_VIEWER" || policy.sourceIndexReady) { "Indexez d’abord le manifeste ou le dossier dans Moteur et transferts." }
        val existing = db.batchDao().getBatchSync(project.id, batchNumber)
        if (existing == null) {
            val previous = db.batchDao().getLatestBatchSync(_activeProjectId.value)
            check(previous == null || previous.status == "PURGED" || (previous.status == "VERIFIED" && policy.keepVerifiedBatches)) { "Un autre lot n’a pas encore de copie vérifiée." }
            _operationProgress.value = OperationProgress("Découverte du lot $batchNumber…", 0, policy.batchSize)
            val result = batchEngine.discoverViewerBatch(project, batchNumber, policy.batchSize)
            check(result.success) { result.error ?: "Découverte impossible" }
        } else check(existing.status !in setOf("VERIFIED", "PURGED", "PUBLISHED", "PUBLISHING")) { "Ce lot a déjà été publié." }
        batchEngine.acquireBatchImages(project.id, batchNumber) { done, total -> _operationProgress.value = OperationProgress("Téléchargement des images", done, total) }
        // Inference is explicitly launched by the curator: importing weights is not consent to replace proposals.
        loadBatch(batchNumber)
        val cases = db.sampleDao().getSamplesForBatchSync(project.id, batchNumber)
        val failed = cases.count { it.acquisitionStatus != "AVAILABLE" }
        _operationProgress.value = if (failed > 0) OperationProgress("$failed image(s) indisponible(s). Le lot reste récupérable avec Réessayer.", 0, cases.size, true) else null
        _currentScreen.value = Screen.BatchGrid
    }

    fun resumeWork() {
        val list = _batchSamples.value
        val last = list.firstOrNull { it.sampleId == preferenceStore.lastSampleId && StudioWorkflow.isPending(it.annotationStatus) && it.localImagePath != null }
        val next = last ?: list.firstOrNull { StudioWorkflow.isPending(it.annotationStatus) && it.localImagePath != null && it.acquisitionStatus == "AVAILABLE" }
        if (next != null) openSampleInEditor(next.sampleId) else navigateTo(Screen.BatchGrid)
    }
    private suspend fun openSample(sampleId: String) {
        val sample = db.sampleDao().getSampleSync(sampleId) ?: error("Cas introuvable.")
        check(sample.projectId == _activeProjectId.value) { "Ce cas appartient à un autre projet" }
        check(db.batchDao().getBatchSync(sample.projectId, sample.batchNumber)?.status !in com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates) { "Ce lot est verrouillé par une publication; consultez Export pour reprendre la vérification." }
        val available = withContext(Dispatchers.IO) { sample.localImagePath?.let { File(it).isFile } == true }
        check(StudioWorkflow.canEdit(sample.acquisitionStatus, sample.syncStatus, available)) { "Image non disponible ou déjà publiée. Les métadonnées restent dans l’historique." }
        _currentSample.value = sample
        _currentAnnotations.value = batchEngine.getSampleAnnotations(sampleId)
        undoStack.clear(); redoStack.clear(); refreshUndo()
        _editorIssues.value = emptyList()
        preferenceStore.lastSampleId = sampleId
        _currentScreen.value = Screen.AnnotationEditor(sampleId)
    }
    fun openSampleInEditor(sampleId: String) = editorAction { openSample(sampleId) }
    fun moveSample(delta: Int) = editorAction {
        val list = _batchSamples.value.filter { StudioWorkflow.canEdit(it.acquisitionStatus, it.syncStatus, it.localImagePath != null) }
        val index = list.indexOfFirst { it.sampleId == _currentSample.value?.sampleId }
        list.getOrNull(index + delta)?.let { openSample(it.sampleId) }
    }
    private fun refreshUndo() { _undoAvailable.value = undoStack.isNotEmpty(); _redoAvailable.value = redoStack.isNotEmpty() }
    private fun enqueueSave(a: SampleAnnotations) {
        val s = _currentSample.value ?: return
        queuedSaves++
        _saveState.value = "Enregistrement…"
        edits.trySend(EditCommand.Save(s.sampleId, a))
    }
    fun retrySave() = enqueueSave(_currentAnnotations.value)
    fun updateAnnotations(newAnnot: SampleAnnotations) {
        if (_editorBusy.value || _isBusy.value || newAnnot == _currentAnnotations.value) return
        undoStack.add(_currentAnnotations.value)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
        val oldPoints = _currentAnnotations.value.points.associateBy { it.id }
        val oldBoxes = _currentAnnotations.value.boxes.associateBy { it.id }
        val adjusted = newAnnot.copy(
            points=newAnnot.points.map { point ->
                val before=oldPoints[point.id]
                if (before != null && point.modelX != null && point.modelY != null && (before.x != point.x || before.y != point.y)) point.copy(explicitlyAdjusted=true) else point
            },
            boxes=newAnnot.boxes.map { box ->
                val before=oldBoxes[box.id]
                if(before!=null && box.modelXmin!=null && box.modelYmin!=null && box.modelXmax!=null && box.modelYmax!=null &&
                    (before.xmin!=box.xmin || before.ymin!=box.ymin || before.xmax!=box.xmax || before.ymax!=box.ymax)) box.copy(explicitlyAdjusted=true) else box
            }
        )
        _currentAnnotations.value = adjusted
        _editorIssues.value = emptyList()
        refreshUndo(); enqueueSave(adjusted)
    }
    fun undo() {
        if (_editorBusy.value || _isBusy.value || undoStack.isEmpty()) return
        redoStack.add(_currentAnnotations.value)
        _currentAnnotations.value = undoStack.removeAt(undoStack.lastIndex)
        refreshUndo(); enqueueSave(_currentAnnotations.value)
    }
    fun redo() {
        if (_editorBusy.value || _isBusy.value || redoStack.isEmpty()) return
        undoStack.add(_currentAnnotations.value)
        _currentAnnotations.value = redoStack.removeAt(redoStack.lastIndex)
        refreshUndo(); enqueueSave(_currentAnnotations.value)
    }
    fun validateCurrentAndNext() = editorAction {
        val s = _currentSample.value ?: return@editorAction
        val p = db.projectDao().getProjectSync(_activeProjectId.value) ?: return@editorAction
        val issues = AnnotationReview.problems(_currentAnnotations.value, StudioWorkflow.parseTasks(p.activeTasksCsv))
        if (issues.isNotEmpty()) { _editorIssues.value = issues; return@editorAction }
        batchEngine.validateSample(s.sampleId, s.batchNumber)
        if (preferences.value.autoAdvance) {
            val list = db.sampleDao().getSamplesForBatchSync(s.projectId, s.batchNumber)
            val eligible = list.filter { it.sampleId != s.sampleId && StudioWorkflow.isPending(it.annotationStatus) && StudioWorkflow.canEdit(it.acquisitionStatus, it.syncStatus, it.localImagePath != null) }
            val next = eligible.firstOrNull { (it.sourceOrdinal ?: it.sourceRowIndex) > (s.sourceOrdinal ?: s.sourceRowIndex) } ?: eligible.firstOrNull()
            if (next != null) openSample(next.sampleId) else _currentScreen.value = Screen.BatchGrid
        } else { _currentScreen.value = Screen.BatchGrid }
    }
    fun rejectCurrent(reason: String) = editorAction {
        require(reason.isNotBlank()) { "Un motif est requis." }
        val s = _currentSample.value ?: return@editorAction
        batchEngine.rejectSample(s.sampleId, s.batchNumber, reason.trim())
        _currentScreen.value = Screen.BatchGrid
    }
    fun deferCurrent() = editorAction {
        val s = _currentSample.value ?: return@editorAction
        batchEngine.deferSample(s.sampleId, s.batchNumber)
        _currentScreen.value = Screen.BatchGrid
    }
    fun bulkAction(ids: Set<String>, action: String, value: String = "") = operation {
        for (id in ids) {
            val s = db.sampleDao().getSampleSync(id) ?: continue
            if (db.batchDao().getBatchSync(s.projectId,s.batchNumber)?.status in com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates) continue
            if (s.projectId != _activeProjectId.value || !StudioWorkflow.canEdit(s.acquisitionStatus, s.syncStatus, s.localImagePath != null)) continue
            when (action) {
                "defer" -> batchEngine.deferSample(id, s.batchNumber)
                "tag" -> {
                    require(value.isNotBlank()) { "Le tag ne doit pas être vide." }
                    val a = batchEngine.getSampleAnnotations(id)
                    if (a.tags.none { it.label == value.trim() }) {
                        db.withTransaction {
                            db.sampleDao().updateSample(s.copy(annotationStatus = "IN_PROGRESS", updatedAt = System.currentTimeMillis()))
                            batchEngine.saveSampleAnnotations(id, a.copy(tags = a.tags + TagTarget(UUID.randomUUID().toString(), value.trim(), isHumanVerified = true)))
                        }
                    }
                }
            }
        }
        _operationProgress.value = OperationProgress("Action appliquée aux cas modifiables. Aucune validation automatique.", 1, 1)
    }

    private fun modelConfig(project: ProjectEntity): ModelConfig = project.modelConfigJson?.takeIf { it.isNotBlank() }?.let {
        moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(it) ?: error("Configuration modèle vide")
    }?.also(ModelContract::validate) ?: ModelConfig.defaultDetectionPreset(project.classesCsv.split(',').map(String::trim)).also(ModelContract::validate)

    fun importModel(uri: Uri) = operation {
        val p = db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Atelier absent")
        val file = storageManager.getModelFile("model-${UUID.randomUUID()}.tflite")
        try {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            written += n
                            check(written <= 256L * 1024 * 1024 && storageManager.hasAvailableBudget(n.toLong(), p.diskBudgetMb, ProjectSettings.read(p).reserveFreeMb)) { "Modèle trop volumineux ou budget disque insuffisant." }
                            output.write(buf, 0, n)
                        }
                    }
                } ?: error("Fichier inaccessible")
            }
            check(withContext(Dispatchers.IO) { liteRtEngine.loadModel(file) }) { "Modèle incompatible avec le runtime installé." }
            db.projectDao().saveProject(p.copy(modelPath = file.absolutePath, updatedAt = System.currentTimeMillis()))
            registerModel(p, file, file.nameWithoutExtension)
            liteRtEngine.close()
            _operationProgress.value = OperationProgress("Modèle importé. Vérifiez son contrat d’entrée/sortie avant l’inférence.", 1, 1)
        } catch (e: Exception) { liteRtEngine.close(); file.delete(); throw e }
    }
    fun importModelUrl(url: String) = operation {
        require(url.startsWith("https://")) { "Un lien HTTPS direct vers les poids est requis." }
        val p = db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Atelier absent")
        val policy=ProjectSettings.read(p);batchEngine.checkNetwork(policy);hfApiClient.configureTimeout(policy.timeoutSeconds)
        val file = storageManager.getModelFile("model-${UUID.randomUUID()}.tflite")
        try {
            val remaining = withContext(Dispatchers.IO) {
                minOf(256L * 1024 * 1024, p.diskBudgetMb * 1024 * 1024 - storageManager.getUsedSpaceBytes(), storageManager.getFreeSpaceBytes() - policy.reserveFreeMb * 1024L * 1024)
            }
            check(remaining > 0L) { "Budget disque insuffisant pour ce modèle." }
            check(hfApiClient.downloadImage(url.trim(), file, maxBytes = remaining)) { "Téléchargement du modèle impossible ou trop volumineux." }
            check(withContext(Dispatchers.IO) { liteRtEngine.loadModel(file) }) { "Modèle incompatible avec le runtime installé." }
            db.projectDao().saveProject(p.copy(modelPath = file.absolutePath, updatedAt = System.currentTimeMillis()))
            registerModel(p, file, file.nameWithoutExtension)
            liteRtEngine.close()
            _operationProgress.value = OperationProgress("Modèle téléchargé. Le contrat d’inférence doit être vérifié.", 1, 1)
        } catch (e: Exception) { liteRtEngine.close(); file.delete(); throw e }
    }
    fun saveModelConfig(json: String) = operation {
        val config=moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(json) ?: error("Contrat JSON absent")
        ModelContract.validate(config)
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        db.projectDao().saveProject(p.copy(modelConfigJson=moshi.adapter(ModelConfig::class.java).toJson(config),updatedAt=System.currentTimeMillis()))
        _dryRunResult.value=null
        _operationProgress.value=OperationProgress("Contrat enregistré. Exécutez un essai sur une image réelle avant le lot.",1,1)
    }
    private suspend fun loadForInference(p:ProjectEntity,c:ModelConfig) {
        ModelContract.validate(c)
        if(c.runtime!="local_http") {
            val file=p.modelPath?.let(::File) ?: error("Sélectionnez ou importez des poids LiteRT")
            check(withContext(Dispatchers.IO){liteRtEngine.loadModel(file,c.threads)}) { "Modèle non chargeable" }
            _modelDiagnostics.value=liteRtEngine.tensorReport()
        } else _modelDiagnostics.value="Serveur loopback fourni par l’utilisateur, non intégré à l’APK. Aucun token HF transmis."
    }
    fun preannotateActiveBatch() = operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val c=modelConfig(p)
        try {
            loadForInference(p,c)
            val processed=batchEngine.runBatchInference(p.id,_activeBatchNumber.value,c) { done,total ->
                _operationProgress.value=OperationProgress("Préannotation $done/$total. Gardez l’application ouverte.",done,total.coerceAtLeast(1))
            }
            _operationProgress.value=OperationProgress("$processed cas prétraité(s). Les résultats restent à valider; aucun résultat ne prouve une absence.",1,1)
        } finally { liteRtEngine.close() }
    }
    private suspend fun decodeSample(s:SampleEntity):Bitmap = withContext(Dispatchers.IO) {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(s.localImagePath,bounds)
        var factor=1;while(maxOf(bounds.outWidth,bounds.outHeight)/factor>2048)factor*=2
        BitmapFactory.decodeFile(s.localImagePath,BitmapFactory.Options().apply { inSampleSize=factor }) ?: error("Image non décodable")
    }
    fun runLiteRtOnCurrentSample() = operation {
        val s=_currentSample.value ?: error("Aucun cas actif")
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val c=modelConfig(p)
        try {
            loadForInference(p,c);val bitmap=decodeSample(s)
            val raw=try { liteRtEngine.runInference(bitmap,c) } finally { bitmap.recycle() }
            val proposals=AdaptiveCorrection.apply(raw,if(ProjectSettings.read(p).adaptiveCorrection)withContext(Dispatchers.IO){correctionStore.read(p.id)} else CorrectionLedger())
            liteRtEngine.lastError?.let { error("Inférence échouée : $it") }
            val a=_currentAnnotations.value
            undoStack.add(a);redoStack.clear();refreshUndo()
            _currentAnnotations.value=ProposalMerger.merge(a,proposals,p.activeTasksCsv,c.captionLanguage,ModelContract.outputTypes(c))
            enqueueSave(_currentAnnotations.value)
            _operationProgress.value=OperationProgress("${proposals.size} proposition(s) reçue(s), appliquées aux tâches actives; corrections conservées.",1,1)
        } finally { liteRtEngine.close() }
    }
    fun acceptCurrentProposals() {
        val a=_currentAnnotations.value
        updateAnnotations(a.copy(points=a.points.map{it.copy(isHumanVerified=true)},boxes=a.boxes.map{it.copy(isHumanVerified=true)},
            tags=a.tags.map{it.copy(isHumanVerified=true)},captions=a.captions.map{it.copy(isHumanVerified=true)},
            groundings=a.groundings.map{it.copy(isHumanVerified=true)},vqaList=a.vqaList.map{it.copy(isHumanVerified=true)},counts=a.counts.map{it.copy(isHumanVerified=true)}))
    }
    fun dryRunActiveModel() = operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val c=modelConfig(p)
        val s=_currentSample.value?.takeIf{it.projectId==p.id && it.localImagePath!=null}
            ?: _batchSamples.value.firstOrNull{it.acquisitionStatus=="AVAILABLE" && it.localImagePath!=null}
            ?: error("Téléchargez un lot pour tester le modèle sur une vraie image. L’essai ne modifie aucune annotation.")
        try {
            loadForInference(p,c);val bitmap=decodeSample(s)
            _dryRunResult.value=try{liteRtEngine.dryRun(bitmap,c)}finally{bitmap.recycle()}
        } finally { liteRtEngine.close() }
    }
    private val _benchmarkReport=MutableStateFlow<String?>(null)
    val benchmarkReport:StateFlow<String?> = _benchmarkReport
    fun benchmarkActiveModel(repetitions:Int=10)=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val config=modelConfig(p)
        val sample=_currentSample.value?.takeIf{it.projectId==p.id && it.localImagePath!=null}
            ?: _batchSamples.value.firstOrNull{it.acquisitionStatus=="AVAILABLE" && it.localImagePath!=null}
            ?: error("Téléchargez une image avant de mesurer")
        _benchmarkReport.value=null
        try {
            liteRtEngine.close();val start=System.nanoTime();loadForInference(p,config);val loadMs=(System.nanoTime()-start)/1e6
            val bitmap=decodeSample(sample)
            val modelHash=if(config.runtime=="local_http")"external-server-unknown" else withContext(Dispatchers.IO){com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(File(p.modelPath!!))}
            val result=try { withContext(Dispatchers.Default) { DeviceBenchmark.run(liteRtEngine,bitmap,config,loadMs,modelHash,repetitions) { done,total ->
                _operationProgress.value=OperationProgress("Mesure $done/$total (aucune annotation modifiée)",done,total)
            } } } finally { bitmap.recycle() }
            _benchmarkReport.value=moshi.adapter(Map::class.java).indent("  ").toJson(result)
            _operationProgress.value=OperationProgress("Mesure terminée sur cet appareil. La mémoire maximale est échantillonnée, pas un pic continu.",1,1)
        } finally { liteRtEngine.close() }
    }
    fun saveBenchmark(uri:Uri)=operation {
        val report=_benchmarkReport.value ?: error("Aucune mesure disponible")
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.use { it.write(report.toByteArray());it.flush() }
                ?: error("Rapport non enregistrable")
        }
        _operationProgress.value=OperationProgress("Rapport de mesure enregistré; aucune image ni jeton inclus.",1,1)
    }
    fun refreshCommunityModelCatalog()=operation {
        _operationProgress.value=OperationProgress("Lecture du catalogue LiteRT UWD…",0,1)
        _communityModels.value=CommunityModelCatalog.discover(hfApiClient)
        _operationProgress.value=OperationProgress("Catalogue actualisé : ${_communityModels.value.count{it.available}} conversion(s) disponible(s).",1,1)
    }
    fun downloadCommunityModel(id:String)=operation {
        val item=_communityModels.value.firstOrNull{it.entry.id==id} ?: CommunityModelCatalog.discover(hfApiClient).firstOrNull{it.entry.id==id} ?: error("Modèle absent du catalogue")
        require(item.available) { item.note }
        require(item.installableNow) { "Ce modèle est un bundle ou nécessite encore un runtime spécialisé. Il est visible dans le catalogue mais n’est pas activé artificiellement." }
        val remoteName=item.entry.expectedFiles.single()
        val remote=item.files.firstOrNull{it.path.endsWith("/$remoteName")} ?: error("Artefact distant absent")
        val project=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val settings=ProjectSettings.read(project);batchEngine.checkNetwork(settings)
        val reserve=(remote.size.takeIf{it>0} ?: 256L*1024*1024)+8L*1024*1024
        check(reserve<=2L*1024*1024*1024 && storageManager.hasAvailableBudget(reserve,project.diskBudgetMb,settings.reserveFreeMb)) { "Budget disque insuffisant pour ce modèle" }
        val file=storageManager.getModelFile("community-${item.entry.id}-$remoteName")
        val profiles=db.modelProfileDao().observe().first()
        check(profiles.none{it.modelPath==file.path}) { "Modèle déjà téléchargé; utilisez sa fiche locale" }
        var saved=false
        try {
            _operationProgress.value=OperationProgress("Téléchargement ${item.entry.title}…",0,1)
            check(hfApiClient.downloadModelFile(CommunityModelCatalog.repoId,item.repoSha,remote.path,file,reserve)) { "Téléchargement interrompu" }
            val config=withContext(Dispatchers.IO){CommunityModelCatalog.suggestedConfig(item,file)}
            val hash=withContext(Dispatchers.IO){com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(file)}
            val report=try {
                check(withContext(Dispatchers.IO){liteRtEngine.loadModel(file,config.threads)}) { liteRtEngine.lastError ?: "Runtime incapable de charger ce modèle" }
                "Catalogue: ${CommunityModelCatalog.repoId}@${item.repoSha}\n${item.note}\nLicence upstream déclarée: ${item.entry.upstreamLicense}\n"+liteRtEngine.tensorReport()
            } finally { liteRtEngine.close() }
            db.modelProfileDao().save(ModelProfileEntity(UUID.randomUUID().toString(),item.entry.title,file.path,hash,moshi.adapter(ModelConfig::class.java).toJson(config),report))
            saved=true;_modelDiagnostics.value=report
            _operationProgress.value=OperationProgress("${item.entry.title} ajouté à la bibliothèque locale. Il n’est pas activé automatiquement.",1,1)
        } finally { if(!saved && file.isFile)withContext(NonCancellable+Dispatchers.IO){file.delete()} }
    }
    fun downloadCatalogModel(id:String)=operation {
        val entry=PublicModelCatalog.entries.single{it.id==id}
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val settings=ProjectSettings.read(p);batchEngine.checkNetwork(settings)
        val file=storageManager.getModelFile("catalog-${entry.id}.tflite")
        // Never overwrite bytes referenced by an existing model profile.
        val profiles=db.modelProfileDao().observe().first()
        check(profiles.none{it.modelPath==file.path}) { "Modèle déjà dans la bibliothèque; sélectionnez son profil" }
        check(storageManager.hasAvailableBudget(entry.maxBytes,p.diskBudgetMb,settings.reserveFreeMb)) { "Budget insuffisant (réserve de téléchargement 64 Mo)" }
        var saved=false
        try {
            _operationProgress.value=OperationProgress("Téléchargement ${entry.title}…",0,1)
            check(hfApiClient.downloadImage(entry.url,file,maxBytes=entry.maxBytes)) { "Téléchargement interrompu; relancez pour reprendre le fichier lorsque le serveur le permet" }
            val inspected=withContext(Dispatchers.IO){PublicModelCatalog.inspect(entry,file)}
            val hash=withContext(Dispatchers.IO){com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(file)}
            val config=moshi.adapter(ModelConfig::class.java).toJson(inspected.config)
            val report=try {
                check(withContext(Dispatchers.IO){liteRtEngine.loadModel(file,inspected.config.threads)}) { "Runtime incapable de charger ces poids" }
                inspected.note+"\n"+liteRtEngine.tensorReport()
            } finally { liteRtEngine.close() }
            db.modelProfileDao().save(ModelProfileEntity(UUID.randomUUID().toString(),entry.title,file.path,hash,config,report))
            saved=true;_modelDiagnostics.value=report
            _operationProgress.value=OperationProgress("Profil ajouté, non activé. Sélectionnez-le puis testez-le sur une image; adaptez les classes du projet séparément.",1,1)
        } finally { if(!saved && file.isFile)withContext(NonCancellable+Dispatchers.IO){file.delete()} }
    }
    fun runModelDryRun(modelFile:File,sampleBitmap:Bitmap) = operation {
        try {
            val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
            val c=modelConfig(p)
            check(withContext(Dispatchers.IO){liteRtEngine.loadModel(modelFile,c.threads)})
            _dryRunResult.value=liteRtEngine.dryRun(sampleBitmap,c)
        } finally { liteRtEngine.close() }
    }

    fun exportActiveBatchToLocalZip(includeWebDataset: Boolean = true, includeJsonl: Boolean = true, includeCoco: Boolean = true, includeYolo: Boolean = true, includeVl: Boolean = true) = operation {
        _operationProgress.value = OperationProgress("Préparation de l’archive locale…", 0, 1)
        val p = db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Atelier absent")
        check(db.batchDao().getBatchSync(_activeProjectId.value, _activeBatchNumber.value)?.status !in com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates) { "Le paquet publié doit rester inchangé pour vérification. Récupérez les fichiers depuis le commit HF ou conservez l’archive déjà prête." }
        val validated = db.sampleDao().getSamplesForBatchSync(_activeProjectId.value, _activeBatchNumber.value).filter { it.annotationStatus == "VALIDATED" && it.localImagePath != null }
        check(validated.isNotEmpty()) { "Aucun cas validé avec image locale." }
        val pairs = validated.map { it to batchEngine.getSampleAnnotations(it.sampleId) }
        check(includeWebDataset || includeJsonl || includeCoco || includeYolo || includeVl) { "Sélectionnez au moins un format." }
        val result = withContext(Dispatchers.IO) { exporters.packageBatchToLocalZip(p, _activeBatchNumber.value, pairs, includeWebDataset, includeJsonl, includeCoco, includeYolo, includeVl) }
        check(result.success && result.zipFile != null) { result.error ?: "Export impossible" }
        batchEngine.recordLocalArchive(p.id,_activeBatchNumber.value,result.zipFile!!)
        _lastExportedZip.value = result.zipFile
        _operationProgress.value = OperationProgress("Archive prête : ${result.sampleCount} cas. Les originaux sont conservés.", 1, 1)
    }
    fun loadPreviewSnippet(format: String) {
        viewModelScope.launch {
            val p = db.projectDao().getProjectSync(_activeProjectId.value) ?: return@launch
            val s = _batchSamples.value.firstOrNull { it.annotationStatus == "VALIDATED" } ?: _batchSamples.value.firstOrNull()
            _previewSnippet.value = if (s == null) "Aucun cas dans ce lot." else exporters.generatePreviewSnippet(format, s, batchEngine.getSampleAnnotations(s.sampleId), p)
        }
    }
    fun saveArchiveToUri(uri: Uri) = operation {
        val file = _lastExportedZip.value?.takeIf { it.isFile } ?: error("Préparez d’abord l’archive")
        val copyReceipt=withContext(Dispatchers.IO) {
            val ctx=kotlin.coroutines.coroutineContext
            com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives.copyVerified(getApplication<Application>().contentResolver,file,uri) { ctx.ensureActive() }
        }
        if(!copyReceipt.persistentRead) {
            _operationProgress.value=OperationProgress("Copie relue mais droit de lecture non persistant. Lot non clôturé : choisissez un fournisseur SAF persistant pour autoriser le nettoyage après redémarrage.",1,1,true)
            return@operation
        }
        val allDone=db.sampleDao().getSamplesForBatchSync(_activeProjectId.value,_activeBatchNumber.value).all { it.annotationStatus in setOf("VALIDATED","REJECTED") }
        if(allDone) {
            batchEngine.verifyLocalArchive(_activeProjectId.value,_activeBatchNumber.value,uri.toString())
            _operationProgress.value=OperationProgress("Copie externe relue et lot clôturé. Vous pouvez purger le cache ou conserver ce lot selon vos réglages.",1,1)
        } else _operationProgress.value=OperationProgress("Copie partielle relue. Terminez le lot et refaites un export pour autoriser sa purge.",1,1)
    }
    fun purgeActiveBatch() = operation {
        val count = batchEngine.purgeReviewedBatch(_activeProjectId.value, _activeBatchNumber.value, discardRejectedConfirmed = true)
        _lastExportedZip.value = null
        _operationProgress.value = OperationProgress("$count images supprimées conformément à votre confirmation. Historique conservé.", 1, 1)
    }
    fun rejectFailedAcquisitions() = operation {
        db.sampleDao().getSamplesForBatchSync(_activeProjectId.value, _activeBatchNumber.value).filter { it.acquisitionStatus.startsWith("ERROR") }.forEach { s ->
            batchEngine.rejectSample(s.sampleId, s.batchNumber, "Exclusion manuelle d’un échec d’acquisition : ${s.auditReason ?: "échec"}")
        }
        _operationProgress.value = OperationProgress("Les échecs ont été rejetés avec motif. Aucune image validée n’a changé.", 1, 1)
    }
    private suspend fun switchProject(id:Long) {
        check(db.projectDao().getProjectSync(id)!=null) { "Projet introuvable" }
        batchObserver?.cancel();liteRtEngine.close()
        _currentSample.value=null;_currentAnnotations.value=SampleAnnotations();undoStack.clear();redoStack.clear();refreshUndo()
        _sourceInspection.value=HfSourceInspectionState();_destRepoStatus.value=null;_dryRunResult.value=null;_modelDiagnostics.value=""
        _lastExportedZip.value=null;_previewSnippet.value=null;_editorIssues.value=emptyList()
        preferenceStore.activeProjectId=id;_activeProjectId.value=id
        loadBatch(db.batchDao().getBatchSync(id,preferenceStore.lastBatch)?.batchNumber ?: db.batchDao().getLatestBatchSync(id)?.batchNumber ?: 1)
        _operationProgress.value=null;_currentScreen.value=Screen.Controls
    }
    fun selectProject(id:Long)=operation { switchProject(id) }
    fun createProject(name:String)=operation {
        require(name.isNotBlank()) { "Nommez le projet" }
        var id=System.currentTimeMillis();while(db.projectDao().getProjectSync(id)!=null)id++
        db.projectDao().saveProject(ProjectEntity(id=id,name=name.trim(),classesCsv="object",activeTasksCsv="DETECTION"))
        switchProject(id)
    }
    fun saveProcessingSettings(policy:ProcessingSettings,budgetMb:Long,idColumn:String,targetSplit:String)=operation {
        policy.validate();require(budgetMb in 128..65536);require(idColumn.isNotBlank())
        require(targetSplit.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Split de sortie invalide" }
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val old=ProjectSettings.read(p);val existing=db.batchDao().getBatches(p.id).first()
        val identityChanged=listOf(old.sourceMode,old.sourceRevision,old.manifestPath,old.filterExpression,old.orderBy,old.importAnnotations.toString()) !=
            listOf(policy.sourceMode,policy.sourceRevision,policy.manifestPath,policy.filterExpression,policy.orderBy,policy.importAnnotations.toString()) || idColumn!=p.idColumn
        check(existing.isEmpty() || !identityChanged) { "La source et sa sélection sont figées après le premier lot. Créez un autre projet." }
        check(existing.isEmpty() || targetSplit==p.targetSplit) { "Le split de sortie est figé après le premier lot" }
        check(existing.none{it.status in setOf("PUBLISHING","PUBLISHED")} || policy==old) { "Terminez d’abord le transfert interrompu" }
        val clean=policy.copy(sourceIndexReady=old.sourceIndexReady && !identityChanged,resolvedSourceRevision=if(identityChanged)null else old.resolvedSourceRevision,
            localSourceLabel=old.localSourceLabel,localTreeUri=old.localTreeUri)
        db.projectDao().saveProject(p.copy(settingsJson=ProjectSettings.write(clean),diskBudgetMb=budgetMb,idColumn=idColumn.trim(),targetSplit=targetSplit,updatedAt=System.currentTimeMillis()))
        _operationProgress.value=OperationProgress("Réglages enregistrés. La taille du prochain lot n’altère pas les lots déjà acquis.",1,1)
    }
    fun chooseManifestImageFolder(uri:Uri)=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        check(db.batchDao().getLatestBatchSync(p.id)==null) { "Source déjà utilisée" }
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        db.projectDao().saveProject(p.copy(settingsJson=ProjectSettings.write(ProjectSettings.read(p).copy(localTreeUri=uri.toString()))))
        _operationProgress.value=OperationProgress("Dossier d’images choisi pour résoudre les chemins relatifs du JSONL.",1,1)
    }
    fun importSourceFolder(uri:Uri)=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        _operationProgress.value=OperationProgress("Indexation des métadonnées du dossier, sans copier les images…",0,1)
        val n=batchEngine.sourceCatalog.importFolder(p,uri)
        _operationProgress.value=OperationProgress("$n images indexées. Seul le prochain lot sera copié dans le cache.",1,1)
    }
    fun importSourceManifest(uri:Uri)=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        _operationProgress.value=OperationProgress("Lecture du manifeste JSONL…",0,1)
        val n=batchEngine.sourceCatalog.importManifest(p,uri,ProjectSettings.read(p).localTreeUri?.let(Uri::parse))
        _operationProgress.value=OperationProgress("$n cas indexés. Les annotations importées, lorsqu’activées, sont des brouillons à relire.",1,1)
    }
    fun fetchHfManifest()=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val policy=ProjectSettings.read(p);batchEngine.checkNetwork(policy);hfApiClient.configureTimeout(policy.timeoutSeconds)
        _operationProgress.value=OperationProgress("Résolution de la révision HF et indexation du JSONL…",0,1)
        val n=batchEngine.sourceCatalog.importHfManifest(p)
        _operationProgress.value=OperationProgress("$n cas indexés à une révision HF résolue. Les URL externes ne sont pas épinglées.",1,1)
    }
    private suspend fun registerModel(p:ProjectEntity,file:File,name:String) {
        val hash=withContext(Dispatchers.IO){com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(file)}
        val config=p.modelConfigJson ?: moshi.adapter(ModelConfig::class.java).toJson(ModelConfig.defaultDetectionPreset(p.classesCsv.split(',').map(String::trim)))
        val report=liteRtEngine.tensorReport();_modelDiagnostics.value=report
        db.modelProfileDao().save(ModelProfileEntity(UUID.randomUUID().toString(),name,file.path,hash,config,report))
    }
    fun saveActiveModelProfile(name:String)=operation {
        require(name.isNotBlank())
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val c=modelConfig(p)
        if(c.runtime=="local_http") {
            db.modelProfileDao().save(ModelProfileEntity(UUID.randomUUID().toString(),name.trim(),"","",moshi.adapter(ModelConfig::class.java).toJson(c),"Serveur loopback configuré; disponibilité à tester."))
        } else try {
            loadForInference(p,c);registerModel(p,File(p.modelPath!!),name.trim())
        } finally { liteRtEngine.close() }
        _operationProgress.value=OperationProgress("Profil ajouté à la bibliothèque de cet appareil.",1,1)
    }
    fun selectModelProfile(id:String)=operation {
        val profile=db.modelProfileDao().get(id) ?: error("Profil absent")
        val c=moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson(profile.configJson) ?: error("Contrat absent")
        ModelContract.validate(c)
        if(c.runtime!="local_http")check(withContext(Dispatchers.IO){File(profile.modelPath).isFile && com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(File(profile.modelPath))==profile.sha256}) { "Poids absents ou altérés" }
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        liteRtEngine.close();db.projectDao().saveProject(p.copy(modelPath=profile.modelPath.takeIf{it.isNotBlank()},modelConfigJson=profile.configJson,updatedAt=System.currentTimeMillis()))
        _modelDiagnostics.value=profile.tensorReport;_dryRunResult.value=null
        _operationProgress.value=OperationProgress("Profil sélectionné; classes du modèle et vocabulaire du projet restent distincts. Vérifiez leur correspondance.",1,1)
    }
    fun exportPack(uri:Uri)=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val pack=StudioPack(name=p.name,classes=p.classesCsv.split(',').map(String::trim),tasks=p.activeTasksCsv.split(','),
            batchSize=ProjectSettings.read(p).batchSize,model=p.modelConfigJson?.let{modelConfig(p)})
        val json=moshi.adapter(StudioPack::class.java).toJson(pack)
        withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(json)} ?: error("Fichier non accessible")}
        _operationProgress.value=OperationProgress("Pack exporté sans jeton HF du coffre, poids ni corpus. Le contrat et ses prompts sont inclus : relisez-les avant partage.",1,1)
    }
    fun importPack(uri:Uri)=operation {
        val text=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=256*1024){"Pack trop volumineux"};out.write(buffer,0,n)}
            out.toString("UTF-8")
        }} ?: error("Pack inaccessible")
        val pack=moshi.adapter(StudioPack::class.java).failOnUnknown().fromJson(text) ?: error("Pack JSON invalide")
        pack.validate();var id=System.currentTimeMillis();while(db.projectDao().getProjectSync(id)!=null)id++
        val modelJson=pack.model?.let{moshi.adapter(ModelConfig::class.java).toJson(it)}
        db.projectDao().saveProject(ProjectEntity(id=id,name=pack.name,classesCsv=pack.classes.joinToString(","),activeTasksCsv=pack.tasks.joinToString(","),
            settingsJson=ProjectSettings.write(ProcessingSettings(batchSize=pack.batchSize)),modelConfigJson=modelJson))
        switchProject(id)
        _operationProgress.value=OperationProgress("Nouveau projet créé depuis le pack. Ajoutez sa source et, si nécessaire, les poids autorisés.",1,1)
    }
    fun isolateConflict() = operation {
        batchEngine.isolateConflictedUpload(_activeProjectId.value,_activeBatchNumber.value)
        _operationProgress.value=OperationProgress("Nouvel emplacement isolé préparé. L’ancien envoi distant n’a pas été modifié.",1,1)
    }
    fun closeAllRejectedBatch()=operation {
        batchEngine.closeRejectedBatch(_activeProjectId.value,_activeBatchNumber.value)
        _operationProgress.value=OperationProgress("Lot intégralement rejeté clôturé. Les décisions restent en base; la purge du cache doit être confirmée séparément.",1,1)
    }
    fun detachModel()=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        liteRtEngine.close();db.projectDao().saveProject(p.copy(modelPath=null,modelConfigJson=null));_modelDiagnostics.value="";_dryRunResult.value=null
    }
    fun removeModelProfile(id:String)=operation {
        val profile=db.modelProfileDao().get(id) ?: error("Profil absent")
        val otherProfiles=db.modelProfileDao().observe().first().filter{it.id!=id}
        val users=db.projectDao().getProjects().first().filter{profile.modelPath.isNotBlank() && it.modelPath==profile.modelPath}
        check(users.isEmpty()) { "Poids utilisés par ${users.joinToString{it.name}}. Détachez-les de ces projets avant suppression." }
        if(profile.modelPath.isNotBlank() && otherProfiles.none{it.modelPath==profile.modelPath}) {
            val file=File(profile.modelPath)
            check(file.canonicalPath.startsWith(File(getApplication<Application>().filesDir,"models").canonicalPath+File.separator))
            withContext(Dispatchers.IO){if(file.exists())check(file.delete()){ "Poids non supprimables" }}
        }
        db.modelProfileDao().delete(id)
        _operationProgress.value=OperationProgress("Profil supprimé. Les poids encore référencés par un autre profil sont conservés.",1,1)
    }
    fun inspectCorrections()=operation {
        _correctionReport.value=withContext(Dispatchers.IO){correctionStore.report(_activeProjectId.value)}
    }
    fun trainCorrectionsFromBatch()=operation {
        val p=db.projectDao().getProjectSync(_activeProjectId.value) ?: error("Projet absent")
        val samples=db.sampleDao().getSamplesForBatchSync(p.id,_activeBatchNumber.value)
        val pairs=samples.map{it to batchEngine.getSampleAnnotations(it.sampleId)}
        _correctionReport.value=correctionStore.train(p,pairs)
        _operationProgress.value=OperationProgress("Corrections explicites examinées. Le modèle visuel reste fixe; consulter le rapport de promotion.",1,1)
    }
    fun resetCorrections()=operation {
        withContext(Dispatchers.IO){correctionStore.reset(_activeProjectId.value)}
        _correctionReport.value="Correcteur réinitialisé. Annotations et modèle visuel inchangés."
    }
    override fun onCleared() { liteRtEngine.close();super.onCleared() }

    fun clearPreviewSnippet() { _previewSnippet.value = null }
    fun publishActiveBatch() = operation {
        _operationProgress.value = OperationProgress("Publication et vérification distante…", 0, 1)
        val result = batchEngine.publishAndVerifyBatch(_activeProjectId.value, _activeBatchNumber.value)
        _operationProgress.value = OperationProgress(result.message, if (result.success) 1 else 0, 1, !result.success)
    }
}

data class HfSourceInspectionState(
    val isInspected: Boolean = false, val repoId: String = "", val splits: List<DatasetSplitItem> = emptyList(),
    val selectedConfig: String = "default", val selectedSplit: String = "train", val availableColumns: List<String> = emptyList(),
    val selectedImageColumn: String = "image", val previewRows: List<ViewerRowData> = emptyList(), val error: String? = null
)
data class OperationProgress(val message: String, val current: Int, val total: Int, val isError: Boolean = false)
