package com.afgover.vault.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.afgover.vault.R
import com.afgover.vault.core.KeyManager
import javax.crypto.SecretKey

/**
 * Biyometrik kilit açma; hem ana uygulama hem de otomatik doldurma
 * kimlik doğrulama ekranı tarafından kullanılır.
 */
object BiometricAuth {

    fun canUse(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

    fun unlock(
        activity: FragmentActivity,
        keyManager: KeyManager,
        onError: (String) -> Unit = {},
        onPayload: (ByteArray) -> Unit
    ) {
        // Parmak izi anahtarı geçersizleşmişse (yeni parmak izi kaydı, cihaz
        // değişikliği) cipher null döner. Eskiden sessizce return ediliyordu:
        // düğme hiçbir şey yapmıyordu (denetim). Şimdi bayat kayıt temizlenir
        // ve kullanıcı paroladan devam etmeye yönlendirilir.
        val cipher = keyManager.biometricDecryptCipher()
        if (cipher == null) {
            keyManager.clearBiometric()
            onError(activity.getString(R.string.main_biometric_invalidated))
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher ?: return
                    val payload = keyManager.unlockWithBiometricStage1(c)
                    if (payload == null) {
                        onError(activity.getString(R.string.main_biometric_decrypt_failed))
                    } else {
                        onPayload(payload)
                    }
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    // Kullanıcı iptali (negatif düğme / geri) sessiz geçer.
                    if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                        code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        code != BiometricPrompt.ERROR_CANCELED) {
                        onError(msg.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.main_unlock_title))
            .setNegativeButtonText(activity.getString(R.string.main_use_password))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
