package com.rkeelan.cairn.phone.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.rkeelan.cairn.link.Interactions

/** The screens' [Interactions]; untimed unless MainActivity provides its timer. */
val LocalInteractions = staticCompositionLocalOf { Interactions {} }
