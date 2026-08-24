package com.afgover.vault.data

import com.afgover.vault.core.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dil tercihinin okunma sözleşmesi (S-2026-08-24-dil-ve-duzen-secimi). */
class AppLocaleTest {

    @Test
    fun `bos ya da eksik tercih sistem dili demektir`() {
        assertEquals(AppLocale.Option.SYSTEM, AppLocale.Option.of(null))
        assertEquals(AppLocale.Option.SYSTEM, AppLocale.Option.of(""))
    }

    @Test
    fun `etiketler secenege cozulur`() {
        assertEquals(AppLocale.Option.TURKISH, AppLocale.Option.of("tr"))
        assertEquals(AppLocale.Option.ENGLISH, AppLocale.Option.of("en"))
    }

    /** Tanınmayan etiket kullanıcıyı kilitlemez, sistem diline düşer. */
    @Test
    fun `bilinmeyen etiket sistem diline duser`() {
        assertEquals(AppLocale.Option.SYSTEM, AppLocale.Option.of("de"))
        assertEquals(AppLocale.Option.SYSTEM, AppLocale.Option.of("bozuk"))
    }

    /** Etiketler kaynak klasörleriyle eşleşmeli: values-tr var, values (taban) İngilizce. */
    @Test
    fun `etiketler kaynak klasorleriyle eslesir`() {
        assertEquals("", AppLocale.Option.SYSTEM.tag)
        assertEquals("tr", AppLocale.Option.TURKISH.tag)
        assertEquals("en", AppLocale.Option.ENGLISH.tag)
    }
}
