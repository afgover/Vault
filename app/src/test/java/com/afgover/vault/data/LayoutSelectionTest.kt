package com.afgover.vault.data

import com.afgover.vault.bt.HidLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kullanıcının ayarlardan seçtiği klavye düzenlerinin sözleşmesi
 * (S-2026-08-24-dil-ve-duzen-secimi).
 */
class LayoutSelectionTest {

    @Test
    fun `tercih yoksa hepsi acik - kimsenin duzeni gizlenmez`() {
        assertEquals(HidLayouts.Layout.entries, HidLayouts.enabledLayouts(null))
    }

    @Test
    fun `secilenler okunur ve sira korunur`() {
        val secilen = HidLayouts.enabledLayouts("US,DE")
        assertEquals(listOf(HidLayouts.Layout.US, HidLayouts.Layout.DE), secilen)
    }

    @Test
    fun `bos ya da bozuk tercih hepsine duser - aktarim ekrani kullanilamaz kalmaz`() {
        assertEquals(HidLayouts.Layout.entries, HidLayouts.enabledLayouts(""))
        assertEquals(HidLayouts.Layout.entries, HidLayouts.enabledLayouts("OLMAYAN,DUZEN"))
    }

    @Test
    fun `bilinmeyen ad atlanir ama bilinenler kalir`() {
        assertEquals(listOf(HidLayouts.Layout.FR), HidLayouts.enabledLayouts("OLMAYAN,FR"))
    }

    @Test
    fun `gidis donus - saklanan metin geri okunur`() {
        val kume = listOf(HidLayouts.Layout.TR_Q, HidLayouts.Layout.IT)
        assertEquals(kume, HidLayouts.enabledLayouts(HidLayouts.storeEnabled(kume)))
    }

    @Test
    fun `hicbiri secilmezse saklarken hepsi yazilir`() {
        assertEquals(
            HidLayouts.Layout.entries,
            HidLayouts.enabledLayouts(HidLayouts.storeEnabled(emptyList()))
        )
    }

    /**
     * Kullanıcı seçili düzeni ayarlardan kapatırsa aktarım ekranında görünmeyen
     * bir düzen seçili kalırdı — sır yanlış düzende yazılırdı.
     */
    @Test
    fun `kapatilan duzen seciliyse acik olanlardan birine duser`() {
        val acik = listOf(HidLayouts.Layout.US, HidLayouts.Layout.DE)
        assertEquals(HidLayouts.Layout.US, HidLayouts.readLayout("FR", acik))
        assertTrue(HidLayouts.readLayout("FR", acik) in acik)
    }

    @Test
    fun `acik duzen seciliyse korunur`() {
        val acik = listOf(HidLayouts.Layout.US, HidLayouts.Layout.DE)
        assertEquals(HidLayouts.Layout.DE, HidLayouts.readLayout("DE", acik))
    }

    /** Eski sürümlerin "TR" tercihi TR-Q'ya taşınmalı (geri uyumluluk). */
    @Test
    fun `eski TR tercihi TR-Q olarak okunur`() {
        assertEquals(HidLayouts.Layout.TR_Q, HidLayouts.readLayout("TR"))
    }
}
