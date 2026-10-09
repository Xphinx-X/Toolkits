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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.toolkits.app.R
import com.toolkits.app.constant.ACTION_ARCHIVE_COMPLETE
import com.toolkits.app.constant.ACTION_ARCHIVE_ERROR
import com.toolkits.app.constant.ACTION_ARCHIVE_PROGRESS
import com.toolkits.app.constant.EXTRA_ERROR_MESSAGE
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.helper.FileUtils
import com.toolkits.app.helper.SafPathResolver
import com.toolkits.app.service.Archive7zService
import com.toolkits.app.service.ArchiveSplitZipService
import com.toolkits.app.service.ArchiveTarService
import com.toolkits.app.service.ArchiveZipService
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.ui.components.CardDivider
import com.toolkits.app.ui.components.CardHeaderRow
import com.toolkits.app.ui.components.OutlinedSectionCard
import java.io.File
import kotlinx.coroutines.flow.first
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

// Mirrors Toolkits-VIEW fragment_compress.xml order: type label + chips →
// TAR dropdown → archive name → select files/folder → selected card →
// dest card → encrypt → password → solid/split → level → compress → progress.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CompressScreen(prefs: UserPreferencesRepository) {
    val context = LocalContext.current
    val prefsState by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
    // Gate hideable cards until DataStore emits: avoids a one-frame flash where
    // the dest card is visible and then animates away when hide_output_path is on.
    var prefsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { prefs.preferences.first(); prefsReady = true }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var filesExpanded by remember { mutableStateOf(true) }
    var removeMode by remember { mutableStateOf(false) }
    var removeSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var format by remember { mutableStateOf("zip") } // zip|7z|tar
    var tarVariant by remember { mutableStateOf("TAR_ONLY") }
    var tarMenuOpen by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(5) }
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var solid by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var splitSize by remember { mutableStateOf("100") }
    var splitUnit by remember { mutableStateOf("MB") } // KB|MB|GB
    var destCustom by remember { mutableStateOf<String?>(null) }
    var destExpanded by remember { mutableStateOf(false) }
    var destEditing by remember { mutableStateOf(false) }
    var destEditText by remember { mutableStateOf("") }
    var archiveName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val tarEntries = context.resources.getStringArray(R.array.compression_format_entries)
    val tarValues = context.resources.getStringArray(R.array.compression_format_values)
    val tarLabel = tarEntries.getOrNull(tarValues.indexOf(tarVariant)) ?: tarEntries.firstOrNull().orEmpty()

    // Seed encryption toggle from Settings default (once, unless user changed it).
    var seededEncryption by remember { mutableStateOf(false) }
    LaunchedEffect(prefsState.zipEncryption) {
        if (!seededEncryption && prefsState.zipEncryption != "none") {
            encrypt = true
            seededEncryption = true
        }
    }
    // Slider range follows format like VIEW (9 for ZIP/7z/TAR_ONLY, 22 for compressed TAR).
    val maxLevel = if (format == "tar" && tarVariant != "TAR_ONLY") 22 else 9
    LaunchedEffect(maxLevel) { level = level.coerceIn(0, maxLevel) }

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
                        status = ""
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
        if (paths.isNotEmpty()) { files = (files + paths).distinct(); filesExpanded = true; removeMode = false; removeSelected = emptySet() }
        else if (uris.isNotEmpty()) Toast.makeText(context, "Could not resolve file path", Toast.LENGTH_SHORT).show()
    }
    val folderAddPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.treeUriToPath(context, uri)
        if (p != null && !files.contains(p)) { files = files + p; filesExpanded = true }
        else if (p == null) Toast.makeText(context, "Could not resolve folder path", Toast.LENGTH_SHORT).show()
    }
    val destDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.treeUriToPath(context, uri)
        if (p != null) { destCustom = p; destEditText = p; destEditing = false }
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
        if (files.isEmpty()) { status = context.getString(R.string.no_files_selected); return }
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
                var bytes = (splitSize.toLongOrNull() ?: 100) * mult
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
                putExtra(ServiceConstants.EXTRA_COMPRESSION_FORMAT, tarVariant)
                putExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, level)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        busy = true
        status = "Archive started — see notification."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Archive type label + chips.
        Text(
            stringResource(R.string.select_archive_type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            FilterChip(selected = format == "zip", onClick = { format = "zip" }, label = { Text(stringResource(R.string.format_zip)) })
            FilterChip(selected = format == "7z", onClick = { format = "7z" }, label = { Text(stringResource(R.string.format_7z)) })
            FilterChip(selected = format == "tar", onClick = { format = "tar" }, label = { Text(stringResource(R.string.format_tar)) })
        }

        // TAR compression format dropdown (TAR only).
        AnimatedVisibility(visible = format == "tar") {
            ExposedDropdownMenuBox(
                expanded = tarMenuOpen,
                onExpandedChange = { tarMenuOpen = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                OutlinedTextField(
                    value = tarLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.compression_format)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tarMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = tarMenuOpen, onDismissRequest = { tarMenuOpen = false }) {
                    tarEntries.forEachIndexed { i, entry ->
                        DropdownMenuItem(
                            text = { Text(entry) },
                            onClick = { tarVariant = tarValues.getOrElse(i) { "TAR_ONLY" }; tarMenuOpen = false }
                        )
                    }
                }
            }
        }

        // Archive name.
        OutlinedTextField(
            value = archiveName,
            onValueChange = { archiveName = it },
            label = { Text(stringResource(R.string.archive_name)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true
        )

        // Select files + Add folder side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.select_files))
            }
            OutlinedButton(onClick = { folderAddPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Add Folder")
            }
        }

        // Selected files card (only once files exist).
        AnimatedVisibility(visible = files.isNotEmpty()) {
            OutlinedSectionCard {
                Column {
                    CardHeaderRow(
                        iconRes = R.drawable.ic_archive,
                        title = context.getString(R.string.files_selected, files.size),
                        expanded = filesExpanded,
                        onToggle = { filesExpanded = !filesExpanded }
                    )
                    AnimatedVisibility(visible = filesExpanded) {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            CardDivider()
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(top = 8.dp), userScrollEnabled = true) {
                                items(files, key = { it }) { f ->
                                    val file = File(f)
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (removeMode) {
                                                        removeSelected = if (removeSelected.contains(f)) removeSelected - f else removeSelected + f
                                                    }
                                                },
                                                onLongClick = {
                                                    removeMode = true
                                                    removeSelected = removeSelected + f
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (removeMode) {
                                            Checkbox(
                                                checked = removeSelected.contains(f),
                                                onCheckedChange = { c ->
                                                    removeSelected = if (c) removeSelected + f else removeSelected - f
                                                }
                                            )
                                        } else {
                                            Icon(
                                                painterResource(if (file.isDirectory) R.drawable.ic_folder_open else R.drawable.ic_archive),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(
                                                FileUtils.formatFileSize(file.length()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(visible = removeMode) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${removeSelected.size} selected",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    TextButton(onClick = {
                                        files = files - removeSelected
                                        removeSelected = emptySet()
                                        removeMode = false
                                    }) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(onClick = { removeSelected = emptySet(); removeMode = false }) { Text("✕", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Destination card — same behaviour as extract tab (GONE, no entry
        // flicker, when the user enabled hide_output_path).
        AnimatedVisibility(visible = prefsReady && !prefsState.hideOutputPath) {
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
                            stringResource(R.string.archive_destination_label),
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedContent(targetState = destExpanded, label = "compressDestChevron") { ex ->
                            Icon(
                                painterResource(if (ex) R.drawable.ic_chevron_up else R.drawable.ic_expand_more),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AnimatedVisibility(visible = destExpanded) {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            if (!destEditing) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .combinedClickable(onLongClick = { destEditing = true; destEditText = destCustom ?: "" }, onClick = {})
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        resolveArchiveDest(),
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
                                    value = destEditText,
                                    onValueChange = { destEditText = it },
                                    label = { Text(stringResource(R.string.destination_path)) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    singleLine = true,
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { destDirPicker.launch(null) }) {
                                                Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
            }
            Spacer(Modifier.height(12.dp))
        }

        // Encryption switch (full-width row like VIEW).
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.encryption), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = encrypt, onCheckedChange = { encrypt = it })
        }

        // Password field (animated).
        AnimatedVisibility(visible = encrypt) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.enter_password)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }

        // 7z solid switch (7z only).
        AnimatedVisibility(visible = format == "7z") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.solid_archive), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = solid, onCheckedChange = { solid = it })
            }
        }

        // Split switch (ZIP only) + size layout.
        AnimatedVisibility(visible = format == "zip") {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.split_archive), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = split, onCheckedChange = { split = it })
                }
                AnimatedVisibility(visible = split) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            FilterChip(selected = splitUnit == "KB", onClick = { splitUnit = "KB" }, label = { Text(stringResource(R.string.split_unit_kb)) })
                            FilterChip(selected = splitUnit == "MB", onClick = { splitUnit = "MB" }, label = { Text(stringResource(R.string.split_unit_mb)) })
                            FilterChip(selected = splitUnit == "GB", onClick = { splitUnit = "GB" }, label = { Text(stringResource(R.string.split_unit_gb)) })
                        }
                        OutlinedTextField(
                            value = splitSize,
                            onValueChange = { splitSize = it },
                            label = { Text(stringResource(R.string.split_size_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            suffix = { Text(splitUnit) }
                        )
                    }
                }
            }
        }

        // Compression level label + slider.
        Text(
            stringResource(R.string.compression_level),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = level.toFloat(),
            onValueChange = { level = it.toInt().coerceIn(0, maxLevel) },
            valueRange = 0f..maxLevel.toFloat(),
            steps = maxLevel,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        )

        // Compress — 56dp filled button with icon.
        Button(
            onClick = { start() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(painterResource(R.drawable.ic_compress), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.compress))
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
