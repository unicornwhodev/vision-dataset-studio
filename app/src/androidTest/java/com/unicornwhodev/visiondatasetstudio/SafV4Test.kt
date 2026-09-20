package com.unicornwhodev.visiondatasetstudio
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SafV4Test {
    private fun testUri(path:String):Uri = Uri.parse("content://${InstrumentationRegistry.getInstrumentation().context.packageName}.documents/$path")
    private fun fixture(block:(File)->Unit) {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"saf-source").apply{writeText("original archive bytes")}
        try { block(file);assertEquals("original archive bytes",file.readText()) } finally {file.delete()}
    }
    @Test fun successfulCopyIsReopenedAndHashed()=fixture {source ->
        val r=SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,source,testUri("good"))
        assertEquals(source.length(),r.bytes);assertFalse(r.persistentRead)
    }
    @Test fun failedWritePreservesPrivateSource()=fixture {source ->
        assertThrows(Exception::class.java){SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,source,testUri("denied"))}
    }
    @Test fun lostReadGrantDoesNotProduceReceipt()=fixture {source ->
        assertThrows(Exception::class.java){SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,source,testUri("unreadable"))}
    }
    @Test fun changedCopyDoesNotProduceReceipt()=fixture {source ->
        assertThrows(Exception::class.java){SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,source,testUri("altered"))}
    }
}
