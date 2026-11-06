package com.louis.melo.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFileExchange(): FileExchange {
    val context = LocalContext.current
    val pendingExport = remember { mutableStateOf<String?>(null) }
    val pendingImport = remember { mutableStateOf<((String) -> Unit)?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport.value
        pendingExport.value = null
        if (uri != null && text != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } }
                .onSuccess { Messages.show("Bibliothèque exportée") }
                .onFailure { Messages.show("Échec de l'export") }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingImport.value
        pendingImport.value = null
        if (uri != null && callback != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                .getOrNull()?.let(callback) ?: Messages.show("Fichier illisible")
        }
    }

    return remember(create, open) {
        object : FileExchange {
            override fun export(fileName: String, content: String) {
                pendingExport.value = content
                create.launch(fileName)
            }

            override fun import(onText: (String) -> Unit) {
                pendingImport.value = onText
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
    }
}
