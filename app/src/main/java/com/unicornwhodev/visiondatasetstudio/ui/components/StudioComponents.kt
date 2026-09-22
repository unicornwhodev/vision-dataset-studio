package com.unicornwhodev.visiondatasetstudio.ui.components

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.ui.OperationProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTopBar(title: String, eyebrow: String? = null, onBack: (() -> Unit)? = null,
                 actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).heightIn(min = 52.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", Modifier.size(20.dp)) }
            else Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    }
}

/** A small visible command inside a full-height touch target. */
@Composable
fun StudioAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                 icon: ImageVector? = null, enabled: Boolean = true, primary: Boolean = false) {
    val foreground = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        else if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val background = if (primary && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(5.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(5.dp)).background(background).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = foreground)
            Text(label, style = MaterialTheme.typography.labelMedium, color = foreground, maxLines = 1)
        }
    }
}

/** One content tree: route changes never duplicate editors or their saved drafts. */
@Composable
fun StudioRouteMotion(route: String, content: @Composable () -> Unit) {
    val enter = remember { Animatable(1f) }
    val offset = with(LocalDensity.current) { 4.dp.toPx() }
    LaunchedEffect(route) { enter.snapTo(0f); enter.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value; translationY = offset * (1f - enter.value) }) { content() }
}

@Composable
fun StudioSection(title: String, subtitle: String? = null, icon: ImageVector? = null,
                  modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var help by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) IconButton(onClick = { help = true }) {
                Icon(Icons.Default.HelpOutline, "Aide : $title", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
        HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
    }
    if (help && subtitle != null) AlertDialog(onDismissRequest = { help = false }, title = { Text(title) },
        text = { Text(subtitle) }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.common_understood)) } })
}

@Composable
fun StudioDisclosure(title: String, icon: ImageVector = Icons.Default.Tune, initiallyExpanded: Boolean = false,
                     content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "disclosure")
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Déplié" else "Replié" }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rotation })
            }
            AnimatedVisibility(expanded, enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(160)) + fadeOut()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

@Composable
fun StudioTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // All destinations remain visible at 320 dp; the active rule moves without resizing labels.
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tab rule")
                Column(Modifier.weight(1f).clickable(role = Role.Tab) { onSelect(index) }.semantics { this.selected = active }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 3.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(color))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}

@Composable
fun StatusPill(text: String, icon: ImageVector = Icons.Default.Circle, attention: Boolean = false) {
    Surface(shape = RoundedCornerShape(4.dp), color = if (attention) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(11.dp), tint = if(attention) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 12.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnimatedContent(value, label = "metric") { Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OperationBanner(progress: OperationProgress?, busy: Boolean, onDismiss: () -> Unit) {
    if (progress == null) return
    var expanded by remember(progress.message) { mutableStateOf(false) }
    Surface(color = if (progress.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (progress.isError) Icons.Default.ErrorOutline else if (busy) Icons.Default.Sync else Icons.Default.CheckCircleOutline, null, Modifier.size(20.dp))
                Text(progress.message, Modifier.weight(1f).clickable { expanded = !expanded }, maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (!busy) IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer le message") }
            }
            if (busy) {
                if (progress.total > 1) LinearProgressIndicator(progress = { (progress.current.toFloat() / progress.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun EmptyWorkspace(title: String, message: String, icon: ImageVector, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(icon, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

/** Long guidance stays available on demand without crowding the workspace. */
@Composable
fun StudioDetails(text: String, modifier: Modifier = Modifier,
                  style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
                  color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.Info, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp)); Text(if (expanded) "Moins de détails" else "En savoir plus")
        }
        AnimatedVisibility(expanded) { Text(text, style = style, color = color) }
    }
}
