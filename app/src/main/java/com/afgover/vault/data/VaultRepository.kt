package com.afgover.vault.data

import android.content.Context
import com.afgover.vault.core.Crypto
import com.afgover.vault.core.KeyManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.crypto.SecretKey

/**
 * Kayıtların şifreli saklanması.
 *
 * Her kaydın aslı [EntryEntity.blob] içinde, ana paroladan türeyen dataKey ile
 * şifrelidir. "Hızlı erişim" işaretli kayıtların bir de [EntryEntity.quickBlob]
 * kopyası vardır; o kopya Keystore'daki kimlik doğrulama istemeyen anahtarla
 * şifrelenir ve klavyenin kasa kilitliyken okuyabildiği tek şeydir.
 */
class VaultRepository(context: Context, private val keys: KeyManager) {

    companion object {
        /**
         * Tek kaydın şifrelenmemiş içeriği için üst sınır. Gerçek tavan
         * Android'in CursorWindow'u (varsayılan 2 MB): satıra sığmayan kayıt
         * yazılırken değil, SONRAKİ liste sorgusunda "Row too big" ile çöker
         * ve kayıt erişilmez olur. Hızlı erişim kopyası (quickBlob) satırı
         * ikiye katladığı için sınır tavanın dörtte birinin de altında
         * tutuluyor. (vault_takip B-040, A-2026-08-21-004)
         */
        const val MAX_PLAIN_BYTES = 256 * 1024
    }

    /** Kayıt [MAX_PLAIN_BYTES] sınırını aşarsa fırlatılır; mesajı ekrana çıkar. */
    class EntryTooLargeException(title: String, bytes: Int) : Exception(
        "\"$title\" kaydı çok büyük: ${bytes / 1024} KB " +
            "(sınır ${MAX_PLAIN_BYTES / 1024} KB). Uzun içeriği bölerek ya da " +
            "dosya olarak bilgisayarda saklayarak ekle."
    )

    private fun checkSize(title: String, plain: ByteArray) {
        if (plain.size > MAX_PLAIN_BYTES) throw EntryTooLargeException(title, plain.size)
    }

    private val dao = VaultDatabase.get(context).entryDao()
    private val tagDao = VaultDatabase.get(context).tagDao()

    fun observeAll(): Flow<List<EntryEntity>> = dao.observeAll()

    suspend fun getAll(): List<EntryEntity> = dao.getAll()

    fun decrypt(entity: EntryEntity, key: SecretKey): DecryptedEntry? =
        entity.toDecrypted(Crypto.decrypt(key, entity.blob))

    suspend fun getDecrypted(id: Long, key: SecretKey): DecryptedEntry? =
        dao.getById(id)?.let { decrypt(it, key) }

    suspend fun getAllDecrypted(key: SecretKey): List<DecryptedEntry> =
        dao.getAll().mapNotNull { decrypt(it, key) }

    /**
     * Kasa kilitliyken görülebilen tek küme: hızlı erişim işaretli kayıtlar.
     * Keystore anahtarı yoksa ya da kopya çözülemiyorsa kayıt atlanır —
     * kilitli kasadan asla ana blob okunmaz.
     */
    suspend fun getQuickDecrypted(): List<DecryptedEntry> {
        val quickKey = keys.quickKey() ?: return emptyList()
        return dao.getQuick().mapNotNull { entity ->
            val blob = entity.quickBlob ?: return@mapNotNull null
            entity.toDecrypted(Crypto.decrypt(quickKey, blob))
        }
    }

    suspend fun save(
        id: Long,
        type: EntryType,
        title: String,
        data: EntryData,
        quick: Boolean,
        key: SecretKey,
        tagIds: List<Long> = emptyList()
    ) {
        val plain = data.bytes()
        checkSize(title, plain)
        val blob = Crypto.encrypt(key, plain)
        val quickBlob = quickCopy(plain, quick)
        val now = System.currentTimeMillis()
        if (id == 0L) {
            dao.insert(
                EntryEntity(
                    type = type.name,
                    title = title,
                    blob = blob,
                    createdAt = now,
                    updatedAt = now,
                    quick = quick,
                    quickBlob = quickBlob,
                    tags = TagIds.serialize(tagIds)
                )
            )
        } else {
            val existing = dao.getById(id) ?: return
            dao.update(
                existing.copy(
                    type = type.name,
                    title = title,
                    blob = blob,
                    updatedAt = now,
                    quick = quick,
                    quickBlob = quickBlob,
                    tags = TagIds.serialize(tagIds)
                )
            )
        }
    }

    /** Detay ekranından hızlı erişimi açıp kapatmak için; içerik değişmez. */
    suspend fun setQuick(id: Long, quick: Boolean, key: SecretKey) {
        val existing = dao.getById(id) ?: return
        val plain = Crypto.decrypt(key, existing.blob) ?: return
        dao.update(existing.copy(quick = quick, quickBlob = quickCopy(plain, quick)))
    }

    /**
     * Kilit açıldığında çağrılır: Keystore anahtarı değiştiyse ya da kopya hiç
     * üretilememişse hızlı erişim kopyalarını yeniden yazar. Kaydın aslı hep
     * dataKey ile şifreli olduğu için bu işlem veriyi kaynağından üretir.
     */
    suspend fun repairQuickCopies(key: SecretKey) {
        val quickKey = keys.quickKey() ?: return
        dao.getQuick().forEach { entity ->
            val usable = entity.quickBlob?.let { Crypto.decrypt(quickKey, it) } != null
            if (usable) return@forEach
            val plain = Crypto.decrypt(key, entity.blob) ?: return@forEach
            dao.update(entity.copy(quickBlob = Crypto.encryptWithGeneratedIv(quickKey, plain)))
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    // ---- Etiketler ----

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    suspend fun addTag(name: String, color: Int): Long =
        tagDao.insert(TagEntity(name = name.trim(), color = color))

    suspend fun updateTag(tag: TagEntity) = tagDao.update(tag)

    /** Etiketi siler ve taşıyan her kaydın listesinden düşürür. */
    suspend fun deleteTag(id: Long) {
        tagDao.deleteById(id)
        dao.getAll().forEach { entity ->
            val ids = TagIds.parse(entity.tags)
            if (id in ids) {
                dao.update(entity.copy(tags = TagIds.serialize(ids - id)))
            }
        }
    }

    /** İçe aktarma: mevcut kayıtları silip yenilerini yazar. */
    suspend fun replaceAll(entries: List<DecryptedEntry>, key: SecretKey, tagColors: Map<String, Int> = emptyMap()) {
        dao.deleteAll()
        val resolve = tagNameResolver(entries, tagColors)
        dao.insertAll(entries.map { it.toEntity(key, resolve(it)) })
    }

    /** İçe aktarma: yedektekileri mevcut kayıtların yanına ekler. */
    suspend fun addAll(entries: List<DecryptedEntry>, key: SecretKey, tagColors: Map<String, Int> = emptyMap()) {
        val resolve = tagNameResolver(entries, tagColors)
        dao.insertAll(entries.map { it.toEntity(key, resolve(it)) })
    }

    /**
     * Yedekteki etiket ADLARINI bu cihazın id'lerine çözer; olmayan etiket
     * oluşturulur (id'ler cihaza özgüdür, yedek ad taşır). Renk yedekteki
     * tanımdan ([BackupManager] tagDefs), yoksa paletten gelir.
     */
    private suspend fun tagNameResolver(
        entries: List<DecryptedEntry>,
        tagColors: Map<String, Int>
    ): (DecryptedEntry) -> List<Long> {
        val wanted = entries.flatMap { it.tagNames }.distinct()
        if (wanted.isEmpty()) return { it.tagIds }
        val byName = tagDao.getAll().associateBy { it.name.lowercase() }.toMutableMap()
        wanted.forEach { name ->
            if (name.lowercase() !in byName) {
                val color = tagColors[name] ?: TagPalette.colorFor(byName.size)
                val id = tagDao.insert(TagEntity(name = name, color = color))
                byName[name.lowercase()] = TagEntity(id, name, color)
            }
        }
        return { e -> e.tagNames.mapNotNull { byName[it.lowercase()]?.id } }
    }

    suspend fun getAllTags(): List<TagEntity> = tagDao.getAll()

    // ---- Yardımcılar ----

    private fun EntryData.bytes(): ByteArray =
        toJson().toString().toByteArray(Charsets.UTF_8)

    /** Keystore erişilemiyorsa null döner; kayıt o an kilitliyken görünmez olur. */
    private fun quickCopy(plain: ByteArray, quick: Boolean): ByteArray? {
        if (!quick) return null
        val quickKey = keys.quickKey() ?: return null
        return Crypto.encryptWithGeneratedIv(quickKey, plain)
    }

    private fun EntryEntity.toDecrypted(plain: ByteArray?): DecryptedEntry? {
        if (plain == null) return null
        val data = try {
            EntryData.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
        } catch (e: Exception) {
            return null
        }
        return DecryptedEntry(
            id = id,
            type = runCatching { EntryType.valueOf(type) }.getOrDefault(EntryType.NOTE),
            title = title,
            data = data,
            createdAt = createdAt,
            updatedAt = updatedAt,
            quick = quick,
            tagIds = TagIds.parse(tags)
        )
    }

    private fun DecryptedEntry.toEntity(key: SecretKey, resolvedTagIds: List<Long>): EntryEntity {
        val plain = data.bytes()
        checkSize(title, plain)
        return EntryEntity(
            type = type.name,
            title = title,
            blob = Crypto.encrypt(key, plain),
            createdAt = createdAt,
            updatedAt = updatedAt,
            quick = quick,
            quickBlob = quickCopy(plain, quick),
            tags = TagIds.serialize(resolvedTagIds)
        )
    }
}
