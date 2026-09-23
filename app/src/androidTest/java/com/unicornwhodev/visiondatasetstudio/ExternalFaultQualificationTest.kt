package com.unicornwhodev.visiondatasetstudio

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.*
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

/** Explicit host-orchestrated tests. They use DocumentsUI and real OS failures, never the fault provider. */
class ExternalFaultQualificationTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val case: String get() = requireNotNull(args.getString("faultCase")).also {
        require(it.matches(Regex("[a-f0-9]{12}")))
    }
    private val root get() = File(target.filesDir, "qa-evidence/external-faults/$case")
    private val context: Context get() = object : ContextWrapper(target) {
        override fun getFilesDir() = File(root, "studio/files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "studio/cache").apply { mkdirs() }
    }
    private fun open() = Room.databaseBuilder(target, AppDatabase::class.java, "qa-real-faults-$case.db").build()
    private fun document() = Uri.parse(JSONObject(File(root, "document.json").readText()).getString("uri"))
    private fun proof(name: String, data: JSONObject) = File(root, "$name.json").writeText(data.toString(2))

    @Test fun prepareAndRequestRealDocument() = runBlocking {
        check(!root.exists()) { "Never replace an existing fault case" }
        root.mkdirs()
        val db = open(); val storage = StorageManager(context); val hf = HfApiClient { null }; val runtime = LiteRtEngine()
        try {
            val project = ProjectEntity(id=710001, name="Synthetic real-storage QA", classesCsv="synthetic", activeTasksCsv="CLASSIFICATION")
            db.projectDao().saveProject(project)
            db.batchDao().insertOrReplace(BatchEntity(project.id, 1, "IN_PROGRESS", totalCases=1))
            val image = storage.getImageFile("fault-$case", "png")
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }.let { bitmap ->
                image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
            }
            image.copyTo(File(root, "original.png"), overwrite=false)
            val sample = SampleEntity("fault-$case", project.id, 1, "fault-$case", 0, sourceFileUrl=null,
                localImagePath=image.path, imageWidth=32, imageHeight=32, sha256=HashUtils.computeSha256(image),
                acquisitionStatus="AVAILABLE", annotationStatus="IN_PROGRESS", syncStatus="NOT_EXPORTED")
            db.sampleDao().insertSamples(listOf(sample))
            val exporters = DatasetExporters(storage, hf)
            val engine = BatchEngine(db, storage, hf, runtime, exporters)
            engine.saveSampleAnnotations(sample.sampleId, SampleAnnotations(tags=listOf(TagTarget("human-$case", "synthetic", isHumanVerified=true))))
            engine.validateSample(sample.sampleId, 1)
            val accepted = db.sampleDao().getSampleSync(sample.sampleId)!!
            val archive = exporters.packageBatchToLocalZip(project, 1, listOf(accepted to engine.getSampleAnnotations(sample.sampleId)), false, true, false, false, false)
            assertTrue(archive.error, archive.success)
            engine.recordLocalArchive(project.id, 1, archive.zipFile!!)
            proof("expected", JSONObject().put("image_path", image.path).put("image_sha256", HashUtils.computeSha256(image))
                .put("archive_path", archive.zipFile!!.path).put("archive_sha256", HashUtils.computeSha256(archive.zipFile!!))
                .put("annotation_json", db.annotationDao().getAnnotationSync(sample.sampleId)!!.dataJson))
        } finally { runtime.close(); db.close() }
        target.startActivity(Intent().setClassName(target.packageName, target.packageName + ".qa.QaDocumentActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("faultCase", case))
        val marker = File(root, "document.json")
        val deadline = System.currentTimeMillis() + 180000
        while (!marker.exists() && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertTrue("Complete the real DocumentsUI selection", marker.isFile)
        assertEquals(-1, JSONObject(marker.readText()).getInt("result_code"))
        assertTrue(target.contentResolver.persistedUriPermissions.any { it.uri == document() && it.isReadPermission && it.isWritePermission })
    }

    @Test fun verifyCopyAndOptionallyRevokeGrant() = runBlocking {
        val expected = JSONObject(File(root, "expected.json").readText())
        val uri = document(); val source = File(expected.getString("archive_path"))
        val receipt = SafArchives.copyVerified(target.contentResolver, source, uri)
        assertTrue(receipt.persistentRead)
        val db = open(); val storage = StorageManager(context); val hf = HfApiClient { null }; val runtime = LiteRtEngine()
        try {
            BatchEngine(db, storage, hf, runtime, DatasetExporters(storage, hf)).verifyLocalArchive(710001, 1, uri.toString())
            assertEquals("VERIFIED", db.batchDao().getBatchSync(710001, 1)!!.status)
            proof("copied", JSONObject().put("bytes", receipt.bytes).put("sha256", receipt.sha256).put("persisted_read", true))
            if (args.getString("revokeGrant") == "true") {
                target.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                assertFalse(target.contentResolver.persistedUriPermissions.any { it.uri == uri })
                proof("revoked", JSONObject().put("persisted_grant_removed", true))
            }
        } finally { runtime.close(); db.close() }
    }

    @Test fun unavailableBackupRefusesPurgeAndPreservesHumanData() = runBlocking {
        val expected = JSONObject(File(root, "expected.json").readText())
        val uri = document()
        assertThrows(Exception::class.java) { target.contentResolver.openInputStream(uri)!!.use { it.read() } }
        val db = open(); val storage = StorageManager(context); val hf = HfApiClient { null }; val runtime = LiteRtEngine()
        try {
            val engine = BatchEngine(db, storage, hf, runtime, DatasetExporters(storage, hf))
            var refused = false
            try { engine.purgeReviewedBatch(710001, 1, true) } catch (_: IllegalStateException) { refused = true }
            assertTrue("Unavailable backup must forbid cleanup", refused)
            assertEquals("VERIFIED", db.batchDao().getBatchSync(710001, 1)!!.status)
            assertEquals(expected.getString("annotation_json"), db.annotationDao().getAnnotationSync("fault-$case")!!.dataJson)
            assertEquals(expected.getString("image_sha256"), HashUtils.computeSha256(File(expected.getString("image_path"))))
            assertEquals(expected.getString("image_sha256"), HashUtils.computeSha256(File(root, "original.png")))
            assertEquals(expected.getString("archive_sha256"), HashUtils.computeSha256(File(expected.getString("archive_path"))))
            proof("refused", JSONObject().put("backup_unavailable", true).put("purge_refused", true)
                .put("annotation_preserved", true).put("image_preserved", true).put("private_archive_preserved", true))
        } finally { runtime.close(); db.close() }
    }

    @Test fun realEnospcPreservesPreviousAtomicExport() {
        require(args.getString("quotaMounted") == "true")
        root.mkdirs()
        val directory = File(root, "quota")
        check(directory.isDirectory)
        val target = File(directory, "previous.bin")
        check(!target.exists())
        val original = ByteArray(64 * 1024) { (it % 251).toByte() }
        target.writeBytes(original)
        var failure: IOException? = null
        try { DurableFiles.replace(target) { out -> repeat(128) { out.write(original) } } }
        catch (e: IOException) { failure = e }
        assertNotNull("The real limited filesystem must reject the write", failure)
        val causes = generateSequence(failure as Throwable?) { it.cause }.map { it.toString() }.joinToString("\n")
        assertTrue("Expected real ENOSPC, not a simulated exception: $causes", causes.contains("ENOSPC"))
        assertArrayEquals(original, target.readBytes())
        assertEquals(listOf("previous.bin"), directory.listFiles()!!.map { it.name }.sorted())
        proof("enospc", JSONObject().put("errno", "ENOSPC").put("previous_export_preserved", true)
            .put("pending_file_removed", true).put("bytes", target.length()))
    }
}
