package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afgover.vault.core.PasswordGenerator

/**
 * Şifre üretici. [onUse] verilirse "Kullan" butonu görünür (düzenleme
 * ekranındaki şifre alanını doldurur); her durumda "Kopyala" vardır.
 */
@Composable
fun GeneratorDialog(
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onUse: ((String) -> Unit)? = null
) {
    var options by remember { mutableStateOf(PasswordGenerator.Options()) }
    var password by remember { mutableStateOf(PasswordGenerator.generate(PasswordGenerator.Options())) }

    fun update(newOptions: PasswordGenerator.Options) {
        options = newOptions
        password = PasswordGenerator.generate(newOptions)
    }

    val entropy = PasswordGenerator.entropyBits(options)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Şifre üretici") },
        text = {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = password,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp)
                        )
                        IconButton(onClick = { update(options) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Yenile")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Uzunluk: ${options.length} • Güç: ~$entropy bit " +
                        if (entropy >= 90) "(çok güçlü)" else if (entropy >= 60) "(güçlü)" else "(zayıf)",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = options.length.toFloat(),
                    onValueChange = { update(options.copy(length = it.toInt())) },
                    valueRange = 8f..64f
                )
                Row {
                    FilterChip(
                        selected = options.upper,
                        onClick = { update(options.copy(upper = !options.upper)) },
                        label = { Text("A-Z") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = options.lower,
                        onClick = { update(options.copy(lower = !options.lower)) },
                        label = { Text("a-z") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = options.digits,
                        onClick = { update(options.copy(digits = !options.digits)) },
                        label = { Text("0-9") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = options.symbols,
                        onClick = { update(options.copy(symbols = !options.symbols)) },
                        label = { Text("!@#") }
                    )
                }
                FilterChip(
                    selected = options.avoidAmbiguous,
                    onClick = { update(options.copy(avoidAmbiguous = !options.avoidAmbiguous)) },
                    label = { Text("Karışan karakterleri eleme (l, 1, O, 0)") }
                )
            }
        },
        confirmButton = {
            if (onUse != null) {
                TextButton(onClick = { onUse(password); onDismiss() }) { Text("Kullan") }
            } else {
                TextButton(onClick = { onCopy(password) }) { Text("Kopyala") }
            }
        },
        dismissButton = {
            if (onUse != null) {
                TextButton(onClick = { onCopy(password) }) { Text("Kopyala") }
            } else {
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        }
    )
}
