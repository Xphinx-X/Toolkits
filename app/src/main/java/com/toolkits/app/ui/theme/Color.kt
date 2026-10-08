package com.toolkits.app.ui.theme

import androidx.compose.ui.graphics.Color

// Exact seeds from Toolkits-VIEW App.SCHEME_SEEDS.
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

// ── Exact static fallback palettes from Toolkits-VIEW ──
// res/values/colors.xml (green light) + res/values-night/colors.xml (dark).

val mdPrimaryLight = Color(0xFF1B6B4D)
val mdOnPrimaryLight = Color(0xFFFFFFFF)
val mdPrimaryContainerLight = Color(0xFFA4F5C8)
val mdOnPrimaryContainerLight = Color(0xFF002114)
val mdSecondaryLight = Color(0xFF4E6355)
val mdOnSecondaryLight = Color(0xFFFFFFFF)
val mdSecondaryContainerLight = Color(0xFFD0E8D7)
val mdOnSecondaryContainerLight = Color(0xFF0B1F14)
val mdTertiaryLight = Color(0xFF3B6470)
val mdOnTertiaryLight = Color(0xFFFFFFFF)
val mdTertiaryContainerLight = Color(0xFFBFE9F7)
val mdOnTertiaryContainerLight = Color(0xFF001F27)
val mdErrorLight = Color(0xFFBA1A1A)
val mdOnErrorLight = Color(0xFFFFFFFF)
val mdErrorContainerLight = Color(0xFFFFDAD6)
val mdOnErrorContainerLight = Color(0xFF410002)
val mdBackgroundLight = Color(0xFFFBFDF8)
val mdOnBackgroundLight = Color(0xFF191C1A)
val mdSurfaceLight = Color(0xFFFBFDF8)
val mdOnSurfaceLight = Color(0xFF191C1A)
val mdSurfaceVariantLight = Color(0xFFDDE5DB)
val mdOnSurfaceVariantLight = Color(0xFF414942)
val mdOutlineLight = Color(0xFF717971)
val mdOutlineVariantLight = Color(0xFFC1C9BF)
val mdSurfaceDimLight = Color(0xFFDADBD8)
val mdSurfaceBrightLight = Color(0xFFFBFDF8)
val mdSurfaceLowestLight = Color(0xFFFFFFFF)
val mdSurfaceLowLight = Color(0xFFF4F6F2)
val mdSurfaceContainerLight = Color(0xFFEEF0EC)
val mdSurfaceHighLight = Color(0xFFE8EAE6)
val mdSurfaceHighestLight = Color(0xFFE2E4E0)
val mdInverseSurfaceLight = Color(0xFF2E312D)
val mdInverseOnSurfaceLight = Color(0xFFEFF1EC)
val mdInversePrimaryLight = Color(0xFF88D8AD)

val mdPrimaryDark = Color(0xFF88D8AD)
val mdOnPrimaryDark = Color(0xFF003824)
val mdPrimaryContainerDark = Color(0xFF005237)
val mdOnPrimaryContainerDark = Color(0xFFA4F5C8)
val mdSecondaryDark = Color(0xFFB4CCBC)
val mdOnSecondaryDark = Color(0xFF203529)
val mdSecondaryContainerDark = Color(0xFF374B3F)
val mdOnSecondaryContainerDark = Color(0xFFD0E8D7)
val mdTertiaryDark = Color(0xFFA3CDDB)
val mdOnTertiaryDark = Color(0xFF033641)
val mdTertiaryContainerDark = Color(0xFF214C58)
val mdOnTertiaryContainerDark = Color(0xFFBFE9F7)
val mdErrorDark = Color(0xFFFFB4AB)
val mdOnErrorDark = Color(0xFF690005)
val mdErrorContainerDark = Color(0xFF93000A)
val mdOnErrorContainerDark = Color(0xFFFFDAD6)
val mdBackgroundDark = Color(0xFF111413)
val mdOnBackgroundDark = Color(0xFFE1E3DE)
val mdSurfaceDark = Color(0xFF111413)
val mdOnSurfaceDark = Color(0xFFE1E3DE)
val mdSurfaceVariantDark = Color(0xFF414942)
val mdOnSurfaceVariantDark = Color(0xFFC1C9BF)
val mdOutlineDark = Color(0xFF8B938A)
val mdOutlineVariantDark = Color(0xFF414942)
val mdSurfaceDimDark = Color(0xFF111413)
val mdSurfaceBrightDark = Color(0xFF373A37)
val mdSurfaceLowestDark = Color(0xFF0C0F0D)
val mdSurfaceLowDark = Color(0xFF191C1A)
val mdSurfaceContainerDark = Color(0xFF1D201E)
val mdSurfaceHighDark = Color(0xFF282B28)
val mdSurfaceHighestDark = Color(0xFF333533)
val mdInverseSurfaceDark = Color(0xFFE2E4E0)
val mdInverseOnSurfaceDark = Color(0xFF2E312D)
val mdInversePrimaryDark = Color(0xFF1B6B4D)
