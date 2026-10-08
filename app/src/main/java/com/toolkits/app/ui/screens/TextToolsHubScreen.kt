package com.toolkits.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toolkits.app.R
import com.toolkits.app.ui.components.ToolRowCard
import com.toolkits.app.ui.components.ToolkitsTopBar

// Mirrors Toolkits-VIEW activity_text_tools.xml: vertical list of 16dp cards,
// 52dp oval icon containers (primaryContainer + primary 28dp icons),
// 24dp trailing arrows. Order: Base64, Projects, Fancy Text, Text Info.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolsHubScreen(onBack: () -> Unit, onBase64: () -> Unit, onProjects: () -> Unit, onTextInfo: () -> Unit, onFancy: () -> Unit) {
    Scaffold(
        topBar = { ToolkitsTopBar(title = "Text Tools", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            ToolRowCard(
                title = "Base64",
                subtitle = "Encode and decode text to Base64 format",
                iconRes = R.drawable.ic_base64,
                circledIcon = true,
                onClick = onBase64
            )
            Spacer(Modifier.height(12.dp))
            ToolRowCard(
                title = "Projects",
                subtitle = "Create and edit project files in any text format",
                iconRes = R.drawable.ic_project,
                circledIcon = true,
                onClick = onProjects
            )
            Spacer(Modifier.height(12.dp))
            ToolRowCard(
                title = "Fancy Text",
                subtitle = "Unicode styles and symbols for Discord and more",
                iconRes = R.drawable.ic_wand,
                circledIcon = true,
                onClick = onFancy
            )
            Spacer(Modifier.height(12.dp))
            ToolRowCard(
                title = "Text Info",
                subtitle = "Words, lines, characters, emojis and top words",
                iconRes = R.drawable.ic_info,
                circledIcon = true,
                onClick = onTextInfo
            )
        }
    }
}
