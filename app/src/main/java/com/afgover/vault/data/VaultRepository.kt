package com.afgover.vault.data

import android.content.Context
import com.afgover.vault.core.Crypto
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.crypto.SecretKey

class VaultRepository(context: Context) {

    private val dao = VaultDatabase.get(context).entryDao()

    fun observeAll(): Flow<List<EntryEntity>> = dao.observeAll()

    suspend fun getAll(): List<EntryEntity> = dao.getAll()

    fun decrypt(entity: EntryEntity, key: SecretKey): DecryptedEntry? {
        val plain = Crypto.decrypt(key, entity.blob) ?: return null
        val data = try {
            EntryData.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
        } catch (e: Exception) {
            return null
        }
        return DecryptedEntry(
            id = entity.id,
            type = runCatching { EntryType.valueOf(entity.type) }.getOrDefault(EntryType.NOTE),
            title = entity.title,
            data = data,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    suspend fun getDecrypted(id: Long, key: SecretKey): DecryptedEntry? =
        dao.getById(id)?.let { decrypt(it, key) }

    suspend fun getAllDecrypted(key: SecretKey): List<DecryptedEntry> =
        dao.getAll().mapNotNull { decrypt(it, key) }

    suspend fun save(
        id: Long,
        type: EntryType,
        title: String,
        data: EntryData,
        key: SecretKey
    ) {
        val blob = Crypto.encrypt(key, data.toJson().toString().toByteArray(Charsets.UTF_8))
        val now = System.currentTimeMillis()
        if (id == 0L) {
            dao.insert(
                EntryEntity(type = type.name, title = title, blob = blob, createdAt = now, updatedAt = now)
            )
        } else {
            val existing = dao.getById(id) ?: return
            dao.update(
                existing.copy(type = type.name, title = title, blob = blob, updatedAt = now)
            )
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** İçe aktarma: mevcut kayıtları silip yenilerini yazar. */
    suspend fun replaceAll(entries: List<DecryptedEntry>, key: SecretKey) {
        dao.deleteAll()
        dao.insertAll(entries.map { it.toEntity(key) })
    }

    /** İçe aktarma: yedektekileri mevcut kayıtların yanına ekler. */
    suspend fun addAll(entries: List<DecryptedEntry>, key: SecretKey) {
        dao.insertAll(entries.map { it.toEntity(key) })
    }

    private fun DecryptedEntry.toEntity(key: SecretKey): EntryEntity =
        EntryEntity(
            type = type.name,
            title = title,
            blob = Crypto.encrypt(key, data.toJson().toString().toByteArray(Charsets.UTF_8)),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}
