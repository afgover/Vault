package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    val biometricAvailable =
        !isSetup && canUseBiometric && viewModel.keyManager.isBiometricEnabled

    // Kilit ekranı açılır açılmaz biyometrik istemi göster
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable) onBiometricUnlock()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isSetup) "Vault'a hoş geldin" else "Vault kilitli",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSetup)
                "Bir ana parola belirle. Tüm verilerin bu paroladan türetilen anahtarla şifrelenir. Bu parolayı unutursan verilerine ERİŞİLEMEZ."
            else
                "Devam etmek için ana parolanı gir.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.error = null },
            label = { Text(if (isSetup) "Ana parola" else "Parola") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isSetup) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; viewModel.error = null },
                label = { Text("Parola (tekrar)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
                Text(if (isSetup) "Kasayı oluştur" else "Kilidi aç")
            }
            if (biometricAvailable) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBiometricUnlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Biyometrik ile aç")
                }
            }
        }
    }
}
