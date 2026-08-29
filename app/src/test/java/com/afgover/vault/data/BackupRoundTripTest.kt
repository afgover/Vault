package com.afgover.vault.data

import com.afgover.vault.backup.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Yedek dosyasının GERÇEKTEN taşıdığı şeyler.
 *
 * Kullanıcı isteği: parola geçmişi ve araç çıpası yedeğe giriyor mu, geri
 * yüklemede aynen dönüyor mu. Model testi JSON'un kendi içinde tutarlı
 * olduğunu gösterir; buradaki testler asıl soruyu sorar — şifreli `.vaultbak`
 * baytlarına yazılıp oradan okununca hiçbir şey düşüyor mu?
 */
class BackupRoundTripTest {

    private val parola = "yedek-parolasi-123".toCharArray()

    private fun gidisDonus(entries: List<DecryptedEntry>): List<DecryptedEntry> {
        val bytes = ByteArrayOutputStream().use { out ->
            BackupManager.export(out, parola, entries, emptyList())
            out.toByteArray()
        }
        return BackupManager.import(bytes.inputStream(), parola).entries
    }

    @Test
    fun `parola gecmisi yedege girer ve aynen geri doner`() {
        val kayit = DecryptedEntry(
            id = 7,
            type = EntryType.LOGIN,
            title = "Örnek hesap",
            data = EntryData(
                username = "kullanici",
                password = "ucuncu",
                passwordChangedAt = 3_000,
                passwordHistory = listOf(
                    OldPassword("ikinci", 2_000),
                    OldPassword("birinci", 1_000)
                )
            ),
            createdAt = 10,
            updatedAt = 20
        )

        val donen = gidisDonus(listOf(kayit)).single()

        assertEquals("ucuncu", donen.data.password)
        assertEquals(3_000L, donen.data.passwordChangedAt)
        assertEquals(
            listOf("ikinci" to 2_000L, "birinci" to 1_000L),
            donen.data.passwordHistory.map { it.value to it.changedAt }
        )
    }

    @Test
    fun `arac cipasi yedege girer ve cipa olarak geri doner`() {
        val cipa = DecryptedEntry(
            id = 1,
            type = EntryType.NOTE,
            title = "aktar.html",
            data = EntryData(notes = "a".repeat(64)),
            createdAt = 100,
            updatedAt = 200,
            noteKind = NoteKind.PARMAK_IZI,
            anchor = true
        )
        val normal = DecryptedEntry(
            id = 2,
            type = EntryType.NOTE,
            title = "Sıradan not",
            data = EntryData(notes = "içerik"),
            createdAt = 100,
            updatedAt = 200
        )

        val donen = gidisDonus(listOf(cipa, normal))

        val donenCipa = donen.single { it.anchor }
        assertEquals("aktar.html", donenCipa.title)
        assertEquals("a".repeat(64), donenCipa.data.notes)
        assertEquals(NoteKind.PARMAK_IZI, donenCipa.noteKind)
        // Sıradan kayıt çıpaya dönüşmemeli: bayrak yanlış yayılırsa normal
        // notlar listeden kaybolurdu.
        assertTrue(donen.none { !it.anchor && it.title == "aktar.html" })
        assertEquals(1, donen.count { it.anchor })
        assertEquals("Sıradan not", donen.single { !it.anchor }.title)
    }

    @Test
    fun `cipa bayragi olmayan eski yedek cipasiz acilir`() {
        // Bu sürümden önce alınmış yedeklerde "anchor" alanı hiç yok; hepsi
        // normal kayıt sayılmalı, yoksa geri yükleme sonrası liste bozulurdu.
        val eski = DecryptedEntry(
            id = 1,
            type = EntryType.NOTE,
            title = "eski not",
            data = EntryData(notes = "x"),
            createdAt = 1,
            updatedAt = 1
        )
        assertTrue(gidisDonus(listOf(eski)).none { it.anchor })
    }

    @Test
    fun `yanlis parolayla acilmaz`() {
        val bytes = ByteArrayOutputStream().use { out ->
            BackupManager.export(
                out, parola,
                listOf(
                    DecryptedEntry(
                        1, EntryType.LOGIN, "x",
                        EntryData(password = "p", passwordHistory = listOf(OldPassword("e", 1))),
                        1, 1
                    )
                ),
                emptyList()
            )
            out.toByteArray()
        }
        val hata = runCatching {
            BackupManager.import(bytes.inputStream(), "baska-parola".toCharArray())
        }.exceptionOrNull()
        assertNotNull(hata)
        assertTrue(hata is BackupManager.WrongPasswordException)
    }
}
