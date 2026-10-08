package com.toolkits.app.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.ui.theme.SeedMap
import kotlinx.coroutines.launch

// Port of Toolkits-VIEW SettingsActivity: seeds + dynamic + theme mode + paths + toggles + encryption + about.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, prefs: UserPreferencesRepository) {
    val state by prefs.preferences.collectAsState(initial = com.toolkits.app.data.preferences.ToolkitsPreferences())
    val scope = rememberCoroutineScope()
    var showTheme by remember { mutableStateOf(false) }
    var showEncryption by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Color scheme", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SeedMap.forEach { (name, color) ->
                            Surface(
                                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { scope.launch { prefs.setColorScheme(name) } },
                                color = color,
                                border = if (state.colorScheme == name) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                            ) {}
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Dynamic color"); Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Follow wallpaper (Android 12+)" else "Requires Android 12+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = state.dynamicColor, onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    }
                    Row(Modifier.fillMaxWidth().clickable { showTheme = true }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (state.themeMode == "light") Icons.Filled.LightMode else Icons.Filled.DarkMode, null)
                            Column { Text("Theme"); Text(state.themeMode.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text("Change", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text("Paths", style = MaterialTheme.typography.titleMedium)
            SettingTextRow("Default extract path", state.extractDirPath.ifBlank { "Auto: <source>/Extracted" }) {}
            SettingTextRow("Default archive path", state.archiveDirPath.ifBlank { "Auto: <source>/Archive" }) {}
            SettingToggleRow("Hide output path card", state.hideOutputPath) { scope.launch { prefs.setHideOutput(it) } }
            SettingToggleRow("Hide path suggestions", state.hidePathSuggest) { scope.launch { prefs.setHideSuggest(it) } }

            Text("ZIP Tools", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().clickable { showEncryption = true }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Default encryption"); Text(state.zipEncryption.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Change", color = MaterialTheme.colorScheme.primary)
            }

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("ToolKits 1.0 (Compose port) • Offline • No ads • minSdk 24 • target 36\nZIP/7z/TAR/RAR + text tools. Original: Toolkits-VIEW.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED").forEach { (v, l) ->
                        Row(Modifier.fillMaxWidth().clickable { scope.launch { prefs.setThemeMode(v) }; showTheme = false }.padding(12.dp)) {
                            Text(if (state.themeMode == v) "● $l" else "○ $l")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Close") } }
        )
    }
    if (showEncryption) {
        AlertDialog(
            onDismissRequest = { showEncryption = false },
            title = { Text("Default ZIP encryption") },
            text = {
                Column {
                    listOf("none" to "None", "aes128" to "AES-128", "aes256" to "AES-256").forEach { (v, l) ->
                        Row(Modifier.fillMaxWidth().clickable { scope.launch { prefs.setZipEncryption(v) }; showEncryption = false }.padding(12.dp)) {
                            Text(if (state.zipEncryption == v) "● $l" else "○ $l")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEncryption = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SettingTextRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(title); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
