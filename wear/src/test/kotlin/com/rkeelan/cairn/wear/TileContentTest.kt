package com.rkeelan.cairn.wear

import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.link.WatchList
import org.junit.Assert.assertEquals
import org.junit.Test

class TileContentTest {
    private fun item(name: String, rank: Int, bought: Boolean = false) = WatchItem(name.lowercase(), name, bought = bought, rank = rank, changedAt = 1)

    @Test
    fun `no list shows that there is none`() {
        assertEquals(TileContent.NoList, TileContent.of(null, lines = 4))
    }

    @Test
    fun `an empty list shows nothing needed`() {
        assertEquals(TileContent.Needed(emptyList(), 0), TileContent.of(WatchList(emptyList()), lines = 4))
    }

    @Test
    fun `needed items show in the phone's order, without the bought ones`() {
        val list = WatchList(listOf(item("Eggs", 2), item("Milk", 0), item("Tea", 3, bought = true), item("Bread", 1)))

        val content = TileContent.of(list, lines = 4) as TileContent.Needed

        assertEquals(listOf("Milk", "Bread", "Eggs"), content.names)
        assertEquals(3, content.count)
        assertEquals(0, content.more)
    }

    @Test
    fun `items that fill the lines exactly all show`() {
        val list = WatchList((0 until 4).map { item("Item $it", it) })

        val content = TileContent.of(list, lines = 4) as TileContent.Needed

        assertEquals(4, content.names.size)
        assertEquals(0, content.more)
    }

    @Test
    fun `items that don't fit leave the last line to say how many more there are`() {
        val list = WatchList((0 until 6).map { item("Item $it", it) })

        val content = TileContent.of(list, lines = 4) as TileContent.Needed

        assertEquals(listOf("Item 0", "Item 1", "Item 2"), content.names)
        assertEquals(6, content.count)
        assertEquals(3, content.more)
    }

    @Test
    fun `a tick on its way leaves the item out`() {
        val list = WatchList(listOf(item("Milk", 0), item("Bread", 1))).with(listOf(Tick("milk", bought = true, at = 2)))

        assertEquals(TileContent.Needed(listOf("Bread"), 1), TileContent.of(list, lines = 4))
    }
}
