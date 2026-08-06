package com.afgover.vault.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Anahtar hiyerarşisi:
 *  - dataKey: rastgele 256 bit; tüm kayıtları şifreler.
 *  - KEK: ana paroladan PBKDF2 ile türetilir; dataKey'i sarar (AES-GCM).
 *    GCM doğrulaması sayesinde yanlış parola açılırken belli olur.
 *  - Biyometrik (opsiyonel): dataKey ayrıca Keystore'daki biyometrik korumalı
 *    bir anahtarla da sarılır. Keystore cihaz sıfırlanınca kaybolur; bu yüzden
 *    yalnızca kolaylık içindir, ana parola her zaman çalışır.
 */
class KeyManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("vault_keys", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_SALT = "kdf_salt"
        private const val PREF_ITERATIONS = "kdf_iterations"
        private const val PREF_WRAPPED_KEY = "wrapped_data_key"
        private const val PREF_BIO_WRAPPED_KEY = "bio_wrapped_data_key"
        private const val PREF_BIO_IV = "bio_iv"
        private const val KEYSTORE_ALIAS = "vault_biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    val isInitialized: Boolean
        get() = prefs.contains(PREF_WRAPPED_KEY)

    /** İlk kurulum: yeni dataKey üretir ve ana parola ile sarıp saklar. */
    fun setup(password: CharArray): SecretKey {
        val dataKey = Crypto.randomKey()
        storeWrappedKey(password, dataKey)
        return dataKey
    }

    private fun storeWrappedKey(password: CharArray, dataKey: SecretKey) {
        val salt = Crypto.randomBytes(16)
        val kek = Crypto.deriveKey(password, salt)
        val wrapped = Crypto.encrypt(kek, dataKey.encoded)
        prefs.edit()
            .putString(PREF_SALT, b64(salt))
            .putInt(PREF_ITERATIONS, Crypto.KDF_ITERATIONS)
            .putString(PREF_WRAPPED_KEY, b64(wrapped))
            .apply()
    }

    /** Parola doğruysa dataKey, yanlışsa null. */
    fun unlockWithPassword(password: CharArray): SecretKey? {
        val salt = prefs.getString(PREF_SALT, null)?.let(::unb64) ?: return null
        val iterations = prefs.getInt(PREF_ITERATIONS, Crypto.KDF_ITERATIONS)
        val wrapped = prefs.getString(PREF_WRAPPED_KEY, null)?.let(::unb64) ?: return null
        val kek = Crypto.deriveKey(password, salt, iterations)
        val raw = Crypto.decrypt(kek, wrapped) ?: return null
        return SecretKeySpec(raw, "AES")
    }

    /** Ana parolayı değiştirir: dataKey aynı kalır, yalnızca sargı yenilenir. */
    fun changePassword(oldPassword: CharArray, newPassword: CharArray): Boolean {
        val dataKey = unlockWithPassword(oldPassword) ?: return false
        storeWrappedKey(newPassword, dataKey)
        return true
    }

    /** İçe aktarma sonrası: mevcut dataKey'i yeni parola ile yeniden sarar. */
    fun rewrap(password: CharArray, dataKey: SecretKey) {
        storeWrappedKey(password, dataKey)
        clearBiometric()
    }

    // ---- Biyometrik ----

    val isBiometricEnabled: Boolean
        get() = prefs.contains(PREF_BIO_WRAPPED_KEY)

    fun clearBiometric() {
        prefs.edit().remove(PREF_BIO_WRAPPED_KEY).remove(PREF_BIO_IV).apply()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.deleteEntry(KEYSTORE_ALIAS)
        } catch (_: Exception) {
        }
    }

    /** Biyometrik etkinleştirme için ENCRYPT modunda cipher hazırlar. */
    fun biometricEncryptCipher(): Cipher {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        val key = keyGenerator.generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    /** BiometricPrompt onayından sonra dataKey'i keystore anahtarıyla sarıp saklar. */
    fun storeBiometricWrappedKey(authorizedCipher: Cipher, dataKey: SecretKey) {
        val ct = authorizedCipher.doFinal(dataKey.encoded)
        prefs.edit()
            .putString(PREF_BIO_WRAPPED_KEY, b64(ct))
            .putString(PREF_BIO_IV, b64(authorizedCipher.iv))
            .apply()
    }

    /**
     * Biyometrik kilit açma için DECRYPT modunda cipher hazırlar.
     * Keystore anahtarı geçersizleşmişse (yeni parmak izi kaydı, sıfırlama vb.)
     * biyometrik veriyi temizler ve null döner → parolaya düşülür.
     */
    fun biometricDecryptCipher(): Cipher? {
        val iv = prefs.getString(PREF_BIO_IV, null)?.let(::unb64) ?: return null
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearBiometric()
            null
        } catch (e: Exception) {
            null
        }
    }

    /** BiometricPrompt onayından sonra dataKey'i çözer. */
    fun unlockWithBiometricCipher(authorizedCipher: Cipher): SecretKey? {
        val ct = prefs.getString(PREF_BIO_WRAPPED_KEY, null)?.let(::unb64) ?: return null
        return try {
            SecretKeySpec(authorizedCipher.doFinal(ct), "AES")
        } catch (e: Exception) {
            null
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
