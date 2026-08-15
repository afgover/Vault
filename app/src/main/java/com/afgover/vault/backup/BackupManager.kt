package com.afgover.vault.backup

import android.util.Base64
import com.afgover.vault.core.Crypto
import com.afgover.vault.data.DecryptedEntry
import com.afgover.vault.data.EntryData
import com.afgover.vault.data.EntryType
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Şifreli yedek dosyası (.vaultbak).
 *
 * Dosya, ana paroladan PBKDF2 ile türetilen bağımsız bir anahtarla AES-256-GCM
 * kullanılarak şifrelenir. Cihaza, Android Keystore'a veya uygulama verisine
 * hiçbir bağımlılığı yoktur: telefon sıfırlansa ya da değişse bile dosya +
 * parola ile her şey geri yüklenir.
 *
 * Biçim (düz JSON zarf, içerik şifreli):
 * {
 *   "app": "vault", "version": 1,
 *   "kdf": {"algo": "PBKDF2WithHmacSHA256", "iterations": N, "salt": b64},
 *   "cipher": "AES-256-GCM",
 *   "data": b64( iv || ciphertext+tag )   // içerik: {"entries":[...]}
 * }
 */
object BackupManager {

    class WrongPasswordException : Exception("Yedek parolası yanlış")
    class InvalidFormatException : Exception("Geçersiz yedek dosyası")

    fun export(output: OutputStream, password: CharArray, entries: List<DecryptedEntry>) {
        val payload = JSONObject().put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(
                    JSONObject()
                        .put("type", e.type.name)
                        .put("title", e.title)
                        .put("createdAt", e.createdAt)
                        .put("updatedAt", e.updatedAt)
                        .put("quick", e.quick)
                        .put("data", e.data.toJson())
                )
            }
        })

        val salt = Crypto.randomBytes(16)
        val key = Crypto.deriveKey(password, salt)
        val blob = Crypto.encrypt(key, payload.toString().toByteArray(Charsets.UTF_8))

        val envelope = JSONObject()
            .put("app", "vault")
            .put("version", 1)
            .put(
                "kdf",
                JSONObject()
                    .put("algo", "PBKDF2WithHmacSHA256")
                    .put("iterations", Crypto.KDF_ITERATIONS)
                    .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            )
            .put("cipher", "AES-256-GCM")
            .put("data", Base64.encodeToString(blob, Base64.NO_WRAP))

        output.use { it.write(envelope.toString(2).toByteArray(Charsets.UTF_8)) }
    }

    fun import(input: InputStream, password: CharArray): List<DecryptedEntry> {
        val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
        val envelope = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw InvalidFormatException()
        }
        if (envelope.optString("app") != "vault") throw InvalidFormatException()

        val kdf = envelope.optJSONObject("kdf") ?: throw InvalidFormatException()
        val salt = try {
            Base64.decode(kdf.optString("salt"), Base64.NO_WRAP)
        } catch (e: Exception) {
            throw InvalidFormatException()
        }
        val iterations = kdf.optInt("iterations", Crypto.KDF_ITERATIONS)
        val blob = try {
            Base64.decode(envelope.optString("data"), Base64.NO_WRAP)
        } catch (e: Exception) {
            throw InvalidFormatException()
        }

        val key = Crypto.deriveKey(password, salt, iterations)
        val plain = Crypto.decrypt(key, blob) ?: throw WrongPasswordException()

        val payload = try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw InvalidFormatException()
        }
        val array = payload.optJSONArray("entries") ?: throw InvalidFormatException()

        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    DecryptedEntry(
                        id = 0,
                        type = runCatching { EntryType.valueOf(o.optString("type")) }
                            .getOrDefault(EntryType.NOTE),
                        title = o.optString("title"),
                        data = EntryData.fromJson(o.optJSONObject("data") ?: JSONObject()),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        // Eski yedeklerde alan yok: korumalı kabul edilir.
                        quick = o.optBoolean("quick", false)
                    )
                )
            }
        }
    }
}
