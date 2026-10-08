package com.toolkits.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toolkits.app.model.ProjectFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Simplified port of Toolkits-VIEW ProjectsActivity: root dir browser, create/rename/delete,
// new-file dialog, traversal guard, JSON import (flat + nested).
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectsScreen(onBack: () -> Unit, onOpenEditor: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rootPath by remember { mutableStateOf(context.filesDir.absolutePath + "/projects") }
    var items by remember { mutableStateOf<List<ProjectFile>>(emptyList()) }
    var pathInput by remember { mutableStateOf("") }
    var showNewFile by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showJson by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ProjectFile?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            try {
                val root = File(rootPath.ifBlank { context.filesDir.absolutePath + "/projects" })
                root.mkdirs()
                val list = root.walkTopDown().maxDepth(3).filter { it.absolutePath != root.absolutePath }
                    .map {
                        val rel = root.toURI().relativize(it.toURI()).path
                        ProjectFile(
                            name = it.name, relativePath = rel, absolutePath = it.absolutePath,
                            isDirectory = it.isDirectory, depth = rel.count { c -> c == '/' }.let { d -> if (it.isDirectory) d else d },
                            isExpanded = false
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.relativePath })).take(300).toList()
                withContext(Dispatchers.Main) { items = list }
            } catch (_: Exception) { }
        }
    }

    fun guarded(target: File, root: File): Boolean {
        return try { target.canonicalPath.startsWith(root.canonicalPath + File.separator) } catch (_: Exception) { false }
    }

    androidx.compose.runtime.LaunchedEffect(rootPath) { refresh() }

    if (showNewFile) {
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text("New file") },
            text = { OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, label = { Text("relative/path.txt") }) },
            confirmButton = {
                TextButton(onClick = {
                    val root = File(rootPath); val target = File(root, newFileName.trim())
                    if (newFileName.isBlank() || !guarded(target, root)) { message = "Invalid path"; return@TextButton }
                    target.parentFile?.mkdirs(); target.createNewFile()
                    showNewFile = false; newFileName = ""; refresh()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFile = false }) { Text("Cancel") } }
        )
    }

    if (showJson) {
        AlertDialog(
            onDismissRequest = { showJson = false },
            title = { Text("Import JSON structure") },
            text = { OutlinedTextField(value = jsonInput, onValueChange = { jsonInput = it }, label = { Text("{\"paths\": [...]} or [\"a/b.txt\"]") }, minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val root = File(rootPath); root.mkdirs()
                        val paths = mutableListOf<String>()
                        val t = jsonInput.trim()
                        if (t.startsWith("[")) {
                            val arr = JSONArray(t)
                            for (i in 0 until arr.length()) paths.add(arr.getString(i))
                        } else {
                            val obj = JSONObject(t)
                            for (k in listOf("paths", "files", "items", "structure")) {
                                if (obj.has(k)) {
                                    val arr = obj.getJSONArray(k)
                                    for (i in 0 until arr.length()) {
                                        val v = arr.get(i)
                                        paths.add(if (v is JSONObject && v.has("name")) v.getString("name") else v.toString())
                                    }
                                }
                            }
                        }
                        paths.filter { it.isNotBlank() && !it.contains("..") }.forEach { p ->
                            val target = File(root, p)
                            if (guarded(target, root)) { target.parentFile?.mkdirs(); if (!target.exists()) target.createNewFile() }
                        }
                        message = "Imported ${paths.size} paths"
                    } catch (e: Exception) { message = "JSON error: ${e.message}" }
                    showJson = false; jsonInput = ""; refresh()
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showJson = false }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { pf ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${pf.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    File(pf.absolutePath).let { f -> if (f.isDirectory) f.deleteRecursively() else f.delete() }
                    pendingDelete = null; refresh()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    pendingRename?.let { pf ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("New name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.contains("/") || renameText.contains("\\") || renameText.contains("..") || renameText.isBlank()) {
                        message = "Invalid name"; return@TextButton
                    }
                    File(pf.absolutePath).renameTo(File(File(pf.absolutePath).parent, renameText))
                    pendingRename = null; refresh()
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } }
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Projects") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { showNewFile = true }) { Icon(Icons.Filled.NoteAdd, null) }
            }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = pathInput.ifBlank { rootPath }, onValueChange = { pathInput = it }, label = { Text("Project root path") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { if (pathInput.isNotBlank()) { rootPath = pathInput; refresh() } }, modifier = Modifier.weight(1f)) { Text("Open") }
                OutlinedButton(onClick = { showJson = true }, modifier = Modifier.weight(1f)) { Text("Import JSON") }
                OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items, key = { it.absolutePath }) { pf ->
                    ListItem(
                        headlineContent = { Text("${"  ".repeat(pf.depth.coerceAtMost(4))}${pf.name}") },
                        supportingContent = { Text(pf.relativePath, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(if (pf.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile, null) },
                        trailingContent = {
                            Row {
                                if (!pf.isDirectory) IconButton(onClick = { pendingRename = pf; renameText = pf.name }) { Icon(Icons.Filled.DriveFileRenameOutline, null) }
                                IconButton(onClick = { pendingDelete = pf }) { Icon(Icons.Filled.Delete, null) }
                                if (!pf.isDirectory) IconButton(onClick = { onOpenEditor(pf.absolutePath) }) { Icon(Icons.Filled.ChevronRight, null) }
                            }
                        },
                        modifier = Modifier.clickable(enabled = !pf.isDirectory) { onOpenEditor(pf.absolutePath) }
                    )
                }
            }
        }
    }
}
