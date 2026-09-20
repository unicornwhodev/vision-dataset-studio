package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson

@Composable
fun TrainingScreen(vm:MainViewModel) {
    val project by vm.projectFlow.collectAsState();val run by vm.trainingRun.collectAsState();val busy by vm.isBusy.collectAsState()
    val number by vm.activeBatchNumber.collectAsState();val batches by vm.batches.collectAsState()
    val eligible=batches.any{it.batchNumber==number && it.status=="VERIFIED" && it.verificationKind in setOf("local","hf","both")}
    val p=project ?: return
    val config=remember(p.modelConfigJson){runCatching{p.modelConfigJson?.let{StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(it)}}.getOrNull()}
    var epochs by rememberSaveable{mutableStateOf(3)}
    val active=run?.phase in setOf("queued","training","evaluating")
    Scaffold(contentWindowInsets=WindowInsets(0),topBar={StudioTopBar("Apprentissage","Sur cet appareil",onBack={vm.navigateTo(Screen.Models)})}) { inset ->
        Box(Modifier.fillMaxSize().padding(inset),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.PhonelinkSetup,null,Modifier.size(24.dp));Spacer(Modifier.width(12.dp));Column{
                    Text(if(config?.training!=null)"Modèle entraînable" else "Conversion d’inférence",style=MaterialTheme.typography.titleMedium)
                    Text(if(config?.training!=null)"${config.training.scope} · poids persistants" else "Les signatures d’apprentissage ne sont pas encore disponibles.",style=MaterialTheme.typography.bodySmall)
                }}
                Text("Lot exporté $number uniquement. Nettoyage après apprentissage.",style=MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment=Alignment.CenterVertically){Text("Cycles",Modifier.weight(1f));listOf(1,3,10).forEach{n->FilterChip(selected=epochs==n,onClick={epochs=n},enabled=!active,label={Text("$n")});Spacer(Modifier.width(6.dp))}}
                StudioAction("Entraîner le lot exporté",{vm.startDeviceTraining(epochs)},icon=Icons.Default.ModelTraining,primary=true,enabled=!busy && !active && eligible && config?.training!=null)
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Apprendre après chaque export",style=MaterialTheme.typography.titleSmall);Text("Option désactivée par défaut. Nouveaux poids activés manuellement.",style=MaterialTheme.typography.bodySmall)};Switch(checked=ProjectSettings.read(p).continuousTraining,onCheckedChange=vm::setContinuousTraining,enabled=!busy && config?.training!=null)}
                run?.let { state ->
                    HorizontalDivider()
                    Text(when(state.phase){"queued"->"En attente";"training"->"Apprentissage";"evaluating"->"Contrôle";"completed"->"Candidat prêt";"rejected"->"Modèle précédent conservé";"cancelled"->"Interrompu";else->"Échec"},style=MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress={if(state.totalSteps>0)state.completedSteps.toFloat()/state.totalSteps else 0f},modifier=Modifier.fillMaxWidth())
                    Text("${state.completedSteps} / ${state.totalSteps} étapes · ${state.samples.count{!it.validation}} apprentissage / ${state.samples.count{it.validation}} contrôle",style=MaterialTheme.typography.bodySmall)
                    if(state.validationLoss!=null)Text("Perte de contrôle : %.5f → %.5f".format(state.initialLoss,state.validationLoss),style=MaterialTheme.typography.bodySmall)
                    if(state.initialWeightProbe!=null && state.finalWeightProbe!=null)Text(if(state.initialWeightProbe!=state.finalWeightProbe)"Poids internes modifiés" else "Poids internes inchangés",style=MaterialTheme.typography.bodySmall)
                    state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                    if(active)StudioAction("Interrompre",vm::cancelDeviceTraining,icon=Icons.Default.Stop)
                    if(state.phase in setOf("failed","cancelled"))StudioAction("Reprendre",vm::resumeDeviceTraining,enabled=!busy)
                    if(state.phase=="completed")StudioAction("Activer les poids appris",vm::activateTrainedModel,icon=Icons.Default.Check,primary=true,enabled=!busy)
                }
                StudioDisclosure("Détails") {
                    Text("32 images d’apprentissage et 8 de contrôle au minimum. Le partage dépend du hash des fichiers ; les fichiers identiques ne traversent pas les deux groupes. Toutes les images acceptées du lot exporté sont utilisées, avec une copie privée conservée jusqu’à la fin de l’apprentissage et au nettoyage.",style=MaterialTheme.typography.bodySmall)
                    Text("L’exécution utilise les signatures train / infer / save / restore du modèle LiteRT. Aucun corpus ni gradient n’est envoyé au pod. Le contrôle réutilisé ne mesure pas la généralisation à un corpus indépendant.",style=MaterialTheme.typography.bodySmall)
                    StudioAction("Contrat du modèle",{vm.navigateTo(Screen.Controls)},icon=Icons.Default.Code)
                }
            }
        }
    }
}
