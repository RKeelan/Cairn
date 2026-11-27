package com.rkeelan.cairn.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchListTest {
    private val list = WatchList(
        listOf(
            WatchItem("bread", "Bread", "2 loaves", bought = false, rank = 1, changedAt = 100),
            WatchItem("milk", "Milk", bought = false, rank = 0, changedAt = 100),
            WatchItem("eggs", "Eggs", "a box", bought = true, rank = 2, changedAt = 300),
            WatchItem("tea", "Tea", bought = true, rank = 3, changedAt = 400),
        ),
    )

    private fun names(items: List<WatchItem>) = items.map(WatchItem::name)

    @Test
    fun neededItemsComeInThePhonesOrderAndBoughtOnesTheMostRecentFirst() {
        assertEquals(listOf("Milk", "Bread"), names(list.needed))
        assertEquals(listOf("Tea", "Eggs"), names(list.bought))
    }

    @Test
    fun aTickIsLaterThanTheChangeItFollows() {
        assertEquals(Tick("milk", bought = true, at = 500), list.tick("milk", bought = true, now = 500))
        assertEquals(Tick("tea", bought = false, at = 401), list.tick("tea", bought = false, now = 50))
        assertNull(list.tick("no such item", bought = true, now = 500))
    }

    @Test
    fun ticksOnTheirWayAreShownAtOnce() {
        val shown = list.with(listOf(Tick("milk", bought = true, at = 500), Tick("tea", bought = false, at = 501)))

        assertEquals(listOf("Bread", "Tea"), names(shown.needed))
        assertEquals(listOf("Milk", "Eggs"), names(shown.bought))
    }

    @Test
    fun theLatestTickOfAnItemCounts() {
        val shown = list.with(listOf(Tick("milk", bought = true, at = 500), Tick("milk", bought = false, at = 600)))

        assertEquals(listOf("Milk", "Bread"), names(shown.needed))
    }

    @Test
    fun aTickOlderThanTheItemsLastChangeGivesWay() {
        val shown = list.with(listOf(Tick("tea", bought = false, at = 400), Tick("gone", bought = true, at = 900)))

        assertEquals(list, shown)
        assertSame(list, list.with(emptyList()))
    }

    @Test
    fun listsAndTicksSurviveTheTrip() {
        val tick = Tick("milk", bought = true, at = 500)

        assertEquals(list, WatchList.decode(list.encode()))
        assertEquals(tick, Tick.decode(tick.encode()))
        assertEquals(tick, Tick.decode("""{"key":"milk","bought":true,"at":500,"from":"a later version"}""".toByteArray()))
        val app = WatchApp(1_000_001_000, fromPlay = true)
        assertEquals(app, WatchApp.decode(app.encode()))
        assertEquals(app, WatchApp.decode("""{"versionCode":1000001000,"fromPlay":true,"from":"a later version"}""".toByteArray()))
    }

    @Test
    fun `a watch app is older than a phone app of a later version, and not of the same or an earlier one`() {
        val watch = WatchApp(1_000_001_000, fromPlay = true)
        assertTrue(watch.isOlderThan(1_001))
        assertTrue(watch.isOlderThan(1_000_000))
        assertFalse(watch.isOlderThan(1_000))
        assertFalse(watch.isOlderThan(999))
    }
}
