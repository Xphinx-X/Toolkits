package com.toolkits.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toolkits.app.R
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.helper.SafPathResolver
import com.toolkits.app.ui.components.SectionLabel
import com.toolkits.app.ui.components.ToolkitsTopBar
import com.toolkits.app.ui.theme.SeedMap
import kotlinx.coroutines.launch

// Mirrors Toolkits-VIEW activity_settings.xml: primary SectionLabels,
// outlined cards with dividers, color-scheme title + desc + horizontal
// swatch row, dynamic switch, theme dialog row, paths, toggles, encryption.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, prefs: UserPreferencesRepository) {
    val context = LocalContext.current
    val state by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
    val scope = rememberCoroutineScope()
    var showTheme by remember { mutableStateOf(false) }
    var showEncryption by remember { mutableStateOf(false) }
    var pathTarget by remember { mutableStateOf<String?>(null) } // "extract"|"archive"
    var showManualPath by remember { mutableStateOf(false) }
    var manualPathText by remember { mutableStateOf("") }

    fun currentPathFor(target: String): String =
        if (target == "extract") state.extractDirPath.ifBlank { SafPathResolver.externalRoot() }
        else state.archiveDirPath.ifBlank { SafPathResolver.externalRoot() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val target = pathTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        val p = SafPathResolver.treeUriToPath(context, uri)
        if (p != null) {
            scope.launch {
                if (target == "extract") prefs.setExtractDir(p) else prefs.setArchiveDir(p)
            }
        } else Toast.makeText(context, "Could not resolve folder path", Toast.LENGTH_SHORT).show()
        pathTarget = null
    }

    Scaffold(
        topBar = { ToolkitsTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SectionLabel("Appearance")
            Spacer16()
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    // Color scheme title + desc + swatch row with labels.
                    // Tapping a seed selects it AND turns dynamic off (VIEW behavior);
                    // swatches dim while dynamic is on since the seed is inactive.
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(
                            "Color scheme",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Text(
                            "Seed color for the Material You palette",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                        val seedLabels = mapOf(
                            "green" to stringResource(R.string.color_green),
                            "blue" to stringResource(R.string.color_blue),
                            "purple" to stringResource(R.string.color_purple),
                            "red" to stringResource(R.string.color_red),
                            "orange" to stringResource(R.string.color_orange)
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SeedMap.forEach { (name, color) ->
                                val selected = state.colorScheme == name && !state.dynamicColor
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.alpha(if (state.dynamicColor) 0.45f else 1f)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                            .clickable {
                                                scope.launch {
                                                    prefs.setColorScheme(name)
                                                    prefs.setDynamicColor(false)
                                                }
                                            },
                                        color = color,
                                        border = if (selected)
                                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {}
                                    Text(
                                        seedLabels[name] ?: name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    // Dynamic color row — whole row toggles (switch itself not clickable
                    // so only one event fires). Hidden below Android 12 like VIEW.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { prefs.setDynamicColor(!state.dynamicColor) }
                            }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Dynamic Color", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Follow wallpaper colors",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.dynamicColor,
                                onCheckedChange = null
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    // Theme mode row → dialog, with light/dark icon like VIEW.
                    Row(
                        Modifier.fillMaxWidth().clickable { showTheme = true }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isDark = state.themeMode == "dark" || state.themeMode == "amoled"
                            Icon(
                                painterResource(if (isDark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text("Theme", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    state.themeMode.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionLabel("Paths")
            Spacer16()
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PathRow(
                        "Default extract path",
                        state.extractDirPath.ifBlank { "Auto: <source>/Extracted" },
                        onPick = { pathTarget = "extract"; folderPicker.launch(null) },
                        onManual = { pathTarget = "extract"; manualPathText = currentPathFor("extract"); showManualPath = true },
                        onReset = { scope.launch { prefs.setExtractDir("") } }
                    )
                    HorizontalDivider()
                    PathRow(
                        "Default archive path",
                        state.archiveDirPath.ifBlank { "Auto: <source>/Archive" },
                        onPick = { pathTarget = "archive"; folderPicker.launch(null) },
                        onManual = { pathTarget = "archive"; manualPathText = currentPathFor("archive"); showManualPath = true },
                        onReset = { scope.launch { prefs.setArchiveDir("") } }
                    )
                    HorizontalDivider()
                    ToggleRow("Hide output path card", state.hideOutputPath) { scope.launch { prefs.setHideOutput(it) } }
                    ToggleRow("Hide path suggestions", state.hidePathSuggest) { scope.launch { prefs.setHideSuggest(it) } }
                }
            }

            SectionLabel("ZIP Tools")
            Spacer16()
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { showEncryption = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Default encryption", style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.zipEncryption.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }

            SectionLabel("About")
            Spacer16()
            Text(
                "ToolKits 1.0 • Offline • No ads\nZIP, 7z, TAR, RAR + text tools.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED").forEach { (v, l) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { scope.launch { prefs.setThemeMode(v) }; showTheme = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.RadioButton(selected = state.themeMode == v, onClick = { scope.launch { prefs.setThemeMode(v) }; showTheme = false })
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Close") } }
        )
    }
    if (showManualPath) {
        AlertDialog(
            onDismissRequest = { showManualPath = false },
            title = { Text("Enter path manually") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = manualPathText,
                    onValueChange = { manualPathText = it },
                    label = { Text("Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = manualPathText.trim()
                    if (p.isNotEmpty()) {
                        scope.launch {
                            if (pathTarget == "extract") prefs.setExtractDir(p) else prefs.setArchiveDir(p)
                        }
                    }
                    showManualPath = false; pathTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showManualPath = false; pathTarget = null }) { Text("Cancel") } }
        )
    }
    if (showEncryption) {
        AlertDialog(
            onDismissRequest = { showEncryption = false },
            title = { Text("Default ZIP encryption") },
            text = {
                Column {
                    listOf("none" to "None", "aes128" to "AES-128", "aes256" to "AES-256").forEach { (v, l) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { scope.launch { prefs.setZipEncryption(v) }; showEncryption = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.RadioButton(selected = state.zipEncryption == v, onClick = { scope.launch { prefs.setZipEncryption(v) }; showEncryption = false })
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEncryption = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun Spacer16() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
}

@Composable
private fun PathRow(title: String, subtitle: String, onPick: () -> Unit, onManual: () -> Unit, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPick) { Text("Pick folder") }
            TextButton(onClick = onManual) { Text("Manual") }
            TextButton(onClick = onReset) { Text("Reset") }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
