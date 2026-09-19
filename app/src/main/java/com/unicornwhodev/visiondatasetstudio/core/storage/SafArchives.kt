package com.unicornwhodev.visiondatasetstudio.core.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import java.io.File

/** SAF providers need not support atomic rename, POSIX fsync, or persistent grants.
 * Success here means the complete destination was re-opened and byte-verified, not cloud durability.
 */
object SafArchives {
    data class Receipt(val bytes:Long,val sha256:String,val persistentRead:Boolean)
    fun copyVerified(resolver:ContentResolver,source:File,uri:Uri,checkCancelled:()->Unit = {}):Receipt {
        require(uri.scheme=="content" && source.isFile && source.length()>0) { "Document SAF ou archive invalide" }
        val length=source.length();val hash=HashUtils.computeSha256(source)
        resolver.openOutputStream(uri,"wt")?.use { output ->
            source.inputStream().use { input ->
                check(DurableFiles.copyBounded(input,output,length,checkCancelled)==length) { "Archive modifiée pendant la copie" }
            };output.flush()
        } ?: error("Document non accessible en écriture; archive privée conservée")
        checkCancelled()
        val copied=resolver.openInputStream(uri)?.use { DurableFiles.hash(it,length) }
            ?: error("Document écrit mais non relisible; purge interdite")
        check(copied==hash && source.length()==length && HashUtils.computeSha256(source)==hash) { "Copie incomplète ou altérée; archive privée conservée" }
        try { resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:SecurityException) { /* Explicitly report a temporary grant. */ }
        val persistent=resolver.persistedUriPermissions.any{it.uri==uri && it.isReadPermission}
        return Receipt(length,hash,persistent)
    }
}
