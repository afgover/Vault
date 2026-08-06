package com.afgover.vault.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** Otomatik doldurmada tanıdığımız alan türleri. */
enum class FieldKind {
    USERNAME,
    PASSWORD,
    CARD_NUMBER,
    CARD_EXPIRY,
    CARD_EXP_MONTH,
    CARD_EXP_YEAR,
    CVV,
    CARDHOLDER;

    val isCard: Boolean
        get() = this != USERNAME && this != PASSWORD
}

data class ParsedField(
    val id: AutofillId,
    val kind: FieldKind,
    /** Kaydetme isteğinde alanın kullanıcı tarafından girilmiş değeri. */
    val value: String? = null
)

data class ParsedStructure(
    val packageName: String,
    val webDomain: String?,
    val fields: List<ParsedField>
) {
    val hasLoginFields: Boolean
        get() = fields.any { !it.kind.isCard }

    val hasCardFields: Boolean
        get() = fields.any { it.kind.isCard }

    fun idsOf(kind: FieldKind): List<AutofillId> =
        fields.filter { it.kind == kind }.map { it.id }

    fun allIds(): Array<AutofillId> = fields.map { it.id }.distinct().toTypedArray()

    fun valueOf(kind: FieldKind): String? =
        fields.firstOrNull { it.kind == kind && !it.value.isNullOrBlank() }?.value
}

/**
 * [AssistStructure] içindeki doldurulabilir metin alanlarını tarar ve
 * her birini bir [FieldKind] ile eşler.
 *
 * Önce `autofillHints` (uygulamanın/HTML'in kendi beyanı) denenir; yoksa
 * alan kimliği, ipucu metni ve `inputType` üzerinden Türkçe/İngilizce
 * anahtar kelimelerle tahmin edilir.
 */
object StructureParser {

    private val HTML_ATTRS_OF_INTEREST = setOf("name", "id", "type", "autocomplete", "placeholder")

    fun parse(structure: AssistStructure): ParsedStructure {
        val fields = mutableListOf<ParsedField>()
        var webDomain: String? = null
        val seen = mutableSetOf<AutofillId>()

        fun visit(node: AssistStructure.ViewNode) {
            node.webDomain?.takeIf { it.isNotBlank() }?.let { if (webDomain == null) webDomain = it }

            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && seen.add(id)) {
                classify(node)?.let { kind ->
                    fields += ParsedField(id, kind, node.autofillValue
                        ?.takeIf { it.isText }
                        ?.textValue
                        ?.toString())
                }
            }
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) {
            visit(structure.getWindowNodeAt(i).rootViewNode)
        }
        return ParsedStructure(structure.activityComponent.packageName, webDomain, fields)
    }

    private fun classify(node: AssistStructure.ViewNode): FieldKind? =
        fromHints(node.autofillHints) ?: fromHeuristics(node)

    private fun fromHints(hints: Array<String>?): FieldKind? {
        hints ?: return null
        for (raw in hints) {
            val hint = raw.lowercase().replace("-", "").replace("_", "")
            when {
                hint.contains("newpassword") || hint == "password" -> return FieldKind.PASSWORD
                hint.contains("creditcardnumber") || hint == "ccnumber" -> return FieldKind.CARD_NUMBER
                hint.contains("creditcardexpirationmonth") || hint == "ccexpmonth" ->
                    return FieldKind.CARD_EXP_MONTH
                hint.contains("creditcardexpirationyear") || hint == "ccexpyear" ->
                    return FieldKind.CARD_EXP_YEAR
                hint.contains("creditcardexpirationdate") || hint == "ccexp" ->
                    return FieldKind.CARD_EXPIRY
                hint.contains("creditcardsecuritycode") || hint == "cccsc" || hint == "cccvc" ->
                    return FieldKind.CVV
                hint == "ccname" || hint.contains("creditcardholder") -> return FieldKind.CARDHOLDER
                hint == "username" || hint == "emailaddress" || hint == "email" ->
                    return FieldKind.USERNAME
            }
        }
        return null
    }

    private fun fromHeuristics(node: AssistStructure.ViewNode): FieldKind? {
        val text = buildString {
            append(node.idEntry ?: "")
            append(' ')
            append(node.hint ?: "")
            append(' ')
            append(node.contentDescription ?: "")
            append(' ')
            node.htmlInfo?.attributes?.forEach { attr ->
                // android.util.Pair: first = öznitelik adı, second = değeri
                if (attr.first in HTML_ATTRS_OF_INTEREST) {
                    append(attr.second ?: "")
                    append(' ')
                }
            }
        }.lowercase().replace("ı", "i").replace("ş", "s").replace("ü", "u")
            .replace("ö", "o").replace("ç", "c").replace("ğ", "g")

        fun has(vararg keys: String) = keys.any { text.contains(it) }

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordType = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        return when {
            has("cvv", "cvc", "securitycode", "guvenlikkodu") -> FieldKind.CVV
            has("cardnumber", "cardno", "kartno", "kartnumara", "creditcard", "cardnum") ->
                FieldKind.CARD_NUMBER
            has("expmonth", "expirationmonth", "sonkullanmaay") -> FieldKind.CARD_EXP_MONTH
            has("expyear", "expirationyear", "sonkullanmayil") -> FieldKind.CARD_EXP_YEAR
            has("expiry", "expiration", "sonkullanma", "skt") -> FieldKind.CARD_EXPIRY
            has("cardholder", "kartsahibi", "nameoncard") -> FieldKind.CARDHOLDER
            isPasswordType || has("password", "passwd", "pwd", "sifre", "parola") ->
                FieldKind.PASSWORD
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ->
                FieldKind.USERNAME
            has("username", "userid", "user_name", "kullanici", "eposta", "e-posta", "email",
                "mail", "login", "hesap", "account") -> FieldKind.USERNAME
            else -> null
        }
    }
}
