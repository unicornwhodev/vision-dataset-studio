package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.unicornwhodev.visiondatasetstudio.ui.components.StudioSection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelLibraryScreen(vm: MainViewModel) {
    val remote by vm.communityModels.collectAsState()
    val local by vm.modelProfiles.collectAsState()
    val project by vm.projectFlow.collectAsState()
    val busy by vm.isBusy.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val weights=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importModel)}
    Scaffold(contentWindowInsets=WindowInsets(0),topBar={
        StudioTopBar("Modèles", "Bibliothèque locale", actions={
            IconButton(onClick=vm::refreshCommunityModelCatalog,enabled=!busy){Icon(Icons.Default.Refresh,"Actualiser le catalogue")}
        })
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            StudioTabs(listOf("Explorer", "Installés", "Importer"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(tab==0) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Catalogue", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text("${remote.count { it.installableNow }} installables", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if(remote.isEmpty()) item { EmptyWorkspace("Catalogue non chargé", "Actualisez pour voir les modèles disponibles.", Icons.Default.Memory, if (!busy) "Explorer le catalogue" else null, vm::refreshCommunityModelCatalog) }
                    items(remote.size,key={remote[it].entry.id}) { index ->
                        val item=remote[index]
                        Card(shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Memory,null,Modifier.padding(top=2.dp).size(20.dp),tint=MaterialTheme.colorScheme.secondary)
                                    Column(Modifier.weight(1f)) { Text(item.entry.title,style=MaterialTheme.typography.titleMedium);Text(item.entry.purpose,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Text(if(item.installableNow) "Compatible · ${item.entry.upstreamLicense}" else "Non installable · ${item.entry.upstreamLicense}", style=MaterialTheme.typography.labelMedium, color=if(item.installableNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                                    var showInfo by remember { mutableStateOf(false) }
                                    TextButton(onClick={showInfo=true}) { Text("Détails") }
                                    if(item.installableNow) FilledTonalButton(onClick={vm.downloadCommunityModel(item.entry.id)},enabled=!busy,shape=MaterialTheme.shapes.small){Icon(Icons.Default.Download,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Installer")}
                                    if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text(item.entry.title)},text={Text("${item.entry.accent} · ${item.entry.expectedFiles.size} fichier(s)\n\n${item.note}")},confirmButton={TextButton(onClick={showInfo=false}){Text("Fermer")}})
                                }
                            }
                        }
                    }
                } else if(tab==1) {
                    if(local.isEmpty()) item { EmptyWorkspace("Aucun modèle installé", "Importez un fichier .tflite compatible.", Icons.Default.Memory, "Importer", { tab = 2 }) }
                    items(local.size,key={local[it].id}) { index ->
                        val profile=local[index];val active=project?.modelPath==profile.modelPath && profile.modelPath.isNotBlank()
                        Card(shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(profile.name,style=MaterialTheme.typography.titleMedium);Text(profile.tensorReport.lineSequence().firstOrNull().orEmpty(),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(active)StatusPill("Actif",Icons.Default.CheckCircle) }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={vm.selectModelProfile(profile.id)},enabled=!busy&&!active){Text(if(active)"Sélectionné" else "Utiliser")};OutlinedButton(onClick={deleteId=profile.id},enabled=!busy){Icon(Icons.Default.DeleteOutline,null);Spacer(Modifier.width(6.dp));Text("Supprimer")} }
                            }
                        }
                    }
                    if (!project?.modelPath.isNullOrBlank() || project?.modelConfigJson != null) item { OutlinedButton(onClick=vm::detachModel,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Détacher du projet")} }
                } else {
                    item { StudioSection("Importer un modèle","Aucun modèle privé n’est embarqué. Les poids restent dans le stockage privé de l’application.",Icons.Default.UploadFile) {
                        Text("Fichier LiteRT", style=MaterialTheme.typography.titleLarge)
                        Text("Sélectionnez les poids sur cet appareil.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick={weights.launch(arrayOf("application/octet-stream","*/*"))},enabled=!busy,shape=MaterialTheme.shapes.small,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.UploadFile,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Choisir un .tflite")}
                    } }
                    item { StudioDisclosure("Depuis une URL", Icons.Default.Link) {
                        OutlinedTextField(url,{url=it},label={Text("URL HTTPS directe")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        OutlinedButton(onClick={vm.importModelUrl(url)},enabled=!busy&&url.startsWith("https://")){Text("Importer l’URL")}
                        TextButton(onClick={vm.navigateTo(Screen.Controls)},enabled=!busy){Text("Options avancées")}
                    } }
                }
            }
        }
    }
    deleteId?.let{id->AlertDialog(onDismissRequest={deleteId=null},title={Text("Supprimer ce profil ?")},text={Text("Les annotations et les correcteurs ne sont pas supprimés. Les poids sont effacés uniquement s’ils ne sont plus référencés.")},confirmButton={TextButton(onClick={deleteId=null;vm.removeModelProfile(id)}){Text("Supprimer")}},dismissButton={TextButton(onClick={deleteId=null}){Text("Annuler")}})}
}
