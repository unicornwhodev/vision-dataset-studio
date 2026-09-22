package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

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
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelCapability
import com.unicornwhodev.visiondatasetstudio.domain.inference.QualificationStatus

@Composable
fun ModelLibraryScreen(vm: MainViewModel) {
    val language=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
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
        StudioTopBar(stringResource(R.string.screen_models), stringResource(R.string.models_summary,local.size), actions = {
            IconButton(onClick = { vm.navigateTo(Screen.Training) }, enabled = !busy) { Icon(Icons.Default.ModelTraining, "Apprentissage sur cet appareil", Modifier.size(19.dp)) }
            IconButton(onClick = { editSource = true }, enabled = !busy) { Icon(Icons.Default.Storage, "Dépôt du catalogue", Modifier.size(19.dp)) }
            IconButton(onClick = vm::refreshCommunityModelCatalog, enabled = !busy) { Icon(Icons.Default.Refresh, "Actualiser le catalogue", Modifier.size(19.dp)) }
        })
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            StudioTabs(listOf(stringResource(R.string.models_tab_explore), stringResource(R.string.models_tab_installed), stringResource(R.string.models_tab_import)), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (tab == 0) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.models_catalogue), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.models_downloadable,remote.count { it.installableNow }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if(remote.isEmpty()) item { EmptyWorkspace(stringResource(R.string.models_empty_title), stringResource(R.string.models_empty_body), Icons.Default.Memory, if (!busy) stringResource(R.string.models_explore) else null, vm::refreshCommunityModelCatalog) }
                        items(remote.size, key = { remote[it].entry.id }) { index ->
                            val item = remote[index]
                            var showInfo by remember { mutableStateOf(false) }
                            val state = stringResource(when {
                                !item.available -> R.string.model_state_upcoming
                                !item.installableNow -> R.string.model_state_unsupported
                                item.entry.adapterStatus == "contract" -> R.string.model_state_contract
                                item.entry.adapterStatus == "heatmap" -> R.string.model_state_heatmap
                                item.entry.adapterStatus == "embedding" -> R.string.model_state_embedding
                                item.entry.adapterStatus == "bundle" -> R.string.model_state_bundle
                                item.entry.adapterStatus == "rfdetr" -> R.string.model_state_detection
                                else -> R.string.model_state_inspection
                            })
                            val capabilityNames=mutableListOf<String>();for(capability in item.entry.capabilities.values)capabilityNames+=when(capability) {
                                ModelCapability.DETECTION->stringResource(R.string.cap_detection);ModelCapability.POINTING->stringResource(R.string.cap_pointing);ModelCapability.SEGMENTATION->stringResource(R.string.cap_segmentation)
                                ModelCapability.CLASSIFICATION->stringResource(R.string.cap_classification);ModelCapability.CAPTIONING->stringResource(R.string.cap_captioning);ModelCapability.EMBEDDING->stringResource(R.string.cap_embedding)
                                ModelCapability.SIMILARITY->stringResource(R.string.cap_similarity);ModelCapability.INTERACTIVE_SEGMENTATION->stringResource(R.string.cap_interactive_segmentation);ModelCapability.TRAINING->stringResource(R.string.cap_training)
                                ModelCapability.INSPECTION_ONLY->stringResource(R.string.cap_inspection)
                                ModelCapability.VQA->stringResource(R.string.cap_vqa)
                                ModelCapability.COUNTING->stringResource(R.string.cap_counting)
                                ModelCapability.GROUNDING->stringResource(R.string.cap_grounding)
                            };val capabilityText=capabilityNames.joinToString(" · ") + " · " + when(item.entry.capabilities.qualification) {
                                QualificationStatus.QUALIFIED->stringResource(R.string.qualification_qualified);QualificationStatus.PARTIALLY_QUALIFIED->stringResource(R.string.qualification_partial)
                                QualificationStatus.INFERENCE_ONLY->stringResource(R.string.qualification_inference);QualificationStatus.TRAINING_QUALIFIED->stringResource(R.string.qualification_training)
                                QualificationStatus.FAILED->stringResource(R.string.qualification_failed);QualificationStatus.UNTESTED->stringResource(R.string.qualification_untested)
                            }
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Memory, null, Modifier.size(18.dp), tint = if(item.installableNow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(item.entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if(language=="fr")item.entry.purpose else capabilityNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$state  ·  ${item.entry.upstreamLicense}", style = MaterialTheme.typography.labelSmall, color = if(item.installableNow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(capabilityText,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
                                    }
                                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, stringResource(R.string.models_details,item.entry.title), Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    if(item.installableNow) IconButton(onClick = { vm.downloadCommunityModel(item.entry.id) }, enabled = !busy) {
                                        Icon(Icons.Default.Download, stringResource(R.string.models_install,item.entry.title), Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                            }
                            if(showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text(item.entry.title) },
                                text = { Text((if(language=="fr")item.entry.purpose else capabilityNames.joinToString(" · "))+"\n\n$capabilityText\n"+stringResource(R.string.model_files,item.entry.expectedFiles.size)+"\n\n"+(if(language=="fr")item.note else stringResource(R.string.model_test_required))) },
                                confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.action_close)) } })
                        }
                    } else if(tab == 1) {
                        if(local.isEmpty()) item { EmptyWorkspace(stringResource(R.string.models_none_installed), stringResource(R.string.models_import_compatible), Icons.Default.Memory, stringResource(R.string.models_tab_import), { tab = 2 }) }
                        items(local.size, key = { local[it].id }) { index ->
                            val profile = local[index]
                            val active = project?.modelPath == profile.modelPath && profile.modelPath.isNotBlank()
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Memory, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                                        Text(profile.tensorReport.lineSequence().firstOrNull().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val config=remember(profile.configJson){runCatching{com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig::class.java).fromJson(profile.configJson)}.getOrNull()}
                                        config?.let { cfg -> val names=mutableListOf<String>();for(cap in com.unicornwhodev.visiondatasetstudio.domain.inference.ModelCapabilities.fromConfig(cfg).values)names+=when(cap){
                                            ModelCapability.DETECTION->stringResource(R.string.cap_detection);ModelCapability.POINTING->stringResource(R.string.cap_pointing);ModelCapability.SEGMENTATION->stringResource(R.string.cap_segmentation);ModelCapability.CLASSIFICATION->stringResource(R.string.cap_classification);ModelCapability.CAPTIONING->stringResource(R.string.cap_captioning);ModelCapability.VQA->stringResource(R.string.cap_vqa);ModelCapability.COUNTING->stringResource(R.string.cap_counting);ModelCapability.GROUNDING->stringResource(R.string.cap_grounding);ModelCapability.EMBEDDING->stringResource(R.string.cap_embedding);ModelCapability.SIMILARITY->stringResource(R.string.cap_similarity);ModelCapability.INTERACTIVE_SEGMENTATION->stringResource(R.string.cap_interactive_segmentation);ModelCapability.TRAINING->stringResource(R.string.cap_training);ModelCapability.INSPECTION_ONLY->stringResource(R.string.cap_inspection)};Text(names.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                        if(active) Text(stringResource(R.string.models_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if(!active) StudioAction(stringResource(R.string.models_use), { vm.selectModelProfile(profile.id) }, enabled = !busy)
                                    IconButton(onClick = { deleteId = profile.id }, enabled = !busy) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.models_delete,profile.name), Modifier.size(18.dp)) }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if(!project?.modelPath.isNullOrBlank() || project?.modelConfigJson != null) item {
                            TextButton(onClick = vm::detachModel, enabled = !busy) { Text(stringResource(R.string.models_detach)) }
                        }
                    } else {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column {
                                        Text(stringResource(R.string.models_litert_file), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.models_device_weights), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                StudioAction(stringResource(R.string.models_choose_file), { weights.launch(arrayOf("application/octet-stream", "*/*")) }, icon = Icons.Default.UploadFile, primary = true, enabled = !busy)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(16.dp))
                        }
                        item { StudioDisclosure(stringResource(R.string.models_from_url), Icons.Default.Link) {
                            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.models_url_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioAction(stringResource(R.string.models_import_url), { vm.importModelUrl(url) }, enabled = !busy && url.startsWith("https://"))
                        } }
                        item { TextButton(onClick = { vm.navigateTo(Screen.Controls) }, enabled = !busy) { Text(stringResource(R.string.models_advanced), style = MaterialTheme.typography.labelMedium) } }
                    }
                }
            }
        }
    }
    if (editSource) {
        var repo by remember { mutableStateOf(source.repository) }
        var revision by remember { mutableStateOf(source.revision) }
        var folder by remember { mutableStateOf(source.folder) }
        AlertDialog(onDismissRequest = { editSource = false }, title = { Text(stringResource(R.string.models_catalog_source)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(repo, { repo = it }, label = { Text(stringResource(R.string.models_repo)) }, singleLine = true)
                OutlinedTextField(revision, { revision = it }, label = { Text(stringResource(R.string.models_revision)) }, singleLine = true)
                OutlinedTextField(folder, { folder = it }, label = { Text(stringResource(R.string.models_folder)) }, singleLine = true)
                Text(stringResource(R.string.models_private_access), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { vm.setModelCatalog(repo, revision, folder); editSource = false }) { Text(stringResource(R.string.action_open)) } },
            dismissButton = { TextButton(onClick = { editSource = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.models_delete_title)) },
        text = { Text(stringResource(R.string.models_delete_body)) },
        confirmButton = { TextButton(onClick = { deleteId = null; vm.removeModelProfile(id) }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.action_cancel)) } }) }
}
