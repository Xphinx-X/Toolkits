package com.toolkits.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.toolkitsDataStore by preferencesDataStore(name = "toolkits_settings")

data class ToolkitsPreferences(
    val themeMode: String = "system", // system|light|dark|amoled
    val colorScheme: String = "green", // green|blue|purple|red|orange
    val dynamicColor: Boolean = true,
    val extractDirPath: String = "",
    val archiveDirPath: String = "",
    val hideOutputPath: Boolean = false,
    val hidePathSuggest: Boolean = false,
    val zipEncryption: String = "none" // none|aes128|aes256
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("key_theme_mode")
        val COLOR_SCHEME = stringPreferencesKey("key_color_scheme")
        val DYNAMIC_COLOR = booleanPreferencesKey("key_dynamic_color")
        val EXTRACT_DIR = stringPreferencesKey("key_extract_path")
        val ARCHIVE_DIR = stringPreferencesKey("key_archive_path")
        val HIDE_OUTPUT = booleanPreferencesKey("pref_hide_output_path")
        val HIDE_SUGGEST = booleanPreferencesKey("pref_hide_path_suggest")
        val ZIP_ENCRYPTION = stringPreferencesKey("key_zip_encryption")
    }

    val preferences: Flow<ToolkitsPreferences> =
        context.toolkitsDataStore.data.map { p ->
            ToolkitsPreferences(
                themeMode = p[Keys.THEME_MODE] ?: "system",
                colorScheme = p[Keys.COLOR_SCHEME] ?: "green",
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
                extractDirPath = p[Keys.EXTRACT_DIR] ?: "",
                archiveDirPath = p[Keys.ARCHIVE_DIR] ?: "",
                hideOutputPath = p[Keys.HIDE_OUTPUT] ?: false,
                hidePathSuggest = p[Keys.HIDE_SUGGEST] ?: false,
                zipEncryption = p[Keys.ZIP_ENCRYPTION] ?: "none"
            )
        }

    suspend fun setThemeMode(v: String) = context.toolkitsDataStore.edit { it[Keys.THEME_MODE] = v }
    suspend fun setColorScheme(v: String) = context.toolkitsDataStore.edit { it[Keys.COLOR_SCHEME] = v }
    suspend fun setDynamicColor(v: Boolean) = context.toolkitsDataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setExtractDir(v: String) = context.toolkitsDataStore.edit { it[Keys.EXTRACT_DIR] = v }
    suspend fun setArchiveDir(v: String) = context.toolkitsDataStore.edit { it[Keys.ARCHIVE_DIR] = v }
    suspend fun setHideOutput(v: Boolean) = context.toolkitsDataStore.edit { it[Keys.HIDE_OUTPUT] = v }
    suspend fun setHideSuggest(v: Boolean) = context.toolkitsDataStore.edit { it[Keys.HIDE_SUGGEST] = v }
    suspend fun setZipEncryption(v: String) = context.toolkitsDataStore.edit { it[Keys.ZIP_ENCRYPTION] = v }
}
