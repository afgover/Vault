package com.afgover.vault.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kullanım günlüğü olayının sözleşmesi (vault_takip P-012). */
class UsageLogTest {

    @Test
    fun `olay gidis donus - tum alanlar korunur`() {
        val event = UsageEvent(
            entryId = 42,
            kind = UsageKind.BT_YAZILDI,
            at = 1_700_000_000_000,
            fieldLabel = "Şifre",
            target = "MacBook Pro"
        )
        val geri = UsageEvent.fromBytes(event.toBytes())
        assertEquals(event, geri)
    }

    @Test
    fun `bos alanlar null kalir - bos dize olarak geri gelmez`() {
        val event = UsageEvent(7, UsageKind.OLUSTURULDU, 1)
        val geri = UsageEvent.fromBytes(event.toBytes())!!
        assertNull(geri.fieldLabel)
        assertNull(geri.target)
    }

    @Test
    fun `bozuk ya da bilinmeyen tur null doner - cokme yok`() {
        assertNull(UsageEvent.fromBytes("bu json degil".toByteArray()))
        assertNull(UsageEvent.fromBytes(ByteArray(0)))
        assertNull(
            UsageEvent.fromBytes(
                """{"entryId":1,"kind":"OLMAYAN_TUR","at":5}""".toByteArray()
            )
        )
    }

    /**
     * Günlük "ne yapıldığını" yazar, ne kullanıldığını değil: serileştirilmiş
     * olayda değerin kendisi hiçbir koşulda bulunmamalı. Bu test, ileride
     * olaya alan eklenirken değerin kazara sızmasına karşı nöbet noktasıdır.
     */
    @Test
    fun `olayda deger tasinmaz - yalniz ad ve zaman`() {
        val gizli = "sifre123-COK-GIZLI"
        val event = UsageEvent(1, UsageKind.KOPYALANDI, 99, fieldLabel = "Şifre", target = "Chrome")
        val json = String(event.toBytes())
        assertFalse(json.contains(gizli))
        assertTrue(json.contains("Şifre"))
        assertTrue(json.contains("Chrome"))
        // Olayda başka hiçbir alan yok: yeni bir alan eklenirse bu test düşer
        // ve "değer taşımıyor mu" sorusu yeniden sorulur.
        assertEquals(
            setOf("entryId", "kind", "at", "field", "target"),
            JSONObject(json).keys().asSequence().toSet()
        )
    }

    @Test
    fun `tur adlari kararlidir - kayitli olaylar okunmaya devam eder`() {
        // Adlar veritabanındaki şifreli olaylarda saklanıyor; değiştirilirse
        // eski günlük okunamaz hâle gelir.
        assertEquals(
            listOf(
                "OLUSTURULDU", "DEGISTIRILDI", "KOPYALANDI", "BT_YAZILDI",
                "KLAVYE_YAZILDI", "HIZLI_ERISIM_ACILDI", "HIZLI_ERISIM_KAPATILDI"
            ),
            UsageKind.entries.map { it.name }
        )
    }
}
