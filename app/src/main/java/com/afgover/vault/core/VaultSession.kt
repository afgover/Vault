package com.afgover.vault.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Kilit durumu TEPKİSEL yayılır. Eskiden `lock()` yalnız anahtarı siliyordu
     * ve arayüzün kendi `lockState`'i bundan habersizdi; ikisi yalnız
     * `onResume`'da eşitleniyordu. Android yayınları önbellekteki süreçlere
     * ERTELEDİĞİ için `ACTION_SCREEN_OFF` çoğu zaman uygulama öne geldikten
     * SONRA teslim oluyordu: anahtar siliniyor, arayüz kilitsiz sanıyor, liste
     * (başlıklar veritabanında şifresiz) görünmeye devam ediyor, kayda
     * tıklayınca çözme başarısız olup boş ekran çıkıyordu.
     *
     * Artık kim ne zaman kilitlerse kilitlesin arayüz aynı anda öğrenir.
     */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    fun unlock(key: SecretKey) {
        dataKey = key
        _unlocked.value = true
    }

    fun lock() {
        dataKey = null
        _unlocked.value = false
    }

    val isUnlocked: Boolean
        get() = dataKey != null

    fun key(): SecretKey? = dataKey
}
