package com.afgover.vault.data

import org.json.JSONArray
import org.json.JSONObject

/** Kullanıcının kendi tanımladığı ek alan (ad + değer). */
data class CustomField(val label: String, val value: String)

/**
 * Bir kaydın şifrelenen alanları. Türe göre ilgili alanlar doldurulur;
 * boş alanlar JSON'a yazılmaz. Yeni alanlar sona eklenir: eski yedeklerde
 * bulunmayan alanlar boş gelir, biçim geriye dönük uyumludur.
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
    val notes: String = "",
    // Gündelik bilgiler
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    // Kullanıcının eklediği serbest alanlar
    val custom: List<CustomField> = emptyList(),
    /**
     * Şifre alanının son değiştiği an (epoch ms); 0 = bilinmiyor (eski kayıt).
     * Eskiyen parola uyarısının temeli. Şifreli veride durur: yalnız kilit
     * açıkken okunacak bir bilgidir.
     */
    val passwordChangedAt: Long = 0
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
        if (fullName.isNotEmpty()) put("fullName", fullName)
        if (phone.isNotEmpty()) put("phone", phone)
        if (email.isNotEmpty()) put("email", email)
        if (address.isNotEmpty()) put("address", address)
        if (custom.isNotEmpty()) {
            put("custom", JSONArray().apply {
                custom.forEach { field ->
                    put(JSONObject().put("label", field.label).put("value", field.value))
                }
            })
        }
        if (passwordChangedAt > 0) put("passwordChangedAt", passwordChangedAt)
    }

    /** IME ve detay ekranında gösterilecek (etiket, değer) çiftleri. */
    fun fields(): List<Pair<String, String>> = buildList {
        if (fullName.isNotEmpty()) add("Ad Soyad" to fullName)
        if (username.isNotEmpty()) add("Kullanıcı adı" to username)
        if (password.isNotEmpty()) add("Şifre" to password)
        if (url.isNotEmpty()) add("Site / Uygulama" to url)
        if (phone.isNotEmpty()) add("Telefon" to phone)
        if (email.isNotEmpty()) add("E-posta" to email)
        if (address.isNotEmpty()) add("Adres" to address)
        if (cardholder.isNotEmpty()) add("Kart sahibi" to cardholder)
        if (cardNumber.isNotEmpty()) add("Kart no" to cardNumber)
        if (expiry.isNotEmpty()) add("Son kul. tarihi" to expiry)
        if (cvv.isNotEmpty()) add("CVV" to cvv)
        if (iban.isNotEmpty()) add("IBAN" to iban)
        custom.forEach { field ->
            if (field.value.isNotEmpty()) add(field.label to field.value)
        }
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
            notes = json.optString("notes"),
            fullName = json.optString("fullName"),
            phone = json.optString("phone"),
            email = json.optString("email"),
            address = json.optString("address"),
            custom = json.optJSONArray("custom").toCustomFields(),
            passwordChangedAt = json.optLong("passwordChangedAt")
        )

        private fun JSONArray?.toCustomFields(): List<CustomField> {
            if (this == null) return emptyList()
            return buildList {
                for (i in 0 until length()) {
                    val o = optJSONObject(i) ?: continue
                    val label = o.optString("label")
                    if (label.isNotEmpty()) add(CustomField(label, o.optString("value")))
                }
            }
        }
    }
}

/** Bellekte çözülmüş kayıt. */
data class DecryptedEntry(
    val id: Long,
    val type: EntryType,
    val title: String,
    val data: EntryData,
    val createdAt: Long,
    val updatedAt: Long,
    /** Klavyede kasa kilitliyken de kullanılabilir mi? */
    val quick: Boolean = false,
    val tagIds: List<Long> = emptyList(),
    /** Yalnız yedek içe aktarmada dolu: id'ler cihaza özgüdür, yedek ad taşır. */
    val tagNames: List<String> = emptyList()
)
