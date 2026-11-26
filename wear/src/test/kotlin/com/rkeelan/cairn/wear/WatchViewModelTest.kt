package com.rkeelan.cairn.wear

import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.link.WatchList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    private val phone = MutableStateFlow<WatchList?>(null)
    private val sent = mutableListOf<Tick>()

    // Each tick waits here until the test lets it through, as a Data Layer call would until it finished.
    private var arrival = CompletableDeferred<Unit>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watch(): WatchViewModel {
        val model = WatchViewModel(phone, send = {
            sent += it
            arrival.await()
        }, clock = { 1_000 })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect { } }
        return model
    }

    private val WatchViewModel.ready: WatchState.Ready get() = state.value as WatchState.Ready

    private fun names(items: List<WatchItem>) = items.map(WatchItem::name)

    private fun item(name: String, rank: Int, bought: Boolean = false, changedAt: Long = 100) =
        WatchItem(name.lowercase(), name, bought = bought, rank = rank, changedAt = changedAt)

    @Test
    fun withNoListFromThePhoneTheWatchSaysSo() = runTest {
        val watch = watch()

        assertEquals(WatchState.NoList, watch.state.value)

        phone.value = WatchList(emptyList())

        assertTrue(watch.ready.isEmpty)
    }

    @Test
    fun aTickShowsAtOnceAndGoesToThePhone() = runTest {
        phone.value = WatchList(listOf(item("Milk", 0), item("Eggs", 1)))
        val watch = watch()

        watch.setBought("milk", true)

        assertEquals(listOf("Eggs"), names(watch.ready.needed))
        assertEquals(listOf("Milk"), names(watch.ready.bought))
        assertEquals(listOf(Tick("milk", bought = true, at = 1_000)), sent)
        assertFalse(watch.ready.isEmpty)
    }

    @Test
    fun onceSentATickIsShownByTheListTheDataLayerReports() = runTest {
        phone.value = WatchList(listOf(item("Milk", 0)))
        val watch = watch()

        watch.setBought("milk", true)
        // The Data Layer reports the tick as a data item laid over the list.
        phone.value = phone.value!!.with(sent)
        arrival.complete(Unit)

        assertEquals(listOf("Milk"), names(watch.ready.bought))
    }

    @Test
    fun aTickThatCannotBeSentIsUndone() = runTest {
        phone.value = WatchList(listOf(item("Milk", 0)))
        val watch = watch()

        watch.setBought("milk", true)
        arrival.completeExceptionally(IllegalStateException("No Data Layer"))

        assertEquals(listOf("Milk"), names(watch.ready.needed))
    }

    @Test
    fun aTickForNoSuchItemOrBeforeAListArrivesDoesNothing() = runTest {
        val watch = watch()
        watch.setBought("milk", true)

        phone.value = WatchList(listOf(item("Milk", 0)))
        watch.setBought("tea", true)

        assertEquals(emptyList<Tick>(), sent)
    }

    @Test
    fun boughtItemsComeAPageAtATime() = runTest {
        val count = WatchViewModel.BOUGHT_PAGE + 5
        phone.value = WatchList((0 until count).map { item("Item $it", it, bought = true, changedAt = it.toLong()) })
        val watch = watch()

        assertEquals(WatchViewModel.BOUGHT_PAGE, watch.ready.bought.size)
        assertEquals("Item ${count - 1}", watch.ready.bought.first().name)
        assertTrue(watch.ready.moreBought)

        watch.showMoreBought()

        assertEquals(count, watch.ready.bought.size)
        assertFalse(watch.ready.moreBought)
    }

    @Test
    fun theRowsAreTheNeededItemsThenAHeadingAndTheBoughtOnes() {
        val milk = item("Milk", 0)
        val bread = item("Bread", 1, bought = true)

        assertEquals(listOf(WatchRow.Item(milk)), WatchState.Ready(listOf(milk), emptyList(), moreBought = false).rows)
        assertEquals(
            listOf(WatchRow.Item(milk), WatchRow.BoughtHeading, WatchRow.Item(bread), WatchRow.MoreBought),
            WatchState.Ready(listOf(milk), listOf(bread), moreBought = true).rows,
        )
    }
}
