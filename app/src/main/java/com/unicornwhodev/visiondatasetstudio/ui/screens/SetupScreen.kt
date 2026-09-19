package com.unicornwhodev.visiondatasetstudio.ui.screens

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
        StudioTopBar("Mon projet", "Configuration", onBack = viewModel::back)
    }, bottomBar = {
        Surface(shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Précédent") }
                Button(onClick = {
                    if (step < 2) step++ else viewModel.saveSetup(name, source, destination, config, split, imageColumn, classes,
                        budget.toLongOrNull() ?: 500L, tasks, prepare)
                }, enabled = !busy && when(step) { 0 -> sourceOk; 1 -> classes.isNotBlank(); else -> sourceOk && destOk && (budget.toLongOrNull() ?: 0L) in 128L..65536L },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("setup_next")) {
                    Text(if (step < 2) "Continuer" else if (prepare) "Démarrer" else "Enregistrer")
                }
            }
        }
    }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                StudioTabs(listOf("Source", "Outils", "Sortie"), step, { if (!busy) step = it })
                when(step) {
                    0 -> {
                        OutlinedTextField(name, { name = it }, label = { Text("Nom du projet") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                        StudioSection("Images locales", "L’index référence les images. Seul le lot actif est copié dans le cache.", Icons.Default.FolderOpen) {
                            val localPolicy = com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(p)
                            if (localPolicy.sourceMode == "LOCAL_INDEX" && localPolicy.sourceIndexReady) Text(localPolicy.localSourceLabel.ifBlank { "Dossier indexé" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { chooseFolder.launch(null) }, enabled = !busy && batches.isEmpty(), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Choisir un dossier")
                            }
                        }
                        StudioDisclosure("Dataset Hugging Face", Icons.Default.CloudDownload, initiallyExpanded = p.hfSourceRepo.isNotBlank()) {
                            OutlinedTextField(source, { source = it }, label = { Text("Lien ou identifiant du dataset") }, placeholder = { Text("organisation/dataset") },
                                isError = source.isNotBlank() && !sourceOk, supportingText = { Text("URL de dataset ou identifiant, pas une URL de fichier.") },
                                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("source_repo_input"))
                            FilledTonalButton(onClick = { viewModel.inspectSourceDataset(source, config, split) }, enabled = sourceOk && source.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Inspecter la source")
                            }
                            if (inspection.isInspected && inspection.repoId == StudioWorkflow.normalizeRepo(source)) {
                                StatusPill("${inspection.previewRows.size} lignes vérifiées", Icons.Default.CheckCircleOutline)
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
                                Text("Vérifiez que « $imageColumn » est bien la colonne contenant les images.", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Réduire" else "Options avancées") }
                            if (advanced) {
                                OutlinedTextField(config, { config = it }, label = { Text("Configuration HF") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(split, { split = it }, label = { Text("Split source") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(imageColumn, { imageColumn = it }, label = { Text("Colonne image") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            if (batches.isNotEmpty()) StudioDetails("La provenance est verrouillée après l’acquisition du premier lot : modifier la source sera refusé, sans toucher aux données.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StudioDisclosure("Connexion Hugging Face", Icons.Default.Key) {
                            Text(if (auth?.isValid == true) "Connecté : ${auth?.username}" else "Lecture publique disponible", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(token, { token = it }, label = { Text("Jeton Hugging Face") }, placeholder = { Text("hf_…") }, singleLine = true,
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("token_input"),
                                trailingIcon = { IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Afficher ou masquer le jeton") } })
                            Button(onClick = { viewModel.saveToken(token); token = "" }, enabled = token.isNotBlank() && !busy) { Text("Connecter") }
                            if (auth?.error != null) Text(auth?.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Stockage chiffré avec Android Keystore. Le jeton n’est pas inclus dans les exports.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    1 -> {
                        StudioSection("Tâches", "Vous pourrez combiner ou changer les tâches ensuite.", Icons.Default.Widgets) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioWorkflow.presets.forEach { preset -> FilterChip(selected = tasks == preset.tasks, onClick = { tasksCsv = StudioWorkflow.tasksCsv(preset.tasks) }, label = { Text(preset.title) }) }
                            }
                            HorizontalDivider()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioTask.entries.forEach { task -> FilterChip(selected = task in tasks, onClick = { tasksCsv = StudioWorkflow.tasksCsv(StudioWorkflow.toggleTask(tasks, task)) }, label = { Text(task.title) }) }
                            }
                            OutlinedTextField(classes, { classes = it }, label = { Text("Classes (séparées par des virgules)") }, supportingText = { Text("Identifiants stables de votre taxonomie. Les classes existantes ne sont pas renommées automatiquement.") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        }
                        StudioSection("Assistant IA", "Les propositions restent à vérifier. Sans modèle, tous les outils manuels restent disponibles.", Icons.Default.AutoAwesome) {
                            StatusPill(if (p.modelPath.isNullOrBlank()) "Aucun modèle importé" else "Poids importés · compatibilité à vérifier", if (p.modelPath.isNullOrBlank()) Icons.Default.Info else Icons.Default.Memory)
                            OutlinedButton(onClick = { chooseModel.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Choisir un .tflite") }
                            OutlinedTextField(modelUrl, { modelUrl = it }, label = { Text("Ou lien HTTPS direct vers les poids") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { viewModel.importModelUrl(modelUrl) }, enabled = modelUrl.startsWith("https://") && !busy) { Text("Télécharger le modèle") }
                            StudioDetails("Adaptateurs présents : détection SSD / TF Object Detection et classification. Un modèle YOLO, de pointing ou un VLM arbitraire nécessite son propre adaptateur.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(modelJson, { modelJson = it }, label = { Text("model-config.json · optionnel") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { viewModel.saveModelConfig(modelJson) }, enabled = !busy) { Text("Enregistrer le contrat") }
                        }
                    }
                    2 -> {
                        StudioSection("Destination du corpus", "L’export local reste possible sans dépôt de destination.", Icons.Default.IosShare) {
                            OutlinedTextField(destination, { destination = it }, label = { Text("Dataset HF final · facultatif") }, placeholder = { Text("utilisateur/corpus-prepare") }, singleLine = true,
                                isError = destination.isNotBlank() && !destOk, modifier = Modifier.fillMaxWidth())
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.checkDestinationRepo(destination) }, enabled = destination.isNotBlank() && destOk && !busy) { Text("Vérifier l’accès") }
                                OutlinedButton(onClick = { createRepo = true }, enabled = destination.isNotBlank() && destOk && auth?.isValid == true && !busy) { Text("Créer en privé") }
                            }
                            if (dest != null) Text(if (dest?.exists == true) "Dépôt accessible. Cela ne garantit pas le droit d’écriture." else dest?.message ?: "Accès non confirmé", style = MaterialTheme.typography.bodySmall)
                            StudioDetails("Les formats et le contenu de l’archive seront choisis dans Export. Aucun téléversement ne démarre à cette étape.", style = MaterialTheme.typography.bodyMedium)
                        }
                        StudioSection("Stockage et rythme", icon = Icons.Default.Storage) {
                            OutlinedTextField(budget, { budget = it.filter(Char::isDigit).take(5) }, label = { Text("Budget local en Mo") }, supportingText = { Text("Entre 128 et 65 536 Mio. Lots de 1 à 1 000 cas, configurables.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Checkbox(prepare, { prepare = it })
                                Text("Préparer le lot ensuite", style = MaterialTheme.typography.bodyMedium)
                            }
                            StudioDetails("Les erreurs réseau ne doivent pas effacer les corrections. La purge automatique exige une vérification distante des fichiers publiés.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (createRepo) AlertDialog(onDismissRequest = { createRepo = false }, title = { Text("Créer un dataset privé ?") }, text = { Text("Le dépôt ${StudioWorkflow.normalizeRepo(destination, true)} sera créé sur votre compte Hugging Face. Aucun fichier ne sera publié maintenant.") },
        confirmButton = { Button(onClick = { createRepo = false; viewModel.createDestinationRepo(destination) }) { Text("Créer le dépôt") } }, dismissButton = { TextButton(onClick = { createRepo = false }) { Text("Annuler") } })
}
