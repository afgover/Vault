package com.afgover.vault.data

import com.afgover.vault.core.Crypto
import com.afgover.vault.core.PinLock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** İkinci kapının sözleşmesi: PIN olmadan dataKey çözülemez (B-066). */
class PinLockTest {

    private val salt = Crypto.randomBytes(16)
    private val dataKey = Crypto.randomKey()

    @Test
    fun `dogru pin dataKeyi aynen geri verir`() {
        val inner = PinLock.wrap(dataKey, "482913", salt)
        assertArrayEquals(dataKey.encoded, PinLock.unwrap(inner, "482913", salt)?.encoded)
    }

    @Test
    fun `yanlis pin null doner - rastgele cozme yok`() {
        val inner = PinLock.wrap(dataKey, "482913", salt)
        assertNull(PinLock.unwrap(inner, "482914", salt))
        assertNull(PinLock.unwrap(inner, "4829", salt))
        assertNull(PinLock.unwrap(inner, "", salt))
    }

    @Test
    fun `farkli salt ayni pinle acilmaz - sargilar birbirinin yerine gecmez`() {
        val inner = PinLock.wrap(dataKey, "482913", salt)
        assertNull(PinLock.unwrap(inner, "482913", Crypto.randomBytes(16)))
    }

    @Test
    fun `ic sargi dataKeyi duz halde tasimaz`() {
        val inner = PinLock.wrap(dataKey, "482913", salt)
        val ham = dataKey.encoded
        // İç sargının hiçbir yerinde dataKey baytları ardışık geçmemeli.
        val gecti = (0..inner.size - ham.size).any { i ->
            ham.indices.all { j -> inner[i + j] == ham[j] }
        }
        assertFalse(gecti)
    }

    @Test
    fun `pin bicim kurali 4-12 rakam`() {
        assertTrue(PinLock.isValid("1234"))
        assertTrue(PinLock.isValid("123456789012"))
        assertFalse(PinLock.isValid("123"))
        assertFalse(PinLock.isValid("1234567890123"))
        assertFalse(PinLock.isValid("12a4"))
    }

    @Test
    fun `dogrulama blobu pini sargiya dokunmadan sinar`() {
        val check = PinLock.wrapCheck("482913", salt)
        assertTrue(PinLock.unwrapCheck(check, "482913", salt))
        assertFalse(PinLock.unwrapCheck(check, "482914", salt))
        assertFalse(PinLock.unwrapCheck(check, "482913", Crypto.randomBytes(16)))
    }

    @Test
    fun `bayat parmak izi kaydi ayirt edilebilir - dogru pin ama acilmayan sargi`() {
        // PIN açılmadan önce yazılmış kayıt: ham dataKey, iç sargı değil.
        val bayat = dataKey.encoded
        val check = PinLock.wrapCheck("482913", salt)
        // PIN doğru...
        assertTrue(PinLock.unwrapCheck(check, "482913", salt))
        // ...ama sargı açılmıyor: uygulama bunu "PIN yanlış" değil
        // "kayıt bayat" olarak yorumlar ve kaydı siler.
        assertNull(PinLock.unwrap(bayat, "482913", salt))
    }
}
