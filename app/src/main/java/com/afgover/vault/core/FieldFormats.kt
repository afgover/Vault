package com.afgover.vault.core

/**
 * Kart ve IBAN alanlarının giriş kuralları ve görüntü biçimi.
 *
 * Ayrım bilinçli: **saklanan değer ham**, boşluksuz; boşluklar yalnız ekranda
 * bir görüntü dönüşümü olarak eklenir. Böylece kopyalanan, klavyeyle yazılan,
 * Bluetooth ile bilgisayara gönderilen ve yedeğe giren değer temiz kalır —
 * kart numarasının boşluklu kopyalanması, yapıştırıldığı formda hataya yol
 * açıyordu.
 *
 * Burası saf Kotlin: arayüzden bağımsız olduğu için JVM testleriyle
 * doğrulanabiliyor.
 */
object FieldFormats {

    /** Kart numarası: yalnız rakam, en fazla 19 (Maestro dahil en uzun PAN). */
    fun cardNumberInput(raw: String): String = raw.filter { it.isDigit() }.take(19)

    /** Son kullanma: yalnız rakam, AAYY — dört hane. */
    fun expiryInput(raw: String): String = raw.filter { it.isDigit() }.take(4)

    /** CVV: yalnız rakam, üç hane. */
    fun cvvInput(raw: String): String = raw.filter { it.isDigit() }.take(3)

    /**
     * IBAN: ilk iki karakter ülke kodu (harf, büyütülür), kalanı rakam.
     * Üst sınır 34 — IBAN standardının izin verdiği en uzun biçim; Türkiye 26.
     */
    fun ibanInput(raw: String): String {
        val temiz = raw.filter { it.isLetterOrDigit() }.uppercase()
        val ulke = temiz.take(2).filter { it.isLetter() }
        val kalan = temiz.drop(2).filter { it.isDigit() }
        return (ulke + kalan).take(34)
    }

    /** Dörderli grupla: "1234567890" → "1234 5678 90". */
    fun grupla(ham: String, boyut: Int = 4): String =
        ham.chunked(boyut).joinToString(" ")

    /** AAYY → "AA/YY"; yarım girişte de bozulmaz ("1" → "1", "123" → "12/3"). */
    fun expiryGoster(ham: String): String =
        if (ham.length <= 2) ham else ham.take(2) + "/" + ham.drop(2)

    /**
     * Son kullanma tarihi anlamlı mı? Boş kabul edilir (alan zorunlu değil);
     * dolu ise ay 01-12 olmalı. Yılı denetlemiyoruz: kartın süresi geçmiş
     * olabilir ve kullanıcı onu yine de saklamak isteyebilir.
     */
    fun expiryGecerliMi(ham: String): Boolean {
        if (ham.isEmpty()) return true
        if (ham.length != 4) return false
        val ay = ham.take(2).toIntOrNull() ?: return false
        return ay in 1..12
    }

    /**
     * E-posta biçimi kabaca doğru mu? Boş kabul edilir.
     *
     * Kasıtlı olarak gevşek: amaç RFC 5322'yi uygulamak değil, "@ ve nokta
     * unuttum" hatasını yakalamak. Fazla katı bir denetim geçerli adresleri
     * reddeder ve kullanıcıyı kendi verisini kaydedemez hâle getirir.
     */
    fun epostaGecerliMi(deger: String): Boolean {
        if (deger.isEmpty()) return true
        val at = deger.indexOf('@')
        if (at <= 0 || at != deger.lastIndexOf('@')) return false
        val alan = deger.substring(at + 1)
        val nokta = alan.lastIndexOf('.')
        if (nokta <= 0 || nokta == alan.length - 1) return false
        return deger.none { it.isWhitespace() } && alan.length - nokta - 1 >= 2
    }
}
