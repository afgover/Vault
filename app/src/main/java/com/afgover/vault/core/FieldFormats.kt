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
     * IBAN girişi, ISO 13616 yapısına göre:
     * 1-2 ülke kodu (harf), 3-4 kontrol hanesi (rakam), 5+ BBAN (harf VEYA
     * rakam), en fazla 34 karakter.
     *
     * BBAN'ın alfanümerik olması önemli: birçok ülkede banka kodu HARFTİR
     * (GB29 **NWBK** …, NL91 **ABNA** …). Önceki sürüm "ilk iki harf, gerisi
     * rakam" kuralını uyguluyordu; Türk IBAN'ında (TR + 24 rakam) doğru
     * çalışıyor ama İngiliz/Hollanda IBAN'ının harflerini sessizce siliyor ve
     * geriye eksik bir numara bırakıyordu — kullanıcı fark etmeden kaydedebilir.
     * Uzunluk da evrensel değil: Norveç 15, Almanya 22, Türkiye 26, Malta 31.
     */
    fun ibanInput(raw: String): String {
        // Konum konum süzülür: geçersiz bir tuş girişi KESMEZ, yok sayılır.
        // (Erken sürüm ilk uygunsuz karakterde duruyordu; "TR1a2" yazan
        // kullanıcı '2'yi kaybediyordu.)
        val sonuc = StringBuilder()
        for (c in raw.uppercase()) {
            if (sonuc.length >= 34) break
            val uygun = when (sonuc.length) {
                0, 1 -> c.isLetter()          // ülke kodu
                2, 3 -> c.isDigit()           // kontrol haneleri
                else -> c.isLetterOrDigit()   // BBAN: harf de olabilir
            }
            if (uygun) sonuc.append(c)
        }
        return sonuc.toString()
    }

    /**
     * IBAN kontrol hanesi doğru mu (ISO 7064, MOD-97-10)?
     *
     * Numaranın tamamından hesaplanır; rakam atlama ve komşu hane değiştirme
     * gibi yazım hatalarının hemen hepsini yakalar. Tamamen çevrimdışı bir
     * hesap — banka sorgusu değil, aritmetik.
     *
     * Boş ve YARIM giriş "geçerli" sayılır: kullanıcı yazarken her tuşta hata
     * göstermek, henüz yapılmamış bir hatayı bildirmek olur. Denetim ancak
     * ülkesine göre makul uzunluğa (en az 15) ulaşınca anlamlıdır.
     */
    fun ibanGecerliMi(deger: String): Boolean {
        val v = deger.filter { it.isLetterOrDigit() }.uppercase()
        if (v.length < 15) return true
        if (v.length > 34) return false
        if (!v.take(2).all { it.isLetter() } || !v.drop(2).take(2).all { it.isDigit() }) return false
        // Ülke kodu ve kontrol haneleri sona alınır, harfler A=10…Z=35 olur.
        val donmus = v.drop(4) + v.take(4)
        var kalan = 0
        for (c in donmus) {
            val basamak = if (c.isDigit()) c - '0' else c - 'A' + 10
            kalan = if (basamak > 9) (kalan * 100 + basamak) % 97 else (kalan * 10 + basamak) % 97
        }
        return kalan == 1
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
