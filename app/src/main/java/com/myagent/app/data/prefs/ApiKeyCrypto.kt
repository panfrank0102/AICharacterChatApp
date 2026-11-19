package com.myagent.app.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加解密工具：Android Keystore 中保存 AES-256 主密钥，使用 AES/GCM/NoPadding 加密。
 *
 * 密文格式为 Base64(iv + ciphertext)，其中 IV 由 Cipher 随机生成（12 字节）。
 * [getOrCreateKey] 内部缓存密钥实例，避免重复访问 KeyStore。
 */
object ApiKeyCrypto {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "myagent_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    @Volatile
    private var cachedKey: SecretKey? = null

    /** 加密明文，返回可直接落库的 Base64 字符串。 */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /** 解密 [encrypt] 产生的密文；格式错误、密钥失效等任何异常都返回 null。 */
    fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        if (bytes.size <= IV_LENGTH_BYTES) {
            null
        } else {
            val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    /** 获取（必要时创建并缓存）Keystore 中的 AES 主密钥。 */
    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
            cachedKey = key
            return key
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }
}
