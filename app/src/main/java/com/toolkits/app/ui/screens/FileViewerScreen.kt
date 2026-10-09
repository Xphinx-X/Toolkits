package com.toolkits.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.toolkits.app.R
import com.toolkits.app.ui.components.OutlinedSectionCard
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Mirrors Toolkits-VIEW FileViewerActivity: line-based list (50k cap +
// truncated card), toolbar search toggle, live search with auto-jump to
// first match + 0/N count, VIEW highlight colors.
@Composable
fun FileViewerScreen(onBack: () -> Unit, uriString: String, fileName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var truncated by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Pair<Int, IntRange>>>(emptyList()) }
    var activeIdx by remember { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    // VIEW highlight colors: normal = tertiaryContainer @160 alpha,
    // active = primary bg + onPrimary fg.
    val normalBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 160 / 255f)
    val activeBg = MaterialTheme.colorScheme.primary
    val activeFg = MaterialTheme.colorScheme.onPrimary

    LaunchedEffect(uriString) {
        if (uriString.isBlank()) {
            loading = false
            scope.launch { snackbar.showSnackbar("No file to display") }
            return@LaunchedEffect
        }
        loading = true
        scope.launch(Dispatchers.IO) {
            try {
                val uri = uriString.toUri()
                val result = mutableListOf<String>()
                var trunc = false
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.bufferedReader(Charsets.UTF_8).use { br: BufferedReader ->
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            result.add(line ?: "")
                            if (result.size >= 50_000) { trunc = true; break }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    lines = result; truncated = trunc; loading = false
                    if (result.isEmpty()) scope.launch { snackbar.showSnackbar("File is empty or could not be read") }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    loading = false
                    scope.launch { snackbar.showSnackbar("File is empty or could not be read") }
                }
            }
        }
    }

    fun jumpTo(index: Int) {
        if (matches.isEmpty()) return
        val wrapped = ((index % matches.size) + matches.size) % matches.size
        activeIdx = wrapped
        scope.launch { listState.scrollToItem(matches[wrapped].first) }
    }

    // Live search like VIEW TextWatcher + auto-jump to first match.
    fun runSearch(q: String) {
        if (q.isEmpty() || lines.isEmpty()) { matches = emptyList(); activeIdx = -1; return }
        val needle = q.lowercase()
        val out = mutableListOf<Pair<Int, IntRange>>()
        lines.forEachIndexed { li, line ->
            var from = 0
            val low = line.lowercase()
            while (true) {
                val idx = low.indexOf(needle, from)
                if (idx < 0) break
                out.add(li to (idx until idx + needle.length))
                from = idx + needle.length
            }
        }
        matches = out
        if (out.isNotEmpty()) {
            activeIdx = 0
            scope.launch { listState.scrollToItem(out[0].first) }
        } else activeIdx = -1
    }

    fun toggleSearch(forceHide: Boolean = false) {
        searchVisible = if (forceHide) false else !searchVisible
        if (!searchVisible) {
            query = ""
            matches = emptyList()
            activeIdx = -1
            keyboard?.hide()
        }
    }

    Scaffold(
        topBar = {
            com.toolkits.app.ui.components.ToolkitsTopBar(
                title = if (fileName.isNotBlank()) fileName else "File Viewer",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { toggleSearch() }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (searchVisible) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; runSearch(it) },
                        label = { Text("Search") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { toggleSearch(forceHide = true) }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close search")
                            }
                        }
                    )
                    IconButton(onClick = { jumpTo(activeIdx + 1) }) {
                        Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = "Next match")
                    }
                    IconButton(onClick = { jumpTo(activeIdx - 1) }) {
                        Icon(painterResource(R.drawable.ic_chevron_up), contentDescription = "Previous match")
                    }
                }
                Text(
                    if (query.isEmpty()) "0/0" else "${activeIdx + 1}/${matches.size}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (truncated) {
                OutlinedSectionCard {
                    Text(
                        "Showing the first 50,000 lines. This file is larger than that, so the rest isn't displayed here.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState) {
                itemsIndexed(lines, key = { i, _ -> i }) { li, line ->
                    val ranges = matches.filter { it.first == li }.map { it.second }
                    val activeRange = if (activeIdx >= 0 && matches.isNotEmpty() && matches[activeIdx].first == li) matches[activeIdx].second else null
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text("${li + 1}", modifier = Modifier.padding(end = 12.dp).align(Alignment.Top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ranges.isEmpty()) {
                            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        } else {
                            Text(
                                buildAnnotatedString {
                                    var pos = 0
                                    ranges.sortedBy { it.first }.forEach { r ->
                                        if (r.first > pos) append(line.substring(pos, r.first))
                                        val isActive = r == activeRange
                                        if (isActive) pushStyle(SpanStyle(color = activeFg))
                                        withStyle(SpanStyle(background = if (isActive) activeBg else normalBg)) {
                                            append(line.substring(r.first, minOf(r.last + 1, line.length)))
                                        }
                                        if (isActive) pop()
                                        pos = minOf(r.last + 1, line.length)
                                    }
                                    if (pos < line.length) append(line.substring(pos))
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
}
