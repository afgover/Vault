package com.afgover.vault.core

import javax.crypto.SecretKey

/**
 * Kilidi açılmış kasanın veri anahtarını yalnızca bellekte tutar.
 * Uygulama, klavye eklentisi ve otomatik doldurma aynı süreçte çalıştığı için
 * üçü de bu oturumu paylaşır.
 *
 * Oturum **ekran kapanınca** sona erer (kaydı VaultApp'te): ekran açık kaldığı
 * sürece parola/parmak izi bir daha sorulmaz, ekran kapandığı anda anahtar
 * bellekten silinir. Süreç öldüğünde de kaybolur; anahtar hiçbir yerde
 * saklanmaz.
 *
 * Kilitliyken klavyenin "hızlı erişim" kayıtlarını kullanabilmesi bu anahtara
 * değil, Keystore'daki ayrı kolaylık anahtarına dayanır (bkz. KeyManager).
 */
object VaultSession {

    @Volatile
    private var dataKey: SecretKey? = null

    fun unlock(key: SecretKey) {
        dataKey = key
    }

    fun lock() {
        dataKey = null
    }

    val isUnlocked: Boolean
        get() = dataKey != null

    fun key(): SecretKey? = dataKey
}
