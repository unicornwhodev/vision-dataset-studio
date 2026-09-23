package com.unicornwhodev.visiondatasetstudio

import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Real HTTPS download; the host drops this dedicated emulator app's network packets. No Hub writes. */
class HfInterruptedDownloadTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val root: File get() {
        val case = requireNotNull(InstrumentationRegistry.getArguments().getString("faultCase"))
        require(case.matches(Regex("[a-f0-9]{12}")))
        return File(target.filesDir, "qa-evidence/hf-interruption/$case")
    }
    private val url = "https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder/resolve/1244117f490e36ce321d70baa672753caeaef028/models/hgnetv2_b0/model.tflite"
    private val expectedHash = "0f9ac48fdb098eb2efc64e36279affa5ee3f039b8c602502f07c1d769f9047e7"
    private val expectedBytes = 24117576L

    @Test fun downloadDuringRealPacketDropPreservesOriginalAndCheckpoint() = runBlocking {
        check(!root.exists()); root.mkdirs()
        val destination = File(root, "download.tflite")
        destination.writeText("previous verified file")
        val previous = HashUtils.computeSha256(destination)
        val hf = HfApiClient(OkHttpClient.Builder().readTimeout(8, TimeUnit.SECONDS).build()) { null }
        var gate = false
        val success = hf.downloadImage(url, destination, { copied, total ->
            if (!gate && copied >= 5L * 1024 * 1024) {
                gate = true
                File(root, "network-cut-requested.json").writeText(JSONObject().put("copied", copied).put("total", total).toString())
                val deadline = System.currentTimeMillis() + 90000
                while (!File(root, "network-cut-installed").exists() && System.currentTimeMillis() < deadline) Thread.sleep(100)
                check(File(root, "network-cut-installed").isFile) { "Host did not install the real packet-drop rule" }
            }
        }, expectedBytes)
        assertTrue("A real remote download must have begun", gate)
        assertFalse("The interrupted download must not be promoted", success)
        assertEquals(previous, HashUtils.computeSha256(destination))
        val part = File(root, "download.tflite.part")
        assertTrue(part.length() in 1 until expectedBytes)
        assertTrue(File(root, "download.tflite.range").isFile)
        File(root, "interrupted.json").writeText(JSONObject().put("original_preserved", true)
            .put("checkpoint_bytes", part.length()).put("expected_sha256", expectedHash).toString(2))
    }

    @Test fun resumeAfterNetworkRecoveryMatchesPublishedSha256() = runBlocking {
        check(File(root, "interrupted.json").isFile)
        val destination = File(root, "download.tflite")
        val events = JSONArray()
        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            events.put(JSONObject().put("status", response.code).put("range", chain.request().header("Range"))
                .put("content_range", response.header("Content-Range")))
            response
        }.build()
        assertTrue(HfApiClient(client) { null }.downloadImage(url, destination, maxBytes=expectedBytes))
        assertEquals(expectedBytes, destination.length())
        assertEquals(expectedHash, HashUtils.computeSha256(destination))
        assertFalse(File(root, "download.tflite.part").exists())
        assertFalse(File(root, "download.tflite.range").exists())
        File(root, "resumed.json").writeText(JSONObject().put("sha256", expectedHash).put("bytes", expectedBytes)
            .put("transport", events).put("public_pinned_revision", "1244117f490e36ce321d70baa672753caeaef028").toString(2))
    }
}
