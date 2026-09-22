package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

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
    val project by vm.projectFlow.collectAsState();val run by vm.trainingRun.collectAsState();val busy by vm.isBusy.collectAsState();val preflight by vm.trainingPreflight.collectAsState()
    val number by vm.activeBatchNumber.collectAsState();val batches by vm.batches.collectAsState()
    val eligible=batches.any{it.batchNumber==number && it.status=="VERIFIED" && it.verificationKind in setOf("local","hf","both")}
    val p=project ?: return
    val config=remember(p.modelConfigJson){runCatching{p.modelConfigJson?.let{StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(it)}}.getOrNull()}
    var epochs by rememberSaveable{mutableStateOf(3)}
    var abandonRunId by remember(p.id) { mutableStateOf<String?>(null) }
    val active=run?.phase in setOf("queued","training","evaluating")
    LaunchedEffect(p.id,p.modelConfigJson,number,run?.phase){vm.refreshTrainingPreflight()}
    Scaffold(contentWindowInsets=WindowInsets(0),topBar={StudioTopBar(stringResource(R.string.screen_training),stringResource(R.string.subtitle_on_device),onBack={vm.navigateTo(Screen.Models)})}) { inset ->
        Box(Modifier.fillMaxSize().padding(inset),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.PhonelinkSetup,null,Modifier.size(24.dp));Spacer(Modifier.width(12.dp));Column{
                    Text(stringResource(if(config?.training!=null)R.string.training_trainable else R.string.training_inference_only),style=MaterialTheme.typography.titleMedium)
                    Text(when(config?.training?.scope) {
                        "classification_head_only","pretrained_classification_head_only" -> stringResource(R.string.training_scope_classification)
                        "candidate_classification_and_box_adaptation_only" -> stringResource(R.string.training_scope_detection)
                        "heatmap_channel_mixing_head_only" -> stringResource(R.string.training_scope_points)
                        "four_task_output_adaptation_only" -> stringResource(R.string.training_scope_multitask)
                        "internal_visual_and_output_layers" -> stringResource(R.string.training_scope_internal)
                        null -> stringResource(R.string.training_scope_missing)
                        else -> stringResource(R.string.training_scope_converter)
                    },style=MaterialTheme.typography.bodySmall)
                }}
                if(config?.training!=null) {
                Text(stringResource(R.string.training_batch_only,number),style=MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.training_epochs),Modifier.weight(1f));listOf(1,3,10).forEach{n->FilterChip(selected=epochs==n,onClick={epochs=n},enabled=!active && config?.training!=null,label={Text("$n")});Spacer(Modifier.width(6.dp))}}
                StudioAction(stringResource(R.string.training_start),{vm.startDeviceTraining(epochs)},icon=Icons.Default.ModelTraining,primary=true,enabled=!busy && !active && preflight?.canStart==true)
                preflight?.let { state ->
                    Text(stringResource(if(state.canStart)R.string.training_ready else R.string.training_blocked),style=MaterialTheme.typography.titleSmall,color=if(state.canStart)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    state.checks.forEach { check -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
                        Icon(if(check.passed)Icons.Default.CheckCircle else Icons.Default.Cancel,null,Modifier.size(17.dp),tint=if(check.passed)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Column { Text(check.label,style=MaterialTheme.typography.labelMedium);Text(check.detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    } }
                    state.error?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
                }
                }
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(stringResource(R.string.training_continuous),style=MaterialTheme.typography.titleSmall);Text(stringResource(R.string.training_continuous_help),style=MaterialTheme.typography.bodySmall)};Switch(checked=ProjectSettings.read(p).continuousTraining,onCheckedChange=vm::setContinuousTraining,enabled=!busy && (config?.training!=null || ProjectSettings.read(p).continuousTraining))}
                run?.let { state ->
                    HorizontalDivider()
                    Text(stringResource(when(state.phase){"queued"->R.string.training_phase_queued;"training"->R.string.training_phase_training;"evaluating"->R.string.training_phase_evaluating;"completed"->R.string.training_phase_completed;"rejected"->R.string.training_phase_rejected;"cancelled"->R.string.training_phase_cancelled;"abandoned"->R.string.training_phase_abandoned;else->R.string.training_phase_failed}),style=MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress={if(state.totalSteps>0)state.completedSteps.toFloat()/state.totalSteps else 0f},modifier=Modifier.fillMaxWidth())
                    Text(stringResource(R.string.training_progress,state.completedSteps,state.totalSteps,state.samples.count{!it.validation},state.samples.count{it.validation}),style=MaterialTheme.typography.bodySmall)
                    if(state.validationLoss!=null)Text(stringResource(R.string.training_loss,state.initialLoss ?: 0.0,state.validationLoss),style=MaterialTheme.typography.bodySmall)
                    if(state.initialWeightProbe!=null && state.finalWeightProbe!=null)Text(stringResource(if(state.initialWeightProbe!=state.finalWeightProbe)R.string.training_weights_changed else R.string.training_weights_unchanged),style=MaterialTheme.typography.bodySmall)
                    state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                    if(active)StudioAction(stringResource(R.string.training_stop),vm::cancelDeviceTraining,icon=Icons.Default.Stop)
                    if(state.phase in setOf("failed","cancelled")) {
                        StudioAction(stringResource(R.string.training_resume),vm::resumeDeviceTraining,enabled=!busy)
                        StudioAction(stringResource(R.string.training_abandon),{abandonRunId=state.id},icon=Icons.Default.Close,enabled=!busy)
                    }
                    if(state.phase=="completed")StudioAction(stringResource(R.string.training_activate),vm::activateTrainedModel,icon=Icons.Default.Check,primary=true,enabled=!busy)
                }
                StudioDisclosure(stringResource(R.string.training_details)) {
                    Text(stringResource(R.string.training_details_dataset),style=MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.training_details_runtime),style=MaterialTheme.typography.bodySmall)
                    StudioAction(stringResource(R.string.training_contract),{vm.navigateTo(Screen.Controls)},icon=Icons.Default.Code)
                }
            }
        }
    }
    abandonRunId?.let { selected ->
        AlertDialog(onDismissRequest={abandonRunId=null},title={Text(stringResource(R.string.training_abandon))},
            text={Text(stringResource(R.string.training_abandon_help))},
            confirmButton={TextButton(onClick={vm.abandonDeviceTraining(selected);abandonRunId=null},enabled=!busy){Text(stringResource(R.string.training_abandon))}},
            dismissButton={TextButton(onClick={abandonRunId=null}){Text(stringResource(R.string.action_cancel))}})
    }

}
