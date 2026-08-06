package com.afgover.vault.data

import org.json.JSONObject

/**
 * Bir kaydın şifrelenen alanları. Türe göre ilgili alanlar doldurulur;
 * boş alanlar JSON'a yazılmaz.
 */
data class EntryData(
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val cardholder: String = "",
    val cardNumber: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val iban: String = "",
    val notes: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (username.isNotEmpty()) put("username", username)
        if (password.isNotEmpty()) put("password", password)
        if (url.isNotEmpty()) put("url", url)
        if (cardholder.isNotEmpty()) put("cardholder", cardholder)
        if (cardNumber.isNotEmpty()) put("cardNumber", cardNumber)
        if (expiry.isNotEmpty()) put("expiry", expiry)
        if (cvv.isNotEmpty()) put("cvv", cvv)
        if (iban.isNotEmpty()) put("iban", iban)
        if (notes.isNotEmpty()) put("notes", notes)
    }

    /** IME ve detay ekranında gösterilecek (etiket, değer) çiftleri. */
    fun fields(): List<Pair<String, String>> = buildList {
        if (username.isNotEmpty()) add("Kullanıcı adı" to username)
        if (password.isNotEmpty()) add("Şifre" to password)
        if (url.isNotEmpty()) add("Adres" to url)
        if (cardholder.isNotEmpty()) add("Kart sahibi" to cardholder)
        if (cardNumber.isNotEmpty()) add("Kart no" to cardNumber)
        if (expiry.isNotEmpty()) add("Son kul. tarihi" to expiry)
        if (cvv.isNotEmpty()) add("CVV" to cvv)
        if (iban.isNotEmpty()) add("IBAN" to iban)
        if (notes.isNotEmpty()) add("Not" to notes)
    }

    companion object {
        fun fromJson(json: JSONObject): EntryData = EntryData(
            username = json.optString("username"),
            password = json.optString("password"),
            url = json.optString("url"),
            cardholder = json.optString("cardholder"),
            cardNumber = json.optString("cardNumber"),
            expiry = json.optString("expiry"),
            cvv = json.optString("cvv"),
            iban = json.optString("iban"),
            notes = json.optString("notes")
        )
    }
}

/** Bellekte çözülmüş kayıt. */
data class DecryptedEntry(
    val id: Long,
    val type: EntryType,
    val title: String,
    val data: EntryData,
    val createdAt: Long,
    val updatedAt: Long
)
