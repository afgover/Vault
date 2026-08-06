package com.afgover.vault.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.afgover.vault.bt.BtHidManager
import com.afgover.vault.bt.HidLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Seçilen değeri Bluetooth klavye olarak bilgisayara yazar.
 * Akış: izin → HID kaydı → eşleşmiş cihaz seç → bağlan → 3 sn geri sayım → yaz.
 */
@Composable
fun BtTypeDialog(
    label: String,
    value: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (!BtHidManager.isSupported) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Desteklenmiyor") },
            text = { Text("Bluetooth klavye özelliği Android 9 ve üzeri gerektirir.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } }
        )
        return
    }

    val needsPermission = Build.VERSION.SDK_INT >= 31
    var permissionGranted by remember {
        mutableStateOf(
            !needsPermission || ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && Build.VERSION.SDK_INT >= 28) {
            BtHidManager.start(context)
        }
    }
    DisposableEffect(Unit) {
        onDispose { BtHidManager.stop() }
    }

    val state by BtHidManager.state.collectAsState()
    val error by BtHidManager.lastError.collectAsState()

    val prefs = remember {
        context.getSharedPreferences("vault_settings", Context.MODE_PRIVATE)
    }
    var layout by remember {
        mutableStateOf(
            runCatching {
                HidLayouts.Layout.valueOf(prefs.getString("pc_layout", "TR") ?: "TR")
            }.getOrDefault(HidLayouts.Layout.TR)
        )
    }
    var countdown by remember { mutableIntStateOf(0) }
    var typing by remember { mutableStateOf(false) }
    var untypedWarning by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!typing) onDismiss() },
        title = { Text("Bilgisayara yaz: $label") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!permissionGranted) {
                    Text("Bluetooth izni gerekli. İzin vermeden bu özellik çalışamaz.")
                    return@Column
                }

                when (val s = state) {
                    is BtHidManager.State.Idle,
                    is BtHidManager.State.Registering ->
                        Text("Bluetooth klavye hazırlanıyor…")

                    is BtHidManager.State.Unsupported ->
                        Text(error ?: "Bu telefon HID profilini desteklemiyor.")

                    is BtHidManager.State.Ready -> {
                        Text(
                            "Bilgisayarını seç (önce telefonla Bluetooth'tan eşleştirilmiş olmalı):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        val devices = remember(state) { BtHidManager.bondedDevices() }
                        if (devices.isEmpty()) {
                            Text(
                                "Eşleştirilmiş cihaz yok. Telefonun Bluetooth ayarlarından " +
                                    "bilgisayarınla eşleştir, sonra tekrar dene."
                            )
                        }
                        devices.forEach { (name, device) ->
                            OutlinedButton(
                                onClick = { BtHidManager.connect(device) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) { Text(name) }
                        }
                    }

                    is BtHidManager.State.Connecting ->
                        Text("${s.name} cihazına bağlanılıyor…")

                    is BtHidManager.State.Connected -> {
                        Text("✓ ${s.name} bağlı", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Bilgisayarın klavye düzeni:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            HidLayouts.Layout.entries.forEach { l ->
                                FilterChip(
                                    selected = layout == l,
                                    onClick = {
                                        layout = l
                                        prefs.edit().putString("pc_layout", l.name).apply()
                                    },
                                    label = { Text(l.label) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        when {
                            typing -> Text("Yazılıyor…")
                            countdown > 0 -> Text(
                                "$countdown saniye içinde yazılacak — bilgisayarda imleci " +
                                    "ilgili alana getir!",
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text(
                                "Bilgisayarda imleci yazılacak alana getir, sonra aşağıdaki " +
                                    "butona bas. 3 saniye sonra yazma başlar.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                untypedWarning?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state !is BtHidManager.State.Unsupported && error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (state is BtHidManager.State.Connected) {
                Button(
                    enabled = !typing && countdown == 0,
                    onClick = {
                        scope.launch {
                            untypedWarning = null
                            for (i in 3 downTo 1) {
                                countdown = i
                                delay(1000)
                            }
                            countdown = 0
                            typing = true
                            val untyped = withContext(Dispatchers.IO) {
                                BtHidManager.typeText(value, layout)
                            }
                            typing = false
                            if (untyped.isEmpty()) {
                                onDismiss()
                            } else {
                                untypedWarning =
                                    "Şu karakterler bu düzende yazılamadı: " +
                                        untyped.distinct().joinToString(" ") +
                                        " — US düzenini deneyebilirsin."
                            }
                        }
                    }
                ) { Text(if (countdown > 0) "$countdown…" else "Yaz") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !typing) { Text("Kapat") }
        }
    )
}
