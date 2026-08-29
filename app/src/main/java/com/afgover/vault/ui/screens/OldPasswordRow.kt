package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afgover.vault.R
import com.afgover.vault.data.OldPassword

/**
 * Geçmişteki tek bir parola: tarihi açıkta, değeri maskeli.
 *
 * Güncel parolayla aynı kural: kilit açık olsa bile bir sır kendiliğinden
 * ekranda durmaz — göstermek ayrı bir el hareketi ister. Omuz üstünden
 * bakan biri için "eski" parola da hâlâ bir paroladır; çoğu insan onu
 * başka bir yerde kullanmaya devam ediyor olabilir.
 */
@Composable
fun OldPasswordRow(
    old: OldPassword,
    dateText: String,
    onCopy: () -> Unit,
    showDivider: Boolean
) {
    var gorunur by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (gorunur) old.value else "•".repeat(old.value.length.coerceAtMost(16)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (gorunur) FontFamily.Monospace else FontFamily.Default
            )
            Text(
                dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = { gorunur = !gorunur }) {
            Icon(
                if (gorunur) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(
                    if (gorunur) R.string.detail_hide else R.string.detail_show
                )
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.detail_copy)
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
