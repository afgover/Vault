package com.afgover.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.afgover.vault.R
import com.afgover.vault.data.TagEntity
import com.afgover.vault.data.TagPalette
import com.afgover.vault.ui.VaultViewModel

/**
 * Belirgin seçim çipi: seçili durum dolgulu birincil renk + ✓ + kalın yazı.
 * BT düzen seçimi, içe aktarma kipi gibi "yanlış anlaşılmaması gereken"
 * ikili/az seçenekli seçimlerde kullanılır (B-050 kalıbının genelleşmişi).
 */
@Composable
fun SecimCipi(
    secili: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = secili,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (secili) androidx.compose.ui.text.font.FontWeight.Bold
                else androidx.compose.ui.text.font.FontWeight.Normal
            )
        },
        leadingIcon = if (secili) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.home_selected),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier
    )
}

/**
 * Etiket rengi noktası. Ölçü, yanında durduğu yazının punto'suyla eşleşir:
 * 10dp'lik nokta başlık yanında kaybolup rengi okunmaz kılıyordu.
 */
@Composable
fun TagDot(color: Int, size: Int = 14) {
    Spacer(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
    )
}

/** Seçilebilir etiket çipi — filtre satırı ve düzenleme ekranı aynı görünümü kullanır. */
@Composable
fun TagChip(tag: TagEntity, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(tag.name) },
        leadingIcon = { TagDot(tag.color) }
    )
}

/** Ad + palet rengiyle etiket oluşturma/düzenleme. */
@Composable
fun TagEditDialog(
    initial: TagEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: TagPalette.colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) stringResource(R.string.home_tag_new)
                else stringResource(R.string.home_tag_edit)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.home_tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TagPalette.colors.forEach { c ->
                        IconButton(onClick = { color = c }, modifier = Modifier.size(32.dp)) {
                            Row(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c)),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (c == color) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription =
                                            stringResource(R.string.home_selected_color),
                                        tint = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), color) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.home_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        }
    )
}

/** Etiket yönetimi: listele, ekle, düzenle, sil. */
@Composable
fun TagManageDialog(viewModel: VaultViewModel, tags: List<TagEntity>, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<TagEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_tags_title)) },
        text = {
            if (tags.isEmpty()) {
                Text(stringResource(R.string.home_tags_empty))
            } else {
                LazyColumn {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TagDot(tag.color, size = 16)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { editing = tag }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.home_edit_cd)
                                )
                            }
                            IconButton(onClick = { deleting = tag }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.home_delete)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { adding = true }) {
                Text(stringResource(R.string.home_tag_add_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) }
        }
    )

    if (adding) {
        TagEditDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { name, color ->
                viewModel.addTag(name, color)
                adding = false
            }
        )
    }
    editing?.let { tag ->
        TagEditDialog(
            initial = tag,
            onDismiss = { editing = null },
            onSave = { name, color ->
                viewModel.updateTag(tag.copy(name = name, color = color))
                editing = null
            }
        )
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.home_tag_delete_title)) },
            text = { Text(stringResource(R.string.home_tag_delete_msg, tag.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag.id)
                    deleting = null
                }) { Text(stringResource(R.string.home_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        )
    }
}
