package com.unicornwhodev.visiondatasetstudio.core.workflow

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
/** Pure Kotlin policies: shared by UI and regression tests. No Android dependency. */
enum class StudioTask(private val titleText: () -> String, private val hintText: () -> String) {
    POINTING({ tr("Point unique", "Single point") }, { tr("Une cible localisée, absente ou non localisable", "One located, absent or unlocatable target") }),
    POINTING_MULTI({ tr("Points multiples", "Multiple points") }, { tr("Plusieurs instances dans une image", "Multiple instances in an image") }),
    DETECTION({ tr("Détection", "Detection") }, { tr("Boîtes englobantes et classes d’objets", "Bounding boxes and object classes") }),
    SEGMENTATION({ tr("Masques", "Masks") }, { tr("Segmentation et correction au pinceau", "Segmentation and brush corrections") }),
    CAPTIONING({ tr("Légendes", "Captions") }, { tr("Descriptions courtes, détaillées et multilingues", "Short, detailed and multilingual descriptions") }),
    CLASSIFICATION({ "Tags & classes" }, { tr("Étiquettes et classification multi-label", "Tags and multilabel classification") }),
    GROUNDING({ tr("Texte ↔ région", "Text ↔ region") }, { tr("Relier une expression à des points ou des boîtes", "Link an expression to points or boxes") }),
    VQA({ tr("Questions / réponses", "Questions / answers") }, { tr("Paires visuelles, avec abstention explicite", "Visual pairs, with explicit abstention") }),
    COUNTING({ tr("Comptage", "Counting") }, { tr("Nombre d’instances, exhaustif ou partiel", "Instance count, exhaustive or partial") }),
    NEGATIVE({ tr("Qualité & négatifs", "Quality & negatives") }, { tr("Absence vérifiée, ambiguïté et audit", "Verified absence, ambiguity and audit") });
    val title get() = titleText()
    val hint get() = hintText()
}

data class WorkflowPreset(val id: String, val title: String, val subtitle: String, val tasks: Set<StudioTask>)

object StudioWorkflow {
    val presets get() = listOf(
        WorkflowPreset("point", tr("Pointer une cible", "Point to a target"), tr("Un point par image", "One point per image"), setOf(StudioTask.POINTING)),
        WorkflowPreset("multi", tr("Pointer plusieurs cibles", "Point to multiple targets"), tr("Toutes les instances utiles", "All relevant instances"), setOf(StudioTask.POINTING_MULTI)),
        WorkflowPreset("detect", tr("Détecter des objets", "Detect objects"), tr("Boîtes et classes", "Boxes and classes"), setOf(StudioTask.DETECTION)),
        WorkflowPreset("segment", tr("Segmenter des régions", "Segment regions"), tr("Masques et corrections", "Masks and corrections"), setOf(StudioTask.SEGMENTATION)),
        WorkflowPreset("caption", tr("Décrire des images", "Describe images"), tr("Légendes et tags", "Captions and tags"), setOf(StudioTask.CAPTIONING, StudioTask.CLASSIFICATION)),
        WorkflowPreset("vl", tr("Préparer un corpus VL", "Prepare a VL corpus"), tr("Régions, questions et descriptions", "Regions, questions and descriptions"), setOf(StudioTask.DETECTION, StudioTask.GROUNDING, StudioTask.CAPTIONING, StudioTask.VQA)),
        WorkflowPreset("sort", tr("Trier et qualifier", "Sort and assess"), tr("Tags et contrôle qualité", "Tags and quality checks"), setOf(StudioTask.CLASSIFICATION, StudioTask.NEGATIVE))
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
enum class GridDensity(val minCellDp: Int) { COMFORTABLE(136), COMPACT(112) }

data class StudioPreferences(
    val theme: ThemeMode = ThemeMode.DARK,
    val gridDensity: GridDensity = GridDensity.COMFORTABLE,
    val autoAdvance: Boolean = true,
    val showGuidance: Boolean = true,
    val leftHanded: Boolean = false,
    val showCanvasLabels: Boolean = true,
    val captionLanguage: String = "fr"
)
