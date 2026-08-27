package com.afgover.vault.core

import com.afgover.vault.core.FieldFormats.CardNetwork as N
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

    // ── IBAN: ISO 13616 yapısı (2 harf + 2 rakam + alfanümerik BBAN) ───────

    @Test
    fun `iban BBAN icinde HARF kabul eder — cok ulkede banka kodu harftir`() {
        // Bu vaka bir gerilemeyi önlüyor: "gerisi rakam" kuralı GB ve NL
        // IBAN'larının banka kodunu siliyordu.
        assertEquals("GB29NWBK60161331926819", FieldFormats.ibanInput("GB29 NWBK 6016 1331 9268 19"))
        assertEquals("NL91ABNA0417164300", FieldFormats.ibanInput("NL91 ABNA 0417 1643 00"))
        assertEquals("TR330006100519786457841326", FieldFormats.ibanInput("TR33 0006 1005 1978 6457 8413 26"))
    }

    @Test
    fun `iban ulke kodu harf, kontrol haneleri rakam olmak zorunda`() {
        assertEquals("TR", FieldFormats.ibanInput("tr"))
        assertEquals("TR1", FieldFormats.ibanInput("TR1"))
        assertEquals("TR12", FieldFormats.ibanInput("TR1a2"))   // kontrolde harf düşer
        assertEquals("", FieldFormats.ibanInput("12"))          // ülke kodu harf değil
        assertEquals(34, FieldFormats.ibanInput("TR12" + "1".repeat(40)).length)
    }

    @Test
    fun `MOD-97 dogru IBAN'i gecirir, yanlisi yakalar`() {
        assertTrue(FieldFormats.ibanGecerliMi("GB29 NWBK 6016 1331 9268 19"))
        assertTrue(FieldFormats.ibanGecerliMi("NL91 ABNA 0417 1643 00"))
        assertTrue(FieldFormats.ibanGecerliMi("TR33 0006 1005 1978 6457 8413 26"))
        assertTrue(FieldFormats.ibanGecerliMi("DE89 3704 0044 0532 0130 00"))
        // Tek hane değişikliği ve komşu hane yer değiştirmesi — en sık yazım hataları
        assertFalse(FieldFormats.ibanGecerliMi("GB29 NWBK 6016 1331 9268 18"))
        assertFalse(FieldFormats.ibanGecerliMi("TR33 0006 1005 1978 6457 8413 62"))
    }

    @Test
    fun `MOD-97 bos ve yarim girisi rahat birakir`() {
        assertTrue(FieldFormats.ibanGecerliMi(""))
        assertTrue(FieldFormats.ibanGecerliMi("TR33 0006"))
        assertFalse(FieldFormats.ibanGecerliMi("TR" + "1".repeat(40)))  // 34 üstü
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

    // ── Kart ağı: numaradan türetilir, kullanıcıya sorulmaz ────────────────

    @Test
    fun `ag numaranin ilk hanelerinden bulunur`() {
        assertEquals(N.VISA, FieldFormats.cardNetwork("4111111111111111"))
        assertEquals(N.MASTERCARD, FieldFormats.cardNetwork("5500000000000004"))
        assertEquals(N.MASTERCARD, FieldFormats.cardNetwork("2221000000000009"))
        assertEquals(N.AMEX, FieldFormats.cardNetwork("378282246310005"))
        assertEquals(N.TROY, FieldFormats.cardNetwork("9792000000000001"))
        assertEquals(N.DISCOVER, FieldFormats.cardNetwork("6011000000000004"))
        assertEquals(N.DINERS, FieldFormats.cardNetwork("36000000000008"))
        assertEquals(N.JCB, FieldFormats.cardNetwork("3530111333300000"))
        assertEquals(N.UNKNOWN, FieldFormats.cardNetwork(""))
        assertEquals(N.UNKNOWN, FieldFormats.cardNetwork("9999"))
    }

    @Test
    fun `ag kismi girişte de daralir — CVV uzunlugu numara bitmeden dogru olur`() {
        assertEquals(N.VISA, FieldFormats.cardNetwork("4"))
        assertEquals(N.AMEX, FieldFormats.cardNetwork("37"))
        assertEquals(N.MASTERCARD, FieldFormats.cardNetwork("55"))
    }

    @Test
    fun `cvv uzunlugu aga bagli — Amex dort, digerleri uc`() {
        val amex = FieldFormats.cardNetwork("3782")
        val visa = FieldFormats.cardNetwork("4111")
        assertEquals("1234", FieldFormats.cvvInput("12345", amex))
        assertEquals("123", FieldFormats.cvvInput("12345", visa))
    }

    @Test
    fun `gruplama agin kendi duzenini kullanir`() {
        val amex = FieldFormats.cardNetwork("378282246310005")
        val diners = FieldFormats.cardNetwork("36000000000008")
        val visa = FieldFormats.cardNetwork("4111111111111111")
        assertEquals("3782 822463 10005", FieldFormats.kartGrupla("378282246310005", amex))
        assertEquals("3600 000000 0008", FieldFormats.kartGrupla("36000000000008", diners))
        assertEquals("4111 1111 1111 1111", FieldFormats.kartGrupla("4111111111111111", visa))
        // Desen bittikten sonra kalan haneler dörderli akar (19 haneli PAN).
        assertEquals(
            "4111 1111 1111 1111 111",
            FieldFormats.kartGrupla("4111111111111111111", visa)
        )
    }

    @Test
    fun `gruplama yine yalnizca gorunum — ham deger bosluksuz`() {
        val amex = FieldFormats.cardNetwork("378282246310005")
        val ham = FieldFormats.cardNumberInput("3782 822463 10005")
        assertEquals("378282246310005", ham)
        assertTrue(FieldFormats.kartGrupla(ham, amex).contains(" "))
    }
}

