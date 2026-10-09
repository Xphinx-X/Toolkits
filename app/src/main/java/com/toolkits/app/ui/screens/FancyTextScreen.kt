package com.toolkits.app.ui.screens

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolkits.app.R
import com.toolkits.app.helper.FancyTextStyles
import com.toolkits.app.ui.components.OutlinedSectionCard
import com.toolkits.app.ui.components.SectionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Mirrors Toolkits-VIEW activity_fancy_text.xml: input (1-3 lines) →
// All/Discord filter → TEXT STYLES label → single card with 420dp list +
// dividers + per-row copy button → SYMBOLS label → horizontal category
// chips → single card with 240dp grid.
@Composable
fun FancyTextScreen(onBack: () -> Unit) {
    var rawInput by remember { mutableStateOf("Hello Toolkits") }
    var liveInput by remember { mutableStateOf("Hello Toolkits") }
    var discordOnly by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var symbolsReady by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun copyText(text: String) {
        clipboard.setText(AnnotatedString(text))
        // VIEW shows Snackbar only pre-Tiramisu (system shows its own popup on 33+).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbar.showSnackbar("Copied!") }
        }
    }

    // 120ms debounce like VIEW onTextChanged — typing doesn't re-convert per keystroke.
    LaunchedEffect(rawInput) {
        delay(120)
        liveInput = rawInput
    }
    // Deferred symbol setup so Back during first frames never hangs (VIEW delay(80)).
    LaunchedEffect(Unit) {
        delay(80)
        if (category == null) category = FancyTextStyles.symbolCategories.firstOrNull()?.name
        symbolsReady = true
    }

    val styles = remember(discordOnly) {
        if (discordOnly) FancyTextStyles.discordOnly else FancyTextStyles.all
    }

    Scaffold(
        topBar = {
            com.toolkits.app.ui.components.ToolkitsTopBar(title = "Fancy Text", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
            OutlinedTextField(
                value = rawInput,
                onValueChange = { rawInput = it },
                label = { Text("Type your text here…") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                minLines = 1, maxLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                FilterChip(selected = !discordOnly, onClick = { discordOnly = false }, label = { Text("All Styles") })
                FilterChip(selected = discordOnly, onClick = { discordOnly = true }, label = { Text("Discord") })
            }

            SectionLabel("TEXT STYLES")
            OutlinedSectionCard {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    userScrollEnabled = true
                ) {
                    itemsIndexed(styles, key = { _, s -> s.name }) { idx, style ->
                        val preview = try { style.convert(liveInput.ifBlank { "Abc" }) } catch (_: Exception) { liveInput }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                // Preview is the hero (18sp), name is the secondary label.
                                Text(preview, fontSize = 18.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
                                Text(style.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                            }
                            IconButton(onClick = { copyText(preview) }, modifier = Modifier.size(44.dp)) {
                                Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (idx < styles.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            SectionLabel("SYMBOLS")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FancyTextStyles.symbolCategories.forEachIndexed { i, cat ->
                    val selected = (category ?: FancyTextStyles.symbolCategories.firstOrNull()?.name) == cat.name
                    FilterChip(selected = selected, onClick = { category = cat.name }, label = { Text(cat.name) })
                }
            }
            OutlinedSectionCard {
                if (!symbolsReady) {
                    Text("Loading symbols…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                } else {
                    val symbols = FancyTextStyles.symbolCategories.firstOrNull { it.name == category }?.symbols ?: emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = true
                    ) {
                        items(symbols) { s ->
                            AssistChip(onClick = { copyText(s) }, label = { Text(s, fontSize = 18.sp) })
                        }
                    }
                }
            }
        }
    }
}
