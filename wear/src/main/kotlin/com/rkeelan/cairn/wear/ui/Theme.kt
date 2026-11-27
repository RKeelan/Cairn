package com.rkeelan.cairn.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/*
 * Cairn's colours on the watch: the phone's dark theme, Slate and Sea, as docs/appearance.md specifies it, on the watch's
 * black ground.
 */

private val Colours =
    ColorScheme(
        primary = Color(0xFF69CEC2),
        primaryDim = Color(0xFF4FAEA3),
        primaryContainer = Color(0xFF0F504B),
        onPrimary = Color(0xFF003733),
        onPrimaryContainer = Color(0xFFA6EFE6),
        secondary = Color(0xFFB4C6CD),
        secondaryDim = Color(0xFF94A7AE),
        secondaryContainer = Color(0xFF35474E),
        onSecondary = Color(0xFF1F3138),
        onSecondaryContainer = Color(0xFFD3E3E9),
        surfaceContainerLow = Color(0xFF141D1C),
        surfaceContainer = Color(0xFF1A2827),
        surfaceContainerHigh = Color(0xFF243332),
        onSurface = Color(0xFFDDE9E7),
        onSurfaceVariant = Color(0xFFA3B5B2),
        outline = Color(0xFF7D8F8C),
        outlineVariant = Color(0xFF3A4846),
    )

/** Draws [content] in Cairn's colours. */
@Composable
fun CairnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colours, content = content)
}
