package com.afgover.vault.bt

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

    enum class Layout(val label: String) { US("US (İngilizce)"), TR("Türkçe Q") }

    fun map(layout: Layout): Map<Char, KeyStroke> =
        when (layout) {
            Layout.US -> US
            Layout.TR -> TR
        }

    private fun MutableMap<Char, KeyStroke>.putLetters(overrides: Map<Char, KeyStroke> = emptyMap()) {
        for (c in 'a'..'z') {
            val usage = 0x04 + (c - 'a')
            put(c, KeyStroke(usage, MOD_NONE))
            put(c.uppercaseChar(), KeyStroke(usage, MOD_SHIFT))
        }
        putAll(overrides)
    }

    private fun MutableMap<Char, KeyStroke>.putDigitsAndCommon() {
        for (c in '1'..'9') put(c, KeyStroke(0x1E + (c - '1'), MOD_NONE))
        put('0', KeyStroke(0x27, MOD_NONE))
        put(' ', KeyStroke(0x2C, MOD_NONE))
        put('\n', KeyStroke(0x28, MOD_NONE))
        put('\t', KeyStroke(0x2B, MOD_NONE))
    }

    private val US: Map<Char, KeyStroke> = buildMap {
        putLetters()
        putDigitsAndCommon()
        // Shift + rakam satırı
        "!@#\$%^&*()".forEachIndexed { i, c ->
            put(c, KeyStroke(if (i == 9) 0x27 else 0x1E + i, MOD_SHIFT))
        }
        put('-', KeyStroke(0x2D, MOD_NONE)); put('_', KeyStroke(0x2D, MOD_SHIFT))
        put('=', KeyStroke(0x2E, MOD_NONE)); put('+', KeyStroke(0x2E, MOD_SHIFT))
        put('[', KeyStroke(0x2F, MOD_NONE)); put('{', KeyStroke(0x2F, MOD_SHIFT))
        put(']', KeyStroke(0x30, MOD_NONE)); put('}', KeyStroke(0x30, MOD_SHIFT))
        put('\\', KeyStroke(0x31, MOD_NONE)); put('|', KeyStroke(0x31, MOD_SHIFT))
        put(';', KeyStroke(0x33, MOD_NONE)); put(':', KeyStroke(0x33, MOD_SHIFT))
        put('\'', KeyStroke(0x34, MOD_NONE)); put('"', KeyStroke(0x34, MOD_SHIFT))
        put('`', KeyStroke(0x35, MOD_NONE)); put('~', KeyStroke(0x35, MOD_SHIFT))
        put(',', KeyStroke(0x36, MOD_NONE)); put('<', KeyStroke(0x36, MOD_SHIFT))
        put('.', KeyStroke(0x37, MOD_NONE)); put('>', KeyStroke(0x37, MOD_SHIFT))
        put('/', KeyStroke(0x38, MOD_NONE)); put('?', KeyStroke(0x38, MOD_SHIFT))
    }

    /**
     * Türkçe Q düzeni. Emin olunmayan birkaç özel karakter bilinçli olarak
     * haritada yok; yazılamayan karakterler kullanıcıya bildirilir
     * (gerekirse US moduna geçilir).
     */
    private val TR: Map<Char, KeyStroke> = buildMap {
        putLetters(
            overrides = mapOf(
                // TR-Q'da fiziksel I tuşu 'ı', home-row'daki ek tuş 'i' üretir
                'ı' to KeyStroke(0x0C, MOD_NONE), 'I' to KeyStroke(0x0C, MOD_SHIFT),
                'i' to KeyStroke(0x34, MOD_NONE), 'İ' to KeyStroke(0x34, MOD_SHIFT)
            )
        )
        putDigitsAndCommon()
        put('ğ', KeyStroke(0x2F, MOD_NONE)); put('Ğ', KeyStroke(0x2F, MOD_SHIFT))
        put('ü', KeyStroke(0x30, MOD_NONE)); put('Ü', KeyStroke(0x30, MOD_SHIFT))
        put('ş', KeyStroke(0x33, MOD_NONE)); put('Ş', KeyStroke(0x33, MOD_SHIFT))
        put('ö', KeyStroke(0x36, MOD_NONE)); put('Ö', KeyStroke(0x36, MOD_SHIFT))
        put('ç', KeyStroke(0x37, MOD_NONE)); put('Ç', KeyStroke(0x37, MOD_SHIFT))
        // Shift + rakam satırı: ! ' ^ + % & / ( ) =
        "!'^+%&/()=".forEachIndexed { i, c ->
            put(c, KeyStroke(if (i == 9) 0x27 else 0x1E + i, MOD_SHIFT))
        }
        put('*', KeyStroke(0x2D, MOD_NONE)); put('?', KeyStroke(0x2D, MOD_SHIFT))
        put('\\', KeyStroke(0x2D, MOD_ALTGR))
        put('-', KeyStroke(0x2E, MOD_NONE)); put('_', KeyStroke(0x2E, MOD_SHIFT))
        put('.', KeyStroke(0x38, MOD_NONE)); put(':', KeyStroke(0x38, MOD_SHIFT))
        // AltGr karakterleri
        put('@', KeyStroke(0x14, MOD_ALTGR))       // AltGr+Q
        put('#', KeyStroke(0x20, MOD_ALTGR))       // AltGr+3
        put('$', KeyStroke(0x21, MOD_ALTGR))       // AltGr+4
        put('{', KeyStroke(0x24, MOD_ALTGR))       // AltGr+7
        put('[', KeyStroke(0x25, MOD_ALTGR))       // AltGr+8
        put(']', KeyStroke(0x26, MOD_ALTGR))       // AltGr+9
        put('}', KeyStroke(0x27, MOD_ALTGR))       // AltGr+0
        put('<', KeyStroke(0x64, MOD_NONE)); put('>', KeyStroke(0x64, MOD_SHIFT))
        put('|', KeyStroke(0x64, MOD_ALTGR))
    }
}
