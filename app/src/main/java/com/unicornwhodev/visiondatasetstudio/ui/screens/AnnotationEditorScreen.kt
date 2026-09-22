package com.unicornwhodev.visiondatasetstudio.ui.screens

import com.unicornwhodev.visiondatasetstudio.R

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unicornwhodev.visiondatasetstudio.core.geometry.ImageViewport
import com.unicornwhodev.visiondatasetstudio.core.geometry.ViewPoint
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelAction
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelCapabilities
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig
import com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec
import com.unicornwhodev.visiondatasetstudio.ui.*
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

/** Compact visible controls with a full 48 dp touch target. */
@Composable
private fun EditorCommand(icon: ImageVector, description: String, onClick: () -> Unit,
                          modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean? = null, accent: Boolean = false) {
    val background by animateColorAsState(when { !enabled -> Color.Transparent; accent -> MaterialTheme.colorScheme.primary; selected == true -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent }, label = "editor command")
    val tint = when { !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f); accent -> MaterialTheme.colorScheme.onPrimary; selected == true -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp).semantics { if (selected != null) this.selected = selected }) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(20.dp), tint = tint)
        }
    }
}

/** Selection and creation are separate tools: moving an existing target never creates another. */
enum class EditorTool { SELECT, BOX, POINT, MASK, ERASE, POLYGON, LASSO, FILL, SAM_POINT, PAN_ZOOM }
private enum class EditorTab(val title: String) { REGIONS("Régions"), CAPTION("Légendes"), TAGS("Tags"), GROUNDING("Texte ↔ région"), VQA("VQA"), COUNTING("Comptage"), QUALITY("Qualité") }
private fun enabledTabs(tasks: Set<StudioTask>): List<EditorTab> = buildList {
    if (tasks.any { it in setOf(StudioTask.POINTING, StudioTask.POINTING_MULTI, StudioTask.DETECTION, StudioTask.SEGMENTATION, StudioTask.GROUNDING) }) add(EditorTab.REGIONS)
    if (StudioTask.CAPTIONING in tasks) add(EditorTab.CAPTION)
    if (StudioTask.CLASSIFICATION in tasks) add(EditorTab.TAGS)
    if (StudioTask.GROUNDING in tasks) add(EditorTab.GROUNDING)
    if (StudioTask.VQA in tasks) add(EditorTab.VQA)
    if (StudioTask.COUNTING in tasks) add(EditorTab.COUNTING)
    add(EditorTab.QUALITY)
}
private fun newId() = UUID.randomUUID().toString()
private fun withoutTarget(a: SampleAnnotations, id: String) = a.copy(
    masks = a.masks.filterNot { it.id == id }, points = a.points.filterNot { it.id == id }, boxes = a.boxes.filterNot { it.id == id },
    groundings = a.groundings.map { it.copy(boxIds = it.boxIds - id, pointIds = it.pointIds - id) },
    vqaList = a.vqaList.map { it.copy(targetIds = it.targetIds - id) }, counts = a.counts.map { it.copy(linkedInstanceIds = it.linkedInstanceIds - id) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationEditorScreen(sampleId: String, viewModel: MainViewModel) {
    val sample by viewModel.currentSample.collectAsState()
    val a by viewModel.currentAnnotations.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    val samples by viewModel.batchSamples.collectAsState()
    val saving by viewModel.saveState.collectAsState()
    val canUndo by viewModel.undoAvailable.collectAsState()
    val canRedo by viewModel.redoAvailable.collectAsState()
    val issues by viewModel.editorIssues.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val editing by viewModel.editorBusy.collectAsState()
    val tasks = remember(project?.activeTasksCsv) { StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION") }
    val tabs = remember(tasks) { enabledTabs(tasks) }
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    LaunchedEffect(tabs) { if (tab !in tabs) tab = tabs.first() }
    var tool by rememberSaveable { mutableStateOf(EditorTool.SELECT) }
    val classes = remember(project?.classesCsv) { project?.classesCsv?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty().ifEmpty { listOf("object") } }
    var label by rememberSaveable { mutableStateOf(classes.first()) }
    LaunchedEffect(classes) { if (label !in classes) label = classes.first() }
    var selected by remember(sampleId) { mutableStateOf<String?>(null) }
    var copiedBox by remember { mutableStateOf<BoxTarget?>(null) }
    var copiedPoint by remember { mutableStateOf<PointTarget?>(null) }
    var brushSize by rememberSaveable { mutableFloatStateOf(.025f) }
    var eraserSize by rememberSaveable { mutableFloatStateOf(.025f) }
    var samPoint by remember(sampleId) { mutableStateOf<ViewPoint?>(null) }
    val samModel = project?.modelConfigJson?.contains("efficientvit_sam") == true
    var zoom by remember(sampleId) { mutableFloatStateOf(1f) }
    var pan by remember(sampleId) { mutableStateOf(Offset.Zero) }
    var rejectDialog by remember { mutableStateOf(false) }
    var acceptDialog by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var labelMenu by remember { mutableStateOf(false) }
    val position = samples.indexOfFirst { it.sampleId == sampleId }.let { if (it < 0) 1 else it + 1 }
    val wideCommands = LocalConfiguration.current.screenWidthDp >= 600
    val locked = busy || editing
    val update: (SampleAnnotations) -> Unit = viewModel::updateAnnotations
    val regionTab = tab == EditorTab.REGIONS || tab == EditorTab.GROUNDING
    val maskAllowed = StudioTask.SEGMENTATION in tasks
    val boxAllowed = StudioTask.DETECTION in tasks || StudioTask.GROUNDING in tasks
    val pointAllowed = StudioTask.POINTING in tasks || StudioTask.POINTING_MULTI in tasks || StudioTask.GROUNDING in tasks
    val hasProposals = a.masks.any { !it.isHumanVerified } || a.boxes.any { !it.isHumanVerified } || a.points.any { !it.isHumanVerified } || a.tags.any { !it.isHumanVerified } || a.captions.any { !it.isHumanVerified } || a.vqaList.any { !it.isHumanVerified } || a.counts.any { !it.isHumanVerified } || a.groundings.any { !it.isHumanVerified }
    val hasModel = !project?.modelPath.isNullOrBlank() || project?.modelConfigJson?.contains("local_http") == true
    val modelAction=remember(project?.modelConfigJson,project?.activeTasksCsv) {
        project?.modelConfigJson?.let { json -> runCatching { StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(json) }.getOrNull() }
            ?.let { ModelCapabilities.action(it,project?.activeTasksCsv.orEmpty()) } ?: ModelAction.NONE
    }

    val inspectorState = rememberSaveableStateHolder()
    var propertiesOpen by rememberSaveable { mutableStateOf(false) }
    val inspector: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.background(MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Annotations", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                EditorCommand(Icons.Default.Close, "Fermer les propriétés", onClick = { propertiesOpen = false })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                tabs.forEach { t -> TextButton(onClick = { tab = t }, colors = ButtonDefaults.textButtonColors(contentColor = if (tab == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) { Text(t.title, style = MaterialTheme.typography.labelMedium) } }
            }
            if (regionTab) Box(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { labelMenu = true }) { Text("Classe · $label", style = MaterialTheme.typography.labelMedium); Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) }
                DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                    classes.forEach { cls -> DropdownMenuItem(text = { Text(cls) }, onClick = { label = cls; labelMenu = false }) }
                }
            }
            StudioAction(stringResource(R.string.editor_similar_images),viewModel::findSimilarImages,icon=Icons.Default.ImageSearch,enabled=!locked,modifier=Modifier.padding(horizontal=12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) {
                inspectorState.SaveableStateProvider("$sampleId:${tab.name}") {
                    when(tab) {
                        EditorTab.REGIONS -> RegionInspector(a, classes, selected, { selected = it; tool = EditorTool.SELECT }, update,
                            onDuplicate={id->a.boxes.firstOrNull{it.id==id}?.let{b->val n=b.copy(id=newId(),instanceId=null,xmin=(b.xmin+.02f).coerceAtMost(.98f),xmax=(b.xmax+.02f).coerceAtMost(1f));update(a.copy(boxes=a.boxes+n));selected=n.id}
                                ?:a.points.firstOrNull{it.id==id}?.let{p->val n=p.copy(id=newId(),instanceId=null,x=(p.x+.02f).coerceAtMost(1f),y=(p.y+.02f).coerceAtMost(1f));update(a.copy(points=a.points+n));selected=n.id}},
                            onCopy={id->copiedBox=a.boxes.firstOrNull{it.id==id};copiedPoint=a.points.firstOrNull{it.id==id}},
                            onPaste={val b=copiedBox;val pnt=copiedPoint;if(b!=null){val n=b.copy(id=newId(),instanceId=null,xmin=(b.xmin+.02f).coerceAtMost(.98f),xmax=(b.xmax+.02f).coerceAtMost(1f));update(a.copy(boxes=a.boxes+n));selected=n.id}else if(pnt!=null){val n=pnt.copy(id=newId(),instanceId=null,x=(pnt.x+.02f).coerceAtMost(1f),y=(pnt.y+.02f).coerceAtMost(1f));update(a.copy(points=a.points+n));selected=n.id}},canPaste=copiedBox!=null||copiedPoint!=null,
                            onInfer = { if (!hasModel) viewModel.navigateTo(Screen.Models) else viewModel.runLiteRtOnCurrentSample(samPoint?.let { listOf(it.x,it.y) }.orEmpty()) }, modelAction = modelAction, locked = locked)
                        EditorTab.CAPTION -> CaptionEditorTab(a, prefs.captionLanguage, update)
                        EditorTab.TAGS -> TagsEditorTab(a, classes, update)
                        EditorTab.GROUNDING -> GroundingEditorTab(a, update)
                        EditorTab.VQA -> VqaEditorTab(a, update)
                        EditorTab.COUNTING -> CountingEditorTab(a, classes, update)
                        EditorTab.QUALITY -> QualityEditorTab(a, update)
                    }
                }
            }
        }
    }
    Scaffold(contentWindowInsets = WindowInsets(0), modifier = Modifier.imePadding(), topBar = {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                EditorCommand(Icons.AutoMirrored.Filled.ArrowBack, "Revenir au lot après enregistrement", onClick = viewModel::back, enabled = !locked)
                Column(Modifier.weight(1f)) {
                    Text(sample?.assetId ?: "Image", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Text("$position / ${samples.size.coerceAtLeast(1)}  ·  " + if (saving == "Enregistré sur cet appareil") "Enregistré" else saving, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = if (saving.startsWith("Échec")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(wideCommands) {
                    EditorCommand(Icons.Default.ChevronLeft, "Image précédente", onClick = { viewModel.moveSample(-1) }, enabled = position > 1 && !locked)
                    EditorCommand(Icons.Default.ChevronRight, "Image suivante", onClick = { viewModel.moveSample(1) }, enabled = position < samples.size && !locked)
                    VerticalDivider(Modifier.height(18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                EditorCommand(Icons.AutoMirrored.Filled.Undo, "Annuler la dernière modification", onClick = viewModel::undo, enabled = canUndo && !locked, modifier = Modifier.testTag("undo_button"))
                EditorCommand(Icons.AutoMirrored.Filled.Redo, "Rétablir", onClick = viewModel::redo, enabled = canRedo && !locked, modifier = Modifier.testTag("redo_button"))
                Box {
                    EditorCommand(Icons.Default.MoreVert, "Actions du cas", onClick = { more = true })
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_zoom_in)) }, onClick = { zoom = (zoom * 1.25f).coerceAtMost(12f); more = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_zoom_out)) }, onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f); more = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_fit_image)) }, onClick = { zoom = 1f; pan = Offset.Zero; more = false })
                        if(maskAllowed) DropdownMenuItem(text = { Text(stringResource(R.string.editor_new_mask)) }, enabled = !locked, onClick = { selected = null; tool = EditorTool.MASK; more = false; propertiesOpen = false })
                        if(samModel) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_sam_point)) }, enabled = !locked, onClick = { tool = EditorTool.SAM_POINT; more = false; propertiesOpen = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_segment_point)) }, enabled = samPoint != null && !locked,
                                onClick = { viewModel.runLiteRtOnCurrentSample(samPoint!!.let { listOf(it.x,it.y) }); more = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.editor_clear_sam)) }, enabled = samPoint != null && !locked, onClick = { samPoint = null; more = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_previous_image)) }, enabled = position > 1 && !locked, onClick = { viewModel.moveSample(-1); more = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_next_image)) }, enabled = position < samples.size && !locked, onClick = { viewModel.moveSample(1); more = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_defer_image)) }, enabled = !locked, onClick = { viewModel.deferCurrent(); more = false }, modifier = Modifier.testTag("defer_button"))
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_reject_reason)) }, enabled = !locked, onClick = { rejectDialog = true; more = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_retry_save)) }, onClick = { viewModel.retrySave(); more = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_accept_reviewed)) }, enabled = hasProposals && !locked, onClick = { acceptDialog = true; more = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_customize_tools)) }, onClick = { viewModel.navigateTo(Screen.Preferences); more = false })
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val roomy = maxWidth >= 600.dp
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val validate: @Composable () -> Unit = {
                            EditorCommand(Icons.Default.Check, if (prefs.autoAdvance) "Valider et passer à l’image suivante" else "Valider l’image",
                                onClick = viewModel::validateCurrentAndNext, enabled = !locked && !saving.startsWith("Échec"), accent = true, modifier = Modifier.testTag("validate_next_button"))
                        }
                        if (prefs.leftHanded) validate()
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        listOf(EditorTool.SELECT, EditorTool.BOX, EditorTool.POINT, EditorTool.MASK, EditorTool.ERASE,EditorTool.POLYGON,EditorTool.LASSO,EditorTool.FILL, EditorTool.PAN_ZOOM)
                            .filter { (it != EditorTool.BOX || boxAllowed) && (it != EditorTool.POINT || pointAllowed) && (it !in setOf(EditorTool.MASK,EditorTool.ERASE,EditorTool.POLYGON,EditorTool.LASSO,EditorTool.FILL) || maskAllowed) && (it != EditorTool.SELECT || boxAllowed || pointAllowed || maskAllowed) }
                            .forEach { t ->
                                val icon = when(t) { EditorTool.SELECT -> Icons.Default.NearMe; EditorTool.BOX -> Icons.Default.CropSquare; EditorTool.POINT, EditorTool.SAM_POINT -> Icons.Default.MyLocation; EditorTool.MASK -> Icons.Default.Brush; EditorTool.ERASE -> Icons.Default.AutoFixOff;EditorTool.POLYGON->Icons.Default.ChangeHistory;EditorTool.LASSO->Icons.Default.Gesture;EditorTool.FILL->Icons.Default.FormatColorFill; EditorTool.PAN_ZOOM -> Icons.Default.PanTool }
                                val description = when(t) { EditorTool.SELECT -> "Sélectionner et déplacer"; EditorTool.BOX -> "Dessiner une boîte"; EditorTool.POINT -> "Placer un point"; EditorTool.SAM_POINT -> "Pointer pour SAM"; EditorTool.MASK -> "Peindre le masque"; EditorTool.ERASE -> "Effacer le masque";EditorTool.POLYGON->"Polygone · double-tap pour fermer";EditorTool.LASSO->"Lasso";EditorTool.FILL->"Remplir une zone"; EditorTool.PAN_ZOOM -> "Déplacer et zoomer l’image" }
                                EditorCommand(icon, description, selected = tool == t, enabled = !locked, onClick = {
                                    tool = t
                                    if (t != EditorTool.PAN_ZOOM && EditorTab.REGIONS in tabs) tab = EditorTab.REGIONS
                                })
                            }
                        }
                        if (roomy) {
                            EditorCommand(Icons.Default.Remove, "Zoom arrière", onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f) })
                            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("${(zoom * 100).toInt()} %", style = MaterialTheme.typography.labelMedium) }
                            EditorCommand(Icons.Default.Add, "Zoom avant", onClick = { zoom = (zoom * 1.25f).coerceAtMost(12f) })
                            Spacer(Modifier.weight(1f))
                        }
                        EditorCommand(Icons.Default.Tune, "Annotations et propriétés", onClick = { propertiesOpen = !propertiesOpen }, selected = propertiesOpen)
                        if (!prefs.leftHanded) validate()
                    }
                }
                if(tool in setOf(EditorTool.MASK,EditorTool.ERASE)) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(tool==EditorTool.MASK)"Pinceau" else "Gomme",style=MaterialTheme.typography.labelMedium)
                    Slider(value=if(tool==EditorTool.MASK)brushSize else eraserSize,onValueChange={if(tool==EditorTool.MASK)brushSize=it else eraserSize=it},valueRange=0.005f..0.1f,modifier=Modifier.weight(1f))
                    Text("${(((if(tool==EditorTool.MASK)brushSize else eraserSize)*100)).toInt()} %",style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }) { inset ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inset)) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).clipToBounds()) {
                        InteractiveAnnotationCanvas(sample, a, if (locked || (propertiesOpen && !regionTab)) EditorTool.PAN_ZOOM else tool,
                            label, selected, zoom, pan, { z, o -> zoom = z; pan = o }, { selected = it }, { next ->
                                if (StudioTask.POINTING in tasks && next.points.size > 1 && next.points.size > a.points.size) viewModel.reportError("Mode Point unique : déplacez le point existant ou activez Points multiples.") else update(next)
                            }, brushFraction=if(tool==EditorTool.ERASE)eraserSize else brushSize,showLabels = prefs.showCanvasLabels, promptPoint = samPoint, onPromptSelected = { samPoint = it })
                    }
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if(tool==EditorTool.SAM_POINT) "SAM · pointez l’objet" else "$label · ${a.masks.size + a.boxes.size + a.points.size} régions", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if(wideCommands) Text("${sample?.imageWidth ?: 0} × ${sample?.imageHeight ?: 0}  ·  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(zoom * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (wide && propertiesOpen) {
                    VerticalDivider()
                    inspector(Modifier.width(300.dp).fillMaxHeight())
                }
            }
            if (!wide && propertiesOpen) ModalBottomSheet(onDismissRequest = { propertiesOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = null) {
                inspector(Modifier.fillMaxWidth().fillMaxHeight(.8f))
            }
        }
    }
    if (issues.isNotEmpty()) AlertDialog(onDismissRequest = viewModel::clearEditorIssues, title = { Text(stringResource(R.string.editor_review_before_validate)) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { issues.forEach { Text(it) } }
    }, confirmButton = { TextButton(onClick = viewModel::clearEditorIssues) { Text(stringResource(R.string.editor_return_corrections)) } })
    if (rejectDialog) AlertDialog(onDismissRequest = { rejectDialog = false }, title = { Text(stringResource(R.string.editor_reject_case)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.editor_reject_explanation))
            OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.editor_rejection_reason)) }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = { rejectDialog = false; viewModel.rejectCurrent(reason) }, enabled = reason.isNotBlank()) { Text(stringResource(R.string.editor_confirm_rejection)) } }, dismissButton = { TextButton(onClick = { rejectDialog = false }) { Text(stringResource(R.string.common_cancel)) } })
    if (acceptDialog) AlertDialog(onDismissRequest = { acceptDialog = false }, title = { Text(stringResource(R.string.editor_confirm_review)) }, text = {
        Text(stringResource(R.string.editor_accept_explanation))
    }, confirmButton = { Button(onClick = { acceptDialog = false; viewModel.acceptCurrentProposals() }) { Text(stringResource(R.string.editor_verified_proposals)) } }, dismissButton = { TextButton(onClick = { acceptDialog = false }) { Text(stringResource(R.string.common_cancel)) } })
}

@Composable
fun InteractiveAnnotationCanvas(sample: SampleEntity?, annotations: SampleAnnotations, activeTool: EditorTool, selectedClass: String,
    selectedTargetId: String?, scale: Float, offset: Offset, onTransformChanged: (Float, Offset) -> Unit,
    onTargetSelected: (String?) -> Unit, onAnnotationsUpdated: (SampleAnnotations) -> Unit, brushFraction:Float=.025f,showLabels: Boolean = true,
    promptPoint: ViewPoint? = null, onPromptSelected: (ViewPoint) -> Unit = {}) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val radius = with(density) { 24.dp.toPx() }
    val viewport = remember(canvasSize, sample?.imageWidth, sample?.imageHeight, scale, offset) {
        ImageViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat(), (sample?.imageWidth ?: 0).toFloat(), (sample?.imageHeight ?: 0).toFloat(), scale, offset.x, offset.y)
    }
    val latest by rememberUpdatedState(annotations)
    val selectedId by rememberUpdatedState(selectedTargetId)
    val emit by rememberUpdatedState(onAnnotationsUpdated)
    val select by rememberUpdatedState(onTargetSelected)
    val selectPrompt by rememberUpdatedState(onPromptSelected)
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)
    val transform by rememberUpdatedState(onTransformChanged)
    var draft by remember(sample?.sampleId) { mutableStateOf<SampleAnnotations?>(null) }
    var dragStart by remember { mutableStateOf<ViewPoint?>(null) }
    var startAnnotation by remember { mutableStateOf<SampleAnnotations?>(null) }
    var dragTarget by remember { mutableStateOf<String?>(null) }
    var corner by remember { mutableIntStateOf(-1) }
    var creatingId by remember { mutableStateOf<String?>(null) }
    var paintingId by remember(sample?.sampleId) { mutableStateOf<String?>(null) }
    var polygonPoints by remember(sample?.sampleId) { mutableStateOf<List<Pair<Float,Float>>>(emptyList()) }
    var lassoPoints by remember(sample?.sampleId) { mutableStateOf<List<Pair<Float,Float>>>(emptyList()) }
    val transformState = rememberTransformableState { z, move, _ ->
        val newScale = (latestScale * z).coerceIn(1f, 12f)
        val maxPanX = canvasSize.width * newScale
        val maxPanY = canvasSize.height * newScale
        transform(newScale, Offset((latestOffset.x + move.x).coerceIn(-maxPanX, maxPanX), (latestOffset.y + move.y).coerceIn(-maxPanY, maxPanY)))
    }
    fun hitPoint(at: Offset): PointTarget? = latest.points.filter { it.canProvideCoordinates }.minByOrNull { p -> val s=viewport.toScreen(p.x,p.y); hypot(s.x-at.x,s.y-at.y) }?.takeIf { p -> val s=viewport.toScreen(p.x,p.y); hypot(s.x-at.x,s.y-at.y)<=radius }
    fun hitBox(n: ViewPoint): BoxTarget? = latest.boxes.filter { n.x in it.xmin..it.xmax && n.y in it.ymin..it.ymax }.minByOrNull { (it.xmax-it.xmin)*(it.ymax-it.ymin) }
    fun maskStroke(at: ViewPoint, previous: ViewPoint = at): SampleAnnotations {
        val origin=draft ?: latest
        val old=origin.masks.firstOrNull { it.id==(paintingId ?: selectedId) }
        if(old==null && activeTool==EditorTool.ERASE) return origin
        val iw=(sample?.imageWidth ?: 512).coerceAtLeast(1);val ih=(sample?.imageHeight ?: 512).coerceAtLeast(1)
        val (w,h)=MaskCodec.rasterSizeForImage(iw,ih)
        val mask=old ?: MaskTarget(newId(),selectedClass,w,h,listOf(w*h),isHumanVerified=true)
        val updated=com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.stroke(mask,previous.x,previous.y,at.x,at.y,brushFraction,activeTool==EditorTool.ERASE)
        paintingId=updated.id
        select(updated.id)
        return origin.copy(masks=origin.masks.filterNot { it.id==updated.id }+updated)
    }
    val drawn = draft ?: annotations
    val maskImages=remember(drawn.masks) { drawn.masks.map { m ->
        val pixels=com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.decode(m)
        m to android.graphics.Bitmap.createBitmap(IntArray(pixels.size) { if(pixels[it]) 0x669BE4BE else 0 },m.width,m.height,android.graphics.Bitmap.Config.ARGB_8888)
    } }
    DisposableEffect(maskImages) { onDispose { maskImages.forEach { it.second.recycle() } } }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { canvasSize = it }
        .semantics { contentDescription = "Image à annoter. ${annotations.boxes.size} boîtes et ${annotations.points.size} points. Les régions sont aussi accessibles dans le panneau de correction." }
        .then(if(activeTool == EditorTool.PAN_ZOOM) Modifier.transformable(transformState) else Modifier)
        .pointerInput(sample?.sampleId, activeTool, selectedClass, viewport) {
            detectTapGestures(onDoubleTap = when(activeTool){EditorTool.PAN_ZOOM->{{_:Offset->transform(1f,Offset.Zero)}};EditorTool.POLYGON->{{_:Offset->if(polygonPoints.size>=3){val origin=latest;val old=origin.masks.firstOrNull{it.id==selectedId};val mask=old?:MaskCodec.empty(newId(),selectedClass,sample?.imageWidth?:512,sample?.imageHeight?:512);val next=MaskCodec.polygon(mask,polygonPoints);emit(origin.copy(masks=origin.masks.filterNot{it.id==next.id}+next));select(next.id);polygonPoints=emptyList()}}};else->null}, onTap = { at ->
                val n = viewport.toImage(at.x, at.y) ?: return@detectTapGestures
                when(activeTool) {
                    EditorTool.SAM_POINT -> selectPrompt(n)
                    EditorTool.POINT -> {
                        val near = hitPoint(at)
                        if (near != null) select(near.id) else {
                            val point = PointTarget(newId(), n.x, n.y, selectedClass, isHumanVerified = true)
                            emit(latest.copy(points = latest.points + point)); select(point.id)
                        }
                    }
                    EditorTool.SELECT, EditorTool.BOX -> select(hitPoint(at)?.id ?: hitBox(n)?.id ?: latest.masks.firstOrNull { m ->
                        val pixels=com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.decode(m)
                        pixels[(n.y*m.height).toInt().coerceIn(0,m.height-1)*m.width+(n.x*m.width).toInt().coerceIn(0,m.width-1)]
                    }?.id)
                    EditorTool.MASK, EditorTool.ERASE -> { paintingId=selectedId; emit(maskStroke(n)); paintingId=null }
                    EditorTool.POLYGON -> polygonPoints=polygonPoints+(n.x to n.y)
                    EditorTool.FILL -> {val origin=latest;val old=origin.masks.firstOrNull{it.id==selectedId};val mask=old?:MaskCodec.empty(newId(),selectedClass,sample?.imageWidth?:512,sample?.imageHeight?:512);val next=MaskCodec.fill(mask,n.x,n.y);emit(origin.copy(masks=origin.masks.filterNot{it.id==next.id}+next));select(next.id)}
                    EditorTool.LASSO -> Unit
                    EditorTool.PAN_ZOOM -> Unit
                }
            })
        }
        .then(if(activeTool in setOf(EditorTool.MASK,EditorTool.ERASE)) Modifier.pointerInput(sample?.sampleId,activeTool,selectedClass,viewport) {
            detectDragGestures(onDragStart={ at ->
                draft=null;paintingId=selectedId;dragStart=viewport.toImage(at.x,at.y);dragStart?.let { draft=maskStroke(it) }
            },onDrag={ change,_ ->
                val at=viewport.toImage(change.position.x,change.position.y,clamp=true)
                if(at!=null) { change.consume();draft=maskStroke(at,dragStart ?: at);dragStart=at }
            },onDragEnd={draft?.let(emit);draft=null;dragStart=null;paintingId=null},onDragCancel={draft=null;dragStart=null;paintingId=null})
        } else Modifier).then(if(activeTool==EditorTool.LASSO)Modifier.pointerInput(sample?.sampleId,selectedClass,viewport){detectDragGestures(onDragStart={at->lassoPoints=viewport.toImage(at.x,at.y)?.let{listOf(it.x to it.y)}.orEmpty()},onDrag={change,_->viewport.toImage(change.position.x,change.position.y,true)?.let{lassoPoints=lassoPoints+(it.x to it.y)};change.consume()},onDragEnd={if(lassoPoints.size>=3){val origin=latest;val old=origin.masks.firstOrNull{it.id==selectedId};val mask=old?:MaskCodec.empty(newId(),selectedClass,sample?.imageWidth?:512,sample?.imageHeight?:512);val next=MaskCodec.polygon(mask,lassoPoints);emit(origin.copy(masks=origin.masks.filterNot{it.id==next.id}+next));select(next.id)};lassoPoints=emptyList()},onDragCancel={lassoPoints=emptyList()})}else Modifier)
        .then(if (activeTool == EditorTool.BOX || activeTool == EditorTool.SELECT) Modifier.pointerInput(sample?.sampleId, activeTool, selectedClass, viewport) {
            detectDragGestures(onDragStart = { at ->
                dragStart = viewport.toImage(at.x, at.y)
                startAnnotation = latest; corner = -1; draft = null; dragTarget = null; creatingId = null
                if (dragStart != null) {
                    if (activeTool == EditorTool.BOX) creatingId = newId()
                    else {
                        val selectedBox = latest.boxes.firstOrNull { it.id == selectedId }
                        if (selectedBox != null) {
                            val corners = listOf(selectedBox.xmin to selectedBox.ymin, selectedBox.xmax to selectedBox.ymin, selectedBox.xmin to selectedBox.ymax, selectedBox.xmax to selectedBox.ymax)
                            corner = corners.indexOfFirst { (x,y) -> val s=viewport.toScreen(x,y); hypot(s.x-at.x,s.y-at.y)<=radius }
                        }
                        dragTarget = if(corner>=0) selectedBox?.id else hitPoint(at)?.id ?: hitBox(dragStart!!)?.id
                        select(dragTarget)
                    }
                }
            }, onDrag = { change, _ ->
                val start = dragStart
                val n = viewport.toImage(change.position.x, change.position.y, clamp = true)
                val origin = startAnnotation
                if (start != null && n != null && origin != null) {
                    change.consume()
                    if (creatingId != null) {
                        val box = BoxTarget(creatingId!!, minOf(start.x,n.x), minOf(start.y,n.y), maxOf(start.x,n.x), maxOf(start.y,n.y), selectedClass, isHumanVerified = true)
                        draft = origin.copy(boxes = origin.boxes + box)
                    } else {
                        val point = origin.points.firstOrNull { it.id == dragTarget }
                        val box = origin.boxes.firstOrNull { it.id == dragTarget }
                        if (point != null) draft = origin.copy(points = origin.points.map { if(it.id == point.id) it.copy(x = (it.x+n.x-start.x).coerceIn(0f,1f), y=(it.y+n.y-start.y).coerceIn(0f,1f), isHumanVerified=true) else it })
                        if (box != null) {
                            val dx=(n.x-start.x).coerceIn(-box.xmin,1f-box.xmax); val dy=(n.y-start.y).coerceIn(-box.ymin,1f-box.ymax)
                            val minW = 1f / viewport.imageWidth.coerceAtLeast(1f); val minH=1f / viewport.imageHeight.coerceAtLeast(1f)
                            val changed = when(corner) {
                                0 -> box.copy(xmin=n.x.coerceAtMost(box.xmax-minW).coerceAtLeast(0f), ymin=n.y.coerceAtMost(box.ymax-minH).coerceAtLeast(0f))
                                1 -> box.copy(xmax=n.x.coerceAtLeast(box.xmin+minW).coerceAtMost(1f), ymin=n.y.coerceAtMost(box.ymax-minH).coerceAtLeast(0f))
                                2 -> box.copy(xmin=n.x.coerceAtMost(box.xmax-minW).coerceAtLeast(0f), ymax=n.y.coerceAtLeast(box.ymin+minH).coerceAtMost(1f))
                                3 -> box.copy(xmax=n.x.coerceAtLeast(box.xmin+minW).coerceAtMost(1f), ymax=n.y.coerceAtLeast(box.ymin+minH).coerceAtMost(1f))
                                else -> box.copy(xmin=box.xmin+dx,xmax=box.xmax+dx,ymin=box.ymin+dy,ymax=box.ymax+dy)
                            }.copy(isHumanVerified=true)
                            draft = origin.copy(boxes=origin.boxes.map { if(it.id==box.id) changed else it })
                        }
                    }
                }
            }, onDragEnd = {
                val created = draft?.boxes?.firstOrNull { it.id == creatingId }
                val acceptable = creatingId == null || (created != null && (created.xmax-created.xmin)*viewport.width >= 4f && (created.ymax-created.ymin)*viewport.height >= 4f)
                if (acceptable) draft?.let { emit(it); if(creatingId != null) select(creatingId) }
                draft=null; dragStart=null; creatingId=null
            }, onDragCancel = { draft=null; dragStart=null; creatingId=null })
        } else Modifier)) {
        if (sample?.localImagePath != null) AsyncImage(model = File(sample.localImagePath), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX=scale; scaleY=scale; translationX=offset.x; translationY=offset.y })
        Canvas(Modifier.fillMaxSize()) {
            val human = Color(0xFF9BE4BE); val proposed = Color(0xFFC3B4FA); val selected = Color(0xFFFFD980)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=12.dp.toPx(); color=android.graphics.Color.WHITE; setShadowLayer(3f,0f,1f,android.graphics.Color.BLACK) }
            if(viewport.isValid) {
                val first=viewport.toScreen(0f,0f);val last=viewport.toScreen(1f,1f)
                maskImages.forEach { (_,bitmap) -> drawContext.canvas.nativeCanvas.drawBitmap(bitmap,null,android.graphics.RectF(first.x,first.y,last.x,last.y),null) }
                promptPoint?.let { point ->
                    val screen=viewport.toScreen(point.x,point.y)
                    val at=Offset(screen.x,screen.y)
                    drawCircle(Color(0xFF74DFFF),10.dp.toPx(),at,style=Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFF74DFFF),3.dp.toPx(),at)
                    drawContext.canvas.nativeCanvas.drawText("SAM",at.x+14.dp.toPx(),at.y-8.dp.toPx(),paint)
                }
            }
            drawn.boxes.forEach { b ->
                val isSelected=b.id==selectedTargetId; val color=if(isSelected) selected else if(b.isHumanVerified) human else proposed
                val p1=viewport.toScreen(b.xmin,b.ymin); val p2=viewport.toScreen(b.xmax,b.ymax)
                if(viewport.isValid && p2.x>p1.x && p2.y>p1.y) {
                    if(isSelected) drawRect(color.copy(alpha=.10f),Offset(p1.x,p1.y),Size(p2.x-p1.x,p2.y-p1.y))
                    drawRect(color,Offset(p1.x,p1.y),Size(p2.x-p1.x,p2.y-p1.y),style=Stroke(2.dp.toPx(),pathEffect=if(!b.isHumanVerified) PathEffect.dashPathEffect(floatArrayOf(10f,8f)) else null))
                    if(isSelected) listOf(p1,ViewPoint(p2.x,p1.y),ViewPoint(p1.x,p2.y),p2).forEach { drawCircle(color,5.dp.toPx(),Offset(it.x,it.y)) }
                    if(showLabels) drawContext.canvas.nativeCanvas.drawText(b.label.take(40),p1.x+5.dp.toPx(),maxOf(16.dp.toPx(),p1.y-5.dp.toPx()),paint)
                }
            }
            drawn.points.filter { it.canProvideCoordinates }.forEach { p ->
                val screen=viewport.toScreen(p.x,p.y); val c=if(p.id==selectedTargetId) selected else if(p.isHumanVerified) human else proposed
                if(viewport.isValid) {
                    val at=Offset(screen.x,screen.y)
                    drawCircle(Color.Black.copy(alpha=.7f),9.dp.toPx(),at); drawCircle(c,5.dp.toPx(),at)
                    drawLine(c,at-Offset(13.dp.toPx(),0f),at+Offset(13.dp.toPx(),0f),1.dp.toPx())
                    drawLine(c,at-Offset(0f,13.dp.toPx()),at+Offset(0f,13.dp.toPx()),1.dp.toPx())
                    if(showLabels) drawContext.canvas.nativeCanvas.drawText(p.label.take(40),screen.x+14.dp.toPx(),screen.y-8.dp.toPx(),paint)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionInspector(a: SampleAnnotations, classes: List<String>, selected: String?, onSelect: (String?) -> Unit,
    onUpdate: (SampleAnnotations) -> Unit,onDuplicate:(String)->Unit,onCopy:(String)->Unit,onPaste:()->Unit,canPaste:Boolean,onInfer: () -> Unit, modelAction: ModelAction, locked: Boolean) {
    val box=a.boxes.firstOrNull { it.id==selected }; val point=a.points.firstOrNull { it.id==selected }; val mask=a.masks.firstOrNull { it.id==selected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(mask!=null) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Masque · ${mask.width} × ${mask.height}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                IconButton(onClick={onUpdate(withoutTarget(a,mask.id));onSelect(null)}) { Icon(Icons.Default.DeleteOutline,"Supprimer le masque") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) { classes.forEach { cls ->
                FilterChip(selected=mask.label==cls,onClick={onUpdate(a.copy(masks=a.masks.map { if(it.id==mask.id) it.copy(label=cls,isHumanVerified=true) else it }))},label={Text(cls)})
            } }
            Text("Pinceau pour ajouter, gomme pour retirer. Annuler reste disponible.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled=a.masks.count{it.label==mask.label&&it.width==mask.width&&it.height==mask.height}>=2,onClick={val chosen=a.masks.filter{it.label==mask.label&&it.width==mask.width&&it.height==mask.height};val merged=MaskCodec.merge(chosen,newId(),mask.label);onUpdate(a.copy(masks=a.masks-chosen.toSet()+merged));onSelect(merged.id)}){Text(stringResource(R.string.editor_merge))}
                OutlinedButton(onClick={val pieces=MaskCodec.split(mask){newId()};if(pieces.size>1){onUpdate(a.copy(masks=a.masks.filterNot{it.id==mask.id}+pieces));onSelect(pieces.first().id)}}){Text(stringResource(R.string.editor_split_islands))}
            }
            if(!mask.isHumanVerified) TextButton(onClick={onUpdate(a.copy(masks=a.masks.map { if(it.id==mask.id) it.copy(isHumanVerified=true) else it }))}) { Text(stringResource(R.string.editor_mask_reviewed)) }
            TextButton(onClick={onSelect(null)}) { Text(stringResource(R.string.editor_deselect)) }
        }
        if(box==null && point==null && mask==null) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${a.masks.size+a.boxes.size+a.points.size} régions", Modifier.weight(1f), style=MaterialTheme.typography.titleSmall)
                val actionLabel=when(modelAction){
                    ModelAction.PREANNOTATE->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.editor_action_preannotate)
                    ModelAction.PREANNOTATE_PARTIALLY->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.editor_action_preannotate_partial)
                    ModelAction.COMPUTE_REPRESENTATION->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.editor_action_embedding)
                    ModelAction.INTERACTIVE_SEGMENTATION->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.editor_action_interactive_segmentation)
                    ModelAction.INSPECT->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.editor_action_inspect)
                    ModelAction.NONE->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.editor_action_model)
                }
                StudioAction(actionLabel, onInfer, icon=Icons.Default.Memory, enabled=!locked && modelAction!=ModelAction.INSPECT)
                IconButton(onClick=onPaste,enabled=canPaste&&!locked){Icon(Icons.Default.ContentPaste,"Coller une boîte ou un point")}
            }
            if (a.masks.isEmpty() && a.boxes.isEmpty() && a.points.isEmpty()) Text("Choisissez un outil de dessin.", style=MaterialTheme.typography.bodySmall)
        } else if(mask==null) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(box!=null) "Boîte sélectionnée" else "Point sélectionné",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                IconButton(onClick={ onUpdate(withoutTarget(a, selected!!)); onSelect(null) }) { Icon(Icons.Default.DeleteOutline,"Supprimer la région sélectionnée") }
                IconButton(onClick={onDuplicate(selected!!)},enabled=!locked){Icon(Icons.Default.CopyAll,"Dupliquer")}
                IconButton(onClick={onCopy(selected!!)},enabled=!locked){Icon(Icons.Default.ContentCopy,"Copier")}
                IconButton(onClick={onSelect(null)}) { Icon(Icons.Default.Close,"Désélectionner") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                classes.forEach { label -> FilterChip(selected=(box?.label ?: point?.label)==label,onClick={
                    onUpdate(if(box!=null) a.copy(boxes=a.boxes.map { if(it.id==box.id) it.copy(label=label,isHumanVerified=true) else it }) else a.copy(points=a.points.map { if(it.id==point?.id) it.copy(label=label,isHumanVerified=true) else it }))
                },label={Text(label)}) }
            }
            Text(if(box!=null) "Glissez les coins pour redimensionner." else "Glissez ou utilisez les flèches.",style=MaterialTheme.typography.bodySmall)
            if(point!=null) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                fun state(value:PointLocalizationState) = onUpdate(a.copy(points=a.points.map{if(it.id==point.id)it.copy(
                    localizationState=value,isAbsent=value==PointLocalizationState.ABSENT,
                    isAbstained=value==PointLocalizationState.UNLOCALIZABLE||value==PointLocalizationState.UNCERTAIN,isHumanVerified=true)else it}))
                FilterChip(selected=point.effectiveLocalizationState==PointLocalizationState.LOCALIZED,onClick={state(PointLocalizationState.LOCALIZED)},label={Text(stringResource(R.string.point_localized))})
                FilterChip(selected=point.effectiveLocalizationState==PointLocalizationState.ABSENT,onClick={state(PointLocalizationState.ABSENT)},label={Text(stringResource(R.string.point_absent))})
                FilterChip(selected=point.effectiveLocalizationState==PointLocalizationState.UNLOCALIZABLE,onClick={state(PointLocalizationState.UNLOCALIZABLE)},label={Text(stringResource(R.string.point_unlocalizable))})
                FilterChip(selected=point.effectiveLocalizationState==PointLocalizationState.UNCERTAIN,onClick={state(PointLocalizationState.UNCERTAIN)},label={Text(stringResource(R.string.point_uncertain))})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                listOf(Icons.Default.KeyboardArrowLeft to (-.002f to 0f),Icons.Default.KeyboardArrowUp to (0f to -.002f),Icons.Default.KeyboardArrowDown to (0f to .002f),Icons.Default.KeyboardArrowRight to (.002f to 0f)).forEachIndexed { i,(icon,d) ->
                    OutlinedIconButton(onClick={
                        if(point!=null) onUpdate(a.copy(points=a.points.map { if(it.id==point.id) it.copy(x=(it.x+d.first).coerceIn(0f,1f),y=(it.y+d.second).coerceIn(0f,1f),isHumanVerified=true) else it }))
                        if(box!=null) { val dx=d.first.coerceIn(-box.xmin,1f-box.xmax); val dy=d.second.coerceIn(-box.ymin,1f-box.ymax); onUpdate(a.copy(boxes=a.boxes.map { if(it.id==box.id) it.copy(xmin=it.xmin+dx,xmax=it.xmax+dx,ymin=it.ymin+dy,ymax=it.ymax+dy,isHumanVerified=true) else it })) }
                    },modifier=Modifier.size(48.dp)) { Icon(icon,listOf("Déplacer à gauche","Déplacer vers le haut","Déplacer vers le bas","Déplacer à droite")[i]) }
                }
                Text("0,2 %",style=MaterialTheme.typography.labelSmall)
            }
            if((box?.isHumanVerified ?: point?.isHumanVerified)==false) TextButton(onClick={
                onUpdate(if(box!=null) a.copy(boxes=a.boxes.map { if(it.id==box.id) it.copy(isHumanVerified=true) else it }) else a.copy(points=a.points.map { if(it.id==point?.id) it.copy(isHumanVerified=true) else it }))
            }) { Text(stringResource(R.string.editor_reviewed_proposal)) }
        }
        selected?.takeIf{box!=null||point!=null||mask!=null}?.let { targetId ->
            InstanceLinkEditor(a,targetId,onUpdate,locked)
        }
        a.masks.forEachIndexed { i,m -> RegionRow("Masque ${i+1}",m.label,m.id==selected,m.isHumanVerified,Icons.Default.Brush) { onSelect(m.id) } }
        a.boxes.forEachIndexed { i,b -> RegionRow("Boîte ${i+1}",b.label,b.id==selected,b.isHumanVerified,Icons.Default.CropSquare) { onSelect(b.id) } }
        a.points.forEachIndexed { i,p -> RegionRow("Point ${i+1}",p.label,p.id==selected,p.isHumanVerified,Icons.Default.MyLocation) { onSelect(p.id) } }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InstanceLinkEditor(a:SampleAnnotations,targetId:String,onUpdate:(SampleAnnotations)->Unit,locked:Boolean) {
    val current=InstanceLinks.instanceId(a,targetId)
    val candidates=InstanceLinks.compatibleTargets(a,targetId)
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Text(if(current==null)stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_unlinked) else stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_linked,current.take(8)),style=MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(current==null) AssistChip(onClick={onUpdate(InstanceLinks.link(a,setOf(targetId),newId()))},enabled=!locked,label={Text(stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_new))})
            else AssistChip(onClick={onUpdate(InstanceLinks.unlink(a,targetId))},enabled=!locked,label={Text(stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_unlink))})
            candidates.forEach { candidateId ->
                val title=when {
                    a.boxes.any{it.id==candidateId}->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_link_box,candidateId.take(8))
                    a.masks.any{it.id==candidateId}->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_link_mask,candidateId.take(8))
                    else->stringResource(com.unicornwhodev.visiondatasetstudio.R.string.instance_link_point,candidateId.take(8))
                }
                AssistChip(onClick={
                    val instance=current ?: InstanceLinks.instanceId(a,candidateId) ?: newId()
                    onUpdate(InstanceLinks.link(a,setOf(targetId,candidateId),instance))
                },enabled=!locked,label={Text(title)})
            }
        }
    }
}

@Composable
private fun RegionRow(title:String, label:String, selected:Boolean, verified:Boolean, icon:ImageVector, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if(selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
        .clickable(role=androidx.compose.ui.semantics.Role.Tab,onClick=onClick).semantics { this.selected=selected }
        .heightIn(min=48.dp).padding(horizontal=8.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.size(17.dp),tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(title,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if(verified) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,if(verified) "Revue humaine" else "Proposition à relire",Modifier.size(14.dp),tint=if(verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun EditorPanel(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        if(hint!=null) StudioDetails(hint)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionEditorTab(a: SampleAnnotations, defaultLanguage: String = "fr", onUpdate: (SampleAnnotations) -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val item=a.captions.getOrNull(index) ?: a.captions.firstOrNull()
    val currentIndex=if(index in a.captions.indices) index else 0
    fun write(c: CaptionTarget) { onUpdate(a.copy(captions=if(item==null) a.captions+c else a.captions.map { if(it.id==item.id) c else it })) }
    EditorPanel("Légende de l’image", "Décrivez uniquement ce qui est visible. Plusieurs langues ou variantes peuvent coexister.") {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            a.captions.forEachIndexed { i,c -> FilterChip(selected=i==currentIndex,onClick={index=i},label={Text("${i+1} · ${c.language}")}) }
            AssistChip(onClick={index=a.captions.size;onUpdate(a.copy(captions=a.captions+CaptionTarget(newId(),"",defaultLanguage,isHumanVerified=true)))},label={Text(stringResource(R.string.editor_add_variant))})
        }
        OutlinedTextField(value=item?.text ?: "",onValueChange={write((item ?: CaptionTarget(newId(),"",defaultLanguage,isHumanVerified=true)).copy(text=it,isHumanVerified=true))},label={Text(stringResource(R.string.editor_description))},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("fr" to "Français","en" to "Anglais").forEach { (lang,title) -> FilterChip(selected=(item?.language ?: defaultLanguage)==lang,onClick={write((item ?: CaptionTarget(newId(),"",defaultLanguage,isHumanVerified=true)).copy(language=lang))},label={Text(title)}) }
            FilterChip(selected=item?.isDetailed==true,onClick={write((item ?: CaptionTarget(newId(),"",defaultLanguage,isHumanVerified=true)).copy(isDetailed=item?.isDetailed!=true))},label={Text(stringResource(R.string.editor_detailed))})
        }
        if(item!=null) TextButton(onClick={onUpdate(a.copy(captions=a.captions.filterNot { it.id==item.id }));index=0}) { Text(stringResource(R.string.editor_delete_variant)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsEditorTab(a: SampleAnnotations, classes: List<String>, onUpdate: (SampleAnnotations) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    EditorPanel("Classes et tags", "Touchez pour ajouter ou retirer une étiquette. Aucune classe n’est déduite automatiquement.") {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (classes+a.tags.map { it.label }).distinct().forEach { label ->
                val selected=a.tags.any { it.label==label }
                FilterChip(selected=selected,onClick={onUpdate(a.copy(tags=if(selected) a.tags.filterNot { it.label==label } else a.tags+TagTarget(newId(),label,isHumanVerified=true)))},label={Text(label)})
            }
        }
        OutlinedTextField(draft,{draft=it},label={Text(stringResource(R.string.editor_other_tag))},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedButton(enabled=draft.isNotBlank(),onClick={val label=draft.trim();if(a.tags.none { it.label==label }) onUpdate(a.copy(tags=a.tags+TagTarget(newId(),label,isHumanVerified=true)));draft=""}) { Text(stringResource(R.string.editor_add_tag)) }
        if(a.tags.any { !it.isHumanVerified }) Text("Des tags proposés par le modèle restent à relire via le menu du cas.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    }
}

@Composable
fun VqaEditorTab(a: SampleAnnotations, onUpdate: (SampleAnnotations) -> Unit) {
    EditorPanel("Questions et réponses", "L’image reste visible pendant la rédaction. Une abstention est différente d’une réponse vide.") {
        a.vqaList.forEachIndexed { i,q ->
            fun write(v: VqaTarget) { onUpdate(a.copy(vqaList=a.vqaList.map { if(it.id==q.id) v else it })) }
            OutlinedCard {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("Question ${i+1}",Modifier.weight(1f));IconButton(onClick={onUpdate(a.copy(vqaList=a.vqaList.filterNot { it.id==q.id }))}){Icon(Icons.Default.DeleteOutline,"Supprimer la question ${i+1}")} }
                    OutlinedTextField(q.question,{write(q.copy(question=it,isHumanVerified=true))},label={Text(stringResource(R.string.editor_question))},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(q.answer,{write(q.copy(answer=it,isHumanVerified=true))},label={Text(stringResource(R.string.editor_answer))},enabled=!q.isAbstained,minLines=2,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(q.isAbstained,{write(q.copy(isAbstained=it,isHumanVerified=true))});Text("Indéterminable à partir de l’image",style=MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Button(onClick={onUpdate(a.copy(vqaList=a.vqaList+VqaTarget(newId(),"","",isHumanVerified=true)))}) { Text(stringResource(R.string.editor_add_question)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroundingEditorTab(a: SampleAnnotations, onUpdate: (SampleAnnotations) -> Unit) {
    EditorPanel("Relier le texte à l’image", "Dessinez d’abord les régions dans l’onglet Régions, puis associez-les à une expression.") {
        a.groundings.forEach { g ->
            fun write(v: GroundingTarget) {onUpdate(a.copy(groundings=a.groundings.map {if(it.id==g.id) v else it}))}
            OutlinedTextField(g.phrase,{write(g.copy(phrase=it,isHumanVerified=true))},label={Text(stringResource(R.string.editor_grounding_phrase))},modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                a.boxes.forEachIndexed { i,b -> FilterChip(selected=b.id in g.boxIds,onClick={write(g.copy(boxIds=if(b.id in g.boxIds) g.boxIds-b.id else g.boxIds+b.id,isHumanVerified=true))},label={Text("Boîte ${i+1} · ${b.label}")}) }
                a.points.filter{it.canProvideCoordinates}.forEachIndexed { i,p -> FilterChip(selected=p.id in g.pointIds,onClick={write(g.copy(pointIds=if(p.id in g.pointIds) g.pointIds-p.id else g.pointIds+p.id,isHumanVerified=true))},label={Text("Point ${i+1} · ${p.label}")}) }
            }
            TextButton(onClick={onUpdate(a.copy(groundings=a.groundings.filterNot{it.id==g.id}))}){Text(stringResource(R.string.editor_delete_expression))}
            HorizontalDivider()
        }
        Button(onClick={onUpdate(a.copy(groundings=a.groundings+GroundingTarget(newId(),"",isHumanVerified=true)))}){Text(stringResource(R.string.editor_add_expression))}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountingEditorTab(a: SampleAnnotations, classes: List<String>, onUpdate: (SampleAnnotations) -> Unit) {
    EditorPanel("Compter les instances", "Zéro est une annotation valide. Indiquez si le comptage est exhaustif.") {
        a.counts.forEach { c ->
            fun write(v: CountingTarget) {onUpdate(a.copy(counts=a.counts.map{if(it.id==c.id)v else it}))}
            OutlinedCard {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { classes.forEach { label -> FilterChip(selected=c.label==label,onClick={write(c.copy(label=label,isHumanVerified=true))},label={Text(label)}) } }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedIconButton(onClick={write(c.copy(count=(c.count-1).coerceAtLeast(0),isHumanVerified=true))}){Icon(Icons.Default.Remove,"Diminuer le compte")}
                        Text("${c.count}",style=MaterialTheme.typography.headlineMedium)
                        OutlinedIconButton(onClick={write(c.copy(count=c.count+1,isHumanVerified=true))}){Icon(Icons.Default.Add,"Augmenter le compte")}
                        Spacer(Modifier.weight(1f));IconButton(onClick={onUpdate(a.copy(counts=a.counts.filterNot{it.id==c.id}))}){Icon(Icons.Default.DeleteOutline,"Supprimer ce comptage")}
                    }
                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(c.isExhaustive,{write(c.copy(isExhaustive=it,isHumanVerified=true))});Text("Toutes les instances ont été comptées",style=MaterialTheme.typography.bodySmall)}
                    TextButton(onClick={val ids=a.boxes.filter{it.label==c.label}.map{it.id};write(c.copy(count=ids.size,linkedInstanceIds=ids,isHumanVerified=true))}){Text(stringResource(R.string.editor_count_boxes))}
                }
            }
        }
        Button(onClick={onUpdate(a.copy(counts=a.counts+CountingTarget(newId(),classes.firstOrNull()?:"object",0,isHumanVerified=true)))}){Text(stringResource(R.string.editor_add_count))}
    }
}

@Composable
fun QualityEditorTab(a: SampleAnnotations, onUpdate: (SampleAnnotations) -> Unit) {
    fun write(q: QualityAuditTarget){onUpdate(a.copy(quality=q))}
    EditorPanel("Décision qualité", "Non annoté, absent, présent mais non localisable et incertain sont des états distincts.") {
        listOf(
            Triple("Cible absente, après vérification",a.quality.verifiedNegativeQueries.isNotEmpty(),0),
            Triple("Négatif difficile (hard negative)",a.quality.isHardNegative,1),
            Triple("Cible présente, mais non localisable",a.quality.isUnlocalizablePresent,2),
            Triple("Cas incertain / ambigu",a.quality.isUncertain,3)
        ).forEach { (label,checked,id) ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked,{value->write(when(id){
                    0->a.quality.copy(verifiedNegativeQueries=if(value) listOf("cible du projet") else emptyList())
                    1->a.quality.copy(isHardNegative=value)
                    2->a.quality.copy(isUnlocalizablePresent=value)
                    else->a.quality.copy(isUncertain=value)
                })});Text(label,style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(a.quality.verifiedNegativeQueries.isNotEmpty()) OutlinedTextField(a.quality.verifiedNegativeQueries.joinToString(", "),{write(a.quality.copy(verifiedNegativeQueries=it.split(',').map(String::trim).filter(String::isNotBlank)))},label={Text(stringResource(R.string.editor_verified_absences))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(a.quality.auditNotes,{write(a.quality.copy(auditNotes=it))},label={Text(stringResource(R.string.editor_audit_notes))},minLines=3,modifier=Modifier.fillMaxWidth())
        Text("Un résultat vide du modèle ne justifie pas à lui seul un négatif. Différez le cas lorsque vous ne pouvez pas conclure.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
