package com.unicornwhodev.visiondatasetstudio.core.workflow

/** Pure Kotlin policies: shared by UI and regression tests. No Android dependency. */
enum class StudioTask(val title: String, val hint: String) {
    POINTING("Point unique", "Une cible localisée, absente ou non localisable"),
    POINTING_MULTI("Points multiples", "Plusieurs instances dans une image"),
    DETECTION("Détection", "Boîtes englobantes et classes d’objets"),
    CAPTIONING("Légendes", "Descriptions courtes, détaillées et multilingues"),
    CLASSIFICATION("Tags & classes", "Étiquettes et classification multi-label"),
    GROUNDING("Texte ↔ région", "Relier une expression à des points ou des boîtes"),
    VQA("Questions / réponses", "Paires visuelles, avec abstention explicite"),
    COUNTING("Comptage", "Nombre d’instances, exhaustif ou partiel"),
    NEGATIVE("Qualité & négatifs", "Absence vérifiée, ambiguïté et audit")
}

data class WorkflowPreset(val id: String, val title: String, val subtitle: String, val tasks: Set<StudioTask>)

object StudioWorkflow {
    val presets = listOf(
        WorkflowPreset("point", "Pointer une cible", "Un point par image", setOf(StudioTask.POINTING)),
        WorkflowPreset("multi", "Pointer plusieurs cibles", "Toutes les instances utiles", setOf(StudioTask.POINTING_MULTI)),
        WorkflowPreset("detect", "Détecter des objets", "Boîtes et classes", setOf(StudioTask.DETECTION)),
        WorkflowPreset("caption", "Décrire des images", "Légendes et tags", setOf(StudioTask.CAPTIONING, StudioTask.CLASSIFICATION)),
        WorkflowPreset("vl", "Préparer un corpus VL", "Régions, questions et descriptions", setOf(StudioTask.DETECTION, StudioTask.GROUNDING, StudioTask.CAPTIONING, StudioTask.VQA)),
        WorkflowPreset("sort", "Trier et qualifier", "Tags et contrôle qualité", setOf(StudioTask.CLASSIFICATION, StudioTask.NEGATIVE))
    )
    fun parseTasks(csv: String): Set<StudioTask> = csv.split(',').mapNotNull { name ->
        StudioTask.entries.firstOrNull { it.name == name.trim() }
    }.toSet().ifEmpty { setOf(StudioTask.DETECTION) }

    fun toggleTask(tasks: Set<StudioTask>, task: StudioTask): Set<StudioTask> {
        if (task in tasks) return (tasks - task).ifEmpty { tasks }
        val compatible = when (task) {
            StudioTask.POINTING -> tasks - StudioTask.POINTING_MULTI
            StudioTask.POINTING_MULTI -> tasks - StudioTask.POINTING
            else -> tasks
        }
        return compatible + task
    }

    fun tasksCsv(tasks: Set<StudioTask>) = StudioTask.entries.filter { it in tasks }.joinToString(",") { it.name }
    fun isPending(status: String) = status in setOf("PENDING", "PROPOSALS_AVAILABLE", "IN_PROGRESS")
    fun canEdit(acquisition: String, sync: String, hasFile: Boolean) =
        acquisition == "AVAILABLE" && sync !in setOf("VERIFIED", "PURGED", "PUBLISHING", "PUBLISHED") && hasFile

    /** Reject arbitrary hosts and deep paths instead of silently sending credentials to them. */
    fun normalizeRepo(input: String, destination: Boolean = false): String? {
        var value = input.trim().removeSuffix("/")
        if (value.startsWith("https://huggingface.co/datasets/")) {
            value = value.removePrefix("https://huggingface.co/datasets/").substringBefore('?').substringBefore('#').trimEnd('/')
        }
        if (value.startsWith("datasets/")) value = value.removePrefix("datasets/")
        val parts = value.split('/')
        if (parts.size !in (if (destination) 2..2 else 1..2)) return null
        if (parts.any { it.isBlank() || it == "." || it == ".." || !it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) }) return null
        return value
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class GridDensity(val minCellDp: Int) { COMFORTABLE(152), COMPACT(112) }

data class StudioPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val gridDensity: GridDensity = GridDensity.COMFORTABLE,
    val autoAdvance: Boolean = true,
    val showGuidance: Boolean = true,
    val leftHanded: Boolean = false,
    val showCanvasLabels: Boolean = true,
    val captionLanguage: String = "fr"
)
