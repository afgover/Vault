package com.afgover.vault.data

import android.content.Context

/**
 * Kayıt sıralaması. Kullanıcı seçer; hem Vault Klavyesi hem ana liste aynı
 * sırayı kullanır — klavyede aradığını bulmak için kurulan alışkanlık
 * uygulamada da geçerli olsun diye.
 *
 * Tercih düz bir ayar dosyasında tutulur (sır değil): klavye ayrı bir süreç
 * gibi davranabildiği için Room'a değil `SharedPreferences`'a yazılır.
 */
enum class EntrySort(val label: String) {
    TITLE_ASC("Başlık (A→Z)"),
    TITLE_DESC("Başlık (Z→A)"),
    UPDATED_DESC("Son güncellenen"),
    CREATED_DESC("Son eklenen"),
    TYPE("Türe göre");

    companion object {
        private const val PREFS = "vault_settings"
        private const val KEY = "entry_sort"

        fun read(context: Context): EntrySort {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return TITLE_ASC
            return runCatching { valueOf(name) }.getOrDefault(TITLE_ASC)
        }

        fun write(context: Context, sort: EntrySort) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, sort.name).apply()
        }
    }
}

/** Çözülmüş kayıtları seçili sıraya göre dizer (klavye). */
fun List<DecryptedEntry>.sortedBy(sort: EntrySort): List<DecryptedEntry> = when (sort) {
    EntrySort.TITLE_ASC -> sortedBy { it.title.lowercase() }
    EntrySort.TITLE_DESC -> sortedByDescending { it.title.lowercase() }
    EntrySort.UPDATED_DESC -> sortedByDescending { it.updatedAt }
    EntrySort.CREATED_DESC -> sortedByDescending { it.createdAt }
    EntrySort.TYPE -> sortedWith(compareBy({ it.type.ordinal }, { it.title.lowercase() }))
}
