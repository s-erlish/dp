package com.v2ray.ang.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tencent.mmkv.MMKV
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides a stable 16-byte crypt key for encrypting the [AuthTokenStore] MMKV instance.
 *
 * A random 16-char secret is generated once, sealed with an AES/GCM key held in the
 * AndroidKeyStore (non-exportable), and the ciphertext+IV persisted in a tiny plaintext MMKV
 * "key holder" (the sealed secret is useless without the Keystore key). Subsequent runs unseal
 * the same secret. Everything is wrapped in try/catch: any Keystore failure returns null so
 * [AuthTokenStore] transparently falls back to a plain MMKV and never crashes.
 */
object KeystoreKeyProvider {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "departament_auth_aes"
    private const val HOLDER_ID = "departament_keyholder"
    private const val K_IV = "iv"
    private const val K_CIPHER = "cipher"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /** Returns the MMKV crypt key (16 chars), or null when Keystore is unavailable. */
    fun getOrCreateCryptKey(): String? {
        return try {
            val holder = MMKV.mmkvWithID(HOLDER_ID)
            val ivB64 = holder.decodeString(K_IV)
            val cipherB64 = holder.decodeString(K_CIPHER)
            if (!ivB64.isNullOrBlank() && !cipherB64.isNullOrBlank()) {
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val cipher = Base64.decode(cipherB64, Base64.NO_WRAP)
                unseal(getOrCreateKey(), iv, cipher)
            } else {
                val secret = randomSecret()
                val sealed = seal(getOrCreateKey(), secret)
                holder.encode(K_IV, Base64.encodeToString(sealed.first, Base64.NO_WRAP))
                holder.encode(K_CIPHER, Base64.encodeToString(sealed.second, Base64.NO_WRAP))
                secret
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** @return Pair(iv, ciphertext) */
    private fun seal(key: SecretKey, plaintext: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return cipher.iv to ct
    }

    private fun unseal(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** 16-char hex secret == exactly 16 bytes, satisfying MMKV's crypt-key length limit. */
    private fun randomSecret(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
