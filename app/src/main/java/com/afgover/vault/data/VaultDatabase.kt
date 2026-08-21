package com.afgover.vault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EntryEntity::class, TagEntity::class], version = 3, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var instance: VaultDatabase? = null

        /**
         * Hızlı erişim alanları. Mevcut kayıtlar `quick = 0` ile gelir, yani
         * varsayılan olarak korumalıdır; içerikleri olduğu gibi kalır.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN quick INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN quickBlob BLOB DEFAULT NULL")
            }
        }

        /**
         * Etiketler. Mevcut kayıtlar boş etiket listesiyle ("[]") gelir;
         * içerikler olduğu gibi kalır (R-004: yıkıcı geçiş yok).
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): VaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault.db"
                )
                    // Yıkıcı geçiş YOK: şema değişince veriler silinmemeli.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
