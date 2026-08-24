package com.afgover.vault.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.os.CancellationSignal
import com.afgover.vault.R
import com.afgover.vault.VaultApp
import com.afgover.vault.core.VaultSession
import com.afgover.vault.data.EntryData
import com.afgover.vault.data.EntryType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sistemin otomatik doldurma servisi: herhangi bir uygulamada veya tarayıcıda
 * bir giriş/kart formuna dokunulduğunda Vault kayıtlarını doldurma seçeneği
 * olarak sunar.
 *
 * Kasa kilitliyse hiçbir değer sisteme verilmez; önce [AutofillUnlockActivity]
 * ile kilit açılır, doldurma seçenekleri ancak ondan sonra üretilir.
 */
class VaultAutofillService : AutofillService() {

    // Beklenmedik bir istisna (şifreleme, boyut sınırı, veritabanı) süreci
    // çökertip SaveCallback/FillCallback sözleşmesini bozmasın: yakala ve yut,
    // her giriş noktası zaten kendi hata yolunu bildiriyor (denetim).
    private val hata = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + hata)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val parsed = StructureParser.parse(structure)
        // Kendi ekranlarımızı doldurmaya çalışma
        if (parsed.fields.isEmpty() || parsed.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val key = VaultSession.key()
        if (key == null) {
            callback.onSuccess(AutofillResponses.authResponse(this, parsed, unlockSender()))
            return
        }

        var cancelled = false
        cancellationSignal.setOnCancelListener { cancelled = true }

        scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    VaultApp.from(this@VaultAutofillService).repository.getAllDecrypted(key)
                }
                if (cancelled) return@launch
                callback.onSuccess(AutofillResponses.fillResponse(this@VaultAutofillService, parsed, entries))
            } catch (e: Exception) {
                if (!cancelled) callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onFailure(getString(R.string.backup_autofill_form_unreadable))
            return
        }
        val parsed = StructureParser.parse(structure)
        val key = VaultSession.key()
        if (key == null) {
            callback.onFailure(getString(R.string.backup_autofill_locked_save))
            return
        }

        val username = parsed.valueOf(FieldKind.USERNAME).orEmpty()
        val password = parsed.valueOf(FieldKind.PASSWORD).orEmpty()
        val cardNumber = parsed.valueOf(FieldKind.CARD_NUMBER).orEmpty()
        val isCard = cardNumber.isNotEmpty()
        if (!isCard && username.isEmpty() && password.isEmpty()) {
            callback.onFailure(getString(R.string.backup_autofill_nothing_to_save))
            return
        }

        val expiry = parsed.valueOf(FieldKind.CARD_EXPIRY)
            ?: listOfNotNull(
                parsed.valueOf(FieldKind.CARD_EXP_MONTH),
                parsed.valueOf(FieldKind.CARD_EXP_YEAR)
            ).takeIf { it.size == 2 }?.joinToString("/")
            ?: ""

        val data = if (isCard) {
            EntryData(
                cardholder = parsed.valueOf(FieldKind.CARDHOLDER).orEmpty(),
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = parsed.valueOf(FieldKind.CVV).orEmpty(),
                url = parsed.webDomain.orEmpty()
            )
        } else {
            EntryData(
                username = username,
                password = password,
                url = parsed.webDomain.orEmpty()
            )
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VaultApp.from(this@VaultAutofillService).repository.save(
                        id = 0L,
                        type = if (isCard) EntryType.CARD else EntryType.LOGIN,
                        title = sourceLabel(parsed),
                        data = data,
                        // Otomatik doldurmadan gelen kayıtlar korumalı başlar.
                        quick = false,
                        key = key
                    )
                }
                callback.onSuccess()
            } catch (e: Exception) {
                callback.onFailure(
                    getString(R.string.backup_autofill_save_failed, e.message.orEmpty())
                )
            }
        }
    }

    /** Kaydın başlığı: site adı, yoksa uygulamanın görünen adı. */
    private fun sourceLabel(parsed: ParsedStructure): String {
        parsed.webDomain?.takeIf { it.isNotBlank() }?.let { return it.removePrefix("www.") }
        return try {
            val info = packageManager.getApplicationInfo(parsed.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            parsed.packageName
        }
    }

    private fun unlockSender() = PendingIntent.getActivity(
        this,
        REQUEST_CODE.incrementAndGet(),
        Intent(this, AutofillUnlockActivity::class.java),
        if (Build.VERSION.SDK_INT >= 31) {
            // Sistem, kimlik doğrulama sonucunu bu intent'e ekler
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        } else {
            PendingIntent.FLAG_CANCEL_CURRENT
        }
    ).intentSender

    private companion object {
        val REQUEST_CODE = AtomicInteger(1000)
    }
}
