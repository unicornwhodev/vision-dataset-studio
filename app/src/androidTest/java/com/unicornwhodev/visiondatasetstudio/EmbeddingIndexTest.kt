package com.unicornwhodev.visiondatasetstudio

import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.domain.inference.EmbeddingIndex
import org.junit.Assert.*
import org.junit.Test

class EmbeddingIndexTest {
    @Test fun cosineSearchPersistsAndIsolatesModelContracts() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project=System.currentTimeMillis()
        val index=EmbeddingIndex(context,project)
        index.put("query","sha1","model-contract-A",floatArrayOf(10f,0f))
        index.put("close","sha2","model-contract-A",floatArrayOf(3f,1f))
        index.put("far","sha3","model-contract-A",floatArrayOf(0f,2f))
        index.put("different-contract","sha4","model-contract-B",floatArrayOf(10f,0f))
        val reopened=EmbeddingIndex(context,project)
        val neighbors=reopened.nearest(reopened.get("query")!!)
        assertEquals(listOf("close","far"),neighbors.map{it.sampleId})
        assertEquals(3f/kotlin.math.sqrt(10f),neighbors.first().cosine,1e-6f)
        assertEquals(0f,neighbors.last().cosine,1e-6f)
    }
}
