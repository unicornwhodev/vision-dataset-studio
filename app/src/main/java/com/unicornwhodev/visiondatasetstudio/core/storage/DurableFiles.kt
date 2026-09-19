package com.unicornwhodev.visiondatasetstudio.core.storage

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
        val parent = target.canonicalFile.parentFile ?: error("Parent absent")
        check(parent.isDirectory || parent.mkdirs()) { "Dossier inaccessible" }
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
        if (!target.exists() && previous.exists()) check(previous.renameTo(target)) { "Récupération du précédent export impossible" }
        if (target.exists()) {
            check(!previous.exists() || previous.deleteRecursively()) { "Ancien export en cours de nettoyage" }
            check(target.renameTo(previous)) { "Conservation du précédent export impossible" }
        }
        if (!prepared.renameTo(target)) {
            if (!target.exists() && previous.exists()) previous.renameTo(target)
            error("Nouveau paquet non finalisé; version précédente conservée")
        }
        check(!previous.exists() || previous.deleteRecursively()) { "Paquet prêt mais ancien export non nettoyé" }
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
            if (count.toLong() > maxBytes - total) throw IOException("Plafond de stockage atteint")
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
        require(candidate != parent && candidate.toPath().startsWith(parent.toPath())) { "Chemin hors du cache géré" }
        return candidate
    }
}
