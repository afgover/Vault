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
        private const val PREF_PIN_SALT = "pin_salt"
        private const val PREF_PIN_CHECK = "pin_check"
        private const val PREF_REMINDER_DAYS = "master_reminder_days"
        private const val PREF_LAST_MASTER = "last_master_password_at"
        private const val KEYSTORE_ALIAS = "vault_biometric_key"
        private const val QUICK_KEYSTORE_ALIAS = "vault_quick_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    val isInitialized: Boolean
        get() = prefs.contains(PREF_WRAPPED_KEY)

    // ---- PIN (ikinci kapı; varsayılan KAPALI) ----
    //
    // PIN, ana parola yolunu DEĞİL **parmak izi yolunu** korur. Gerekçe:
    // ana parola zaten güçlü sırdır, üstüne PIN sormak yalnız sürtünme
    // ekler. Korunmak istenen senaryo "biri parmağımı kullanır" olduğu için
    // katman tam oraya kondu:
    //
    //   parola  → Enc(KEK_parola, dataKey)              (PIN sorulmaz)
    //   parmak  → Enc(K_keystore, Enc(K_pin, dataKey))  (PIN şart)
    //
    // Böylece kurtarma garantisi de bozulmaz: ana parola her zaman tek
    // başına açar; PIN unutulursa parmak izi kaydı silinip yenilenir.

    val isPinEnabled: Boolean
        get() = prefs.contains(PREF_PIN_SALT)

    private fun pinSalt(): ByteArray? = prefs.getString(PREF_PIN_SALT, null)?.let(::unb64)

    /** PIN'in doğruluğunu sargıya dokunmadan sınar. */
    fun verifyPin(pin: String): Boolean {
        val salt = pinSalt() ?: return false
        val check = prefs.getString(PREF_PIN_CHECK, null)?.let(::unb64) ?: return false
        return PinLock.unwrapCheck(check, pin, salt)
    }

    /**
     * PIN'i açar. Ana parola yalnız DOĞRULAMA için istenir; sargı biçimi
     * değişmez. Var olan parmak izi kaydı ham dataKey'i sardığı için
     * temizlenir — kullanıcı yeniden etkinleştirdiğinde PIN katmanıyla
     * sarılır ([wrapForBiometric]).
     */
    fun enablePin(password: CharArray, pin: String): Boolean {
        if (!PinLock.isValid(pin) || isPinEnabled) return false
        unlockWithPassword(password) ?: return false
        val salt = Crypto.randomBytes(16)
        prefs.edit()
            .putString(PREF_PIN_SALT, b64(salt))
            .putString(PREF_PIN_CHECK, b64(PinLock.wrapCheck(pin, salt)))
            .apply()
        clearBiometric()
        return true
    }

    /** PIN'i kapatır; parmak izi kaydı yine tazelenmeli. */
    fun disablePin(password: CharArray, pin: String): Boolean {
        unlockWithPassword(password) ?: return false
        if (!verifyPin(pin)) return false
        prefs.edit().remove(PREF_PIN_SALT).remove(PREF_PIN_CHECK).apply()
        clearBiometric()
        return true
    }

    /** Parmak izi kaydına yazılacak içerik: PIN açıksa iç sargı, kapalıysa dataKey. */
    fun wrapForBiometric(dataKey: SecretKey, pin: String?): ByteArray? {
        if (!isPinEnabled) return dataKey.encoded
        val salt = pinSalt() ?: return null
        if (pin == null || !verifyPin(pin)) return null
        return PinLock.wrap(dataKey, pin, salt)
    }

    /** Parmak izinden gelen iç sargıyı PIN ile çözer. */
    fun unlockWithPin(inner: ByteArray, pin: String): SecretKey? {
        val salt = pinSalt() ?: return null
        return PinLock.unwrap(inner, pin, salt)
    }

    // ---- Ana parola hatırlatıcısı (varsayılan: süresiz) ----

    /** 0 = süresiz (kapalı). */
    var reminderDays: Int
        get() = prefs.getInt(PREF_REMINDER_DAYS, 0)
        set(value) { prefs.edit().putInt(PREF_REMINDER_DAYS, value).apply() }

    fun markMasterPasswordUsed() {
        prefs.edit().putLong(PREF_LAST_MASTER, System.currentTimeMillis()).apply()
    }

    /**
     * Biyometrik açılış yerine ana parola istenmeli mi? Amaç güvenlik değil
     * **unutmayı önlemek**: aylarca yazılmayan parola kaybolur ve yedekleri
     * de o açar. Süre kullanıcı tarafından seçilir; varsayılan süresizdir.
     */
    fun masterPasswordDue(now: Long = System.currentTimeMillis()): Boolean {
        val days = reminderDays
        if (days <= 0) return false
        val last = prefs.getLong(PREF_LAST_MASTER, 0L)
        if (last == 0L) return false
        return now - last >= days * 24L * 60L * 60L * 1000L
    }

    /** İlk kurulum: yeni dataKey üretir ve ana parola ile sarıp saklar. */
    fun setup(password: CharArray): SecretKey {
        val dataKey = Crypto.randomKey()
        storeWrappedKey(password, dataKey)
        return dataKey
    }

    private fun storeWrappedKey(password: CharArray, dataKey: SecretKey) =
        storePayload(password, dataKey.encoded)

    /**
     * Saklanan sargının içeriği: PIN kapalıyken `dataKey`, açıkken PIN ile
     * sarılmış iç sargı. Ana parola katmanı ikisini de aynı şekilde tutar.
     */
    private fun storePayload(password: CharArray, payload: ByteArray) {
        val salt = Crypto.randomBytes(16)
        val kek = Crypto.deriveKey(password, salt)
        val wrapped = Crypto.encrypt(kek, payload)
        prefs.edit()
            .putString(PREF_SALT, b64(salt))
            .putInt(PREF_ITERATIONS, Crypto.KDF_ITERATIONS)
            .putString(PREF_WRAPPED_KEY, b64(wrapped))
            .apply()
    }

    /**
     * Ana parola katmanını açar. Dönen bayt dizisi PIN kapalıyken `dataKey`,
     * açıkken PIN ile sarılmış iç sargıdır ([unlockWithPin] ile çözülür).
     */
    fun unlockStage1(password: CharArray): ByteArray? {
        val salt = prefs.getString(PREF_SALT, null)?.let(::unb64) ?: return null
        val iterations = prefs.getInt(PREF_ITERATIONS, Crypto.KDF_ITERATIONS)
        val wrapped = prefs.getString(PREF_WRAPPED_KEY, null)?.let(::unb64) ?: return null
        val kek = Crypto.deriveKey(password, salt, iterations)
        return Crypto.decrypt(kek, wrapped)
    }

    /** Parola doğruysa dataKey. PIN bu yolu etkilemez (bilinçli). */
    fun unlockWithPassword(password: CharArray): SecretKey? {
        val raw = unlockStage1(password) ?: return null
        return SecretKeySpec(raw, "AES")
    }

    /**
     * Ana parolayı değiştirir: içerik (dataKey ya da PIN iç sargısı) aynı
     * kalır, yalnızca ana parola katmanı yenilenir — PIN açıkken de çalışır.
     */
    fun changePassword(oldPassword: CharArray, newPassword: CharArray): Boolean {
        val payload = unlockStage1(oldPassword) ?: return false
        storePayload(newPassword, payload)
        return true
    }

    /** İçe aktarma sonrası: mevcut dataKey'i yeni parola ile yeniden sarar. */
    fun rewrap(password: CharArray, dataKey: SecretKey) {
        storeWrappedKey(password, dataKey)
        clearBiometric()
    }

    // ---- Hızlı erişim ----

    /**
     * "Hızlı erişim" işaretli kayıtların klavye kopyasını şifreleyen anahtar.
     * Keystore'da durur ve **kimlik doğrulama istemez** — kasa kilitliyken de
     * kullanılabilmesinin sebebi budur. Dolayısıyla bu anahtarla korunan
     * kayıtların güvenliği, kasa parolasına değil telefonun kendi ekran
     * kilidine dayanır; oraya yalnızca düşük değerli bilgiler konmalıdır.
     *
     * Anahtar cihaza bağlıdır (sıfırlamada kaybolur). Kayıtların aslı ana
     * paroladan türeyen dataKey ile şifreli olduğu için bu bir veri kaybı
     * yaratmaz: kopyalar kilit açıldığında yeniden üretilir.
     *
     * Keystore erişilemiyorsa null döner; bu durumda hızlı erişim çalışmaz,
     * kayıtlar korumalı gibi davranır.
     */
    fun quickKey(): SecretKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(QUICK_KEYSTORE_ALIAS, null) as? SecretKey) ?: generateQuickKey()
    } catch (e: Exception) {
        null
    }

    private fun generateQuickKey(): SecretKey? = try {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    QUICK_KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKey()
    } catch (e: Exception) {
        null
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

    /**
     * BiometricPrompt onayından sonra saklanan içeriği (PIN kapalıysa
     * dataKey, açıksa iç sargı) keystore anahtarıyla sarıp saklar.
     */
    fun storeBiometricWrappedKey(authorizedCipher: Cipher, payload: ByteArray) {
        val ct = authorizedCipher.doFinal(payload)
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
    /** Biyometrik katmanı açar; PIN açıksa iç sargı, kapalıysa dataKey döner. */
    fun unlockWithBiometricStage1(authorizedCipher: Cipher): ByteArray? {
        val ct = prefs.getString(PREF_BIO_WRAPPED_KEY, null)?.let(::unb64) ?: return null
        return try {
            authorizedCipher.doFinal(ct)
        } catch (e: Exception) {
            null
        }
    }

    fun unlockWithBiometricCipher(authorizedCipher: Cipher): SecretKey? {
        if (isPinEnabled) return null
        val raw = unlockWithBiometricStage1(authorizedCipher) ?: return null
        return SecretKeySpec(raw, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
