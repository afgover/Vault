package com.afgover.vault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yerelleştirmenin iki tuzağının sözleşmesi (S-2026-08-24-ingilizce-yerellestirme):
 * maskeleme metne bakmaz, günlüğe yazılan ad dile bağlı değildir.
 */
class FieldKeyTest {

    private val dolu = EntryData(
        username = "afgover", password = "gizli", cvv = "123", cardNumber = "4444",
        notes = "not", custom = listOf(CustomField("API anahtarı", "abc"))
    )

    @Test
    fun `sifre ve cvv kimlikle maskelenir - metin karsilastirmasi yok`() {
        val alanlar = dolu.fields()
        val sifre = alanlar.first { it.key == FieldKey.PASSWORD }
        val cvv = alanlar.first { it.key == FieldKey.CVV }
        val kullanici = alanlar.first { it.key == FieldKey.USERNAME }
        assertTrue(sifre.hidden)
        assertTrue(cvv.hidden)
        assertFalse(kullanici.hidden)
    }

    @Test
    fun `gunluge yazilan ad dile bagli degil - kararli anahtar`() {
        val sifre = dolu.fields().first { it.key == FieldKey.PASSWORD }
        assertEquals("password", sifre.stableName)
        // Kullanıcının kendi alanı zaten kendi yazdığı metindir, çevrilmez.
        val ozel = dolu.fields().first { it.key == null }
        assertEquals("API anahtarı", ozel.stableName)
    }

    @Test
    fun `kararli adlar benzersiz ve geri cozulebilir`() {
        val adlar = FieldKey.entries.map { it.stable }
        assertEquals(adlar.size, adlar.toSet().size)
        assertEquals(FieldKey.PASSWORD, FieldKey.ofStable("password"))
        assertNull(FieldKey.ofStable("Şifre"))   // eski günlük satırı: çözülemez, ham gösterilir
    }

    @Test
    fun `bos alanlar listede yok`() {
        assertTrue(EntryData().fields().isEmpty())
    }
}
