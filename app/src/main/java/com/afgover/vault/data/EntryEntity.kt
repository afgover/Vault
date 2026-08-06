package com.afgover.vault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntryType { LOGIN, CARD, NOTE }

/**
 * Veritabanında yalnızca başlık ve tür açık durur (liste/arama için);
 * tüm hassas alanlar [blob] içinde AES-256-GCM ile şifrelidir.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val blob: ByteArray,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean =
        other is EntryEntity && other.id == id && other.updatedAt == updatedAt

    override fun hashCode(): Int = (id * 31 + updatedAt).toInt()
}
