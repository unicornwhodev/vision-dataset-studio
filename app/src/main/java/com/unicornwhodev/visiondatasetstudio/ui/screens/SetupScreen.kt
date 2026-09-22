package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val project by viewModel.projectFlow.collectAsState()
    val auth by viewModel.authStatus.collectAsState()
    val inspection by viewModel.sourceInspection.collectAsState()
    val dest by viewModel.destRepoStatus.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val p = project ?: return
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(p.name) }
    var source by rememberSaveable { mutableStateOf(p.hfSourceRepo) }
    var destination by rememberSaveable { mutableStateOf(p.hfDestRepo) }
    var config by rememberSaveable { mutableStateOf(p.sourceConfig) }
    var split by rememberSaveable { mutableStateOf(p.sourceSplit) }
    var imageColumn by rememberSaveable { mutableStateOf(p.imageColumn) }
    var classes by rememberSaveable { mutableStateOf(p.classesCsv) }
    var tasksCsv by rememberSaveable { mutableStateOf(p.activeTasksCsv) }
    val tasks = StudioWorkflow.parseTasks(tasksCsv)
    var budget by rememberSaveable { mutableStateOf(p.diskBudgetMb.toString()) }
    var prepare by rememberSaveable { mutableStateOf(batches.isEmpty()) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var modelUrl by rememberSaveable { mutableStateOf("") }
    var modelJson by rememberSaveable { mutableStateOf(p.modelConfigJson ?: "") }
    var showToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") } // Never place a credential in saved instance state.
    var createRepo by remember { mutableStateOf(false) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) viewModel.importSourceFolder(uri) }
    val chooseModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importModel) }
    LaunchedEffect(p.settingsJson, p.hfSourceRepo) {
        val savedPolicy = com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(p)
        if (savedPolicy.sourceMode == "LOCAL_INDEX" && savedPolicy.sourceIndexReady) source = p.hfSourceRepo
    }
    val sourceOk = (com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(p).sourceMode == "LOCAL_INDEX" && source.isBlank()) || StudioWorkflow.normalizeRepo(source) != null
    val destOk = destination.isBlank() || StudioWorkflow.normalizeRepo(destination, true) != null
    Scaffold(contentWindowInsets = WindowInsets(0), modifier = Modifier.imePadding(), topBar = {
        StudioTopBar(stringResource(R.string.screen_setup), stringResource(R.string.subtitle_configuration), onBack = viewModel::back)
    }, bottomBar = {
        Surface(shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_previous)) }
                Button(onClick = {
                    if (step < 2) step++ else viewModel.saveSetup(name, source, destination, config, split, imageColumn, classes,
                        budget.toLongOrNull() ?: 500L, tasks, prepare)
                }, enabled = !busy && when(step) { 0 -> sourceOk; 1 -> classes.isNotBlank(); else -> sourceOk && destOk && (budget.toLongOrNull() ?: 0L) in 128L..65536L },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("setup_next")) {
                    Text(if (step < 2) "Continuer" else if (prepare) stringResource(R.string.setup_start) else "Enregistrer")
                }
            }
        }
    }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                StudioTabs(listOf("Source", stringResource(R.string.prefs_tools), "Sortie"), step, { if (!busy) step = it })
                when(step) {
                    0 -> {
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.setup_project_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                        StudioSection(stringResource(R.string.setup_local_images), stringResource(R.string.setup_index_help), Icons.Default.FolderOpen) {
                            val localPolicy = com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(p)
                            if (localPolicy.sourceMode == "LOCAL_INDEX" && localPolicy.sourceIndexReady) Text(localPolicy.localSourceLabel.ifBlank { stringResource(R.string.setup_folder_indexed) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { chooseFolder.launch(null) }, enabled = !busy && batches.isEmpty(), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setup_choose_folder))
                            }
                        }
                        StudioDisclosure(stringResource(R.string.setup_hf_dataset), Icons.Default.CloudDownload, initiallyExpanded = p.hfSourceRepo.isNotBlank()) {
                            OutlinedTextField(source, { source = it }, label = { Text(stringResource(R.string.setup_dataset_link)) }, placeholder = { Text("organisation/dataset") },
                                isError = source.isNotBlank() && !sourceOk, supportingText = { Text(stringResource(R.string.setup_dataset_url_help)) },
                                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("source_repo_input"))
                            FilledTonalButton(onClick = { viewModel.inspectSourceDataset(source, config, split) }, enabled = sourceOk && source.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setup_inspect_source))
                            }
                            if (inspection.isInspected && inspection.repoId == StudioWorkflow.normalizeRepo(source)) {
                                StatusPill(stringResource(R.string.setup_rows_verified,inspection.previewRows.size), Icons.Default.CheckCircleOutline)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    inspection.splits.forEach { item ->
                                        FilterChip(selected = config == item.config && split == item.split, onClick = {
                                            config = item.config; split = item.split
                                            viewModel.inspectSourceDataset(source, item.config, item.split)
                                        }, label = { Text("${item.config} / ${item.split}") })
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    inspection.availableColumns.forEach { column -> FilterChip(selected = imageColumn == column, onClick = { imageColumn = column }, label = { Text(column) }) }
                                }
                                Text(stringResource(R.string.setup_verify_image_column,imageColumn), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) stringResource(R.string.setup_collapse) else stringResource(R.string.setup_advanced)) }
                            if (advanced) {
                                OutlinedTextField(config, { config = it }, label = { Text(stringResource(R.string.setup_hf_config)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(split, { split = it }, label = { Text(stringResource(R.string.setup_source_split)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(imageColumn, { imageColumn = it }, label = { Text(stringResource(R.string.setup_image_column)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            if (batches.isNotEmpty()) StudioDetails(stringResource(R.string.setup_provenance_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StudioDisclosure(stringResource(R.string.setup_hf_login), Icons.Default.Key) {
                            Text(if (auth?.isValid == true) stringResource(R.string.setup_connected,auth?.username.orEmpty()) else stringResource(R.string.setup_public_read), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.setup_hf_token)) }, placeholder = { Text("hf_…") }, singleLine = true,
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("token_input"),
                                trailingIcon = { IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Afficher ou masquer le jeton") } })
                            Button(onClick = { viewModel.saveToken(token); token = "" }, enabled = token.isNotBlank() && !busy) { Text(stringResource(R.string.setup_connect)) }
                            if (auth?.error != null) Text(auth?.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.setup_token_security), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    1 -> {
                        StudioSection(stringResource(R.string.setup_tasks), stringResource(R.string.setup_tasks_help), Icons.Default.Widgets) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioWorkflow.presets.forEach { preset -> FilterChip(selected = tasks == preset.tasks, onClick = { tasksCsv = StudioWorkflow.tasksCsv(preset.tasks) }, label = { Text(preset.title) }) }
                            }
                            HorizontalDivider()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioTask.entries.forEach { task -> FilterChip(selected = task in tasks, onClick = { tasksCsv = StudioWorkflow.tasksCsv(StudioWorkflow.toggleTask(tasks, task)) }, label = { Text(task.title) }) }
                            }
                            OutlinedTextField(classes, { classes = it }, label = { Text(stringResource(R.string.setup_classes)) }, supportingText = { Text(stringResource(R.string.setup_classes_help)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        }
                        StudioSection(stringResource(R.string.setup_ai_assistant), stringResource(R.string.setup_proposals_help), Icons.Default.AutoAwesome) {
                            StatusPill(if (p.modelPath.isNullOrBlank()) stringResource(R.string.setup_no_model) else stringResource(R.string.setup_weights_imported), if (p.modelPath.isNullOrBlank()) Icons.Default.Info else Icons.Default.Memory)
                            OutlinedButton(onClick = { chooseModel.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setup_choose_tflite)) }
                            OutlinedTextField(modelUrl, { modelUrl = it }, label = { Text(stringResource(R.string.setup_weights_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { viewModel.importModelUrl(modelUrl) }, enabled = modelUrl.startsWith("https://") && !busy) { Text(stringResource(R.string.setup_download_model)) }
                            StudioDetails(stringResource(R.string.setup_adapters_help), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(modelJson, { modelJson = it }, label = { Text(stringResource(R.string.setup_optional_contract)) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { viewModel.saveModelConfig(modelJson) }, enabled = !busy) { Text(stringResource(R.string.setup_save_contract)) }
                        }
                    }
                    2 -> {
                        StudioSection(stringResource(R.string.setup_dataset_destination), stringResource(R.string.setup_local_export_help), Icons.Default.IosShare) {
                            OutlinedTextField(destination, { destination = it }, label = { Text(stringResource(R.string.setup_optional_destination)) }, placeholder = { Text("utilisateur/corpus-prepare") }, singleLine = true,
                                isError = destination.isNotBlank() && !destOk, modifier = Modifier.fillMaxWidth())
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.checkDestinationRepo(destination) }, enabled = destination.isNotBlank() && destOk && !busy) { Text(stringResource(R.string.setup_verify_access)) }
                                OutlinedButton(onClick = { createRepo = true }, enabled = destination.isNotBlank() && destOk && auth?.isValid == true && !busy) { Text(stringResource(R.string.setup_create_private)) }
                            }
                            if (dest != null) Text(if (dest?.exists == true) stringResource(R.string.setup_repo_accessible) else dest?.message ?: stringResource(R.string.setup_access_unconfirmed), style = MaterialTheme.typography.bodySmall)
                            StudioDetails(stringResource(R.string.setup_export_later), style = MaterialTheme.typography.bodyMedium)
                        }
                        StudioSection(stringResource(R.string.setup_storage_pace), icon = Icons.Default.Storage) {
                            OutlinedTextField(budget, { budget = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.setup_local_budget)) }, supportingText = { Text(stringResource(R.string.setup_budget_help)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Checkbox(prepare, { prepare = it })
                                Text(stringResource(R.string.setup_prepare_next), style = MaterialTheme.typography.bodyMedium)
                            }
                            StudioDetails(stringResource(R.string.setup_network_safety), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (createRepo) AlertDialog(onDismissRequest = { createRepo = false }, title = { Text(stringResource(R.string.setup_create_private_title)) }, text = { Text(stringResource(R.string.setup_create_private_body,StudioWorkflow.normalizeRepo(destination,true))) },
        confirmButton = { Button(onClick = { createRepo = false; viewModel.createDestinationRepo(destination) }) { Text(stringResource(R.string.setup_create_repo)) } }, dismissButton = { TextButton(onClick = { createRepo = false }) { Text(stringResource(R.string.common_cancel)) } })
}
