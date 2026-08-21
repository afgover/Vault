package com.afgover.vault.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afgover.vault.VaultApp
import com.afgover.vault.backup.BackupManager
import com.afgover.vault.core.VaultSession
import com.afgover.vault.data.DecryptedEntry
import com.afgover.vault.data.EntryData
import com.afgover.vault.data.EntryType
import com.afgover.vault.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EntryListItem(
    val id: Long,
    val type: EntryType,
    val title: String,
    val quick: Boolean
)

enum class LockState { NEEDS_SETUP, LOCKED, UNLOCKED }

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VaultApp
    val keyManager = app.keyManager
    private val repo = app.repository

    var lockState by mutableStateOf(
        when {
            !keyManager.isInitialized -> LockState.NEEDS_SETUP
            VaultSession.isUnlocked -> LockState.UNLOCKED
            else -> LockState.LOCKED
        }
    )
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    val entries: StateFlow<List<EntryListItem>> = repo.observeAll()
        .map { list ->
            list.map {
                EntryListItem(
                    id = it.id,
                    type = runCatching { EntryType.valueOf(it.type) }.getOrDefault(EntryType.NOTE),
                    title = it.title,
                    quick = it.quick
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ekran öne geldiğinde: ekran kapanıp kasa kilitlendiyse kilit ekranına dön. */
    fun refreshLockState() {
        if (lockState == LockState.UNLOCKED && !VaultSession.isUnlocked) {
            lockState = LockState.LOCKED
        }
    }

    /**
     * Kilit açıldığında hızlı erişim kopyaları da tazelenir: Keystore anahtarı
     * kaybolmuş ya da kopya hiç üretilememişse kaydın aslından yeniden yazılır.
     */
    private fun onUnlocked(key: javax.crypto.SecretKey) {
        VaultSession.unlock(key)
        lockState = LockState.UNLOCKED
        viewModelScope.launch(Dispatchers.IO) { repo.repairQuickCopies(key) }
    }

    fun setup(password: String, confirm: String) {
        if (password.length < 8) {
            error = "Ana parola en az 8 karakter olmalı"
            return
        }
        if (password != confirm) {
            error = "Parolalar eşleşmiyor"
            return
        }
        viewModelScope.launch {
            busy = true
            val key = withContext(Dispatchers.Default) {
                keyManager.setup(password.toCharArray())
            }
            busy = false
            onUnlocked(key)
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            busy = true
            val key = withContext(Dispatchers.Default) {
                keyManager.unlockWithPassword(password.toCharArray())
            }
            busy = false
            if (key == null) {
                error = "Parola yanlış"
            } else {
                onUnlocked(key)
            }
        }
    }

    fun onBiometricUnlocked(key: javax.crypto.SecretKey) = onUnlocked(key)

    fun lock() {
        VaultSession.lock()
        lockState = LockState.LOCKED
    }

    fun changePassword(old: String, new: String, confirm: String, onDone: () -> Unit) {
        if (new.length < 8) {
            error = "Yeni parola en az 8 karakter olmalı"
            return
        }
        if (new != confirm) {
            error = "Parolalar eşleşmiyor"
            return
        }
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                keyManager.changePassword(old.toCharArray(), new.toCharArray())
            }
            busy = false
            if (ok) {
                toast("Ana parola değiştirildi")
                onDone()
            } else {
                error = "Mevcut parola yanlış"
            }
        }
    }

    // ---- Kayıtlar ----

    suspend fun loadEntry(id: Long): DecryptedEntry? {
        val key = VaultSession.key() ?: return null
        return withContext(Dispatchers.IO) { repo.getDecrypted(id, key) }
    }

    fun saveEntry(
        id: Long,
        type: EntryType,
        title: String,
        data: EntryData,
        quick: Boolean,
        onDone: () -> Unit
    ) {
        val key = VaultSession.key() ?: run { lockState = LockState.LOCKED; return }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.save(id, type, title, data, quick, key) }
                onDone()
            } catch (e: VaultRepository.EntryTooLargeException) {
                // Ekran açık kalır, girilenler durur; kullanıcı kısaltıp yeniden dener.
                toast(e.message ?: "Kayıt çok büyük")
            }
        }
    }

    /** Detay ekranındaki hızlı erişim anahtarı; içeriğe dokunmaz. */
    fun setQuick(id: Long, quick: Boolean, onDone: () -> Unit = {}) {
        val key = VaultSession.key() ?: run { lockState = LockState.LOCKED; return }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setQuick(id, quick, key) }
            toast(
                if (quick) "Klavyede parolasız kullanılabilir"
                else "Klavyede kilit açmadan görünmez"
            )
            onDone()
        }
    }

    fun deleteEntry(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            onDone()
        }
    }

    // ---- Pano ----

    private var clipboardClearJob: Job? = null

    fun copyToClipboard(label: String, value: String) {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)
        toast("$label kopyalandı (45 sn sonra silinecek)")
        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(45_000)
            try {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            } catch (_: Exception) {
            }
        }
    }

    // ---- Yedekleme ----

    fun exportBackup(uri: Uri, password: String) {
        val key = VaultSession.key() ?: run { lockState = LockState.LOCKED; return }
        if (password.length < 8) {
            error = "Yedek parolası en az 8 karakter olmalı"
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val all = repo.getAllDecrypted(key)
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw Exception("Dosya açılamadı")
                    BackupManager.export(out, password.toCharArray(), all)
                }
                toast("Yedek kaydedildi")
            } catch (e: Exception) {
                error = "Yedekleme başarısız: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun importBackup(uri: Uri, password: String, replace: Boolean) {
        val key = VaultSession.key() ?: run { lockState = LockState.LOCKED; return }
        viewModelScope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw Exception("Dosya açılamadı")
                    val imported = BackupManager.import(input, password.toCharArray())
                    if (replace) repo.replaceAll(imported, key) else repo.addAll(imported, key)
                    imported.size
                }
                toast("$count kayıt geri yüklendi")
            } catch (e: BackupManager.WrongPasswordException) {
                error = "Yedek parolası yanlış"
            } catch (e: BackupManager.InvalidFormatException) {
                error = "Geçersiz yedek dosyası"
            } catch (e: Exception) {
                error = "Geri yükleme başarısız: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    // ---- Ayarlar ----

    fun disableBiometric() {
        keyManager.clearBiometric()
        toast("Biyometrik kilit açma kapatıldı")
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }
}
