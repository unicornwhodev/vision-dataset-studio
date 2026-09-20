package com.unicornwhodev.visiondatasetstudio.ui.screens

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
    val dryRun by vm.dryRunResult.collectAsState()
    val benchmark by vm.benchmarkReport.collectAsState()
    var benchmarkRuns by rememberSaveable { mutableStateOf(10) }
    var catalogChoice by remember { mutableStateOf<PublicModelCatalog.Entry?>(null) }
    val correctionReport by vm.correctionReport.collectAsState()
    var removeProfile by remember{mutableStateOf<String?>(null)}
    var confirmReset by remember{mutableStateOf(false)}
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
    Scaffold(contentWindowInsets=WindowInsets(0),modifier=Modifier.imePadding(),topBar={StudioTopBar("Mon espace", p.name, onBack={ if (!busy) vm.back() })}) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            StudioTabs(listOf("Projets", "Sources", "Transferts", "Modèles"), tab, { tab=it }, Modifier.padding(horizontal=16.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                when(tab) {
                    0 -> {
                        StudioSection("Mes projets","Chaque projet conserve sa source, son curseur, ses lots, annotations et preuves de copie. Le token HF et la bibliothèque de modèles restent communs à l’appareil.",Icons.Default.FolderOpen) {
                            allProjects.forEach { item ->
                                OutlinedButton(onClick={vm.selectProject(item.id)},enabled=!busy && item.id!=p.id,modifier=Modifier.fillMaxWidth()) {
                                    Icon(if(item.id==p.id)Icons.Default.CheckCircle else Icons.Default.Folder,null);Spacer(Modifier.width(8.dp));Text(item.name,Modifier.weight(1f))
                                }
                            }
                            OutlinedTextField(newName,{newName=it},label={Text("Nom du nouveau projet")},singleLine=true,modifier=Modifier.fillMaxWidth())
                            Button(onClick={vm.createProject(newName);newName=""},enabled=!busy && newName.isNotBlank()){Text("Créer un projet")}
                            OutlinedButton(onClick={vm.navigateTo(Screen.Setup)},enabled=!busy){Text("Configurer")}
                        }
                        StudioSection("Presets","Un pack configure les tâches, classes, taille de lot et contrat modèle. Il exclut les poids, le jeton HF du coffre, le corpus et ses emplacements. Le contrat, le prompt et le corps JSON personnalisé sont inclus : retirez tout secret avant partage.",Icons.Default.Inventory2) {
                            Button(onClick={packIn.launch(arrayOf("application/json","text/*","application/octet-stream"))},enabled=!busy){Text("Importer un preset")}
                            OutlinedButton(onClick={packOut.launch("studio-preset.json")},enabled=!busy){Text("Exporter le preset")}
                            StudioDetails("Chaque projet peut utiliser son propre pack de tâches et de classes. Sélectionnez séparément les modèles que vous êtes autorisé à utiliser.", style =MaterialTheme.typography.bodyMedium)
                        }
                    }
                    1 -> {
                        StudioSection("Source et sélection","Enregistrez les réglages avant d’indexer. Après le premier lot, changer de source ou de filtre exige un nouveau projet.",Icons.Default.CloudDownload) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("HF_VIEWER" to "HF Viewer","HF_MANIFEST" to "JSONL sur HF","LOCAL_INDEX" to "Local").forEach{(value,label)->
                                    FilterChip(selected=policy.sourceMode==value,onClick={policy=policy.copy(sourceMode=value)},enabled=!busy,label={Text(label)})
                                }
                            }
                            Text("Source HF : ${p.hfSourceRepo.ifBlank { "non configurée" }}",style=MaterialTheme.typography.bodyMedium)
                            TextButton(onClick={vm.navigateTo(Screen.Setup)},enabled=!busy){Text("Configurer la source")}
                            if(policy.sourceMode=="HF_VIEWER") {
                                ControlField("Filtre HF (where), facultatif",policy.filterExpression){policy=policy.copy(filterExpression=it)}
                                ControlField("Ordre HF (orderby), facultatif",policy.orderBy){policy=policy.copy(orderBy=it)}
                                StudioDetails("Le Viewer doit prendre en charge cette source. Les requêtes sont paginées à 100 lignes maximum. Une révision de fichier épinglée n’est pas disponible pour cette voie.", style =MaterialTheme.typography.bodySmall)
                            } else if(policy.sourceMode=="HF_MANIFEST") {
                                ControlField("Révision source : branche ou SHA",policy.sourceRevision){policy=policy.copy(sourceRevision=it)}
                                ControlField("Chemin du JSONL dans le dépôt",policy.manifestPath){policy=policy.copy(manifestPath=it)}
                                Button(onClick=vm::fetchHfManifest,enabled=!busy && stored.sourceMode=="HF_MANIFEST"){Text("Indexer le manifeste")}
                                if(stored.resolvedSourceRevision!=null) SelectionContainer { Text("Révision résolue : ${stored.resolvedSourceRevision}",style=MaterialTheme.typography.bodySmall) }
                            } else {
                                OutlinedButton(onClick={folder.launch(null)},enabled=!busy && stored.sourceMode=="LOCAL_INDEX"){Text("Choisir un dossier")}
                                OutlinedButton(onClick={manifestFolder.launch(null)},enabled=!busy && stored.sourceMode=="LOCAL_INDEX"){Text("Dossier du manifeste")}
                                Button(onClick={manifest.launch(arrayOf("application/json","application/x-ndjson","text/*","application/octet-stream"))},enabled=!busy && stored.sourceMode=="LOCAL_INDEX"){Text("Importer le JSONL")}
                                StudioDetails("Le dossier source n’est jamais modifié. Les chemins relatifs du JSONL sont résolus à partir du dossier choisi; les URL HTTPS sont également acceptées.", style =MaterialTheme.typography.bodySmall)
                            }
                            ControlField("Colonne identifiant",idColumn){idColumn=it}
                            ControlSwitch("Importer les brouillons",policy.importAnnotations){policy=policy.copy(importAnnotations=it)}
                            Text(if(stored.sourceIndexReady)"Index prêt · ${stored.localSourceLabel}" else "Index local non préparé (inutile en mode Viewer)",style=MaterialTheme.typography.labelMedium)
                        }
                        StudioSection("Collaboration","Évite que plusieurs personnes téléchargent et traitent les mêmes cas. Les réservations sont stockées dans le dépôt HF de destination et expirent si un appareil est abandonné.",Icons.Default.Groups) {
                            ControlSwitch("Activer les réservations partagées",policy.collaborationEnabled){ enabled ->
                                if(enabled && (policy.sourceMode=="LOCAL_INDEX" || p.hfDestRepo.isBlank())) vm.reportError("Le travail partagé exige une source HF et un dépôt HF de destination")
                                else policy=policy.copy(collaborationEnabled=enabled)
                            }
                            if(policy.collaborationEnabled) {
                                ControlField("Identifiant local",workerId){workerId=it;policy=policy.copy(collaborationWorkerId=it.trim())}
                                TextButton(onClick={ val id="worker-"+java.util.UUID.randomUUID().toString().take(8);workerId=id;policy=policy.copy(collaborationWorkerId=id) }){Text("Générer")}
                                ControlField("Bail (minutes)",leaseText,true){leaseText=it;policy=policy.copy(claimLeaseMinutes=it.toIntOrNull() ?: -1)}
                                StudioDetails("Au prochain lot, l’app ignore les cas marqués DONE ou CLAIMED par un autre collaborateur avant de télécharger leurs images. Les réservations utilisent un commit parent HF : un conflit force une relecture avant nouvelle tentative.", style =MaterialTheme.typography.bodySmall)
                                Text("Coordination : ${p.hfDestRepo.ifBlank { "destination HF à configurer" }} · branche ${policy.destBranch}",style=MaterialTheme.typography.labelMedium)
                            }
                        }
                        StudioSection("Lots et réseau","La taille du lot d’annotation est indépendante de la pagination HTTP et du nombre d’images inférées simultanément.",Icons.Default.Layers) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(25,50,100,250,500,1000).forEach{n->FilterChip(selected=batchText==n.toString(),onClick={batchText=n.toString()},label={Text("$n")})}}
                            ControlField("Cas par lot · 1 à 1 000",batchText,true){batchText=it}
                            ControlInt("Téléchargements simultanés · 1 à 4",policy.downloadConcurrency){policy=policy.copy(downloadConcurrency=it)}
                            ControlInt("Nouvelles tentatives · 0 à 5",policy.retryCount){policy=policy.copy(retryCount=it)}
                            ControlInt("Délai HTTP, secondes · 10 à 300",policy.timeoutSeconds){policy=policy.copy(timeoutSeconds=it)}
                            ControlSwitch("Autoriser un réseau facturé / mobile",policy.allowMetered){policy=policy.copy(allowMetered=it)}
                            StudioDetails("Le refus d’un réseau facturé est contrôlé avant chaque transfert. Le travail s’effectue au premier plan; aucune exécution de fond permanente n’est promise.", style =MaterialTheme.typography.bodySmall)
                        }
                        SaveControlsButton(busy) {
                            val batch=batchText.toIntOrNull();val mb=budget.toLongOrNull()
                            if(batch==null || mb==null)vm.reportError("Taille du lot ou budget invalide") else vm.saveProcessingSettings(policy.copy(batchSize=batch),mb,idColumn,split)
                        }
                    }
                    2 -> {
                        StudioSection("Stockage borné","Les budgets s’appliquent à l’espace utilisé par cette application, modèles et exports compris. Les sources sélectionnées restent en lecture seule.",Icons.Default.Storage) {
                            ControlField("Budget de l’application, Mio · 128 à 65 536",budget,true){budget=it}
                            ControlInt("Espace libre à conserver, Mio · 32 à 4 096",policy.reserveFreeMb){policy=policy.copy(reserveFreeMb=it)}
                            ControlInt("Taille maximale d’une image, Mio · 1 à 256",policy.maxImageMb){policy=policy.copy(maxImageMb=it)}
                            ControlSwitch("Préannoter les nouvelles images à l’import",policy.autoPreannotate){policy=policy.copy(autoPreannotate=it)}
                            ControlSwitch("Conserver les lots vérifiés pour passer au suivant",policy.keepVerifiedBatches){policy=policy.copy(keepVerifiedBatches=it)}
                            ControlSwitch("Normaliser l’orientation EXIF de la copie cache",policy.normalizeExif){policy=policy.copy(normalizeExif=it)}
                            StudioDetails("La normalisation ne redimensionne pas l’image, mais la réencode; les deux hashes et la transformation sont conservés. Une rotation avec annotations importées ambiguës ou plus de 8 mégapixels est refusée. La copie source reste intacte.", style =MaterialTheme.typography.bodySmall)
                            StudioDetails("Une archive externe est relue et comparée avant de clôturer un lot local. La purge reste explicite. Conserver les lots peut finir par épuiser le budget.", style =MaterialTheme.typography.bodyMedium)
                        }
                        StudioSection("Publication Hugging Face","Destination : ${p.hfDestRepo.ifBlank{"non configurée — export local disponible"}}",Icons.Default.CloudUpload) {
                            ControlField("Branche de destination existante",policy.destBranch){policy=policy.copy(destBranch=it)}
                            ControlField("Préfixe de publication",policy.destPrefix){policy=policy.copy(destPrefix=it)}
                            ControlField("Split de sortie",split){split=it}
                            ControlSwitch("Ajouter les shards WebDataset",policy.hfWebDataset){policy=policy.copy(hfWebDataset=it)}
                            ControlSwitch("Ajouter la projection COCO",policy.hfCoco){policy=policy.copy(hfCoco=it)}
                            ControlSwitch("Ajouter la projection YOLO",policy.hfYolo){policy=policy.copy(hfYolo=it)}
                            ControlSwitch("Ajouter les instructions vision-language",policy.hfVl){policy=policy.copy(hfVl=it)}
                            StudioDetails("Images et JSONL canonique restent obligatoires. Les fichiers sont isolés par projet et lot sous le préfixe. Aucun fichier du dépôt source n’est supprimé; pas de miroir destructif ni de suppression distante.", style =MaterialTheme.typography.bodyMedium)
                            TextButton(onClick={vm.navigateTo(Screen.Publication)},enabled=!busy){Text("Ouvrir les exports et preuves de copie")}
                        }
                        SaveControlsButton(busy) {
                            val batch=batchText.toIntOrNull();val mb=budget.toLongOrNull()
                            if(batch==null || mb==null)vm.reportError("Taille du lot ou budget invalide") else vm.saveProcessingSettings(policy.copy(batchSize=batch),mb,idColumn,split)
                        }
                    }
                    3 -> {
                        StudioSection("Bibliothèque de modèles","Le sélecteur principal est maintenant séparé des réglages avancés : catalogue UWD réellement disponible, modèles installés et import manuel.",Icons.Default.Memory) {
                            Button(onClick={vm.navigateTo(Screen.Models)},enabled=!busy){Text("Ouvrir la bibliothèque de modèles")}
                            Text("Cette page conserve les outils avancés de contrat, diagnostic et correction adaptative.",style=MaterialTheme.typography.bodySmall)
                        }
                        StudioSection("Catalogue public téléchargeable","Profils TensorFlow avec métadonnées. Les tenseurs, labels et normalisations sont vérifiés à l’import; un essai sur image reste nécessaire. Aucun poids n’est inclus dans l’APK.",Icons.Default.Download) {
                            PublicModelCatalog.entries.forEach { item ->
                                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(item.title,style=MaterialTheme.typography.titleSmall)
                                    Text(item.purpose,style=MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick={catalogChoice=item},enabled=!busy) { Text("Télécharger et inspecter…") }
                                }
                            }
                        }
                        StudioSection("Bibliothèque de modèles","Poids locaux et profils d’appel sont sélectionnés explicitement. Importer des poids ne lance ni inférence ni validation.",Icons.Default.Memory) {
                            Text("Poids actifs : ${p.modelPath?.substringAfterLast('/') ?: "aucun"}",style=MaterialTheme.typography.labelLarge)
                            Button(onClick={weights.launch(arrayOf("application/octet-stream","*/*"))},enabled=!busy){Text("Importer un fichier .tflite")}
                            ControlField("URL HTTPS directe des poids",modelUrl){modelUrl=it}
                            OutlinedButton(onClick={vm.importModelUrl(modelUrl)},enabled=!busy && modelUrl.startsWith("https://")){Text("Télécharger ces poids")}
                            OutlinedButton(onClick=vm::detachModel,enabled=!busy){Text("Détacher le modèle de ce projet")}
                            models.forEach { profile->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                OutlinedButton(onClick={vm.selectModelProfile(profile.id)},enabled=!busy,modifier=Modifier.weight(1f)){Text(profile.name)}
                                IconButton(onClick={removeProfile=profile.id},enabled=!busy){Icon(Icons.Default.DeleteOutline,"Supprimer le profil ${profile.name}")}
                            } }
                            ControlField("Nom pour sauvegarder ce profil",modelName){modelName=it}
                            OutlinedButton(onClick={vm.saveActiveModelProfile(modelName)},enabled=!busy && modelName.isNotBlank()){Text("Conserver poids + contrat dans la bibliothèque")}
                        }
                        StudioSection("Contrat et prétraitement","Choisissez un gabarit, puis adaptez-le aux véritables tenseurs du modèle. Un nom de famille de modèles ne garantit pas la compatibilité.",Icons.Default.Tune) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ModelPresets.names.forEach{(id,title)->AssistChip(onClick={contract=configAdapter.toJson(ModelPresets.create(id,p.classesCsv.split(',').map(String::trim)));contractError=null},label={Text(title)},enabled=!busy)}}
                            StudioDetails("Entrées : NHWC/NCHW, RGB/BGR/gris, FLOAT32/UINT8/INT8, normalisation par canal, stretch/letterbox/crop. Sorties : index, layout, coordonnées, activation, seuil, NMS, points issus de boîtes et comptage proposé.", style =MaterialTheme.typography.bodySmall)
                            OutlinedTextField(contract,{contract=it;contractError=null},label={Text("Contrat JSON versionné")},modifier=Modifier.fillMaxWidth().heightIn(min=240.dp,max=500.dp),textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),isError=contractError!=null)
                            contractError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                            Button(onClick={
                                try { val c=configAdapter.failOnUnknown().fromJson(contract) ?: error("JSON vide");ModelContract.validate(c);vm.saveModelConfig(contract) }
                                catch(e:Exception){contractError=e.message ?: "Contrat invalide"}
                            },enabled=!busy){Text("Vérifier et enregistrer le contrat")}
                            StudioDetails("Le runtime embarqué utilise Interpreter CPU. Le mode local_http contacte uniquement localhost / 127.0.0.1; son serveur et son modèle doivent déjà fonctionner sur l’appareil. Pas de VLM embarqué ni de GPU/NPU simulé.", style =MaterialTheme.typography.bodySmall)
                        }
                        StudioSection("Essai sans modifier les annotations","Utilise la première image disponible du lot, ou l’image active. La durée affichée est celle de cet essai, pas un benchmark garanti.",Icons.Default.Science) {
                            Button(onClick=vm::dryRunActiveModel,enabled=!busy){Text("Tester le modèle enregistré sur une image")}
                            if(diagnostics.isNotBlank()) SelectionContainer { Text(diagnostics,style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
                            dryRun?.let{result->
                                Text(if(result.success)"${result.backend} · ${result.latencyMs} ms · ${result.proposals.size} sorties" else "Échec : ${result.error}",color=if(result.success)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                                result.proposals.take(8).forEach{Text("${it.type} · ${it.label} · score ${it.score}",style=MaterialTheme.typography.bodySmall)}
                            }
                            OutlinedButton(onClick={vm.preannotateActiveBatch()},enabled=!busy){Text("Préannoter les cas en attente du lot")}
                        }
                        StudioSection("Mesurer sur cet appareil","Trois passages de chauffe, puis plusieurs essais sur la même image. Les annotations restent intactes. La mesure de mémoire concerne ce processus, pas un serveur HTTP distinct.",Icons.Default.Speed) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(5,10,30).forEach{n ->
                                FilterChip(selected=benchmarkRuns==n,onClick={benchmarkRuns=n},label={Text("$n essais")},enabled=!busy)
                            } }
                            Button(onClick={vm.benchmarkActiveModel(benchmarkRuns)},enabled=!busy){Text("Mesurer temps et RAM")}
                            benchmark?.let { report ->
                                OutlinedButton(onClick={benchmarkOut.launch("studio-device-benchmark.json")},enabled=!busy){Text("Exporter les mesures JSON")}
                                SelectionContainer { Text(report.take(14000),style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
                            }
                        }
                        StudioSection("Correction adaptative des points","Option locale, indépendante du modèle visuel. Seuls les déplacements humains explicites et validés sont éligibles.",Icons.Default.Adjust) {
                            ControlSwitch("Appliquer le correcteur lors des prochaines préannotations",policy.adaptiveCorrection){policy=policy.copy(adaptiveCorrection=it)}
                            Button(onClick={vm.saveProcessingSettings(policy.copy(batchSize=batchText.toIntOrNull() ?: -1),budget.toLongOrNull() ?: -1,idColumn,split)},enabled=!busy){Text("Enregistrer l’activation du correcteur")}
                            OutlinedButton(onClick=vm::trainCorrectionsFromBatch,enabled=!busy){Text("Apprendre les corrections du lot terminé")}
                            TextButton(onClick=vm::inspectCorrections,enabled=!busy){Text("Lire l’état des correcteurs du projet")}
                            Text(correctionReport,style=MaterialTheme.typography.bodySmall)
                            StudioDetails("Séparation par hash d’image, 32 images d’apprentissage et 8 de contrôle au minimum par contexte modèle/classe. Une nouvelle tête doit améliorer le contrôle d’au moins 5 %; déplacement limité à ±8 %. Ce contrôle réutilisé ne prouve pas une généralisation sur un nouveau corpus.", style =MaterialTheme.typography.bodySmall)
                            TextButton(onClick={confirmReset=true},enabled=!busy){Text("Réinitialiser les corrections apprises…")}
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    catalogChoice?.let { entry -> AlertDialog(onDismissRequest={catalogChoice=null},title={Text("Télécharger ${entry.title} ?")},text={Text("Téléchargement depuis le stockage public TensorFlow, sans token HF. Réserve maximale : 64 Mo. Les labels et métadonnées sont lus dans les poids. Une incompatibilité bloque l’import au lieu de deviner le contrat. Vérifiez les conditions du modèle avant redistribution. L’ajout ne remplace pas le modèle actif.")},confirmButton={TextButton(onClick={catalogChoice=null;vm.downloadCatalogModel(entry.id)}){Text("Télécharger")}},dismissButton={TextButton(onClick={catalogChoice=null}){Text("Annuler")}}) }
    removeProfile?.let{id->AlertDialog(onDismissRequest={removeProfile=null},title={Text("Supprimer ce profil ?")},text={Text("Ses poids seront supprimés seulement lorsqu’aucun autre profil ni projet ne les utilise. Les annotations et correcteurs ne seront pas supprimés.")},confirmButton={TextButton(onClick={removeProfile=null;vm.removeModelProfile(id)}){Text("Supprimer")}},dismissButton={TextButton(onClick={removeProfile=null}){Text("Conserver")}})}
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text("Effacer le correcteur local ?")},text={Text("Les exemples numériques et têtes de correction de ce projet seront effacés. Les annotations, images et poids du modèle restent intacts.")},confirmButton={TextButton(onClick={confirmReset=false;vm.resetCorrections()}){Text("Réinitialiser")}},dismissButton={TextButton(onClick={confirmReset=false}){Text("Conserver")}})
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
private fun SaveControlsButton(busy:Boolean,onClick:()->Unit) { Button(onClick=onClick,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Enregistrer ces réglages")} }
