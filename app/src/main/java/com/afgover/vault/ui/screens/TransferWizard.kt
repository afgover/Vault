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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afgover.vault.R
import com.afgover.vault.ui.theme.vaultButtonColors
import com.afgover.vault.core.PasswordGenerator
import com.afgover.vault.ui.VaultViewModel

/**
 * "Bilgisayardan aktar" sihirbazı (B-052): Mac'teki aktar.html ile buradaki
 * içe aktarmayı tek akışa bağlar. Parola burada ÜRETİLİR, Bluetooth ile
 * bilgisayardaki iki parola alanına yazılır ve dönüşte içe aktarmada hazır
 * bekler — kullanıcı hiçbir parola ezberlemez/yazmaz, yalnız zarfı yapıştırır.
 *
 * Parola kümesi bilinçli olarak harf+rakam (simge yok): harf ve rakamlar
 * US ile TR-Q düzenlerinde AYNI tuşlardadır (HidLayoutsTest bunu doğrular),
 * yani yanlış düzen seçilse bile parola bozulmadan yazılır. 20 karakter
 * harf+rakam ≈ 119 bit — üretilmiş tam entropi (SEC-016 şartı sağlanır).
 * Parola tek aktarımlıktır ve sihirbaz kapanınca bellekten gider: statik
 * anahtar takasındaki (SEC-017) hasat riski bu akışta hiç doğmaz.
 */
private fun aktarimParolasi(): String = PasswordGenerator.generate(
    PasswordGenerator.Options(length = 20, symbols = false, avoidAmbiguous = true)
)

@Composable
fun TransferWizardDialog(viewModel: VaultViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var parola by remember { mutableStateOf(aktarimParolasi()) }
    var showBt by remember { mutableStateOf(false) }
    var qrTarama by remember { mutableStateOf(false) }
    var zarf by remember { mutableStateOf("") }
    // "Tümünü değiştir" burada yok: bu akış bilgisayardan sır GETİRİR,
    // kasanın yerine geçmez. Bkz. PasteImportDialog'daki aynı gerekçe.
    val context = LocalContext.current

    // Tarayıcı açıkken sihirbaz çizilmez: AlertDialog kamera görüntüsünün
    // üstünde kalıyor ve kareyi göremez hâle getiriyordu.
    if (!qrTarama) {
    AlertDialog(
        onDismissRequest = { if (!viewModel.busy) onDismiss() },
        title = { Text(stringResource(R.string.transfer_title, step)) },
        text = {
            Column {
                if (step == 1) {
                    Text(
                        stringResource(R.string.transfer_step1_steps),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Text(
                            parola,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { parola = aktarimParolasi() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.transfer_regenerate)
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.transfer_password_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        colors = vaultButtonColors(),
                        onClick = { showBt = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.transfer_bt_write_both)) }
                } else {
                    Text(
                        stringResource(R.string.transfer_step2_steps),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = zarf, onValueChange = { zarf = it },
                        label = { Text(stringResource(R.string.transfer_envelope_label)) },
                        minLines = 3, maxLines = 5,
                        supportingText = {
                            if (zarf.isNotEmpty()) {
                                Text(stringResource(R.string.transfer_char_count, zarf.length))
                            }
                        }
                    )
                    // Zarf iki yoldan gelebilir: panodan yapıştırılarak ya da
                    // bilgisayardaki QR'dan okutularak. İkisi de AYNI aktarım
                    // parolasıyla çözülür (aşağıdaki içe aktarma `parola`yı
                    // kullanır), yani kullanıcı hiçbir parola yazmaz.
                    Row {
                        TextButton(onClick = { zarf = viewModel.clipboardText() }) {
                            Text(stringResource(R.string.transfer_from_clipboard))
                        }
                        TextButton(onClick = { qrTarama = true }) {
                            Text(stringResource(R.string.transfer_scan_qr))
                        }
                    }
                    viewModel.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                TextButton(onClick = { step = 2 }) {
                    Text(stringResource(R.string.transfer_password_typed_next))
                }
            } else {
                TextButton(
                    onClick = {
                        if (zarf.isBlank()) {
                            viewModel.error =
                                context.getString(R.string.transfer_envelope_empty)
                        } else {
                            viewModel.importBackupText(zarf, parola, replace = false) { onDismiss() }
                        }
                    },
                    enabled = !viewModel.busy
                ) {
                    Text(
                        if (viewModel.busy) stringResource(R.string.transfer_importing)
                        else stringResource(R.string.transfer_import)
                    )
                }
            }
        },
        dismissButton = {
            if (step == 2) {
                TextButton(onClick = { step = 1 }) { Text(stringResource(R.string.transfer_back)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_cancel)) }
            }
        }
    )

    }

    if (qrTarama) {
        QrScanScreen(
            onEnvelope = { okunan ->
                qrTarama = false
                zarf = okunan
            },
            onCancel = { qrTarama = false }
        )
    }

    if (showBt) {
        BtTypeDialog(
            label = stringResource(R.string.transfer_bt_label),
            value = parola + "\t" + parola,
            onDismiss = {
                showBt = false
                step = 2
            }
        )
    }
}
