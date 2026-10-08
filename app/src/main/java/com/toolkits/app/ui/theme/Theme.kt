package com.toolkits.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun schemeFor(seed: Color, dark: Boolean) =
    if (dark) darkColorScheme(primary = seed, primaryContainer = seed.copy(alpha = 0.35f))
    else lightColorScheme(primary = seed, primaryContainer = seed.copy(alpha = 0.18f))

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitsTheme(
    themeMode: String = "system",
    colorScheme: String = "green",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> systemDark
    }
    val seed = seedFor(colorScheme)

    // Mirror Toolkits-VIEW App.applyColors: content-based dynamic when enabled on S+,
    // otherwise seed-based; AMOLED forces pure-black surfaces.
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = seed,
            surface = if (themeMode == "amoled") Color.Black else mdSurfaceDark,
            background = if (themeMode == "amoled") Color.Black else mdSurfaceDark
        )
        else -> expressiveLightColorScheme(
            primary = seed,
            surfaceContainerLow = mdSurfaceContainerLowLight
        )
    }

    MaterialExpressiveTheme(colorScheme = scheme, content = content)
}
