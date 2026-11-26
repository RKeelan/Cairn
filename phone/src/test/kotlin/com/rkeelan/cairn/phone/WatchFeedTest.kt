package com.rkeelan.cairn.phone

import com.rkeelan.cairn.core.Amount
import com.rkeelan.cairn.core.Order
import com.rkeelan.cairn.core.Settings
import com.rkeelan.cairn.core.ShoppingList
import com.rkeelan.cairn.core.Status
import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WatchFeedTest {
    // Added in this order; then eggs bought and tea removed.
    private val list = ShoppingList()
        .add("Milk", now = 100)
        .add("Eggs", now = 101, amount = Amount(unit = "box"))
        .add("Bread", now = 102, amount = Amount(2.0, "loaves"))
        .add("Tea", now = 103)
        .tick("eggs", now = 200)
        .remove("tea", now = 201)

    private fun names(items: List<WatchItem>) = items.map(WatchItem::name)

    @Test
    fun theWatchGetsNeededAndBoughtItemsWithTheirAmountsWritten() {
        val sent = watchList(list, Settings())

        assertEquals(listOf("Milk", "Bread"), names(sent.needed))
        assertEquals(listOf("Eggs"), names(sent.bought))
        assertEquals(WatchItem("bread", "Bread", "2 loaves", bought = false, rank = 2, changedAt = 102), sent.items.single { it.key == "bread" })
        assertEquals("box", sent.bought.single().amount)
    }

    @Test
    fun theWatchFollowsThePhonesOrderAndUntickingPutsAnItemBackInIt() {
        val sent = watchList(list, Settings(Order.ALPHABETICAL))
        val unticked = sent.with(listOf(sent.tick("eggs", bought = false, now = 300)!!))

        assertEquals(listOf("Bread", "Milk"), names(sent.needed))
        assertEquals(listOf("Bread", "Eggs", "Milk"), names(unticked.needed))
    }

    @Test
    fun aTickFromTheWatchChangesTheList() {
        val tick = Tick("milk", bought = true, at = 300)

        assertEquals(Status.BOUGHT, list.with(tick)["milk"]?.status)
        assertSame(list, list.with(Tick("eggs", bought = false, at = 150)))
    }
}
