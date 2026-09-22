package com.unicornwhodev.visiondatasetstudio.ui.screens

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
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
    val learningDone=training?.let{it.sourceBatchNumber==number && com.unicornwhodev.visiondatasetstudio.domain.training.TrainingPolicy.finished(it.phase) && (it.phase=="abandoned" || it.exportSnapshot==batch?.archiveSnapshot)}==true
    val learningRequired=validated>0 && (project?.let(com.unicornwhodev.visiondatasetstudio.domain.training.TrainingPolicy::enabled)==true || training?.let{it.sourceBatchNumber==number && !com.unicornwhodev.visiondatasetstudio.domain.training.TrainingPolicy.finished(it.phase)}==true)
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
        StudioTopBar(stringResource(R.string.screen_export), tr("LOT $number", "BATCH $number"))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 800.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (samples.isEmpty()) Text(tr("Aucune image validée à exporter.", "No approved images to export."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (samples.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("$validated", tr("Validés", "Approved"), Modifier.weight(1f)); MetricTile("$unfinished", tr("À terminer", "Unfinished"), Modifier.weight(1f)); MetricTile("$rejected", tr("Rejetés", "Rejected"), Modifier.weight(1f))
            }
            if(samples.isNotEmpty() && rejected==samples.size && batch?.status !in setOf("VERIFIED","PURGED")) OutlinedButton(onClick={confirmRejected=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.publication_close_rejections))}
            StudioSection(stringResource(R.string.publication_local_archive), tr("Sans publication, avec choix de l’emplacement Android.", "Choose an Android destination without publishing."), Icons.Default.FolderZip) {
                Text(tr("ZIP · Images et annotations JSONL", "ZIP · Images and JSONL annotations"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExportToggle("WebDataset TAR", tr("Lecture par shards. Demande de l’espace supplémentaire.", "Read by shards. Requires additional storage."), tar, !busy) { tar = it }
                ExportToggle("COCO", tr("Boîtes uniquement; ne remplace pas les points ou légendes.", "Boxes only; does not replace points or captions."), coco, !busy) { coco = it }
                ExportToggle("YOLO", tr("Boîtes et classes. Une classe inconnue bloque l’export.", "Boxes and classes. An unknown class blocks export."), yolo, !busy) { yolo = it }
                ExportToggle(tr("Vision-langage", "Vision-language"), tr("Conserve les paires saisies et les abstentions; n’invente pas de réponse.", "Preserves entered pairs and abstentions; never invents answers."), vl, !busy) { vl = it }
                Button(onClick = { viewModel.exportActiveBatchToLocalZip(tar, true, coco, yolo, vl) }, enabled = !busy && available > 0 && batch?.status !in com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety.lockedStates, modifier = Modifier.heightIn(min = 40.dp)) {
                    Icon(Icons.Default.Archive, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Créer l’archive · $available", "Create archive · $available"))
                }
                lastZip?.takeIf { it.isFile }?.let { file ->
                    Text(tr("Archive prête · %.1f Mo", "Archive ready · %.1f MB").format(file.length() / 1048576.0), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { saveArchive.launch(file.name) }, enabled = !busy) { Text(stringResource(R.string.publication_save_copy)) }
                }
                StudioDetails(tr("Pour clôturer le lot local, tous les cas doivent être validés ou rejetés. Les cas rejetés ou différés ne sont pas inclus. Un export local ne déclenche aucune suppression.", "To close the local batch, every sample must be approved or rejected. Rejected or deferred samples are excluded. Local export never triggers deletion."), style = MaterialTheme.typography.bodySmall)
            }
            if(batch?.status in setOf("VERIFIED","PURGING","PURGED","EMPTY","DISCARDED")) StudioSection(stringResource(R.string.publication_batch_next), icon = Icons.Default.SkipNext) {
                if (batch != null && batch.status in setOf("VERIFIED","PURGING")) {
                    StatusPill(if(batch.verificationKind=="local")tr("Archive externe vérifiée", "External archive verified") else if(batch.verificationKind=="rejection_only")tr("Rejets explicitement confirmés", "Rejections explicitly confirmed") else tr("Contenus distants vérifiés", "Remote contents verified"), Icons.Default.VerifiedUser)
                    if(learningRequired && !learningDone) TextButton(onClick={viewModel.navigateTo(Screen.Training)}){Text(stringResource(R.string.publication_finish_training))}
                    OutlinedButton(onClick = { confirmPurge = true }, enabled = !busy && (!learningRequired || learningDone), modifier = Modifier.heightIn(min = 40.dp)) { Text(if(batch?.status=="PURGING") tr("Reprendre le nettoyage", "Resume cleanup") else tr("Libérer le stockage", "Free storage")) }
                }
                if (batch?.status in setOf("PURGED","EMPTY","DISCARDED") || (batch?.status=="VERIFIED" && policy?.keepVerifiedBatches==true && (!learningRequired || learningDone))) Button(onClick = { viewModel.nextBatch() }, enabled = !busy, modifier = Modifier.heightIn(min = 40.dp)) { Text(stringResource(R.string.publication_next_batch)) }
                if(batch?.status=="EMPTY") Text(tr("Fin de source · aucune nouvelle image", "End of source · no new images"),style=MaterialTheme.typography.bodySmall)
            }
            StudioDisclosure(stringResource(R.string.publication_hf), Icons.Default.CloudUpload, initiallyExpanded = batch?.status in setOf("PUBLISHING", "CONFLICT", "VERIFIED", "PURGING")) {
                Text((batch?.remoteRepoId ?: project?.hfDestRepo)?.ifBlank { tr("Destination à configurer", "Configure destination") } ?: tr("Destination à configurer", "Configure destination"), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { viewModel.navigateTo(Screen.Controls) },enabled=!busy){Text(stringResource(R.string.publication_transfer_options))}
                if (project?.hfDestRepo.isNullOrBlank()) OutlinedButton(onClick = { viewModel.navigateTo(Screen.Setup) }) { Text(stringResource(R.string.publication_configure_destination)) }
                StudioDetails(tr("Le lot distant utilise les formats enregistrés dans Moteur et transferts, avec images et JSONL canonique obligatoires. Tous les cas doivent avoir une décision finale. Les fichiers sont relus à distance et comparés par SHA-256.", "The remote batch uses the formats saved in Transfers; images and canonical JSONL are required. Every sample needs a final decision. Remote files are read back and compared by SHA-256."), style = MaterialTheme.typography.bodyMedium)
                if (unfinished > 0) Text(tr("$unfinished cas non terminés : reprenez les différés ou rejetez-les avec un motif.", "$unfinished unfinished samples: resume deferred samples or reject them with a reason."), color = MaterialTheme.colorScheme.error)
                Button(onClick = { confirmPublish = true }, enabled = !busy && validated > 0 && unfinished == 0 && !project?.hfDestRepo.isNullOrBlank() && batch?.status !in setOf("PURGING","PURGED") && (batch?.status != "VERIFIED" || batch.verificationKind == "local"), modifier = Modifier.heightIn(min = 40.dp)) { Text(when(batch?.status) { "PUBLISHED" -> tr("Reprendre la vérification", "Resume verification"); "PREPARED","PUBLISHING","CONFLICT" -> tr("Réconcilier l’envoi", "Reconcile upload"); else -> tr("Publier & vérifier", "Publish & verify") }) }
                batch?.lastTransferError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                if(batch?.status=="CONFLICT" && batch.hfCommitSha==null) OutlinedButton(onClick={confirmIsolate=true},enabled=!busy) { Text(stringResource(R.string.publication_isolate_retry)) }
                batch?.hfCommitSha?.let { sha -> SelectionContainer { Text("Commit : $sha", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) } }
                StudioDetails(tr("L’application doit rester ouverte pendant le transfert. Une coupure ne supprime pas les originaux. Le premier essai doit utiliser un dépôt privé de test.", "Keep the app open during transfer. An interruption does not delete originals. Use a private test repository for the first trial."), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showPreview = !showPreview }) { Icon(Icons.Default.DataObject, null); Spacer(Modifier.width(8.dp)); Text(if (showPreview) tr("Masquer l’aperçu", "Hide preview") else tr("Aperçu des données", "Data preview")) }
            if (showPreview) StudioSection(stringResource(R.string.publication_preview), tr("La sortie complète peut contenir davantage d’annotations.", "The full output may contain more annotations.")) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DatasetExportFormat.entries.forEach { format ->
                    val label=stringResource(when(format){DatasetExportFormat.CANONICAL_JSONL->R.string.export_format_canonical;DatasetExportFormat.COCO->R.string.export_format_coco;DatasetExportFormat.YOLO->R.string.export_format_yolo;DatasetExportFormat.VISION_LANGUAGE->R.string.export_format_vl})
                    FilterChip(previewKey == format.key, onClick = { previewKey = format.key }, label = { Text(label) })
                } }
                SelectionContainer { Text(preview?.take(16000) ?: tr("Chargement…", "Loading…"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
    if(confirmIsolate) AlertDialog(onDismissRequest={confirmIsolate=false},title={Text(stringResource(R.string.publication_isolate_title))},text={Text(stringResource(R.string.publication_isolate_body))},confirmButton={TextButton(onClick={confirmIsolate=false;viewModel.isolateConflict()}){Text(stringResource(R.string.publication_create_isolated_path))}},dismissButton={TextButton(onClick={confirmIsolate=false}){Text(stringResource(R.string.common_cancel))}})
    if(confirmRejected)AlertDialog(onDismissRequest={confirmRejected=false},title={Text(stringResource(R.string.publication_close_all_title))},text={Text(stringResource(R.string.publication_close_all_body))},confirmButton={TextButton(onClick={confirmRejected=false;viewModel.closeAllRejectedBatch()}){Text(stringResource(R.string.publication_close_rejections))}},dismissButton={TextButton(onClick={confirmRejected=false}){Text(stringResource(R.string.common_cancel))}})
    if (confirmPublish) AlertDialog(onDismissRequest = { confirmPublish = false }, icon = { Icon(Icons.Default.CloudUpload, null) }, title = { Text(tr("Publier $validated cas ?", "Publish $validated samples?")) },
        text = { Text(tr("Destination : ${batch?.remoteRepoId ?: project?.hfDestRepo}. Vérifiez les droits de redistribution des images. La vérification relit les fichiers et consomme du réseau. Aucune suppression locale automatique.", "Destination: ${batch?.remoteRepoId ?: project?.hfDestRepo}. Check image redistribution rights. Verification reads files back and uses network data. No automatic local deletion.")) },
        confirmButton = { Button(onClick = { confirmPublish = false; viewModel.publishActiveBatch() }) { Text(stringResource(R.string.publication_publish)) } }, dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text(stringResource(R.string.common_cancel)) } })
    if (confirmPurge) AlertDialog(onDismissRequest = { confirmPurge = false }, icon = { Icon(Icons.Default.DeleteOutline, null) }, title = { Text(stringResource(R.string.publication_purge_title)) },
        text = { Text(tr("Les $validated images validées disposent d’une copie vérifiée (${batch?.verificationKind ?: "aucune"}). Les $rejected images rejetées, non publiées, seront aussi supprimées de cet appareil. Les annotations et décisions restent dans l’historique. Cette suppression n’est pas annulable.", "The $validated approved images have a verified copy (${batch?.verificationKind ?: "none"}). The $rejected rejected, unpublished images will also be deleted from this device. Annotations and decisions remain in history. Deletion cannot be undone.")) },
        confirmButton = { Button(onClick = { confirmPurge = false; viewModel.purgeActiveBatch() }) { Text(stringResource(R.string.publication_confirm_delete)) } }, dismissButton = { TextButton(onClick = { confirmPurge = false }) { Text(stringResource(R.string.common_keep)) } })
}

@Composable
private fun ExportToggle(title: String, hint: String, selected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    var help by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { help = true }) { Icon(Icons.Default.Info, tr("Détails : $title", "Details: $title"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Checkbox(selected, onCheckedChange = null, enabled = enabled)
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(title) }, text = { Text(hint) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.common_understood)) } })
}
