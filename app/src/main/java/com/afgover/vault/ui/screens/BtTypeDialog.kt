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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.afgover.vault.bt.BtHidManager
import com.afgover.vault.bt.HidLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Aynı geri sayım + yazma akışından geçen üç gönderim. */
private enum class Gonderim { DUZEN_TESTI, HIZ_TESTI, DEGER }

/** Bu uzunluğa kadar olan değerler yazılınca diyalog kendi kapanır. */
private const val KISA_DEGER = 100

/** "42 sn" / "2 dk 15 sn". */
private fun sureMetni(ms: Long): String {
    val sn = ((ms + 999L) / 1000L).toInt()
    return if (sn < 60) "$sn sn" else "${sn / 60} dk ${sn % 60} sn"
}

/**
 * Seçilen değeri Bluetooth klavye olarak bilgisayara yazar.
 * Akış: izin → HID kaydı → eşleşmiş cihaz seç → bağlan → 3 sn geri sayım → yaz.
 *
 * Uzun sırlarda (5.000+ karakter) yazma dakikalar sürebildiği için hız
 * seçilebilir; hızın bu bilgisayarda güvenli olduğu "hız testi" ile ölçülür.
 */
@Composable
fun BtTypeDialog(
    label: String,
    value: String,
    onDismiss: () -> Unit,
    /** Asıl değer yazıldığında hedef bilgisayarın adıyla çağrılır (günlük). */
    onTyped: (target: String) -> Unit = {}
) {
    val context = LocalContext.current

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

    /** Yazma arka planda blokluyor; durdurma bu bayrakla bildirilir. */
    val iptal = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            iptal.set(true)
            BtHidManager.stop()
        }
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
    var speed by remember {
        mutableStateOf(
            runCatching {
                BtHidManager.Speed.valueOf(prefs.getString("pc_speed", "FAST") ?: "FAST")
            }.getOrDefault(BtHidManager.Speed.FAST)
        )
    }
    var countdown by remember { mutableIntStateOf(0) }
    var typing by remember { mutableStateOf(false) }

    // Uzun yazma dakikalar sürebiliyor ve kullanıcı bilgisayara bakar; ekran
    // zaman aşımı yazmayı yarıda kesip kasayı kilitler (denetim). Yazma/geri
    // sayım boyunca ekranı açık tut.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(typing || countdown > 0) {
        val w = (view.context as? android.app.Activity)?.window
        if (typing || countdown > 0) {
            w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    var untypedWarning by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var testTyped by remember { mutableStateOf(false) }
    var hizSonucu by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Gonderim?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    // Üç gönderim de aynı akıştan geçer: geri sayım → yaz → sonucu bildir.
    LaunchedEffect(pending) {
        val kind = pending ?: return@LaunchedEffect
        val metin = when (kind) {
            Gonderim.DUZEN_TESTI -> HidLayouts.LAYOUT_TEST_TEXT
            Gonderim.HIZ_TESTI -> HidLayouts.SPEED_TEST_TEXT
            Gonderim.DEGER -> value
        }
        untypedWarning = null
        summary = null
        progress = 0
        total = metin.length
        iptal.set(false)
        for (i in 3 downTo 1) {
            countdown = i
            delay(1000)
        }
        countdown = 0
        typing = true
        val sonuc = withContext(Dispatchers.IO) {
            BtHidManager.typeText(
                metin, layout, speed,
                onProgress = { progress = it },
                isCancelled = { !isActive || iptal.get() }
            )
        }
        typing = false
        pending = null

        when {
            sonuc.cancelled -> summary =
                "Durduruldu: ${sonuc.typed} / ${metin.length} karakter yazıldı. " +
                    "Bilgisayardaki metin EKSİK — sil ve baştan yaz."

            sonuc.aborted -> summary =
                "Yazma yarıda kesildi: ${sonuc.typed} / ${metin.length} karakter. " +
                    "Bilgisayardaki metin EKSİK — sil, daha yavaş bir hız seçip tekrar dene."

            else -> {
                if (kind == Gonderim.DUZEN_TESTI) testTyped = true
                if (kind == Gonderim.HIZ_TESTI) {
                    hizSonucu = "${sonuc.typed} karakter, ${sureMetni(sonuc.elapsedMs)} " +
                        "(${sonuc.charsPerSecond} karakter/sn)"
                }
                if (kind == Gonderim.DEGER) {
                    onTyped((state as? BtHidManager.State.Connected)?.name ?: "bilinmeyen cihaz")
                }
                if (sonuc.untyped.isNotEmpty()) {
                    untypedWarning = "Şu karakterler bu düzende yazılamadı: " +
                        sonuc.untyped.distinct().joinToString(" ") +
                        " — diğer düzeni deneyebilirsin."
                } else if (kind == Gonderim.DEGER) {
                    if (value.length <= KISA_DEGER) {
                        onDismiss()
                    } else {
                        summary = "✓ ${sonuc.typed} karakter yazıldı — " +
                            "${sureMetni(sonuc.elapsedMs)}, ${sonuc.charsPerSecond} karakter/sn. " +
                            "Bilgisayarda karakter sayısını doğrula."
                    }
                }
            }
        }
    }

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
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "BİLGİSAYARIN klavye düzeni (telefonun değil):",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Tuş kodlarını bilgisayar yorumlar — yanlış düzen seçersen " +
                                "@ \" ? gibi karakterler SESSİZCE başka karaktere dönüşür.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        Row {
                            HidLayouts.Layout.entries.forEach { l ->
                                SecimCipi(
                                    secili = layout == l,
                                    onClick = {
                                        layout = l
                                        testTyped = false
                                        prefs.edit().putString("pc_layout", l.name).apply()
                                    },
                                    label = l.label,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        Text(
                            "Seçili düzen: ${layout.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        val riskli = remember(value, layout) {
                            HidLayouts.layoutSensitiveChars(value)
                        }
                        if (riskli.isNotEmpty() && !testTyped) {
                            Text(
                                "Bu metinde düzene duyarlı karakterler var: " +
                                    riskli.joinToString(" ") +
                                    " — göndermeden önce test yazmanı öneririm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        OutlinedButton(
                            enabled = !typing && countdown == 0,
                            onClick = { pending = Gonderim.DUZEN_TESTI },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (testTyped) "✓ Test yazıldı — tekrar dene" else "🧪 Önce test yaz (önerilir)") }
                        if (testTyped) {
                            Text(
                                "Bilgisayarda TAM OLARAK şu çıkmış olmalı:\n" +
                                    HidLayouts.LAYOUT_TEST_TEXT +
                                    "\nFarklıysa üstteki diğer düzeni seç ve testi tekrarla.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Yazma hızı:", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tuşlar tek tek gönderilir. Hız, bağlantının taşıyabileceğinden " +
                                "yüksek olursa yazma durur (karakter kaybolmaz) — hangisinin " +
                                "çalıştığını hız testiyle ölç.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Row {
                            BtHidManager.Speed.entries.forEach { h ->
                                SecimCipi(
                                    secili = speed == h,
                                    onClick = {
                                        speed = h
                                        hizSonucu = null
                                        prefs.edit().putString("pc_speed", h.name).apply()
                                    },
                                    label = h.label,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        Text(
                            "${value.length} karakter ≈ " +
                                sureMetni(value.length * speed.perCharMs),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            enabled = !typing && countdown == 0,
                            onClick = { pending = Gonderim.HIZ_TESTI },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("⏱ Hız testi (${HidLayouts.SPEED_TEST_TEXT.length} karakter)") }
                        hizSonucu?.let {
                            Text(
                                "$it\nBilgisayarda boşlukla ayrılmış 10 grup olmalı ve her biri " +
                                    "birebir ${HidLayouts.SPEED_TEST_BLOCK} — biri bile farklıysa " +
                                    "bir alt hızı seç ve testi tekrarla.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        when {
                            typing -> {
                                Text("Yazılıyor… $progress / $total karakter")
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (total == 0) 0f else progress / total.toFloat()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "~" + sureMetni((total - progress) * speed.perCharMs) +
                                        " kaldı — bitene kadar bilgisayarda imleci oynatma.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            countdown > 0 -> Text(
                                "$countdown saniye içinde yazılacak — bilgisayarda imleci " +
                                    "ilgili alana getir!",
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text(
                                "Bilgisayarda imleci yazılacak alana getir, sonra düğmeye " +
                                    "bas. 3 saniye sonra yazma başlar.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                summary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
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
                    onClick = { pending = Gonderim.DEGER }
                ) { Text(if (countdown > 0) "$countdown…" else "Yaz") }
            }
        },
        dismissButton = {
            if (typing) {
                TextButton(onClick = { iptal.set(true) }) { Text("Durdur") }
            } else {
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        }
    )
}
