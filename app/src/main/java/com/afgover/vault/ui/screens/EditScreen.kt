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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.afgover.vault.core.FieldFormats
import com.afgover.vault.R
import com.afgover.vault.ui.theme.vaultButtonColors
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
                title = {
                    Text(
                        if (id == 0L) stringResource(R.string.edit_title_new, type.label())
                        else stringResource(R.string.edit_title_edit)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { cikmayiDene() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.edit_back))
                    }
                },
                actions = {
                    if (id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.edit_delete))
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
                label = { Text(stringResource(R.string.edit_field_title)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            when (type) {
                EntryType.LOGIN -> {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text(stringResource(R.string.edit_field_username)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    // E-posta ayrı alan: birçok sitede kullanıcı adı ile
                    // e-posta farklı ve tek alana sıkıştırıldığında hangisinin
                    // istendiği kaybolyordu. Denetim kasıtlı olarak gevşek —
                    // amaç RFC'yi uygulamak değil, "@ ya da nokta unuttum"
                    // hatasını yakalamak (FieldFormats).
                    val epostaHatali = !FieldFormats.epostaGecerliMi(email)
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text(stringResource(R.string.field_email)) }, singleLine = true,
                        isError = epostaHatali,
                        supportingText = {
                            if (epostaHatali) Text(stringResource(R.string.edit_email_invalid))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(stringResource(R.string.field_password)) }, singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showGenerator = true }) {
                                Icon(Icons.Filled.Casino, contentDescription = stringResource(R.string.edit_generate_password))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text(stringResource(R.string.field_url)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EntryType.CARD -> {
                    OutlinedTextField(
                        value = cardholder, onValueChange = { cardholder = it },
                        label = { Text(stringResource(R.string.field_cardholder)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    // Kart alanlarında SAKLANAN değer ham (boşluksuz); boşluk
                    // yalnız ekranda, görüntü dönüşümü olarak var. Kopyalanan
                    // ya da bilgisayara yazılan numara bu sayede temiz gidiyor.
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = FieldFormats.cardNumberInput(it) },
                        label = { Text(stringResource(R.string.edit_field_card_number)) },
                        singleLine = true,
                        visualTransformation = GrupluGorunum(4),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val tarihHatali = !FieldFormats.expiryGecerliMi(expiry)
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = FieldFormats.expiryInput(it) },
                        label = { Text(stringResource(R.string.edit_field_expiry)) },
                        singleLine = true,
                        isError = tarihHatali,
                        supportingText = {
                            if (tarihHatali) Text(stringResource(R.string.edit_expiry_invalid))
                        },
                        visualTransformation = TarihGorunumu,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cvv,
                        onValueChange = { cvv = FieldFormats.cvvInput(it) },
                        label = { Text(stringResource(R.string.field_cvv)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = iban,
                        onValueChange = { iban = FieldFormats.ibanInput(it) },
                        label = { Text(stringResource(R.string.edit_field_iban)) },
                        singleLine = true,
                        visualTransformation = GrupluGorunum(4),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EntryType.EVERYDAY -> {
                    OutlinedTextField(
                        value = fullName, onValueChange = { fullName = it },
                        label = { Text(stringResource(R.string.field_full_name)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.field_phone)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text(stringResource(R.string.field_email)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address, onValueChange = { address = it },
                        label = { Text(stringResource(R.string.field_address)) }, minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EntryType.NOTE -> {
                    // Notun ne taşıdığı: ikon ve liste görünümü buna göre
                    // değişir (araç çıpası, .sh, .pem, kurtarma kodları…).
                    Text(stringResource(R.string.edit_note_kind), style = MaterialTheme.typography.titleSmall)
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
                Text(stringResource(R.string.edit_custom_fields), style = MaterialTheme.typography.titleSmall)
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
                                label = { Text(stringResource(R.string.edit_field_name)) }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { customFields.removeAt(index) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_remove_field))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = field.value,
                            onValueChange = { customFields[index] = field.copy(value = it) },
                            label = { Text(stringResource(R.string.edit_field_value)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { customFields.add(CustomField("", "")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.edit_add_field)) }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(R.string.edit_field_notes)) },
                minLines = if (type == EntryType.NOTE) 6 else 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.edit_quick_title))
                    Text(
                        stringResource(R.string.edit_quick_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = quick, onCheckedChange = { quick = it })
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.edit_tags), style = MaterialTheme.typography.titleSmall)
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
                OutlinedButton(onClick = { addTagDialog = true }) { Text(stringResource(R.string.edit_new_tag)) }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                colors = vaultButtonColors(),
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
                Text(stringResource(R.string.edit_save))
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscard = false; onBack() }) {
                    Text(stringResource(R.string.edit_discard_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.edit_discard_cancel))
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
        // Pano etiketi @Composable olmayan geri çağrımda kullanılıyor: dizeyi
        // burada, Compose bağlamında çöz.
        val sifreEtiketi = stringResource(R.string.field_password)
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onCopy = { viewModel.copyToClipboard(sifreEtiketi, it) },
            onUse = { password = it }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_title)) },
            text = { Text(stringResource(R.string.edit_delete_text, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteEntry(id, onDeleted)
                }) { Text(stringResource(R.string.edit_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.edit_cancel)) }
            }
        )
    }
}

/**
 * Ham değeri değiştirmeden ekranda [boyut]'luk gruplara ayırır.
 *
 * `VisualTransformation` bilinçli tercih: değeri boşluklu SAKLASAYDIK kopyalama,
 * klavye, Bluetooth ve yedek yollarının hepsine boşluk sızardı. Burada boşluk
 * yalnız çizime giriyor.
 */
private class GrupluGorunum(private val boyut: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val gosterilen = FieldFormats.grupla(text.text, boyut)
        val esleme = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                offset + (if (offset == 0) 0 else (offset - 1) / boyut)
            override fun transformedToOriginal(offset: Int) =
                offset - offset / (boyut + 1)
        }
        return TransformedText(AnnotatedString(gosterilen), esleme)
    }
}

/** AAYY değerini ekranda AA/YY olarak gösterir. */
private object TarihGorunumu : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val gosterilen = FieldFormats.expiryGoster(text.text)
        val esleme = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= 2) offset else offset - 1
        }
        return TransformedText(AnnotatedString(gosterilen), esleme)
    }
}

