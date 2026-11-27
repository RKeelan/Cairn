package com.rkeelan.cairn.wear

import com.rkeelan.cairn.link.Received
import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem

/**
 * The ticks made on the watch, or found waiting in the Data Layer, that the list from the phone does not show yet, the latest of each item's. The
 * watch shows each from the tap until the phone's list does. Neither the Data Layer nor the phone's list can be relied on in between: the watch
 * reads a tick's data item back only after sending it, and the phone deletes the data item as soon as it has taken the tick, a moment before it
 * sends the list it made with it.
 */
data class WaitingTicks(private val waiting: Map<String, Waiting> = emptyMap()) {
    /** A [tick], and whether the Data Layer has been [seen] to hold it. */
    data class Waiting(val tick: Tick, val seen: Boolean)

    val ticks: List<Tick> get() = waiting.values.map(Waiting::tick)

    /** With [tick], just made, in place of any earlier tick of the same item. */
    operator fun plus(tick: Tick): WaitingTicks = if (isNewer(tick)) WaitingTicks(waiting + (tick.key to Waiting(tick, seen = false))) else this

    /** Without [tick], which could not be sent. */
    fun undo(tick: Tick): WaitingTicks = if (waiting[tick.key]?.tick == tick) WaitingTicks(waiting - tick.key) else this

    /**
     * With what the Data Layer holds, [received]. Its ticks join these, and a tick is let go once the phone's list shows it: the list no longer has
     * the item, or has a change to it as late as the tick or later, which is the tick itself once taken. A tick the phone has taken without
     * changing the item, since the item was already as the tick marks it, is let go once its data item is gone.
     */
    fun after(received: Received): WaitingTicks {
        val joined = waiting.toMutableMap()
        for (tick in received.ticks) {
            val known = joined[tick.key]
            if (known == null || tick.at > known.tick.at || tick == known.tick) joined[tick.key] = Waiting(tick, seen = true)
        }
        val list = received.list ?: return WaitingTicks(joined)
        val items = list.items.associateBy(WatchItem::key)
        val kept = joined.filterValues { (tick, seen) ->
            val item = items[tick.key]
            val taken = seen && tick !in received.ticks
            item != null && item.changedAt < tick.at && !(taken && item.bought == tick.bought)
        }
        return WaitingTicks(kept)
    }

    private fun isNewer(tick: Tick): Boolean = waiting[tick.key].let { it == null || tick.at > it.tick.at }
}
