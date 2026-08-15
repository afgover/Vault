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
    val quickBlob: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean =
        other is EntryEntity && other.id == id && other.updatedAt == updatedAt

    override fun hashCode(): Int = (id * 31 + updatedAt).toInt()
}
