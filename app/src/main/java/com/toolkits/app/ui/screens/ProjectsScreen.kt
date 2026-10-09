package com.toolkits.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.toolkits.app.R
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.helper.SafPathResolver
import com.toolkits.app.model.ProjectFile
import com.toolkits.app.ui.components.ToolkitsTopBar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PREF_PROJECT_ROOT = "project_root_path"
private const val PREF_RECENT_PROJECTS = "recent_projects_json"
private const val PREF_COLLAPSED_PATHS = "collapsed_paths_json"
private const val MAX_RECENT = 5

// Color-coded file icons by extension — mirrors VIEW ProjectFileAdapter.fileStyle.
private fun fileIconColor(name: String): Color {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "js", "jsx", "ts", "tsx" -> Color(0xFFF0A500)
        "html", "htm" -> Color(0xFFE44D26)
        "css" -> Color(0xFF264DE4)
        "json" -> Color(0xFF4CAF50)
        "md", "markdown" -> Color(0xFF9C27B0)
        "yaml", "yml" -> Color(0xFFFF5722)
        "xml" -> Color(0xFF00ACC1)
        "kt" -> Color(0xFF7F52FF)
        "java" -> Color(0xFFED8B00)
        "py" -> Color(0xFF3572A5)
        "txt" -> Color(0xFF9E9E9E)
        "sh", "bash" -> Color(0xFF43A047)
        "sql" -> Color(0xFF00838F)
        "php" -> Color(0xFF777BB4)
        "swift" -> Color(0xFFFA7343)
        "dart" -> Color(0xFF00B4D8)
        else -> Color(0xFF78909C)
    }
}

// Compose mirror of Toolkits-VIEW ProjectsActivity + activity_projects.xml:
// hero empty state, recent list, clickable header with recents dropdown,
// collapsible file tree (48dp rows, accent bar, chevrons, per-extension icons),
// toolbar menu, extended New-File FAB, path suggestions, JSON import.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    prefs: UserPreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val prefsState by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
    var projectRoot by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ProjectFile>>(emptyList()) }
    var recents by remember { mutableStateOf<List<String>>(emptyList()) }
    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var showNewFile by remember { mutableStateOf(false) }
    var newFileTitle by remember { mutableStateOf("New File") }
    var newFileHint by remember { mutableStateOf("e.g. readme.md  or  src/utils/helper.js") }
    var newFileName by remember { mutableStateOf("") }
    var suggestOpen by remember { mutableStateOf(false) }
    var showJson by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ProjectFile?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectFile?>(null) }
    var pendingConflicts by remember { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
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

    fun loadCollapsed(): Set<String> = try {
        val raw = sharedPrefs.getString(PREF_COLLAPSED_PATHS, null) ?: return emptySet()
        val arr = JSONArray(raw)
        buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
    } catch (_: Exception) { emptySet() }

    fun saveCollapsed(paths: Set<String>) {
        try { sharedPrefs.edit().putString(PREF_COLLAPSED_PATHS, JSONArray(paths.toList()).toString()).apply() } catch (_: Exception) { }
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
        dropdownOpen = false
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
        return try {
            target.canonicalPath.startsWith(root.canonicalPath + File.separator) ||
                target.canonicalPath == root.canonicalPath
        } catch (_: Exception) { false }
    }

    fun toggleCollapse(pf: ProjectFile) {
        collapsed = if (collapsed.contains(pf.absolutePath)) collapsed - pf.absolutePath
        else collapsed + pf.absolutePath
        saveCollapsed(collapsed)
    }

    // Rows hidden when an ancestor folder is collapsed — mirrors VIEW buildVisible().
    val visibleItems = remember(items, collapsed) {
        items.filter { pf ->
            collapsed.none { c -> pf.absolutePath != c && pf.absolutePath.startsWith(c + File.separator) }
        }
    }

    fun directChildCount(pf: ProjectFile): Int {
        val prefix = pf.relativePath + "/"
        return items.count { it.relativePath.startsWith(prefix) && !it.relativePath.removePrefix(prefix).contains('/') }
    }

    fun createFile(root: File, rel: String): Boolean {
        val target = File(root, rel)
        if (!guarded(target, root)) {
            message = "Invalid path: must stay inside the project folder"
            return false
        }
        if (target.exists()) {
            message = "File already exists"
            return false
        }
        return try {
            target.parentFile?.mkdirs()
            target.createNewFile()
            refresh()
            onOpenEditor(target.absolutePath)
            true
        } catch (e: Exception) {
            message = "Failed: ${e.message}"
            false
        }
    }

    // ── JSON import: flat array, wrapper object, or nested tree (mirrors VIEW). ──
    fun parseJsonStructure(jsonText: String): List<Pair<String, Boolean>> {
        val trimmed = jsonText.trim()
        val result = mutableListOf<Pair<String, Boolean>>()
        when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val raw = arr.optString(i, "").trim()
                    if (raw.isEmpty()) continue
                    result += (raw.trimEnd('/') to raw.endsWith("/"))
                }
            }
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                val pathsKey = listOf("paths", "structure", "files", "items")
                    .firstOrNull { obj.has(it) && obj.get(it) is JSONArray }
                if (pathsKey != null) {
                    val arr = obj.getJSONArray(pathsKey)
                    for (i in 0 until arr.length()) {
                        val raw = arr.optString(i, "").trim()
                        if (raw.isEmpty()) continue
                        result += (raw.trimEnd('/') to raw.endsWith("/"))
                    }
                } else {
                    parseNestedNode(obj, "", result)
                }
            }
            else -> throw IllegalArgumentException("JSON must start with '{' or '['.")
        }
        if (result.isEmpty()) throw IllegalArgumentException("No file or folder entries found.")
        return result.sortedWith(compareBy({ it.first.count { c -> c == '/' } }, { !it.second }, { it.first }))
    }

    fun executeCreation(root: File, entries: List<Pair<String, Boolean>>, skipExisting: Boolean) {
        var created = 0
        var skipped = 0
        var failed = 0
        for ((rel, isDir) in entries) {
            val target = File(root, rel)
            if (target.exists()) {
                if (skipExisting || isDir) { skipped++; continue }
                if (!target.delete()) { failed++; continue }
            }
            try {
                if (isDir) {
                    if (target.mkdirs()) created++ else skipped++
                } else {
                    target.parentFile?.mkdirs()
                    if (target.createNewFile()) created++ else failed++
                }
            } catch (_: Exception) { failed++ }
        }
        refresh()
        message = buildString {
            append("Created $created item${if (created != 1) "s" else ""}")
            if (skipped > 0) append(", skipped $skipped")
            if (failed > 0) append(", $failed failed")
        }
    }

    fun doImport() {
        val rootPath = projectRoot ?: run { message = "Open a project first"; return }
        val root = File(rootPath)
        val entries = try {
            parseJsonStructure(jsonInput)
        } catch (e: Exception) {
            message = "Invalid JSON: ${e.message}"
            return
        }
        val safe = entries.filter { (rel, _) ->
            try { File(root, rel).canonicalPath.startsWith(root.canonicalPath + File.separator) } catch (_: Exception) { false }
        }
        if (safe.isEmpty()) {
            message = "All paths were blocked by the security check"
            return
        }
        val conflicts = safe.filter { (rel, _) -> File(root, rel).exists() }
        if (conflicts.isEmpty()) {
            executeCreation(root, safe, skipExisting = false)
            showJson = false
            jsonInput = ""
        } else {
            pendingConflicts = safe
        }
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

    val jsonFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            if (text != null) jsonInput = text
        } catch (_: Exception) { Toast.makeText(context, "Could not read file", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) {
        recents = loadRecents()
        collapsed = loadCollapsed()
        val saved = sharedPrefs.getString(PREF_PROJECT_ROOT, null)
        if (!saved.isNullOrEmpty() && File(saved).exists() && File(saved).isDirectory) {
            projectRoot = saved
        }
    }
    LaunchedEffect(projectRoot) { if (projectRoot != null) refresh() }

    fun openNewFileDialog() {
        newFileTitle = "New File"
        newFileHint = "e.g. readme.md  or  src/utils/helper.js"
        newFileName = ""
        suggestOpen = false
        showNewFile = true
    }

    fun openCreateHereDialog(pf: ProjectFile) {
        // If item is a folder, create inside it; if a file, create alongside it (VIEW).
        val parentDir = if (pf.isDirectory) pf.relativePath else {
            pf.relativePath.substringBeforeLast('/').takeIf { it != pf.relativePath } ?: ""
        }
        val prefix = if (parentDir.isEmpty()) "" else "$parentDir/"
        newFileTitle = "Create File Here"
        newFileHint = "Creates in: ${if (prefix.isEmpty()) "/" else prefix}"
        newFileName = prefix
        suggestOpen = false
        showNewFile = true
    }

    if (showNewFile) {
        val allPaths = remember(items) { items.map { it.relativePath } }
        AlertDialog(
            onDismissRequest = { showNewFile = false; newFileName = "" },
            title = { Text(newFileTitle) },
            text = {
                Column {
                    Text(
                        newFileHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (prefsState.hidePathSuggest) {
                        OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            label = { Text("File path") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = suggestOpen,
                            onExpandedChange = { suggestOpen = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newFileName,
                                onValueChange = { newFileName = it; suggestOpen = true },
                                label = { Text("File path") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = suggestOpen) }
                            )
                            val filtered = allPaths
                                .filter { it.contains(newFileName, ignoreCase = true) && it != newFileName }
                                .take(8)
                            if (filtered.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = suggestOpen,
                                    onDismissRequest = { suggestOpen = false }
                                ) {
                                    filtered.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = { newFileName = s; suggestOpen = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rootPath = projectRoot ?: return@TextButton
                    val input = newFileName.trim()
                    if (input.isEmpty()) {
                        message = "Enter a path"
                        return@TextButton
                    }
                    if (createFile(File(rootPath), input)) {
                        showNewFile = false
                        newFileName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFile = false }) { Text("Cancel") } }
        )
    }

    if (showJson) {
        AlertDialog(
            onDismissRequest = { showJson = false },
            title = { Text("Import from JSON") },
            text = {
                Column {
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text("JSON structure") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    TextButton(
                        onClick = { jsonFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Pick JSON file") }
                }
            },
            confirmButton = { TextButton(onClick = { doImport() }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { showJson = false }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { pf ->
        val msg = if (pf.isDirectory) "Delete '${pf.name}' and all its contents?"
        else "Delete '${pf.name}'?"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    File(pf.absolutePath).let { f -> if (pf.isDirectory) f.deleteRecursively() else f.delete() }
                    pendingDelete = null
                    refresh()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    pendingRename?.let { pf ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isEmpty() || newName == pf.name) {
                        pendingRename = null
                        return@TextButton
                    }
                    if (newName.contains('/') || newName.contains('\\') || newName == ".." || newName == ".") {
                        message = "Name must not contain path separators"
                        return@TextButton
                    }
                    val src = File(pf.absolutePath)
                    val dst = File(src.parent, newName)
                    if (dst.exists()) {
                        message = "A file with that name already exists"
                        return@TextButton
                    }
                    val rootPath = projectRoot
                    val inside = try {
                        rootPath == null || dst.canonicalPath.startsWith(File(rootPath).canonicalPath + File.separator)
                    } catch (_: Exception) { false }
                    if (!inside) {
                        message = "Invalid name"
                        return@TextButton
                    }
                    if (src.renameTo(dst)) {
                        pendingRename = null
                        refresh()
                    } else {
                        message = "Rename failed"
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } }
        )
    }

    pendingConflicts?.let { safe ->
        AlertDialog(
            onDismissRequest = { pendingConflicts = null },
            title = { Text("Conflicts Found") },
            text = {
                Text(
                    "${safe.count { (rel, _) -> File(projectRoot ?: "", rel).exists() }} item(s) already exist in this project.\n\n" +
                        "• Skip — leave existing items unchanged, create only new ones.\n" +
                        "• Replace — overwrite existing files with empty files. Folders are kept as-is."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val rootPath = projectRoot ?: return@TextButton
                    executeCreation(File(rootPath), safe, skipExisting = false)
                    pendingConflicts = null
                    showJson = false
                    jsonInput = ""
                }) { Text("Replace") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val rootPath = projectRoot ?: return@TextButton
                        executeCreation(File(rootPath), safe, skipExisting = true)
                        pendingConflicts = null
                        showJson = false
                        jsonInput = ""
                    }) { Text("Skip") }
                    TextButton(onClick = { pendingConflicts = null }) { Text("Cancel") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ToolkitsTopBar(
                title = "Projects",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        if (projectRoot == null) message = "Open a project first"
                        else {
                            message = ""
                            showJson = true
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_json), contentDescription = "Import from JSON")
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Import from JSON") },
                                onClick = {
                                    overflowOpen = false
                                    if (projectRoot == null) message = "Open a project first"
                                    else showJson = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change Folder") },
                                onClick = {
                                    overflowOpen = false
                                    folderPicker.launch(null)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (projectRoot != null) {
                ExtendedFloatingActionButton(
                    onClick = { openNewFileDialog() },
                    icon = { Icon(painterResource(R.drawable.ic_add_file), contentDescription = null) },
                    text = { Text("New File") }
                )
            }
        }
    ) { pad ->
        if (projectRoot == null) {
            // ── Empty state: hero card + recent projects (mirrors VIEW). ──
            Column(
                Modifier.fillMaxSize().padding(pad)
                    .verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_project),
                            contentDescription = "Projects",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "No project folder selected",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            "Pick a folder to start creating and editing project files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.padding(top = 24.dp)
                        ) { Text("Select Project Folder") }
                    }
                }
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (recents.isNotEmpty()) {
                    Text(
                        "Recent Projects",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            recents.forEach { r ->
                                RecentRow(
                                    name = File(r).name,
                                    path = r,
                                    onClick = { openProject(r) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ── Project state: header card + recents dropdown + file tree. ──
            val rootFile = File(projectRoot!!)
            val chevronRot by animateFloatAsState(
                if (dropdownOpen) 180f else 0f, label = "projHeaderChevron"
            )
            Column(Modifier.fillMaxSize().padding(pad)) {
                Card(
                    onClick = {
                        if (recents.isNotEmpty() || projectRoot != null) dropdownOpen = !dropdownOpen
                    },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_folder_open),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                rootFile.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                rootFile.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_expand_more),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).rotate(chevronRot),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AnimatedVisibility(visible = dropdownOpen) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp).padding(bottom = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "Recent & Switch",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                                    .padding(top = 8.dp, bottom = 4.dp)
                            )
                            recents.forEach { r ->
                                RecentRow(
                                    name = File(r).name,
                                    path = r,
                                    onClick = { openProject(r) }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            Row(
                                Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    dropdownOpen = false
                                    folderPicker.launch(null)
                                }) {
                                    Icon(
                                        painterResource(R.drawable.ic_folder_open),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose New Folder")
                                }
                            }
                        }
                    }
                }
                Text(
                    "Files",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp)
                )
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (visibleItems.isEmpty()) {
                    Text(
                        "Empty folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(visibleItems, key = { it.absolutePath }) { pf ->
                            val expanded = pf.isDirectory && !collapsed.contains(pf.absolutePath)
                            ProjectTreeRow(
                                pf = pf,
                                expanded = expanded,
                                subtitle = if (pf.isDirectory) {
                                    val n = directChildCount(pf)
                                    if (n > 0) "$n ${if (n == 1) "item" else "items"}" else null
                                } else null,
                                menuOpen = menuFor == pf.absolutePath,
                                onToggle = { toggleCollapse(pf) },
                                onOpen = { onOpenEditor(pf.absolutePath) },
                                onMenuChange = { open -> menuFor = if (open) pf.absolutePath else null },
                                onRename = {
                                    menuFor = null
                                    pendingRename = pf
                                    renameText = pf.name
                                },
                                onDelete = { menuFor = null; pendingDelete = pf },
                                onCreateHere = { menuFor = null; openCreateHereDialog(pf) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(name: String, path: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_folder_open),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTreeRow(
    pf: ProjectFile,
    expanded: Boolean,
    subtitle: String?,
    menuOpen: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMenuChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCreateHere: () -> Unit
) {
    // 48dp row, 20dp depth indent, folder accent bar + animated chevron,
    // per-extension file icon colors — mirrors VIEW item_project_file.xml.
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = { if (pf.isDirectory) onToggle() else onOpen() })
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width((pf.depth * 20).dp))
        if (pf.isDirectory) {
            Box(
                Modifier.width(3.dp).height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            val chevRot by animateFloatAsState(
                if (expanded) 0f else -90f, label = "projTreeChevron"
            )
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(chevRot),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Icon(
            painterResource(if (pf.isDirectory) R.drawable.ic_folder_open else R.drawable.ic_file),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (pf.isDirectory) MaterialTheme.colorScheme.primary else fileIconColor(pf.name)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                pf.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Box {
            IconButton(
                onClick = { onMenuChange(true) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuChange(false) }
            ) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
                DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
                DropdownMenuItem(text = { Text("Create file here") }, onClick = onCreateHere)
            }
        }
    }
}

private fun parseNestedNode(
    node: JSONObject,
    parentPath: String,
    out: MutableList<Pair<String, Boolean>>
) {
    val name = node.optString("name", "").trim()
    if (name.isEmpty() || name == "." || name == "..") return
    val typeStr = node.optString("type", "").lowercase()
    val hasChildren = node.has("children")
    val isDir = typeStr == "folder" || typeStr == "directory" || typeStr == "dir" || hasChildren
    val rel = if (parentPath.isEmpty()) name else "$parentPath/$name"
    out += (rel to isDir)
    if (hasChildren) {
        val children = node.optJSONArray("children") ?: return
        for (i in 0 until children.length()) {
            children.optJSONObject(i)?.let { parseNestedNode(it, rel, out) }
        }
    }
}
