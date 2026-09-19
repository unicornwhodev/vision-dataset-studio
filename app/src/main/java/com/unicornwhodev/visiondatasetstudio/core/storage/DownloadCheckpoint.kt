package com.unicornwhodev.visiondatasetstudio.core.storage

import com.unicornwhodev.visiondatasetstudio.core.workflow.RangeSafety
import java.io.File
import java.io.RandomAccessFile
import java.util.Properties

/** Only fsynced, hashed prefixes are resumable; unjournaled trailing bytes are discarded. */
object DownloadCheckpoint {
    fun recover(part:File,state:Properties,identity:String?,maxBytes:Long):Long? = runCatching {
        val bytes=state.getProperty("committed_bytes")?.toLongOrNull() ?: return null
        val total=state.getProperty("total")?.toLongOrNull() ?: return null
        require(bytes>0 && bytes<total && total<=maxBytes && part.isFile && part.length()>=bytes)
        require(state.getProperty("resource")==identity && RangeSafety.strongEtag(state.getProperty("etag")))
        if(part.length()>bytes)RandomAccessFile(part,"rw").use{it.setLength(bytes);it.fd.sync()}
        require(part.inputStream().use{DurableFiles.hash(it,bytes)}==state.getProperty("prefix_sha256"))
        bytes
    }.getOrNull()
    fun save(part:File,sidecar:File,identity:String,etag:String,total:Long) {
        require(RangeSafety.strongEtag(etag) && total>0)
        val bytes=part.length();require(bytes in 1..total)
        RandomAccessFile(part,"rw").use{it.fd.sync()}
        val hash=part.inputStream().use{DurableFiles.hash(it,bytes)}
        val state=Properties().apply {
            setProperty("resource",identity);setProperty("etag",etag);setProperty("total",total.toString())
            setProperty("committed_bytes",bytes.toString());setProperty("prefix_sha256",hash)
        }
        DurableFiles.replace(sidecar){state.store(it,"VDS verified private download prefix")}
    }
}
