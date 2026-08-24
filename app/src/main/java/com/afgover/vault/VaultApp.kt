package com.afgover.vault

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.afgover.vault.core.KeyManager
import com.afgover.vault.core.VaultSession
import com.afgover.vault.data.UsageLogRepository
import com.afgover.vault.data.VaultRepository
import android.hardware.display.DisplayManager
import android.view.Display

class VaultApp : Application() {

    val keyManager by lazy { KeyManager(this) }
    val usageLog by lazy { UsageLogRepository(this, keyManager) }
    val repository by lazy { VaultRepository(this, keyManager, usageLog) }

    /**
     * Kasa yalnızca ekran kapandığında kilitlenir. Klavye eklentisi süreci
     * ayakta tuttuğu için bu kayıt pratikte hep dinlemededir; ekran kapanır
     * kapanmaz veri anahtarı bellekten silinir.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            VaultSession.lock()
            clearSensitiveClip()
        }
    }

    /**
     * Panoda bizim kopyaladığımız duyarlı bir değer duruyorsa siler. 45 sn'lik
     * gecikmeli temizlik süreç ölünce çalışmıyordu; ekran kapanır kapanmaz
     * (kasa kilidiyle aynı an) temizlemek hem daha erken hem sürece bağlı değil
     * (denetim). Yalnız kendi işaretimizi taşıyan panoya dokunulur.
     */
    private fun clearSensitiveClip() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val bizim = cm.primaryClipDescription?.extras?.getBoolean(CLIP_MARKER) == true
            if (bizim) {
                if (android.os.Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                else cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Ana ekranın kapanmasını dinler. Katlanabilir cihazda telefonu katlamak
     * iç ekranı kapatır ama kapak ekranı açık kaldığı için cihaz "uyanık"
     * sayılır ve `ACTION_SCREEN_OFF` HİÇ GELMEZ (ölçüldü). Activity o sırada
     * zaten durmuş olabileceği için `onStop` da devreye girmez — kasa açık
     * kalırdı. Bu dinleyici o boşluğu kapatır.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = kilitle(displayId)
        override fun onDisplayChanged(displayId: Int) = kilitle(displayId)

        private fun kilitle(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val dm = getSystemService(DisplayManager::class.java) ?: return
            val durum = dm.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_OFF
            if (durum != Display.STATE_ON) {
                VaultSession.lock()
                clearSensitiveClip()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(displayListener, null)
    }

    companion object {
        /** Panoya konan duyarlı değeri tanımak için işaret (ClipDescription extras). */
        const val CLIP_MARKER = "com.afgover.vault.sensitive_clip"

        fun from(context: Context): VaultApp = context.applicationContext as VaultApp
    }
}
