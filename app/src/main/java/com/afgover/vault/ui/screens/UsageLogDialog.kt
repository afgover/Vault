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
import com.afgover.vault.R
import com.afgover.vault.data.UsageEvent
import com.afgover.vault.ui.VaultViewModel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

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
    // Silinmiş kaydın etiketi: buildString içinde stringResource çağırmamak için dışarı alındı.
    val deletedLabel = stringResource(R.string.log_deleted_entry)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.log_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (pending > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pluralStringResource(R.plurals.log_pending_note, pending, pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (targets.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.log_filter_by_target),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        SecimCipi(
                            secili = target == null,
                            onClick = { target = null },
                            label = stringResource(R.string.log_filter_all),
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
                        if (events.isEmpty()) stringResource(R.string.log_empty)
                        else stringResource(R.string.log_empty_for_target),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val secilenHedef = target
                    if (secilenHedef != null) {
                        val kayitlar = shown.map { titles[it.entryId] ?: deletedLabel }
                            .distinct()
                        Text(
                            stringResource(
                                R.string.log_entries_to_target,
                                secilenHedef,
                                kayitlar.joinToString(", ")
                            ),
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
                                append(" · ").append(titles[ev.entryId] ?: deletedLabel)
                                append(" · ").append(stringResource(ev.kind.labelRes))
                                ev.fieldLabel?.let { append(" (").append(it).append(")") }
                                ev.target?.let { append(" → ").append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (shown.size > 200) {
                        Text(
                            pluralStringResource(R.plurals.log_more_events, shown.size - 200, shown.size - 200),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (confirmClear) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.log_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        TextButton(onClick = {
                            viewModel.clearUsageLog { tick++ }
                            confirmClear = false
                        }) { Text(stringResource(R.string.log_clear_yes)) }
                        TextButton(onClick = { confirmClear = false }) {
                            Text(stringResource(R.string.log_cancel))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_close)) }
        },
        dismissButton = {
            if (!confirmClear) {
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.log_clear))
                }
            }
        }
    )
}
