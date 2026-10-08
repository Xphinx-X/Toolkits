package com.toolkits.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toolkits.app.R
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.FileOperationsDao
import com.toolkits.app.helper.MultipartArchiveHelper
import com.toolkits.app.helper.PathUtils
import com.toolkits.app.model.ArchiveItem
import com.toolkits.app.service.ExtractArchiveService
import com.toolkits.app.ui.components.CardDivider
import com.toolkits.app.ui.components.CardHeaderRow
import com.toolkits.app.ui.components.OutlinedSectionCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile

private val COMPRESSOR_EXTENSIONS = setOf("xz", "bz2", "gz", "gzip", "zst", "zstd", "lzma", "lz4")

// Compose mirror of Toolkits-VIEW ExtractFragment + fragment_extract.xml:
// editable path field with picker end-icon, outlined contents card with
// selection bar, outlined destination card, password field, 56dp Extract button.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtractScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archivePath by remember { mutableStateOf("") }
    var destCustom by remember { mutableStateOf("") }
    var destExpanded by remember { mutableStateOf(false) }
    var destEditing by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ArchiveItem>>(emptyList()) }
    var contentsExpanded by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val effectiveDest = destCustom.ifBlank { "" }

    fun resolveUriToPath(uri: Uri): String? {
        // Same 4-step SAF order as the original fragment.
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
                val canon = File("/proc/self/fd/${pfd.fd}").canonicalPath
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
        busy = true
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
                    MultipartArchiveHelper.isMultipartArchive(f) -> listOf(ArchiveItem("(multipart — contents resolved at extract)", "", 0, false, 0))
                    else -> listOf(ArchiveItem("(preview via service at extract — ZIP shows full list)", "", f.length(), false, f.lastModified()))
                }
                withContext(Dispatchers.Main) { items = found; selected = emptySet(); status = ""; busy = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "List failed: ${e.message}"; busy = false }
            }
        }
    }

    fun startExtraction() {
        val src = archivePath.ifBlank { status = "Choose an archive first"; return }
        val jobId = try { FileOperationsDao(context).addFilesForJob(listOf(src)) }
        catch (e: Exception) { status = "DB error: ${e.message}"; return }
        val intent = Intent(context, ExtractArchiveService::class.java).apply {
            putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
            putExtra(ServiceConstants.EXTRA_ARCHIVE_PATH, src)
            putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, effectiveDest)
            putExtra(ServiceConstants.EXTRA_PASSWORD, password)
            if (selected.isNotEmpty()) putStringArrayListExtra(ServiceConstants.EXTRA_SELECTED_PATHS, ArrayList(selected))
        }
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        busy = true
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val p = resolveUriToPath(uri)
        if (p != null) { archivePath = p; listArchive(p) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Archive path — editable, picker end-icon, Done lists contents.
        OutlinedTextField(
            value = archivePath,
            onValueChange = { archivePath = it; if (it.isNotBlank()) listArchive(it) },
            label = { Text(stringResource(R.string.archive_file_path)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { archivePicker.launch(arrayOf("*/*")) }) {
                    Icon(painterResource(R.drawable.ic_archive), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        )

        // Contents card — visible once listed.
        if (items.isNotEmpty()) {
            OutlinedSectionCard {
                Column {
                    CardHeaderRow(
                        iconRes = R.drawable.ic_archive,
                        title = "${stringResource(R.string.archive_contents)} (${items.size})",
                        expanded = contentsExpanded,
                        onToggle = { contentsExpanded = !contentsExpanded }
                    )
                    if (contentsExpanded) {
                        CardDivider()
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp).padding(top = 8.dp, bottom = 12.dp)) {
                            items(items, key = { it.path }) { item ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .combinedClickable(onClick = {
                                            selected = if (selected.contains(item.path)) selected - item.path else selected + item.path
                                        })
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected.contains(item.path),
                                        onCheckedChange = { c -> selected = if (c) selected + item.path else selected - item.path }
                                    )
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                        if (item.size > 0) Text(
                                            "${item.size} bytes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (selected.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${selected.size} selected",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                TextButton(onClick = { selected = items.map { it.path }.toSet() }) { Text("All", style = MaterialTheme.typography.labelSmall) }
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = { selected = emptySet() }) { Text("✕", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Destination card — collapsed / expanded / editable states like the original.
        OutlinedSectionCard {
            Column {
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = { destExpanded = !destExpanded },
                        onLongClick = { destExpanded = true; destEditing = true }
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.ic_folder_open), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.extract_destination_label),
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        painterResource(if (destExpanded) R.drawable.ic_chevron_up else R.drawable.ic_expand_more),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (destExpanded) {
                    if (!destEditing) {
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(onLongClick = { destEditing = true }, onClick = {})
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                destCustom.ifBlank { "Auto: <archive>/Extracted" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3
                            )
                            Text(
                                stringResource(R.string.dest_long_press_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = destCustom,
                            onValueChange = { destCustom = it },
                            label = { Text(stringResource(R.string.destination_path)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { destEditing = false }) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Password with visibility toggle.
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.optional_password)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painterResource(if (passwordVisible) R.drawable.ic_chevron_up else R.drawable.ic_lock),
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // Extract — 56dp filled button, leading icon.
        Button(
            onClick = { startExtraction() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(painterResource(R.drawable.ic_archive), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.extract))
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
