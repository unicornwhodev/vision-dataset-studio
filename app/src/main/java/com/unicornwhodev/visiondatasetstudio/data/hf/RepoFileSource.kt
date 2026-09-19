package com.unicornwhodev.visiondatasetstudio.data.hf

import com.unicornwhodev.visiondatasetstudio.core.geometry.NormalizedRect
import com.unicornwhodev.visiondatasetstudio.data.model.BoxTarget
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Handles Hugging Face repository file navigation (Path B),
 * parsing ImageFolder, JSONL/CSV manifests, and COCO / YOLO import formats.
 */
class RepoFileSource(
    private val client: OkHttpClient,
    private val tokenProvider: () -> String?
) {
    private val moshi = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi

    private fun newRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        builder.header("User-Agent", "VisionDatasetStudio-Android/1.0")
        return builder
    }

    suspend fun listFilesTree(repoId: String, path: String = "", revision: String = "main"): List<RepoTreeItem> = withContext(Dispatchers.IO) {
        val cleanRepo = repoId.trim().removePrefix("https://huggingface.co/datasets/").removePrefix("datasets/")
        val url = "https://huggingface.co/api/datasets/$cleanRepo/tree/$revision/$path"
        val request = newRequestBuilder(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
                val adapter = moshi.adapter<List<Map<String, Any?>>>(type)
                val list = adapter.fromJson(body) ?: emptyList()
                list.map { map ->
                    RepoTreeItem(
                        type = map["type"] as? String ?: "file",
                        path = map["path"] as? String ?: "",
                        size = (map["size"] as? Number)?.toLong() ?: 0L,
                        oid = map["oid"] as? String
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a YOLO annotation text format: lines of `class_id x_center y_center width height`
     */
    fun parseYoloAnnotation(
        content: String,
        classMap: Map<Int, String>
    ): List<BoxTarget> {
        val result = mutableListOf<BoxTarget>()
        content.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 5) {
                val classIdx = parts[0].toIntOrNull() ?: 0
                val xc = parts[1].toFloatOrNull() ?: 0f
                val yc = parts[2].toFloatOrNull() ?: 0f
                val w = parts[3].toFloatOrNull() ?: 0f
                val h = parts[4].toFloatOrNull() ?: 0f
                val rect = NormalizedRect.fromYolo(xc, yc, w, h)
                val label = classMap[classIdx] ?: "class_$classIdx"
                result.add(
                    BoxTarget(
                        id = UUID.randomUUID().toString().take(8),
                        xmin = rect.xmin,
                        ymin = rect.ymin,
                        xmax = rect.xmax,
                        ymax = rect.ymax,
                        label = label,
                        isHumanVerified = false,
                        sourceProvenance = "import_yolo"
                    )
                )
            }
        }
        return result
    }

    /**
     * Parses COCO format annotations for a given image id and image dimensions.
     */
    fun parseCocoBoxes(
        annotationsList: List<Map<String, Any?>>,
        categoriesMap: Map<Int, String>,
        targetImageId: Long,
        imgWidth: Int,
        imgHeight: Int
    ): List<BoxTarget> {
        val result = mutableListOf<BoxTarget>()
        for (item in annotationsList) {
            val imgId = (item["image_id"] as? Number)?.toLong() ?: -1L
            if (imgId != targetImageId) continue

            val catId = (item["category_id"] as? Number)?.toInt() ?: 0
            val label = categoriesMap[catId] ?: "cat_$catId"

            @Suppress("UNCHECKED_CAST")
            val bbox = item["bbox"] as? List<Number>
            if (bbox != null && bbox.size >= 4) {
                val x = bbox[0].toDouble()
                val y = bbox[1].toDouble()
                val w = bbox[2].toDouble()
                val h = bbox[3].toDouble()
                val rect = NormalizedRect.fromCocoPx(x, y, w, h, imgWidth, imgHeight)
                result.add(
                    BoxTarget(
                        id = UUID.randomUUID().toString().take(8),
                        xmin = rect.xmin,
                        ymin = rect.ymin,
                        xmax = rect.xmax,
                        ymax = rect.ymax,
                        label = label,
                        isHumanVerified = false,
                        sourceProvenance = "import_coco"
                    )
                )
            }
        }
        return result
    }
}

data class RepoTreeItem(
    val type: String,
    val path: String,
    val size: Long,
    val oid: String?
)
