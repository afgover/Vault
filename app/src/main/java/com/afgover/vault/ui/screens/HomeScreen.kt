package com.afgover.vault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.afgover.vault.data.EntryType
import com.afgover.vault.ui.VaultViewModel

fun EntryType.icon(): ImageVector = when (this) {
    EntryType.LOGIN -> Icons.Filled.Key
    EntryType.EVERYDAY -> Icons.Filled.Badge
    EntryType.CARD -> Icons.Filled.CreditCard
    EntryType.NOTE -> Icons.AutoMirrored.Filled.Note
}

fun EntryType.label(): String = when (this) {
    EntryType.LOGIN -> "Hesap / Şifre"
    EntryType.EVERYDAY -> "Gündelik"
    EntryType.CARD -> "Kart"
    EntryType.NOTE -> "Güvenli Not"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VaultViewModel,
    onOpen: (Long) -> Unit,
    onAdd: (EntryType) -> Unit,
    onSettings: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    var query by remember { mutableStateOf("") }
    var addMenuOpen by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }

    val filtered = if (query.isBlank()) entries
    else entries.filter { it.title.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = { showGenerator = true }) {
                        Icon(Icons.Filled.Casino, contentDescription = "Şifre üretici")
                    }
                    IconButton(onClick = { viewModel.lock() }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Kilitle")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                DropdownMenu(
                    expanded = addMenuOpen,
                    onDismissRequest = { addMenuOpen = false }
                ) {
                    EntryType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label()) },
                            leadingIcon = { Icon(type.icon(), contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                onAdd(type)
                            }
                        )
                    }
                }
                FloatingActionButton(onClick = { addMenuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Ekle")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Ara") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Text(
                    text = if (entries.isEmpty())
                        "Henüz kayıt yok. Sağ alttaki + ile ekleyebilirsin."
                    else "Eşleşen kayıt yok.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onOpen(item.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    item.type.icon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (item.quick) {
                                            "${item.type.label()} · ⚡ klavyede parolasız"
                                        } else {
                                            item.type.label()
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onCopy = { viewModel.copyToClipboard("Şifre", it) }
        )
    }
}
