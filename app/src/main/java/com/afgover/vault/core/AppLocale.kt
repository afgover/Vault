package com.afgover.vault.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.afgover.vault.R
import java.util.Locale

/**
 * Uygulama dili — cihaz dilinden BAĞIMSIZ seçilebilir.
 *
 * Yerelleştirme ilk hâlinde yalnız cihaz diline bakıyordu: telefonu Türkçe olan
 * kullanıcı İngilizceyi hiç göremiyordu, çünkü seçenek yoktu. Android'in kendi
 * "uygulama dili" ayarı yalnız API 33+'ta var; burada minSdk 26 olduğu için
 * tercih uygulamanın kendi ayarında tutulur ve her Context açılışında
 * yapılandırma üstüne bindirilir ([wrap]).
 *
 * Dil değişince ekranların yeniden yaratılması gerekir; ayar ekranı bunu
 * `recreate()` ile yapar.
 */
object AppLocale {

    private const val PREFS = "vault_settings"
    private const val KEY = "app_language"

    /** Seçenekler: sistem dili ya da açıkça bir dil. */
    enum class Option(val tag: String, @StringRes val labelRes: Int) {
        SYSTEM("", R.string.settings_lang_system),
        TURKISH("tr", R.string.settings_lang_tr),
        ENGLISH("en", R.string.settings_lang_en),
        SPANISH("es", R.string.settings_lang_es),
        HINDI("hi", R.string.settings_lang_hi),
        ARABIC("ar", R.string.settings_lang_ar),
        CHINESE("zh", R.string.settings_lang_zh),
        FRENCH("fr", R.string.settings_lang_fr);

        companion object {
            fun of(tag: String?): Option =
                entries.firstOrNull { it.tag == (tag ?: "") } ?: SYSTEM
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** O anki tercih etiketi ("" = sistem). Servislerin değişimi görmesi için. */
    fun currentTag(context: Context): String =
        prefs(context).getString(KEY, "").orEmpty()

    fun current(context: Context): Option =
        Option.of(prefs(context).getString(KEY, ""))

    fun set(context: Context, option: Option) {
        prefs(context).edit().putString(KEY, option.tag).apply()
    }

    /**
     * Seçili dili bir Context'e uygular. Her Activity ve Service
     * `attachBaseContext` içinde bunu çağırır; sistem dili seçiliyse Context
     * olduğu gibi döner.
     *
     * [Locale.setDefault] de ayarlanır: tarih/sayı biçimleyiciler (ör. kullanım
     * günlüğündeki tarih) kaynak dosyalarından değil JVM'in varsayılan dilinden
     * beslenir, yoksa arayüz İngilizce olurken tarihler Türkçe kalırdı.
     */
    fun wrap(base: Context): Context {
        // SharedPreferences'a attachBaseContext içinde erişmek güvenlidir:
        // uygulama bağlamı bu noktada hazırdır.
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()
        if (tag.isEmpty()) {
            // "Sistem dili"ne DÖNERKEN de varsayılanı geri koymak gerekiyor:
            // erken çıkılırsa Locale.setDefault önceki seçimde kalır ve arayüz
            // Türkçeye dönerken tarihler İngilizce biçimlenirdi (denetim).
            // base.resources.configuration setDefault'tan etkilenmez, sistemin
            // gerçek dilini taşır.
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
