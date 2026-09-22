package com.unicornwhodev.visiondatasetstudio.ui.screens

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

private enum class BatchFilter(val title: String) { ALL("Tous"), PENDING("À traiter"), PROPOSALS("Suggestions"), VALIDATED("Validés"), DEFERRED("À revoir"), REJECTED("Rejetés"), ERRORS("Erreurs") }
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
                Icon(Icons.Default.PlayArrow, "Reprendre", tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { batchMenu = true }, enabled = !busy) { Icon(Icons.Default.History, "Choisir un lot") }
                DropdownMenu(expanded = batchMenu, onDismissRequest = { batchMenu = false }) {
                    batches.forEach { b -> DropdownMenuItem(text = { Text("Lot ${b.batchNumber} · ${b.totalCases} cas · ${b.status}") }, enabled = !busy, onClick = { viewModel.loadBatch(b.batchNumber); batchMenu = false }) }
                    if (batches.isEmpty()) DropdownMenuItem(text = { Text("Aucun lot acquis") }, onClick = { batchMenu = false })
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions du lot") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Réessayer les téléchargements") }, enabled = !busy, onClick = { menu = false; viewModel.fetchAndPrepareBatch(batchNumber) })
                    DropdownMenuItem(text = { Text(if(annotationModelCompatible) "Préannoter le lot…" else "Modèle incompatible avec les tâches") }, enabled = !busy && annotationModelCompatible && (project?.modelPath != null || project?.modelConfigJson?.contains("local_http") == true) && samples.any { StudioWorkflow.isPending(it.annotationStatus) }, onClick = { menu = false; confirmInference = true })
                    DropdownMenuItem(text = { Text("Exclure les acquisitions en échec…") }, enabled = !busy && samples.any { it.acquisitionStatus.startsWith("ERROR") }, onClick = { menu = false; rejectErrors = true })
                    DropdownMenuItem(text = { Text("Préparer le lot suivant") }, enabled = !busy, onClick = { menu = false; viewModel.nextBatch() })
                    DropdownMenuItem(text = { Text("Sélectionner les cas modifiables affichés") }, onClick = {
                        selected = filtered.filter { StudioWorkflow.canEdit(it.acquisitionStatus, it.syncStatus, it.localImagePath != null) }.map { it.sampleId }.toSet(); menu = false
                    })
                    DropdownMenuItem(text = { Text(if(priority) "Trier par ordre source" else "Prioriser erreurs et révisions") }, onClick = { priority = !priority; menu = false })
                }
            }
        })
    }, bottomBar = {
        if (selected.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "Annuler la sélection") }
                Text("${selected.size}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { actionDialog = "defer" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Différer") }
                Button(onClick = { actionDialog = "tag" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Ajouter tag") }
            }
        }
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            if (samples.isEmpty()) {
                EmptyWorkspace("Aucun lot préparé", "Configurez une source, puis préparez vos images.", Icons.Default.PhotoLibrary,
                    if (!sourceReady) "Configurer la source" else "Préparer le lot") {
                    if (!sourceReady) viewModel.navigateTo(Screen.Setup) else viewModel.fetchAndPrepareBatch(batchNumber)
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) }, placeholder = { Text("Rechercher une image", style = MaterialTheme.typography.bodySmall) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
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
                    if (priority) Text("Priorité aux révisions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (filtered.isEmpty()) EmptyWorkspace("Aucun résultat", "Essayez un autre filtre.", Icons.Default.FilterAltOff, "Tout afficher") { filter = BatchFilter.ALL; search = "" }
                else LazyVerticalGrid(columns = GridCells.Adaptive(gridWidth), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered, key = { it.sampleId }) { sample ->
                        val editable = StudioWorkflow.canEdit(sample.acquisitionStatus, sample.syncStatus, sample.localImagePath != null)
                        SampleThumbnailCard(sample, selected = sample.sampleId in selected, onLongClick = {
                            if (editable && !busy) selected = if (sample.sampleId in selected) selected - sample.sampleId else selected + sample.sampleId
                        }, onClick = {
                            if (selected.isNotEmpty() && editable) selected = if (sample.sampleId in selected) selected - sample.sampleId else selected + sample.sampleId
                            else if (editable && !busy) viewModel.openSampleInEditor(sample.sampleId)
                            else viewModel.reportError(sample.auditReason ?: "Ce média est indisponible ou a déjà été publié. Consultez l’historique ou relancez son téléchargement.")
                        })
                    }
                }
            }
        }
    }
    if (confirmInference) AlertDialog(onDismissRequest = { confirmInference = false }, title = { Text("Préannoter ?") },
        text = { Text("Le modèle traitera une image à la fois, parmi les cas à traiter. Vos corrections et les cas validés, différés ou rejetés seront conservés. Aucune proposition ne sera validée automatiquement. Gardez l’application ouverte.") },
        confirmButton = { Button(onClick = { confirmInference = false; viewModel.preannotateActiveBatch() }) { Text("Lancer") } }, dismissButton = { TextButton(onClick = { confirmInference = false }) { Text("Annuler") } })
    if (rejectErrors) AlertDialog(onDismissRequest = { rejectErrors = false }, title = { Text("Rejeter les échecs ?") },
        text = { Text("Les images non récupérées seront exclues du corpus, avec leur motif d’échec conservé. Cette action ne corrige pas les fichiers et ne valide aucune image.") },
        confirmButton = { Button(onClick = { rejectErrors = false; viewModel.rejectFailedAcquisitions() }) { Text("Exclure ces cas") } }, dismissButton = { TextButton(onClick = { rejectErrors = false }) { Text("Annuler") } })
    if (actionDialog != null) AlertDialog(onDismissRequest = { actionDialog = null }, title = { Text(if (actionDialog == "tag") "Ajouter un tag à ${selected.size} cas" else "Différer ${selected.size} cas") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("L’action ne touche que les cas sélectionnés et modifiables. Aucune image ne sera validée automatiquement.")
            if (actionDialog == "tag") OutlinedTextField(tag, { tag = it }, label = { Text("Tag à ajouter") }, singleLine = true)
        }
    }, confirmButton = { Button(enabled = actionDialog != "tag" || tag.isNotBlank(), onClick = {
        viewModel.bulkAction(selected, actionDialog ?: "defer", tag); actionDialog = null; selected = emptySet(); tag = ""
    }) { Text("Appliquer") } }, dismissButton = { TextButton(onClick = { actionDialog = null }) { Text("Annuler") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SampleThumbnailCard(sample: SampleEntity, onClick: () -> Unit, selected: Boolean = false, onLongClick: () -> Unit = {}) {
    val published = sample.syncStatus in setOf("PURGED", "VERIFIED", "PUBLISHED")
    val label = when {
        published -> "Copie vérifiée"
        sample.acquisitionStatus.startsWith("ERROR") -> "À récupérer"
        else -> when(sample.annotationStatus) { "VALIDATED" -> "Validé"; "REJECTED" -> "Rejeté"; "DEFERRED" -> "À revoir"; "PROPOSALS_AVAILABLE" -> "Suggestions"; "IN_PROGRESS" -> "En cours"; else -> "À traiter" }
    }
    val icon = when(label) { "Copie vérifiée" -> Icons.Default.CloudDone; "Validé" -> Icons.Default.CheckCircleOutline; "À revoir" -> Icons.Default.Schedule; "À récupérer" -> Icons.Default.ErrorOutline; "Rejeté" -> Icons.Default.Block; else -> Icons.Default.Edit }
    val outline by androidx.compose.animation.animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "sample selection")
    Column(Modifier.clip(RoundedCornerShape(4.dp)).border(if(selected) 2.dp else 1.dp, outline, RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = onClick, onLongClickLabel = "Sélectionner cette image", onLongClick = onLongClick)
        .semantics(mergeDescendants = true) { contentDescription = "${sample.assetId}, $label"; this.selected = selected }
        .testTag("sample_${sample.sampleId}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            if (sample.localImagePath != null) AsyncImage(model = File(sample.localImagePath), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Icon(if (published) Icons.Default.CloudDone else Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            if (selected) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Check, "Sélectionné", Modifier.padding(5.dp).size(18.dp)) }
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
