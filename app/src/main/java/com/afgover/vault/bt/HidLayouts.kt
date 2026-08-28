package com.afgover.vault.bt

import androidx.annotation.StringRes
import com.afgover.vault.R

/**
 * Karakter → HID klavye tuş kodu eşlemesi.
 *
 * HID kullanım kodları FİZİKSEL tuşu belirtir; hangi karakterin çıkacağı
 * bilgisayarda seçili klavye düzenine bağlıdır. Bu yüzden iki harita var:
 * bilgisayarın düzeni neyse o seçilmeli.
 */
object HidLayouts {

    const val MOD_NONE = 0x00
    const val MOD_SHIFT = 0x02
    const val MOD_ALTGR = 0x40

    data class KeyStroke(val usage: Int, val modifier: Int)

    enum class Layout(@StringRes val labelRes: Int) {
        US(R.string.bt_layout_us),
        UK(R.string.bt_layout_uk),
        TR_Q(R.string.bt_layout_tr),
        DE(R.string.bt_layout_de),
        FR(R.string.bt_layout_fr),
        ES(R.string.bt_layout_es),
        IT(R.string.bt_layout_it),
        TR_F(R.string.bt_layout_tr_f),
        PL(R.string.bt_layout_pl),
        PT(R.string.bt_layout_pt),
        SE(R.string.bt_layout_se),
        BR(R.string.bt_layout_br),
        CH(R.string.bt_layout_ch),
        NL(R.string.bt_layout_nl)
    }

    /**
     * Kullanıcının ayarlardan açtığı düzenler. Sekiz düzenin hepsi aktarım
     * ekranında birden görünmesin diye kullanıcı listeyi kendi daraltır;
     * varsayılan hepsi açıktır (kimsenin ihtiyacı olan düzen gizlenmiş
     * olmasın), ayarlardan kapatılır.
     *
     * En az bir düzen her zaman açık kalır: hiçbiri seçilmezse aktarım ekranı
     * kullanılamaz hâle gelirdi.
     */
    fun enabledLayouts(storedDisabled: String?): List<Layout> {
        val kapali = storedDisabled.orEmpty().split(",").mapNotNull { ad ->
            runCatching { Layout.valueOf(ad.trim()) }.getOrNull()
        }.toSet()
        // Sıra HER ZAMAN enum sırasıdır: tercih, açma-kapama geçmişine göre
        // kayan bir liste olarak saklanırsa çipler her açılışta başka sırada
        // görünür ve geri düşüş başka bir düzene denk gelir (denetim).
        val acik = Layout.entries.filter { it !in kapali }
        return acik.ifEmpty { Layout.entries }
    }

    /**
     * KAPATILAN düzenler saklanır, açıklar değil: ileride yeni bir düzen
     * eklendiğinde mevcut kullanıcılarda kendiliğinden görünür. İzin listesi
     * saklansaydı yeni düzen herkeste gizli kalırdı (denetim).
     */
    fun storeDisabled(layouts: Collection<Layout>): String {
        val kapali = Layout.entries.filter { it !in layouts }
        return kapali.joinToString(",") { it.name }
    }

    /**
     * Saklanmış düzen tercihini okur. Eski sürümler Türkçe Q'yu `"TR"` diye
     * yazıyordu; düzen sayısı artınca ad `TR_Q` oldu. Eski tercih sessizce
     * varsayılana düşmemeli — kullanıcı bir daha seçmek zorunda kalmasın.
     */
    fun readLayout(stored: String?, enabled: List<Layout> = Layout.entries): Layout {
        val secili = when (stored) {
            null -> Layout.TR_Q
            "TR" -> Layout.TR_Q       // eski sürümlerin adı
            else -> runCatching { Layout.valueOf(stored) }.getOrDefault(Layout.TR_Q)
        }
        // Kullanıcı bu düzeni ayarlardan kapattıysa açık olanlardan birine düş;
        // yoksa aktarım ekranında görünmeyen bir düzen seçili kalırdı.
        return if (secili in enabled) secili else enabled.first()
    }

    fun map(layout: Layout): Map<Char, KeyStroke> =
        when (layout) {
            Layout.US -> US
            Layout.UK -> UK
            Layout.TR_Q -> TR
            Layout.DE -> DE
            Layout.FR -> FR
            Layout.ES -> ES
            Layout.IT -> IT
            Layout.TR_F -> TR_F
            Layout.PL -> PL
            Layout.PT -> PT
            Layout.SE -> SE
            Layout.BR -> BR
            Layout.CH -> CH
            Layout.NL -> NL
        }

    /**
     * Seçili düzende yazıldığında, BAŞKA bir düzende farklı tuşa düşen
     * karakterler: "düzeni yanlış seçtiysen bunlar bozuk çıkar" listesi.
     *
     * Eskiden yalnız US ile TR-Q karşılaştırılıyordu ve yorum "harfler ve
     * rakamlar iki düzende aynıdır, tehlike simgelerde" diyordu. Bu, düzen
     * sayısı ikiyken doğruydu; AZERTY'de A↔Q ve Z↔W yer değiştirir, rakamlar
     * Shift ister, QWERTZ'de Y↔Z değişir — yani artık HARFLER DE duyarlıdır.
     * Şifreler çoğunlukla harf ve rakamdan oluştuğu için yanlış düzen artık
     * birkaç simgeyi değil neredeyse her karakteri bozar.
     */
    fun layoutSensitiveChars(text: String, selected: Layout): List<Char> {
        val secili = map(selected)
        val digerleri = Layout.entries.filter { it != selected }.map { map(it) }
        return text.toSet()
            .filter { ch -> digerleri.any { it[ch] != secili[ch] } }
            .sorted()
    }

    /**
     * Bu metin, DESTEKLENEN HER DÜZENDE farklı bir tuş dizisine düşer; yani
     * bilgisayarda beklenenden farklı çıkması "yanlış düzen" demektir.
     * Harfleri de içerir: AZERTY/QWERTZ ile birlikte harfler de ayırt edici
     * oldu, yalnız simgelerden oluşan bir test artık yetmiyor.
     */
    const val LAYOUT_TEST_TEXT = "azqwym1! @ - _ ; : / ? . , ( ) ="

    /** Her düzen çifti bu metinde farklı tuş dizisi üretiyor mu (test güvencesi). */
    internal fun testTextKeystrokes(layout: Layout): List<KeyStroke?> =
        LAYOUT_TEST_TEXT.map { map(layout)[it] }

    /**
     * Hız testi bloğu ve metni: TAŞIMAyı ölçer, düzeni değil. Boşlukla
     * ayrılmış 10 özdeş blok; seçilen tempoda rapor düşerse ya da tuş tekrarı
     * olursa bloklardan biri diğerlerine benzemez, gözle anında görülür.
     *
     * DİKKAT: düzenden bağımsız DEĞİLDİR — AZERTY'de harfler, QWERTZ'de y/z
     * yer değiştirir. Bu yüzden hız testi ancak DÜZEN TESTİ geçtikten sonra
     * anlamlıdır; arayüz bu sırayı gözetir.
     */
    const val SPEED_TEST_BLOCK = "abcdefghjk0123456789"
    val SPEED_TEST_TEXT: String = List(10) { SPEED_TEST_BLOCK }.joinToString(" ")

    /**
     * ABD (ANSI) düzeni — konum tablosu. Mevcut, cihazda doğrulanmış haritadan
     * birebir türetildi; eşdeğerliği [com.afgover.vault.data.HidLayoutsTest]
     * koruyor.
     */
    private val US_TABLE = KeyTable(
        KeyTable.latinHarfler() + KeyTable.ORTAK + listOf(
            KeyCap(0x1E, '1', '!'), KeyCap(0x1F, '2', '@'), KeyCap(0x20, '3', '#'),
            KeyCap(0x21, '4', '$'), KeyCap(0x22, '5', '%'), KeyCap(0x23, '6', '^'),
            KeyCap(0x24, '7', '&'), KeyCap(0x25, '8', '*'), KeyCap(0x26, '9', '('),
            KeyCap(0x27, '0', ')'),
            KeyCap(0x2D, '-', '_'), KeyCap(0x2E, '=', '+'),
            KeyCap(0x2F, '[', '{'), KeyCap(0x30, ']', '}'), KeyCap(0x31, '\\', '|'),
            KeyCap(0x33, ';', ':'), KeyCap(0x34, '\'', '"'), KeyCap(0x35, '`', '~'),
            KeyCap(0x36, ',', '<'), KeyCap(0x37, '.', '>'), KeyCap(0x38, '/', '?')
        )
    )

    /**
     * Türkçe Q (ISO) — konum tablosu. Cihazda doğrulandı (vault_takip B-050).
     * `^` (Shift+3) ÖLÜ TUŞTUR: tek başına karakter basmaz, sonraki sesliyle
     * birleşir (â). Bu yüzden tabloda yok — yazılamayan karakter olarak
     * bildirilmesi, sessizce yanlış üretilmesinden iyidir.
     */
    private val TR_Q_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(
                // TR-Q'da fiziksel I tuşu 'ı', home-row'daki ek tuş 'i' üretir
                0x0C to KeyCap(0x0C, 'ı', 'I'),
                0x14 to KeyCap(0x14, 'q', 'Q', altgr = '@')
            )
        ) + KeyTable.ORTAK + listOf(
            KeyCap(0x1E, '1', '!'), KeyCap(0x1F, '2', '\''),
            KeyCap(0x20, '3', null, altgr = '#'),          // Shift+3 = ^ ölü tuş
            KeyCap(0x21, '4', '+', altgr = '$'),
            KeyCap(0x22, '5', '%'), KeyCap(0x23, '6', '&'),
            KeyCap(0x24, '7', '/', altgr = '{'), KeyCap(0x25, '8', '(', altgr = '['),
            KeyCap(0x26, '9', ')', altgr = ']'), KeyCap(0x27, '0', '=', altgr = '}'),
            KeyCap(0x2D, '*', '?', altgr = '\\'),
            KeyCap(0x2E, '-', '_'),
            KeyCap(0x2F, 'ğ', 'Ğ'), KeyCap(0x30, 'ü', 'Ü'),
            KeyCap(0x32, ',', ';'), KeyCap(0x33, 'ş', 'Ş'),
            KeyCap(0x34, 'i', 'İ'), KeyCap(0x35, '"'),
            KeyCap(0x36, 'ö', 'Ö'), KeyCap(0x37, 'ç', 'Ç'),
            KeyCap(0x38, '.', ':'),
            KeyCap(0x64, '<', '>', altgr = '|')
        )
    )

    private val US: Map<Char, KeyStroke> = US_TABLE.toMap()
    private val TR: Map<Char, KeyStroke> = TR_Q_TABLE.toMap()

    /**
     * İngiliz (UK, ISO) — konum tablosu.
     * UK ISO, US ile aynı QWERTY harf sırasını ve aynı rakam sırası tabanını kullanır; farklar noktalamada toplanır ve tam olarak şifrelerde sık geçen karak
     *
     * Hakem doğrulaması: onaylandı.
     */
    private val UK_TABLE = KeyTable(
        KeyTable.ORTAK + listOf(
            KeyCap(0x04, 'a', shift = 'A'),
            KeyCap(0x05, 'b', shift = 'B'),
            KeyCap(0x06, 'c', shift = 'C'),
            KeyCap(0x07, 'd', shift = 'D'),
            KeyCap(0x08, 'e', shift = 'E'),
            KeyCap(0x09, 'f', shift = 'F'),
            KeyCap(0x0A, 'g', shift = 'G'),
            KeyCap(0x0B, 'h', shift = 'H'),
            KeyCap(0x0C, 'i', shift = 'I'),
            KeyCap(0x0D, 'j', shift = 'J'),
            KeyCap(0x0E, 'k', shift = 'K'),
            KeyCap(0x0F, 'l', shift = 'L'),
            KeyCap(0x10, 'm', shift = 'M'),
            KeyCap(0x11, 'n', shift = 'N'),
            KeyCap(0x12, 'o', shift = 'O'),
            KeyCap(0x13, 'p', shift = 'P'),
            KeyCap(0x14, 'q', shift = 'Q'),
            KeyCap(0x15, 'r', shift = 'R'),
            KeyCap(0x16, 's', shift = 'S'),
            KeyCap(0x17, 't', shift = 'T'),
            KeyCap(0x18, 'u', shift = 'U'),
            KeyCap(0x19, 'v', shift = 'V'),
            KeyCap(0x1A, 'w', shift = 'W'),
            KeyCap(0x1B, 'x', shift = 'X'),
            KeyCap(0x1C, 'y', shift = 'Y'),
            KeyCap(0x1D, 'z', shift = 'Z'),
            KeyCap(0x1E, '1', shift = '!'),
            KeyCap(0x1F, '2', shift = '"'),
            KeyCap(0x20, '3', shift = '£'),
            KeyCap(0x21, '4', shift = '$', altgr = '€'),
            KeyCap(0x22, '5', shift = '%'),
            KeyCap(0x23, '6', shift = '^'),
            KeyCap(0x24, '7', shift = '&'),
            KeyCap(0x25, '8', shift = '*'),
            KeyCap(0x26, '9', shift = '('),
            KeyCap(0x27, '0', shift = ')'),
            KeyCap(0x2D, '-', shift = '_'),
            KeyCap(0x2E, '=', shift = '+'),
            KeyCap(0x2F, '[', shift = '{'),
            KeyCap(0x30, ']', shift = '}'),
            KeyCap(0x31, '#', shift = '~'),
            KeyCap(0x33, ';', shift = ':'),
            KeyCap(0x34, '\'', shift = '@'),
            KeyCap(0x35, '`', shift = '¬'),
            KeyCap(0x36, ',', shift = '<'),
            KeyCap(0x37, '.', shift = '>'),
            KeyCap(0x38, '/', shift = '?'),
            KeyCap(0x64, '\\', shift = '|')
        )
    )


    /**
     * Almanca (QWERTZ, ISO) — konum tablosu.
     * Almanca T1 (QWERTZ, ISO) düzeninin US'ten farkları, KONUM bazında: (1) 0x1C konumu (US-Y) 'z', 0x1D konumu (US-Z) 'y' üretir — Y/Z yer değiştirir. (2)
     *
     * Hakem doğrulaması: onaylandı.
     */
    private val DE_TABLE = KeyTable(
        KeyTable.ORTAK + listOf(
            KeyCap(0x04, 'a', shift = 'A'),
            KeyCap(0x05, 'b', shift = 'B'),
            KeyCap(0x06, 'c', shift = 'C'),
            KeyCap(0x07, 'd', shift = 'D'),
            KeyCap(0x08, 'e', shift = 'E', altgr = '€'),
            KeyCap(0x09, 'f', shift = 'F'),
            KeyCap(0x0A, 'g', shift = 'G'),
            KeyCap(0x0B, 'h', shift = 'H'),
            KeyCap(0x0C, 'i', shift = 'I'),
            KeyCap(0x0D, 'j', shift = 'J'),
            KeyCap(0x0E, 'k', shift = 'K'),
            KeyCap(0x0F, 'l', shift = 'L'),
            KeyCap(0x10, 'm', shift = 'M'),
            KeyCap(0x11, 'n', shift = 'N'),
            KeyCap(0x12, 'o', shift = 'O'),
            KeyCap(0x13, 'p', shift = 'P'),
            KeyCap(0x14, 'q', shift = 'Q', altgr = '@'),
            KeyCap(0x15, 'r', shift = 'R'),
            KeyCap(0x16, 's', shift = 'S'),
            KeyCap(0x17, 't', shift = 'T'),
            KeyCap(0x18, 'u', shift = 'U'),
            KeyCap(0x19, 'v', shift = 'V'),
            KeyCap(0x1A, 'w', shift = 'W'),
            KeyCap(0x1B, 'x', shift = 'X'),
            KeyCap(0x1C, 'z', shift = 'Z'),
            KeyCap(0x1D, 'y', shift = 'Y'),
            KeyCap(0x1E, '1', shift = '!'),
            KeyCap(0x1F, '2', shift = '"'),
            KeyCap(0x20, '3', shift = '§'),
            KeyCap(0x21, '4', shift = '$'),
            KeyCap(0x22, '5', shift = '%'),
            KeyCap(0x23, '6', shift = '&'),
            KeyCap(0x24, '7', shift = '/', altgr = '{'),
            KeyCap(0x25, '8', shift = '(', altgr = '['),
            KeyCap(0x26, '9', shift = ')', altgr = ']'),
            KeyCap(0x27, '0', shift = '=', altgr = '}'),
            KeyCap(0x2D, 'ß', shift = '?', altgr = '\\'),
            KeyCap(0x2F, 'ü', shift = 'Ü'),
            KeyCap(0x30, '+', shift = '*', altgr = '~'),
            KeyCap(0x31, '#', shift = '\''),
            KeyCap(0x33, 'ö', shift = 'Ö'),
            KeyCap(0x34, 'ä', shift = 'Ä'),
            KeyCap(0x36, ',', shift = ';'),
            KeyCap(0x37, '.', shift = ':'),
            KeyCap(0x38, '-', shift = '_'),
            KeyCap(0x64, '<', shift = '>', altgr = '|')
        )
    )


    /**
     * Fransızca (AZERTY, ISO) — konum tablosu.
     * US'e göre en kritik farklar: (1) Harf konumları kayıyor — 0x14 (US-Q) = a, 0x04 (US-A) = q, 0x1A (US-W) = z, 0x1D (US-Z) = w, ve M harfi 0x33'te (US-;
     *
     * Hakem doğrulaması: onaylandı.
     */
    private val FR_TABLE = KeyTable(
        KeyTable.ORTAK + listOf(
            KeyCap(0x04, 'q', shift = 'Q'),
            KeyCap(0x05, 'b', shift = 'B'),
            KeyCap(0x06, 'c', shift = 'C'),
            KeyCap(0x07, 'd', shift = 'D'),
            KeyCap(0x08, 'e', shift = 'E', altgr = '€'),
            KeyCap(0x09, 'f', shift = 'F'),
            KeyCap(0x0A, 'g', shift = 'G'),
            KeyCap(0x0B, 'h', shift = 'H'),
            KeyCap(0x0C, 'i', shift = 'I'),
            KeyCap(0x0D, 'j', shift = 'J'),
            KeyCap(0x0E, 'k', shift = 'K'),
            KeyCap(0x0F, 'l', shift = 'L'),
            KeyCap(0x10, ',', shift = '?'),
            KeyCap(0x11, 'n', shift = 'N'),
            KeyCap(0x12, 'o', shift = 'O'),
            KeyCap(0x13, 'p', shift = 'P'),
            KeyCap(0x14, 'a', shift = 'A'),
            KeyCap(0x15, 'r', shift = 'R'),
            KeyCap(0x16, 's', shift = 'S'),
            KeyCap(0x17, 't', shift = 'T'),
            KeyCap(0x18, 'u', shift = 'U'),
            KeyCap(0x19, 'v', shift = 'V'),
            KeyCap(0x1A, 'z', shift = 'Z'),
            KeyCap(0x1B, 'x', shift = 'X'),
            KeyCap(0x1C, 'y', shift = 'Y'),
            KeyCap(0x1D, 'w', shift = 'W'),
            KeyCap(0x1E, '&', shift = '1'),
            KeyCap(0x1F, 'é', shift = '2'),
            KeyCap(0x20, '"', shift = '3', altgr = '#'),
            KeyCap(0x21, '\'', shift = '4', altgr = '{'),
            KeyCap(0x22, '(', shift = '5', altgr = '['),
            KeyCap(0x23, '-', shift = '6', altgr = '|'),
            KeyCap(0x24, 'è', shift = '7'),
            KeyCap(0x25, '_', shift = '8', altgr = '\\'),
            KeyCap(0x26, 'ç', shift = '9'),
            KeyCap(0x27, 'à', shift = '0', altgr = '@'),
            KeyCap(0x2D, ')', shift = '°', altgr = ']'),
            KeyCap(0x2E, '=', shift = '+', altgr = '}'),
            KeyCap(0x30, '$', shift = '£', altgr = '¤'),
            KeyCap(0x31, '*', shift = 'µ'),
            KeyCap(0x33, 'm', shift = 'M'),
            KeyCap(0x34, 'ù', shift = '%'),
            KeyCap(0x35, '²'),
            KeyCap(0x36, ';', shift = '.'),
            KeyCap(0x37, ':', shift = '/'),
            KeyCap(0x38, '!', shift = '§'),
            KeyCap(0x64, '<', shift = '>')
        )
    )


    /**
     * İspanyolca (ISO) — konum tablosu.
     * İspanyol ISO düzeninin US'ten kritik farkları: (1) Noktalama tamamen kaymış — US'te ;: olan 0x33 konumu Ñ, US'te '" olan 0x34 konumu ÖLÜ ´/¨, US'te \|
     *
     * Hakem doğrulaması: onaylandı.
     */
    private val ES_TABLE = KeyTable(
        KeyTable.ORTAK + listOf(
            KeyCap(0x04, 'a', shift = 'A'),
            KeyCap(0x05, 'b', shift = 'B'),
            KeyCap(0x06, 'c', shift = 'C'),
            KeyCap(0x07, 'd', shift = 'D'),
            KeyCap(0x08, 'e', shift = 'E', altgr = '€'),
            KeyCap(0x09, 'f', shift = 'F'),
            KeyCap(0x0A, 'g', shift = 'G'),
            KeyCap(0x0B, 'h', shift = 'H'),
            KeyCap(0x0C, 'i', shift = 'I'),
            KeyCap(0x0D, 'j', shift = 'J'),
            KeyCap(0x0E, 'k', shift = 'K'),
            KeyCap(0x0F, 'l', shift = 'L'),
            KeyCap(0x10, 'm', shift = 'M'),
            KeyCap(0x11, 'n', shift = 'N'),
            KeyCap(0x12, 'o', shift = 'O'),
            KeyCap(0x13, 'p', shift = 'P'),
            KeyCap(0x14, 'q', shift = 'Q'),
            KeyCap(0x15, 'r', shift = 'R'),
            KeyCap(0x16, 's', shift = 'S'),
            KeyCap(0x17, 't', shift = 'T'),
            KeyCap(0x18, 'u', shift = 'U'),
            KeyCap(0x19, 'v', shift = 'V'),
            KeyCap(0x1A, 'w', shift = 'W'),
            KeyCap(0x1B, 'x', shift = 'X'),
            KeyCap(0x1C, 'y', shift = 'Y'),
            KeyCap(0x1D, 'z', shift = 'Z'),
            KeyCap(0x1E, '1', shift = '!', altgr = '|'),
            KeyCap(0x1F, '2', shift = '"', altgr = '@'),
            KeyCap(0x20, '3', shift = '·', altgr = '#'),
            KeyCap(0x21, '4', shift = '$', altgr = '~'),
            KeyCap(0x22, '5', shift = '%'),
            KeyCap(0x23, '6', shift = '&'),
            KeyCap(0x24, '7', shift = '/'),
            KeyCap(0x25, '8', shift = '('),
            KeyCap(0x26, '9', shift = ')'),
            KeyCap(0x27, '0', shift = '='),
            KeyCap(0x2D, '\'', shift = '?'),
            KeyCap(0x2E, '¡', shift = '¿'),
            KeyCap(0x2F, null, shift = null, altgr = '['),
            KeyCap(0x30, '+', shift = '*', altgr = ']'),
            KeyCap(0x31, 'ç', shift = 'Ç', altgr = '}'),
            KeyCap(0x33, 'ñ', shift = 'Ñ'),
            KeyCap(0x34, null, shift = null, altgr = '{'),
            KeyCap(0x35, 'º', shift = 'ª', altgr = '\\'),
            KeyCap(0x36, ',', shift = ';'),
            KeyCap(0x37, '.', shift = ':'),
            KeyCap(0x38, '-', shift = '_'),
            KeyCap(0x64, '<', shift = '>')
        )
    )


    /**
     * İtalyanca (ISO) — konum tablosu.
     * İtalyan ISO düzeni tam QWERTY'dir: harflerin (0x04-0x1D) ve rakamların base değerleri US ile birebir aynıdır — tehlike tamamen simgelerdedir. US'ten f
     *
     * Hakem doğrulaması: onaylandı.
     */
    private val IT_TABLE = KeyTable(
        KeyTable.ORTAK + listOf(
            KeyCap(0x04, 'a', shift = 'A'),
            KeyCap(0x05, 'b', shift = 'B'),
            KeyCap(0x06, 'c', shift = 'C'),
            KeyCap(0x07, 'd', shift = 'D'),
            KeyCap(0x08, 'e', shift = 'E', altgr = '€'),
            KeyCap(0x09, 'f', shift = 'F'),
            KeyCap(0x0A, 'g', shift = 'G'),
            KeyCap(0x0B, 'h', shift = 'H'),
            KeyCap(0x0C, 'i', shift = 'I'),
            KeyCap(0x0D, 'j', shift = 'J'),
            KeyCap(0x0E, 'k', shift = 'K'),
            KeyCap(0x0F, 'l', shift = 'L'),
            KeyCap(0x10, 'm', shift = 'M'),
            KeyCap(0x11, 'n', shift = 'N'),
            KeyCap(0x12, 'o', shift = 'O'),
            KeyCap(0x13, 'p', shift = 'P'),
            KeyCap(0x14, 'q', shift = 'Q'),
            KeyCap(0x15, 'r', shift = 'R'),
            KeyCap(0x16, 's', shift = 'S'),
            KeyCap(0x17, 't', shift = 'T'),
            KeyCap(0x18, 'u', shift = 'U'),
            KeyCap(0x19, 'v', shift = 'V'),
            KeyCap(0x1A, 'w', shift = 'W'),
            KeyCap(0x1B, 'x', shift = 'X'),
            KeyCap(0x1C, 'y', shift = 'Y'),
            KeyCap(0x1D, 'z', shift = 'Z'),
            KeyCap(0x1E, '1', shift = '!'),
            KeyCap(0x1F, '2', shift = '"'),
            KeyCap(0x20, '3', shift = '£'),
            KeyCap(0x21, '4', shift = '$'),
            KeyCap(0x22, '5', shift = '%'),
            KeyCap(0x23, '6', shift = '&'),
            KeyCap(0x24, '7', shift = '/'),
            KeyCap(0x25, '8', shift = '('),
            KeyCap(0x26, '9', shift = ')'),
            KeyCap(0x27, '0', shift = '='),
            KeyCap(0x2D, '\'', shift = '?'),
            KeyCap(0x2E, 'ì', shift = '^'),
            KeyCap(0x2F, 'è', shift = 'é', altgr = '[', altgrShift = '{'),
            KeyCap(0x30, '+', shift = '*', altgr = ']', altgrShift = '}'),
            KeyCap(0x31, 'ù', shift = '§'),
            KeyCap(0x33, 'ò', shift = 'ç', altgr = '@'),
            KeyCap(0x34, 'à', shift = '°', altgr = '#'),
            KeyCap(0x35, '\\', shift = '|'),
            KeyCap(0x36, ',', shift = ';'),
            KeyCap(0x37, '.', shift = ':'),
            KeyCap(0x38, '-', shift = '_'),
            KeyCap(0x64, '<', shift = '>')
        )
    )

    private val UK: Map<Char, KeyStroke> = UK_TABLE.toMap()
    private val DE: Map<Char, KeyStroke> = DE_TABLE.toMap()
    private val FR: Map<Char, KeyStroke> = FR_TABLE.toMap()
    private val ES: Map<Char, KeyStroke> = ES_TABLE.toMap()
    /**
     * Türkçe F — konum tablosu.
     * TR-F, US ile yalnız rakam tabanlarını (1-0) ve boşluk/enter/tab'ı paylaşır; geri kalan her şey farklıdır.
     *
     * İki bağımsız hakem onayladı; ikincisi Microsoft'un resmî TR-F düzen
     * dosyasıyla (KBDTUF) satır satır karşılaştırdı.
     *
     * `|` düzende iki tuştan da çıkıyor (AltGr+0x2E ve AltGr+0x64); tabloda
     * yalnız 0x2E bırakıldı: 0x64 (ISO 102. tuş) ANSI-only ana bilgisayarlarda
     * eşlenmeyebilir, 0x2E her yerde vardır.
     */
    private val TR_F_TABLE = KeyTable(
        KeyTable.ORTAK + listOf(
            KeyCap(0x04, 'u', shift = 'U'),
            KeyCap(0x05, 'ç', shift = 'Ç'),
            KeyCap(0x06, 'v', shift = 'V'),
            KeyCap(0x07, 'e', shift = 'E'),
            KeyCap(0x08, 'ğ', shift = 'Ğ'),
            KeyCap(0x09, 'a', shift = 'A'),
            KeyCap(0x0A, 'ü', shift = 'Ü'),
            KeyCap(0x0B, 't', shift = 'T'),
            KeyCap(0x0C, 'n', shift = 'N'),
            KeyCap(0x0D, 'k', shift = 'K'),
            KeyCap(0x0E, 'm', shift = 'M'),
            KeyCap(0x0F, 'l', shift = 'L'),
            KeyCap(0x10, 's', shift = 'S'),
            KeyCap(0x11, 'z', shift = 'Z'),
            KeyCap(0x12, 'h', shift = 'H'),
            KeyCap(0x13, 'p', shift = 'P'),
            KeyCap(0x14, 'f', shift = 'F', altgr = '@'),
            KeyCap(0x15, 'ı', shift = 'I'),
            KeyCap(0x16, 'i', shift = 'İ'),
            KeyCap(0x17, 'o', shift = 'O'),
            KeyCap(0x18, 'r', shift = 'R'),
            KeyCap(0x19, 'c', shift = 'C'),
            KeyCap(0x1A, 'g', shift = 'G'),
            KeyCap(0x1B, 'ö', shift = 'Ö'),
            KeyCap(0x1C, 'd', shift = 'D'),
            KeyCap(0x1D, 'j', shift = 'J'),
            KeyCap(0x1E, '1', shift = '!'),
            KeyCap(0x1F, '2', shift = '"'),
            KeyCap(0x20, '3', shift = null, altgr = '#'),
            KeyCap(0x21, '4', shift = '$'),
            KeyCap(0x22, '5', shift = '%'),
            KeyCap(0x23, '6', shift = '&'),
            KeyCap(0x24, '7', shift = '\'', altgr = '{'),
            KeyCap(0x25, '8', shift = '(', altgr = '['),
            KeyCap(0x26, '9', shift = ')', altgr = ']'),
            KeyCap(0x27, '0', shift = '=', altgr = '}'),
            KeyCap(0x2D, '/', shift = '?', altgr = '\\'),
            KeyCap(0x2E, '-', shift = '_', altgr = '|'),
            KeyCap(0x2F, 'q', shift = 'Q'),
            KeyCap(0x30, 'w', shift = 'W'),
            KeyCap(0x31, 'x', shift = 'X'),
            KeyCap(0x33, 'y', shift = 'Y'),
            KeyCap(0x34, 'ş', shift = 'Ş'),
            KeyCap(0x35, '+', shift = '*'),
            KeyCap(0x36, 'b', shift = 'B'),
            KeyCap(0x37, '.', shift = ':'),
            KeyCap(0x38, ',', shift = ';'),
            KeyCap(0x64, '<', shift = '>')
        )
    )


    /**
     * Lehçe (Programcı, "Polish Programmers") — konum tablosu.
     *
     * Standart Polonya düzeni (214) daktilo mirası taşır ve köşeli parantez
     * gibi programlama için gerekli karakterleri taşımıştır; bu yüzden
     * Microsoft ayrı bir "Programcı" düzeni tanımladı: harfler, rakamlar ve
     * noktalama US ile BİREBİR aynı konumda, tek fark dokuz harfte AltGr ile
     * gelen Lehçe aksanlı karakterler (ą ć ę ł ń ó ś ź ż). Diğer harflerde
     * AltGr yok.
     *
     * Kaynak: Microsoft Learn resmi klavye düzeni belgesi
     * (learn.microsoft.com/globalization/keyboards/kbdpl1) + kbdlayout.info,
     * ikisi çakıştı.
     */
    private val PL_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(
                0x04 to KeyCap(0x04, 'a', 'A', altgr = 'ą'),
                0x06 to KeyCap(0x06, 'c', 'C', altgr = 'ć'),
                0x08 to KeyCap(0x08, 'e', 'E', altgr = 'ę'),
                0x0F to KeyCap(0x0F, 'l', 'L', altgr = 'ł'),
                0x11 to KeyCap(0x11, 'n', 'N', altgr = 'ń'),
                0x12 to KeyCap(0x12, 'o', 'O', altgr = 'ó'),
                0x16 to KeyCap(0x16, 's', 'S', altgr = 'ś'),
                0x1B to KeyCap(0x1B, 'x', 'X', altgr = 'ź'),
                0x1D to KeyCap(0x1D, 'z', 'Z', altgr = 'ż')
            )
        ) + KeyTable.ORTAK + listOf(
            KeyCap(0x1E, '1', '!'), KeyCap(0x1F, '2', '@'), KeyCap(0x20, '3', '#'),
            KeyCap(0x21, '4', '$'), KeyCap(0x22, '5', '%'), KeyCap(0x23, '6', '^'),
            KeyCap(0x24, '7', '&'), KeyCap(0x25, '8', '*'), KeyCap(0x26, '9', '('),
            KeyCap(0x27, '0', ')'),
            KeyCap(0x2D, '-', '_'), KeyCap(0x2E, '=', '+'),
            KeyCap(0x2F, '[', '{'), KeyCap(0x30, ']', '}'), KeyCap(0x31, '\\', '|'),
            KeyCap(0x33, ';', ':'), KeyCap(0x34, '\'', '"'), KeyCap(0x35, '`', '~'),
            KeyCap(0x36, ',', '<'), KeyCap(0x37, '.', '>'), KeyCap(0x38, '/', '?')
        )
    )

    private val IT: Map<Char, KeyStroke> = IT_TABLE.toMap()
    private val TR_F: Map<Char, KeyStroke> = TR_F_TABLE.toMap()

    /**
     * Portekizce (Avrupa, ISO) — konum tablosu.
     *
     * Kaynak: Microsoft'un resmî klavye düzeni belgesi
     * (learn.microsoft.com/globalization/keyboards/kbdpo.html), ham Unicode
     * ad etiketleriyle (ör. "U+0021 EXCLAMATION MARK") — özetleme değil
     * birebir okuma.
     *
     * L'den sonra Ç, ondan sonra º/ª (eril/dişil sıra sayı işareti — gerçek
     * karakterdir, ölü tuş değil). P'den sonraki `+`/`*` tuşundan hemen
     * sonraki konum (ISO 0x30) akut/gravis ÖLÜ TUŞTUR, tabloda yok. Üst-sol
     * köşe (0x35, ABD'de `~) için kaynakta net bir okuma çıkmadı — tahmin
     * etmek yerine boş bırakıldı; şifrelerde bu karakter aranmıyor.
     * ISO ekstra tuşu (0x64, Z'den önce) `<`/`>` üretiyor.
     */
    private val PT_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(
                0x33 to KeyCap(0x33, 'ç', 'Ç')
            )
        ) + listOf(
            KeyCap(0x2C, ' '), KeyCap(0x28, '\n'), KeyCap(0x2B, '\t')
        ) + listOf(
            KeyCap(0x1E, '1', '!'),
            KeyCap(0x1F, '2', '"', altgr = '@'),
            KeyCap(0x20, '3', '#', altgr = '£'),
            KeyCap(0x21, '4', '$', altgr = '§'),
            KeyCap(0x22, '5', '%', altgr = '€'),
            KeyCap(0x23, '6', '&'),
            KeyCap(0x24, '7', '/', altgr = '{'),
            KeyCap(0x25, '8', '(', altgr = '['),
            KeyCap(0x26, '9', ')', altgr = ']'),
            KeyCap(0x27, '0', '=', altgr = '}'),
            KeyCap(0x2D, '\'', '?'),
            KeyCap(0x2E, '«', '»'),
            KeyCap(0x2F, '+', '*'),
            // 0x30 (P'den sonraki ikinci tuş): akut/gravis ÖLÜ TUŞ — atlanıyor
            KeyCap(0x34, 'º', 'ª'),
            // 0x35 (üst-sol köşe): kaynakta belirsiz — atlanıyor
            KeyCap(0x36, ',', ';'),
            KeyCap(0x37, '.', ':'),
            KeyCap(0x38, '-', '_'),
            KeyCap(0x64, '<', '>')
        )
    )

    private val PL: Map<Char, KeyStroke> = PL_TABLE.toMap()

    /**
     * İsveççe (Nordic, ISO) — konum tablosu. Finlandiya'da da aynı fiziksel
     * dizilim kullanılıyor (kbdfi = kbdsw ile aynı harita).
     *
     * Kaynak: Microsoft'un resmî klavye düzeni belgesi
     * (learn.microsoft.com/globalization/keyboards/kbdsw.html), ham Unicode
     * ad etiketleriyle, birebir okuma.
     *
     * L'den sonra ö, ondan sonra ä. P'den sonra å. Üst-sol köşe (0x35) § / ½
     * üretiyor (ABD'deki `~ karşılığı). Akut (´), çift nokta (¨), sirkumfleks
     * (^) ve gravis (`) — dördü de ÖLÜ TUŞ, tabloda yok.
     */
    private val SE_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(0x10 to KeyCap(0x10, 'm', 'M', altgr = 'µ'))
        ) + KeyTable.ORTAK + listOf(
            KeyCap(0x1E, '1', '!'),
            KeyCap(0x1F, '2', '"', altgr = '@'),
            KeyCap(0x20, '3', '#', altgr = '£'),
            KeyCap(0x21, '4', '¤', altgr = '$'),
            KeyCap(0x22, '5', '%', altgr = '€'),
            KeyCap(0x23, '6', '&'),
            KeyCap(0x24, '7', '/', altgr = '{'),
            KeyCap(0x25, '8', '(', altgr = '['),
            KeyCap(0x26, '9', ')', altgr = ']'),
            KeyCap(0x27, '0', '=', altgr = '}'),
            KeyCap(0x2D, '+', '?', altgr = '\\'),
            // 0x2E (0'dan sonraki ikinci tuş): akut/gravis ÖLÜ TUŞ — atlanıyor
            KeyCap(0x2F, 'å', 'Å'),
            // 0x30 (P'den sonraki ikinci tuş): ¨/^ ÖLÜ TUŞ — atlanıyor
            KeyCap(0x33, 'ö', 'Ö'),
            KeyCap(0x34, 'ä', 'Ä'),
            KeyCap(0x32, '\'', '*'),
            KeyCap(0x35, '§', '½'),
            KeyCap(0x36, ',', ';'),
            KeyCap(0x37, '.', ':'),
            KeyCap(0x38, '-', '_'),
            KeyCap(0x64, '<', '>', altgr = '|')
        )
    )

    private val PT: Map<Char, KeyStroke> = PT_TABLE.toMap()

    /**
     * Brezilya (ABNT2, ISO) — konum tablosu.
     *
     * Kaynak: Microsoft'un arşivlenmiş resmî düzen belgesi
     * (learn.microsoft.com/msdn-files/resources/msdn/goglobal/keyboards/kbdbr.html),
     * ham Unicode ad etiketleriyle, birebir okuma.
     *
     * ABNT2 standart ISO'dan FAZLA tuş taşıyor (kayıtlarda "shiftler arası
     * 12 tuş" — ABD'de 10). İki ekstra konum var: L'den sonra ç, ondan
     * sonra ~ ölü tuş, ondan sonra ] (0x31 — ISO'nun "Non-US #" konumu).
     * M'den sonra ise gerçek bir "/" tuşu var (ABNT_C1, HID International1
     * = 0x87) — çoğu düzende "/" bir OEM konumundayken burada ayrı, özel bir
     * tuş. Akut (´), sirkumfleks (~) ve çift nokta (¨) ÖLÜ TUŞ, tabloda yok.
     * AltGr+C → ₢ (eski cruzeiro işareti) Wikipedia'yla da doğrulandı.
     *
     * Not: HID usage 0x87 bu kod tabanında ilk kez kullanılıyor — diğer
     * düzenlerin hepsi 0x04-0x38 ve 0x64 aralığında kalıyor.
     * `BtHidManager.stroke.usage.toByte()` her kodu aynı şekilde tek bayt
     * olarak rapora yazıyor, yani ACTARIM tarafında özel bir şey gerekmiyor
     * (doğrulandı, kod okunarak). Kalan tek soru diğer tüm düzenlerle aynı:
     * hedef işletim sisteminin bu konumu gerçekten ABNT_C1 "/" tuşu olarak
     * yorumlaması — cihazda deneme bekliyor (R-003).
     */
    private val BR_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(0x06 to KeyCap(0x06, 'c', 'C', altgr = '₢'))
        ) + KeyTable.ORTAK + listOf(
            KeyCap(0x1E, '1', '!'),
            KeyCap(0x1F, '2', '@'),
            KeyCap(0x20, '3', '#'),
            KeyCap(0x21, '4', '$'),
            KeyCap(0x22, '5', '%'),
            KeyCap(0x23, '6'), // shift: ¨ ÖLÜ TUŞ — yalnız base var
            KeyCap(0x24, '7', '&'),
            KeyCap(0x25, '8', '*'),
            KeyCap(0x26, '9', '('),
            KeyCap(0x27, '0', ')'),
            KeyCap(0x2D, '-', '_'),
            KeyCap(0x2E, '=', '+'),
            // 0x2F (P'den sonra): ´ ÖLÜ TUŞ — atlanıyor
            KeyCap(0x30, '[', '{'),
            KeyCap(0x31, ']', '}'),
            KeyCap(0x33, 'ç', 'Ç'),
            // 0x34 (Ç'den sonra): ~ ÖLÜ TUŞ — atlanıyor
            KeyCap(0x35, '\'', '"'),
            KeyCap(0x36, ',', '<'),
            KeyCap(0x37, '.', '>'),
            KeyCap(0x38, ';', ':'),
            KeyCap(0x64, '\\', '|'),
            KeyCap(0x87, '/', '?')
        )
    )

    private val SE: Map<Char, KeyStroke> = SE_TABLE.toMap()
    private val BR: Map<Char, KeyStroke> = BR_TABLE.toMap()

    /**
     * İsviçre Almancası (QWERTZ, ISO) — konum tablosu.
     *
     * Kaynak: Microsoft'un resmî klavye düzeni belgesi
     * (learn.microsoft.com/globalization/keyboards/kbdsg.html), ham Unicode
     * ad etiketleriyle, birebir okuma.
     *
     * Almanca gibi Y/Z yer değiştirir. Şifre karakterleri açısından en
     * kritik fark: rakam satırının SHIFT hâli tamamen farklı simgeler
     * üretir (+ " * ç % & / ( ) = ? — Almanca'nın ! " § $ % & / ( ) = 'inden
     * apayrı), yani "hangi düzen seçili" burada özellikle önemli.
     *
     * Üst-sol köşe tuşu (0x35) base=§, shift=°; bu ikisi digit-4 ve digit-5
     * konumlarının AltGr hâlinde DE TEKRAR üretiliyor (gerçek klavyede iki
     * yoldan erişilebilir simgeler) — tabloda çakışma olmasın diye yalnız
     * 0x35'teki tanım bırakıldı, digit-4/5'in AltGr'ı atlandı (B-104'teki
     * gibi bir test kırılması değil, gerçek fiziksel fazlalık).
     *
     * Sirkumfleks (^), gravis (`) ve tilde (~) konumu (0x2E) HER ÜÇ
     * değiştiricide de ÖLÜ TUŞ — tabloda hiç yok. Buna karşın ¨ konumu
     * (0x30) yalnız BASE hâlinde ölü tuştur; shift'i ! ve AltGr'ı ] gerçek
     * karakterdir — bu satır bilinçli olarak base'siz yazıldı.
     */
    private val CH_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(
                0x08 to KeyCap(0x08, 'e', 'E', altgr = '€'),
                0x1C to KeyCap(0x1C, 'z', 'Z'),
                0x1D to KeyCap(0x1D, 'y', 'Y')
            )
        ) + KeyTable.ORTAK + listOf(
            KeyCap(0x35, '§', '°'),
            KeyCap(0x1E, '1', '+', altgr = '¦'),
            KeyCap(0x1F, '2', '"', altgr = '@'),
            KeyCap(0x20, '3', '*', altgr = '#'),
            KeyCap(0x21, '4', 'ç'), // altgr: ° — 0x35'te zaten var, çakışma olmasın diye atlandı
            KeyCap(0x22, '5', '%'), // altgr: § — 0x35'te zaten var, çakışma olmasın diye atlandı
            KeyCap(0x23, '6', '&', altgr = '¬'),
            KeyCap(0x24, '7', '/', altgr = '|'),
            KeyCap(0x25, '8', '(', altgr = '¢'),
            KeyCap(0x26, '9', ')'),
            KeyCap(0x27, '0', '='),
            KeyCap(0x2D, '\'', '?'), // altgr: ´ ÖLÜ TUŞ — atlanıyor
            // 0x2E (üst sıranın son tuşu): ^ / ` / ~ ÜÇÜ DE ÖLÜ TUŞ — atlanıyor
            KeyCap(0x2F, 'ü', 'è', altgr = '['),
            KeyCap(0x30, shift = '!', altgr = ']'), // base: ¨ ÖLÜ TUŞ
            KeyCap(0x32, '$', '£'),
            KeyCap(0x33, 'ö', 'é', altgr = '{'),
            KeyCap(0x34, 'ä', 'à', altgr = '}'),
            KeyCap(0x36, ',', ';'),
            KeyCap(0x37, '.', ':'),
            KeyCap(0x38, '-', '_'),
            KeyCap(0x64, '<', '>', altgr = '\\')
        )
    )

    private val CH: Map<Char, KeyStroke> = CH_TABLE.toMap()

    /**
     * Hollandaca (Felemenkçe, ISO) — konum tablosu.
     *
     * Kaynak: Microsoft'un resmî klavye düzeni belgesi
     * (learn.microsoft.com/globalization/keyboards/kbdne.html), ham Unicode
     * ad etiketleriyle, birebir okuma.
     *
     * Harf sırası tam QWERTY (İsviçre/Almanca'nın aksine Y/Z YER
     * DEĞİŞTİRMEZ). Şifre karakterleri açısından en kritik fark yine
     * rakam satırının SHIFT hâli: ! " # $ % & _ ( ) ' — 7 üstünde `&`
     * değil `_` var, bu düzene özgü bir tuzak.
     *
     * ¨ (0x2F: base ¨, shift ^) ve ´ (0x34: base ´, shift `) konumlarının
     * HER İKİ değiştiricisi de ÖLÜ TUŞTUR — bu iki konum tabloda hiç yok.
     * Buna karşın ° konumu (0x2E) yalnız BASE'de gerçek karakter, shift'i
     * (~) ve AltGr'ı (¸) ikisi de ölü tuş — yalnız base yazıldı.
     */
    private val NL_TABLE = KeyTable(
        KeyTable.latinHarfler(
            istisnalar = mapOf(
                0x06 to KeyCap(0x06, 'c', 'C', altgr = '¢'),
                0x08 to KeyCap(0x08, 'e', 'E', altgr = '€'),
                0x10 to KeyCap(0x10, 'm', 'M', altgr = 'µ'),
                0x15 to KeyCap(0x15, 'r', 'R', altgr = '¶'),
                0x16 to KeyCap(0x16, 's', 'S', altgr = 'ß'),
                0x1B to KeyCap(0x1B, 'x', 'X', altgr = '»'),
                0x1D to KeyCap(0x1D, 'z', 'Z', altgr = '«')
            )
        ) + KeyTable.ORTAK + listOf(
            KeyCap(0x35, '@', '§'),
            KeyCap(0x1E, '1', '!', altgr = '¹'),
            KeyCap(0x1F, '2', '"', altgr = '²'),
            KeyCap(0x20, '3', '#', altgr = '³'),
            KeyCap(0x21, '4', '$', altgr = '¼'),
            KeyCap(0x22, '5', '%', altgr = '½'),
            KeyCap(0x23, '6', '&', altgr = '¾'),
            KeyCap(0x24, '7', '_', altgr = '£'),
            KeyCap(0x25, '8', '(', altgr = '{'),
            KeyCap(0x26, '9', ')', altgr = '}'),
            KeyCap(0x27, '0', '\''),
            KeyCap(0x2D, '/', '?', altgr = '\\'),
            KeyCap(0x2E, '°'), // shift: ~ ölü, altgr: ¸ ölü — yalnız base var
            // 0x2F (P'den sonra): base ¨ / shift ^ İKİSİ DE ÖLÜ TUŞ — atlanıyor
            KeyCap(0x30, '*', '|'),
            KeyCap(0x32, '<', '>'),
            KeyCap(0x33, '+', '±'),
            // 0x34 (+'dan sonra): base ´ / shift ` İKİSİ DE ÖLÜ TUŞ — atlanıyor
            KeyCap(0x36, ',', ';'),
            KeyCap(0x37, '.', ':', altgr = '·'),
            KeyCap(0x38, '-', '='),
            KeyCap(0x64, ']', '[', altgr = '¦')
        )
    )

    private val NL: Map<Char, KeyStroke> = NL_TABLE.toMap()

    /** Tablolar: tutarlılık testleri buradan geçer. */
    internal val TABLES: Map<Layout, KeyTable> = mapOf(
        Layout.US to US_TABLE, Layout.UK to UK_TABLE, Layout.TR_Q to TR_Q_TABLE,
        Layout.DE to DE_TABLE, Layout.FR to FR_TABLE, Layout.ES to ES_TABLE,
        Layout.IT to IT_TABLE, Layout.TR_F to TR_F_TABLE, Layout.PL to PL_TABLE, Layout.PT to PT_TABLE, Layout.SE to SE_TABLE, Layout.BR to BR_TABLE,
        Layout.CH to CH_TABLE, Layout.NL to NL_TABLE
    )
}
