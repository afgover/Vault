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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    var zarf by remember { mutableStateOf("") }
    var replace by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (!viewModel.busy) onDismiss() },
        title = { Text("Bilgisayardan aktar · adım $step/2") },
        text = {
            Column {
                if (step == 1) {
                    Text(
                        "1. Bilgisayarda aktar.html'i aç, imleci \"Yedek parolası\" " +
                            "alanına getir.\n2. Aşağıdaki düğmeyle parolayı iki alana " +
                            "birden yazdır (araya Tab girer).",
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
                            Icon(Icons.Filled.Refresh, contentDescription = "Yeni parola üret")
                        }
                    }
                    Text(
                        "Üretilmiş, tek aktarımlık parola — ezberlemene gerek yok, " +
                            "adım 2'de otomatik kullanılacak. Harf+rakam: klavye düzeni " +
                            "farkından etkilenmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showBt = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("💻 Bluetooth ile iki alana yaz") }
                } else {
                    Text(
                        "Bilgisayarda: içeriği yapıştır → \"Şifrele ve yayınla\" → " +
                            "telefonla QR'ı okut → sayfada 📋 Kopyala. Sonra buraya dön " +
                            "ve zarfı al. Parola sihirbazda hazır — girmeyeceksin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = zarf, onValueChange = { zarf = it },
                        label = { Text("Şifreli zarf") },
                        minLines = 3, maxLines = 5,
                        supportingText = {
                            if (zarf.isNotEmpty()) Text("${zarf.length} karakter")
                        }
                    )
                    TextButton(onClick = { zarf = viewModel.clipboardText() }) {
                        Text("Panodan al")
                    }
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
            }
        },
        confirmButton = {
            if (step == 1) {
                TextButton(onClick = { step = 2 }) { Text("Parola yazıldı, devam →") }
            } else {
                TextButton(
                    onClick = {
                        if (zarf.isBlank()) {
                            viewModel.error = "Şifreli zarf alanı boş — " +
                                "bilgisayarda yayınla, QR'ı okut ve \"Panodan al\"a bas."
                        } else {
                            viewModel.importBackupText(zarf, parola, replace == 1) { onDismiss() }
                        }
                    },
                    enabled = !viewModel.busy
                ) { Text(if (viewModel.busy) "Aktarılıyor…" else "İçe aktar") }
            }
        },
        dismissButton = {
            if (step == 2) {
                TextButton(onClick = { step = 1 }) { Text("← Geri") }
            } else {
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        }
    )

    if (showBt) {
        BtTypeDialog(
            label = "Aktarım parolası (iki alana, Tab ile)",
            value = parola + "\t" + parola,
            onDismiss = {
                showBt = false
                step = 2
            }
        )
    }
}
