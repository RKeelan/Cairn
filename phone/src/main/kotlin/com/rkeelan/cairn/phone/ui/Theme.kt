package com.rkeelan.cairn.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Cairn's colours, Slate and Sea, as docs/appearance.md specifies them: wet slate on a sea-green ground, with teal for what
 * stands out. Light or dark follows the system; the wallpaper's colours are not used.
 */

private val Shore = Color(0xFFEEF3F2)
private val ShoreInk = Color(0xFF172221)
private val Deep = Color(0xFF0E1615)
private val DeepInk = Color(0xFFD7E3E1)
private val Teal = Color(0xFF0D6C66)
private val BrightTeal = Color(0xFF69CEC2)

private val LightColours =
    lightColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC4E8E3),
        onPrimaryContainer = Color(0xFF00201E),
        inversePrimary = BrightTeal,
        secondary = Color(0xFF4E5F66),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3DEE2),
        onSecondaryContainer = Color(0xFF0B1D23),
        tertiary = Color(0xFF3F5F7A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0E2F3),
        onTertiaryContainer = Color(0xFF0A1D2C),
        background = Shore,
        onBackground = ShoreInk,
        surface = Shore,
        onSurface = ShoreInk,
        surfaceVariant = Color(0xFFDCE6E4),
        onSurfaceVariant = Color(0xFF4F605E),
        // Nothing is tinted by its elevation.
        surfaceTint = Shore,
        inverseSurface = Color(0xFF2B3634),
        inverseOnSurface = Color(0xFFE6EFED),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF73827F),
        outlineVariant = Color(0xFFC3CFCC),
        scrim = Color.Black,
        surfaceBright = Color(0xFFF8FBFA),
        surfaceDim = Color(0xFFD5E0DE),
        surfaceContainerLowest = Color(0xFFF8FBFA),
        surfaceContainerLow = Color(0xFFE8EFEE),
        surfaceContainer = Color(0xFFE2EAE8),
        surfaceContainerHigh = Color(0xFFDCE6E4),
        surfaceContainerHighest = Color(0xFFD5E0DE),
    )

private val DarkColours =
    darkColorScheme(
        primary = BrightTeal,
        onPrimary = Color(0xFF003733),
        primaryContainer = Color(0xFF0F504B),
        onPrimaryContainer = Color(0xFFA6EFE6),
        inversePrimary = Teal,
        secondary = Color(0xFFB4C6CD),
        onSecondary = Color(0xFF1F3138),
        secondaryContainer = Color(0xFF35474E),
        onSecondaryContainer = Color(0xFFD3E3E9),
        tertiary = Color(0xFFA9C8E4),
        onTertiary = Color(0xFF14324A),
        tertiaryContainer = Color(0xFF2C4A62),
        onTertiaryContainer = Color(0xFFD0E2F3),
        background = Deep,
        onBackground = DeepInk,
        surface = Deep,
        onSurface = DeepInk,
        surfaceVariant = Color(0xFF2A3634),
        onSurfaceVariant = Color(0xFFA3B5B2),
        surfaceTint = Deep,
        inverseSurface = DeepInk,
        inverseOnSurface = Color(0xFF243030),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        outline = Color(0xFF7D8F8C),
        outlineVariant = Color(0xFF3A4846),
        scrim = Color.Black,
        surfaceBright = Color(0xFF2A3634),
        surfaceDim = Deep,
        surfaceContainerLowest = Color(0xFF09100F),
        surfaceContainerLow = Color(0xFF141D1C),
        surfaceContainer = Color(0xFF182221),
        surfaceContainerHigh = Color(0xFF1F2A29),
        surfaceContainerHighest = Color(0xFF2A3634),
    )

/** Draws [content] in Cairn's colours, light or dark as the system is. */
@Composable
fun CairnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColours else LightColours, content = content)
}
