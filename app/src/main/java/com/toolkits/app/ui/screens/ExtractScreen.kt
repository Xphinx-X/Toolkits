package com.toolkits.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.MultipartArchiveHelper
import com.toolkits.app.helper.PathUtils
import com.toolkits.app.model.ArchiveItem
import com.toolkits.app.service.ExtractArchiveService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile

private val COMPRESSOR_EXTENSIONS = setOf("xz", "bz2", "gz", "gzip", "zst", "zstd", "lzma", "lz4")

// Compose port of Toolkits-VIEW ExtractFragment: SAF 4-step resolve, listing, selective extract.
@Composable
fun ExtractScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archivePath by remember { mutableStateOf("") }
    var destPath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ArchiveItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Pick an archive (ZIP, 7z, RAR, TAR, GZ…).") }
    var loading by remember { mutableStateOf(false) }

    fun resolveUriToPath(uri: Uri): String? {
        // 1) direct 2) docId reconstruct 3) fd symlink 4) cache copy — mirrors original
        PathUtils.getPath(context, uri)?.takeIf { File(it).exists() }?.let { return it }
        try {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size == 2 && parts[0] == "primary") {
                val cand = File("/storage/emulated/0/${parts[1]}")
                if (cand.exists()) return cand.absolutePath
            }
        } catch (_: Exception) { }
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val link = File("/proc/self/fd/${pfd.fd}")
                val canon = link.canonicalPath
                if (File(canon).exists() && !canon.startsWith("/proc")) return canon
            }
        } catch (_: Exception) { }
        return try {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.takeLast(80) ?: "archive.tmp"
            val out = File(context.cacheDir, "extract_src_$name")
            context.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            out.absolutePath
        } catch (_: Exception) { null }
    }

    fun listArchive(path: String) {
        loading = true
        scope.launch(Dispatchers.IO) {
            try {
                val f = File(path)
                val ext = f.extension.lowercase()
                val found: List<ArchiveItem> = when {
                    ext == "zip" -> {
                        val z = if (password.isNotBlank()) ZipFile(f, password.toCharArray()) else ZipFile(f)
                        z.fileHeaders.map { h -> ArchiveItem(h.fileName, h.fileName, h.uncompressedSize, h.isDirectory, h.lastModifiedTime) }
                    }
                    ext in COMPRESSOR_EXTENSIONS -> listOf(ArchiveItem(f.nameWithoutExtension, f.nameWithoutExtension, f.length(), false, f.lastModified()))
                    MultipartArchiveHelper.isMultipartArchive(path) -> listOf(ArchiveItem("(multipart — contents resolved at extract)", "", 0, false, 0))
                    else -> listOf(ArchiveItem("(preview via service at extract — ZIP shows full list)", "", f.length(), false, f.lastModified()))
                }
                withContext(Dispatchers.Main) { items = found; selected = emptySet(); selectionMode = false; status = "${found.size} entries"; loading = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "List failed: ${e.message}"; loading = false }
            }
        }
    }

    fun startExtraction() {
        val src = archivePath.ifBlank { status = "Choose an archive first"; return }
        val intent = Intent(context, ExtractArchiveService::class.java).apply {
            putExtra(ServiceConstants.EXTRA_ARCHIVE_PATH, src)
            putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, destPath.ifBlank { "" })
            putExtra(ServiceConstants.EXTRA_PASSWORD, password)
            if (selectionMode && selected.isNotEmpty()) putExtra("selectedPaths", selected.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        status = "Extraction started — see notification."
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val p = resolveUriToPath(uri)
        if (p != null) { archivePath = p; listArchive(p) } else status = "Could not resolve SAF uri — try another file."
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = archivePath, onValueChange = { archivePath = it }, label = { Text("Archive path") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { archivePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Pick file") }
            OutlinedButton(onClick = { if (archivePath.isNotBlank()) listArchive(archivePath) }, modifier = Modifier.weight(1f)) { Text(if (loading) "Loading…" else "List") }
        }
        OutlinedTextField(value = destPath, onValueChange = { destPath = it }, label = { Text("Extract to (blank = auto)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (items.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectionMode, onClick = { selectionMode = !selectionMode; if (!selectionMode) selected = emptySet() }, label = { Text("Selective") })
                if (selectionMode) {
                    FilterChip(selected = selected.size == items.size, onClick = { selected = if (selected.size == items.size) emptySet() else items.map { it.path }.toSet() }, label = { Text("Select all (${selected.size})") })
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items, key = { it.path }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectionMode) Checkbox(
                            checked = selected.contains(item.path),
                            onCheckedChange = { c -> selected = if (c) selected + item.path else selected - item.path }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            if (item.size > 0) Text("${item.size} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Button(onClick = { startExtraction() }, modifier = Modifier.fillMaxWidth()) { Text("Extract") }
    }
}
