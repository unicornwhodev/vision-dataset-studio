package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioHomeScreen(viewModel: MainViewModel) {
    val project by viewModel.projectFlow.collectAsState()
    val samples by viewModel.batchSamples.collectAsState()
    val batch by viewModel.activeBatchNumber.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val policy = project?.let(ProjectSettings::read)
    val configured = !project?.hfSourceRepo.isNullOrBlank() || (policy?.sourceMode == "LOCAL_INDEX" && policy.sourceIndexReady)
    val reviewed = samples.count { it.annotationStatus in setOf("VALIDATED", "REJECTED") }
    val pending = samples.count { StudioWorkflow.isPending(it.annotationStatus) && it.localImagePath != null }
    var presetId by remember { mutableStateOf<String?>(null) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0), title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CenterFocusStrong, null, tint = MaterialTheme.colorScheme.primary)
                Text("Vision Dataset Studio", style = MaterialTheme.typography.titleMedium)
            }
        }, actions = { IconButton(onClick = { viewModel.navigateTo(Screen.Preferences) }) { Icon(Icons.Default.Tune, "Personnaliser mon atelier") } })
    }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("VOTRE ATELIER D’ANNOTATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(if (configured) "Reprendre, sans tout reconfigurer." else "Du corpus brut aux données utiles.", style = MaterialTheme.typography.headlineLarge)
                        Text(if (configured) project?.name ?: "Mon atelier" else "Un lot à la fois. L’image au centre, les outils dont vous avez besoin à portée de main.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (samples.isEmpty()) Icons.Default.FolderOpen else Icons.Default.Layers, null)
                                Column(Modifier.weight(1f)) {
                                    Text(if (samples.isEmpty()) "Préparer votre espace" else "Lot $batch · ${samples.size} images", style = MaterialTheme.typography.titleLarge)
                                    Text(project?.hfSourceRepo?.ifBlank { policy?.localSourceLabel?.ifBlank { "Source à choisir" } ?: "Source à choisir" } ?: "Chargement de l’atelier…", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (samples.isNotEmpty()) {
                                LinearProgressIndicator(progress = { reviewed.toFloat() / samples.size }, Modifier.fillMaxWidth())
                                Text("$reviewed / ${samples.size} décisions terminées · $pending images à annoter", style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(enabled = !busy && project != null, onClick = {
                                if (!configured) viewModel.navigateTo(Screen.Setup)
                                else if (samples.isEmpty()) viewModel.fetchAndPrepareBatch(batch)
                                else viewModel.resumeWork()
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Icon(if (samples.isEmpty()) Icons.Default.Add else Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (!configured) "Configurer mon atelier" else if (samples.isEmpty()) "Préparer un lot de ${policy?.batchSize ?: 100} cas" else if (pending > 0) "Reprendre l’annotation" else "Ouvrir le lot")
                            }
                            OutlinedButton(onClick = { viewModel.navigateTo(Screen.Controls) }, enabled = !busy, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Tune,null);Spacer(Modifier.width(8.dp));Text("Projets, sources, lots et modèles") }
                            if (configured) TextButton(onClick = { viewModel.navigateTo(Screen.Setup) }) { Text("Identité, classes et connexions") }
                        }
                    }
                }
                if (samples.isNotEmpty()) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("${samples.count { it.annotationStatus == "VALIDATED" }}", "Validés", Modifier.weight(1f))
                        MetricTile("${samples.count { it.annotationStatus == "DEFERRED" }}", "À revoir", Modifier.weight(1f))
                        MetricTile("${samples.count { it.acquisitionStatus.startsWith("ERROR") }}", "À récupérer", Modifier.weight(1f))
                    }
                }
                item {
                    StudioSection("Un atelier adapté à votre tâche", "Ces profils modifient les outils visibles, jamais les annotations existantes.", Icons.Default.Widgets) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StudioWorkflow.presets.forEach { preset ->
                                FilterChip(selected = StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION") == preset.tasks,
                                    enabled = !busy, onClick = { presetId = preset.id }, label = { Text(preset.title) })
                            }
                        }
                        TextButton(onClick = { viewModel.navigateTo(Screen.Preferences) }) { Text("Combiner les tâches et personnaliser l’affichage") }
                    }
                }
                if (prefs.showGuidance) item {
                    StudioSection("Votre parcours", icon = Icons.Default.Route) {
                        listOf("01" to ("Configurer" to "Source, classes, tâches et modèle facultatif"), "02" to ("Annoter" to "Préannotations explicites, correction et décisions humaines"), "03" to ("Exporter" to "Choix des formats et vérification avant purge")).forEach { (number, texts) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Column { Text(texts.first, style = MaterialTheme.typography.titleMedium); Text(texts.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                item { Text("Local d’abord · Plusieurs projets isolés · Préannotation facultative", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    presetId?.let { id -> StudioWorkflow.presets.firstOrNull { it.id == id }?.let { preset ->
        AlertDialog(onDismissRequest = { presetId = null }, title = { Text(preset.title) }, text = {
            Text("Activer ${preset.tasks.joinToString { it.title }} ? Les données des autres tâches seront conservées et pourront être réaffichées.")
        }, confirmButton = { Button(onClick = { viewModel.updateTasks(preset.tasks); presetId = null }) { Text("Appliquer le profil") } }, dismissButton = { TextButton(onClick = { presetId = null }) { Text("Annuler") } })
    } }
}
