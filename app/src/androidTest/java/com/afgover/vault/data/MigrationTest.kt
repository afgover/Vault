package com.afgover.vault.data

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 2→3 göçünün, GERÇEK bir v2 veritabanının üstünde veri kaybetmeden
 * koştuğunu doğrular (B-024/P-008.5, R-004). Şema JSON'ları hiç dışa
 * aktarılmadığı için MigrationTestHelper kullanılamıyor; v2 şeması ham SQL
 * ile, üretimde 1→2 göçünün bıraktığı hâliyle kurulur.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"

    @Before
    fun temizle() {
        context.getDatabasePath(dbName).delete()
    }

    private fun v2VeritabaniKur(): ByteArray {
        val blob = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL(
            "CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, title TEXT NOT NULL, blob BLOB NOT NULL, " +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                "quick INTEGER NOT NULL DEFAULT 0, quickBlob BLOB DEFAULT NULL)"
        )
        db.execSQL(
            "INSERT INTO entries (type, title, blob, createdAt, updatedAt, quick) " +
                "VALUES ('LOGIN', 'Eski kayıt', x'0102030405060708', 100, 200, 1)"
        )
        db.version = 2
        db.close()
        return blob
    }

    private fun v3VeritabaniKur(): ByteArray {
        val blob = byteArrayOf(9, 8, 7, 6)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL(
            "CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, title TEXT NOT NULL, blob BLOB NOT NULL, " +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                "quick INTEGER NOT NULL DEFAULT 0, quickBlob BLOB DEFAULT NULL, " +
                "tags TEXT NOT NULL DEFAULT '[]')"
        )
        db.execSQL(
            "CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, color INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO entries (type, title, blob, createdAt, updatedAt, quick, tags) " +
                "VALUES ('NOTE', 'v3 kaydı', x'09080706', 10, 20, 0, '[5]')"
        )
        db.version = 3
        db.close()
        return blob
    }

    @Test
    fun goc3ten4e_notTuruVeSiraKolonlariniVarsayilanlaEkler() {
        val beklenenBlob = v3VeritabaniKur()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) =
                        error("v3 veritabanı vardı; onCreate çağrılmamalıydı")

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
                        assertEquals(3, old)
                        VaultDatabase.MIGRATION_3_4.migrate(db)
                    }
                })
                .build()
        )

        helper.writableDatabase.use { db ->
            db.query("SELECT title, blob, tags, noteKind, sortIndex FROM entries").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("v3 kaydı", c.getString(0))
                assertArrayEquals(beklenenBlob, c.getBlob(1))
                assertEquals("[5]", c.getString(2))     // etiketler korunur
                assertEquals("GENEL", c.getString(3))   // varsayılan not türü
                assertEquals(0, c.getInt(4))            // varsayılan sıra
            }
        }
    }

    @Test
    fun goc2den3e_veriKaybetmedenTagsKolonuEkler() {
        val beklenenBlob = v2VeritabaniKur()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) =
                        error("v2 veritabanı vardı; onCreate çağrılmamalıydı")

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
                        assertEquals(2, old)
                        VaultDatabase.MIGRATION_2_3.migrate(db)
                    }
                })
                .build()
        )

        helper.writableDatabase.use { db ->
            // Eski satır aynen duruyor ve yeni sütun varsayılanıyla geldi
            db.query("SELECT type, title, blob, quick, tags FROM entries").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("LOGIN", c.getString(0))
                assertEquals("Eski kayıt", c.getString(1))
                assertArrayEquals(beklenenBlob, c.getBlob(2))
                assertEquals(1, c.getInt(3))
                assertEquals("[]", c.getString(4))
            }
            // tags tablosu kullanılabilir durumda
            db.execSQL("INSERT INTO tags (name, color) VALUES ('finans', -1)")
            db.query("SELECT name FROM tags").use { c ->
                assertEquals(1, c.count)
            }
        }
    }
}
