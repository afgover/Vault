package com.afgover.vault.core

import android.view.Display

/**
 * "Kasa ne zaman kilitlenmeli?" kararının saf mantığı.
 *
 * Doğru soru "cihaz uyanık mı" DEĞİL, **"uygulamanın bulunduğu ekran hâlâ
 * açık mı"**. Fark katlanabilir cihazlarda ortaya çıkıyor: telefon katlanınca
 * iç ekran kapanır ama kapak ekranı açık kaldığı için cihaz `Awake` sayılır ve
 * `ACTION_SCREEN_OFF` hiç gelmez — ölçüldü (SM-F731B: katlıyken
 * `mWakefulness=Awake`). Eski kural bu yüzden katlamayı kilit saymıyordu.
 *
 * Uygulama değiştirmek kilitlemez: o durumda ekran açıktır ve klavye ile
 * otomatik doldurma aynı oturumu kullanmaya devam eder (tasarım gereği).
 */
object ScreenLockPolicy {

    /**
     * @param displayState uygulamanın bulunduğu ekranın durumu ([Display.STATE_ON] vb.)
     * @param interactive cihaz genel olarak etkileşimli mi ([android.os.PowerManager.isInteractive])
     */
    fun shouldLock(displayState: Int, interactive: Boolean): Boolean =
        displayState != Display.STATE_ON || !interactive
}
