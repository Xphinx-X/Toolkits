package com.toolkits.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.toolkits.app.data.preferences.ToolkitsPreferences
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.ui.navigation.ToolkitsNavHost
import com.toolkits.app.ui.theme.ToolkitsTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: UserPreferencesRepository
    private var hasStorageAccess by mutableStateOf(false)
    private var showStorageDialog by mutableStateOf(false)
    private var storageAsked by mutableStateOf(false)

    private val storagePerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStorageState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UserPreferencesRepository(applicationContext)
        refreshStorageState()
        enableEdgeToEdge()
        setContent {
            val state by prefs.preferences.collectAsState(initial = ToolkitsPreferences())
            ToolkitsTheme(
                themeMode = state.themeMode,
                colorScheme = state.colorScheme,
                dynamicColor = state.dynamicColor
            ) {
                ToolkitsNavHost(
                    prefs = prefs,
                    hasStorageAccess = hasStorageAccess,
                    showStorageDialog = showStorageDialog,
                    onDismissStorageDialog = { showStorageDialog = false },
                    onGrantStorage = { grantStorageAccess() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have granted all-files access in Settings and come back.
        val wasMissing = !hasStorageAccess
        refreshStorageState()
        if (wasMissing && hasStorageAccess) showStorageDialog = false
        // First-launch prompt, mirrors VIEW checkStoragePermission().
        if (!storageAsked && !hasStorageAccess) {
            storageAsked = true
            showStorageDialog = true
        }
    }

    private fun refreshStorageState() {
        hasStorageAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun grantStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePerms.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
