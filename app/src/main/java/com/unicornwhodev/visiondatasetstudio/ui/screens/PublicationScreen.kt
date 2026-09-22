package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExportFormat
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.Screen
import com.unicornwhodev.visiondatasetstudio.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicationScreen(viewModel: MainViewModel) {
    val number by viewModel.activeBatchNumber.collectAsState()
    val samples by viewModel.batchSamples.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val training by viewModel.trainingRun.collectAsState()
    val lastZip by viewModel.lastExportedZip.collectAsState()
    val preview by viewModel.previewSnippet.collectAsState()
    val policy = project?.let(ProjectSettings::read)
    val batch = batches.firstOrNull { it.batchNumber == number }
    val validated = samples.count { it.annotationStatus == "VALIDATED" }
    val rejected = samples.count { it.annotationStatus == "REJECTED" }
    val unfinished = samples.size - validated - rejected
    val learningDone=training?.let{it.sourceBatchNumber==number && it.phase in setOf("completed","rejected")}==true
    val learningRequired=validated>0 && (policy?.continuousTraining==true || training?.let{it.sourceBatchNumber==number && it.phase !in setOf("completed","rejected")}==true)
    val available = samples.count { it.annotationStatus == "VALIDATED" && it.localImagePath != null }
    var tar by rememberSaveable { mutableStateOf(false) }
    var coco by rememberSaveable { mutableStateOf(false) }
    var yolo by rememberSaveable { mutableStateOf(false) }
    var vl by rememberSaveable { mutableStateOf(true) }
    var previewKey by rememberSaveable { mutableStateOf("CANONICAL_JSON") }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var confirmIsolate by remember { mutableStateOf(false) }
    var confirmPublish by remember { mutableStateOf(false) }
    var confirmRejected by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }
    val saveArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.saveArchiveToUri(uri)
    }
    LaunchedEffect(showPreview, previewKey, number, samples) { if (showPreview) viewModel.loadPreviewSnippet(previewKey) }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        StudioTopBar(stringResource(R.string.screen_export), "LOT $number")
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 800.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (samples.isEmpty()) Text("Aucune image validée à exporter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (samples.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("$validated", "Validés", Modifier.weight(1f)); MetricTile("$unfinished", "À terminer", Modifier.weight(1f)); MetricTile("$rejected", "Rejetés", Modifier.weight(1f))
            }
            if(samples.isNotEmpty() && rejected==samples.size && batch?.status !in setOf("VERIFIED","PURGED")) OutlinedButton(onClick={confirmRejected=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.publication_close_rejections))}
            StudioSection(stringResource(R.string.publication_local_archive), "Sans publication, avec choix de l’emplacement Android.", Icons.Default.FolderZip) {
                Text("ZIP · Images et annotations JSONL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExportToggle("WebDataset TAR", "Lecture par shards. Demande de l’espace supplémentaire.", tar, !busy) { tar = it }
                ExportToggle("COCO", "Boîtes uniquement; ne remplace pas les points ou légendes.", coco, !busy) { coco = it }
                ExportToggle("YOLO", "Boîtes et classes. Une classe inconnue bloque l’export.", yolo, !busy) { yolo = it }
                ExportToggle("Vision-langage", "Conserve les paires saisies et les abstentions; n’invente pas de réponse.", vl, !busy) { vl = it }
                Button(onClick = { viewModel.exportActiveBatchToLocalZip(tar, true, coco, yolo, vl) }, enabled = !busy && available > 0 && batch?.status !in com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates, modifier = Modifier.heightIn(min = 40.dp)) {
                    Icon(Icons.Default.Archive, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Créer l’archive · $available")
                }
                lastZip?.takeIf { it.isFile }?.let { file ->
                    Text("Archive prête · %.1f Mo".format(file.length() / 1048576.0), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { saveArchive.launch(file.name) }, enabled = !busy) { Text(stringResource(R.string.publication_save_copy)) }
                }
                StudioDetails("Pour clôturer le lot local, tous les cas doivent être validés ou rejetés. Les cas rejetés ou différés ne sont pas inclus. Un export local ne déclenche aucune suppression.", style = MaterialTheme.typography.bodySmall)
            }
            if(batch?.status in setOf("VERIFIED","PURGING","PURGED","EMPTY")) StudioSection(stringResource(R.string.publication_batch_next), icon = Icons.Default.SkipNext) {
                if (batch != null && batch.status in setOf("VERIFIED","PURGING")) {
                    StatusPill(if(batch.verificationKind=="local")"Archive externe vérifiée" else if(batch.verificationKind=="rejection_only")"Rejets explicitement confirmés" else "Contenus distants vérifiés", Icons.Default.VerifiedUser)
                    if(learningRequired && !learningDone) TextButton(onClick={viewModel.navigateTo(Screen.Training)}){Text(stringResource(R.string.publication_finish_training))}
                    OutlinedButton(onClick = { confirmPurge = true }, enabled = !busy && (!learningRequired || learningDone), modifier = Modifier.heightIn(min = 40.dp)) { Text(if(batch?.status=="PURGING") "Reprendre le nettoyage" else "Libérer le stockage") }
                }
                if (batch?.status in setOf("PURGED","EMPTY") || (batch?.status=="VERIFIED" && policy?.keepVerifiedBatches==true && (!learningRequired || learningDone))) Button(onClick = { viewModel.nextBatch() }, enabled = !busy, modifier = Modifier.heightIn(min = 40.dp)) { Text(stringResource(R.string.publication_next_batch)) }
                if(batch?.status=="EMPTY") Text("Fin de source · aucune nouvelle image",style=MaterialTheme.typography.bodySmall)
            }
            StudioDisclosure(stringResource(R.string.publication_hf), Icons.Default.CloudUpload, initiallyExpanded = batch?.status in setOf("PUBLISHING", "CONFLICT", "VERIFIED", "PURGING")) {
                Text((batch?.remoteRepoId ?: project?.hfDestRepo)?.ifBlank { "Destination à configurer" } ?: "Destination à configurer", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { viewModel.navigateTo(Screen.Controls) },enabled=!busy){Text(stringResource(R.string.publication_transfer_options))}
                if (project?.hfDestRepo.isNullOrBlank()) OutlinedButton(onClick = { viewModel.navigateTo(Screen.Setup) }) { Text(stringResource(R.string.publication_configure_destination)) }
                StudioDetails("Le lot distant utilise les formats enregistrés dans Moteur et transferts, avec images et JSONL canonique obligatoires. Tous les cas doivent avoir une décision finale. Les fichiers sont relus à distance et comparés par SHA-256.", style = MaterialTheme.typography.bodyMedium)
                if (unfinished > 0) Text("$unfinished cas non terminés : reprenez les différés ou rejetez-les avec un motif.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { confirmPublish = true }, enabled = !busy && validated > 0 && unfinished == 0 && !project?.hfDestRepo.isNullOrBlank() && batch?.status !in setOf("PURGING","PURGED") && (batch?.status != "VERIFIED" || batch.verificationKind == "local"), modifier = Modifier.heightIn(min = 40.dp)) { Text(when(batch?.status) { "PUBLISHED" -> "Reprendre la vérification"; "PREPARED","PUBLISHING","CONFLICT" -> "Réconcilier l’envoi"; else -> "Publier & vérifier" }) }
                batch?.lastTransferError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                if(batch?.status=="CONFLICT" && batch.hfCommitSha==null) OutlinedButton(onClick={confirmIsolate=true},enabled=!busy) { Text(stringResource(R.string.publication_isolate_retry)) }
                batch?.hfCommitSha?.let { sha -> SelectionContainer { Text("Commit : $sha", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) } }
                StudioDetails("L’application doit rester ouverte pendant le transfert. Une coupure ne supprime pas les originaux. Le premier essai doit utiliser un dépôt privé de test.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showPreview = !showPreview }) { Icon(Icons.Default.DataObject, null); Spacer(Modifier.width(8.dp)); Text(if (showPreview) "Masquer l’aperçu" else "Aperçu des données") }
            if (showPreview) StudioSection(stringResource(R.string.publication_preview), "La sortie complète peut contenir davantage d’annotations.") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DatasetExportFormat.entries.forEach { format ->
                    val label=stringResource(when(format){DatasetExportFormat.CANONICAL_JSONL->R.string.export_format_canonical;DatasetExportFormat.COCO->R.string.export_format_coco;DatasetExportFormat.YOLO->R.string.export_format_yolo;DatasetExportFormat.VISION_LANGUAGE->R.string.export_format_vl})
                    FilterChip(previewKey == format.key, onClick = { previewKey = format.key }, label = { Text(label) })
                } }
                SelectionContainer { Text(preview?.take(16000) ?: "Chargement…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
    if(confirmIsolate) AlertDialog(onDismissRequest={confirmIsolate=false},title={Text(stringResource(R.string.publication_isolate_title))},text={Text(stringResource(R.string.publication_isolate_body))},confirmButton={TextButton(onClick={confirmIsolate=false;viewModel.isolateConflict()}){Text(stringResource(R.string.publication_create_isolated_path))}},dismissButton={TextButton(onClick={confirmIsolate=false}){Text(stringResource(R.string.common_cancel))}})
    if(confirmRejected)AlertDialog(onDismissRequest={confirmRejected=false},title={Text(stringResource(R.string.publication_close_all_title))},text={Text(stringResource(R.string.publication_close_all_body))},confirmButton={TextButton(onClick={confirmRejected=false;viewModel.closeAllRejectedBatch()}){Text(stringResource(R.string.publication_close_rejections))}},dismissButton={TextButton(onClick={confirmRejected=false}){Text(stringResource(R.string.common_cancel))}})
    if (confirmPublish) AlertDialog(onDismissRequest = { confirmPublish = false }, icon = { Icon(Icons.Default.CloudUpload, null) }, title = { Text("Publier $validated cas ?") },
        text = { Text("Destination : ${batch?.remoteRepoId ?: project?.hfDestRepo}. Vérifiez les droits de redistribution des images. La vérification relit les fichiers et consomme du réseau. Aucune suppression locale automatique.") },
        confirmButton = { Button(onClick = { confirmPublish = false; viewModel.publishActiveBatch() }) { Text(stringResource(R.string.publication_publish)) } }, dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text(stringResource(R.string.common_cancel)) } })
    if (confirmPurge) AlertDialog(onDismissRequest = { confirmPurge = false }, icon = { Icon(Icons.Default.DeleteOutline, null) }, title = { Text(stringResource(R.string.publication_purge_title)) },
        text = { Text("Les $validated images validées disposent d’une copie vérifiée (${batch?.verificationKind ?: "aucune"}). Les $rejected images rejetées, non publiées, seront aussi supprimées de cet appareil. Les annotations et décisions restent dans l’historique. Cette suppression n’est pas annulable.") },
        confirmButton = { Button(onClick = { confirmPurge = false; viewModel.purgeActiveBatch() }) { Text(stringResource(R.string.publication_confirm_delete)) } }, dismissButton = { TextButton(onClick = { confirmPurge = false }) { Text(stringResource(R.string.common_keep)) } })
}

@Composable
private fun ExportToggle(title: String, hint: String, selected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    var help by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { help = true }) { Icon(Icons.Default.Info, "Détails : $title", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Checkbox(selected, onCheckedChange = null, enabled = enabled)
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(title) }, text = { Text(hint) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.common_understood)) } })
}
