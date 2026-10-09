package com.toolkits.app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.toolkits.app.R
import com.toolkits.app.constant.ACTION_EXTRACTION_COMPLETE
import com.toolkits.app.constant.ACTION_EXTRACTION_ERROR
import com.toolkits.app.constant.ACTION_EXTRACTION_PROGRESS
import com.toolkits.app.constant.EXTRA_ERROR_MESSAGE
import com.toolkits.app.constant.EXTRA_PROGRESS
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.helper.FileOperationsDao
import com.toolkits.app.helper.MultipartArchiveHelper
import com.toolkits.app.helper.SafPathResolver
import com.toolkits.app.model.ArchiveItem
import com.toolkits.app.service.ExtractArchiveService
import com.toolkits.app.ui.components.CardDivider
import com.toolkits.app.ui.components.CardHeaderRow
import com.toolkits.app.ui.components.OutlinedSectionCard
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory

private val COMPRESSOR_EXTENSIONS = setOf("xz", "bz2", "gz", "gzip", "zst", "zstd", "lzma", "lz4")

// Compose mirror of Toolkits-VIEW ExtractFragment + fragment_extract.xml.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtractScreen(prefs: UserPreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsState by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
    var archivePath by remember { mutableStateOf("") }
    var destCustom by remember { mutableStateOf<String?>(null) }
    var destExpanded by remember { mutableStateOf(false) }
    var destEditing by remember { mutableStateOf(false) }
    var destEditText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ArchiveItem>>(emptyList()) }
    var contentsExpanded by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    fun resolveExtractDest(): String {
        destCustom?.takeIf { it.isNotBlank() }?.let { return it }
        val base = prefsState.extractDirPath.ifBlank { SafPathResolver.externalRoot() }
        val dir = File(base, "Extracted")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }
    val effectiveDest = resolveExtractDest()

    // Completion / error / progress feedback — mirrors VIEW broadcastReceiver.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_EXTRACTION_COMPLETE -> {
                        busy = false; progress = 0
                        status = ""
                        Toast.makeText(context, context.getString(R.string.extraction_completed), Toast.LENGTH_SHORT).show()
                    }
                    ACTION_EXTRACTION_ERROR -> {
                        busy = false; progress = 0
                        val err = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
                            ?: context.getString(R.string.general_error_msg)
                        status = err
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                    ACTION_EXTRACTION_PROGRESS -> {
                        progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                        busy = true
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_EXTRACTION_COMPLETE)
            addAction(ACTION_EXTRACTION_ERROR)
            addAction(ACTION_EXTRACTION_PROGRESS)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        onDispose { LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver) }
    }

    fun listArchive(path: String) {
        val f = File(path)
        if (!f.exists() || !f.isFile) return
        if (!f.canRead()) {
            items = emptyList(); selected = emptySet()
            status = "Cannot read file — grant storage access (see Home banner) or re-pick it"
            return
        }
        // Non-archives show nothing (VIEW hides the card) — never a stub row.
        if (!SafPathResolver.isArchiveFile(path)) {
            items = emptyList(); selected = emptySet(); status = ""
            return
        }
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val ext = f.extension.lowercase()
                val found: List<ArchiveItem> = when {
                    ext == "zip" -> {
                        val z = if (password.isNotBlank()) ZipFile(f, password.toCharArray()) else ZipFile(f)
                        z.fileHeaders.map { h ->
                            ArchiveItem(h.fileName, h.fileName, h.uncompressedSize, h.isDirectory, h.lastModifiedTime)
                        }
                    }
                    ext == "rar" || f.name.matches(Regex(".*\\.part\\d+\\.rar", RegexOption.IGNORE_CASE)) -> {
                        try {
                            com.github.junrar.Archive(f).use { arc ->
                                arc.fileHeaders.map { h ->
                                    ArchiveItem(h.fileName, h.fileName, h.fullUnpackSize, h.isDirectory, h.mTime?.time ?: 0L)
                                }
                            }
                        } catch (_: Exception) {
                            listOf(ArchiveItem("(Could not read RAR contents)", "", 0, false, 0))
                        }
                    }
                    ext == "7z" -> {
                        try {
                            val raf = RandomAccessFile(f, "r")
                            val inStream = RandomAccessFileInStream(raf)
                            try {
                                val inArchive = SevenZip.openInArchive(null, inStream)
                                try {
                                    buildList {
                                        for (i in 0 until inArchive.numberOfItems) {
                                            val itemPath = inArchive.getStringProperty(i, PropID.PATH) ?: continue
                                            if (itemPath.isBlank()) continue
                                            val isFolder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                                            val size = inArchive.getProperty(i, PropID.SIZE) as? Long ?: 0L
                                            val modDate = inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date
                                            add(ArchiveItem(itemPath, itemPath, size, isFolder, modDate?.time ?: 0L))
                                        }
                                    }
                                } finally { inArchive.close() }
                            } finally { inStream.close(); raf.close() }
                        } catch (_: Exception) {
                            listOf(ArchiveItem("(Could not read 7z contents)", "", 0, false, 0))
                        }
                    }
                    ext == "tar" -> {
                        try {
                            TarArchiveInputStream(FileInputStream(f)).use { tarInput ->
                                buildList {
                                    var entry: TarArchiveEntry? = tarInput.nextEntry
                                    while (entry != null) {
                                        add(ArchiveItem(entry.name, entry.name, entry.size, entry.isDirectory, entry.modTime?.time ?: 0L))
                                        entry = tarInput.nextEntry
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            listOf(ArchiveItem("(Could not read TAR contents)", "", 0, false, 0))
                        }
                    }
                    ext in COMPRESSOR_EXTENSIONS -> {
                        val innerName = f.nameWithoutExtension
                        if (innerName.endsWith(".tar", ignoreCase = true)) {
                            try {
                                FileInputStream(f).use { fis ->
                                    BufferedInputStream(fis).use { bis ->
                                        val compressorName = CompressorStreamFactory.detect(bis)
                                        CompressorStreamFactory()
                                            .createCompressorInputStream(compressorName, bis).use { cis ->
                                                TarArchiveInputStream(cis).use { tarInput ->
                                                    buildList {
                                                        var entry: TarArchiveEntry? = tarInput.nextEntry
                                                        while (entry != null) {
                                                            add(ArchiveItem(entry.name, entry.name, entry.size, entry.isDirectory, entry.modTime?.time ?: 0L))
                                                            entry = tarInput.nextEntry
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                }
                            } catch (_: Exception) {
                                listOf(ArchiveItem(innerName, innerName, 0, false, f.lastModified()))
                            }
                        } else {
                            listOf(ArchiveItem(innerName, innerName, 0, false, f.lastModified()))
                        }
                    }
                    MultipartArchiveHelper.isMultipartArchive(f) -> {
                        listOf(ArchiveItem("(multipart — contents resolved at extract)", "", 0, false, 0))
                    }
                    else -> emptyList()
                }
                withContext(Dispatchers.Main) { items = found; selected = emptySet(); status = ""; busy = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "List failed: ${e.message}"; busy = false }
            }
        }
    }

    fun startExtraction(selectedPaths: List<String> = emptyList()) {
        val src = archivePath.ifBlank {
            status = context.getString(R.string.select_file_to_extract); return
        }
        val srcFile = File(src)
        if (!srcFile.exists() || !srcFile.isFile) {
            status = context.getString(R.string.select_file_to_extract); return
        }
        val destination = effectiveDest.also { File(it).mkdirs() }
        val jobId = try { FileOperationsDao(context).addFilesForJob(listOf(src)) }
        catch (e: Exception) { status = "DB error: ${e.message}"; return }
        val intent = Intent(context, ExtractArchiveService::class.java).apply {
            putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
            putExtra(ServiceConstants.EXTRA_ARCHIVE_PATH, src)
            putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, destination)
            putExtra(ServiceConstants.EXTRA_PASSWORD, password.ifEmpty { null })
            val realSelection = selectedPaths.filter { it.isNotBlank() }
            if (realSelection.isNotEmpty()) putStringArrayListExtra(ServiceConstants.EXTRA_SELECTED_PATHS, ArrayList(realSelection))
        }
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        busy = true; progress = 0; selected = emptySet()
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.resolveUriToPath(context, uri)
        if (p != null) { archivePath = p; listArchive(p) }
        else Toast.makeText(context, context.getString(R.string.path_resolve_error), Toast.LENGTH_SHORT).show()
    }
    val destDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.treeUriToPath(context, uri)
        if (p != null) { destCustom = p; destEditText = p; destEditing = false }
        else Toast.makeText(context, context.getString(R.string.path_resolve_error), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Archive path — editable, picker end-icon, lists only for real files.
        OutlinedTextField(
            value = archivePath,
            onValueChange = { typed ->
                archivePath = typed
                val tf = File(typed.trim())
                if (tf.exists() && tf.isFile) listArchive(typed.trim())
            },
            label = { Text(stringResource(R.string.archive_file_path)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val typed = archivePath.trim()
                val tf = File(typed)
                if (typed.isNotEmpty() && tf.exists() && tf.isFile) listArchive(typed)
            }),
            trailingIcon = {
                IconButton(onClick = { archivePicker.launch(SafPathResolver.ARCHIVE_MIME_TYPES) }) {
                    Icon(painterResource(R.drawable.ic_archive), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        )

        // Contents card — hidden for non-archives / empty.
        val visibleItems = items.filter { it.path.isNotBlank() || it.name.startsWith("(") }
        androidx.compose.animation.AnimatedVisibility(visible = visibleItems.isNotEmpty()) {
            OutlinedSectionCard {
                Column {
                    CardHeaderRow(
                        iconRes = R.drawable.ic_archive,
                        title = "${stringResource(R.string.archive_contents)} (${visibleItems.size})",
                        expanded = contentsExpanded,
                        onToggle = { contentsExpanded = !contentsExpanded }
                    )
                    androidx.compose.animation.AnimatedVisibility(visible = contentsExpanded) {
                        Column {
                        CardDivider()
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp).padding(top = 8.dp, bottom = 12.dp)) {
                            items(visibleItems, key = { it.path.ifBlank { it.name } }) { item ->
                                val selectable = item.path.isNotBlank()
                                Row(
                                    Modifier.fillMaxWidth()
                                        .combinedClickable(
                                            enabled = selectable,
                                            onClick = {
                                                selected = if (selected.contains(item.path)) selected - item.path else selected + item.path
                                            })
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected.contains(item.path),
                                        enabled = selectable,
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
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val count = selected.size
                            if (count > 0) {
                                Text("$count selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = {
                                    val real = visibleItems.map { it.path }.filter { it.isNotBlank() }
                                    if (real.isEmpty()) {
                                        Toast.makeText(context, "Select at least one file", Toast.LENGTH_SHORT).show()
                                    } else startExtraction(real)
                                }) { Text("Extract selected") }
                                TextButton(onClick = { selected = visibleItems.map { it.path }.filter { it.isNotBlank() }.toSet() }) { Text("All", style = MaterialTheme.typography.labelSmall) }
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = { selected = emptySet() }) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
                            } else {
                                Text("Tap to select files (optional)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { selected = visibleItems.map { it.path }.filter { it.isNotBlank() }.toSet() }) { Text("All", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Destination card — hidden when the user enabled hide_output_path.
        if (!prefsState.hideOutputPath) {
            OutlinedSectionCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = { destExpanded = !destExpanded },
                            onLongClick = { destExpanded = true; destEditing = true; destEditText = destCustom ?: "" }
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
                        androidx.compose.animation.AnimatedContent(targetState = destExpanded, label = "destChevron") { ex ->
                            Icon(
                                painterResource(if (ex) R.drawable.ic_chevron_up else R.drawable.ic_expand_more),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = destExpanded) {
                        if (!destEditing) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(onLongClick = { destEditing = true; destEditText = destCustom ?: "" }, onClick = {})
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    effectiveDest,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3
                                )
                                IconButton(onClick = { destDirPicker.launch(null) }) {
                                    Icon(painterResource(R.drawable.ic_folder_open), contentDescription = "Pick folder", tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    stringResource(R.string.dest_long_press_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = destEditText,
                                onValueChange = { destEditText = it },
                                label = { Text(stringResource(R.string.destination_path)) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                                singleLine = true,
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = { destDirPicker.launch(null) }) {
                                            Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            destCustom = destEditText.trim().ifBlank { null }
                                            destEditing = false
                                        }) {
                                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Password with visibility toggle.
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; if (archivePath.isNotBlank()) listArchive(archivePath) },
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
            onClick = { startExtraction(selected.toList()) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(painterResource(R.drawable.ic_archive), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.extract))
        }

        if (busy) {
            if (progress > 0) LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
