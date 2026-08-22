package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afgover.vault.ui.VaultViewModel

/**
 * B-032 — Metinden içe aktarma: relay sayfasındaki "Kopyala" ile alınan
 * şifreli zarf buraya yapıştırılır; dosya indirmeye gerek kalmaz. Aynı
 * boruya gider ve başarıda pano temizlenir.
 */
@Composable
fun PasteImportDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var replace by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metinden içe aktar") },
        text = {
            Column {
                Text(
                    "Sayfadaki \"Kopyala\" ile aldığın şifreli metni yapıştır. " +
                        "Metin şifrelidir; parolasını bir sonraki alana gireceksin.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Şifreli zarf") },
                    minLines = 3, maxLines = 5,
                    supportingText = {
                        if (text.isNotEmpty()) Text("${text.length} karakter")
                    }
                )
                TextButton(onClick = { text = viewModel.clipboardText() }) {
                    Text("Panodan al")
                }
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it },
                    label = { Text("Yedek parolası") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    SecimCipi(
                        secili = replace == 0,
                        onClick = { replace = 0 },
                        label = "Mevcuta ekle",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SecimCipi(
                        secili = replace == 1,
                        onClick = { replace = 1 },
                        label = "Tümünü değiştir"
                    )
                }
                if (replace == 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dikkat: mevcut tüm kayıtlar silinip zarftakiler yazılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                viewModel.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.importBackupText(text, pw, replace == 1) { onDismiss() }
                },
                enabled = text.isNotBlank() && pw.isNotEmpty() && !viewModel.busy
            ) { Text(if (viewModel.busy) "Aktarılıyor…" else "İçe aktar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
