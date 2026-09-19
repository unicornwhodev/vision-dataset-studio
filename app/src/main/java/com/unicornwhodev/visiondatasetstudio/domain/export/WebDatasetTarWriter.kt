package com.unicornwhodev.visiondatasetstudio.domain.export

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Pure Kotlin POSIX UStar TAR archive writer.
 * Produces compliant uncompressed WebDataset TAR shards without heavy external libraries.
 */
class WebDatasetTarWriter(private val outputStream: OutputStream) : AutoCloseable {

    constructor(file: File) : this(FileOutputStream(file))

    private var closed = false

    fun addFile(entryName: String, file: File) {
        file.inputStream().use { stream ->
            addStream(entryName, stream, file.length(), file.lastModified())
        }
    }

    fun addBytes(entryName: String, bytes: ByteArray, lastModified: Long = System.currentTimeMillis()) {
        bytes.inputStream().use { stream ->
            addStream(entryName, stream, bytes.size.toLong(), lastModified)
        }
    }

    fun addStream(
        entryName: String,
        inputStream: InputStream,
        size: Long,
        lastModified: Long = System.currentTimeMillis()
    ) {
        check(!closed) { "Tar writer is closed" }
        require(size >= 0 && size <= 8589934591L) { "Size exceeds USTAR range" }
        require(entryName.toByteArray(Charsets.UTF_8).size <= 100 && entryName.isNotBlank() && !entryName.startsWith('/') && entryName.split('/').none { it == ".." } && !entryName.contains('\u0000')) { "Unsafe or overlong TAR path" }
        val header = createTarHeader(entryName, size, lastModified)
        outputStream.write(header)

        val buffer = ByteArray(8192)
        var totalWritten = 0L
        while (totalWritten < size) {
            val read = inputStream.read(buffer, 0, minOf(buffer.size.toLong(), size - totalWritten).toInt())
            check(read > 0) { "Source shorter than the declared TAR size" }
            outputStream.write(buffer, 0, read)
            totalWritten += read
        }
        check(inputStream.read() == -1) { "Source longer than the declared TAR size" }

        // Pad to 512-byte block boundary
        val remainder = (totalWritten % 512).toInt()
        if (remainder > 0) {
            val padding = 512 - remainder
            outputStream.write(ByteArray(padding))
        }
    }

    private fun createTarHeader(name: String, size: Long, lastModified: Long): ByteArray {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))

        // File mode: 0644
        writeOctal(header, 100, 8, 0x1A4L) // 0644 octal = 420 decimal
        // UID / GID
        writeOctal(header, 108, 8, 0L)
        writeOctal(header, 116, 8, 0L)
        // File size in octal
        writeOctal(header, 124, 12, size)
        // Modification time in octal (seconds)
        writeOctal(header, 136, 12, lastModified / 1000L)

        // Typeflag: '0' for normal file
        header[156] = '0'.code.toByte()

        // Magic: "ustar\0"
        val magic = "ustar".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, header, 257, magic.size)
        header[262] = 0.toByte()
        // Version: "00"
        header[263] = '0'.code.toByte()
        header[264] = '0'.code.toByte()

        // Checksum calculation: sum of all bytes with checksum field filled with spaces (0x20)
        for (i in 148 until 156) {
            header[i] = ' '.code.toByte()
        }
        var chkSum = 0L
        for (b in header) {
            chkSum += (b.toInt() and 0xFF)
        }
        writeOctal(header, 148, 7, chkSum)
        header[155] = ' '.code.toByte()

        return header
    }

    private fun writeOctal(buf: ByteArray, offset: Int, length: Int, value: Long) {
        val octalStr = java.lang.Long.toOctalString(value)
        val numSpaces = length - 1 - octalStr.length
        var cur = offset
        for (i in 0 until numSpaces) {
            buf[cur++] = '0'.code.toByte()
        }
        for (ch in octalStr) {
            buf[cur++] = ch.code.toByte()
        }
        buf[offset + length - 1] = 0 // null terminator
    }

    override fun close() {
        if (!closed) {
            // Write two 512-byte zero blocks to signify EOF
            outputStream.write(ByteArray(1024))
            outputStream.flush()
            outputStream.close()
            closed = true
        }
    }
}
