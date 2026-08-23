package com.afgover.vault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * EVERYDAY: gündelik kullanımda lazım olan bilgiler (ad soyad, telefon,
 * e-posta, adres). Bu türde açılan yeni kayıtlar varsayılan olarak klavyede
 * parolasız kullanılabilir işaretiyle gelir.
 */
enum class EntryType { LOGIN, EVERYDAY, CARD, NOTE }

/**
 * Veritabanında yalnızca başlık ve tür açık durur (liste/arama için);
 * tüm hassas alanlar [blob] içinde AES-256-GCM ile şifrelidir.
 *
 * [quick] işaretli kayıtlar klavyede kasa kilitliyken de kullanılabilir.
 * Bunun için aynı içeriğin ikinci bir kopyası [quickBlob] içinde, Keystore'daki
 * kimlik doğrulama istemeyen anahtarla şifrelenir. Aslı ([blob]) her zaman ana
 * paroladan türeyen dataKey ile şifreli kalır — Keystore kaybolsa bile veri
 * kaybı olmaz, kopyalar yeniden üretilir.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val blob: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
    val quick: Boolean = false,
    val quickBlob: ByteArray? = null,
    /** Etiket id'leri, JSON dizi ("[1,3]"). Adlar tags tablosunda (SEC-021). */
    val tags: String = "[]",
    /**
     * Güvenli not alt türü ([NoteKind]); yalnız NOTE kayıtlarında anlamlı.
     * Türle aynı gerekçeyle şifresiz: liste ikonunu çizmek için kilit
     * açmadan okunabilmeli (SEC-013/SEC-021 ailesi).
     */
    val noteKind: String = NoteKind.GENEL.name,
    /** Kullanıcı sırası ([EntrySort.MANUAL]); küçükten büyüğe. */
    val sortIndex: Int = 0
) {
    override fun equals(other: Any?): Boolean =
        other is EntryEntity && other.id == id && other.updatedAt == updatedAt

    override fun hashCode(): Int = (id * 31 + updatedAt).toInt()
}

/** Güvenli notun ne taşıdığı — ikon ve düzenleme ipucu bundan türer. */
enum class NoteKind(val label: String) {
    GENEL("Genel not"),
    BETIK("Betik / komut (.sh)"),
    ANAHTAR("Anahtar / sertifika (.pem)"),
    PARMAK_IZI("Parmak izi / çıpa"),
    KURTARMA("Kurtarma kodları"),
    YAPILANDIRMA("Yapılandırma (.env, json)");

    companion object {
        fun of(name: String?): NoteKind =
            runCatching { valueOf(name ?: "") }.getOrDefault(GENEL)
    }
}
