package com.toolkits.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolkits.app.ui.components.OutlinedSectionCard

// Mirrors Toolkits-VIEW activity_text_info.xml: pick-file card (desc +
// Choose/Choose-Another + View-Content + filename) → results group →
// 2-col stats card → Most Used Words card → Emojis Found card.
// State lives in TextInfoViewModel so back-nav from File Viewer keeps the file.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextInfoScreen(
    onBack: () -> Unit,
    onViewFile: (uri: String, name: String) -> Unit,
    vm: TextInfoViewModel = viewModel()
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        vm.analyse(context, uri)
    }

    Scaffold(topBar = {
        com.toolkits.app.ui.components.ToolkitsTopBar(title = "Text Info", onBack = onBack)
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Pick file card.
            OutlinedSectionCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        "Select a text file to analyse",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            picker.launch(arrayOf(
                                "text/*", "application/json", "application/xml",
                                "application/javascript", "application/x-yaml", "*/*"
                            ))
                        },
                        enabled = !vm.analysing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (vm.analysing) "Analysing…" else if (vm.hasResult) "Choose Another File" else "Choose File")
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = vm.analysing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                    if (vm.hasResult) {
                        OutlinedButton(
                            onClick = {
                                if (vm.uriString.isNotBlank()) {
                                    onViewFile(vm.uriString, vm.stats?.name ?: "")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) { Text("View File Content") }
                        Text(
                            vm.stats?.name ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    if (vm.error.isNotBlank()) {
                        Text(vm.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            // Results group — hidden until a file is analysed.
            val s = vm.stats
            if (vm.hasResult && s != null) {
                // Stats card — 2-col grid like VIEW.
                OutlinedSectionCard {
                    Column {
                        StatGridRow(StatCell(s.sizeFormatted, "File Size"), StatCell(s.lines, "Lines"))
                        StatDivider()
                        StatGridRow(StatCell(s.words, "Words"), StatCell(s.chars, "Characters"))
                        StatDivider()
                        StatGridRow(StatCell(s.charsNoSpaces, "Non-space Chars"), StatCell(s.letters, "Letters"))
                        StatDivider()
                        StatGridRow(StatCell(s.emojiCount, "Emojis"), StatCell(s.readingTime, "Reading Time"))
                    }
                }
                // Most Used Words card.
                if (s.topWords.isNotEmpty()) {
                    OutlinedSectionCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Most Used Words", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.topWords.forEach { (w, c) -> AssistChip(onClick = {}, label = { Text("$w  ×$c") }) }
                            }
                        }
                    }
                }
                // Emojis Found card — all unique emojis, like VIEW.
                if (s.emojis.isNotEmpty()) {
                    OutlinedSectionCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Emojis Found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.emojis.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class StatCell(val value: String, val label: String)

@Composable
private fun StatGridRow(left: StatCell, right: StatCell) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Text(left.value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(left.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Column(Modifier.weight(1f).padding(16.dp)) {
            Text(right.value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(right.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
