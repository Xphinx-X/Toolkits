package com.toolkits.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toolkits.app.R
import com.toolkits.app.ui.components.ToolRowCard
import com.toolkits.app.ui.components.ToolkitsTopBar

// Mirrors Toolkits-VIEW activity_main.xml: surface bg, toolbar + settings menu,
// filled welcome card (24dp, primaryContainer, DisplaySmall/BodyLarge),
// "Tools" TitleMedium section, 16dp tool cards with direct 48dp icons.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onZipTools: () -> Unit, onTextTools: () -> Unit, onSettings: () -> Unit) {
    Scaffold(
        topBar = {
            ToolkitsTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            // Welcome — Widget.ToolKits.CardView.Filled
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.tools_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ToolRowCard(
                title = stringResource(R.string.tool_zip_tools),
                subtitle = stringResource(R.string.tool_zip_tools_desc),
                iconRes = R.drawable.ic_archive,
                circledIcon = false,
                onClick = onZipTools
            )

            Spacer(Modifier.height(12.dp))

            ToolRowCard(
                title = "Text Tools",
                subtitle = "Base64, Projects, and more text utilities",
                iconRes = R.drawable.ic_text_tools,
                circledIcon = false,
                onClick = onTextTools
            )
        }
    }
}
