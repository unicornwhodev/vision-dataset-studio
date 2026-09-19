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
import com.unicornwhodev.visiondatasetstudio.ui.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
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
                    onClick = { viewModel.navigateTo(item.screen) }, modifier = Modifier.testTag("nav_${item.screen.javaClass.simpleName}"), icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }
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
                    if(activeProject?.id != projectId) CircularProgressIndicator() else StudioRouteMotion("$projectId:$screenKey") { holder.SaveableStateProvider("$projectId:$screenKey") {
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
                    } }
                }
                OperationBanner(progress, busy, viewModel::clearOperationProgress)
                if (showNavigation && !rail) Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp)) {
                        destinations.forEach { item ->
                            val selected = screen == item.screen
                            val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "navigation")
                            val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "selected icon")
                            Column(Modifier.weight(1f).testTag("nav_${item.screen.javaClass.simpleName}").clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = !editorBusy && !busy, role = Role.Tab) { viewModel.navigateTo(item.screen) }
                                .semantics { this.selected = selected }.heightIn(min = 60.dp).padding(bottom = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.width(24.dp).height(2.dp).background(color))
                                Spacer(Modifier.height(3.dp))
                                Icon(item.icon, null, Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.label, style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
