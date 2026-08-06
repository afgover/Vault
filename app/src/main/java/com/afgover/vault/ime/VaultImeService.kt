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
import com.afgover.vault.ui.MainActivity
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
 * Kasa kilitliyse önce uygulamadan kilidin açılması istenir (uygulama ve
 * klavye aynı süreçte çalıştığı için oturum paylaşılır).
 */
class VaultImeService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout

    private var entries: List<DecryptedEntry> = emptyList()
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
        header.addView(flatButton("⌫") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        header.addView(flatButton("⌄") { requestHideSelf(0) })
        root.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun refresh() {
        val key = VaultSession.key()
        if (key == null) {
            entries = emptyList()
            renderLocked()
            return
        }
        VaultSession.touch()
        scope.launch {
            val app = application as VaultApp
            entries = withContext(Dispatchers.IO) {
                app.repository.getAllDecrypted(key)
            }
            render()
        }
    }

    private fun renderLocked() {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = "Kasa kilitli. Bilgileri kullanmak için önce Vault uygulamasında kilidi aç."
            setTextColor(color(R.color.ime_text))
            setPadding(dp(8), dp(16), dp(8), dp(8))
        })
        content.addView(actionButton("Vault'u aç") {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        })
    }

    private fun render() {
        content.removeAllViews()
        val current = selected
        if (current == null) {
            if (entries.isEmpty()) {
                content.addView(TextView(this).apply {
                    text = "Kayıt yok."
                    setTextColor(color(R.color.ime_text))
                    setPadding(dp(8), dp(16), dp(8), dp(8))
                })
                return
            }
            entries.forEach { entry ->
                content.addView(actionButton(entry.title) {
                    selected = entry
                    render()
                })
            }
        } else {
            content.addView(actionButton("← ${current.title}") {
                selected = null
                render()
            })
            current.data.fields().forEach { (label, value) ->
                content.addView(actionButton("$label yaz") {
                    VaultSession.touch()
                    currentInputConnection?.commitText(value, 1)
                })
            }
        }
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

    private fun switchBackToKeyboard() {
        if (Build.VERSION.SDK_INT >= 28) {
            switchToPreviousInputMethod()
        } else {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
    }
}
