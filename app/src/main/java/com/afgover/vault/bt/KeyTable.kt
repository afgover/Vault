package com.afgover.vault.bt

/**
 * Bir fiziksel tuşun, belirli bir klavye düzeninde ürettiği karakterler.
 *
 * HID "usage" kodu **konumu** belirtir (0x14 = ABD klavyesindeki Q'nun yeri),
 * hangi karakterin çıkacağına bilgisayardaki düzen karar verir. Düzenler bu
 * yüzden "karakter → tuş" olarak DEĞİL, konum tablosu olarak yazılır ve
 * harita [KeyTable.toMap] ile mekanik olarak türetilir: elle ters çevirme,
 * sessizce yanlış karakter üreten hataların kaynağıdır.
 *
 * Boş bırakılan alan "bu tuş bu değiştiriciyle karakter üretmiyor" demektir.
 * **Ölü tuşlar** (^ ¨ ´ ` gibi: tek başına basmaz, sonraki harfle birleşir)
 * bilinçli olarak boş bırakılır — haritada olmayan karakter kullanıcıya
 * "bu düzende yazılamadı" diye bildirilir, sessizce yanlış yazılmaz.
 */
data class KeyCap(
    val usage: Int,
    val base: Char? = null,
    val shift: Char? = null,
    val altgr: Char? = null,
    /**
     * AltGr **ve** Shift birlikte. Bazı düzenlerde şifrelerde sık geçen
     * karakterler yalnız bu kombinasyonla üretilir — İtalyan düzeninde `{` ve
     * `}` böyledir. HID değiştirici baytı bir bit maskesi olduğu için iki
     * değiştirici aynı raporda gönderilebilir.
     */
    val altgrShift: Char? = null
)

/** Bir klavye düzeninin tuş konumları. */
class KeyTable(private val caps: List<KeyCap>) {

    /**
     * Konum tablosunu "karakter → tuş basımı" haritasına çevirir.
     *
     * Aynı karakter birden çok tuşta üretilebiliyorsa İLK tanım kazanır
     * (tabloda üstte olan). Çakışmalar teste takılır ([HidLayoutsTest]).
     */
    fun toMap(): Map<Char, HidLayouts.KeyStroke> = buildMap {
        caps.forEach { cap ->
            cap.base?.let { putIfAbsent(it, HidLayouts.KeyStroke(cap.usage, HidLayouts.MOD_NONE)) }
            cap.shift?.let { putIfAbsent(it, HidLayouts.KeyStroke(cap.usage, HidLayouts.MOD_SHIFT)) }
            cap.altgr?.let { putIfAbsent(it, HidLayouts.KeyStroke(cap.usage, HidLayouts.MOD_ALTGR)) }
            cap.altgrShift?.let {
                putIfAbsent(it, HidLayouts.KeyStroke(cap.usage, HidLayouts.MOD_ALTGR or HidLayouts.MOD_SHIFT))
            }
        }
    }

    /** Aynı karakteri üreten birden çok tuş var mı (tablo tutarlılık denetimi). */
    fun duplicateChars(): List<Char> {
        val sayac = HashMap<Char, Int>()
        caps.forEach { cap ->
            listOfNotNull(cap.base, cap.shift, cap.altgr, cap.altgrShift).forEach {
                sayac[it] = (sayac[it] ?: 0) + 1
            }
        }
        return sayac.filterValues { it > 1 }.keys.sorted()
    }

    companion object {
        /** Her düzende aynı olan tuşlar: boşluk, satır sonu, sekme. */
        val ORTAK = listOf(
            KeyCap(0x2C, ' '),
            KeyCap(0x28, '\n'),
            KeyCap(0x2B, '\t')
        )

        /**
         * ABD dizilimindeki harf sırası (0x04=a … 0x1D=z). Çoğu Latin düzeni
         * bunun üstüne birkaç istisna koyar; [istisnalar] o tuşları değiştirir.
         */
        fun latinHarfler(istisnalar: Map<Int, KeyCap> = emptyMap()): List<KeyCap> =
            ('a'..'z').map { c ->
                val usage = 0x04 + (c - 'a')
                istisnalar[usage] ?: KeyCap(usage, c, c.uppercaseChar())
            }
    }
}
