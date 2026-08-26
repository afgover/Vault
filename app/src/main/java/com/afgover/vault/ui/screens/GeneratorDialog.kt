package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.afgover.vault.core.PasswordGenerator

/**
 * Şifre üretici. [onUse] verilirse "Kullan" butonu görünür (düzenleme
 * ekranındaki şifre alanını doldurur); her durumda "Kopyala" vardır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorDialog(
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onUse: ((String) -> Unit)? = null
) {
    var options by remember { mutableStateOf(PasswordGenerator.Options()) }
    var password by remember { mutableStateOf(PasswordGenerator.generate(PasswordGenerator.Options())) }

    fun update(newOptions: PasswordGenerator.Options) {
        options = newOptions
        password = PasswordGenerator.generate(newOptions)
    }

    val entropy = PasswordGenerator.entropyBits(options)

    // Parola kutusu BEŞ satırlık. Yüksekliği hesaplamıyoruz — satır yüksekliği
    // × n denemesi son satırın alt payını (descender) saymadığı için son satır
    // yarım kalıyordu; `minLines` ölçüyü metnin kendisine bırakır.
    //
    // Neden dört değil beş: en uzun parola (64 karakter) dar bir ekranda beşinci
    // satıra taşıyor ve kutu dörtte sabitlenince uzunluk kaydırılırken tam o
    // noktada büyüyüp diyalogu zıplatıyordu. Beş satır bütün uzunlukları
    // kapsadığı için çerçeve hiç oynamıyor.
    val stil = MaterialTheme.typography.bodyLarge

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unlock_generator_title)) },
        text = {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sabit yükseklik: uzunluk kaydırılırken satır sayısı
                    // değiştiği için diyalog boyu zıplıyordu. Ölçü, izin verilen
                    // EN UZUN parolanın (64 karakter) tam sığdığı yer: daha kısası
                    // son satırı kırpıyordu, daha uzunu boşuna boşluk bırakırdı.
                    // İki karar burada:
                    // (1) Yenile düğmesi kutunun İÇİNDE değil — metnin yanında
                    //     durunca satırı kısaltıyor ve 20 karakterlik bir parola
                    //     "J/" gibi çirkin bir yerden bölünüyordu.
                    // (2) Yükseklik sabit değil, TABANLI: kısa parolada boşuna
                    //     SABİT dört satır. İçeriğe göre büyüyen bir kutu,
                    //     uzunluk kaydırılırken diyalogu zıplatıyor; dört satır
                    //     en uzun parolayı (64 karakter) kırpmadan alıyor.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = password,
                            fontFamily = FontFamily.Monospace,
                            style = stil,
                            minLines = 5,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.unlock_generator_summary,
                            options.length,
                            entropy,
                            stringResource(
                                if (entropy >= 90) R.string.unlock_generator_very_strong
                                else if (entropy >= 60) R.string.unlock_generator_strong
                                else R.string.unlock_generator_weak
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { update(options) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription =
                                stringResource(R.string.unlock_generator_refresh)
                        )
                    }
                }
                Slider(
                    value = options.length.toFloat(),
                    onValueChange = { update(options.copy(length = it.toInt())) },
                    valueRange = 8f..64f
                )
                // Beş seçenek tek akışta: eskiden dördü bir satırda, beşincisi
                // altında ayrı duruyordu ve kendi başına bir bölüm gibi
                // görünüyordu — oysa hepsi aynı işin ayarı.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = options.upper,
                        onClick = { update(options.copy(upper = !options.upper)) },
                        label = { Text("A-Z") }
                    )
                    FilterChip(
                        selected = options.lower,
                        onClick = { update(options.copy(lower = !options.lower)) },
                        label = { Text("a-z") }
                    )
                    FilterChip(
                        selected = options.digits,
                        onClick = { update(options.copy(digits = !options.digits)) },
                        label = { Text("0-9") }
                    )
                    FilterChip(
                        selected = options.symbols,
                        onClick = { update(options.copy(symbols = !options.symbols)) },
                        label = { Text("!@#") }
                    )
                    FilterChip(
                        selected = options.avoidAmbiguous,
                        onClick = { update(options.copy(avoidAmbiguous = !options.avoidAmbiguous)) },
                        label = { Text(stringResource(R.string.unlock_generator_avoid_ambiguous)) }
                    )
                }
            }
        },
        confirmButton = {
            if (onUse != null) {
                TextButton(onClick = { onUse(password); onDismiss() }) {
                    Text(stringResource(R.string.unlock_generator_use))
                }
            } else {
                TextButton(onClick = { onCopy(password) }) {
                    Text(stringResource(R.string.unlock_generator_copy))
                }
            }
        },
        dismissButton = {
            if (onUse != null) {
                TextButton(onClick = { onCopy(password) }) {
                    Text(stringResource(R.string.unlock_generator_copy))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.unlock_generator_close))
                }
            }
        }
    )
}
