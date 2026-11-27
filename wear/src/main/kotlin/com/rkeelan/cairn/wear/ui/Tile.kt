package com.rkeelan.cairn.wear.ui

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.layoutString
import com.rkeelan.cairn.wear.MainActivity
import com.rkeelan.cairn.wear.R
import com.rkeelan.cairn.wear.TileContent

/*
 * Cairn's tile, which a person adds to the watch's tiles to see the list with a swipe from the watch face. The watch draws the app's icon at
 * its top; below that are how many items are needed, the first of them, and a button that opens the app. A tap anywhere on the tile opens it
 * too.
 */

/** Cairn's colours for the tile, by the same Material 3 roles as the app's screen. */
internal val TileColours =
    ColorScheme(
        primary = colour(Palette.PRIMARY),
        primaryDim = colour(Palette.PRIMARY_DIM),
        primaryContainer = colour(Palette.PRIMARY_CONTAINER),
        onPrimary = colour(Palette.ON_PRIMARY),
        onPrimaryContainer = colour(Palette.ON_PRIMARY_CONTAINER),
        secondary = colour(Palette.SECONDARY),
        secondaryDim = colour(Palette.SECONDARY_DIM),
        secondaryContainer = colour(Palette.SECONDARY_CONTAINER),
        onSecondary = colour(Palette.ON_SECONDARY),
        onSecondaryContainer = colour(Palette.ON_SECONDARY_CONTAINER),
        surfaceContainerLow = colour(Palette.SURFACE_CONTAINER_LOW),
        surfaceContainer = colour(Palette.SURFACE_CONTAINER),
        surfaceContainerHigh = colour(Palette.SURFACE_CONTAINER_HIGH),
        onSurface = colour(Palette.ON_SURFACE),
        onSurfaceVariant = colour(Palette.ON_SURFACE_VARIANT),
        outline = colour(Palette.OUTLINE),
        outlineVariant = colour(Palette.OUTLINE_VARIANT),
    )

private fun colour(argb: Long) = LayoutColor(argb.toInt())

/** How many lines of the list the tile has room for on this watch. */
internal fun MaterialScope.tileLines(): Int = if (deviceConfiguration.screenHeightDp >= LARGE_SCREEN_DP) 5 else 4

/** The tile's layout, which shows [content]. */
internal fun MaterialScope.tileLayout(content: TileContent): LayoutElement {
    val open =
        clickable(
            ActionBuilders.LaunchAction
                .Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity
                        .Builder()
                        .setPackageName(context.packageName)
                        .setClassName(ComponentName(context, MainActivity::class.java).className)
                        .build(),
                ).build(),
            OPEN,
        )
    val title =
        when {
            content is TileContent.Needed && content.count > 0 -> context.resources.getQuantityString(R.plurals.to_buy, content.count, content.count)
            else -> context.getString(R.string.app_name)
        }
    return primaryLayout(
        titleSlot = { text(title.layoutString) },
        mainSlot = {
            when {
                content is TileContent.NoList -> message(context.getString(R.string.open_on_phone))
                content is TileContent.Needed && content.count == 0 -> message(context.getString(R.string.nothing_to_buy))
                else -> items(content as TileContent.Needed)
            }
        },
        bottomSlot = { textEdgeButton(onClick = open) { text(context.getString(R.string.open).layoutString) } },
        onClick = open,
    )
}

private fun MaterialScope.message(message: String): LayoutElement =
    text(message.layoutString, typography = Typography.BODY_MEDIUM, color = colorScheme.onSurfaceVariant, maxLines = 2)

private fun MaterialScope.items(content: TileContent.Needed): LayoutElement {
    val lines =
        content.names.map { name ->
            text(name.layoutString, typography = Typography.BODY_LARGE, color = colorScheme.onSurface, maxLines = 1, overflow = TEXT_OVERFLOW_ELLIPSIZE)
        } +
            if (content.more > 0) {
                listOf(
                    text(
                        context.resources.getQuantityString(R.plurals.more, content.more, content.more).layoutString,
                        typography = Typography.BODY_MEDIUM,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    ),
                )
            } else {
                emptyList()
            }
    return column(*lines.toTypedArray(), width = expand())
}

// A watch at least this tall, such as the Pixel Watch 4 at 213 dp, has room for a fifth line.
private const val LARGE_SCREEN_DP = 210
private const val OPEN = "open"
