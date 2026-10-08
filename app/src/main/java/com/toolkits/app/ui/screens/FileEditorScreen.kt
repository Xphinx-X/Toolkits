package com.toolkits.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of Toolkits-VIEW FileEditorActivity: atomic tmp+rename save, unsaved guard.
@Composable
fun FileEditorScreen(onBack: () -> Unit, filePath: String) {
    var text by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = text != original

    LaunchedEffect(filePath) {
        scope.launch(Dispatchers.IO) {
            try {
                val content = File(filePath).readText()
                withContext(Dispatchers.Main) { text = content; original = content; loaded = true }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { loaded = true }
            }
        }
    }

    fun save(onDone: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val tmp = File(file.parent, "${file.name}.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
                withContext(Dispatchers.Main) { original = text; onDone() }
            } catch (_: Exception) { }
        }
    }

    BackHandler(enabled = dirty) { showDiscard = true }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save before leaving?") },
            confirmButton = { TextButton(onClick = { showDiscard = false; save { onBack() } }) { Text("Save") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDiscard = false }) { Text("Cancel") }
                    TextButton(onClick = { showDiscard = false; onBack() }) { Text("Discard") }
                }
            }
        )
    }

    Scaffold(topBar = {
        com.toolkits.app.ui.components.ToolkitsTopBar(
            title = File(filePath).name.ifBlank { "Editor" },
            onBack = { if (dirty) showDiscard = true else onBack() },
            actions = {
                TextButton(onClick = { save {} }, enabled = dirty) { Text("Save") }
            }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!loaded) Text("Loading…")
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxSize().weight(1f), label = { Text(filePath) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { text = original }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Revert") }
                Button(onClick = { save {} }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            if (dirty) Text("Unsaved changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
