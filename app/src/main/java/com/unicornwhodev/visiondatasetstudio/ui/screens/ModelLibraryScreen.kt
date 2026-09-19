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
import com.unicornwhodev.visiondatasetstudio.ui.components.StatusPill
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
        TopAppBar(windowInsets=WindowInsets(0),title={Column{Text("Bibliothèque de modèles");Text("Préannotation locale, sans validation automatique",style=MaterialTheme.typography.labelMedium)}},actions={
            IconButton(onClick=vm::refreshCommunityModelCatalog,enabled=!busy){Icon(Icons.Default.Refresh,"Actualiser le catalogue")}
        })
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=tab==0,onClick={tab=0},label={Text("Disponibles")},leadingIcon={Icon(Icons.Default.CloudDownload,null)})
                FilterChip(selected=tab==1,onClick={tab=1},label={Text("Installés")},leadingIcon={Icon(Icons.Default.Inventory2,null)})
                FilterChip(selected=tab==2,onClick={tab=2},label={Text("Importer")},leadingIcon={Icon(Icons.Default.AddCircleOutline,null)})
            }
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(tab==0) {
                    item {
                        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                Icon(Icons.Default.AutoAwesome,null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) { Text("Catalogue LiteRT pour Android",style=MaterialTheme.typography.titleMedium);Text("Les poids restent téléchargés à la demande depuis Hugging Face. Une conversion non publiée n’est pas affichée comme disponible.",style=MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    if(remote.isEmpty()) item { StudioSection("Catalogue non chargé","Actualisez pour lire ${com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog.repoId}.",Icons.Default.CloudOff){Button(onClick=vm::refreshCommunityModelCatalog,enabled=!busy){Text("Actualiser")}} }
                    items(remote.size,key={remote[it].entry.id}) { index ->
                        val item=remote[index]
                        OutlinedCard(shape=MaterialTheme.shapes.large) {
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer){Icon(Icons.Default.Memory,null,Modifier.padding(12.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)}
                                    Column(Modifier.weight(1f)) { Text(item.entry.title,style=MaterialTheme.typography.titleMedium);Text(item.entry.purpose,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                    StatusPill(if(item.available)"Disponible" else "En attente",if(item.available)Icons.Default.CheckCircle else Icons.Default.Schedule,attention=!item.installableNow)
                                }
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    SuggestionChip(onClick={},label={Text(item.entry.accent)});SuggestionChip(onClick={},label={Text(item.entry.upstreamLicense)});SuggestionChip(onClick={},label={Text("${item.entry.expectedFiles.size} fichier(s)")})
                                }
                                Text(item.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(item.installableNow) Button(onClick={vm.downloadCommunityModel(item.entry.id)},enabled=!busy){Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Télécharger dans l’app")}
                            }
                        }
                    }
                } else if(tab==1) {
                    if(local.isEmpty()) item { StudioSection("Aucun modèle local","Téléchargez une conversion disponible ou importez vos propres poids LiteRT.",Icons.Default.Inventory2){} }
                    items(local.size,key={local[it].id}) { index ->
                        val profile=local[index];val active=project?.modelPath==profile.modelPath && profile.modelPath.isNotBlank()
                        OutlinedCard(shape=MaterialTheme.shapes.large) {
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(profile.name,style=MaterialTheme.typography.titleMedium);Text(profile.tensorReport.lineSequence().firstOrNull().orEmpty(),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(active)StatusPill("Actif",Icons.Default.CheckCircle) }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={vm.selectModelProfile(profile.id)},enabled=!busy&&!active){Text(if(active)"Sélectionné" else "Utiliser")};OutlinedButton(onClick={deleteId=profile.id},enabled=!busy){Icon(Icons.Default.DeleteOutline,null);Spacer(Modifier.width(6.dp));Text("Supprimer")} }
                            }
                        }
                    }
                    item { OutlinedButton(onClick=vm::detachModel,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Détacher le modèle du projet actif")} }
                } else {
                    item { StudioSection("Importer vos poids","Aucun modèle privé n’est embarqué. Les poids restent dans le stockage privé de l’application.",Icons.Default.UploadFile) {
                        Button(onClick={weights.launch(arrayOf("application/octet-stream","*/*"))},enabled=!busy){Text("Choisir un .tflite")}
                        OutlinedTextField(url,{url=it},label={Text("URL HTTPS directe")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        OutlinedButton(onClick={vm.importModelUrl(url)},enabled=!busy&&url.startsWith("https://")){Text("Télécharger cette URL")}
                        TextButton(onClick={vm.navigateTo(Screen.Controls)},enabled=!busy){Text("Contrat, prétraitement et options avancées")}
                    } }
                }
            }
        }
    }
    deleteId?.let{id->AlertDialog(onDismissRequest={deleteId=null},title={Text("Supprimer ce profil ?")},text={Text("Les annotations et les correcteurs ne sont pas supprimés. Les poids sont effacés uniquement s’ils ne sont plus référencés.")},confirmButton={TextButton(onClick={deleteId=null;vm.removeModelProfile(id)}){Text("Supprimer")}},dismissButton={TextButton(onClick={deleteId=null}){Text("Annuler")}})}
}
