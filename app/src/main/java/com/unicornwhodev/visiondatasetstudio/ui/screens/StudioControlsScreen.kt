package com.unicornwhodev.visiondatasetstudio.ui.screens

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Every enabled control below has an execution path; unsupported runtimes are not advertised as toggles. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun StudioControlsScreen(vm:MainViewModel) {
    val project by vm.projectFlow.collectAsState()
    val allProjects by vm.projects.collectAsState()
    val models by vm.modelProfiles.collectAsState()
    val busy by vm.isBusy.collectAsState()
    val diagnostics by vm.modelDiagnostics.collectAsState()
    val receipts by vm.inferenceReceipts.collectAsState()
    val dryRun by vm.dryRunResult.collectAsState()
    val benchmark by vm.benchmarkReport.collectAsState()
    var benchmarkRuns by rememberSaveable { mutableStateOf(10) }
    var catalogChoice by remember { mutableStateOf<PublicModelCatalog.Entry?>(null) }
    val correctionReport by vm.correctionReport.collectAsState()
    var removeProfile by remember{mutableStateOf<String?>(null)}
    var confirmReset by remember{mutableStateOf(false)}
    var destructiveAction by remember{mutableStateOf<String?>(null)}
    val p=project ?: return
    val stored=remember(p.settingsJson){ProjectSettings.read(p)}
    var tab by rememberSaveable{mutableStateOf(0)}
    var policy by remember(p.id,p.settingsJson){mutableStateOf(stored)}
    var budget by remember(p.id,p.diskBudgetMb){mutableStateOf(p.diskBudgetMb.toString())}
    var idColumn by remember(p.id,p.idColumn){mutableStateOf(p.idColumn)}
    var split by remember(p.id,p.targetSplit){mutableStateOf(p.targetSplit)}
    var batchText by remember(p.id,p.settingsJson){mutableStateOf(stored.batchSize.toString())}
    var workerId by remember(p.id,p.settingsJson){mutableStateOf(stored.collaborationWorkerId)}
    var leaseText by remember(p.id,p.settingsJson){mutableStateOf(stored.claimLeaseMinutes.toString())}
    var newName by rememberSaveable{mutableStateOf("")}
    var modelName by rememberSaveable{mutableStateOf("")}
    var modelUrl by rememberSaveable{mutableStateOf("")}
    val moshi=remember{com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi}
    val configAdapter=remember{moshi.adapter(ModelConfig::class.java).indent("  ")}
    var contract by remember(p.id,p.modelConfigJson){mutableStateOf(p.modelConfigJson ?: configAdapter.toJson(ModelConfig.defaultDetectionPreset(p.classesCsv.split(',').map(String::trim))))}
    var contractError by remember{mutableStateOf<String?>(null)}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let(vm::importSourceFolder)}
    val manifestFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let(vm::chooseManifestImageFolder)}
    val manifest=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importSourceManifest)}
    val weights=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importModel)}
    val packIn=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importPack)}
    val benchmarkOut=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::saveBenchmark)}
    val packOut=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportPack)}
    Scaffold(contentWindowInsets=WindowInsets(0),modifier=Modifier.imePadding(),topBar={StudioTopBar(stringResource(R.string.screen_controls), p.name, onBack={ if (!busy) vm.back() })}) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            StudioTabs(listOf(tr("Projets", "Projects"), "Sources", tr("Transferts", "Transfers"), tr("Modèles", "Models")), tab, { tab=it }, Modifier.padding(horizontal=16.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                when(tab) {
                    0 -> {
                        StudioSection(stringResource(R.string.controls_projects),tr("Chaque projet conserve sa source, son curseur, ses lots, annotations et preuves de copie. Le token HF et la bibliothèque de modèles restent communs à l’appareil.", "Each project keeps its own source, cursor, batches, annotations and copy receipts. The HF token and model library are shared on the device."),Icons.Default.FolderOpen) {
                            allProjects.forEach { item ->
                                OutlinedButton(onClick={vm.selectProject(item.id)},enabled=!busy && item.id!=p.id,modifier=Modifier.fillMaxWidth()) {
                                    Icon(if(item.id==p.id)Icons.Default.CheckCircle else Icons.Default.Folder,null);Spacer(Modifier.width(8.dp));Text(item.name,Modifier.weight(1f))
                                }
                            }
                            OutlinedTextField(newName,{newName=it},label={Text(stringResource(R.string.controls_new_project_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
                            Button(onClick={vm.createProject(newName);newName=""},enabled=!busy && newName.isNotBlank()){Text(stringResource(R.string.controls_create_project))}
                            OutlinedButton(onClick={vm.navigateTo(Screen.Setup)},enabled=!busy){Text(stringResource(R.string.common_configure))}
                            HorizontalDivider()
                            Text(tr("Données locales", "Local data"),style=MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick={destructiveAction="discard"},enabled=!busy){Text(tr("Supprimer le lot · garder l’historique", "Delete batch · keep history"))}
                            OutlinedButton(onClick={destructiveAction="batch"},enabled=!busy){Text(stringResource(R.string.controls_reset_batch))}
                            OutlinedButton(onClick={destructiveAction="project"},enabled=!busy){Text(stringResource(R.string.controls_reset_project))}
                            TextButton(onClick={destructiveAction="delete"},enabled=!busy,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text(stringResource(R.string.controls_delete_project))}
                        }
                        StudioSection(stringResource(R.string.controls_presets),tr("Un pack configure les tâches, classes, taille de lot et contrat modèle. Il exclut les poids, le jeton HF du coffre, le corpus et ses emplacements. Le contrat, le prompt et le corps JSON personnalisé sont inclus : retirez tout secret avant partage.", "A preset configures tasks, classes, batch size and the model contract. It excludes weights, the vault's HF token, corpus and locations. The contract, prompt and custom JSON body are included: remove secrets before sharing."),Icons.Default.Inventory2) {
                            Button(onClick={packIn.launch(arrayOf("application/json","text/*","application/octet-stream"))},enabled=!busy){Text(stringResource(R.string.controls_import_preset))}
                            OutlinedButton(onClick={packOut.launch("studio-preset.json")},enabled=!busy){Text(stringResource(R.string.controls_export_preset))}
                            StudioDetails(tr("Chaque projet peut utiliser son propre pack de tâches et de classes. Sélectionnez séparément les modèles que vous êtes autorisé à utiliser.", "Each project can use its own task and class preset. Select models you are authorized to use separately."), style =MaterialTheme.typography.bodyMedium)
                        }
                    }
                    1 -> {
                        StudioSection(stringResource(R.string.controls_source_selection),tr("Enregistrez les réglages avant d’indexer. Après le premier lot, changer de source ou de filtre exige un nouveau projet.", "Save settings before indexing. After the first batch, changing sources or filters requires a new project."),Icons.Default.CloudDownload) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("HF_VIEWER" to "HF Viewer","HF_MANIFEST" to tr("JSONL sur HF", "JSONL on HF"),"LOCAL_INDEX" to "Local").forEach{(value,label)->
                                    FilterChip(selected=policy.sourceMode==value,onClick={policy=policy.copy(sourceMode=value)},enabled=!busy,label={Text(label)})
                                }
                            }
                            Text(tr("Source HF : ${p.hfSourceRepo.ifBlank { "non configurée" }}", "HF source: ${p.hfSourceRepo.ifBlank { "not configured" }}"),style=MaterialTheme.typography.bodyMedium)
                            TextButton(onClick={vm.navigateTo(Screen.Setup)},enabled=!busy){Text(stringResource(R.string.controls_configure_source))}
                            if(policy.sourceMode=="HF_VIEWER") {
                                ControlField(tr("Filtre HF (where), facultatif", "HF filter (where), optional"),policy.filterExpression){policy=policy.copy(filterExpression=it)}
                                ControlField(tr("Ordre HF (orderby), facultatif", "HF sort order (orderby), optional"),policy.orderBy){policy=policy.copy(orderBy=it)}
                                StudioDetails(tr("Le Viewer doit prendre en charge cette source. Les requêtes sont paginées à 100 lignes maximum. Une révision de fichier épinglée n’est pas disponible pour cette voie.", "The Viewer must support this source. Requests are paginated with at most 100 rows. This route does not support pinning a file revision."), style =MaterialTheme.typography.bodySmall)
                            } else if(policy.sourceMode=="HF_MANIFEST") {
                                ControlField(tr("Révision source : branche ou SHA", "Source revision: branch or SHA"),policy.sourceRevision){policy=policy.copy(sourceRevision=it)}
                                ControlField(tr("Chemin du JSONL dans le dépôt", "JSONL path in the repository"),policy.manifestPath){policy=policy.copy(manifestPath=it)}
                                Button(onClick=vm::fetchHfManifest,enabled=!busy && stored.sourceMode=="HF_MANIFEST"){Text(stringResource(R.string.controls_index_manifest))}
                                if(stored.resolvedSourceRevision!=null) SelectionContainer { Text(tr("Révision résolue : ${stored.resolvedSourceRevision}", "Resolved revision: ${stored.resolvedSourceRevision}"),style=MaterialTheme.typography.bodySmall) }
                            } else {
                                OutlinedButton(onClick={folder.launch(null)},enabled=!busy && stored.sourceMode=="LOCAL_INDEX"){Text(stringResource(R.string.setup_choose_folder))}
                                OutlinedButton(onClick={manifestFolder.launch(null)},enabled=!busy && stored.sourceMode=="LOCAL_INDEX"){Text(stringResource(R.string.controls_manifest_folder))}
                                Button(onClick={manifest.launch(arrayOf("application/json","application/x-ndjson","text/*","application/octet-stream"))},enabled=!busy && stored.sourceMode=="LOCAL_INDEX"){Text(stringResource(R.string.controls_import_jsonl))}
                                StudioDetails(tr("Le dossier source n’est jamais modifié. Les chemins relatifs du JSONL sont résolus à partir du dossier choisi; les URL HTTPS sont également acceptées.", "The source folder is never modified. Relative JSONL paths are resolved from the selected folder; HTTPS URLs are also accepted."), style =MaterialTheme.typography.bodySmall)
                            }
                            ControlField(tr("Colonne identifiant", "ID column"),idColumn){idColumn=it}
                            ControlSwitch(tr("Importer les brouillons", "Import draft annotations"),policy.importAnnotations){policy=policy.copy(importAnnotations=it)}
                            Text(if(stored.sourceIndexReady)tr("Index prêt · ${stored.localSourceLabel}", "Index ready · ${stored.localSourceLabel}") else tr("Index local non préparé (inutile en mode Viewer)", "Local index not prepared (not required in Viewer mode)"),style=MaterialTheme.typography.labelMedium)
                        }
                        StudioSection(stringResource(R.string.controls_collaboration),tr("Évite que plusieurs personnes téléchargent et traitent les mêmes cas. Les réservations sont stockées dans le dépôt HF de destination et expirent si un appareil est abandonné.", "Prevents collaborators from downloading and processing the same samples. Reservations are stored in the destination HF repository and expire if a device is abandoned."),Icons.Default.Groups) {
                            ControlSwitch(tr("Activer les réservations partagées", "Enable shared reservations"),policy.collaborationEnabled){ enabled ->
                                if(enabled && (policy.sourceMode=="LOCAL_INDEX" || p.hfDestRepo.isBlank())) vm.reportError(tr("Le travail partagé exige une source HF et un dépôt HF de destination", "Shared work requires an HF source and destination HF repository"))
                                else policy=policy.copy(collaborationEnabled=enabled)
                            }
                            if(policy.collaborationEnabled) {
                                ControlField(tr("Identifiant local", "Local identifier"),workerId){workerId=it;policy=policy.copy(collaborationWorkerId=it.trim())}
                                TextButton(onClick={ val id="worker-"+java.util.UUID.randomUUID().toString().take(8);workerId=id;policy=policy.copy(collaborationWorkerId=id) }){Text(stringResource(R.string.controls_generate))}
                                ControlField(tr("Bail (minutes)", "Lease (minutes)"),leaseText,true){leaseText=it;policy=policy.copy(claimLeaseMinutes=it.toIntOrNull() ?: -1)}
                                StudioDetails(tr("Au prochain lot, l’app ignore les cas marqués DONE ou CLAIMED par un autre collaborateur avant de télécharger leurs images. Les réservations utilisent un commit parent HF : un conflit force une relecture avant nouvelle tentative.", "For the next batch, the app skips samples marked DONE or CLAIMED by another collaborator before downloading images. Reservations use an HF parent commit: a conflict requires rereading before retrying."), style =MaterialTheme.typography.bodySmall)
                                Text(tr("Coordination : ${p.hfDestRepo.ifBlank { "destination HF à configurer" }} · branche ${policy.destBranch}", "Coordination: ${p.hfDestRepo.ifBlank { "configure HF destination" }} · branch ${policy.destBranch}"),style=MaterialTheme.typography.labelMedium)
                            }
                        }
                        StudioSection(stringResource(R.string.controls_batches_network),tr("La taille du lot d’annotation est indépendante de la pagination HTTP et du nombre d’images inférées simultanément.", "Annotation batch size is independent of HTTP pagination and the number of images inferred simultaneously."),Icons.Default.Layers) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(25,50,100,250,500,1000).forEach{n->FilterChip(selected=batchText==n.toString(),onClick={batchText=n.toString()},label={Text("$n")})}}
                            ControlField(tr("Cas par lot · 1 à 1 000", "Samples per batch · 1 to 1,000"),batchText,true){batchText=it}
                            ControlInt(tr("Téléchargements simultanés · 1 à 4", "Simultaneous downloads · 1 to 4"),policy.downloadConcurrency){policy=policy.copy(downloadConcurrency=it)}
                            ControlInt(tr("Nouvelles tentatives · 0 à 5", "Retries · 0 to 5"),policy.retryCount){policy=policy.copy(retryCount=it)}
                            ControlInt(tr("Délai HTTP, secondes · 10 à 300", "HTTP timeout, seconds · 10 to 300"),policy.timeoutSeconds){policy=policy.copy(timeoutSeconds=it)}
                            ControlSwitch(tr("Autoriser un réseau facturé / mobile", "Allow metered / mobile networks"),policy.allowMetered){policy=policy.copy(allowMetered=it)}
                            StudioDetails(tr("Le refus d’un réseau facturé est contrôlé avant chaque transfert. Le travail s’effectue au premier plan; aucune exécution de fond permanente n’est promise.", "Metered network permission is checked before each transfer. Processing runs in the foreground; continuous background execution is not promised."), style =MaterialTheme.typography.bodySmall)
                        }
                        SaveControlsButton(busy) {
                            val batch=batchText.toIntOrNull();val mb=budget.toLongOrNull()
                            if(batch==null || mb==null)vm.reportError(tr("Taille du lot ou budget invalide", "Invalid batch size or budget")) else vm.saveProcessingSettings(policy.copy(batchSize=batch),mb,idColumn,split)
                        }
                    }
                    2 -> {
                        StudioSection(stringResource(R.string.controls_bounded_storage),tr("Les budgets s’appliquent à l’espace utilisé par cette application, modèles et exports compris. Les sources sélectionnées restent en lecture seule.", "Budgets apply to this app's storage, including models and exports. Selected sources remain read-only."),Icons.Default.Storage) {
                            ControlField(tr("Budget de l’application, Mio · 128 à 65 536", "App budget, MiB · 128 to 65,536"),budget,true){budget=it}
                            ControlInt(tr("Espace libre à conserver, Mio · 32 à 4 096", "Free storage reserve, MiB · 32 to 4,096"),policy.reserveFreeMb){policy=policy.copy(reserveFreeMb=it)}
                            ControlInt(tr("Taille maximale d’une image, Mio · 1 à 256", "Maximum image size, MiB · 1 to 256"),policy.maxImageMb){policy=policy.copy(maxImageMb=it)}
                            ControlSwitch(tr("Préannoter les nouvelles images à l’import", "Preannotate new images on import"),policy.autoPreannotate){policy=policy.copy(autoPreannotate=it)}
                            ControlSwitch(tr("Conserver les lots vérifiés pour passer au suivant", "Keep verified batches when moving to the next one"),policy.keepVerifiedBatches){policy=policy.copy(keepVerifiedBatches=it)}
                            ControlSwitch(tr("Normaliser l’orientation EXIF de la copie cache", "Normalize EXIF orientation of the cached copy"),policy.normalizeExif){policy=policy.copy(normalizeExif=it)}
                            StudioDetails(tr("La normalisation ne redimensionne pas l’image, mais la réencode; les deux hashes et la transformation sont conservés. Une rotation avec annotations importées ambiguës ou plus de 8 mégapixels est refusée. La copie source reste intacte.", "Normalization re-encodes without resizing; both hashes and the transform are retained. Rotation with ambiguous imported annotations or images above 8 megapixels is refused. The source copy remains intact."), style =MaterialTheme.typography.bodySmall)
                            StudioDetails(tr("Une archive externe est relue et comparée avant de clôturer un lot local. La purge reste explicite. Conserver les lots peut finir par épuiser le budget.", "An external archive is read back and compared before closing a local batch. Cleanup remains explicit. Keeping batches can eventually exhaust the budget."), style =MaterialTheme.typography.bodyMedium)
                        }
                        StudioSection(stringResource(R.string.controls_hf_publication),tr("Destination : ${p.hfDestRepo.ifBlank{"non configurée — export local disponible"}}", "Destination: ${p.hfDestRepo.ifBlank{"not configured — local export available"}}"),Icons.Default.CloudUpload) {
                            ControlField(tr("Branche de destination existante", "Existing destination branch"),policy.destBranch){policy=policy.copy(destBranch=it)}
                            ControlField(tr("Préfixe de publication", "Publication prefix"),policy.destPrefix){policy=policy.copy(destPrefix=it)}
                            ControlField(tr("Split de sortie", "Output split"),split){split=it}
                            ControlSwitch(tr("Ajouter les shards WebDataset", "Add WebDataset shards"),policy.hfWebDataset){policy=policy.copy(hfWebDataset=it)}
                            ControlSwitch(tr("Ajouter la projection COCO", "Add COCO projection"),policy.hfCoco){policy=policy.copy(hfCoco=it)}
                            ControlSwitch(tr("Ajouter la projection YOLO", "Add YOLO projection"),policy.hfYolo){policy=policy.copy(hfYolo=it)}
                            ControlSwitch(tr("Ajouter les instructions vision-language", "Add vision-language instructions"),policy.hfVl){policy=policy.copy(hfVl=it)}
                            StudioDetails(tr("Images et JSONL canonique restent obligatoires. Les fichiers sont isolés par projet et lot sous le préfixe. Aucun fichier du dépôt source n’est supprimé; pas de miroir destructif ni de suppression distante.", "Images and canonical JSONL are required. Files are isolated by project and batch under the prefix. Source repository files are never deleted; no destructive mirroring or remote deletion."), style =MaterialTheme.typography.bodyMedium)
                            TextButton(onClick={vm.navigateTo(Screen.Publication)},enabled=!busy){Text(stringResource(R.string.controls_open_exports))}
                        }
                        SaveControlsButton(busy) {
                            val batch=batchText.toIntOrNull();val mb=budget.toLongOrNull()
                            if(batch==null || mb==null)vm.reportError(tr("Taille du lot ou budget invalide", "Invalid batch size or budget")) else vm.saveProcessingSettings(policy.copy(batchSize=batch),mb,idColumn,split)
                        }
                    }
                    3 -> {
                        StudioSection(stringResource(R.string.controls_model_library),tr("Le sélecteur principal est maintenant séparé des réglages avancés : catalogue UWD réellement disponible, modèles installés et import manuel.", "The main selector is separate from advanced settings: available UWD catalog, installed models and manual import."),Icons.Default.Memory) {
                            Button(onClick={vm.navigateTo(Screen.Models)},enabled=!busy){Text(stringResource(R.string.controls_open_models))}
                            Text(tr("Cette page conserve les outils avancés de contrat, diagnostic et correction adaptative.", "This page keeps advanced contract, diagnostic and adaptive correction tools."),style=MaterialTheme.typography.bodySmall)
                        }
                        StudioSection(stringResource(R.string.controls_public_catalog),tr("Profils TensorFlow avec métadonnées. Les tenseurs, labels et normalisations sont vérifiés à l’import; un essai sur image reste nécessaire. Aucun poids n’est inclus dans l’APK.", "TensorFlow profiles with metadata. Tensors, labels and normalization are checked on import; an image trial is still required. No weights are bundled in the APK."),Icons.Default.Download) {
                            PublicModelCatalog.entries.forEach { item ->
                                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(item.title,style=MaterialTheme.typography.titleSmall)
                                    Text(item.purpose,style=MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick={catalogChoice=item},enabled=!busy) { Text(stringResource(R.string.controls_download_inspect)) }
                                }
                            }
                        }
                        StudioSection(stringResource(R.string.controls_model_library),tr("Poids locaux et profils d’appel sont sélectionnés explicitement. Importer des poids ne lance ni inférence ni validation.", "Local weights and call profiles are selected explicitly. Importing weights starts neither inference nor approval."),Icons.Default.Memory) {
                            Text(tr("Poids actifs : ${p.modelPath?.substringAfterLast('/') ?: "aucun"}", "Active weights: ${p.modelPath?.substringAfterLast('/') ?: "none"}"),style=MaterialTheme.typography.labelLarge)
                            Button(onClick={weights.launch(arrayOf("application/octet-stream","*/*"))},enabled=!busy){Text(stringResource(R.string.controls_import_tflite))}
                            ControlField(tr("URL HTTPS directe des poids", "Direct HTTPS weights URL"),modelUrl){modelUrl=it}
                            OutlinedButton(onClick={vm.importModelUrl(modelUrl)},enabled=!busy && modelUrl.startsWith("https://")){Text(stringResource(R.string.controls_download_weights))}
                            OutlinedButton(onClick=vm::detachModel,enabled=!busy){Text(stringResource(R.string.controls_detach_model))}
                            models.forEach { profile->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                OutlinedButton(onClick={vm.selectModelProfile(profile.id)},enabled=!busy,modifier=Modifier.weight(1f)){Text(profile.name)}
                                IconButton(onClick={removeProfile=profile.id},enabled=!busy){Icon(Icons.Default.DeleteOutline,tr("Supprimer le profil ${profile.name}", "Delete profile ${profile.name}"))}
                            } }
                            ControlField(tr("Nom pour sauvegarder ce profil", "Name to save this profile"),modelName){modelName=it}
                            OutlinedButton(onClick={vm.saveActiveModelProfile(modelName)},enabled=!busy && modelName.isNotBlank()){Text(stringResource(R.string.controls_keep_profile))}
                        }
                        StudioSection(stringResource(R.string.controls_contract_preprocessing),tr("Choisissez un gabarit, puis adaptez-le aux véritables tenseurs du modèle. Un nom de famille de modèles ne garantit pas la compatibilité.", "Choose a template and adapt it to the model's actual tensors. A model family name does not guarantee compatibility."),Icons.Default.Tune) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ModelPresets.names.forEach{(id,title)->AssistChip(onClick={contract=configAdapter.toJson(ModelPresets.create(id,p.classesCsv.split(',').map(String::trim)));contractError=null},label={Text(title)},enabled=!busy)}}
                            StudioDetails(tr("Entrées : NHWC/NCHW, RGB/BGR/gris, FLOAT32/UINT8/INT8, normalisation par canal, stretch/letterbox/crop. Sorties : index, layout, coordonnées, activation, seuil, NMS, points issus de boîtes et comptage proposé.", "Inputs: NHWC/NCHW, RGB/BGR/grayscale, FLOAT32/UINT8/INT8, per-channel normalization, stretch/letterbox/crop. Outputs: indices, layout, coordinates, activation, threshold, NMS, box-derived points and proposed counts."), style =MaterialTheme.typography.bodySmall)
                            OutlinedTextField(contract,{contract=it;contractError=null},label={Text(stringResource(R.string.controls_versioned_contract))},modifier=Modifier.fillMaxWidth().heightIn(min=240.dp,max=500.dp),textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),isError=contractError!=null)
                            contractError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                            Button(onClick={
                                try { val c=configAdapter.failOnUnknown().fromJson(contract) ?: error(tr("JSON vide", "Empty JSON"));ModelContract.validate(c);vm.saveModelConfig(contract) }
                                catch(e:Exception){contractError=e.message ?: tr("Contrat invalide", "Invalid contract")}
                            },enabled=!busy){Text(stringResource(R.string.controls_validate_contract))}
                            StudioDetails(tr("Le runtime embarqué utilise Interpreter CPU. Le mode local_http contacte uniquement localhost / 127.0.0.1; son serveur et son modèle doivent déjà fonctionner sur l’appareil. Pas de VLM embarqué ni de GPU/NPU simulé.", "The bundled runtime uses CPU Interpreter. local_http only contacts localhost / 127.0.0.1; its server and model must already run on the device. No bundled VLM or simulated GPU/NPU."), style =MaterialTheme.typography.bodySmall)
                        }
                        StudioSection(stringResource(R.string.controls_dry_run),tr("Utilise la première image disponible du lot, ou l’image active. La durée affichée est celle de cet essai, pas un benchmark garanti.", "Uses the first available image in the batch or the active image. The displayed duration is one trial, not a guaranteed benchmark."),Icons.Default.Science) {
                            Button(onClick=vm::dryRunActiveModel,enabled=!busy){Text(stringResource(R.string.controls_test_image))}
                            if(diagnostics.isNotBlank()) SelectionContainer { Text(diagnostics,style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
                            dryRun?.let{result->
                                Text(if(result.success)tr("${result.backend} · ${result.latencyMs} ms · ${result.proposals.size} sorties", "${result.backend} · ${result.latencyMs} ms · ${result.proposals.size} outputs") else tr("Échec : ${result.error}", "Failed: ${result.error}"),color=if(result.success)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                                result.proposals.take(8).forEach{Text("${it.type} · ${it.label} · score ${it.score}",style=MaterialTheme.typography.bodySmall)}
                            }
                            OutlinedButton(onClick={vm.preannotateActiveBatch()},enabled=!busy){Text(stringResource(R.string.controls_preannotate_pending))}
                            OutlinedButton(onClick=vm::refreshInferenceReceipts,enabled=!busy){Text(stringResource(R.string.controls_show_receipts))}
                            if(receipts.isNotBlank())SelectionContainer{Text(receipts,style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))}
                        }
                        StudioSection(stringResource(R.string.controls_measure_device),tr("Trois passages de chauffe, puis plusieurs essais sur la même image. Les annotations restent intactes. La mesure de mémoire concerne ce processus, pas un serveur HTTP distinct.", "Three warmup runs, then repeated trials on the same image. Annotations remain intact. Memory measurements cover this process, not a separate HTTP server."),Icons.Default.Speed) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(5,10,30).forEach{n ->
                                FilterChip(selected=benchmarkRuns==n,onClick={benchmarkRuns=n},label={Text(tr("$n essais", "$n trials"))},enabled=!busy)
                            } }
                            Button(onClick={vm.benchmarkActiveModel(benchmarkRuns)},enabled=!busy){Text(stringResource(R.string.controls_measure))}
                            benchmark?.let { report ->
                                OutlinedButton(onClick={benchmarkOut.launch("studio-device-benchmark.json")},enabled=!busy){Text(stringResource(R.string.controls_export_metrics))}
                                SelectionContainer { Text(report.take(14000),style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
                            }
                        }
                        StudioSection(stringResource(R.string.controls_adaptive_points),tr("Option locale, indépendante du modèle visuel. Seuls les déplacements humains explicites et validés sont éligibles.", "Local option, independent of the visual model. Only explicit, approved human movements are eligible."),Icons.Default.Adjust) {
                            ControlSwitch(tr("Appliquer le correcteur lors des prochaines préannotations", "Apply the corrector to future preannotations"),policy.adaptiveCorrection){policy=policy.copy(adaptiveCorrection=it)}
                            Button(onClick={vm.saveProcessingSettings(policy.copy(batchSize=batchText.toIntOrNull() ?: -1),budget.toLongOrNull() ?: -1,idColumn,split)},enabled=!busy){Text(stringResource(R.string.controls_save_corrector))}
                            OutlinedButton(onClick=vm::trainCorrectionsFromBatch,enabled=!busy){Text(stringResource(R.string.controls_train_corrections))}
                            TextButton(onClick=vm::inspectCorrections,enabled=!busy){Text(stringResource(R.string.controls_inspect_corrections))}
                            Text(correctionReport,style=MaterialTheme.typography.bodySmall)
                            StudioDetails(tr("Séparation par hash d’image, 32 images d’apprentissage et 8 de contrôle au minimum par contexte modèle/classe. Une nouvelle tête doit améliorer le contrôle d’au moins 5 %; déplacement limité à ±8 %. Ce contrôle réutilisé ne prouve pas une généralisation sur un nouveau corpus.", "Split by image hash, with at least 32 training and 8 validation images per model/class context. A new head must improve validation by at least 5%; movement is limited to ±8%. This reused validation set does not establish generalization to a new corpus."), style =MaterialTheme.typography.bodySmall)
                            TextButton(onClick={confirmReset=true},enabled=!busy){Text(stringResource(R.string.controls_reset_corrections))}
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    catalogChoice?.let { entry -> AlertDialog(onDismissRequest={catalogChoice=null},title={Text(stringResource(R.string.controls_download_title,entry.title))},text={Text(stringResource(R.string.controls_download_body))},confirmButton={TextButton(onClick={catalogChoice=null;vm.downloadCatalogModel(entry.id)}){Text(stringResource(R.string.common_download))}},dismissButton={TextButton(onClick={catalogChoice=null}){Text(stringResource(R.string.common_cancel))}}) }
    removeProfile?.let{id->AlertDialog(onDismissRequest={removeProfile=null},title={Text(stringResource(R.string.controls_delete_profile_title))},text={Text(stringResource(R.string.controls_delete_profile_body))},confirmButton={TextButton(onClick={removeProfile=null;vm.removeModelProfile(id)}){Text(stringResource(R.string.common_delete))}},dismissButton={TextButton(onClick={removeProfile=null}){Text(stringResource(R.string.common_keep))}})}
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text(stringResource(R.string.controls_clear_corrector_title))},text={Text(stringResource(R.string.controls_clear_corrector_body))},confirmButton={TextButton(onClick={confirmReset=false;vm.resetCorrections()}){Text(stringResource(R.string.common_reset))}},dismissButton={TextButton(onClick={confirmReset=false}){Text(stringResource(R.string.common_keep))}})
    destructiveAction?.let { action ->
        val deleting=action in setOf("delete","discard")
        AlertDialog(onDismissRequest={destructiveAction=null},title={Text(when(action){"discard"->tr("Supprimer ce lot ?", "Delete this batch?");"batch"->tr("Réinitialiser ce lot ?", "Reset this batch?");"project"->tr("Réinitialiser ce projet ?", "Reset this project?");else->tr("Supprimer définitivement ce projet local ?", "Permanently delete this local project?")})},
            text={Text(when(action){
                "discard"->tr("Les images et annotations locales de ce lot seront effacées. L’historique anti-doublons et le curseur sont conservés : ces images ne seront pas réimportées dans le lot suivant. Aucune donnée distante ne sera touchée.", "Local images and annotations in this batch will be deleted. Duplicate history and the cursor are preserved: these images will not be imported again in the next batch. Remote data is untouched.")
                "batch"->tr("Cette remise à zéro oublie les identités du lot et autorise sa réimportation. Annotations et fichiers locaux du lot courant seront effacés. Le projet, sa source, ses classes et son modèle restent configurés. Aucune publication distante ne sera touchée.", "This reset forgets the batch's identities and allows reimporting it. Local batch annotations and files will be erased. The project, source, classes and model remain configured. Remote publications are untouched.")
                "project"->tr("Lots, annotations, curseurs, index, exports locaux, embeddings et apprentissages propres au projet seront effacés. La configuration et les modèles partagés seront conservés. Aucune donnée distante ne sera supprimée.", "Batches, annotations, cursors, index, local exports, embeddings and project-specific training will be erased. Configuration and shared models are preserved. Remote data is not deleted.")
                else->tr("Toutes les données locales de ce projet seront effacées. Les profils et poids partagés restent disponibles. Les dépôts Hugging Face ne seront jamais supprimés.", "All local data in this project will be erased. Shared profiles and weights remain available. Hugging Face repositories are never deleted.")
            })},confirmButton={Button(colors=if(deleting)ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),onClick={
                destructiveAction=null;when(action){"discard"->vm.discardCurrentBatch();"batch"->vm.resetCurrentBatch();"project"->vm.resetCurrentProject();else->vm.deleteCurrentProject()}
            }){Text(if(deleting)tr("Supprimer localement", "Delete locally") else tr("Confirmer", "Confirm"))}},dismissButton={TextButton(onClick={destructiveAction=null}){Text(stringResource(R.string.common_cancel))}})
    }
}

@Composable
private fun ControlField(label:String,value:String,numeric:Boolean=false,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Number else KeyboardType.Text))
}
@Composable
private fun ControlInt(label:String,value:Int,onChange:(Int)->Unit) {
    var text by remember{mutableStateOf(value.toString())}
    ControlField(label,text,true){text=it;onChange(it.toIntOrNull() ?: -1)}
}
@Composable
private fun ControlSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Switch(checked=value,onCheckedChange=onChange) }
}
@Composable
private fun SaveControlsButton(busy:Boolean,onClick:()->Unit) { Button(onClick=onClick,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(stringResource(R.string.controls_save_settings))} }
