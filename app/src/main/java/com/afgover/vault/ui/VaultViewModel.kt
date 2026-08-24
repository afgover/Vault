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
import com.afgover.vault.data.EntrySort
import com.afgover.vault.data.NoteKind
import com.afgover.vault.data.EntryType
import com.afgover.vault.data.TagEntity
import com.afgover.vault.data.UsageEvent
import com.afgover.vault.data.UsageKind
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
    val quick: Boolean,
    val tagIds: List<Long> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val noteKind: NoteKind = NoteKind.GENEL,
    val sortIndex: Int = 0
)

enum class LockState { NEEDS_SETUP, LOCKED, NEEDS_PIN, UNLOCKED }

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VaultApp
    val keyManager = app.keyManager
    private val repo = app.repository
    private val usageLog = app.usageLog

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

    /** İlk kurulum biter bitmez biyometrik teklifi gösterilsin mi? */
    var offerBiometric by mutableStateOf(false)

    /** PIN aşaması için bekleyen iç sargı; PIN doğrulanınca temizlenir. */
    private var pendingInner: ByteArray? = null

    /**
     * Biyometrik kaydın durumu Compose'un göreceği bir durumda tutulur.
     * Doğrudan `keyManager.isBiometricEnabled` okunduğunda ekran, kayıt
     * değişse bile yeniden çizilmiyordu: kullanıcı parmak izini açıyor ama
     * düğme "etkinleştir" demeye devam ediyor, açılmamış sanılıyordu.
     */
    var biometricEnabled by mutableStateOf(keyManager.isBiometricEnabled)
        private set

    var pinEnabledState by mutableStateOf(keyManager.isPinEnabled)
        private set

    fun refreshLockOptions() {
        biometricEnabled = keyManager.isBiometricEnabled
        pinEnabledState = keyManager.isPinEnabled
    }

    val isPinEnabled: Boolean get() = pinEnabledState
    val isPinLegacy: Boolean get() = keyManager.isPinLegacy

    /** Eski sürümde açılmış PIN kaydını onarır (parola + PIN ile). */
    fun migrateLegacyPin(password: String, pin: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                keyManager.migrateLegacyPin(password.toCharArray(), pin)
            }
            busy = false
            refreshLockOptions()
            if (ok) toast("PIN kaydı onarıldı · kasa ana parolayla açılıyor")
            else error = "Onarılamadı: parola ya da PIN yanlış"
            onDone(ok)
        }
    }
    val reminderDays: Int get() = keyManager.reminderDays

    fun setReminderDays(days: Int) {
        keyManager.reminderDays = days
    }

    /** Biyometrik yerine ana parola istenmeli mi (hatırlatıcı doldu mu)? */
    val masterPasswordDue: Boolean get() = keyManager.masterPasswordDue()

    /** Kullanıcının seçtiği sıra; klavye ile ana liste aynı düzeni kullanır. */
    var sort by mutableStateOf(EntrySort.read(application))
        private set

    fun selectSort(value: EntrySort) {
        sort = value
        EntrySort.write(getApplication(), value)
    }

    val entries: StateFlow<List<EntryListItem>> = repo.observeAll()
        .map { list ->
            list.map {
                EntryListItem(
                    id = it.id,
                    type = runCatching { EntryType.valueOf(it.type) }.getOrDefault(EntryType.NOTE),
                    title = it.title,
                    quick = it.quick,
                    tagIds = com.afgover.vault.data.TagIds.parse(it.tags),
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    noteKind = NoteKind.of(it.noteKind),
                    sortIndex = it.sortIndex
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> = repo.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(name: String, color: Int, onDone: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { repo.addTag(name, color) }
            onDone(id)
        }
    }

    fun updateTag(tag: TagEntity) {
        if (tag.name.isBlank()) return
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.updateTag(tag) } }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.deleteTag(id) } }
    }

    /** Ekran öne geldiğinde: ekran kapanıp kasa kilitlendiyse kilit ekranına dön. */
    fun refreshLockState() {
        if (lockState == LockState.UNLOCKED && !VaultSession.isUnlocked) {
            lockState = LockState.LOCKED
        }
    }

    /**
     * Kilit açıldığında hızlı erişim kopyaları da tazelenir: Keystore anahtarı
     * kaybolmuş ya da kopya hiç üretilememişse kaydın aslından yeniden yazılır.
     * Kilitliyken biriken kullanım olayları da bu anda günlüğe taşınır.
     */
    private fun onUnlocked(key: javax.crypto.SecretKey) {
        VaultSession.unlock(key)
        lockState = LockState.UNLOCKED
        viewModelScope.launch(Dispatchers.IO) {
            repo.repairQuickCopies(key)
            usageLog.foldBuffer(key)
        }
    }

    private fun SecretKeySpec(bytes: ByteArray, algo: String) =
        javax.crypto.spec.SecretKeySpec(bytes, algo)

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
            keyManager.markMasterPasswordUsed()
            onUnlocked(key)
            // Teklif kurulumun hemen ardından yapılır: sonraya bırakılırsa
            // kullanıcı ana parolayı her açılışta yeniden yazmak zorunda kalır
            // ve özelliğin var olduğunu çoğu zaman hiç fark etmez.
            offerBiometric = true
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            busy = true
            val payload = withContext(Dispatchers.Default) {
                keyManager.unlockStage1(password.toCharArray())
            }
            busy = false
            if (payload == null) {
                error = "Parola yanlış"
                return@launch
            }
            // Ana parola tek başına açar: PIN yalnız parmak izi yolunu korur.
            keyManager.markMasterPasswordUsed()
            onUnlocked(SecretKeySpec(payload, "AES"))
        }
    }

    /**
     * İkinci aşama: parmak izinden gelen iç sargıyı PIN ile çözer.
     *
     * PIN doğru olmasına rağmen sargı açılmıyorsa kayıt **bayattır** (PIN
     * açılmadan önce yazılmış bir parmak izi kaydı ham anahtarı sarar). O
     * durumda kayıt silinir ve kullanıcıya yenilemesi söylenir — aksi hâlde
     * uygulama "PIN yanlış" diyerek açılmaz hâle gelirdi.
     */
    fun unlockWithPin(pin: String) {
        val inner = pendingInner ?: run { lockState = LockState.LOCKED; return }
        viewModelScope.launch {
            busy = true
            val key = withContext(Dispatchers.Default) { keyManager.unlockWithPin(inner, pin) }
            busy = false
            if (key != null) {
                pendingInner = null
                onUnlocked(key)
                return@launch
            }
            if (keyManager.verifyPin(pin)) {
                keyManager.clearBiometric()
                pendingInner = null
                lockState = LockState.LOCKED
                error = "Parmak izi kaydı bu PIN'den eskiydi; kayıt silindi. " +
                    "Ana parolanla aç ve parmak izini yeniden etkinleştir."
            } else {
                error = "PIN yanlış"
            }
        }
    }

    fun cancelPinStage() {
        pendingInner = null
        error = null
        lockState = LockState.LOCKED
    }

    /** Biyometrik/parola aşama-1 çıktısı; PIN açıksa ikinci aşamaya geçer. */
    fun onStage1Payload(payload: ByteArray) {
        if (keyManager.isPinEnabled) {
            pendingInner = payload
            lockState = LockState.NEEDS_PIN
        } else {
            onUnlocked(SecretKeySpec(payload, "AES"))
        }
    }

    fun onBiometricUnlocked(key: javax.crypto.SecretKey) = onUnlocked(key)

    /** Parmak izi kaydı için sarılacak içerik (PIN açıksa PIN gerekir). */
    fun biometricPayload(pin: String?): ByteArray? {
        val key = VaultSession.key() ?: return null
        return keyManager.wrapForBiometric(key, pin)
    }

    fun enablePin(password: String, pin: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                keyManager.enablePin(password.toCharArray(), pin)
            }
            busy = false
            refreshLockOptions()
            if (!ok) error = "PIN açılamadı: parola yanlış ya da PIN geçersiz (4-12 rakam)"
            else toast("PIN açıldı · parmak izini yeniden etkinleştir")
            onDone(ok)
        }
    }

    fun disablePin(password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                keyManager.disablePin(password.toCharArray())
            }
            busy = false
            refreshLockOptions()
            if (!ok) error = "PIN kapatılamadı: ana parola yanlış"
            else toast("PIN kapatıldı · parmak izini yeniden etkinleştir")
            onDone(ok)
        }
    }

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
        tagIds: List<Long> = emptyList(),
        noteKind: NoteKind = NoteKind.GENEL,
        onDone: () -> Unit
    ) {
        val key = VaultSession.key() ?: run { lockState = LockState.LOCKED; return }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.save(id, type, title, data, quick, key, tagIds, noteKind) }
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

    /** Sürükleme bitince yeni kullanıcı sırasını kaydeder. */
    fun saveManualOrder(idsInOrder: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.applyManualOrder(idsInOrder) }
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

    fun copyToClipboard(label: String, value: String, entryId: Long? = null) {
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
        entryId?.let { logUsage(it, UsageKind.KOPYALANDI, label) }
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
                    val allTags = repo.getAllTags()
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw Exception("Dosya açılamadı")
                    BackupManager.export(out, password.toCharArray(), all, allTags)
                }
                toast("Yedek kaydedildi")
            } catch (e: Exception) {
                error = "Yedekleme başarısız: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    /**
     * B-032: yapıştırılan şifreli zarf metninden içe aktarma. Aynı boru
     * ([BackupManager.import]); dosya yerine pano. Başarıda pano temizlenir —
     * şifreli de olsa zarfın klavye geçmişlerinde sürüklenmesine gerek yok.
     */
    fun importBackupText(text: String, password: String, replace: Boolean, onSuccess: () -> Unit = {}) {
        val key = VaultSession.key() ?: run {
            // Ekran kapanıp kasa kilitlenmiş olabilir; sessizce dönmek
            // "düğme çalışmıyor" gibi görünüyordu.
            error = "Kasa kilitlendi — ana parolanla açıp tekrar dene."
            lockState = LockState.LOCKED
            return
        }
        error = null
        val kirpik = BackupManager.normalize(text)
        if (kirpik.isEmpty()) { error = "Yapıştırılan metin boş"; return }
        if (kirpik.length > 1_000_000) { error = "Metin çok büyük — bu bir Vault zarfı olamaz"; return }
        // Dostça ön-tanı: en sık iki yanlış yapıştırma ayrı ayrı adlandırılır,
        // çünkü "geçersiz" demek kullanıcıya ne yapacağını söylemiyor.
        if (kirpik.startsWith("http://") || kirpik.startsWith("https://")) {
            error = "Bu bir bağlantı, zarfın kendisi değil. Bağlantıyı telefonun " +
                "tarayıcısında aç ve açılan METNİ kopyala."
            return
        }
        val zarfGibi = kirpik.startsWith("{") && kirpik.contains("\"app\"") &&
            kirpik.contains("\"vault\"")
        if (!zarfGibi) {
            error = "Bu, şifreli bir Vault zarfı değil — kopyalanan metin eksik ya da " +
                "farklı bir şey olabilir. Sayfadaki metnin TAMAMINI kopyala."
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) {
                    val imported = BackupManager.import(kirpik.byteInputStream(), password.toCharArray())
                    // Boş zarfta HİÇBİR depo işlemi yapma: 'Tümünü değiştir' ile
                    // boş zarf, kasanın tamamını silerdi (denetim: kritik).
                    if (imported.entries.isEmpty()) return@withContext 0
                    if (replace) repo.replaceAll(imported.entries, key, imported.tagColors)
                    else repo.addAll(imported.entries, key, imported.tagColors)
                    imported.entries.size
                }
                if (count == 0) {
                    error = "Zarf açıldı ama içinde kayıt yok — gönderen tarafta " +
                        "alanlar boş kalmış olabilir. Kasan olduğu gibi duruyor."
                    return@launch
                }
                clearClipboard()
                toast("$count kayıt eklendi · pano temizlendi")
                onSuccess()
            } catch (e: BackupManager.WrongPasswordException) {
                error = "Yedek parolası yanlış"
            } catch (e: BackupManager.InvalidFormatException) {
                error = "Geçersiz zarf — metin eksik kopyalanmış olabilir"
            } catch (e: Exception) {
                error = "İçe aktarma başarısız: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Panodaki metni okur (yalnız ön plandayken çalışır; izin gerekmez). */
    fun clipboardText(): String {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(getApplication()).toString()
    }

    private fun clearClipboard() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {
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
                    if (imported.entries.isEmpty()) return@withContext 0
                    if (replace) repo.replaceAll(imported.entries, key, imported.tagColors)
                    else repo.addAll(imported.entries, key, imported.tagColors)
                    imported.entries.size
                }
                if (count == 0) {
                    error = "Yedek açıldı ama içinde kayıt yok. Kasan olduğu gibi duruyor."
                    return@launch
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

    /** Parmak izi kaydı yazıldıktan sonra çağrılır: durum + geri bildirim. */
    fun onBiometricRegistered(basarili: Boolean) {
        refreshLockOptions()
        if (basarili) toast("Parmak izi etkinleştirildi")
        else error = "Parmak izi etkinleştirilemedi"
    }

    fun disableBiometric() {
        keyManager.clearBiometric()
        toast("Biyometrik kilit açma kapatıldı")
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    // ---- Kullanım günlüğü ----

    /**
     * Olayı arka planda kaydeder. Günlüğe yazamamak asıl işi (kopyalama,
     * yazma) hiçbir zaman engellemez; bu yüzden sonucu beklenmez.
     */
    fun logUsage(entryId: Long, kind: UsageKind, fieldLabel: String? = null, target: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            usageLog.record(
                UsageEvent(entryId, kind, System.currentTimeMillis(), fieldLabel, target)
            )
        }
    }

    suspend fun usageFor(entryId: Long): List<UsageEvent> {
        val key = VaultSession.key() ?: return emptyList()
        return withContext(Dispatchers.IO) { usageLog.eventsFor(entryId, key) }
    }

    suspend fun allUsage(): List<UsageEvent> {
        val key = VaultSession.key() ?: return emptyList()
        return withContext(Dispatchers.IO) { usageLog.events(key) }
    }

    /** Kilitliyken birikmiş, henüz günlüğe taşınmamış olay sayısı. */
    suspend fun pendingUsage(): Int = withContext(Dispatchers.IO) { usageLog.pendingCount() }

    fun clearUsageLog(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { usageLog.clearAll() }
            toast("Kullanım günlüğü silindi")
            onDone()
        }
    }
}
