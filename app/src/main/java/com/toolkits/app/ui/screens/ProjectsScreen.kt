package com.toolkits.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.toolkits.app.helper.SafPathResolver
import com.toolkits.app.model.ProjectFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PREF_PROJECT_ROOT = "project_root_path"
private const val PREF_RECENT_PROJECTS = "recent_projects_json"
private const val MAX_RECENT = 5

// Port of Toolkits-VIEW ProjectsActivity: SAF folder picker + persisted root +
// recent list + empty state + full-depth tree. Never exposes internal filesDir.
@Composable
fun ProjectsScreen(onBack: () -> Unit, onOpenEditor: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var projectRoot by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ProjectFile>>(emptyList()) }
    var recents by remember { mutableStateOf<List<String>>(emptyList()) }
    var showNewFile by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showJson by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ProjectFile?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun loadRecents(): List<String> = try {
        val raw = sharedPrefs.getString(PREF_RECENT_PROJECTS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }.filter { File(it).exists() }
    } catch (_: Exception) { emptyList() }

    fun saveRecent(path: String) {
        try {
            val updated = (listOf(path) + loadRecents()).distinct().take(MAX_RECENT)
            sharedPrefs.edit().putString(PREF_RECENT_PROJECTS, JSONArray(updated).toString()).apply()
            recents = updated
        } catch (_: Exception) { }
    }

    fun openProject(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(context, "Folder not found", Toast.LENGTH_SHORT).show()
            projectRoot = null
            return
        }
        projectRoot = dir.absolutePath
        sharedPrefs.edit().putString(PREF_PROJECT_ROOT, dir.absolutePath).apply()
        saveRecent(dir.absolutePath)
        message = ""
    }

    fun refresh() {
        val rootPath = projectRoot ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val root = File(rootPath)
                if (!root.exists() || !root.isDirectory) {
                    withContext(Dispatchers.Main) { projectRoot = null }
                    return@launch
                }
                val out = mutableListOf<ProjectFile>()
                fun build(dir: File, depth: Int) {
                    if (out.size > 2000) return
                    val entries = dir.listFiles()
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
                    for (e in entries) {
                        if (out.size > 2000) break
                        val rel = try { e.relativeTo(root).path } catch (_: Exception) { e.name }
                        out.add(ProjectFile(e.name, rel, e.absolutePath, e.isDirectory, depth))
                        if (e.isDirectory) build(e, depth + 1)
                    }
                }
                build(root, 0)
                withContext(Dispatchers.Main) { items = out }
            } catch (_: Exception) { }
        }
    }

    fun guarded(target: File, root: File): Boolean {
        return try { target.canonicalPath.startsWith(root.canonicalPath + File.separator) } catch (_: Exception) { false }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { }
        val path = SafPathResolver.treeUriToPath(context, uri)
        if (path != null) openProject(path)
        else Toast.makeText(context, "Could not resolve folder path", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        recents = loadRecents()
        val saved = sharedPrefs.getString(PREF_PROJECT_ROOT, null)
        if (!saved.isNullOrEmpty() && File(saved).exists() && File(saved).isDirectory) {
            projectRoot = saved
        }
    }
    LaunchedEffect(projectRoot) { if (projectRoot != null) refresh() }

    if (showNewFile) {
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text("New file") },
            text = { OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, label = { Text("relative/path.txt") }) },
            confirmButton = {
                TextButton(onClick = {
                    val rootPath = projectRoot ?: return@TextButton
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
                        val rootPath = projectRoot ?: return@TextButton
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
                    val src = File(pf.absolutePath)
                    val dst = File(src.parent, renameText)
                    if (dst.exists()) { message = "A file with that name already exists"; return@TextButton }
                    val rootPath = projectRoot
                    if (rootPath != null && !dst.canonicalPath.startsWith(File(rootPath).canonicalPath)) {
                        message = "Invalid name"; return@TextButton
                    }
                    src.renameTo(dst)
                    pendingRename = null; refresh()
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            com.toolkits.app.ui.components.ToolkitsTopBar(
                title = "Projects",
                onBack = onBack,
                actions = {
                    if (projectRoot != null) {
                        IconButton(onClick = { showNewFile = true }) { Icon(Icons.Filled.NoteAdd, null) }
                    }
                }
            )
        },
        floatingActionButton = {
            // VIEW has a New-File FAB on the project view.
            if (projectRoot != null) {
                androidx.compose.material3.FloatingActionButton(onClick = { showNewFile = true }) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = "New file")
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (projectRoot == null) {
                // Empty state — mirrors VIEW showEmptyState, no raw internal path shown.
                Text("No project open", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose a folder on shared storage to browse and edit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Choose folder")
                }
                if (recents.isNotEmpty()) {
                    Text("Recent", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(recents, key = { it }) { r ->
                            ListItem(
                                headlineContent = { Text(File(r).name) },
                                supportingContent = { Text(r, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = { Icon(Icons.Filled.Folder, null) },
                                modifier = Modifier.clickable { openProject(r) }
                            )
                        }
                    }
                }
            } else {
                val rootFile = File(projectRoot!!)
                // Header card — mirrors VIEW cardProjectHeader (name + path).
                com.toolkits.app.ui.components.OutlinedSectionCard {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(rootFile.name, style = MaterialTheme.typography.titleMedium)
                            Text(rootFile.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) { Text("Change") }
                    OutlinedButton(onClick = { showJson = true }, modifier = Modifier.weight(1f)) { Text("Import JSON") }
                    OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (items.isEmpty()) {
                    Text("Empty folder", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(items, key = { it.absolutePath }) { pf ->
                            ListItem(
                                headlineContent = { Text("${"  ".repeat(pf.depth.coerceAtMost(8))}${pf.name}") },
                                supportingContent = { Text(pf.relativePath, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = { Icon(if (pf.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null) },
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
    }
}
