package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unicornwhodev.visiondatasetstudio.data.model.AuditLogEntity
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityDashboardScreen(viewModel: MainViewModel) {
    val samples by viewModel.batchSamples.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val logsFlow = remember(project?.id) { viewModel.db.auditDao().getRecentLogs(project?.id ?: 1L) }
    val logs by logsFlow.collectAsState(initial = emptyList())

    val totalCases = samples.size
    val validatedCases = samples.count { it.annotationStatus == "VALIDATED" }
    val rejectedCases = samples.count { it.annotationStatus == "REJECTED" }
    val deferredCases = samples.count { it.annotationStatus == "DEFERRED" }

    // Disk usage
    val metrics by produceState(0L to 0L, samples) {
        value = withContext(Dispatchers.IO) { viewModel.storageManager.getFreeSpaceBytes() / (1024 * 1024) to viewModel.storageManager.getUsedSpaceBytes() / (1024 * 1024) }
    }
    val freeSpaceMb = metrics.first
    val usedSpaceMb = metrics.second

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Métriques de Qualité & Audit", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.BatchGrid) },
                        modifier = Modifier.testTag("dashboard_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Budget Card
            item {
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Stockage Borné & Quota Disque", fontWeight = FontWeight.Bold)
                        }
                        Text("Espace utilisé par l'application : ${usedSpaceMb} Mo / ${project?.diskBudgetMb ?: 500} Mo (Budget)", fontSize = 13.sp)
                        Text("Espace libre disponible sur l'appareil : ${freeSpaceMb} Mo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = { (usedSpaceMb.toFloat() / (project?.diskBudgetMb ?: 500L)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quality metrics overview
            item {
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Indicateurs du lot actif", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricColumn("Total", "$totalCases", MaterialTheme.colorScheme.primary)
                            MetricColumn("Validés", "$validatedCases", Color(0xFF10B981))
                            MetricColumn("Rejetés", "$rejectedCases", MaterialTheme.colorScheme.error)
                            MetricColumn("Différés", "$deferredCases", Color(0xFFF59E0B))
                        }
                    }
                }
            }

            // Audit Logs Title
            item {
                Text("Journal d'audit horodaté", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Audit Logs List
            if (logs.isEmpty()) {
                item {
                    Text("Aucun événement d'audit enregistré.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(logs) { log ->
                    AuditLogItem(log)
                }
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AuditLogItem(log: AuditLogEntity) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (log.action) {
                    "VALIDATE" -> Icons.Default.CheckCircle
                    "REJECT" -> Icons.Default.Cancel
                    "DEFER" -> Icons.Default.Schedule
                    "PUBLISHED_AND_PURGED" -> Icons.Default.CloudDone
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = when (log.action) {
                    "VALIDATE" -> Color(0xFF10B981)
                    "REJECT" -> MaterialTheme.colorScheme.error
                    "DEFER" -> Color(0xFFF59E0B)
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(timeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(log.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
