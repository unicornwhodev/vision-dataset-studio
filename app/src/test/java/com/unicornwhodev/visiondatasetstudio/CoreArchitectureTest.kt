package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.geometry.LetterboxMath
import com.unicornwhodev.visiondatasetstudio.core.geometry.NormalizedPoint
import com.unicornwhodev.visiondatasetstudio.core.geometry.NormalizedRect
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.export.WebDatasetTarWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class CoreArchitectureTest {

    @Test
    fun testNormalizedPointClamp() {
        val pt = NormalizedPoint(-0.5f, 1.5f).clamp()
        assertEquals(0f, pt.x, 0.001f)
        assertEquals(1f, pt.y, 0.001f)
    }

    @Test
    fun testNormalizedRectConversions() {
        val rect = NormalizedRect(0.1f, 0.2f, 0.7f, 0.8f)
        val coco = rect.toCocoPx(1000, 1000)
        assertEquals(100.0, coco[0], 0.001) // x
        assertEquals(200.0, coco[1], 0.001) // y
        assertEquals(600.0, coco[2], 0.001) // width
        assertEquals(600.0, coco[3], 0.001) // height

        val reconstructed = NormalizedRect.fromCocoPx(coco[0], coco[1], coco[2], coco[3], 1000, 1000)
        assertEquals(rect.xmin, reconstructed.xmin, 0.001f)
        assertEquals(rect.ymin, reconstructed.ymin, 0.001f)
        assertEquals(rect.xmax, reconstructed.xmax, 0.001f)
        assertEquals(rect.ymax, reconstructed.ymax, 0.001f)

        val yolo = rect.toYolo()
        assertEquals(0.4f, yolo[0], 0.001f) // center x
        assertEquals(0.5f, yolo[1], 0.001f) // center y
        assertEquals(0.6f, yolo[2], 0.001f) // width
        assertEquals(0.6f, yolo[3], 0.001f) // height
    }

    @Test
    fun testLetterboxCalculations() {
        val lb = LetterboxMath.calculateLetterbox(1920, 1080, 300, 300)
        assertTrue(lb.scale > 0f)
        assertEquals(0f, lb.padX, 0.001f) // Since 16:9 width is constrained first
        assertTrue(lb.padY > 0f)
    }

    @Test
    fun testSha256Checksum() {
        val data = "Hello Vision Dataset Studio".toByteArray(Charsets.UTF_8)
        val hash = HashUtils.computeSha256(data)
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun testWebDatasetTarArchiveCreation() {
        val outputStream = ByteArrayOutputStream()
        WebDatasetTarWriter(outputStream).use { writer ->
            writer.addBytes("sample_001.txt", "Sample Data".toByteArray(Charsets.UTF_8))
            writer.addBytes("sample_001.json", "{\"key\":\"value\"}".toByteArray(Charsets.UTF_8))
        }

        val tarBytes = outputStream.toByteArray()
        assertTrue(tarBytes.isNotEmpty())
        // TAR files must always be multiples of 512 bytes
        assertEquals(0, tarBytes.size % 512)
        // Contains POSIX magic 'ustar'
        val tarString = String(tarBytes, Charsets.US_ASCII)
        assertTrue(tarString.contains("ustar"))
        assertTrue(tarString.contains("sample_001.txt"))
        assertTrue(tarString.contains("sample_001.json"))
    }

    @Test
    fun testCanonicalSampleJsonSerialization() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CanonicalDatasetSample::class.java)

        val sample = CanonicalDatasetSample(
            sample_id = "sample_test_001",
            asset_id = "asset_001",
            dataset_source = "beans",
            source_revision = "main",
            source_config = "default",
            source_split = "train",
            source_row_index = 0L,
            group_id = null,
            split = "train",
            media = CanonicalMediaInfo(
                filename = "sample_test_001.jpg",
                width = 500,
                height = 500,
                mime_type = "image/jpeg",
                sha256 = "dummy_sha",
                phash = null,
                original_url = "https://example.com/test.jpg"
            ),
            annotations = SampleAnnotations(
                boxes = listOf(
                    BoxTarget("b1", 0.1f, 0.1f, 0.5f, 0.5f, "healthy", isHumanVerified = true)
                ),
                captions = listOf(
                    CaptionTarget("c1", "A healthy bean leaf", language = "en", isHumanVerified = true)
                )
            ),
            review_status = "VALIDATED",
            audit = CanonicalAuditInfo(
                created_at = 1000L,
                updated_at = 2000L,
                validated_by = "curator",
                model_assist_used = false
            )
        )

        val json = adapter.toJson(sample)
        assertTrue(json.contains("sample_test_001"))
        assertTrue(json.contains("healthy"))
        assertTrue(json.contains("A healthy bean leaf"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals("sample_test_001", deserialized?.sample_id)
        assertEquals(1, deserialized?.annotations?.boxes?.size)
        assertEquals("healthy", deserialized?.annotations?.boxes?.first()?.label)
    }
}
