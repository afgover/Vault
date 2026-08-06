package com.afgover.vault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}
