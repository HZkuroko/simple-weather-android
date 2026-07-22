package com.example.nmcweather

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 使用 Android Keystore 加密保存 API Key；密钥材料不会写入应用数据。 */
class SecurePrefs(context: Context) {
    companion object {
        private const val STORE = "nmc_weather_secrets"
        private const val KEY_ALIAS = "nmc_weather_api_key_v1"
        private const val IV_SIZE = 12
    }

    private val sp = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun get(name: String): String? {
        val encoded = sp.getString(name, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= IV_SIZE) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, packed.copyOfRange(0, IV_SIZE))
            )
            String(cipher.doFinal(packed.copyOfRange(IV_SIZE, packed.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            // Keystore 被系统重置或数据损坏时安全地视为未配置，不暴露密文或崩溃。
            null
        }
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrBlank()) {
            sp.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val packed = cipher.iv + encrypted
        sp.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
