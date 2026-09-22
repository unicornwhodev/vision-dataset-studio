package com.unicornwhodev.visiondatasetstudio.core.storage

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Atomic replacement of an app-private file. Never truncate the previous valid file. */
object DurableFiles {
    fun replace(target: File, write: (FileOutputStream) -> Unit) {
        val parent = target.canonicalFile.parentFile ?: error(tr("Parent absent", "Parent missing"))
        check(parent.isDirectory || parent.mkdirs()) { tr("Dossier inaccessible", "Folder inaccessible") }
        val pending = File.createTempFile(".${target.name}.", ".pending", parent)
        try {
            FileOutputStream(pending).use { out -> write(out); out.flush(); out.fd.sync() }
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }

    /** Same-volume directory handover, with recovery of the previous generation after a crash. */
    fun replaceDirectory(prepared: File, target: File) {
        require(prepared.isDirectory && prepared.canonicalFile.parentFile == target.canonicalFile.parentFile)
        val previous = File(target.path + ".previous")
        if (!target.exists() && previous.exists()) check(previous.renameTo(target)) { tr("Récupération du précédent export impossible", "Cannot recover the previous export") }
        if (target.exists()) {
            check(!previous.exists() || previous.deleteRecursively()) { tr("Ancien export en cours de nettoyage", "Previous export cleanup in progress") }
            check(target.renameTo(previous)) { tr("Conservation du précédent export impossible", "Cannot preserve the previous export") }
        }
        if (!prepared.renameTo(target)) {
            if (!target.exists() && previous.exists()) previous.renameTo(target)
            error(tr("Nouveau paquet non finalisé; version précédente conservée", "New package incomplete; previous version preserved"))
        }
        check(!previous.exists() || previous.deleteRecursively()) { tr("Paquet prêt mais ancien export non nettoyé", "Package ready but previous export not cleaned up") }
    }

    fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long,
                    checkCancelled: () -> Unit = {}, progress: (Long) -> Unit = {}): Long {
        require(maxBytes > 0)
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > maxBytes - total) throw IOException(tr("Plafond de stockage atteint", "Storage limit reached"))
            output.write(buffer, 0, count)
            total += count; progress(total)
        }
        return total
    }

    fun hash(input: InputStream, maxBytes: Long = Long.MAX_VALUE): String {
        val md = MessageDigest.getInstance("SHA-256")
        copyBounded(input, object : OutputStream() {
            override fun write(b: Int) { md.update(b.toByte()) }
            override fun write(b: ByteArray, off: Int, len: Int) { md.update(b, off, len) }
        }, maxBytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Fail closed: database paths must never allow removal of a source document or another app file. */
    fun ownedFile(root: File, path: String): File {
        val parent = root.canonicalFile
        val candidate = File(path).canonicalFile
        require(candidate != parent && candidate.toPath().startsWith(parent.toPath())) { tr("Chemin hors du cache géré", "Path outside the managed cache") }
        return candidate
    }
}
