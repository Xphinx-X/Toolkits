package com.toolkits.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolkits.app.helper.FancyTextStyles

// Port of Toolkits-VIEW FancyTextActivity: 51 styles, Discord filter, symbol grid, copy.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FancyTextScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("Hello Toolkits") }
    var discordOnly by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(FancyTextStyles.symbolCategories.firstOrNull()?.name ?: "") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val styles = remember(discordOnly) {
        if (discordOnly) FancyTextStyles.discordOnly else FancyTextStyles.all
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Fancy Text") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Input") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !discordOnly, onClick = { discordOnly = false }, label = { Text("All") })
                    FilterChip(selected = discordOnly, onClick = { discordOnly = true }, label = { Text("Discord-friendly") })
                }
            }
            items(styles, key = { it.name }) { style ->
                val preview = try { style.convert(input.ifBlank { "Abc" }) } catch (_: Exception) { input }
                Card(
                    onClick = {
                        clipboard.setText(AnnotatedString(preview))
                        Toast.makeText(context, "Copied ${style.name}", Toast.LENGTH_SHORT).show()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(preview, fontSize = 18.sp, maxLines = 2)
                        Text(style.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("Symbols", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FancyTextStyles.symbolCategories.forEach { cat ->
                        FilterChip(selected = category == cat.name, onClick = { category = cat.name }, label = { Text(cat.name) })
                    }
                }
            }
            item {
                val symbols = FancyTextStyles.symbolCategories.firstOrNull { it.name == category }?.symbols ?: emptyList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(symbols) { s ->
                        AssistChip(onClick = {
                            clipboard.setText(AnnotatedString(s))
                            Toast.makeText(context, "Copied $s", Toast.LENGTH_SHORT).show()
                        }, label = { Text(s, fontSize = 18.sp) })
                    }
                }
            }
        }
    }
}
