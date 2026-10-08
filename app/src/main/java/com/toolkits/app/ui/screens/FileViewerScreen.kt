package com.toolkits.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of Toolkits-VIEW FileViewerActivity: 50k-line cap, line RV, case-insensitive search.
@Composable
fun FileViewerScreen(onBack: () -> Unit, uriString: String, fileName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var truncated by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Pair<Int, IntRange>>>(emptyList()) }
    var activeIdx by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(uriString) {
        if (uriString.isBlank()) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            try {
                val uri = uriString.toUri()
                val result = mutableListOf<String>()
                var trunc = false
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.bufferedReader(Charsets.UTF_8).use { br: BufferedReader ->
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            if (result.size >= 50_000) { trunc = true; break }
                            result.add(line ?: "")
                        }
                    }
                }
                withContext(Dispatchers.Main) { lines = result; truncated = trunc }
            } catch (_: Exception) { }
        }
    }

    fun runSearch() {
        if (query.isBlank() || lines.isEmpty()) { matches = emptyList(); return }
        val q = query.lowercase()
        val out = mutableListOf<Pair<Int, IntRange>>()
        lines.forEachIndexed { li, line ->
            var from = 0
            val low = line.lowercase()
            while (true) {
                val idx = low.indexOf(q, from)
                if (idx < 0) break
                out.add(li to (idx until idx + q.length))
                from = idx + q.length
            }
        }
        matches = out; activeIdx = 0
    }

    Scaffold(topBar = {
        com.toolkits.app.ui.components.ToolkitsTopBar(
            title = if (fileName.isNotBlank()) fileName else "File Viewer",
            onBack = onBack
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.weight(1f), singleLine = true)
                IconButton(onClick = {
                    if (matches.isEmpty()) runSearch()
                    if (matches.isNotEmpty()) {
                        activeIdx = (activeIdx + 1) % matches.size
                        scope.launch { listState.scrollToItem(matches[activeIdx].first) }
                    }
                }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                IconButton(onClick = {
                    if (matches.isNotEmpty()) {
                        activeIdx = (activeIdx - 1 + matches.size) % matches.size
                        scope.launch { listState.scrollToItem(matches[activeIdx].first) }
                    }
                }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
            }
            if (truncated) Text("Truncated at 50,000 lines (original limit).", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (matches.isNotEmpty()) Text("${matches.size} matches • ${activeIdx + 1}/${matches.size}", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState) {
                itemsIndexed(lines, key = { i, _ -> i }) { li, line ->
                    val ranges = matches.filter { it.first == li }.map { it.second }
                    val activeRange = if (matches.isNotEmpty() && matches[activeIdx].first == li) matches[activeIdx].second else null
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text("${li + 1}", modifier = Modifier.padding(end = 12.dp).align(Alignment.Top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            buildAnnotatedString {
                                if (ranges.isEmpty()) append(line)
                                else {
                                    var pos = 0
                                    ranges.sortedBy { it.first }.forEach { r ->
                                        if (r.first > pos) append(line.substring(pos, r.first))
                                        withStyle(SpanStyle(background = if (r == activeRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)) {
                                            append(line.substring(r.first, minOf(r.last + 1, line.length)))
                                        }
                                        pos = minOf(r.last + 1, line.length)
                                    }
                                    if (pos < line.length) append(line.substring(pos))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
