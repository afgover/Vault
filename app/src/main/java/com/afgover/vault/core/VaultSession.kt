package com.afgover.vault.core

import android.os.SystemClock
import javax.crypto.SecretKey

/**
 * Kilidi açılmış kasanın veri anahtarını yalnızca bellekte tutar.
 * Uygulama ve klavye eklentisi aynı süreçte çalıştığı için ikisi de
 * bu oturumu paylaşır. Zaman aşımında anahtar bellekten silinir.
 */
object VaultSession {

    @Volatile
    private var dataKey: SecretKey? = null

    @Volatile
    private var lastActiveAt: Long = 0L

    /** 0 = hiç kilitlenme; varsayılan 5 dakika */
    @Volatile
    var autoLockMillis: Long = 5 * 60_000L

    fun unlock(key: SecretKey) {
        dataKey = key
        touch()
    }

    fun lock() {
        dataKey = null
    }

    fun touch() {
        lastActiveAt = SystemClock.elapsedRealtime()
    }

    val isUnlocked: Boolean
        get() = key() != null

    /** Geçerli anahtar; zaman aşımı dolduysa kilitler ve null döner. */
    fun key(): SecretKey? {
        val k = dataKey ?: return null
        if (autoLockMillis > 0 &&
            SystemClock.elapsedRealtime() - lastActiveAt > autoLockMillis
        ) {
            lock()
            return null
        }
        return k
    }
}
