package com.afgover.vault.ime

import android.content.Context

/**
 * Klavyeden en son kullanılan kayıtların sırası.
 *
 * Yalnızca kayıt kimlikleri (sayı) saklanır; başlık, şifre gibi hiçbir içerik
 * yazılmaz. Kimlikler zaten veritabanında açık duruyor, ek bir sızıntı yok.
 */
class RecentEntries(context: Context) {

    private val prefs = context.getSharedPreferences("vault_ime_recents", Context.MODE_PRIVATE)

    fun ids(): List<Long> = prefs.getString(KEY, "")
        .orEmpty()
        .split(',')
        .mapNotNull { it.toLongOrNull() }

    fun record(id: Long) {
        val updated = (listOf(id) + ids().filter { it != id }).take(MAX)
        prefs.edit().putString(KEY, updated.joinToString(",")).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "recent_ids"
        const val MAX = 5
    }
}
