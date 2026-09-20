package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.Screen
import com.unicornwhodev.visiondatasetstudio.ui.components.*

@Composable
fun ModelLibraryScreen(vm: MainViewModel) {
    val source by vm.catalogSource.collectAsState()
    var editSource by remember { mutableStateOf(false) }
    val remote by vm.communityModels.collectAsState()
    val local by vm.modelProfiles.collectAsState()
    val project by vm.projectFlow.collectAsState()
    val busy by vm.isBusy.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val weights = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importModel) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        StudioTopBar("Modèles", "Bibliothèque  /  ${local.size} installé(s)", actions = {
            IconButton(onClick = { vm.navigateTo(Screen.Training) }, enabled = !busy) { Icon(Icons.Default.ModelTraining, "Apprentissage sur cet appareil", Modifier.size(19.dp)) }
            IconButton(onClick = { editSource = true }, enabled = !busy) { Icon(Icons.Default.Storage, "Dépôt du catalogue", Modifier.size(19.dp)) }
            IconButton(onClick = vm::refreshCommunityModelCatalog, enabled = !busy) { Icon(Icons.Default.Refresh, "Actualiser le catalogue", Modifier.size(19.dp)) }
        })
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            StudioTabs(listOf("Explorer", "Installés", "Importer"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (tab == 0) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("CATALOGUE", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${remote.count { it.installableNow }} téléchargeables", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if(remote.isEmpty()) item { EmptyWorkspace("Catalogue non chargé", "Actualisez pour voir les modèles disponibles.", Icons.Default.Memory, if (!busy) "Explorer le catalogue" else null, vm::refreshCommunityModelCatalog) }
                        items(remote.size, key = { remote[it].entry.id }) { index ->
                            val item = remote[index]
                            var showInfo by remember { mutableStateOf(false) }
                            val state = when {
                                !item.available -> "À venir"
                                !item.installableNow -> "Bundle non pris en charge"
                                item.entry.adapterStatus == "contract" -> "Contrat fourni"
                                item.entry.adapterStatus == "heatmap" -> "Contrat heatmap"
                                item.entry.adapterStatus == "embedding" -> "Représentations visuelles"
                                item.entry.adapterStatus == "bundle" -> "Pipeline local"
                                item.entry.adapterStatus == "rfdetr" -> "Détection"
                                else -> "Inspection"
                            }
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Memory, null, Modifier.size(18.dp), tint = if(item.installableNow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(item.entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.entry.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$state  ·  ${item.entry.upstreamLicense}", style = MaterialTheme.typography.labelSmall, color = if(item.installableNow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, "Détails : ${item.entry.title}", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    if(item.installableNow) IconButton(onClick = { vm.downloadCommunityModel(item.entry.id) }, enabled = !busy) {
                                        Icon(Icons.Default.Download, "Installer ${item.entry.title}", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                            }
                            if(showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text(item.entry.title) },
                                text = { Text("${item.entry.purpose}\n\n${item.entry.accent} · ${item.entry.expectedFiles.size} fichier(s)\n\n${item.note}") },
                                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Fermer") } })
                        }
                    } else if(tab == 1) {
                        if(local.isEmpty()) item { EmptyWorkspace("Aucun modèle installé", "Importez un fichier .tflite compatible.", Icons.Default.Memory, "Importer", { tab = 2 }) }
                        items(local.size, key = { local[it].id }) { index ->
                            val profile = local[index]
                            val active = project?.modelPath == profile.modelPath && profile.modelPath.isNotBlank()
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Memory, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                                        Text(profile.tensorReport.lineSequence().firstOrNull().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(active) Text("Actif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if(!active) StudioAction("Utiliser", { vm.selectModelProfile(profile.id) }, enabled = !busy)
                                    IconButton(onClick = { deleteId = profile.id }, enabled = !busy) { Icon(Icons.Default.DeleteOutline, "Supprimer ${profile.name}", Modifier.size(18.dp)) }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if(!project?.modelPath.isNullOrBlank() || project?.modelConfigJson != null) item {
                            TextButton(onClick = vm::detachModel, enabled = !busy) { Text("Détacher du projet") }
                        }
                    } else {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column {
                                        Text("Fichier LiteRT", style = MaterialTheme.typography.titleMedium)
                                        Text("Poids .tflite sur cet appareil", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                StudioAction("Choisir un .tflite", { weights.launch(arrayOf("application/octet-stream", "*/*")) }, icon = Icons.Default.UploadFile, primary = true, enabled = !busy)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(16.dp))
                        }
                        item { StudioDisclosure("Depuis une URL", Icons.Default.Link) {
                            OutlinedTextField(url, { url = it }, label = { Text("URL HTTPS directe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioAction("Importer l’URL", { vm.importModelUrl(url) }, enabled = !busy && url.startsWith("https://"))
                        } }
                        item { TextButton(onClick = { vm.navigateTo(Screen.Controls) }, enabled = !busy) { Text("Contrats et options avancées", style = MaterialTheme.typography.labelMedium) } }
                    }
                }
            }
        }
    }
    if (editSource) {
        var repo by remember { mutableStateOf(source.repository) }
        var revision by remember { mutableStateOf(source.revision) }
        var folder by remember { mutableStateOf(source.folder) }
        AlertDialog(onDismissRequest = { editSource = false }, title = { Text("Catalogue Hugging Face") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(repo, { repo = it }, label = { Text("Compte / dépôt") }, singleLine = true)
                OutlinedTextField(revision, { revision = it }, label = { Text("Révision") }, singleLine = true)
                OutlinedTextField(folder, { folder = it }, label = { Text("Dossier · vide pour la racine") }, singleLine = true)
                Text("L’accès privé utilise la connexion HF de l’application.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { vm.setModelCatalog(repo, revision, folder); editSource = false }) { Text("Ouvrir") } },
            dismissButton = { TextButton(onClick = { editSource = false }) { Text("Annuler") } })
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Supprimer ce profil ?") },
        text = { Text("Les annotations et les correcteurs ne sont pas supprimés. Les poids sont effacés uniquement s’ils ne sont plus référencés.") },
        confirmButton = { TextButton(onClick = { deleteId = null; vm.removeModelProfile(id) }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Annuler") } }) }
}
