package com.toolkits.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.PathUtils
import com.toolkits.app.service.Archive7zService
import com.toolkits.app.service.ArchiveSplitZipService
import com.toolkits.app.service.ArchiveTarService
import com.toolkits.app.service.ArchiveZipService
import java.io.File
import java.util.UUID

// Compose port of Toolkits-VIEW CompressFragment: format chips, level slider, encryption,
// solid 7z, split ZIP, TAR variants, dest card, per-format service dispatch.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressScreen() {
    val context = LocalContext.current
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
    var destDir by remember { mutableStateOf("") }
    var archiveName by remember { mutableStateOf("Archive") }
    var status by remember { mutableStateOf("Add files, choose format, then compress.") }

    val maxLevel = if (format == "tar" && tarVariant != "TAR_ONLY") 22 else 9

    fun resolveUri(uri: Uri): String? {
        PathUtils.getPath(context, uri)?.takeIf { File(it).exists() }?.let { return it }
        return try {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.takeLast(60) ?: "picked_${UUID.randomUUID()}"
            val out = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            out.absolutePath
        } catch (_: Exception) { null }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val paths = uris.mapNotNull { uri ->
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            resolveUri(uri)
        }
        if (paths.isNotEmpty()) files = (files + paths).distinct()
    }

    fun start() {
        if (files.isEmpty()) { status = "Add at least one file"; return }
        // Stage exactly like VIEW CompressFragment: DAO returns the jobId the service reads.
        val jobId = try { com.toolkits.app.helper.FileOperationsDao(context).addFilesForJob(files) }
        catch (e: Exception) { status = "DB error: ${e.message}"; return }
        val dest = destDir.ifBlank { "" }
        val intent = when {
            format == "zip" && split -> Intent(context, ArchiveSplitZipService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, "$archiveName.zip")
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
                putExtra(ServiceConstants.EXTRA_PASSWORD, password)
                putExtra(ServiceConstants.EXTRA_IS_ENCRYPTED, encrypt)
                val mult = when (splitUnit) { "KB" -> 1024L; "GB" -> 1024L * 1024L * 1024L; else -> 1024L * 1024L }
                putExtra(ServiceConstants.EXTRA_SPLIT_SIZE, (splitSize.toLongOrNull() ?: 10) * mult)
            }
            format == "zip" -> Intent(context, ArchiveZipService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, "$archiveName.zip")
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
                putExtra(ServiceConstants.EXTRA_PASSWORD, password)
                putExtra(ServiceConstants.EXTRA_IS_ENCRYPTED, encrypt)
            }
            format == "7z" -> Intent(context, Archive7zService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, "$archiveName.7z")
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
                putExtra(ServiceConstants.EXTRA_PASSWORD, password)
                putExtra(ServiceConstants.EXTRA_SOLID, solid)
            }
            else -> Intent(context, ArchiveTarService::class.java).apply {
                putExtra(ServiceConstants.EXTRA_JOB_ID, jobId)
                putExtra(ServiceConstants.EXTRA_ARCHIVE_NAME, archiveName)
                putExtra(ServiceConstants.EXTRA_DESTINATION_PATH, dest)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_FORMAT, tarVariant)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        status = "Archive started — see notification."
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Add files (${files.size})") }
            OutlinedButton(onClick = { files = emptyList() }) { Text("Clear") }
        }
        if (files.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(files, key = { it }) { f ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(File(f).name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
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
        OutlinedTextField(value = destDir, onValueChange = { destDir = it }, label = { Text("Save to (blank = auto)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Create archive") }
    }
}
