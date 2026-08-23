package com.afgover.vault.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
        onPayload: (ByteArray) -> Unit
    ) {
        val cipher = keyManager.biometricDecryptCipher() ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher ?: return
                    val payload = keyManager.unlockWithBiometricStage1(c) ?: return
                    onPayload(payload)
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Vault kilidini aç")
            .setNegativeButtonText("Parola kullan")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
