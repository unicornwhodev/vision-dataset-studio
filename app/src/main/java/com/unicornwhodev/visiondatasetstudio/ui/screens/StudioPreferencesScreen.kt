package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.ui.MainViewModel
import com.unicornwhodev.visiondatasetstudio.ui.components.*

@Composable
private fun PreferenceToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioPreferencesScreen(viewModel: MainViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    var chosenTasks by remember(project?.activeTasksCsv) { mutableStateOf(StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION")) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { StudioTopBar("Réglages", onBack = viewModel::back) }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StudioSection("Apparence", "Réglages enregistrés automatiquement sur cet appareil.", Icons.Default.Palette) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { theme -> FilterChip(selected = prefs.theme == theme, onClick = { viewModel.updatePreferences(prefs.copy(theme = theme)) }, label = { Text(when(theme) { ThemeMode.SYSTEM -> "Système"; ThemeMode.LIGHT -> "Clair"; ThemeMode.DARK -> "Sombre" }) }) }
                    }
                    Text("Taille des miniatures", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridDensity.entries.forEach { d -> FilterChip(selected = prefs.gridDensity == d, onClick = { viewModel.updatePreferences(prefs.copy(gridDensity = d)) }, label = { Text(if(d == GridDensity.COMPACT) "Compactes" else "Confortables") }) }
                    }
                    PreferenceToggle("Repères sur l’image", "Classes visibles.", prefs.showCanvasLabels) { viewModel.updatePreferences(prefs.copy(showCanvasLabels = it)) }
                }
                StudioSection("Gestes & rythme", icon = Icons.Default.TouchApp) {
                    PreferenceToggle("Passer à l’image suivante", "Après validation.", prefs.autoAdvance) { viewModel.updatePreferences(prefs.copy(autoAdvance = it)) }
                    PreferenceToggle("Mode gaucher", "Valider à gauche.", prefs.leftHanded) { viewModel.updatePreferences(prefs.copy(leftHanded = it)) }
                    PreferenceToggle("Afficher les conseils", "Repères dans l’atelier.", prefs.showGuidance) { viewModel.updatePreferences(prefs.copy(showGuidance = it)) }
                    Text("Langue des légendes", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("fr" to "Français", "en" to "Anglais").forEach { (code, label) -> FilterChip(selected = prefs.captionLanguage == code, onClick = { viewModel.updatePreferences(prefs.copy(captionLanguage = code)) }, label = { Text(label) }) }
                    }
                }
                StudioSection("Outils", "Qualité reste toujours accessible. Masquer un outil n’efface rien.", Icons.Default.Widgets) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTask.entries.forEach { task -> FilterChip(selected = task in chosenTasks, onClick = { chosenTasks = StudioWorkflow.toggleTask(chosenTasks, task) }, label = { Text(task.title) }) }
                    }
                    Button(onClick = { viewModel.updateTasks(chosenTasks) }, enabled = !busy && chosenTasks != StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION")) { Text("Appliquer") }
                }
                StudioDetails("L’adaptation est explicite : aucun apprentissage caché de vos décisions, aucune modification automatique des classes ou des annotations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
