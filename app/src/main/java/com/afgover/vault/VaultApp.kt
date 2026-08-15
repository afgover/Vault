package com.afgover.vault

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.afgover.vault.core.KeyManager
import com.afgover.vault.core.VaultSession
import com.afgover.vault.data.VaultRepository

class VaultApp : Application() {

    val keyManager by lazy { KeyManager(this) }
    val repository by lazy { VaultRepository(this, keyManager) }

    /**
     * Kasa yalnızca ekran kapandığında kilitlenir. Klavye eklentisi süreci
     * ayakta tuttuğu için bu kayıt pratikte hep dinlemededir; ekran kapanır
     * kapanmaz veri anahtarı bellekten silinir.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            VaultSession.lock()
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
    }

    companion object {
        fun from(context: Context): VaultApp = context.applicationContext as VaultApp
    }
}
