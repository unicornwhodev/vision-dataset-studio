package com.unicornwhodev.visiondatasetstudio.core.storage

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import android.content.Context
import android.graphics.BitmapFactory
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages bounded app-private storage, cache limits, disk quotas, and verified purge.
 */
class StorageManager(val context: Context) {
    fun batchExportDir(projectId: Long, batchNumber: Int): File =
        File(exportsDir, "p-$projectId-batch-%06d".format(java.util.Locale.US, batchNumber))
    fun batchArchiveFile(projectId: Long, batchNumber: Int): File = File(exportsDir, batchExportDir(projectId, batchNumber).name + ".zip")


    /** Random app-install namespace; not a hardware identifier. Prevents two devices overwriting batch 1. */
    fun publicationNamespace(projectId:Long):String {
        val prefs=context.getSharedPreferences("studio_publication",Context.MODE_PRIVATE)
        val id=prefs.getString("namespace",null) ?: java.util.UUID.randomUUID().toString().also{prefs.edit().putString("namespace",it).commit()}
        return "project-$projectId-$id"
    }
    private val imagesDir: File get() = File(context.filesDir, "images").apply { mkdirs() }
    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    val exportsDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }
    val tempDir: File get() = File(context.cacheDir, "temp").apply { mkdirs() }

    fun getFreeSpaceBytes(): Long {
        val stat = StatFs(context.filesDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun getTotalSpaceBytes(): Long {
        val stat = StatFs(context.filesDir.path)
        return stat.blockCountLong * stat.blockSizeLong
    }

    fun getUsedSpaceBytes(): Long {
        return calculateDirSize(context.filesDir) + calculateDirSize(context.cacheDir) + context.getDatabasePath("vision_dataset_studio.db").length() + File(context.getDatabasePath("vision_dataset_studio.db").path + "-wal").length()
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    fun hasAvailableBudget(requiredBytes: Long, budgetMb: Long, reserveMb: Int = 64): Boolean {
        require(requiredBytes >= 0 && reserveMb >= 0)
        val currentUsed = getUsedSpaceBytes()
        val budgetBytes = budgetMb * 1024 * 1024
        val freeBytes = getFreeSpaceBytes()

        if (freeBytes < requiredBytes + reserveMb * 1024L * 1024) { // Respect the configured reserve
            return false
        }
        return (currentUsed + requiredBytes) <= budgetBytes
    }

    fun getImageFile(sampleId: String, extension: String = "jpg"): File {
        val safeName = sampleId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(imagesDir, "$safeName.$extension")
    }

    fun ownedImage(path: String): File = DurableFiles.ownedFile(imagesDir, path)

    /** No directory scan: known paths for one sample only; never touches a SAF source. */
    fun removeImageCheckpoints(sampleId: String) {
        for (ext in listOf("jpg", "png", "webp")) {
            val file = getImageFile(sampleId, ext)
            for (suffix in listOf(".part", ".range", ".normalize")) {
                val child = File(file.path + suffix)
                check(!child.exists() || child.delete()) { tr("Nettoyage du transfert impossible", "Transfer cleanup failed") }
            }
        }
    }

    fun saveImageStream(sampleId: String, stream: InputStream, extension: String = "jpg"): ImageMetadata {
        val file = getImageFile(sampleId, extension)
        FileOutputStream(file).use { out ->
            stream.copyTo(out)
        }
        return readImageMetadata(file)
    }

    fun readImageMetadata(file: File): ImageMetadata {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return ImageMetadata(
            file = file,
            width = options.outWidth,
            height = options.outHeight,
            mimeType = options.outMimeType ?: "image/jpeg",
            fileSizeBytes = file.length()
        )
    }

    /**
     * Purges only verified media and temporary files, preserving audit logs and decisions in DB.
     */
    fun purgeVerifiedBatchMedia(sampleIds: List<String>): Int {
        var count = 0
        for (sampleId in sampleIds) {
            val safeName = sampleId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            imagesDir.listFiles { _, name -> name.substringBeforeLast('.') == safeName }?.forEach { f ->
                if (f.delete()) count++
            }
        }
        clearTempFiles()
        return count
    }

    fun clearTempFiles() {
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun getModelFile(filename: String): File {
        return File(modelsDir, filename)
    }

    data class ImageMetadata(
        val file: File,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val fileSizeBytes: Long
    )
}
