package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afgover.vault.R
import com.afgover.vault.ui.LockState
import com.afgover.vault.ui.VaultViewModel

@Composable
fun UnlockScreen(
    viewModel: VaultViewModel,
    canUseBiometric: Boolean,
    onBiometricUnlock: () -> Unit
) {
    val isSetup = viewModel.lockState == LockState.NEEDS_SETUP
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    // Hatırlatıcı dolduysa bu açılışta parola istenir: amaç güvenlik değil,
    // parolanın unutulmasını önlemek (kullanıcının seçtiği süre; varsayılan
    // süresiz).
    val hatirlatmaZamani = viewModel.masterPasswordDue
    val biometricAvailable =
        !isSetup && canUseBiometric && viewModel.biometricEnabled &&
            !hatirlatmaZamani

    // Kilit ekranı açılır açılmaz biyometrik istemi göster — ama YALNIZ BİR
    // KEZ. Aksi hâlde PIN aşamasından geri dönüldüğünde istem yeniden açılıp
    // kullanıcıyı döngüye sokar (parolaya hiç geçemez).
    var biometricIstendi by remember { mutableStateOf(false) }
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !biometricIstendi) {
            biometricIstendi = true
            onBiometricUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(
                if (isSetup) R.string.unlock_welcome_title else R.string.unlock_locked_title
            ),
            style = MaterialTheme.typography.headlineSmall
        )
        if (isSetup) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.unlock_slogan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isSetup) R.string.unlock_setup_desc else R.string.unlock_enter_desc
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hatirlatmaZamani) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.unlock_master_reminder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))

        SecretField(
            value = password,
            onValueChange = { password = it; viewModel.error = null },
            label = stringResource(
                if (isSetup) R.string.unlock_master_password_label
                else R.string.unlock_password_label
            )
        )
        if (isSetup) {
            Spacer(Modifier.height(8.dp))
            SecretField(
                value = confirm,
                onValueChange = { confirm = it; viewModel.error = null },
                label = stringResource(R.string.unlock_password_again_label)
            )
        }

        viewModel.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        if (viewModel.busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isSetup) viewModel.setup(password, confirm)
                    else viewModel.unlock(password)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (isSetup) {
                            if (viewModel.restoreAfterSetup) R.string.unlock_create_and_restore
                            else R.string.unlock_create_vault
                        } else R.string.unlock_open_button
                    )
                )
            }
            if (isSetup) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.restoreAfterSetup = !viewModel.restoreAfterSetup },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (viewModel.restoreAfterSetup) R.string.unlock_restore_selected
                            else R.string.unlock_have_backup
                        )
                    )
                }
                if (viewModel.restoreAfterSetup) {
                    Text(
                        stringResource(R.string.unlock_restore_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (biometricAvailable) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBiometricUnlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  " + stringResource(R.string.unlock_biometric_button))
                }
            }
        }
    }
}
