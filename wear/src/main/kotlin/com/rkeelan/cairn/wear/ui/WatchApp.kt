package com.rkeelan.cairn.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.wear.R
import com.rkeelan.cairn.wear.WatchState
import com.rkeelan.cairn.wear.WatchViewModel
import com.rkeelan.cairn.wear.curveOffset

/** The watch app: the list, or a message when there is none to show. */
@Composable
fun WatchApp(model: WatchViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    MaterialTheme {
        AppScaffold {
            when (val current = state) {
                WatchState.Starting -> Unit

                WatchState.NoList -> Message(stringResource(R.string.open_on_phone))

                is WatchState.Ready ->
                    if (current.isEmpty) {
                        Message(stringResource(R.string.nothing_to_buy))
                    } else {
                        CurvedList(current, onTick = model::setBought, onMoreBought = model::showMoreBought)
                    }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    ScreenScaffold {
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * The needed items, then the bought ones, as plain rows whose starts follow the left edge of the round screen. A tap ticks an item off or puts it
 * back, and the crown scrolls.
 */
@Composable
private fun CurvedList(state: WatchState.Ready, onTick: (key: String, bought: Boolean) -> Unit, onMoreBought: () -> Unit) {
    val rows = rememberLazyListState()
    ScreenScaffold(scrollState = rows) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val radius = constraints.maxHeight / 2f
            LazyColumn(
                state = rows,
                modifier = Modifier
                    .fillMaxSize()
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(rows), remember { FocusRequester() }),
                // Room for the first and last rows to scroll to the middle of the screen, where there is most room for them.
                contentPadding = PaddingValues(top = maxHeight * 0.3f, bottom = maxHeight * 0.4f),
            ) {
                items(state.needed, key = WatchItem::key) { item ->
                    ItemRow(item, Modifier.curved(rows, item.key, radius), onTick)
                }
                if (state.bought.isNotEmpty()) {
                    item(key = BOUGHT_HEADING) {
                        Text(
                            stringResource(R.string.bought),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(state.bought, key = WatchItem::key) { item ->
                        ItemRow(item, Modifier.curved(rows, item.key, radius), onTick)
                    }
                }
                if (state.moreBought) {
                    // Composed only as the list is scrolled near its end, which is when the next page of bought items is wanted.
                    item(key = MORE) { LaunchedEffect(state.bought.size) { onMoreBought() } }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: WatchItem, modifier: Modifier, onTick: (key: String, bought: Boolean) -> Unit) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(if (item.bought) R.string.untick else R.string.tick)) { onTick(item.key, !item.bought) }
            .padding(start = 18.dp, end = 28.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (item.bought) R.drawable.ic_ticked else R.drawable.ic_unticked),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = if (item.bought) colours.primary else colours.onSurfaceVariant,
        )
        Column {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.bought) colours.onSurfaceVariant else colours.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.amount.isNotEmpty()) {
                Text(item.amount, style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Moves the row with this [key] right as far as the screen's edge curves in at the height the row is drawn, which changes as the list scrolls. */
private fun Modifier.curved(rows: LazyListState, key: String, radius: Float): Modifier = graphicsLayer {
    val layout = rows.layoutInfo
    val row = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    // A row's offset is measured from below the list's top padding, which viewportStartOffset gives as a negative number.
    val top = row.offset - layout.viewportStartOffset
    translationX = curveOffset(top + row.size / 2f - radius, radius)
}

private const val BOUGHT_HEADING = "heading:bought"
private const val MORE = "more"
