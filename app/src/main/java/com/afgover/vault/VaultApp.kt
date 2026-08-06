package com.afgover.vault

import android.app.Application
import android.content.Context
import com.afgover.vault.core.KeyManager
import com.afgover.vault.core.VaultSession
import com.afgover.vault.data.VaultRepository

class VaultApp : Application() {

    val keyManager by lazy { KeyManager(this) }
    val repository by lazy { VaultRepository(this) }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("vault_settings", Context.MODE_PRIVATE)
        VaultSession.autoLockMillis =
            prefs.getLong("auto_lock_millis", 5 * 60_000L)
    }

    companion object {
        fun from(context: Context): VaultApp = context.applicationContext as VaultApp
    }
}
