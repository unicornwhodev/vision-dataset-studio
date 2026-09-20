package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowTools
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.Screen
import com.unicornwhodev.visiondatasetstudio.ui.components.*

@Composable
fun WorkflowScreen(vm:MainViewModel) {
    val run by vm.workflow.collectAsState();val choice by vm.agentChoice.collectAsState();val busy by vm.isBusy.collectAsState()
    val project by vm.activeProjectId.collectAsState();val batch by vm.activeBatchNumber.collectAsState()
    var selected by rememberSaveable { mutableStateOf("assisted") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("http://127.0.0.1:8080/plan") }
    LaunchedEffect(project,batch) { vm.loadWorkflow() }
    Scaffold(contentWindowInsets=WindowInsets(0),topBar={StudioTopBar("Workflow","Lot $batch",onBack={vm.back()})}) { inset ->
        Box(Modifier.fillMaxSize().padding(inset),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                WorkflowTools.templates.forEach { template ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected==template.id,{selected=template.id},enabled=!busy)
                        Text(template.title,style=MaterialTheme.typography.titleSmall)
                    }
                }
                OutlinedTextField(instructions,{instructions=it.take(8000)},label={Text("Consignes de l’agent · facultatif")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
                StudioAction(if(run==null)"Préparer" else "Repartir du template",{vm.startWorkflow(selected,instructions)},enabled=!busy,icon=Icons.Default.AccountTree)
                run?.takeIf { it.projectId==project && it.batchNumber==batch }?.let { state ->
                    HorizontalDivider()
                    val template=WorkflowTools.template(state.template)
                    template.steps.forEachIndexed { index,step ->
                        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Icon(if(index<state.cursor)Icons.Default.CheckCircle else if(index==state.cursor)Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,null,Modifier.size(18.dp),tint=if(index<=state.cursor)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(WorkflowTools.labels.getValue(step),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if(state.message.isNotBlank())Text(state.message,style=MaterialTheme.typography.bodySmall,color=if(state.phase=="failed")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if(state.phase!="completed")StudioAction("Continuer",vm::resumeWorkflow,primary=true,enabled=!busy,icon=Icons.Default.PlayArrow)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={vm.navigateTo(Screen.BatchGrid)},enabled=!busy) { Text("Corriger") }
                        TextButton(onClick={vm.navigateTo(Screen.Publication)},enabled=!busy) { Text("Export") }
                        TextButton(onClick={vm.navigateTo(Screen.Training)},enabled=!busy) { Text("Apprentissage") }
                    }
                }
                StudioDisclosure("Agent local facultatif",Icons.Default.SmartToy) {
                    OutlinedTextField(endpoint,{endpoint=it},label={Text("Serveur sur cet appareil")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Text("Envoie uniquement les consignes et les nombres de cas. Le serveur est fourni séparément.",style=MaterialTheme.typography.bodySmall)
                    StudioAction("Proposer un workflow",{vm.askLocalWorkflowAgent(endpoint,instructions)},enabled=!busy)
                    choice?.let { plan ->
                        Text(plan.reason,style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={selected=plan.template;vm.startWorkflow(plan.template,instructions)},enabled=!busy) { Text("Utiliser ${WorkflowTools.template(plan.template).title}") }
                    }
                    Text("Prompt ${WorkflowTools.PROMPT_VERSION}",style=MaterialTheme.typography.labelSmall)
                    Text(WorkflowTools.systemPrompt,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
