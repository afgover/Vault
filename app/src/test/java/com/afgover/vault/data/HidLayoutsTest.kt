package com.afgover.vault.data

import com.afgover.vault.bt.HidLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Düzen testi mekanizmasının sözleşmesi (B-050). */
class HidLayoutsTest {

    @Test
    fun `test metni iki duzende de tamamen yazilabilir`() {
        for (layout in HidLayouts.Layout.entries) {
            val map = HidLayouts.map(layout)
            val yazilamayan = HidLayouts.LAYOUT_TEST_TEXT.toSet().filter { it !in map }
            assertTrue(
                "$layout düzeninde yazılamıyor: $yazilamayan",
                yazilamayan.isEmpty()
            )
        }
    }

    @Test
    fun `test metni duzene duyarli karakter iceriyor - ayirt edici`() {
        assertTrue(HidLayouts.layoutSensitiveChars(HidLayouts.LAYOUT_TEST_TEXT).size >= 5)
    }

    @Test
    fun `harf ve rakamlar duyarli degil - yalniz simgeler`() {
        assertTrue(HidLayouts.layoutSensitiveChars("abcXYZ0123 \n").isEmpty())
        assertFalse(HidLayouts.layoutSensitiveChars("kullanici@site.com").isEmpty())
    }

    @Test
    fun `hiz testi metni duzenden bagimsiz - tasimayi olcer`() {
        assertTrue(HidLayouts.layoutSensitiveChars(HidLayouts.SPEED_TEST_TEXT).isEmpty())
        for (layout in HidLayouts.Layout.entries) {
            val map = HidLayouts.map(layout)
            assertTrue(
                "$layout düzeninde yazılamıyor",
                HidLayouts.SPEED_TEST_TEXT.all { it in map }
            )
        }
    }

    @Test
    fun `hiz testi ozdes bloklardan olusur - dusen karakter goze carpar`() {
        val bloklar = HidLayouts.SPEED_TEST_TEXT.split(" ")
        assertEquals(10, bloklar.size)
        assertTrue(bloklar.all { it == HidLayouts.SPEED_TEST_BLOCK })
    }
}
