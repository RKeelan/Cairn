package com.rkeelan.cairn.wear

import com.rkeelan.cairn.link.WatchList

/** What Cairn's tile shows. */
sealed interface TileContent {
    /** The phone has sent no list, or has said there is none, as when nobody is signed in on it. */
    data object NoList : TileContent

    /** The names of the first needed items, in the phone's order; how many needed items there are in all, [count]; and how many are left out, [more]. */
    data class Needed(val names: List<String>, val count: Int) : TileContent {
        val more: Int get() = count - names.size
    }

    companion object {
        /**
         * What the tile shows of [list], in at most [lines] lines. When the needed items don't all fit, the last line says how many more there are,
         * so it holds no item.
         */
        fun of(list: WatchList?, lines: Int): TileContent {
            if (list == null) return NoList
            val needed = list.needed
            val shown = if (needed.size <= lines) needed else needed.take(maxOf(lines - 1, 0))
            return Needed(shown.map { it.name }, needed.size)
        }
    }
}
