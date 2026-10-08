package com.toolkits.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Full static schemes mirror Toolkits-VIEW values/themes.xml + values-night.
// Dynamic color (S+) uses content-based seed like App.applyColors; AMOLED blacks out surfaces.
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

    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = seed,
            onPrimary = mdOnPrimaryDark,
            primaryContainer = seed.copy(alpha = 0.30f),
            onPrimaryContainer = mdOnPrimaryContainerDark,
            secondary = mdSecondaryDark,
            onSecondary = mdOnSecondaryDark,
            secondaryContainer = mdSecondaryContainerDark,
            onSecondaryContainer = mdOnSecondaryContainerDark,
            tertiary = mdTertiaryDark,
            onTertiary = mdOnTertiaryDark,
            tertiaryContainer = mdTertiaryContainerDark,
            onTertiaryContainer = mdOnTertiaryContainerDark,
            error = mdErrorDark,
            onError = mdOnErrorDark,
            errorContainer = mdErrorContainerDark,
            onErrorContainer = mdOnErrorContainerDark,
            background = if (amoled) Color.Black else mdBackgroundDark,
            onBackground = mdOnBackgroundDark,
            surface = if (amoled) Color.Black else mdSurfaceDark,
            onSurface = mdOnSurfaceDark,
            surfaceVariant = if (amoled) Color(0xFF1C1B1F) else mdSurfaceVariantDark,
            onSurfaceVariant = mdOnSurfaceVariantDark,
            outline = mdOutlineDark,
            outlineVariant = mdOutlineVariantDark,
            surfaceDim = if (amoled) Color.Black else mdSurfaceDimDark,
            surfaceBright = if (amoled) Color(0xFF1C1B1F) else mdSurfaceBrightDark,
            surfaceContainerLowest = if (amoled) Color.Black else mdSurfaceLowestDark,
            surfaceContainerLow = if (amoled) Color(0xFF0F0D13) else mdSurfaceLowDark,
            surfaceContainer = if (amoled) Color(0xFF141218) else mdSurfaceContainerDark,
            surfaceContainerHigh = if (amoled) Color(0xFF1F1D23) else mdSurfaceHighDark,
            surfaceContainerHighest = if (amoled) Color(0xFF2A282F) else mdSurfaceHighestDark,
            inverseSurface = mdInverseSurfaceDark,
            inverseOnSurface = mdInverseOnSurfaceDark,
            inversePrimary = mdInversePrimaryDark
        )
        else -> lightColorScheme(
            primary = seed,
            onPrimary = mdOnPrimaryLight,
            primaryContainer = seed.copy(alpha = 0.22f),
            onPrimaryContainer = mdOnPrimaryContainerLight,
            secondary = mdSecondaryLight,
            onSecondary = mdOnSecondaryLight,
            secondaryContainer = mdSecondaryContainerLight,
            onSecondaryContainer = mdOnSecondaryContainerLight,
            tertiary = mdTertiaryLight,
            onTertiary = mdOnTertiaryLight,
            tertiaryContainer = mdTertiaryContainerLight,
            onTertiaryContainer = mdOnTertiaryContainerLight,
            error = mdErrorLight,
            onError = mdOnErrorLight,
            errorContainer = mdErrorContainerLight,
            onErrorContainer = mdOnErrorContainerLight,
            background = mdBackgroundLight,
            onBackground = mdOnBackgroundLight,
            surface = mdSurfaceLight,
            onSurface = mdOnSurfaceLight,
            surfaceVariant = mdSurfaceVariantLight,
            onSurfaceVariant = mdOnSurfaceVariantLight,
            outline = mdOutlineLight,
            outlineVariant = mdOutlineVariantLight,
            surfaceDim = mdSurfaceDimLight,
            surfaceBright = mdSurfaceBrightLight,
            surfaceContainerLowest = mdSurfaceLowestLight,
            surfaceContainerLow = mdSurfaceLowLight,
            surfaceContainer = mdSurfaceContainerLight,
            surfaceContainerHigh = mdSurfaceHighLight,
            surfaceContainerHighest = mdSurfaceHighestLight,
            inverseSurface = mdInverseSurfaceLight,
            inverseOnSurface = mdInverseOnSurfaceLight,
            inversePrimary = mdInversePrimaryLight
        )
    }

    MaterialExpressiveTheme(colorScheme = scheme, content = content)
}
