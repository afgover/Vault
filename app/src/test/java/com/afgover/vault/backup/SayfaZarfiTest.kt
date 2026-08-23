package com.afgover.vault.backup

import com.afgover.vault.data.EntryType
import com.afgover.vault.data.NoteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `aktar.html`'in ürettiği gerçek zarf, uygulamanın içe aktarma yolundan
 * geçmeli. Dosya sayfanın kendi betiği koşturularak üretildi (B-072).
 */
class SayfaZarfiTest {

    private fun zarf(): String =
        javaClass.classLoader!!.getResourceAsStream("sayfa_zarfi.json")!!
            .readBytes().toString(Charsets.UTF_8)

    @Test
    fun `sayfanin zarfi ice aktarilir`() {
        val sonuc = BackupManager.import(zarf().byteInputStream(), "TestParola123".toCharArray())
        assertEquals(1, sonuc.entries.size)
        val e = sonuc.entries[0]
        assertEquals(EntryType.LOGIN, e.type)
        assertEquals("Uçtan uca", e.title)
        assertEquals("afgover", e.data.username)
        assertEquals("Gizli\"Şifre\"\n2. satır", e.data.password)
        assertEquals("API anahtarı", e.data.custom[0].label)
        assertTrue(e.data.passwordChangedAt > 0)
        assertEquals(NoteKind.GENEL, e.noteKind)
    }

    @Test
    fun `tarayicidan kopyalanan bicimler de kabul edilir`() {
        val ham = zarf().trim()
        // Chrome'dan kopyalarken başa/sona boşluk, satır sonu ya da BOM gelebilir.
        for (varyant in listOf(
            "\n$ham\n", "  $ham  ", "\uFEFF$ham", "$ham\r\n",
            "\uFEFF  $ham \u200B", "sayfa metni\n$ham"
        )) {
            val sonuc = BackupManager.import(
                varyant.byteInputStream(), "TestParola123".toCharArray()
            )
            assertEquals(1, sonuc.entries.size)
        }
    }
}
