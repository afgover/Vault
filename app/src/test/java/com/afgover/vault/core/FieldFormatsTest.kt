package com.afgover.vault.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alan biçimlendirmesinin sözleşmesi. Asıl korunan şey şu: SAKLANAN değer
 * hiçbir zaman boşluk içermez — boşluk yalnız ekranda vardır. Bu bozulursa
 * kopyalanan kart numarası yapıştırıldığı yerde reddedilir.
 */
class FieldFormatsTest {

    @Test
    fun `kart numarasi yalniz rakam tutar ve 19 hanede durur`() {
        assertEquals("4111111111111111", FieldFormats.cardNumberInput("4111 1111-1111/1111"))
        assertEquals("1234567890123456789", FieldFormats.cardNumberInput("1".repeat(0) + "12345678901234567890123"))
        assertEquals("", FieldFormats.cardNumberInput("abc-def"))
    }

    @Test
    fun `gruplama yalniz gorunumdur, ham deger bosluksuz kalir`() {
        val ham = FieldFormats.cardNumberInput("4111111111111111")
        assertEquals("4111 1111 1111 1111", FieldFormats.grupla(ham))
        assertFalse(ham.contains(" "))
    }

    @Test
    fun `son kullanma AAYY olarak sinirlanir ve bolu ile gosterilir`() {
        assertEquals("1229", FieldFormats.expiryInput("12/29"))
        assertEquals("1229", FieldFormats.expiryInput("122934"))
        assertEquals("12/29", FieldFormats.expiryGoster("1229"))
        assertEquals("1", FieldFormats.expiryGoster("1"))
        assertEquals("12/3", FieldFormats.expiryGoster("123"))
    }

    @Test
    fun `gecersiz ay reddedilir, bos ve gecmis tarih kabul edilir`() {
        assertTrue(FieldFormats.expiryGecerliMi(""))
        assertTrue(FieldFormats.expiryGecerliMi("0130"))
        assertTrue(FieldFormats.expiryGecerliMi("1220"))   // süresi geçmiş: yine de saklanır
        assertFalse(FieldFormats.expiryGecerliMi("1329"))
        assertFalse(FieldFormats.expiryGecerliMi("0029"))
        assertFalse(FieldFormats.expiryGecerliMi("129"))
    }

    @Test
    fun `cvv uc hanede durur`() {
        assertEquals("123", FieldFormats.cvvInput("1234"))
        assertEquals("12", FieldFormats.cvvInput("1a2"))
    }

    @Test
    fun `iban ilk iki karakteri harf, kalanini rakam kabul eder`() {
        assertEquals("TR330006100519786457841326", FieldFormats.ibanInput("TR33 0006 1005 1978 6457 8413 26"))
        assertEquals("TR12", FieldFormats.ibanInput("tr1a2"))
        // Ülke kodundan sonra harf gelirse düşer; rakamlar korunur.
        assertEquals("DE89", FieldFormats.ibanInput("DE89X"))
        assertEquals(34, FieldFormats.ibanInput("TR" + "1".repeat(40)).length)
    }

    @Test
    fun `eposta at ve alan adi noktasi arar, gerisine karismaz`() {
        assertTrue(FieldFormats.epostaGecerliMi(""))
        assertTrue(FieldFormats.epostaGecerliMi("demo@example.com"))
        assertTrue(FieldFormats.epostaGecerliMi("a.b+c@alt.example.co.uk"))
        assertFalse(FieldFormats.epostaGecerliMi("demo.example.com"))
        assertFalse(FieldFormats.epostaGecerliMi("demo@example"))
        assertFalse(FieldFormats.epostaGecerliMi("demo@example."))
        assertFalse(FieldFormats.epostaGecerliMi("demo @example.com"))
        assertFalse(FieldFormats.epostaGecerliMi("a@b@c.com"))
    }
}
