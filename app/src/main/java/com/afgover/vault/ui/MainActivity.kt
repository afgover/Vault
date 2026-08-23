package com.afgover.vault.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.afgover.vault.data.EntryType
import com.afgover.vault.ui.screens.EditScreen
import com.afgover.vault.ui.screens.DetailScreen
import com.afgover.vault.ui.screens.HomeScreen
import com.afgover.vault.ui.screens.SettingsScreen
import com.afgover.vault.ui.screens.UnlockScreen
import com.afgover.vault.ui.theme.VaultTheme

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ekran görüntüsü ve son uygulamalar önizlemesini engelle
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            VaultTheme {
                VaultRoot(
                    viewModel = viewModel,
                    canUseBiometric = ::canUseBiometric,
                    onBiometricUnlock = ::biometricUnlock,
                    onBiometricEnable = ::biometricEnable
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLockState()
    }

    private fun canUseBiometric(): Boolean = BiometricAuth.canUse(this)

    private fun biometricUnlock() {
        BiometricAuth.unlock(this, viewModel.keyManager) { viewModel.onBiometricUnlocked(it) }
    }

    private fun biometricEnable() {
        val key = com.afgover.vault.core.VaultSession.key() ?: return
        val cipher = try {
            viewModel.keyManager.biometricEncryptCipher()
        } catch (e: Exception) {
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher ?: return
                    viewModel.keyManager.storeBiometricWrappedKey(c, key)
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biyometrik kilit açmayı etkinleştir")
            .setNegativeButtonText("Vazgeç")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

sealed interface Nav {
    data object Home : Nav
    data class Detail(val id: Long) : Nav
    data class Edit(val id: Long, val type: EntryType) : Nav
    data object Settings : Nav
}

@Composable
fun VaultRoot(
    viewModel: VaultViewModel,
    canUseBiometric: () -> Boolean,
    onBiometricUnlock: () -> Unit,
    onBiometricEnable: () -> Unit
) {
    // Basit gezinme: durum bellekte tutulur; süreç yeniden başlarsa Home'a döner.
    var nav by remember { mutableStateOf<Nav>(Nav.Home) }

    // Sistem geri tuşu üst çubuktaki ok ile aynı yere döner; uygulamadan yalnız
    // Home'dayken çıkılır. (Ok'suz sistem geri'si doğrudan uygulamayı kapatıyordu.)
    androidx.activity.compose.BackHandler(
        enabled = viewModel.lockState == LockState.UNLOCKED && nav != Nav.Home
    ) {
        nav = when (val screen = nav) {
            is Nav.Edit -> if (screen.id == 0L) Nav.Home else Nav.Detail(screen.id)
            else -> Nav.Home
        }
    }

    when (viewModel.lockState) {
        LockState.NEEDS_SETUP, LockState.LOCKED -> {
            LaunchedEffect(Unit) { nav = Nav.Home }
            UnlockScreen(
                viewModel = viewModel,
                canUseBiometric = canUseBiometric(),
                onBiometricUnlock = onBiometricUnlock
            )
        }

        LockState.UNLOCKED -> when (val screen = nav) {
            is Nav.Home -> HomeScreen(
                viewModel = viewModel,
                onOpen = { nav = Nav.Detail(it) },
                onAdd = { type -> nav = Nav.Edit(0L, type) },
                onSettings = { nav = Nav.Settings }
            )

            is Nav.Detail -> DetailScreen(
                viewModel = viewModel,
                id = screen.id,
                onEdit = { id, type -> nav = Nav.Edit(id, type) },
                onBack = { nav = Nav.Home }
            )

            is Nav.Edit -> EditScreen(
                viewModel = viewModel,
                id = screen.id,
                type = screen.type,
                onBack = { nav = if (screen.id == 0L) Nav.Home else Nav.Detail(screen.id) },
                onDeleted = { nav = Nav.Home }
            )

            is Nav.Settings -> SettingsScreen(
                viewModel = viewModel,
                canUseBiometric = canUseBiometric(),
                onBiometricEnable = onBiometricEnable,
                onBack = { nav = Nav.Home }
            )
        }
    }
}
