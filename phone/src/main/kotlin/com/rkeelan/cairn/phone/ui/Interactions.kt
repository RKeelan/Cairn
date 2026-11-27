package com.rkeelan.cairn.phone.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.rkeelan.cairn.link.Interactions

/** The screens' [Interactions]; untimed unless MainActivity provides its timer. */
val LocalInteractions = staticCompositionLocalOf { Interactions {} }

/** The updates on offer: [phone] installs a newer phone app, and [watch] opens the Play Store on a watch whose watch app is older. */
class Updates(val phone: (() -> Unit)? = null, val watch: (() -> Unit)? = null)

/** What the list's Update and Update watch call; neither shows unless MainActivity provides one. */
val LocalUpdates = compositionLocalOf { Updates() }
