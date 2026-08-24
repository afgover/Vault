package com.afgover.vault.core

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kilitleme kuralının sözleşmesi. Katlanabilir cihazda ölçülen davranış:
 * telefon katlıyken cihaz `Awake` kalır (kapak ekranı açık) ama uygulamanın
 * bulunduğu iç ekran kapanır — kural buna göre yazıldı.
 */
class ScreenLockPolicyTest {

    @Test
    fun `ekran acik ve cihaz etkilesimli - kilitleme yok`() {
        // Uygulama değiştirme durumu: klavye ve otomatik doldurma oturumu
        // kullanmaya devam etmeli.
        assertFalse(ScreenLockPolicy.shouldLock(Display.STATE_ON, interactive = true))
    }

    @Test
    fun `ekran kapali - kilitle`() {
        assertTrue(ScreenLockPolicy.shouldLock(Display.STATE_OFF, interactive = true))
    }

    /**
     * KATLAMA DURUMU: iç ekran kapanır, kapak ekranı açık olduğu için cihaz
     * hâlâ etkileşimlidir. Eski kural yalnız `interactive`e baktığı için
     * katlamayı kilit saymıyordu.
     */
    @Test
    fun `katlanmis cihaz - ekran kapali ama cihaz etkilesimli - yine kilitle`() {
        assertTrue(ScreenLockPolicy.shouldLock(Display.STATE_OFF, interactive = true))
    }

    @Test
    fun `cihaz uyumus - kilitle`() {
        assertTrue(ScreenLockPolicy.shouldLock(Display.STATE_ON, interactive = false))
        assertTrue(ScreenLockPolicy.shouldLock(Display.STATE_OFF, interactive = false))
    }

    /** Always-on ekran (DOZE) içerik göstermez sayılır: kilitlenmeli. */
    @Test
    fun `doze durumu kilitler`() {
        assertTrue(ScreenLockPolicy.shouldLock(Display.STATE_DOZE, interactive = true))
        assertTrue(ScreenLockPolicy.shouldLock(Display.STATE_DOZE_SUSPEND, interactive = true))
    }
}
