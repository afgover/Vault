package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
            e.data.fields().forEach { (label, value) ->
                FieldCard(
                    label = label,
                    value = value,
                    hiddenByDefault = label in HIDDEN_LABELS,
                    onCopy = { viewModel.copyToClipboard(label, value) }
                )
                Spacer(Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun FieldCard(
    label: String,
    value: String,
    hiddenByDefault: Boolean,
    onCopy: () -> Unit
) {
    var visible by remember { mutableStateOf(!hiddenByDefault) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (visible) value else "••••••••",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
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
        }
    }
}
