package com.afgover.vault.core

import java.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * QR ile doğrudan aktarımın kare biçimi (vault_takip B-035/B-036).
 *
 * Zarf ekrandan kameraya, **ağ olmadan** geçer: ne relay, ne tarayıcı, ne
 * pano. Büyük zarflar tek kareye sığmadığı için içerik parçalanır ve kareler
 * ekranda döngüyle oynar; okuyucu sırasız toplar.
 *
 * ```
 * VLT1|i/n|F|veri
 *   i,n : 1'den başlayan kare sırası ve toplam
 *   F   : Z = deflate(raw) + base64 · P = base64
 *   veri: kodlanmış yükün bu kareye düşen parçası
 * ```
 *
 * Biçim bilinçli olarak kendini tarif eder: eksik kare varken birleştirme
 * yapılmaz, kareler arasında toplam sayı çelişirse küme reddedilir. Zarfın
 * kendisi zaten AES-GCM ile korunuyor; buradaki denetimler gizlilik için
 * değil, **sessizce yanlış veri üretmemek** için.
 */
object QrTransfer {

    private const val PREFIX = "VLT1"

    /** Kare başına yaklaşık base64 uzunluğu — telefon kamerasının rahat okuduğu yoğunluk. */
    const val CHUNK = 900

    data class Frame(val index: Int, val total: Int, val flag: Char, val data: String)

    /** Bir QR metnini kareye çevirir; bizim biçimimiz değilse null. */
    fun parse(text: String): Frame? {
        val t = text.trim()
        if (!t.startsWith("$PREFIX|")) return null
        val parts = t.split("|", limit = 4)
        if (parts.size != 4) return null
        val sira = parts[1].split("/")
        if (sira.size != 2) return null
        val i = sira[0].toIntOrNull() ?: return null
        val n = sira[1].toIntOrNull() ?: return null
        if (i < 1 || n < 1 || i > n) return null
        val flag = parts[2].firstOrNull() ?: return null
        if (flag != 'Z' && flag != 'P') return null
        return Frame(i, n, flag, parts[3])
    }

    /**
     * Toplanan karelerden zarfı kurar. Küme tamamlanmadıysa ya da kareler
     * birbiriyle çelişiyorsa null döner — yarım veri asla çözülmeye
     * gönderilmez.
     */
    fun assemble(frames: Collection<Frame>): String? {
        if (frames.isEmpty()) return null
        val total = frames.first().total
        val flag = frames.first().flag
        if (frames.any { it.total != total || it.flag != flag }) return null
        val byIndex = frames.associateBy { it.index }
        if (byIndex.size != total) return null
        val encoded = buildString {
            for (i in 1..total) append(byIndex[i]?.data ?: return null)
        }
        val bytes = try {
            Base64.getMimeDecoder().decode(encoded)
        } catch (e: Exception) {
            return null
        }
        return when (flag) {
            'P' -> String(bytes, Charsets.UTF_8)
            'Z' -> inflate(bytes)
            else -> null
        }
    }

    /** Ham deflate (zlib başlığı yok) — Python `zlib` ve tarayıcı `deflate-raw` ile aynı. */
    private fun inflate(data: ByteArray): String? = try {
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(8 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        val text = out.toString(Charsets.UTF_8.name())
        text.ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    /** Tarama ekranının "3/4 alındı" göstergesi için eksik kare listesi. */
    fun missing(frames: Collection<Frame>): List<Int> {
        val total = frames.firstOrNull()?.total ?: return emptyList()
        val var_ = frames.map { it.index }.toSet()
        return (1..total).filter { it !in var_ }
    }
}
