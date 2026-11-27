package com.rkeelan.cairn.wear

import com.rkeelan.cairn.link.Received
import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.link.WatchList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WaitingTicksTest {
    private val list = WatchList(
        listOf(
            WatchItem("milk", "Milk", bought = false, rank = 0, changedAt = 100),
            WatchItem("tea", "Tea", bought = true, rank = 1, changedAt = 100),
        ),
    )
    private val milk = Tick("milk", bought = true, at = 1_000)

    @Test
    fun aLaterTickOfAnItemReplacesAnEarlierOneAndNotTheOtherWayRound() {
        val later = Tick("milk", bought = false, at = 1_001)

        assertEquals(listOf(later), (WaitingTicks() + milk + later).ticks)
        assertEquals(listOf(later), (WaitingTicks() + later + milk).ticks)
    }

    @Test
    fun onlyTheTickThatCouldNotBeSentIsUndone() {
        val later = Tick("milk", bought = false, at = 1_001)
        val waiting = WaitingTicks() + milk + later

        assertSame(waiting, waiting.undo(milk))
        assertEquals(emptyList<Tick>(), waiting.undo(later).ticks)
    }

    @Test
    fun ticksWaitWhileThereIsNoList() {
        val fromDataLayer = Tick("tea", bought = false, at = 900)

        assertEquals(listOf(milk, fromDataLayer), (WaitingTicks() + milk).after(Received(null, listOf(fromDataLayer))).ticks)
    }

    @Test
    fun aTickIsLetGoOnceTheListHasALaterChangeOrNoLongerHasTheItem() {
        val waiting = WaitingTicks() + milk + Tick("bread", bought = true, at = 1_000)

        assertEquals(listOf(milk), waiting.after(Received(list)).ticks)
        val taken = list.copy(items = listOf(list.items[0].copy(bought = true, changedAt = 1_000), list.items[1]))
        assertEquals(emptyList<Tick>(), waiting.after(Received(taken)).ticks)
    }

    @Test
    fun anOlderTickInTheDataLayerGivesWayToANewerOneMadeHere() {
        val older = Tick("milk", bought = false, at = 900)

        assertEquals(listOf(milk), (WaitingTicks() + milk).after(Received(list, listOf(older))).ticks)
    }
}
