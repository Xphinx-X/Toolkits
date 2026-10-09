package com.toolkits.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.toolkits.app.R
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.ui.components.ToolkitsTopBar

// Mirrors Toolkits-VIEW activity_zip_tools.xml: surface bg, toolbar with back,
// fixed/fill TabLayout under the bar, pager content below.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipToolsScreen(onBack: () -> Unit, prefs: UserPreferencesRepository) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { ToolkitsTopBar(title = stringResource(R.string.tool_zip_tools), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.extract)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.create_archive)) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (tab == 0) ExtractScreen(prefs) else CompressScreen(prefs)
        }
    }
}
