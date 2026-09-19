package com.unicornwhodev.visiondatasetstudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.ui.components.OperationBanner
import com.unicornwhodev.visiondatasetstudio.ui.screens.*

private data class StudioDestination(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun StudioRoot(viewModel: MainViewModel) {
    val screen by viewModel.currentScreen.collectAsState()
    val progress by viewModel.operationProgress.collectAsState()
    val busy by viewModel.isBusy.collectAsState()
    val projectId by viewModel.activeProjectId.collectAsState()
    val activeProject by viewModel.projectFlow.collectAsState()
    val editorBusy by viewModel.editorBusy.collectAsState()
    val holder = rememberSaveableStateHolder()
    val destinations = remember { listOf(
        StudioDestination(Screen.Home, "Atelier", Icons.Default.DashboardCustomize),
        StudioDestination(Screen.BatchGrid, "Lot", Icons.Default.GridView),
        StudioDestination(Screen.Models, "Modèles", Icons.Default.Memory),
        StudioDestination(Screen.Publication, "Export", Icons.Default.IosShare),
        StudioDestination(Screen.QualityDashboard, "Qualité", Icons.Default.Insights)
    ) }
    BackHandler(enabled = screen !is Screen.Home) { viewModel.back() }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        val editor = screen is Screen.AnnotationEditor
        val showNavigation = !editor && screen !is Screen.Setup && screen !is Screen.Preferences && screen !is Screen.Controls
        val rail = maxWidth >= 840.dp && showNavigation
        Row(Modifier.fillMaxSize()) {
            if (rail) NavigationRail(windowInsets = WindowInsets(0), header = {
                Icon(Icons.Default.CenterFocusStrong, "Vision Dataset Studio", Modifier.padding(20.dp), tint = MaterialTheme.colorScheme.primary)
            }) {
                destinations.forEach { item -> NavigationRailItem(
                    selected = screen == item.screen, enabled = !editorBusy && !busy,
                    onClick = { viewModel.navigateTo(item.screen) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }
                ) }
                Spacer(Modifier.weight(1f))
                NavigationRailItem(selected = false, onClick = { viewModel.navigateTo(Screen.Preferences) }, icon = { Icon(Icons.Default.Tune, "Préférences") }, label = { Text("Réglages") })
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    val screenKey = when (val s = screen) {
                        is Screen.AnnotationEditor -> "editor" // One remembered workspace; case-specific drafts use sampleId keys.
                        else -> s.javaClass.simpleName
                    }
                    if(activeProject?.id != projectId) CircularProgressIndicator() else holder.SaveableStateProvider("$projectId:$screenKey") {
                        when (val s = screen) {
                            is Screen.Home -> StudioHomeScreen(viewModel)
                            is Screen.Setup -> SetupScreen(viewModel)
                            is Screen.BatchGrid -> BatchGridScreen(viewModel)
                            is Screen.AnnotationEditor -> AnnotationEditorScreen(s.sampleId, viewModel)
                            is Screen.Models -> ModelLibraryScreen(viewModel)
                            is Screen.Publication -> PublicationScreen(viewModel)
                            is Screen.QualityDashboard -> QualityDashboardScreen(viewModel)
                            is Screen.Preferences -> StudioPreferencesScreen(viewModel)
                            is Screen.Controls -> StudioControlsScreen(viewModel)
                        }
                    }
                }
                OperationBanner(progress, busy, viewModel::clearOperationProgress)
                if (showNavigation && !rail) NavigationBar(windowInsets = WindowInsets(0)) {
                    destinations.forEach { item -> NavigationBarItem(
                        selected = screen == item.screen, enabled = !editorBusy && !busy,
                        onClick = { viewModel.navigateTo(item.screen) }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }
                    ) }
                }
            }
        }
    }
}
