package com.afgover.vault.ui.screens

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.afgover.vault.data.CustomField
import com.afgover.vault.data.EntryData
import com.afgover.vault.data.EntryType
import com.afgover.vault.data.NoteKind
import com.afgover.vault.ui.VaultViewModel
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    viewModel: VaultViewModel,
    id: Long,
    type: EntryType,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var cardholder by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var iban by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val customFields = remember { mutableStateListOf<CustomField>() }
    // Gündelik bilgiler klavyede parolasız kullanılsın diye açık başlar.
    var quick by remember { mutableStateOf(id == 0L && type == EntryType.EVERYDAY) }
    val tags by viewModel.tags.collectAsState()
    val selectedTagIds = remember { mutableStateListOf<Long>() }
    var addTagDialog by remember { mutableStateOf(false) }
    var noteKind by remember { mutableStateOf(NoteKind.GENEL) }
    // Şifre alanının yüklendiği andaki değeri: değişirse passwordChangedAt tazelenir.
    var originalPassword by remember { mutableStateOf("") }
    var originalPasswordChangedAt by remember { mutableStateOf(0L) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var initialSnap by remember { mutableStateOf<String?>(null) }
    var kaydedildi by remember { mutableStateOf(false) }

    // Kaydedilmemiş değişiklikte geri çıkış tüm girilenleri kaybettiriyordu
    // (denetim). Alanların anlık özetini yükleme anındaki özetle karşılaştırıp
    // farklıysa onay iste.
    fun snapshot(): String = listOf(
        title, username, password, url, cardholder, cardNumber, expiry, cvv, iban,
        notes, fullName, phone, email, address, quick.toString(), noteKind.name,
        selectedTagIds.sorted().joinToString(","),
        customFields.joinToString("|") { it.label + "=" + it.value }
    ).joinToString(String(charArrayOf('\u0001')))

    fun cikmayiDene() {
        if (!kaydedildi && initialSnap != null && snapshot() != initialSnap) showDiscard = true
        else onBack()
    }
    BackHandler(enabled = true) { cikmayiDene() }

    LaunchedEffect(id) {
        if (id != 0L) {
            viewModel.loadEntry(id)?.let { e ->
                title = e.title
                username = e.data.username
                password = e.data.password
                url = e.data.url
                cardholder = e.data.cardholder
                cardNumber = e.data.cardNumber
                expiry = e.data.expiry
                cvv = e.data.cvv
                iban = e.data.iban
                notes = e.data.notes
                fullName = e.data.fullName
                phone = e.data.phone
                email = e.data.email
                address = e.data.address
                customFields.clear()
                customFields.addAll(e.data.custom)
                quick = e.quick
                selectedTagIds.clear()
                selectedTagIds.addAll(e.tagIds)
                noteKind = e.noteKind
                originalPassword = e.data.password
                originalPasswordChangedAt = e.data.passwordChangedAt
            }
            loaded = true
        }
        initialSnap = snapshot()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Yeni ${type.label()}" else "Düzenle") },
                navigationIcon = {
                    IconButton(onClick = { cikmayiDene() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Sil")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Başlık") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            when (type) {
                EntryType.LOGIN -> {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text("Kullanıcı adı / E-posta") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Şifre") }, singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showGenerator = true }) {
                                Icon(Icons.Filled.Casino, contentDescription = "Şifre üret")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Site / Uygulama") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EntryType.CARD -> {
                    OutlinedTextField(
                        value = cardholder, onValueChange = { cardholder = it },
                        label = { Text("Kart sahibi") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cardNumber, onValueChange = { cardNumber = it },
                        label = { Text("Kart numarası") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expiry, onValueChange = { expiry = it },
                        label = { Text("Son kullanma (AA/YY)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cvv, onValueChange = { cvv = it },
                        label = { Text("CVV") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = iban, onValueChange = { iban = it },
                        label = { Text("IBAN (opsiyonel)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EntryType.EVERYDAY -> {
                    OutlinedTextField(
                        value = fullName, onValueChange = { fullName = it },
                        label = { Text("Ad Soyad") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("Telefon") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("E-posta") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address, onValueChange = { address = it },
                        label = { Text("Adres") }, minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EntryType.NOTE -> {
                    // Notun ne taşıdığı: ikon ve liste görünümü buna göre
                    // değişir (araç çıpası, .sh, .pem, kurtarma kodları…).
                    Text("Not türü", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.shapes.medium
                            )
                            .padding(8.dp)
                    ) {
                        NoteKind.entries.chunked(2).forEach { satir ->
                            Row(Modifier.fillMaxWidth()) {
                                satir.forEach { k ->
                                    val secili = noteKind == k
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(4.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(
                                                if (secili) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                            .clickable { noteKind = k }
                                            .padding(horizontal = 8.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            k.icon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (secili)
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(k.labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (secili)
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (satir.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Serbest alanlar her türde düzenlenebilir: aktarımla gelen bir
            // kaydın içeriği "alan adı + değer" çiftinde olabilir (ör. QR ile
            // eklenen not), ve o kayıt görünmezse ekran boş sanılır.
            run {
                Spacer(Modifier.height(16.dp))
                Text("Ek alanlar", style = MaterialTheme.typography.titleSmall)
                customFields.forEachIndexed { index, field ->
                    Spacer(Modifier.height(12.dp))
                    // Alan adı ve değer alt alta: yan yana iken ikisi de dar
                    // kalıyor ve uzun değerler okunmuyordu.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.shapes.medium
                            )
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = field.label,
                                onValueChange = { customFields[index] = field.copy(label = it) },
                                label = { Text("Alan adı") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { customFields.removeAt(index) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Alanı kaldır")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = field.value,
                            onValueChange = { customFields[index] = field.copy(value = it) },
                            label = { Text("Değer") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { customFields.add(CustomField("", "")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Alan ekle") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notlar") },
                minLines = if (type == EntryType.NOTE) 6 else 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Klavyede parolasız kullan")
                    Text(
                        "Açıkken bu kayıt, kasa kilitliyken de Vault Klavyesi'nde çıkar. " +
                            "Koruması telefonun ekran kilidi kadardır; şifre gibi hassas " +
                            "bilgiler için kapalı bırak.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = quick, onCheckedChange = { quick = it })
            }

            Spacer(Modifier.height(16.dp))
            Text("Etiketler", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = tag.id in selectedTagIds,
                        onToggle = {
                            if (tag.id in selectedTagIds) selectedTagIds.remove(tag.id)
                            else selectedTagIds.add(tag.id)
                        }
                    )
                }
                OutlinedButton(onClick = { addTagDialog = true }) { Text("+ Yeni") }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    viewModel.saveEntry(
                        id = id,
                        type = type,
                        title = title.trim(),
                        data = EntryData(
                            username = username.trim(),
                            password = password,
                            url = url.trim(),
                            cardholder = cardholder.trim(),
                            cardNumber = cardNumber.trim(),
                            expiry = expiry.trim(),
                            cvv = cvv.trim(),
                            iban = iban.trim(),
                            notes = notes,
                            fullName = fullName.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            custom = customFields
                                .map { CustomField(it.label.trim(), it.value.trim()) }
                                .filter { it.label.isNotEmpty() },
                            passwordChangedAt = when {
                                password.isEmpty() -> 0L
                                password != originalPassword -> System.currentTimeMillis()
                                // Değişmedi: eski damga korunur; damgasız eski
                                // kayıtta 0 kalır (bilinmeyen tarih uydurulmaz).
                                else -> originalPasswordChangedAt
                            }
                        ),
                        quick = quick,
                        tagIds = selectedTagIds.toList(),
                        noteKind = noteKind,
                        onDone = { kaydedildi = true; onBack() }
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kaydet")
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Değişiklikler kaydedilmedi") },
            text = { Text("Girdiğin bilgiler kaydedilmeden çıkılacak. Emin misin?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscard = false; onBack() }) {
                    Text("Çık, kaydetme")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscard = false }) {
                    Text("Düzenlemeye dön")
                }
            }
        )
    }

    if (addTagDialog) {
        TagEditDialog(
            initial = null,
            onDismiss = { addTagDialog = false },
            onSave = { name, color ->
                viewModel.addTag(name, color) { newId -> selectedTagIds.add(newId) }
                addTagDialog = false
            }
        )
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onCopy = { viewModel.copyToClipboard("Şifre", it) },
            onUse = { password = it }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kaydı sil") },
            text = { Text("\"$title\" kalıcı olarak silinecek. Emin misin?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteEntry(id, onDeleted)
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            }
        )
    }
}
