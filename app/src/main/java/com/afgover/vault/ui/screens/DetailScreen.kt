package com.afgover.vault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afgover.vault.core.Fingerprint
import com.afgover.vault.data.DecryptedEntry
import com.afgover.vault.data.EntryType
import com.afgover.vault.ui.VaultViewModel

private val HIDDEN_LABELS = setOf("Şifre", "CVV")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: VaultViewModel,
    id: Long,
    onEdit: (Long, EntryType) -> Unit,
    onBack: () -> Unit
) {
    var entry by remember { mutableStateOf<DecryptedEntry?>(null) }
    val tags by viewModel.tags.collectAsState()
    var btField by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(id) {
        entry = viewModel.loadEntry(id)
    }

    val e = entry
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (e != null) {
                        IconButton(onClick = { onEdit(e.id, e.type) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Düzenle")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (e == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val entryTags = tags.filter { it.id in e.tagIds }
            if (entryTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entryTags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagDot(tag.color)
                            Spacer(Modifier.padding(2.dp))
                            Text(tag.name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            e.data.fields().forEach { (label, value) ->
                FieldCard(
                    label = label,
                    value = value,
                    hiddenByDefault = label in HIDDEN_LABELS,
                    onCopy = { viewModel.copyToClipboard(label, value) },
                    onTypeToPc = { btField = label to value },
                    onCopyFingerprint = { viewModel.copyToClipboard("$label parmak izi", it) }
                )
                Spacer(Modifier.padding(4.dp))
            }

            Spacer(Modifier.padding(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Klavyede parolasız kullan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (e.quick) {
                                "Kasa kilitliyken de Vault Klavyesi'nde çıkar; koruması " +
                                    "telefonun ekran kilidi kadardır."
                            } else {
                                "Yalnızca kasa kilidi açıkken kullanılabilir."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = e.quick,
                        onCheckedChange = { value ->
                            entry = e.copy(quick = value)
                            viewModel.setQuick(e.id, value)
                        }
                    )
                }
            }

            Spacer(Modifier.padding(6.dp))
            val df = remember { java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale("tr")) }
            Text(
                buildString {
                    append("Eklendi: ").append(df.format(java.util.Date(e.createdAt)))
                    append(" · Güncellendi: ").append(df.format(java.util.Date(e.updatedAt)))
                    if (e.data.passwordChangedAt > 0) {
                        append("\nŞifre son değişti: ")
                        append(df.format(java.util.Date(e.data.passwordChangedAt)))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    btField?.let { (label, value) ->
        BtTypeDialog(
            label = label,
            value = value,
            onDismiss = { btField = null }
        )
    }
}

@Composable
private fun FieldCard(
    label: String,
    value: String,
    hiddenByDefault: Boolean,
    onCopy: () -> Unit,
    onTypeToPc: () -> Unit,
    onCopyFingerprint: (String) -> Unit
) {
    var visible by remember { mutableStateOf(!hiddenByDefault) }
    var fullFingerprint by remember { mutableStateOf(false) }
    // Uzun/çok satırlı değerler (.pem, anahtar) parmak iziyle karşılaştırılır.
    val uzun = value.length >= 100 || value.contains('\n')
    val hex = remember(value) { if (uzun) Fingerprint.sha256Hex(value) else "" }

    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Başlık ve eylemler üstte: değer artık tam genişlikte akıyor,
            // düğmeler metnin ortasında yer kaplamıyor.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (hiddenByDefault) {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "Gizle" else "Göster"
                        )
                    }
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Kopyala")
                }
                IconButton(onClick = onTypeToPc) {
                    Icon(Icons.Filled.Computer, contentDescription = "Bilgisayara yaz")
                }
            }
            Text(
                text = if (visible) value else "••••••••",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = if (uzun) 6.dp else 0.dp)
            )
            if (uzun) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fullFingerprint = !fullFingerprint }
                        .padding(top = 6.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SHA-256 parmak izi · ${value.length} karakter",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (fullFingerprint) Fingerprint.grouped(hex)
                            else Fingerprint.short(hex),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        if (fullFingerprint) {
                            Text(
                                "Bilgisayarda: shasum -a 256 dosya\n" +
                                    "(elle yapıştırdıysan son satır sonu düşmüş olabilir: " +
                                    "printf '%s' \"\$(cat dosya)\" | shasum -a 256)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = { onCopyFingerprint(hex) }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Parmak izini kopyala",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
