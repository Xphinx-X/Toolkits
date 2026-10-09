package com.toolkits.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

// Theme mirrors Toolkits-VIEW App.applyColors exactly:
// dynamic on (S+) → system DynamicColors; otherwise a content-based tonal
// scheme generated from the seed (same TonalSpot engine DynamicColors uses
// for setContentBasedSource). AMOLED blacks out surfaces like
// ThemeOverlay.ToolKits.Amoled. Zenith-style expressive remap collapses
// background/surface onto surfaceContainerLow (skipped for AMOLED).
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
    val amoled = themeMode == "amoled" && dark
    val seed = seedFor(colorScheme)

    // Seed → full M3 tonal scheme. remember() avoids regenerating every frame.
    val seedScheme = remember(seed, dark) { seedColorScheme(seed, dark) }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> seedScheme
    }

    val scheme = when {
        amoled -> baseScheme.copy(
            background = Color.Black,
            onBackground = Color(0xFFE6E1E5),
            surface = Color.Black,
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF1C1B1F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF1C1B1F),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0F0D13),
            surfaceContainer = Color(0xFF141218),
            surfaceContainerHigh = Color(0xFF1F1D23),
            surfaceContainerHighest = Color(0xFF2A282F),
            inverseSurface = Color(0xFFE6E1E5),
            inverseOnSurface = Color(0xFF1C1B1F),
        )
        // Zenith-style expressive remap so cards float with tonal separation.
        else -> baseScheme.copy(
            background = baseScheme.surfaceContainerLow,
            surface = baseScheme.surfaceContainerLow,
        )
    }

    // Match status/nav bar icons to the theme like Zenith does.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !dark
            insetsController.isAppearanceLightNavigationBars = !dark
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialExpressiveTheme(colorScheme = scheme, content = content)
}

/**
 * Generates a complete M3 tonal-spot scheme from a seed color — the same
 * engine behind DynamicColors.setContentBasedSource(seed) used by the
 * original View app, so seeds render identical tonal palettes there and here.
 */
fun seedColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed.toArgb()), dark, 0.0)
    val dyn = MaterialDynamicColors()
    fun c(role: com.google.android.material.color.utilities.DynamicColor): Color =
        Color(role.getArgb(scheme))
    return if (dark) darkColorScheme(
        primary = c(dyn.primary()),
        onPrimary = c(dyn.onPrimary()),
        primaryContainer = c(dyn.primaryContainer()),
        onPrimaryContainer = c(dyn.onPrimaryContainer()),
        secondary = c(dyn.secondary()),
        onSecondary = c(dyn.onSecondary()),
        secondaryContainer = c(dyn.secondaryContainer()),
        onSecondaryContainer = c(dyn.onSecondaryContainer()),
        tertiary = c(dyn.tertiary()),
        onTertiary = c(dyn.onTertiary()),
        tertiaryContainer = c(dyn.tertiaryContainer()),
        onTertiaryContainer = c(dyn.onTertiaryContainer()),
        error = c(dyn.error()),
        onError = c(dyn.onError()),
        errorContainer = c(dyn.errorContainer()),
        onErrorContainer = c(dyn.onErrorContainer()),
        background = c(dyn.background()),
        onBackground = c(dyn.onBackground()),
        surface = c(dyn.surface()),
        onSurface = c(dyn.onSurface()),
        surfaceVariant = c(dyn.surfaceVariant()),
        onSurfaceVariant = c(dyn.onSurfaceVariant()),
        outline = c(dyn.outline()),
        outlineVariant = c(dyn.outlineVariant()),
        scrim = c(dyn.scrim()),
        inverseSurface = c(dyn.inverseSurface()),
        inverseOnSurface = c(dyn.inverseOnSurface()),
        inversePrimary = c(dyn.inversePrimary()),
        surfaceDim = c(dyn.surfaceDim()),
        surfaceBright = c(dyn.surfaceBright()),
        surfaceContainerLowest = c(dyn.surfaceContainerLowest()),
        surfaceContainerLow = c(dyn.surfaceContainerLow()),
        surfaceContainer = c(dyn.surfaceContainer()),
        surfaceContainerHigh = c(dyn.surfaceContainerHigh()),
        surfaceContainerHighest = c(dyn.surfaceContainerHighest()),
        surfaceTint = c(dyn.primary()),
    ) else lightColorScheme(
        primary = c(dyn.primary()),
        onPrimary = c(dyn.onPrimary()),
        primaryContainer = c(dyn.primaryContainer()),
        onPrimaryContainer = c(dyn.onPrimaryContainer()),
        secondary = c(dyn.secondary()),
        onSecondary = c(dyn.onSecondary()),
        secondaryContainer = c(dyn.secondaryContainer()),
        onSecondaryContainer = c(dyn.onSecondaryContainer()),
        tertiary = c(dyn.tertiary()),
        onTertiary = c(dyn.onTertiary()),
        tertiaryContainer = c(dyn.tertiaryContainer()),
        onTertiaryContainer = c(dyn.onTertiaryContainer()),
        error = c(dyn.error()),
        onError = c(dyn.onError()),
        errorContainer = c(dyn.errorContainer()),
        onErrorContainer = c(dyn.onErrorContainer()),
        background = c(dyn.background()),
        onBackground = c(dyn.onBackground()),
        surface = c(dyn.surface()),
        onSurface = c(dyn.onSurface()),
        surfaceVariant = c(dyn.surfaceVariant()),
        onSurfaceVariant = c(dyn.onSurfaceVariant()),
        outline = c(dyn.outline()),
        outlineVariant = c(dyn.outlineVariant()),
        scrim = c(dyn.scrim()),
        inverseSurface = c(dyn.inverseSurface()),
        inverseOnSurface = c(dyn.inverseOnSurface()),
        inversePrimary = c(dyn.inversePrimary()),
        surfaceDim = c(dyn.surfaceDim()),
        surfaceBright = c(dyn.surfaceBright()),
        surfaceContainerLowest = c(dyn.surfaceContainerLowest()),
        surfaceContainerLow = c(dyn.surfaceContainerLow()),
        surfaceContainer = c(dyn.surfaceContainer()),
        surfaceContainerHigh = c(dyn.surfaceContainerHigh()),
        surfaceContainerHighest = c(dyn.surfaceContainerHighest()),
        surfaceTint = c(dyn.primary()),
    )
}
