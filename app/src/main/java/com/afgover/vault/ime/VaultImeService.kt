package com.afgover.vault.ime

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.afgover.vault.R
import com.afgover.vault.VaultApp
import com.afgover.vault.core.VaultSession
import com.afgover.vault.data.DecryptedEntry
import com.afgover.vault.data.EntrySort
import com.afgover.vault.data.sortedBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vault Klavyesi: herhangi bir uygulamada kayıtlı kullanıcı adı, şifre, kart
 * bilgisi vb. alanları doğrudan odaklanılan metin kutusuna yazar.
 *
 * Şifre değerleri ekranda gösterilmez; yalnızca alan etiketleri listelenir.
 *
 * Kasa kilitliyken listede **yalnızca "hızlı erişim" işaretli kayıtlar** çıkar;
 * bunlar Keystore'daki ayrı anahtarla çözülür, ana parola gerekmez. Geri kalan
 * her şey için 🔓 ile kilit açılır (parola/parmak izi) — kilit açık kaldığı
 * sürece (ekran kapanana kadar) tekrar sorulmaz.
 *
 * Kayıt sayısı arttığında listeyi taramak yerine son kullanılanlar en üstte
 * durur; 🔍 ile de arama yapılır. Klavye kendi metin kutusuna yazamadığı için
 * arama harfleri klavyenin kendi tuş ızgarasından gelir.
 */
class VaultImeService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView




    private var entries: List<DecryptedEntry> = emptyList()
    private var locked: Boolean = true
    private var selected: DecryptedEntry? = null


    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun color(res: Int): Int = ContextCompat.getColor(this, res)

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.ime_background))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // Üst çubuk
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "🔐 Vault"
            setTextColor(color(R.color.ime_accent))
            setTypeface(null, Typeface.BOLD)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(flatButton("ABC") { switchBackToKeyboard() })
        // Basılı tutunca sistem klavyesindeki gibi silmeye devam eder.
        header.addView(repeatingButton("⌫") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        header.addView(flatButton("⌄") { requestHideSelf(0) })
        root.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
            )
            addView(content)
        }
        root.addView(scroll)

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        selected = null
        refresh()
    }

    /** Kilit açma ekranından dönüldüğünde liste tazelenir. */
    override fun onWindowShown() {
        super.onWindowShown()
        if (locked && VaultSession.isUnlocked) refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /**
     * Kilitliyken yalnızca hızlı erişim kopyaları okunur; ana blob'a
     * dokunulmaz, yani korumalı kayıtlar kilitliyken çözülemez.
     */
    private fun refresh() {
        val key = VaultSession.key()
        locked = key == null
        scope.launch {
            val repo = (application as VaultApp).repository
            val sort = EntrySort.read(this@VaultImeService)
            entries = withContext(Dispatchers.IO) {
                (if (key == null) repo.getQuickDecrypted() else repo.getAllDecrypted(key))
                    .sortedBy(sort)
            }
            render()
        }
    }

    private fun render() {
        content.removeAllViews()
        val current = selected
        if (current != null) {
            renderFields(current)
            return
        }

        if (locked) {
            content.addView(hint("Kasa kilitli — yalnızca hızlı erişim kayıtları."))
            content.addView(actionButton("🔓 Kilidi aç (tümü için)") { startUnlock() })
        }

        if (entries.isEmpty()) {
            content.addView(
                hint(
                    if (locked) "Hızlı erişim işaretli kayıt yok."
                    else "Kayıt yok."
                )
            )
            return
        }

        entries.forEach { content.addView(entryButton(it)) }
    }

    private fun renderFields(entry: DecryptedEntry) {
        content.addView(actionButton("← ${entry.title}") {
            selected = null
            render()
        })
        entry.data.fields().forEach { (label, value) ->
            content.addView(actionButton("$label yaz") {
                currentInputConnection?.commitText(value, 1)
            })
        }
    }

    private fun entryButton(entry: DecryptedEntry): Button =
        actionButton(entry.title) {
            selected = entry
            render()
        }

    // --- Ortak görünümler ------------------------------------------------

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color(R.color.ime_text))
        setPadding(dp(8), dp(16), dp(8), dp(8))
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color(R.color.ime_accent))
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(8), dp(8), dp(8), dp(2))
    }

    private fun flatButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(color(R.color.ime_text))
            setBackgroundColor(Color.TRANSPARENT)
            minWidth = dp(48)
            minimumWidth = dp(48)
            setOnClickListener { onClick() }
        }

    /**
     * Basılı tutuldukça eylemi tekrarlayan düğme (silme tuşu). Sistem
     * klavyelerindeki davranış: 400 ms bekle, sonra 55 ms'de bir tekrarla.
     */
    private fun repeatingButton(label: String, onAction: () -> Unit): Button =
        flatButton(label) { onAction() }.apply {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var repeater: Runnable? = null
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        val r = object : Runnable {
                            override fun run() {
                                onAction()
                                handler.postDelayed(this, 55)
                            }
                        }
                        repeater = r
                        handler.postDelayed(r, 400)
                        false // tıklama da normal aksın (tek basış = tek silme)
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        repeater?.let(handler::removeCallbacks)
                        repeater = null
                        view.performClick()
                        false
                    }
                    else -> false
                }
            }
        }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(color(R.color.ime_text))
            setBackgroundColor(color(R.color.ime_surface))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) }
            setOnClickListener { onClick() }
        }

    /**
     * Kilit açma ekranı ayrı bir aktivitede açılır; onaylandıktan sonra
     * kullanıcı yazdığı uygulamaya döner ve klavye tazelenir ([onWindowShown]).
     */
    private fun startUnlock() {
        startActivity(Intent(this, ImeUnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun switchBackToKeyboard() {
        if (Build.VERSION.SDK_INT >= 28) {
            switchToPreviousInputMethod()
        } else {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
    }
}
