package com.toolkits.app.ui.screens

import android.os.Build
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toolkits.app.R
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
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
    val state by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
    val scope = rememberCoroutineScope()
    var showTheme by remember { mutableStateOf(false) }
    var showEncryption by remember { mutableStateOf(false) }

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
                    // Color scheme title + desc + swatch row.
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
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SeedMap.forEach { (name, color) ->
                                Surface(
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .clickable { scope.launch { prefs.setColorScheme(name) } },
                                    color = color,
                                    border = if (state.colorScheme == name)
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                                ) {}
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    // Dynamic color row.
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Dynamic Color", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Follow wallpaper colors"
                                else "Requires Android 12+",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    // Theme mode row → dialog.
                    Row(
                        Modifier.fillMaxWidth().clickable { showTheme = true }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Theme", style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.themeMode.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    PathRow("Default extract path", state.extractDirPath.ifBlank { "Auto: <source>/Extracted" })
                    HorizontalDivider()
                    PathRow("Default archive path", state.archiveDirPath.ifBlank { "Auto: <source>/Archive" })
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
private fun PathRow(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
