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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.toolkits.app.constant.ACTION_ARCHIVE_COMPLETE
import com.toolkits.app.constant.ACTION_ARCHIVE_ERROR
import com.toolkits.app.constant.ACTION_ARCHIVE_PROGRESS
import com.toolkits.app.constant.EXTRA_ERROR_MESSAGE
import com.toolkits.app.constant.EXTRA_PROGRESS
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.helper.SafPathResolver
import com.toolkits.app.service.Archive7zService
import com.toolkits.app.service.ArchiveSplitZipService
import com.toolkits.app.service.ArchiveTarService
import com.toolkits.app.service.ArchiveZipService
import java.io.File
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

// Compose port of Toolkits-VIEW CompressFragment.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressScreen(prefs: UserPreferencesRepository) {
    val context = LocalContext.current
    val prefsState by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var format by remember { mutableStateOf("zip") } // zip|7z|tar
    var tarVariant by remember { mutableStateOf("gz") } // TAR_ONLY|gz|bzip2|xz|zstd|lzma
    var level by remember { mutableStateOf(5) }
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var solid by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var splitSize by remember { mutableStateOf("10") }
    var splitUnit by remember { mutableStateOf("MB") } // KB|MB|GB
    var destCustom by remember { mutableStateOf<String?>(null) }
    var archiveName by remember { mutableStateOf("Archive") }
    var status by remember { mutableStateOf("Add files, choose format, then compress.") }
    var busy by remember { mutableStateOf(false) }

    // Seed encryption toggle from Settings default (once, unless user changed it).
    var seededEncryption by remember { mutableStateOf(false) }
    LaunchedEffect(prefsState.zipEncryption) {
        if (!seededEncryption && prefsState.zipEncryption != "none") {
            encrypt = true
            seededEncryption = true
        }
    }

    fun resolveArchiveDest(): String {
        destCustom?.takeIf { it.isNotBlank() }?.let { return it }
        val base = prefsState.archiveDirPath.ifBlank { SafPathResolver.externalRoot() }
        val dir = File(base, "Archive")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_ARCHIVE_COMPLETE -> {
                        busy = false
                        status = "Archive created."
                        Toast.makeText(context, "Archive created", Toast.LENGTH_SHORT).show()
                    }
                    ACTION_ARCHIVE_ERROR -> {
                        busy = false
                        val err = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "An error occurred"
                        status = err
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                    ACTION_ARCHIVE_PROGRESS -> { busy = true }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_ARCHIVE_COMPLETE)
            addAction(ACTION_ARCHIVE_ERROR)
            addAction(ACTION_ARCHIVE_PROGRESS)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        onDispose { LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver) }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val paths = uris.mapNotNull { uri ->
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            SafPathResolver.resolveUriToPath(context, uri)
        }
        if (paths.isNotEmpty()) files = (files + paths).distinct()
        else if (uris.isNotEmpty()) Toast.makeText(context, "Could not resolve file path", Toast.LENGTH_SHORT).show()
    }
    val folderAddPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.treeUriToPath(context, uri)
        if (p != null && !files.contains(p)) files = files + p
        else if (p == null) Toast.makeText(context, "Could not resolve folder path", Toast.LENGTH_SHORT).show()
    }
    val destDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.treeUriToPath(context, uri)
        if (p != null) destCustom = p
        else Toast.makeText(context, "Could not resolve folder path", Toast.LENGTH_SHORT).show()
    }

    fun mapZipLevel(v: Int): CompressionLevel = when (v) {
        0 -> CompressionLevel.NO_COMPRESSION
        1 -> CompressionLevel.FASTEST
        2, 3 -> CompressionLevel.FAST
        4, 5, 6 -> CompressionLevel.NORMAL
        7, 8 -> CompressionLevel.MAXIMUM
        9 -> CompressionLevel.ULTRA
        else -> CompressionLevel.NORMAL
    }

    fun start() {
        if (files.isEmpty()) { status = "Add at least one file"; return }
        val missing = files.filter { !File(it).exists() }
        if (missing.isNotEmpty()) { status = "Missing: ${missing.first()}"; return }
        val jobId = try { com.toolkits.app.helper.FileOperationsDao(context).addFilesForJob(files) }
        catch (e: Exception) { status = "DB error: ${e.message}"; return }
        val dest = resolveArchiveDest().also { File(it).mkdirs() }
        val cleanName = archiveName.trim().ifBlank { "Archive" }
        val isEncrypted = encrypt && password.isNotEmpty()
        val intent = when {
            format == "zip" && split -> {
                val mult = when (splitUnit) { "KB" -> 1024L; "GB" -> 1024L * 1024L * 1024L; else -> 1024L * 1024L }
                var bytes = (splitSize.toLongOrNull() ?: 10) * mult
                if (bytes < 65536L) bytes = 65536L // Zip4j minimum 64KB
                Intent(context, ArchiveSplitZipService::class.java).apply {
                    putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                    putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, "$cleanName.zip")
                    putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                    putExtra(ServiceConstants.EXTRA_PASSWORD, password.ifEmpty { null })
                    putExtra(ServiceConstants.EXTRA_SPLIT_SIZE, bytes)
                }
            }
            format == "zip" -> Intent(context, ArchiveZipService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, "$cleanName.zip")
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                putExtra(ServiceConstants.EXTRA_PASSWORD, password.ifEmpty { null })
                putExtra(ServiceConstants.EXTRA_COMPRESSION_METHOD, CompressionMethod.DEFLATE)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, mapZipLevel(level))
                putExtra(ServiceConstants.EXTRA_IS_ENCRYPTED, isEncrypted)
                putExtra(ServiceConstants.EXTRA_ENCRYPTION_METHOD, EncryptionMethod.AES)
                putExtra(ServiceConstants.EXTRA_AES_STRENGTH, AesKeyStrength.KEY_STRENGTH_256)
            }
            format == "7z" -> Intent(context, Archive7zService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, "$cleanName.7z")
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                putExtra(ServiceConstants.EXTRA_PASSWORD, password.ifEmpty { null })
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
                putExtra(ServiceConstants.EXTRA_SOLID, solid)
                putExtra(ServiceConstants.EXTRA_THREAD_COUNT, -1)
            }
            else -> Intent(context, ArchiveTarService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, cleanName)
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                // Match VIEW compression_format_values exactly.
                val compressionFormat = when (tarVariant) {
                    "TAR_ONLY" -> "TAR_ONLY"; "gz" -> "gz"; "bzip2" -> "bzip2"
                    "xz" -> "xz"; "zstd" -> "zstd"; "lzma" -> "lzma"; else -> "TAR_ONLY"
                }
                putExtra(ServiceConstants.EXTRA_COMPRESSION_FORMAT, compressionFormat)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        busy = true
        status = "Archive started — see notification."
    }

    val maxLevel = if (format == "tar" && tarVariant != "TAR_ONLY") 22 else 9

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Add files (${files.size})") }
            OutlinedButton(onClick = { folderAddPicker.launch(null) }, modifier = Modifier.weight(1f)) { Text("Add folder") }
            OutlinedButton(onClick = { files = emptyList() }) { Text("Clear") }
        }
        if (files.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(files, key = { it }) { f ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(File(f).name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(f, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        OutlinedButton(onClick = { files = files - f }) { Text("×") }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = format == "zip", onClick = { format = "zip" }, label = { Text("ZIP") })
            FilterChip(selected = format == "7z", onClick = { format = "7z" }, label = { Text("7Z") })
            FilterChip(selected = format == "tar", onClick = { format = "tar" }, label = { Text("TAR") })
        }
        if (format == "tar") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TAR_ONLY" to "TAR", "gz" to "GZ", "bzip2" to "BZ2", "xz" to "XZ", "zstd" to "ZST", "lzma" to "LZMA").forEach { (v, l) ->
                    FilterChip(selected = tarVariant == v, onClick = { tarVariant = v }, label = { Text(l) })
                }
            }
        }
        Text("Level: $level (0–$maxLevel)")
        Slider(value = level.toFloat(), onValueChange = { level = it.toInt().coerceIn(0, maxLevel) }, valueRange = 0f..maxLevel.toFloat(), steps = maxLevel)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Encrypt (ZIP/7z AES)")
            Switch(checked = encrypt, onCheckedChange = { encrypt = it })
        }
        if (encrypt) OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Archive password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (format == "7z") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solid archive"); Switch(checked = solid, onCheckedChange = { solid = it })
            }
        }
        if (format == "zip") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Split ZIP"); Switch(checked = split, onCheckedChange = { split = it })
            }
            if (split) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = splitSize, onValueChange = { splitSize = it }, label = { Text("Part size") }, modifier = Modifier.weight(1f), singleLine = true)
                    listOf("KB", "MB", "GB").forEach { u -> FilterChip(selected = splitUnit == u, onClick = { splitUnit = u }, label = { Text(u) }) }
                }
                Text("Minimum 64KB per part (original rule).", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(value = archiveName, onValueChange = { archiveName = it }, label = { Text("Archive name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (!prefsState.hideOutputPath) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = destCustom ?: resolveArchiveDest(),
                    onValueChange = { destCustom = it.ifBlank { null } },
                    label = { Text("Save to (blank = auto)") },
                    modifier = Modifier.weight(1f), singleLine = true, maxLines = 2
                )
                OutlinedButton(onClick = { destDirPicker.launch(null) }) { Text("Pick") }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Create archive") }
    }
}
