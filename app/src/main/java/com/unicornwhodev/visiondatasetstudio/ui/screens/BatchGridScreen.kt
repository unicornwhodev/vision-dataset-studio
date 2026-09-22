package com.unicornwhodev.visiondatasetstudio.ui.screens

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import java.io.File
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelContract

private enum class BatchFilter(private val titleText: () -> String) {
    ALL({ tr("Tous", "All") }),
    PENDING({ tr("À traiter", "To process") }),
    PROPOSALS({ "Suggestions" }),
    VALIDATED({ tr("Validés", "Approved") }),
    DEFERRED({ tr("À revoir", "To review") }),
    REJECTED({ tr("Rejetés", "Rejected") }),
    ERRORS({ tr("Erreurs", "Errors") });
    val title get() = titleText()
}
private fun matches(sample: SampleEntity, filter: BatchFilter) = when (filter) {
    BatchFilter.ALL -> true
    BatchFilter.PENDING -> StudioWorkflow.isPending(sample.annotationStatus)
    BatchFilter.PROPOSALS -> sample.annotationStatus == "PROPOSALS_AVAILABLE"
    BatchFilter.VALIDATED -> sample.annotationStatus == "VALIDATED"
    BatchFilter.DEFERRED -> sample.annotationStatus == "DEFERRED"
    BatchFilter.REJECTED -> sample.annotationStatus == "REJECTED"
    BatchFilter.ERRORS -> sample.acquisitionStatus.startsWith("ERROR")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchGridScreen(viewModel: MainViewModel) {
    val samples by viewModel.batchSamples.collectAsState()
    val batchNumber by viewModel.activeBatchNumber.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val policy = project?.let(ProjectSettings::read)
    val sourceReady = !project?.hfSourceRepo.isNullOrBlank() || policy?.sourceIndexReady == true
    val annotationModelCompatible=remember(project?.modelConfigJson,project?.activeTasksCsv) {
        runCatching { project?.modelConfigJson?.let { StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(it) }?.let { ModelContract.supportsTasks(it,project?.activeTasksCsv.orEmpty()) }==true }.getOrDefault(false)
    }
    var filter by rememberSaveable { mutableStateOf(BatchFilter.PENDING) }
    var search by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(false) }
    var selected by remember(batchNumber) { mutableStateOf(setOf<String>()) }
    var actionDialog by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var batchMenu by remember { mutableStateOf(false) }
    var rejectErrors by remember { mutableStateOf(false) }
    var confirmInference by remember { mutableStateOf(false) }
    var replaceProposals by remember { mutableStateOf(false) }
    val filtered = remember(samples, filter, search, priority) {
        samples.filter { matches(it, filter) && (search.isBlank() || it.assetId.contains(search, true) || it.sampleId.contains(search, true) || it.auditReason?.contains(search, true) == true) }
            .let { list -> if (priority) list.sortedBy { when { it.acquisitionStatus.startsWith("ERROR") -> 0; it.annotationStatus == "DEFERRED" -> 1; it.annotationStatus == "PROPOSALS_AVAILABLE" -> 2; else -> 3 } } else list }
    }
    val validated = samples.count { it.annotationStatus == "VALIDATED" }
    val gridWidth = if(LocalConfiguration.current.screenWidthDp >= 840) {
        if(preferences.gridDensity == GridDensity.COMPACT) 188.dp else 240.dp
    } else preferences.gridDensity.minCellDp.dp
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        StudioTopBar(stringResource(R.string.batch_title,batchNumber), stringResource(R.string.batch_summary,samples.size,validated), actions = {
            if (samples.isNotEmpty()) IconButton(onClick = viewModel::resumeWork,
                enabled = !busy && samples.any { StudioWorkflow.isPending(it.annotationStatus) && it.localImagePath != null },
                modifier = Modifier.testTag("start_annotating_button")) {
                Icon(Icons.Default.PlayArrow, tr("Reprendre", "Resume"), tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { batchMenu = true }, enabled = !busy) { Icon(Icons.Default.History, tr("Choisir un lot", "Choose a batch")) }
                DropdownMenu(expanded = batchMenu, onDismissRequest = { batchMenu = false }) {
                    batches.forEach { b -> DropdownMenuItem(text = { Text(tr("Lot ${b.batchNumber} · ${b.totalCases} cas · ${b.status}", "Batch ${b.batchNumber} · ${b.totalCases} samples · ${b.status}")) }, enabled = !busy, onClick = { viewModel.loadBatch(b.batchNumber); batchMenu = false }) }
                    if (batches.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.batch_none)) }, onClick = { batchMenu = false })
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Actions du lot", "Batch actions")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.batch_retry_downloads)) }, enabled = !busy, onClick = { menu = false; viewModel.fetchAndPrepareBatch(batchNumber) })
                    DropdownMenuItem(text = { Text(if(annotationModelCompatible) tr("Préannoter le lot…", "Preannotate batch…") else tr("Modèle incompatible avec les tâches", "Model incompatible with the tasks")) }, enabled = !busy && annotationModelCompatible && (project?.modelPath != null || project?.modelConfigJson?.contains("local_http") == true) && samples.any { StudioWorkflow.isPending(it.annotationStatus) }, onClick = { menu = false; replaceProposals = false; confirmInference = true })
                    DropdownMenuItem(text = { Text(tr("Relancer les propositions IA…", "Rerun AI proposals…")) }, enabled = !busy && annotationModelCompatible && samples.any { StudioWorkflow.isPending(it.annotationStatus) }, onClick = { menu = false; replaceProposals = true; confirmInference = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.batch_exclude_failures)) }, enabled = !busy && samples.any { it.acquisitionStatus.startsWith("ERROR") }, onClick = { menu = false; rejectErrors = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.publication_next_batch)) }, enabled = !busy, onClick = { menu = false; viewModel.nextBatch() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.batch_select_editable)) }, onClick = {
                        selected = filtered.filter { StudioWorkflow.canEdit(it.acquisitionStatus, it.syncStatus, it.localImagePath != null) }.map { it.sampleId }.toSet(); menu = false
                    })
                    DropdownMenuItem(text = { Text(if(priority) tr("Trier par ordre source", "Sort by source order") else tr("Prioriser erreurs et révisions", "Prioritize errors and revisions")) }, onClick = { priority = !priority; menu = false })
                }
            }
        })
    }, bottomBar = {
        if (selected.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, tr("Annuler la sélection", "Clear selection")) }
                Text("${selected.size}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { actionDialog = "defer" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.batch_defer)) }
                Button(onClick = { actionDialog = "tag" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.batch_add_tag)) }
            }
        }
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            if (samples.isEmpty()) {
                EmptyWorkspace(tr("Aucun lot préparé", "No batch prepared"), tr("Configurez une source, puis préparez vos images.", "Configure a source, then prepare your images."), Icons.Default.PhotoLibrary,
                    if (!sourceReady) tr("Configurer la source", "Configure source") else tr("Préparer le lot", "Prepare batch")) {
                    if (!sourceReady) viewModel.navigateTo(Screen.Setup) else viewModel.fetchAndPrepareBatch(batchNumber)
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) }, placeholder = { Text(tr("Rechercher une image", "Search images"), style = MaterialTheme.typography.bodySmall) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BatchFilter.entries.forEach { f ->
                            Column(Modifier.width(IntrinsicSize.Max).clickable(role = Role.Tab) { filter = f }.semantics { this.selected = filter == f }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(Modifier.heightIn(min = 46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(f.title, style = MaterialTheme.typography.labelMedium, color = if(filter == f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${samples.count { matches(it, f) }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(Modifier.fillMaxWidth().height(2.dp).background(if(filter == f) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                            }
                        }
                    }
                    if (priority) Text(tr("Priorité aux révisions", "Prioritize review"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (filtered.isEmpty()) EmptyWorkspace(tr("Aucun résultat", "No results"), tr("Essayez un autre filtre.", "Try another filter."), Icons.Default.FilterAltOff, tr("Tout afficher", "Show all")) { filter = BatchFilter.ALL; search = "" }
                else LazyVerticalGrid(columns = GridCells.Adaptive(gridWidth), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered, key = { it.sampleId }) { sample ->
                        val editable = StudioWorkflow.canEdit(sample.acquisitionStatus, sample.syncStatus, sample.localImagePath != null)
                        SampleThumbnailCard(sample, selected = sample.sampleId in selected, onLongClick = {
                            if (editable && !busy) selected = if (sample.sampleId in selected) selected - sample.sampleId else selected + sample.sampleId
                        }, onClick = {
                            if (selected.isNotEmpty() && editable) selected = if (sample.sampleId in selected) selected - sample.sampleId else selected + sample.sampleId
                            else if (editable && !busy) viewModel.openSampleInEditor(sample.sampleId)
                            else viewModel.reportError(sample.auditReason ?: tr("Ce média est indisponible ou a déjà été publié. Consultez l’historique ou relancez son téléchargement.", "This media is unavailable or already published. Check history or retry the download."))
                        })
                    }
                }
            }
        }
    }
    if (confirmInference) AlertDialog(onDismissRequest = { confirmInference = false }, title = { Text(if(replaceProposals) tr("Recalculer les propositions ?", "Recalculate proposals?") else stringResource(R.string.batch_preannotate_title)) },
        text = { Text(if(replaceProposals) tr("Les propositions IA non validées du lot seront remplacées avec les réglages actuels. Les corrections humaines et les cas finalisés restent intacts.", "Unreviewed AI proposals in this batch will be replaced using the current settings. Human corrections and finalized samples stay unchanged.") else stringResource(R.string.batch_preannotate_body)) },
        confirmButton = { Button(onClick = { confirmInference = false; viewModel.preannotateActiveBatch(replaceExistingProposals=replaceProposals) }) { Text(stringResource(R.string.batch_run)) } }, dismissButton = { TextButton(onClick = { confirmInference = false }) { Text(stringResource(R.string.common_cancel)) } })
    if (rejectErrors) AlertDialog(onDismissRequest = { rejectErrors = false }, title = { Text(stringResource(R.string.batch_reject_failures)) },
        text = { Text(stringResource(R.string.batch_reject_failures_body)) },
        confirmButton = { Button(onClick = { rejectErrors = false; viewModel.rejectFailedAcquisitions() }) { Text(stringResource(R.string.batch_exclude_cases)) } }, dismissButton = { TextButton(onClick = { rejectErrors = false }) { Text(stringResource(R.string.common_cancel)) } })
    if (actionDialog != null) AlertDialog(onDismissRequest = { actionDialog = null }, title = { Text(if (actionDialog == "tag") tr("Ajouter un tag à ${selected.size} cas", "Add a tag to ${selected.size} samples") else tr("Différer ${selected.size} cas", "Defer ${selected.size} samples")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.batch_bulk_body))
            if (actionDialog == "tag") OutlinedTextField(tag, { tag = it }, label = { Text(stringResource(R.string.batch_tag_to_add)) }, singleLine = true)
        }
    }, confirmButton = { Button(enabled = actionDialog != "tag" || tag.isNotBlank(), onClick = {
        viewModel.bulkAction(selected, actionDialog ?: "defer", tag); actionDialog = null; selected = emptySet(); tag = ""
    }) { Text(stringResource(R.string.common_apply)) } }, dismissButton = { TextButton(onClick = { actionDialog = null }) { Text(stringResource(R.string.common_cancel)) } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SampleThumbnailCard(sample: SampleEntity, onClick: () -> Unit, selected: Boolean = false, onLongClick: () -> Unit = {}) {
    val published = sample.syncStatus in setOf("PURGED", "VERIFIED", "PUBLISHED")
    val label = when {
        published -> tr("Copie vérifiée", "Copy verified")
        sample.acquisitionStatus.startsWith("ERROR") -> tr("À récupérer", "To download")
        else -> when(sample.annotationStatus) { "VALIDATED" -> tr("Validé", "Approved"); "REJECTED" -> tr("Rejeté", "Rejected"); "DEFERRED" -> tr("À revoir", "To review"); "PROPOSALS_AVAILABLE" -> "Suggestions"; "IN_PROGRESS" -> tr("En cours", "In progress"); else -> tr("À traiter", "To process") }
    }
    val icon = when(label) { tr("Copie vérifiée", "Copy verified") -> Icons.Default.CloudDone; tr("Validé", "Approved") -> Icons.Default.CheckCircleOutline; tr("À revoir", "To review") -> Icons.Default.Schedule; tr("À récupérer", "To download") -> Icons.Default.ErrorOutline; tr("Rejeté", "Rejected") -> Icons.Default.Block; else -> Icons.Default.Edit }
    val outline by androidx.compose.animation.animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "sample selection")
    Column(Modifier.clip(RoundedCornerShape(4.dp)).border(if(selected) 2.dp else 1.dp, outline, RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = onClick, onLongClickLabel = tr("Sélectionner cette image", "Select this image"), onLongClick = onLongClick)
        .semantics(mergeDescendants = true) { contentDescription = "${sample.assetId}, $label"; this.selected = selected }
        .testTag("sample_${sample.sampleId}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            if (sample.localImagePath != null) AsyncImage(model = File(sample.localImagePath), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Icon(if (published) Icons.Default.CloudDone else Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            if (selected) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Check, tr("Sélectionné", "Selected"), Modifier.padding(5.dp).size(18.dp)) }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(sample.assetId, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
