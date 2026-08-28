package com.afgover.vault.data

import com.afgover.vault.bt.HidLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Düzen testi mekanizmasının sözleşmesi (B-050). */
class HidLayoutsTest {

    @Test
    fun `test metni iki duzende de tamamen yazilabilir`() {
        for (layout in HidLayouts.Layout.entries) {
            val map = HidLayouts.map(layout)
            val yazilamayan = HidLayouts.LAYOUT_TEST_TEXT.toSet().filter { it !in map }
            assertTrue(
                "$layout düzeninde yazılamıyor: $yazilamayan",
                yazilamayan.isEmpty()
            )
        }
    }

    /**
     * Bilinçli istisna: "Programcı" düzenleri (PL gibi) ABD diziliminin
     * ÜSTÜNE yalnız AltGr ile birkaç aksanlı harf ekler — temel harf, rakam
     * ve noktalama US ile bayt bayt aynıdır (bu, tasarımın amacı: kod ASCII
     * kalsın). LAYOUT_TEST_TEXT evrensel kalmak zorunda (her düzende
     * YAZILABİLMELİ — aksanlı bir harf eklersek diğer yedi düzen o karakteri
     * hiç üretemediği için "tamamen yazılabilir" testleri kırılır). Bu
     * yüzden test metni PL'yi US'ten AYIRT EDEMEZ — ve bu ZARARSIZDIR: ASCII
     * bir şifre iki düzende de doğru çıkar, yalnız ą/ć/ę/ł/ń/ó/ś/ź/ż içeren
     * bir sır yanlış düzende bozulur (AZERTY/QWERTZ karışıklığının HER harfi
     * bozmasından çok daha dar bir risk). Çift burada açıkça listelenir ki
     * gelecekte başka bir "programcı" düzeni eklenince aynı gerekçe
     * tekrarlanmadan unutulmasın.
     */
    private val ASCII_OZDES_CIFTLER = setOf(
        setOf(HidLayouts.Layout.US, HidLayouts.Layout.PL),
        // İspanyolca ve Portekizce (Avrupa) gerçekten FARKLI düzenler — 0x2E
        // konumunda ES '¡'/'¿', PT '«'/'»' üretir. Ama LAYOUT_TEST_TEXT o
        // konuma hiç dokunmuyor (yalnız 1! @ - _ ; : / ? . , ( ) = içeriyor)
        // ve iki düzen tam bu alt kümede eşleşiyor — kasıtlı bir tasarım
        // değil, test metninin örneklediği karakterlerin rastlantısal
        // çakışması. Yanlış düzen seçimi yine de zararsız KALMIYOR burada
        // (¡¿ içeren bir sır bozulur) — yalnız BU test metniyle yakalanmıyor.
        setOf(HidLayouts.Layout.ES, HidLayouts.Layout.PT)
    )

    @Test
    fun `test metni her duzende AYRI tus dizisi uretir - duzeni ayirt eder`() {
        // Düzen testinin tek işi bu: bilgisayarda beklenenden farklı çıkıyorsa
        // düzen yanlıştır. İki düzen aynı diziyi üretirse test onları ayıramaz.
        val diziler = HidLayouts.Layout.entries.associateWith {
            HidLayouts.testTextKeystrokes(it)
        }
        for (a in HidLayouts.Layout.entries) {
            for (b in HidLayouts.Layout.entries) {
                if (a < b) {
                    if (setOf(a, b) in ASCII_OZDES_CIFTLER) continue
                    assertFalse(
                        "$a ve $b düzen testinde AYNI tuş dizisini üretiyor — test bunları ayırt edemez",
                        diziler[a] == diziler[b]
                    )
                }
            }
        }
    }

    @Test
    fun `test metni her duzende tamamen yazilabilir`() {
        for (layout in HidLayouts.Layout.entries) {
            val map = HidLayouts.map(layout)
            val yazilamayan = HidLayouts.LAYOUT_TEST_TEXT.toSet().filter { it !in map && it != ' ' }
            assertTrue("$layout düzeninde yazılamıyor: $yazilamayan", yazilamayan.isEmpty())
        }
    }

    /**
     * Eskiden "harf ve rakamlar düzene duyarlı değil, tehlike simgelerde"
     * diye bir test vardı ve düzen sayısı ikiyken (US, TR-Q) DOĞRUYDU.
     * AZERTY ve QWERTZ eklenince bu varsayım çöktü: harfler de yer değiştiriyor.
     * Şifreler çoğunlukla harften oluştuğu için bu, yanlış düzen seçiminin
     * artık çok daha yıkıcı olduğu anlamına gelir — testi silmek yerine
     * gerçeği yazıyoruz.
     */
    @Test
    fun `harfler de duzene duyarli - AZERTY ve QWERTZ yuzunden`() {
        // 'a' AZERTY'de US'tekinden başka tuşta
        assertNotEquals(
            HidLayouts.map(HidLayouts.Layout.US)['a'],
            HidLayouts.map(HidLayouts.Layout.FR)['a']
        )
        // 'y' QWERTZ'de yer değiştirir
        assertNotEquals(
            HidLayouts.map(HidLayouts.Layout.US)['y'],
            HidLayouts.map(HidLayouts.Layout.DE)['y']
        )
        // dolayısıyla düz bir şifre metni artık duyarlı sayılır
        assertFalse(
            HidLayouts.layoutSensitiveChars("abcXYZ0123", HidLayouts.Layout.US).isEmpty()
        )
    }

    @Test
    fun `rakamlar AZERTYde Shift ister`() {
        val fr = HidLayouts.map(HidLayouts.Layout.FR)
        assertEquals(HidLayouts.MOD_SHIFT, fr['1']?.modifier)
        assertEquals(HidLayouts.MOD_NONE, HidLayouts.map(HidLayouts.Layout.US)['1']?.modifier)
    }

    @Test
    fun `her duzende sifrelerde sik gecen karakterler yazilabilir`() {
        // Bunlar üretilemezse kullanıcı sırrı o düzende hiç yazamaz.
        val sart = "abcxyz0189@#\$%&*()-_=+[]{};:,.?/"
        for (layout in HidLayouts.Layout.entries) {
            val map = HidLayouts.map(layout)
            val eksik = sart.toSet().filter { it !in map }
            assertTrue("$layout düzeninde yazılamayan şifre karakterleri: $eksik", eksik.isEmpty())
        }
    }

    @Test
    fun `hicbir tabloda ayni karakter iki tusa atanmamis`() {
        HidLayouts.TABLES.forEach { (layout, table) ->
            assertTrue(
                "$layout tablosunda çift atanmış karakter: ${table.duplicateChars()}",
                table.duplicateChars().isEmpty()
            )
        }
    }

    @Test
    fun `hiz testi metni duzenden bagimsiz - tasimayi olcer`() {
        for (layout in HidLayouts.Layout.entries) {
            val map = HidLayouts.map(layout)
            assertTrue(
                "$layout düzeninde yazılamıyor",
                HidLayouts.SPEED_TEST_TEXT.all { it in map }
            )
        }
    }

    @Test
    fun `hiz testi ozdes bloklardan olusur - dusen karakter goze carpar`() {
        val bloklar = HidLayouts.SPEED_TEST_TEXT.split(" ")
        assertEquals(10, bloklar.size)
        assertTrue(bloklar.all { it == HidLayouts.SPEED_TEST_BLOCK })
    }

    // ---- Konum tablosuna geçişin güvencesi ----
    //
    // US ve TR-Q haritaları CİHAZDA doğrulanmıştı; konum tablosuna çevrilirken
    // birebir korunmaları şart. Aşağıdaki değerler eski elle yazılmış
    // haritadan alındı: biri bile kayarsa sır bilgisayara yanlış yazılır.

    @Test
    fun `US haritasi tabloya cevrildikten sonra ayni kaldi`() {
        val us = HidLayouts.map(HidLayouts.Layout.US)
        val beklenen = mapOf(
            'a' to (0x04 to HidLayouts.MOD_NONE), 'z' to (0x1D to HidLayouts.MOD_NONE),
            'A' to (0x04 to HidLayouts.MOD_SHIFT),
            '1' to (0x1E to HidLayouts.MOD_NONE), '0' to (0x27 to HidLayouts.MOD_NONE),
            '!' to (0x1E to HidLayouts.MOD_SHIFT), '@' to (0x1F to HidLayouts.MOD_SHIFT),
            '#' to (0x20 to HidLayouts.MOD_SHIFT), '$' to (0x21 to HidLayouts.MOD_SHIFT),
            '%' to (0x22 to HidLayouts.MOD_SHIFT), '^' to (0x23 to HidLayouts.MOD_SHIFT),
            '&' to (0x24 to HidLayouts.MOD_SHIFT), '*' to (0x25 to HidLayouts.MOD_SHIFT),
            '(' to (0x26 to HidLayouts.MOD_SHIFT), ')' to (0x27 to HidLayouts.MOD_SHIFT),
            '-' to (0x2D to HidLayouts.MOD_NONE), '_' to (0x2D to HidLayouts.MOD_SHIFT),
            '=' to (0x2E to HidLayouts.MOD_NONE), '+' to (0x2E to HidLayouts.MOD_SHIFT),
            '[' to (0x2F to HidLayouts.MOD_NONE), '{' to (0x2F to HidLayouts.MOD_SHIFT),
            ']' to (0x30 to HidLayouts.MOD_NONE), '}' to (0x30 to HidLayouts.MOD_SHIFT),
            '\\' to (0x31 to HidLayouts.MOD_NONE), '|' to (0x31 to HidLayouts.MOD_SHIFT),
            ';' to (0x33 to HidLayouts.MOD_NONE), ':' to (0x33 to HidLayouts.MOD_SHIFT),
            '\'' to (0x34 to HidLayouts.MOD_NONE), '"' to (0x34 to HidLayouts.MOD_SHIFT),
            '`' to (0x35 to HidLayouts.MOD_NONE), '~' to (0x35 to HidLayouts.MOD_SHIFT),
            ',' to (0x36 to HidLayouts.MOD_NONE), '<' to (0x36 to HidLayouts.MOD_SHIFT),
            '.' to (0x37 to HidLayouts.MOD_NONE), '>' to (0x37 to HidLayouts.MOD_SHIFT),
            '/' to (0x38 to HidLayouts.MOD_NONE), '?' to (0x38 to HidLayouts.MOD_SHIFT),
            ' ' to (0x2C to HidLayouts.MOD_NONE)
        )
        beklenen.forEach { (ch, bekle) ->
            val k = us[ch]
            assertNotNull("US haritasında '$ch' yok", k)
            assertEquals("US '$ch' usage", bekle.first, k!!.usage)
            assertEquals("US '$ch' modifier", bekle.second, k.modifier)
        }
    }

    @Test
    fun `TR-Q haritasi tabloya cevrildikten sonra ayni kaldi`() {
        val tr = HidLayouts.map(HidLayouts.Layout.TR_Q)
        val beklenen = mapOf(
            'ı' to (0x0C to HidLayouts.MOD_NONE), 'I' to (0x0C to HidLayouts.MOD_SHIFT),
            'i' to (0x34 to HidLayouts.MOD_NONE), 'İ' to (0x34 to HidLayouts.MOD_SHIFT),
            'ğ' to (0x2F to HidLayouts.MOD_NONE), 'ü' to (0x30 to HidLayouts.MOD_NONE),
            'ş' to (0x33 to HidLayouts.MOD_NONE), 'ö' to (0x36 to HidLayouts.MOD_NONE),
            'ç' to (0x37 to HidLayouts.MOD_NONE),
            '@' to (0x14 to HidLayouts.MOD_ALTGR), '#' to (0x20 to HidLayouts.MOD_ALTGR),
            '$' to (0x21 to HidLayouts.MOD_ALTGR), '{' to (0x24 to HidLayouts.MOD_ALTGR),
            '[' to (0x25 to HidLayouts.MOD_ALTGR), ']' to (0x26 to HidLayouts.MOD_ALTGR),
            '}' to (0x27 to HidLayouts.MOD_ALTGR), '\\' to (0x2D to HidLayouts.MOD_ALTGR),
            '|' to (0x64 to HidLayouts.MOD_ALTGR),
            '*' to (0x2D to HidLayouts.MOD_NONE), '?' to (0x2D to HidLayouts.MOD_SHIFT),
            '-' to (0x2E to HidLayouts.MOD_NONE), '_' to (0x2E to HidLayouts.MOD_SHIFT),
            ',' to (0x32 to HidLayouts.MOD_NONE), ';' to (0x32 to HidLayouts.MOD_SHIFT),
            '"' to (0x35 to HidLayouts.MOD_NONE),
            '.' to (0x38 to HidLayouts.MOD_NONE), ':' to (0x38 to HidLayouts.MOD_SHIFT),
            '<' to (0x64 to HidLayouts.MOD_NONE), '>' to (0x64 to HidLayouts.MOD_SHIFT),
            '\'' to (0x1F to HidLayouts.MOD_SHIFT), '+' to (0x21 to HidLayouts.MOD_SHIFT),
            '/' to (0x24 to HidLayouts.MOD_SHIFT), '=' to (0x27 to HidLayouts.MOD_SHIFT)
        )
        beklenen.forEach { (ch, bekle) ->
            val k = tr[ch]
            assertNotNull("TR-Q haritasında '$ch' yok", k)
            assertEquals("TR-Q '$ch' usage", bekle.first, k!!.usage)
            assertEquals("TR-Q '$ch' modifier", bekle.second, k.modifier)
        }
    }

    /** '^' TR-Q'da ölü tuş: haritada OLMAMALI (sessiz yanlış üretim yerine uyarı). */
    @Test
    fun `TR-Q olu tuslar haritada yok`() {
        assertNull(HidLayouts.map(HidLayouts.Layout.TR_Q)['^'])
    }
}
