package com.toolkits.app.ui.screens

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Mirrors Toolkits-VIEW activity_base64.xml: wrap toggle group (Encode/Decode),
// monospace input, full-width Convert, monospace output, end-aligned
// outlined row (Use as input / Copy / Clear). NO_WRAP encode, DEFAULT decode.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Base64Screen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var encodeMode by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = { com.toolkits.app.ui.components.ToolkitsTopBar(title = "Base64", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            // Mode toggle — single-choice segmented control (VIEW toggle group).
            // Both halves always clickable; selection is visual, never disabled.
            SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 16.dp)) {
                SegmentedButton(
                    selected = encodeMode,
                    onClick = { encodeMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Encode") }
                )
                SegmentedButton(
                    selected = !encodeMode,
                    onClick = { encodeMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Decode") }
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(if (encodeMode) "Input text" else "Base64 string") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                minLines = 4, maxLines = 10,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            Button(
                onClick = {
                    if (input.isBlank()) {
                        Toast.makeText(context, "Enter some text first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    output = try {
                        if (encodeMode) Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        else String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Invalid Base64 input", Toast.LENGTH_SHORT).show()
                        ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) { Text(if (encodeMode) "Encode" else "Decode") }

            OutlinedTextField(
                value = output,
                onValueChange = {},
                readOnly = true,
                label = { Text("Output") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                minLines = 4, maxLines = 10,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            // End-aligned outlined row: Use as input / Copy / Clear.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        if (output.isBlank()) {
                            Toast.makeText(context, "No output to use", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        input = output
                        encodeMode = !encodeMode
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("Use as input") }
                OutlinedButton(
                    onClick = {
                        if (output.isBlank()) {
                            Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        clipboard.setText(AnnotatedString(output))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("Copy") }
                OutlinedButton(onClick = { input = ""; output = "" }) { Text("Clear") }
            }
        }
    }
}
