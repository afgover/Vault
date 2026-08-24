package com.afgover.vault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.afgover.vault.core.QrTransfer
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * QR ile doğrudan aktarım — tarama tarafı (vault_takip B-035).
 *
 * Kamera görüntüsü cihazdan çıkmaz: çözücü gömülüdür (ZXing) ve uygulamanın
 * `INTERNET` izni yoktur, yani görüntünün gidebileceği bir yer de yoktur
 * (SEC-019). Çok kareli aktarımda kareler sırasız toplanır; küme tamamlanana
 * kadar hiçbir şey çözülmeye gönderilmez.
 */
@Composable
fun QrScanScreen(onEnvelope: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var izinVar by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var istendi by remember { mutableStateOf(false) }
    val izinIstegi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { verildi -> izinVar = verildi }
    LaunchedEffect(Unit) {
        if (!izinVar) { istendi = true; izinIstegi.launch(Manifest.permission.CAMERA) }
    }

    val toplanan = remember { mutableStateOf(mapOf<Int, QrTransfer.Frame>()) }
    var durum by remember { mutableStateOf("QR'ı çerçeveye al") }
    var cozunurluk by remember { mutableStateOf("") }
    var bitti by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (izinVar) {
                    val executor = remember { Executors.newSingleThreadExecutor() }
                    // Kamerayı ekran kapanınca/çıkınca bırak: sağlayıcıyı tut ve
                    // onDispose'da çöz, yoksa tarama ekranından çıkılsa bile
                    // kamera göstergesi yanık kalır (denetim).
                    val providerRef = remember { java.util.concurrent.atomic.AtomicReference<ProcessCameraProvider?>() }
                    DisposableEffect(Unit) {
                        onDispose {
                            providerRef.get()?.unbindAll()
                            executor.shutdown()
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                providerRef.set(provider)
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val reader = MultiFormatReader().apply {
                                    setHints(
                                        mapOf(
                                            DecodeHintType.POSSIBLE_FORMATS to
                                                listOf(BarcodeFormat.QR_CODE),
                                            // Yoğun kareyi zor açılarda da bul;
                                            // maliyeti CPU, kazancı bekleme.
                                            DecodeHintType.TRY_HARDER to true
                                        )
                                    )
                                }
                                // Çözünürlük bilinçli olarak yükseltildi:
                                // CameraX varsayılanı 640x480 ve 117 modüllük
                                // bir kare ekranın yarısını kapladığında modül
                                // başına ~2,7 piksel düşüyor — ZXing'in
                                // sınırı. 720p'de bu 5,5 piksele çıkıyor ve
                                // okuma anında oluyor (ölçüm: B-080).
                                val analysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(
                                        ResolutionSelector.Builder()
                                            .setResolutionStrategy(
                                                ResolutionStrategy(
                                                    Size(1280, 720),
                                                    ResolutionStrategy
                                                        .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                                )
                                            )
                                            .build()
                                    )
                                    .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()
                                analysis.setAnalyzer(executor) { image ->
                                    if (cozunurluk.isEmpty()) {
                                        cozunurluk = "${image.width}×${image.height}"
                                    }
                                    if (!bitti) {
                                        okunanQr(image, reader)?.let { metin ->
                                            QrTransfer.parse(metin)?.let { kare ->
                                                val yeni = toplanan.value + (kare.index to kare)
                                                toplanan.value = yeni
                                                val eksik = QrTransfer.missing(yeni.values)
                                                durum = if (eksik.isEmpty()) "Tamam"
                                                else "${yeni.size}/${kare.total} alındı"
                                                if (eksik.isEmpty()) {
                                                    val zarf = QrTransfer.assemble(yeni.values)
                                                    if (zarf != null) {
                                                        bitti = true
                                                        onEnvelope(zarf)
                                                    } else {
                                                        toplanan.value = emptyMap()
                                                        durum = "Kareler tutmadı, baştan"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    image.close()
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Kamera izni gerekli. Görüntü cihazdan çıkmaz: " +
                                "çözücü uygulamanın içinde ve uygulamanın ağ izni yok.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        // İzin kalıcı reddedildiyse sistem istemi bir daha
                        // açılmaz; o durumda düğme sessizce hiçbir şey yapmasın
                        // diye ayarları açan bir yol sunulur (denetim).
                        Button(onClick = {
                            if (istendi) {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            } else {
                                istendi = true
                                izinIstegi.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Text(if (istendi) "Ayarlardan izin ver" else "İzin ver")
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(durum, style = MaterialTheme.typography.titleMedium)
                if (cozunurluk.isNotEmpty()) {
                    Text(
                        "Kamera analizi: $cozunurluk · kareyi ekranın yarısını " +
                            "kaplayacak kadar yakın tut",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val kareler = toplanan.value.values
                val toplam = kareler.firstOrNull()?.total ?: 0
                if (toplam > 1) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { kareler.size.toFloat() / toplam },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Eksik kare: " + QrTransfer.missing(kareler).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Vazgeç") }
            }
        }
    }
}

/** ImageProxy'nin parlaklık düzlemini ZXing'e verir; QR yoksa null. */
private fun okunanQr(image: ImageProxy, reader: MultiFormatReader): String? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(
        bytes, image.planes[0].rowStride, image.height,
        0, 0, image.width, image.height, false
    )
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}
