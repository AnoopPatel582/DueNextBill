package com.anooppatel.cardtracker.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts ("wraps") a small secret so it can be stored on disk, and decrypts it again. */
interface KeyWrapper {
    fun wrap(secret: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * Wraps with an AES key that lives inside the Android Keystore. The AES key never leaves the
 * Keystore (on most phones, secure hardware): the app can ask it to encrypt and decrypt, but can't read it.
 *
 * The key needs no fingerprint or screen unlock, so the daily reminder job can open the database
 * in the background.
 */
class KeystoreKeyWrapper(private val alias: String = "duenextbill-db-key") : KeyWrapper {

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        // Stored as [IV length][IV][ciphertext]. The IV is not secret, but decrypting needs it.
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(secret)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val ivLength = wrapped[0].toInt()
        val iv = wrapped.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(wrapped, 1 + ivLength, wrapped.size - 1 - ivLength)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as SecretKey?)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

/**
 * The database's encryption key. Made once from secure random bytes; after that it is kept in [file],
 * wrapped by [wrapper], so only this app on this phone can unwrap it.
 */
class DatabaseKeyStore(private val file: File, private val wrapper: KeyWrapper) {

    fun getOrCreate(): ByteArray {
        if (file.exists()) return wrapper.unwrap(file.readBytes())

        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        file.parentFile?.mkdirs()
        // Write to a temporary file, then rename: a crash mid-write can't leave a half-written key behind
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(wrapper.wrap(key))
        check(temp.renameTo(file)) { "could not save the database key" }
        return key
    }

    private companion object {
        const val KEY_BYTES = 32 // 256 bits
    }
}