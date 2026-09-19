package com.unicornwhodev.visiondatasetstudio
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/** Fault-injection provider installed ONLY in the test APK. Real provider/device QA remains separate. */
class SafFaultProvider:ContentProvider() {
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/octet-stream"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        val key=uri.lastPathSegment ?: error("key")
        require(key.matches(Regex("[a-z-]+")))
        val file=File(context!!.cacheDir,"saf-$key")
        if(mode.contains('w')) {
            if(key=="denied")throw FileNotFoundException("Injected storage removed")
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        if(key=="unreadable")throw FileNotFoundException("Injected read grant lost")
        if(key=="altered")file.appendText("unexpected")
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
@RunWith(AndroidJUnit4::class)
class SafV4Test {
    private fun testUri(path:String):Uri = Uri.parse("content://${InstrumentationRegistry.getInstrumentation().context.packageName}.documents/$path")
    private fun fixture(block:(File)->Unit) {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"saf-source").apply{writeText("original archive bytes")}
        try { block(file);assertEquals("original archive bytes",file.readText()) } finally {file.delete()}
    }
    @Test fun successfulCopyIsReopenedAndHashed()=fixture {source ->
        val r=SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().context.contentResolver,source,testUri("good"))
        assertEquals(source.length(),r.bytes);assertFalse(r.persistentRead)
    }
    @Test fun failedWritePreservesPrivateSource()=fixture {source ->
        assertThrows(Exception::class.java){SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().context.contentResolver,source,testUri("denied"))}
    }
    @Test fun lostReadGrantDoesNotProduceReceipt()=fixture {source ->
        assertThrows(Exception::class.java){SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().context.contentResolver,source,testUri("unreadable"))}
    }
    @Test fun changedCopyDoesNotProduceReceipt()=fixture {source ->
        assertThrows(Exception::class.java){SafArchives.copyVerified(InstrumentationRegistry.getInstrumentation().context.contentResolver,source,testUri("altered"))}
    }
}
