package com.toolkits.app.ui.screens

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.launch

// Port of Toolkits-VIEW Base64Activity: encode(NO_WRAP)/decode(DEFAULT), swap, copy, clear.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Base64Screen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var encodeMode by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Base64 Tools") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = encodeMode, onClick = { encodeMode = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Encode") }
                SegmentedButton(selected = !encodeMode, onClick = { encodeMode = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Decode") }
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text(if (encodeMode) "Plain text" else "Base64 input") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                minLines = 4, maxLines = 10
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    output = try {
                        if (encodeMode) Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        else String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
                    } catch (e: Exception) { "Error: ${e.message}" }
                }, modifier = Modifier.weight(1f)) { Text("Convert") }
                OutlinedButton(onClick = {
                    val oldOut = output
                    output = ""
                    input = oldOut
                    encodeMode = !encodeMode
                }) { Text("Swap") }
            }
            OutlinedTextField(value = output, onValueChange = {}, readOnly = true, label = { Text("Output") }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(output))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    scope.launch {}
                }, modifier = Modifier.weight(1f)) { Text("Copy output") }
                OutlinedButton(onClick = { input = ""; output = "" }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
            Text("Matches original: NO_WRAP encode, DEFAULT decode, swap flips mode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
