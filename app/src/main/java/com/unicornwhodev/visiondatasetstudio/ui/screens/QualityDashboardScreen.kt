package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.data.model.AuditLogEntity
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QualityDashboardScreen(viewModel: MainViewModel) {
    val samples by viewModel.batchSamples.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val logsFlow = remember(project?.id) { viewModel.db.auditDao().getRecentLogs(project?.id ?: 1L) }
    val logs by logsFlow.collectAsState(initial = emptyList())
    val validated = samples.count { it.annotationStatus == "VALIDATED" }
    val rejected = samples.count { it.annotationStatus == "REJECTED" }
    val deferred = samples.count { it.annotationStatus == "DEFERRED" }
    val reviewed = validated + rejected
    val progress by animateFloatAsState(if (samples.isEmpty()) 0f else reviewed.toFloat() / samples.size, tween(600), label = "review progress")
    val metrics by produceState(0L to 0L, samples) {
        value = withContext(Dispatchers.IO) { viewModel.storageManager.getFreeSpaceBytes() / (1024 * 1024) to viewModel.storageManager.getUsedSpaceBytes() / (1024 * 1024) }
    }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { StudioTopBar(stringResource(R.string.screen_quality), project?.name) }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (samples.isEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Aucun lot à analyser", style = MaterialTheme.typography.titleLarge)
                        Text("Les résultats apparaîtront après l’import des images.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { viewModel.navigateTo(com.unicornwhodev.visiondatasetstudio.ui.Screen.BatchGrid) }) {
                            Text("Ouvrir les lots"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("REVUE DU LOT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$reviewed / ${samples.size}", style = MaterialTheme.typography.headlineLarge)
                                }
                                Text("${(progress * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                            Row(Modifier.fillMaxWidth()) {
                                MetricTile("$validated", "Validés", Modifier.weight(1f))
                                MetricTile("$deferred", "À revoir", Modifier.weight(1f))
                                MetricTile("$rejected", "Rejetés", Modifier.weight(1f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                item {
                    StudioSection("Stockage", icon = Icons.Default.Storage) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${metrics.second} Mo", style = MaterialTheme.typography.titleLarge)
                            Text("/ ${project?.diskBudgetMb ?: 500} Mo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { (metrics.second.toFloat() / (project?.diskBudgetMb ?: 500L).coerceAtLeast(1L)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text("${metrics.first} Mo libres sur l’appareil", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Text("Activité récente", style = MaterialTheme.typography.titleMedium) }
                if (logs.isEmpty()) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Aucune activité enregistrée.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(logs) { AuditLogItem(it) }
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AuditLogItem(log: AuditLogEntity) {
    var expanded by remember(log) { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val title = when (log.action) { "VALIDATE" -> "Image validée"; "REJECT" -> "Image rejetée"; "DEFER" -> "Image à revoir"; "PUBLISHED_AND_PURGED" -> "Publication terminée"; else -> log.action }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize()) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when (log.action) { "VALIDATE" -> Icons.Default.CheckCircleOutline; "REJECT" -> Icons.Default.Block; "DEFER" -> Icons.Default.Schedule; else -> Icons.Default.History },
                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
            }
            Text(format.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
