package com.afgover.vault.core

import java.security.MessageDigest

/**
 * Uzun değerlerin (özel anahtar, `.pem`, sertifika) doğru aktarıldığını
 * gözle karşılaştırmak için SHA-256 parmak izi.
 *
 * Karşılaştırma bilgisayarla yapılır; hangi komutun aynı sonucu vereceği
 * değerin nasıl saklandığına bağlıdır ve bu ayrım gerçek bir tuzaktır:
 * `vault-clip --dosya` dosyayı BAYT BAYT aktarır (son satır sonu dahil),
 * dolayısıyla `shasum -a 256 dosya` birebir tutar. Elle kopyala-yapıştırda
 * son satır sonu genellikle kaybolur; o zaman eşleşen komut
 * `printf '%s' "$(cat dosya)" | shasum -a 256` olur.
 */
object Fingerprint {

    /** Değerin UTF-8 baytlarının SHA-256'sı, küçük harf hex. */
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Gözle karşılaştırmak için dörtlü gruplanmış tam hex. */
    fun grouped(hex: String): String = hex.chunked(4).joinToString(" ")

    /** Listede/satırda gösterilecek kısa biçim: ilk 8 + son 4 hex. */
    fun short(hex: String): String =
        if (hex.length <= 16) grouped(hex)
        else "${hex.take(8).chunked(4).joinToString(" ")} … ${hex.takeLast(4)}"
}
