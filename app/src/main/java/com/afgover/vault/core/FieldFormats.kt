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

    /**
     * Kart ağı. Numaranın ilk hanelerinden (IIN) türetilir — kullanıcıya
     * sorulmaz: bilgi zaten numaranın içinde ve sorulduğunda yanlış seçilebilir.
     * Tespit tamamen yerel bir hesap; ağ erişimi gerektirmez.
     *
     * [cvvLength] ve [gruplar] burada duruyor çünkü ikisi de ağa bağlı:
     * Amex'in güvenlik kodu 4 hanedir ve numarası 4-6-5 olarak yazılır.
     */
    enum class CardNetwork(val gorunenAd: String, val cvvLength: Int, val gruplar: List<Int>) {
        VISA("Visa", 3, listOf(4, 4, 4, 4)),
        MASTERCARD("Mastercard", 3, listOf(4, 4, 4, 4)),
        AMEX("American Express", 4, listOf(4, 6, 5)),
        TROY("Troy", 3, listOf(4, 4, 4, 4)),
        DISCOVER("Discover", 3, listOf(4, 4, 4, 4)),
        DINERS("Diners Club", 3, listOf(4, 6, 4)),
        JCB("JCB", 3, listOf(4, 4, 4, 4)),
        UNKNOWN("", 3, listOf(4, 4, 4, 4)),
    }

    /**
     * Numaradan ağı bul. Kısmi girişte de çalışır: kullanıcı yazarken ilk
     * haneden itibaren daralır, bu yüzden CVV alanı numara tamamlanmadan
     * doğru uzunluğa geçer.
     */
    fun cardNetwork(number: String): CardNetwork {
        val n = number.filter { it.isDigit() }
        if (n.isEmpty()) return CardNetwork.UNKNOWN
        fun ilk(k: Int) = n.take(k).padEnd(k, '0').toInt()
        return when {
            n[0] == '4' -> CardNetwork.VISA
            n.length >= 2 && ilk(2) in 51..55 -> CardNetwork.MASTERCARD
            n.length >= 4 && ilk(4) in 2221..2720 -> CardNetwork.MASTERCARD
            n.length >= 2 && ilk(2) in listOf(34, 37) -> CardNetwork.AMEX
            n.length >= 4 && ilk(4) == 9792 -> CardNetwork.TROY
            n.length >= 4 && ilk(4) == 6011 -> CardNetwork.DISCOVER
            n.length >= 2 && ilk(2) == 65 -> CardNetwork.DISCOVER
            n.length >= 3 && ilk(3) in 644..649 -> CardNetwork.DISCOVER
            n.length >= 2 && ilk(2) in listOf(36, 38) -> CardNetwork.DINERS
            n.length >= 4 && ilk(4) in 3528..3589 -> CardNetwork.JCB
            else -> CardNetwork.UNKNOWN
        }
    }

    /** Kart numarası: yalnız rakam, en fazla 19 (Maestro dahil en uzun PAN). */
    fun cardNumberInput(raw: String): String = raw.filter { it.isDigit() }.take(19)

    /** Son kullanma: yalnız rakam, AAYY — dört hane. */
    fun expiryInput(raw: String): String = raw.filter { it.isDigit() }.take(4)

    /**
     * CVV: yalnız rakam. Uzunluk karta göre — Amex 4, diğerleri 3. Sabit 3
     * seçmek Amex kullananın kartını eksik kaydetmesine yol açıyordu.
     */
    fun cvvInput(raw: String, network: CardNetwork = CardNetwork.UNKNOWN): String =
        raw.filter { it.isDigit() }.take(network.cvvLength)

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

    /**
     * Ağın kendi düzenine göre grupla: Amex 4-6-5, Diners 4-6-4, diğerleri
     * dörderli. Desen bittikten sonra kalan haneler dörderli akar.
     */
    fun kartGrupla(ham: String, network: CardNetwork): String {
        val parcalar = mutableListOf<String>()
        var i = 0
        for (uzunluk in network.gruplar) {
            if (i >= ham.length) break
            parcalar += ham.substring(i, minOf(i + uzunluk, ham.length))
            i += uzunluk
        }
        if (i < ham.length) parcalar += ham.substring(i).chunked(4)
        return parcalar.joinToString(" ")
    }

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
