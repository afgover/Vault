package com.afgover.vault.core

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * İsteğe bağlı ikinci kapı: PIN (vault_takip B-066).
 *
 * PIN bir **arayüz kontrolü değil**, kriptografik bir katmandır. Açıkken
 * saklanan sargının içinde doğrudan `dataKey` değil, PIN'den türeyen anahtarla
 * şifrelenmiş hâli (*iç sargı*) durur:
 *
 * ```
 * PIN kapalı:  saklanan = Enc(KEK_parola, dataKey)
 * PIN açık:    saklanan = Enc(KEK_parola, Enc(K_pin, dataKey))
 * ```
 *
 * Sonuç: ana parola tek başına yetmez, PIN tek başına yetmez. PIN'in düşük
 * entropisi (4-12 hane) burada kabul edilebilir, çünkü iç sargıya ulaşmak
 * için önce ana parolayı (ya da biyometrik Keystore anahtarını) geçmek
 * gerekir — PIN'i tek başına deneyecek bir saldırgan için elde blob yoktur.
 *
 * Yanlış PIN "rastgele çözme" üretmez: GCM etiketi tutmaz ve `null` döner.
 */
object PinLock {

    /** PIN kısa olduğu için tur sayısı ana paroladakiyle aynı tutulur. */
    const val ITERATIONS = Crypto.KDF_ITERATIONS

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    private fun key(pin: String, salt: ByteArray): SecretKey =
        Crypto.deriveKey(pin.toCharArray(), salt, ITERATIONS)

    /** `dataKey` → iç sargı. */
    fun wrap(dataKey: SecretKey, pin: String, salt: ByteArray): ByteArray =
        Crypto.encrypt(key(pin, salt), dataKey.encoded)

    /** İç sargı → `dataKey`; PIN yanlışsa null. */
    fun unwrap(inner: ByteArray, pin: String, salt: ByteArray): SecretKey? =
        Crypto.decrypt(key(pin, salt), inner)?.let { SecretKeySpec(it, "AES") }
}
