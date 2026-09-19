package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
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
    val pending = samples.count { StudioWorkflow.isPending(it.annotationStatus) && it.localImagePath != null }
    val previews = remember(samples) { samples.filter { it.localImagePath != null }.take(3) }
    val completion by animateFloatAsState(if (samples.isEmpty()) 0f else reviewed.toFloat() / samples.size, tween(350), label = "batch progress")
    var presetId by remember { mutableStateOf<String?>(null) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        StudioTopBar("Vision Studio", actions = {
            IconButton(onClick = { viewModel.navigateTo(Screen.Preferences) }) { Icon(Icons.Default.Tune, "Réglages", Modifier.size(22.dp)) }
        })
    }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 960.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = !busy, role = Role.Button) { viewModel.navigateTo(Screen.Controls) }
                        .testTag("controls_shortcut").heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PROJET ACTIF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(project?.name ?: "Chargement…", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.UnfoldMore, "Gérer les projets", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (samples.isEmpty()) "Lot de travail" else "Lot ${batch.toString().padStart(2, '0')}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(if (samples.isEmpty()) "Aucune image" else "${samples.size} images", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                            if (previews.isNotEmpty()) {
                                BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
                                    val previewHeight = (maxWidth / previews.size / .85f).coerceAtMost(194.dp)
                                    Row(Modifier.fillMaxWidth().height(previewHeight), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        previews.forEach { sample ->
                                            AsyncImage(model = File(sample.localImagePath!!), contentDescription = "Aperçu ${sample.assetId}", contentScale = ContentScale.Crop,
                                                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)))
                                        }
                                    }
                                }
                            } else if (samples.isEmpty()) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(if (configured) "Source prête" else "Connectez vos images", style = MaterialTheme.typography.titleMedium)
                                    Text(if (configured) "Préparez jusqu’à ${policy?.batchSize ?: 100} images." else "Dossier local ou dataset Hugging Face",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (samples.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("$reviewed / ${samples.size} traitées", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(completion * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                                    }
                                    LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                }
                                Button(enabled = !busy && project != null, onClick = {
                                    if (!configured) viewModel.navigateTo(Screen.Setup)
                                    else if (samples.isEmpty()) viewModel.fetchAndPrepareBatch(batch)
                                    else viewModel.resumeWork()
                                }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home_primary")) {
                                    Icon(if (!configured) Icons.Default.Add else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (!configured) "Configurer la source" else if (samples.isEmpty()) "Préparer le lot" else if (pending > 0) "Annoter · $pending restantes" else "Ouvrir le lot")
                                }
                            }
                        }
                    }
                }
                item {
                    Column {
                        WorkspaceRow("Source", if (policy?.sourceMode == "LOCAL_INDEX") "Dossier ou manifeste local" else project?.hfSourceRepo?.ifBlank { "Non configurée" } ?: "Non configurée",
                            Icons.Default.Storage, !busy) { viewModel.navigateTo(Screen.Setup) }
                        WorkspaceRow("Assistance", if (models.isEmpty()) "Annotation manuelle" else "${models.size} modèle(s) installé(s)",
                            Icons.Default.Memory, !busy) { viewModel.navigateTo(Screen.Models) }
                    }
                }
                item {
                    StudioDisclosure("Outils d’annotation", Icons.Default.CropFree) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StudioWorkflow.presets.forEach { preset -> FilterChip(
                                selected = StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION") == preset.tasks,
                                enabled = !busy, onClick = { presetId = preset.id }, label = { Text(preset.title) }) }
                        }
                        TextButton(onClick = { viewModel.navigateTo(Screen.Preferences) }) { Text("Personnaliser les outils") }
                    }
                }
            }
        }
    }
    presetId?.let { id -> StudioWorkflow.presets.firstOrNull { it.id == id }?.let { preset ->
        AlertDialog(onDismissRequest = { presetId = null }, title = { Text(preset.title) }, text = {
            Text("Activer ${preset.tasks.joinToString { it.title }} ? Vos annotations sont conservées.")
        }, confirmButton = { Button(onClick = { viewModel.updateTasks(preset.tasks); presetId = null }) { Text("Appliquer") } },
            dismissButton = { TextButton(onClick = { presetId = null }) { Text("Annuler") } })
    } }
}

@Composable
private fun WorkspaceRow(title: String, detail: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
    }
}
