package com.afgover.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afgover.vault.data.EntryData
import com.afgover.vault.data.EntryType
import com.afgover.vault.ui.VaultViewModel

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
    var loaded by remember { mutableStateOf(id == 0L) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Yeni ${type.label()}" else "Düzenle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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

                EntryType.NOTE -> Unit
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notlar") },
                minLines = if (type == EntryType.NOTE) 6 else 2,
                modifier = Modifier.fillMaxWidth()
            )

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
                            notes = notes
                        ),
                        onDone = onBack
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
