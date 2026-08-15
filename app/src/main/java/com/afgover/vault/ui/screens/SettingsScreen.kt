package com.afgover.vault.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afgover.vault.ui.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    canUseBiometric: Boolean,
    onBiometricEnable: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) exportUri = uri }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri = uri }

    fun autofillIsEnabled(): Boolean =
        context.getSystemService(AutofillManager::class.java)
            ?.let { it.isAutofillSupported && it.hasEnabledAutofillServices() } ?: false

    var autofillEnabled by remember { mutableStateOf(autofillIsEnabled()) }
    val autofillLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { autofillEnabled = autofillIsEnabled() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (viewModel.busy) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }

            Text("Yedekleme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Yedek dosyası AES-256 ile şifrelenir ve yalnızca yedek parolasıyla açılır. " +
                    "Dosyayı Drive, SD kart vb. istediğin yere kaydedebilirsin; telefon " +
                    "sıfırlansa bile dosya + parola ile her şey geri yüklenir.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("vault-$stamp.vaultbak")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Şifreli yedek al") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Yedekten geri yükle") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Güvenlik", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text("Kilitlenme", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Kasa, ekran kapandığı anda kilitlenir; anahtar bellekten silinir. " +
                    "Ekran açık kaldığı sürece parola veya parmak izi tekrar sorulmaz.\n\n" +
                    "Klavyede \"parolasız kullan\" işaretli kayıtlar kasa kilitliyken de " +
                    "kullanılabilir; işaretlemeyi her kaydın kendi ekranından açıp " +
                    "kapatabilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            if (canUseBiometric) {
                if (viewModel.keyManager.isBiometricEnabled) {
                    OutlinedButton(
                        onClick = { viewModel.disableBiometric() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Biyometrik kilit açmayı kapat") }
                } else {
                    OutlinedButton(
                        onClick = onBiometricEnable,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Biyometrik kilit açmayı etkinleştir") }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = { showChangePassword = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ana parolayı değiştir") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Otomatik doldurma", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (autofillEnabled) {
                    "Etkin. Uygulama ve tarayıcılardaki giriş/kart formlarına dokununca " +
                        "Vault kayıtların doldurma seçeneği olarak çıkar. Kasa kilitliyse " +
                        "önce kilit açma ekranı gelir; hiçbir değer kilitliyken sisteme verilmez."
                } else {
                    "Etkinleştirirsen uygulama ve tarayıcılardaki giriş/kart formlarına " +
                        "dokununca Vault kayıtların doğrudan doldurma seçeneği olarak çıkar. " +
                        "Ayrıca yeni girdiğin bilgileri Vault'a kaydetmeyi teklif eder.\n\n" +
                        "Aşağıdaki düğme sistem ayarlarını açar. Açılan listenin adı " +
                        "cihaza göre değişir (\"Tercih edilen servis\", \"Otomatik doldurma " +
                        "servisi\" ya da \"Şifreler, parolalar ve otomatik doldurma\"); " +
                        "o listeden Vault'u seçmen yeterli."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                        .setData(Uri.parse("package:${context.packageName}"))
                    runCatching { autofillLauncher.launch(intent) }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (autofillEnabled) "Otomatik doldurma ayarlarını aç"
                    else "Otomatik doldurmayı etkinleştir"
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Vault Klavyesi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Klavye eklentisini etkinleştirirsen herhangi bir uygulamada klavye " +
                    "değiştiriciden Vault Klavyesi'ni seçip kayıtlı bilgilerini doğrudan " +
                    "ilgili alana yazdırabilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Klavye ayarlarını aç") }
        }
    }

    // Yedek alma: parola iste
    exportUri?.let { uri ->
        PasswordDialog(
            title = "Yedek parolası belirle",
            description = "Bu parola olmadan yedek dosyası AÇILAMAZ. Ana parolanla aynı olabilir.",
            confirmField = true,
            onConfirm = { pw ->
                viewModel.exportBackup(uri, pw)
                exportUri = null
            },
            onDismiss = { exportUri = null }
        )
    }

    // Geri yükleme: parola + mod iste
    importUri?.let { uri ->
        ImportDialog(
            onConfirm = { pw, replace ->
                viewModel.importBackup(uri, pw, replace)
                importUri = null
            },
            onDismiss = { importUri = null }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            viewModel = viewModel,
            onDismiss = { showChangePassword = false }
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    confirmField: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it },
                    label = { Text("Parola") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (confirmField) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it },
                        label = { Text("Parola (tekrar)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pw) },
                enabled = pw.isNotEmpty() && (!confirmField || pw == pw2)
            ) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun ImportDialog(
    onConfirm: (password: String, replace: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var replace by remember { mutableIntStateOf(0) } // 0 = ekle, 1 = değiştir
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yedekten geri yükle") },
        text = {
            Column {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it },
                    label = { Text("Yedek parolası") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(
                        selected = replace == 0,
                        onClick = { replace = 0 },
                        label = { Text("Mevcuta ekle") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = replace == 1,
                        onClick = { replace = 1 },
                        label = { Text("Tümünü değiştir") }
                    )
                }
                if (replace == 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dikkat: mevcut tüm kayıtlar silinip yedektekiler yazılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pw, replace == 1) },
                enabled = pw.isNotEmpty()
            ) { Text("Geri yükle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun ChangePasswordDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ana parolayı değiştir") },
        text = {
            Column {
                OutlinedTextField(
                    value = old, onValueChange = { old = it },
                    label = { Text("Mevcut parola") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = new, onValueChange = { new = it },
                    label = { Text("Yeni parola") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = { Text("Yeni parola (tekrar)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                viewModel.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.changePassword(old, new, confirm, onDismiss) },
                enabled = old.isNotEmpty() && new.isNotEmpty() && !viewModel.busy
            ) { Text("Değiştir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
