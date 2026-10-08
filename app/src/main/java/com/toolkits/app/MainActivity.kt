package com.toolkits.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.ui.navigation.ToolkitsNavHost
import com.toolkits.app.ui.theme.ToolkitsTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        prefs = UserPreferencesRepository(applicationContext)
        enableEdgeToEdge()
        setContent {
            val state by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
            ToolkitsTheme(
                themeMode = state.themeMode,
                colorScheme = state.colorScheme,
                dynamicColor = state.dynamicColor
            ) {
                ToolkitsNavHost(prefs = prefs)
            }
        }
    }
}
