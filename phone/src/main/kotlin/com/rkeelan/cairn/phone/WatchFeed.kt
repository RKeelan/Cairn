package com.rkeelan.cairn.phone

import com.rkeelan.cairn.core.Settings
import com.rkeelan.cairn.core.ShoppingList
import com.rkeelan.cairn.core.Status
import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.link.WatchList

/**
 * [list] as the watch shows it: every needed and bought item, ranked in the order [settings] give the needed ones. A bought item is ranked as if it
 * were needed, so that unticking it on the watch puts it back where it was.
 */
fun watchList(list: ShoppingList, settings: Settings): WatchList {
    val items = list.items.filter { it.status != Status.REMOVED }.sortedWith(settings.order.comparator)
    return WatchList(
        items.mapIndexed { rank, item ->
            WatchItem(item.key, item.name, item.amount.toString(), bought = item.status == Status.BOUGHT, rank = rank, changedAt = item.changedAt)
        },
    )
}

/** [list] with [tick] from the watch taken. */
fun ShoppingList.with(tick: Tick): ShoppingList = ticked(tick.key, tick.bought, tick.at)
