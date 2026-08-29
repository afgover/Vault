package com.afgover.vault.data

import com.afgover.vault.core.VaultSession
import com.afgover.vault.core.Crypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryDataTest {

    @Test
    fun `toJson-fromJson gidis donusu tum alanlari korur`() {
        val orijinal = EntryData(
            username = "u", password = "p", url = "example.com",
            cardholder = "Ad Soyad", cardNumber = "4111", expiry = "12/28",
            cvv = "123", iban = "TR00", notes = "not\nsatır",
            fullName = "Ahmet", phone = "+90", email = "a@b.c", address = "adres",
            custom = listOf(CustomField("etiket", "değer")),
            passwordChangedAt = 42
        )
        assertEquals(orijinal, EntryData.fromJson(orijinal.toJson()))
    }

    @Test
    fun `bos alanlar jsona yazilmaz - damgasiz kayitta passwordChangedAt sifir kalir`() {
        val json = EntryData(password = "x").toJson()
        assertFalse(json.has("username"))
        assertFalse(json.has("passwordChangedAt"))
        assertEquals(0, EntryData.fromJson(json).passwordChangedAt)
    }

    @Test
    fun `parola gecmisi gidis donusu - deger ve tarih korunur`() {
        val orijinal = EntryData(
            password = "yeni",
            passwordChangedAt = 300,
            passwordHistory = listOf(OldPassword("orta", 200), OldPassword("ilk", 100))
        )
        val donen = EntryData.fromJson(orijinal.toJson())
        assertEquals(orijinal, donen)
        // Sıra korunmalı: en yenisi başta.
        assertEquals(listOf("orta", "ilk"), donen.passwordHistory.map { it.value })
        assertEquals(listOf(200L, 100L), donen.passwordHistory.map { it.changedAt })
    }

    @Test
    fun `bos gecmis jsona yazilmaz`() {
        assertFalse(EntryData(password = "p").toJson().has("passwordHistory"))
    }

    @Test
    fun `gecmisi olmayan eski yedek bos gecmisle acilir`() {
        val eski = org.json.JSONObject("""{"password":"p","passwordChangedAt":5}""")
        assertTrue(EntryData.fromJson(eski).passwordHistory.isEmpty())
    }

    @Test
    fun `eski parola klavye ve otomatik doldurma alanlarina SIZMAZ`() {
        // Geçmiş bilerek fields() dışında: klavye yalnız güncel parolayı yazar.
        val data = EntryData(
            password = "guncel",
            passwordHistory = listOf(OldPassword("eskisi", 1))
        )
        assertFalse(data.fields().any { it.value == "eskisi" })
        assertTrue(data.fields().any { it.value == "guncel" })
    }

    @Test
    fun `eski yedekte olmayan alanlar bos gelir`() {
        val eski = org.json.JSONObject("""{"username":"u","password":"p"}""")
        val data = EntryData.fromJson(eski)
        assertEquals("u", data.username)
        assertTrue(data.custom.isEmpty())
        assertEquals(0, data.passwordChangedAt)
    }
}

class TagIdsTest {

    @Test
    fun `parse - bos, null, cop girdide bos liste`() {
        assertTrue(TagIds.parse(null).isEmpty())
        assertTrue(TagIds.parse("").isEmpty())
        assertTrue(TagIds.parse("cop").isEmpty())
        assertTrue(TagIds.parse("{\"a\":1}").isEmpty())
    }

    @Test
    fun `serialize-parse gidis donusu ve tekrar eleme`() {
        assertEquals(listOf(1L, 3L), TagIds.parse(TagIds.serialize(listOf(1L, 3L, 1L))))
        assertEquals("[]", TagIds.serialize(emptyList()))
    }
}

class VaultSessionTest {

    @Test
    fun `kilit semantigi - lock anahtari dusurur`() {
        val key = Crypto.randomKey()
        VaultSession.unlock(key)
        assertTrue(VaultSession.isUnlocked)
        assertEquals(key, VaultSession.key())

        VaultSession.lock()
        assertFalse(VaultSession.isUnlocked)
        assertNull(VaultSession.key())
    }
}
