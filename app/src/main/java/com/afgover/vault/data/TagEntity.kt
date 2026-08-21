package com.afgover.vault.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Etiket tanımı. Ad ve renk veritabanında ŞİFRESİZ durur: filtreleme liste
 * ekranında çalışır ve başlık/tür ile aynı gerekçeye dayanır (SEC-013).
 * Bilinçli üstveri kabulü: SEC-021. Sır niteliğinde etiket adı kullanılmaz.
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB; palet [TagPalette] içinden seçilir. */
    val color: Int
)

/** Etiket renk paleti (ARGB). Seçim ekranı da içe aktarma da buradan alır. */
object TagPalette {
    val colors: List<Int> = listOf(
        0xFFE57373.toInt(), // kırmızı
        0xFFFFB74D.toInt(), // turuncu
        0xFFFFF176.toInt(), // sarı
        0xFF81C784.toInt(), // yeşil
        0xFF4FC3F7.toInt(), // mavi
        0xFF9575CD.toInt(), // mor
        0xFFF06292.toInt(), // pembe
        0xFF90A4AE.toInt()  // gri
    )

    fun colorFor(index: Int): Int = colors[index.mod(colors.size)]
}

/** Kayıtların `tags` sütunundaki JSON id listesi için ortak çözümleyici. */
object TagIds {
    fun parse(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            buildList { for (i in 0 until arr.length()) add(arr.getLong(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serialize(ids: List<Long>): String =
        org.json.JSONArray().apply { ids.distinct().forEach { put(it) } }.toString()
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<TagEntity>

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)
}
