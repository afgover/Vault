package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afgover.vault.data.UsageEvent
import com.afgover.vault.ui.VaultViewModel

/**
 * Tüm kullanım günlüğü: hangi kaydın hangi alanı, ne zaman, nereye gitti.
 *
 * Hedefe göre süzme, "bir bilgisayar ele geçti, oraya ne gitti" sorusunun
 * cevabıdır — günlüğün asıl varlık sebebi bu (vault_takip SEC-023).
 */
@Composable
fun UsageLogDialog(viewModel: VaultViewModel, onDismiss: () -> Unit) {
    var events by remember { mutableStateOf<List<UsageEvent>>(emptyList()) }
    var pending by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val entries by viewModel.entries.collectAsState()

    LaunchedEffect(tick) {
        events = viewModel.allUsage()
        pending = viewModel.pendingUsage()
    }

    val titles = remember(entries) { entries.associate { it.id to it.title } }
    val targets = remember(events) { events.mapNotNull { it.target }.distinct().sorted() }
    val shown = remember(events, target) {
        if (target == null) events else events.filter { it.target == target }
    }
    val df = remember { java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale("tr")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kullanım günlüğü") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Günlük yalnız uygulamanın yaptığını görür: ekrandan okunan, " +
                        "fotoğraflanan ya da elle yazılan bir değer iz bırakmaz. " +
                        "Otomatik doldurmada hangi kaydı seçtiğin de Android " +
                        "tarafından uygulamaya bildirilmediği için kaydedilemiyor. " +
                        "Kasa açıkken bu günlük silinebilir — kurcalanamaz bir kayıt değildir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (pending > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$pending olay kilitliyken oluştu ve henüz günlüğe taşınmadı; " +
                            "kasa açıldığında listeye girecek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (targets.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Hedefe göre süz:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        SecimCipi(
                            secili = target == null,
                            onClick = { target = null },
                            label = "Hepsi",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        targets.forEach { t ->
                            SecimCipi(
                                secili = target == t,
                                onClick = { target = t },
                                label = t,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))

                if (shown.isEmpty()) {
                    Text(
                        if (events.isEmpty()) "Günlük boş." else "Bu hedefe giden kayıt yok.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    if (target != null) {
                        val kayitlar = shown.map { titles[it.entryId] ?: "(silinmiş kayıt)" }
                            .distinct()
                        Text(
                            "\"$target\" hedefine giden kayıtlar: " + kayitlar.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    shown.take(200).forEach { ev ->
                        Text(
                            buildString {
                                append(df.format(java.util.Date(ev.at)))
                                append(" · ").append(titles[ev.entryId] ?: "(silinmiş kayıt)")
                                append(" · ").append(ev.kind.label)
                                ev.fieldLabel?.let { append(" (").append(it).append(")") }
                                ev.target?.let { append(" → ").append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (shown.size > 200) {
                        Text(
                            "… ve ${shown.size - 200} olay daha (listede ilk 200 gösteriliyor).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (confirmClear) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Günlüğün tamamı silinsin mi? Geri alınamaz; kayıtların " +
                            "kendisi etkilenmez.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        TextButton(onClick = {
                            viewModel.clearUsageLog { tick++ }
                            confirmClear = false
                        }) { Text("Evet, sil") }
                        TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        dismissButton = {
            if (!confirmClear) {
                TextButton(onClick = { confirmClear = true }) { Text("Günlüğü sil") }
            }
        }
    )
}
