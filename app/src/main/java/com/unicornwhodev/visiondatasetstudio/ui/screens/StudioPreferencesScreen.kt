package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.foundation.layout.*
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
import com.unicornwhodev.visiondatasetstudio.ui.components.StudioSection

@Composable
private fun PreferenceToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioPreferencesScreen(viewModel: MainViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    val project by viewModel.projectFlow.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    var chosenTasks by remember(project?.activeTasksCsv) { mutableStateOf(StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION")) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { TopAppBar(windowInsets = WindowInsets(0), title = { Text("Votre façon de travailler") }, navigationIcon = {
        IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour à l’atelier") }
    }) }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StudioSection("Outils de cet atelier", "Qualité reste toujours accessible. Masquer un outil n’efface rien.", Icons.Default.Widgets) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTask.entries.forEach { task -> FilterChip(selected = task in chosenTasks, onClick = { chosenTasks = StudioWorkflow.toggleTask(chosenTasks, task) }, label = { Text(task.title) }) }
                    }
                    Button(onClick = { viewModel.updateTasks(chosenTasks) }, enabled = !busy && chosenTasks != StudioWorkflow.parseTasks(project?.activeTasksCsv ?: "DETECTION")) { Text("Appliquer les tâches") }
                }
                StudioSection("Confort visuel", "Réglages enregistrés automatiquement sur cet appareil.", Icons.Default.Palette) {
                    Text("Apparence", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { theme -> FilterChip(selected = prefs.theme == theme, onClick = { viewModel.updatePreferences(prefs.copy(theme = theme)) }, label = { Text(when(theme) { ThemeMode.SYSTEM -> "Système"; ThemeMode.LIGHT -> "Clair"; ThemeMode.DARK -> "Sombre" }) }) }
                    }
                    Text("Taille des miniatures", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridDensity.entries.forEach { d -> FilterChip(selected = prefs.gridDensity == d, onClick = { viewModel.updatePreferences(prefs.copy(gridDensity = d)) }, label = { Text(if(d == GridDensity.COMPACT) "Compactes" else "Confortables") }) }
                    }
                    PreferenceToggle("Repères sur l’image", "Afficher la classe à côté de chaque région.", prefs.showCanvasLabels) { viewModel.updatePreferences(prefs.copy(showCanvasLabels = it)) }
                }
                StudioSection("Rythme d’annotation", icon = Icons.Default.TouchApp) {
                    PreferenceToggle("Enchaîner après validation", "Ouvrir le prochain cas à traiter, sans repasser par la grille.", prefs.autoAdvance) { viewModel.updatePreferences(prefs.copy(autoAdvance = it)) }
                    PreferenceToggle("Commandes pour main gauche", "Placer le bouton de validation à gauche dans l’éditeur.", prefs.leftHanded) { viewModel.updatePreferences(prefs.copy(leftHanded = it)) }
                    PreferenceToggle("Afficher les conseils", "Aides courtes, sans modifier les tâches ou les décisions.", prefs.showGuidance) { viewModel.updatePreferences(prefs.copy(showGuidance = it)) }
                    Text("Langue par défaut des nouvelles légendes", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("fr" to "Français", "en" to "Anglais").forEach { (code, label) -> FilterChip(selected = prefs.captionLanguage == code, onClick = { viewModel.updatePreferences(prefs.copy(captionLanguage = code)) }, label = { Text(label) }) }
                    }
                }
                Text("L’adaptation est explicite : aucun apprentissage caché de vos décisions, aucune modification automatique des classes ou des annotations.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
