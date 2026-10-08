package com.toolkits.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toolkits.app.helper.FileUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TextStats(
    val size: Long = 0, val name: String = "", val lines: Int = 0, val words: Int = 0,
    val chars: Int = 0, val charsNoSpaces: Int = 0, val letters: Int = 0,
    val emojiCount: Int = 0, val emojis: List<String> = emptyList(),
    val readingMinutes: Double = 0.0, val topWords: List<Pair<String, Int>> = emptyList()
)

private val STOP_WORDS = setOf("the","a","an","and","or","of","to","in","is","it","you","that","he","she","we","they","i")

private fun isEmoji(cp: Int): Boolean =
    (cp in 0x1F300..0x1F9FF) || (cp in 0x1FA00..0x1FAFF) || (cp in 0x2600..0x27BF) ||
    (cp in 0x2300..0x23FF) || (cp in 0x1F000..0x1F02F) || (cp in 0x1F0A0..0x1F0FF)

// Port of Toolkits-VIEW TextInfoActivity.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextInfoScreen(onBack: () -> Unit, onViewFile: (uri: String, name: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<TextStats?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        lastUri = uri
        loading = true
        scope.launch(Dispatchers.IO) {
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"
                lastName = name
                var size = 0L
                val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                    val bytes = ins.readBytes()
                    size = bytes.size.toLong()
                    String(bytes, Charsets.UTF_8)
                } ?: ""
                val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
                val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
                val charsNoSpaces = text.count { !it.isWhitespace() }
                val letters = text.count { it.isLetter() }
                val emojiList = mutableListOf<String>()
                var i = 0
                while (i < text.length) {
                    val cp = text.codePointAt(i)
                    if (isEmoji(cp)) emojiList.add(String(Character.toChars(cp)))
                    i += Character.charCount(cp)
                }
                val freq = words.map { it.lowercase().trim { c -> !c.isLetterOrDigit() } }
                    .filter { it.length > 2 && it !in STOP_WORDS }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10)
                    .map { it.key to it.value }
                val s = TextStats(size, name, lines, words.size, text.length, charsNoSpaces, letters, emojiList.size, emojiList.distinct().take(20), words.size / 200.0, freq)
                withContext(Dispatchers.Main) { stats = s; loading = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loading = false }
            }
        }
    }

    Scaffold(topBar = {
        com.toolkits.app.ui.components.ToolkitsTopBar(title = "Text Info", onBack = onBack)
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/xml", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Reading…" else "Pick a text file")
            }
            val s = stats
            if (s != null) {
                StatRow("File", s.name)
                StatRow("Size", FileUtils.formatFileSize(s.size))
                StatRow("Lines", s.lines.toString())
                StatRow("Words", s.words.toString())
                StatRow("Characters", s.chars.toString())
                StatRow("No-spaces", s.charsNoSpaces.toString())
                StatRow("Letters", s.letters.toString())
                StatRow("Emojis", s.emojiCount.toString())
                StatRow("Reading time", "%.1f min @200wpm".format(s.readingMinutes))
                if (s.emojis.isNotEmpty()) {
                    Text("Emojis", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.emojis.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
                if (s.topWords.isNotEmpty()) {
                    Text("Top words", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.topWords.forEach { (w, c) -> AssistChip(onClick = {}, label = { Text("$w ×$c") }) }
                    }
                }
                OutlinedButton(onClick = { lastUri?.let { onViewFile(it.toString(), lastName) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open in File Viewer")
                }
            } else {
                Text("Pick a file to see lines/words/chars/emojis/top-words/reading time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatRow(k: String, v: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}
