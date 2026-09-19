package com.unicornwhodev.visiondatasetstudio.core.geometry

import android.graphics.Bitmap
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Hashing algorithms for integrity (SHA-256) and perceptual deduplication (pHash/dHash).
 */
object HashUtils {
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Difference hash (dHash) 64-bit for fast perceptual duplicate detection.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = scaled.getPixel(x, y) and 0xFF
                val rightPixel = scaled.getPixel(x + 1, y) and 0xFF
                if (leftPixel > rightPixel) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        return hash
    }

    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }
}
