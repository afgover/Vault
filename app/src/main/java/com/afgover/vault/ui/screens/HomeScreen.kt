package com.afgover.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.afgover.vault.data.EntrySort
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

/** Tür kimlik rengi: liste ve detayda ikon kapsülünü boyar, taramayı hızlandırır. */
@Composable
fun EntryType.renk(): Color = when (this) {
    EntryType.LOGIN -> MaterialTheme.colorScheme.primary
    EntryType.EVERYDAY -> Color(0xFF63C7B2)
    EntryType.CARD -> MaterialTheme.colorScheme.tertiary
    EntryType.NOTE -> Color(0xFFB08FE0)
}

/** Yumuşak renkli kapsül içinde tür ikonu. */
@Composable
fun TypeBadge(type: EntryType, size: Int = 42) {
    val renk = type.renk()
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(size.dp / 3))
            .background(renk.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            type.icon(),
            contentDescription = null,
            tint = renk,
            modifier = Modifier.size((size * 0.55).dp)
        )
    }
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
    val tags by viewModel.tags.collectAsState()
    var query by remember { mutableStateOf("") }
    var addMenuOpen by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<EntryType?>(null) }
    val filterTagIds = remember { mutableStateListOf<Long>() }
    var manageTags by remember { mutableStateOf(false) }
    var showPasteImport by remember { mutableStateOf(false) }
    var showTransferWizard by remember { mutableStateOf(false) }

    // Seçimler daraltarak birleşir: tür VE seçili etiketlerin tamamı.
    // Sıra kullanıcının seçtiği düzendir (klavyeyle aynı — EntrySort).
    val filtered = entries.filter { item ->
        (query.isBlank() || item.title.contains(query, ignoreCase = true)) &&
            (filterType == null || item.type == filterType) &&
            filterTagIds.all { it in item.tagIds }
    }.let { list ->
        when (viewModel.sort) {
            EntrySort.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            EntrySort.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            EntrySort.UPDATED_DESC -> list.sortedByDescending { it.updatedAt }
            EntrySort.CREATED_DESC -> list.sortedByDescending { it.createdAt }
            EntrySort.TYPE -> list.sortedWith(
                compareBy({ it.type.ordinal }, { it.title.lowercase() })
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = { showTransferWizard = true }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Bilgisayardan aktar")
                    }
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
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Metinden içe aktar") },
                        leadingIcon = {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null)
                        },
                        onClick = {
                            addMenuOpen = false
                            showPasteImport = true
                        }
                    )
                }
                FloatingActionButton(
                    onClick = { addMenuOpen = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
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
                placeholder = { Text("Kayıtlarda ara") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EntryType.entries.forEach { t ->
                    FilterChip(
                        selected = filterType == t,
                        onClick = { filterType = if (filterType == t) null else t },
                        label = { Text(t.label()) }
                    )
                }
                tags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = tag.id in filterTagIds,
                        onToggle = {
                            if (tag.id in filterTagIds) filterTagIds.remove(tag.id)
                            else filterTagIds.add(tag.id)
                        }
                    )
                }
                IconButton(onClick = { manageTags = true }) {
                    Icon(Icons.Filled.Sell, contentDescription = "Etiketleri yönet")
                }
            }
            Spacer(Modifier.height(4.dp))

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (entries.isEmpty()) Icons.Filled.Lock else Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (entries.isEmpty())
                            "Kasa hazır — ilk kaydını sağ alttaki + ile ekle."
                        else "Bu filtrelerle eşleşen kayıt yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onOpen(item.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TypeBadge(item.type)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        item.tagIds.forEach { tid ->
                                            tags.find { it.id == tid }?.let {
                                                Spacer(Modifier.width(6.dp))
                                                TagDot(it.color)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        item.type.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.quick) {
                                    Text(
                                        "⚡",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 8.dp)
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

    if (showTransferWizard) {
        TransferWizardDialog(viewModel = viewModel, onDismiss = { showTransferWizard = false })
    }

    if (showPasteImport) {
        PasteImportDialog(viewModel = viewModel, onDismiss = { showPasteImport = false })
    }

    if (manageTags) {
        TagManageDialog(viewModel = viewModel, tags = tags, onDismiss = { manageTags = false })
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onCopy = { viewModel.copyToClipboard("Şifre", it) }
        )
    }
}
