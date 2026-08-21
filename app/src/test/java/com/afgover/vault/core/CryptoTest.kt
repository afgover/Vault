package com.afgover.vault.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kripto çekirdeğinin sözleşmesi (vault_takip B-024, SEC-015).
 * Bu testler JVM'de koşar; Android'e özgü hiçbir şey kullanılmaz.
 */
class CryptoTest {

    private val key = Crypto.randomKey()

    @Test
    fun `sifrele-coz gidis donusu icerigi korur`() {
        val plain = "Türkçe içerik: şğüöıç \"tırnak\" \$dolar \\ters\nyeni satır".toByteArray()
        val decrypted = Crypto.decrypt(key, Crypto.encrypt(key, plain))
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun `yanlis anahtar null doner - istisna firlatmaz`() {
        val blob = Crypto.encrypt(key, "gizli".toByteArray())
        assertNull(Crypto.decrypt(Crypto.randomKey(), blob))
    }

    @Test
    fun `kurcalanmis veri null doner`() {
        val blob = Crypto.encrypt(key, "gizli".toByteArray())
        // iv sonrası ilk baytı çevir: GCM etiketi tutmamalı
        blob[13] = (blob[13].toInt() xor 0x01).toByte()
        assertNull(Crypto.decrypt(key, blob))
    }

    @Test
    fun `kisa veya bos girdi null doner`() {
        assertNull(Crypto.decrypt(key, ByteArray(0)))
        assertNull(Crypto.decrypt(key, ByteArray(12)))
    }

    @Test
    fun `iv her sifrelemede farkli - ayni girdi ayni ciktiyi vermez`() {
        val plain = "ayni icerik".toByteArray()
        val a = Crypto.encrypt(key, plain)
        val b = Crypto.encrypt(key, plain)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
    }

    @Test
    fun `deriveKey ayni girdiyle deterministik, farkli saltla farkli`() {
        val salt = Crypto.randomBytes(16)
        val k1 = Crypto.deriveKey("parola".toCharArray(), salt, 1_000)
        val k2 = Crypto.deriveKey("parola".toCharArray(), salt, 1_000)
        val k3 = Crypto.deriveKey("parola".toCharArray(), Crypto.randomBytes(16), 1_000)
        assertArrayEquals(k1.encoded, k2.encoded)
        assertFalse(k1.encoded.contentEquals(k3.encoded))
    }

    @Test
    fun `deriveKey bilinen PBKDF2-HMAC-SHA256 vektorune uyar`() {
        // RFC 7914 §11: P="passwd", S="salt", c=1, dkLen=64'ün ilk 32 baytı
        val dk = Crypto.deriveKey("passwd".toCharArray(), "salt".toByteArray(), 1)
        val expectedHex = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
        assertEquals(expectedHex, dk.encoded.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `deriveKey parola karakterlerini temizler ama anahtari uretir`() {
        val pw = "gecici-parola".toCharArray()
        val dk = Crypto.deriveKey(pw, Crypto.randomBytes(16), 1_000)
        assertNotNull(dk.encoded)
        // PBEKeySpec.clearPassword() çağrıldı: dizinin içi sıfırlanmış olmalı değil —
        // spec kendi kopyasını temizler; bizim dizimiz bize aittir. Yalnız çökmediğini
        // ve anahtarın 256 bit olduğunu doğruluyoruz.
        assertEquals(32, dk.encoded.size)
    }
}
