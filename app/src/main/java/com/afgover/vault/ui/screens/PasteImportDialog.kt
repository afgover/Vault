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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afgover.vault.R
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
    // Hata metinleri onClick içinde, yani @Composable olmayan bir bağlamda
    // kullanılıyor; kaynaklar burada, gövdede çözülmek zorunda.
    val bosZarfHatasi = stringResource(R.string.paste_err_envelope_empty)
    val qrParolaHatasi = stringResource(R.string.paste_err_password_required_qr)
    val eksikParolaHatasi = stringResource(R.string.paste_err_password_missing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (qrdanGeldi) stringResource(R.string.paste_title_qr)
                else stringResource(R.string.paste_title_text)
            )
        },
        text = {
            Column {
                if (qrdanGeldi) {
                    Text(
                        stringResource(R.string.paste_qr_ready, text.length),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        stringResource(R.string.paste_intro),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text(stringResource(R.string.paste_label_envelope)) },
                        minLines = 3, maxLines = 5,
                        supportingText = {
                            if (text.isNotEmpty()) {
                                Text(stringResource(R.string.paste_char_count, text.length))
                            }
                        }
                    )
                    TextButton(onClick = { text = viewModel.clipboardText() }) {
                        Text(stringResource(R.string.paste_btn_clipboard))
                    }
                }
                SecretField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = stringResource(R.string.paste_label_backup_password)
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    SecimCipi(
                        secili = replace == 0,
                        onClick = { replace = 0 },
                        label = stringResource(R.string.paste_mode_merge),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SecimCipi(
                        secili = replace == 1,
                        onClick = { replace = 1 },
                        label = stringResource(R.string.paste_mode_replace)
                    )
                }
                if (replace == 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.paste_replace_warning),
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
                        text.isBlank() -> viewModel.error = bosZarfHatasi
                        pw.isEmpty() && qrdanGeldi -> viewModel.error = qrParolaHatasi
                        pw.isEmpty() -> viewModel.error = eksikParolaHatasi
                        else -> viewModel.importBackupText(text, pw, replace == 1) { onDismiss() }
                    }
                },
                enabled = !viewModel.busy
            ) {
                Text(
                    if (viewModel.busy) stringResource(R.string.paste_btn_importing)
                    else stringResource(R.string.paste_btn_import)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paste_btn_cancel)) }
        }
    )
}
