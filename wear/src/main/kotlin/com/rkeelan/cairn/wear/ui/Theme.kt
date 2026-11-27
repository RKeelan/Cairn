package com.rkeelan.cairn.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/*
 * Cairn's colours on the watch: the phone's dark theme, Slate and Sea, as docs/appearance.md specifies it, on the watch's
 * black ground. The app's screen and its tile both draw from this palette.
 */

/** Cairn's colours on the watch, as ARGB, by the Material 3 role each fills. */
internal object Palette {
    const val PRIMARY = 0xFF69CEC2
    const val PRIMARY_DIM = 0xFF4FAEA3
    const val PRIMARY_CONTAINER = 0xFF0F504B
    const val ON_PRIMARY = 0xFF003733
    const val ON_PRIMARY_CONTAINER = 0xFFA6EFE6
    const val SECONDARY = 0xFFB4C6CD
    const val SECONDARY_DIM = 0xFF94A7AE
    const val SECONDARY_CONTAINER = 0xFF35474E
    const val ON_SECONDARY = 0xFF1F3138
    const val ON_SECONDARY_CONTAINER = 0xFFD3E3E9
    const val SURFACE_CONTAINER_LOW = 0xFF141D1C
    const val SURFACE_CONTAINER = 0xFF1A2827
    const val SURFACE_CONTAINER_HIGH = 0xFF243332
    const val ON_SURFACE = 0xFFDDE9E7
    const val ON_SURFACE_VARIANT = 0xFFA3B5B2
    const val OUTLINE = 0xFF7D8F8C
    const val OUTLINE_VARIANT = 0xFF3A4846
}

private val Colours =
    ColorScheme(
        primary = Color(Palette.PRIMARY),
        primaryDim = Color(Palette.PRIMARY_DIM),
        primaryContainer = Color(Palette.PRIMARY_CONTAINER),
        onPrimary = Color(Palette.ON_PRIMARY),
        onPrimaryContainer = Color(Palette.ON_PRIMARY_CONTAINER),
        secondary = Color(Palette.SECONDARY),
        secondaryDim = Color(Palette.SECONDARY_DIM),
        secondaryContainer = Color(Palette.SECONDARY_CONTAINER),
        onSecondary = Color(Palette.ON_SECONDARY),
        onSecondaryContainer = Color(Palette.ON_SECONDARY_CONTAINER),
        surfaceContainerLow = Color(Palette.SURFACE_CONTAINER_LOW),
        surfaceContainer = Color(Palette.SURFACE_CONTAINER),
        surfaceContainerHigh = Color(Palette.SURFACE_CONTAINER_HIGH),
        onSurface = Color(Palette.ON_SURFACE),
        onSurfaceVariant = Color(Palette.ON_SURFACE_VARIANT),
        outline = Color(Palette.OUTLINE),
        outlineVariant = Color(Palette.OUTLINE_VARIANT),
    )

/** Draws [content] in Cairn's colours. */
@Composable
fun CairnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colours, content = content)
}
