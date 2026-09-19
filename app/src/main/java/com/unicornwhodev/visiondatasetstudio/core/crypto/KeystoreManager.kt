package com.unicornwhodev.visiondatasetstudio.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles hardware-backed or software-backed Keystore encryption for sensitive credentials
 * (Hugging Face tokens). Fails closed if Keystore is unavailable; no plaintext fallback.
 */
class KeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hf_token_secret_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PREFS_NAME = "vds_secure_prefs"
        private const val PREF_KEY_TOKEN = "enc_hf_token"
        private const val PREF_KEY_IV = "enc_hf_iv"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveToken(token: String): Boolean {
        return try {
            if (token.isBlank()) {
                clearToken()
                return true
            }
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

            prefs.edit()
                .putString(PREF_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(PREF_KEY_TOKEN, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .commit()
        } catch (e: Exception) {
            // Do not silently store a recoverable base64 token when encryption fails.
            false
        }
    }

    fun getToken(): String? {
        return try {
            val encIv = prefs.getString(PREF_KEY_IV, null)
            val encToken = prefs.getString(PREF_KEY_TOKEN, null) ?: return null
            if (encIv == null) {
                // Legacy unencrypted fallback values must be re-entered, not reused.
                clearToken()
                return null
            }
            val iv = Base64.decode(encIv, Base64.NO_WRAP)
            val cipherText = Base64.decode(encToken, Base64.NO_WRAP)

            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clearToken() {
        prefs.edit().remove(PREF_KEY_IV).remove(PREF_KEY_TOKEN).apply()
    }
}
