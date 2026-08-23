package com.afgover.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.afgover.vault.core.PinLock
import com.afgover.vault.ui.VaultViewModel

/**
 * PIN aşaması (ikinci kapı). Buraya gelindiğinde ana parola ya da parmak izi
 * çoktan geçilmiştir; PIN olmadan `dataKey` **kriptografik olarak** elde
 * edilemez (bkz. [PinLock]).
 */
@Composable
fun PinScreen(viewModel: VaultViewModel) {
    var pin by remember { mutableStateOf("") }

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
                Icons.Filled.Pin,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("İkinci kapı", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "PIN kodunu gir. Bu kod olmadan kayıtlar çözülemez — " +
                "ana parola tek başına yetmez.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        SecretField(
            value = pin,
            onValueChange = { yeni ->
                if (yeni.all { it.isDigit() } && yeni.length <= PinLock.MAX_LENGTH) {
                    pin = yeni
                    viewModel.error = null
                }
            },
            label = "PIN (${PinLock.MIN_LENGTH}-${PinLock.MAX_LENGTH} rakam)",
            numeric = true
        )
        viewModel.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        if (viewModel.busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.unlockWithPin(pin) },
                enabled = pin.length >= PinLock.MIN_LENGTH,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Aç") }
            TextButton(onClick = { viewModel.cancelPinStage() }) { Text("Vazgeç") }
        }
    }
}
