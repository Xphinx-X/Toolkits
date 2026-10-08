package com.toolkits.app.ui.theme

import androidx.compose.ui.graphics.Color

// Ported seed palette from Toolkits-VIEW App.SCHEME_SEEDS + Material3 fallback.
val GreenSeed = Color(0xFF1B6B4D)
val BlueSeed = Color(0xFF0061A4)
val PurpleSeed = Color(0xFF6750A4)
val RedSeed = Color(0xFFB5261E)
val OrangeSeed = Color(0xFF8B5000)

val SeedMap = mapOf(
    "green" to GreenSeed,
    "blue" to BlueSeed,
    "purple" to PurpleSeed,
    "red" to RedSeed,
    "orange" to OrangeSeed
)

fun seedFor(name: String): Color = SeedMap[name] ?: GreenSeed

// Static M3 fallbacks (green default, mirrors Theme.ToolKits light values)
val mdPrimaryLight = Color(0xFF1B6B4D)
val mdOnPrimaryLight = Color(0xFFFFFFFF)
val mdPrimaryContainerLight = Color(0xFFA9F2C6)
val mdSurfaceLight = Color(0xFFF6FBF4)
val mdSurfaceContainerLowLight = Color(0xFFEFF4EC)

val mdPrimaryDark = Color(0xFF7EDAAA)
val mdOnPrimaryDark = Color(0xFF003825)
val mdPrimaryContainerDark = Color(0xFF00513B)
val mdSurfaceDark = Color(0xFF0E1512)
val mdSurfaceContainerLowDark = Color(0xFF151D19)
