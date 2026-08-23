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
    initialText: String = "",
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    // QR'dan gelindiğinde zarf hazırdır; ekran yalnız parolayı istemeli.
    val qrdanGeldi = initialText.isNotEmpty()
    var pw by remember { mutableStateOf("") }
    var replace by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (qrdanGeldi) "QR ile aktar" else "Metinden içe aktar") },
        text = {
            Column {
                if (qrdanGeldi) {
                    Text(
                        "✓ QR okundu · ${text.length} karakterlik şifreli zarf alındı.\n" +
                            "Şimdi yalnız **yedek parolasını** gir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Sayfadaki metni yapıştır ya da \"Panodan al\"a bas. " +
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
                }
                SecretField(value = pw, onValueChange = { pw = it }, label = "Yedek parolası")
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
                    // Düğme boş alanda da basılabilir: sessizce devre dışı
                    // kalmak "tepki yok" olarak okunuyordu, oysa kullanıcının
                    // ihtiyacı NEDEN olmadığını görmek.
                    when {
                        text.isBlank() ->
                            viewModel.error = "Şifreli zarf alanı boş — " +
                                "\"Panodan al\" ile yapıştır."
                        pw.isEmpty() && qrdanGeldi ->
                            viewModel.error = "Yedek parolası gerekli: zarfı " +
                                "bilgisayarda şifrelerken girdiğin parola."
                        pw.isEmpty() ->
                            viewModel.error = "Yedek parolası girilmedi."
                        else -> viewModel.importBackupText(text, pw, replace == 1) { onDismiss() }
                    }
                },
                enabled = !viewModel.busy
            ) { Text(if (viewModel.busy) "Aktarılıyor…" else "İçe aktar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
