package com.unicornwhodev.visiondatasetstudio.ui.screens

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import java.io.File

@Composable
fun StudioHomeScreen(viewModel: MainViewModel) {
    val project by viewModel.projectFlow.collectAsState()
    val samples by viewModel.batchSamples.collectAsState()
    val batch by viewModel.activeBatchNumber.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val models by viewModel.modelProfiles.collectAsState()
    val policy = project?.let(ProjectSettings::read)
    val configured = !project?.hfSourceRepo.isNullOrBlank() || (policy?.sourceMode == "LOCAL_INDEX" && policy.sourceIndexReady)
    val reviewed = samples.count { it.annotationStatus in setOf("VALIDATED", "REJECTED") }
    val completion by animateFloatAsState(if(samples.isEmpty()) 0f else reviewed.toFloat() / samples.size, label = "batch review")
    var previewId by rememberSaveable(project?.id, batch) { mutableStateOf<String?>(null) }
    val preview = samples.firstOrNull { it.sampleId == previewId }
        ?: samples.firstOrNull { StudioWorkflow.isPending(it.annotationStatus) && it.localImagePath != null }
        ?: samples.firstOrNull()
    val previewEditable = preview?.let { StudioWorkflow.canEdit(it.acquisitionStatus, it.syncStatus, it.localImagePath != null) } == true
    var presetMenu by remember { mutableStateOf(false) }
    var presetId by remember { mutableStateOf<String?>(null) }
    val currentPreset = StudioWorkflow.presets.firstOrNull { it.tasks == StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION") }
    val openPreview: () -> Unit = {
        preview?.takeIf { previewEditable && !busy }?.let { viewModel.openSampleInEditor(it.sampleId) }
    }
    val primary: @Composable () -> Unit = {
        StudioAction(if (!configured) tr("Importer", "Import") else if(samples.isEmpty()) tr("Préparer le lot", "Prepare batch") else if(previewEditable) tr("Annoter", "Annotate") else tr("Ouvrir le lot", "Open batch"),
            onClick = { if(!configured) viewModel.navigateTo(Screen.Setup) else if(samples.isEmpty()) viewModel.fetchAndPrepareBatch(batch)
                else if(previewEditable) openPreview() else viewModel.navigateTo(Screen.BatchGrid) },
            icon = if(samples.isEmpty()) Icons.Default.Add else Icons.Default.ArrowForward,
            primary = true, enabled = !busy && project != null, modifier = Modifier.testTag("home_primary"))
    }
    val details: @Composable () -> Unit = {
        WorkspaceLink("Source", if(policy?.sourceMode == "LOCAL_INDEX") tr("Dossier local", "Local folder") else project?.hfSourceRepo?.ifBlank { tr("À configurer", "Configure") } ?: tr("À configurer", "Configure"),
            Icons.Default.FolderOpen, !busy) { viewModel.navigateTo(Screen.Setup) }
        WorkspaceLink(tr("Modèle", "Model"), if(project?.modelPath != null || project?.modelConfigJson?.contains("local_http") == true) tr("Profil du projet", "Project profile")
            else if(models.isEmpty()) tr("Aucun modèle actif", "No active model") else tr("${models.size} disponible(s)", "${models.size} available"), Icons.Default.Memory, !busy) { viewModel.navigateTo(Screen.Models) }
        Box {
            WorkspaceLink(stringResource(R.string.prefs_tools), currentPreset?.title ?: tr("Personnalisés", "Custom"), Icons.Default.CropFree, !busy) { presetMenu = true }
            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                StudioWorkflow.presets.forEach { preset -> DropdownMenuItem(text = { Text(preset.title) }, onClick = { presetMenu = false; presetId = preset.id }) }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.editor_customize_tools)) }, onClick = { presetMenu = false; viewModel.navigateTo(Screen.Preferences) })
            }
        }
    }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        StudioTopBar(project?.name ?: tr("Atelier", "Studio"), tr("Cadryl  /  Lot ${batch.toString().padStart(2, '0')}", "Cadryl  /  Batch ${batch.toString().padStart(2, '0')}"), actions = {
            IconButton(onClick = { viewModel.navigateTo(Screen.Workflow) }, enabled = !busy) { Icon(Icons.Default.AccountTree, tr("Workflows et agent", "Workflows and agent"), Modifier.size(19.dp)) }
            IconButton(onClick = { viewModel.navigateTo(Screen.Controls) }, enabled = !busy, modifier = Modifier.testTag("controls_shortcut")) {
                Icon(Icons.Default.FolderOpen, tr("Gérer les projets", "Manage projects"), Modifier.size(19.dp))
            }
            IconButton(onClick = { viewModel.navigateTo(Screen.Preferences) }) { Icon(Icons.Default.Tune, tr("Réglages", "Settings"), Modifier.size(19.dp)) }
        })
    }) { inset ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inset)) {
            if (maxWidth >= 760.dp) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Espace d’annotation", "Annotation workspace"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        primary()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            WorkspacePreview(preview, Modifier.weight(1f).fillMaxWidth(), previewEditable && !busy, openPreview)
                            PreviewCaption(preview)
                        }
                        Column(Modifier.width(236.dp).fillMaxHeight()) {
                            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("FILE DE TRAVAIL", "WORK QUEUE"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${samples.size}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                            LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                            Text(tr("$reviewed / ${samples.size} traitées", "$reviewed / ${samples.size} processed"), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if(samples.isEmpty()) item { Text(tr("Aucune image importée", "No images imported"), Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                itemsIndexed(samples, key = { _, s -> s.sampleId }) { index, sample ->
                                    QueueRow(sample, index, sample.sampleId == preview?.sampleId) { previewId = sample.sampleId }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                            details()
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Lot ${batch.toString().padStart(2, '0')}", "Batch ${batch.toString().padStart(2, '0')}"), style = MaterialTheme.typography.titleSmall)
                                Text(tr("$reviewed / ${samples.size} traitées", "$reviewed / ${samples.size} processed"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            primary()
                        }
                        Spacer(Modifier.height(8.dp))
                        WorkspacePreview(preview, Modifier.fillMaxWidth().aspectRatio(4f / 3f), previewEditable && !busy, openPreview)
                        PreviewCaption(preview)
                        LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Images du lot", "Batch images"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { viewModel.navigateTo(Screen.BatchGrid) }) { Text(tr("Tout voir", "View all"), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    itemsIndexed(samples.take(3), key = { _, s -> s.sampleId }) { index, sample ->
                        QueueRow(sample, index, sample.sampleId == preview?.sampleId) { previewId = sample.sampleId }
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        details()
                    }
                }
            }
        }
    }
    presetId?.let { id -> StudioWorkflow.presets.firstOrNull { it.id == id }?.let { preset ->
        AlertDialog(onDismissRequest = { presetId = null }, title = { Text(preset.title) }, text = {
            Text(tr("Activer ${preset.tasks.joinToString { it.title }} ? Vos annotations sont conservées.", "Enable ${preset.tasks.joinToString { it.title }}? Your annotations are preserved."))
        }, confirmButton = { Button(onClick = { viewModel.updateTasks(preset.tasks); presetId = null }) { Text(stringResource(R.string.common_apply)) } },
            dismissButton = { TextButton(onClick = { presetId = null }) { Text(stringResource(R.string.common_cancel)) } })
    } }
}

@Composable
private fun WorkspacePreview(sample: SampleEntity?, modifier: Modifier, editable: Boolean, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), RoundedCornerShape(5.dp))
        .clickable(enabled = editable, role = Role.Button, onClickLabel = tr("Annoter cette image", "Annotate this image"), onClick = onClick), contentAlignment = Alignment.Center) {
        if(sample?.localImagePath != null) AsyncImage(model = File(sample.localImagePath), contentDescription = tr("Aperçu ${sample.assetId}", "Preview ${sample.assetId}"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CropFree, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(sample == null) tr("Votre espace de travail", "Your workspace") else tr("Aperçu indisponible", "Preview unavailable"), style = MaterialTheme.typography.titleSmall)
            Text(if(sample == null) tr("Importez un dossier ou un dataset.", "Import a folder or dataset.") else tr("Ouvrez le lot pour vérifier l’acquisition.", "Open the batch to check acquisition."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreviewCaption(sample: SampleEntity?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(sample?.assetId ?: tr("Aucune image sélectionnée", "No image selected"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(sample?.imageWidth != null && sample.imageHeight != null) Text("${sample.imageWidth} × ${sample.imageHeight}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QueueRow(sample: SampleEntity, index: Int, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if(selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background, label = "preview selection")
    val status = when(sample.annotationStatus) { "VALIDATED" -> tr("Validée", "Approved"); "REJECTED" -> tr("Rejetée", "Rejected"); "IN_PROGRESS" -> tr("En cours", "In progress"); "DEFERRED" -> tr("À revoir", "To review"); "PROPOSALS_AVAILABLE" -> "Suggestions"; else -> tr("À traiter", "To process") }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(background).clickable(role = Role.Tab, onClick = onClick)
        .semantics { this.selected = selected }.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((index+1).toString().padStart(2,'0'), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(44.dp, 36.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if(sample.localImagePath != null) AsyncImage(File(sample.localImagePath), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Image, null, Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(sample.assetId, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.labelSmall, color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkspaceLink(title: String, detail: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
