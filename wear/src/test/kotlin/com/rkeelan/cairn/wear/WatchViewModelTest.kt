package com.rkeelan.cairn.wear

import com.rkeelan.cairn.link.Received
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
    private val dataLayer = MutableStateFlow(Received(null))
    private val sent = mutableListOf<Tick>()

    private fun phoneSends(list: WatchList?) {
        dataLayer.value = dataLayer.value.copy(list = list)
    }

    private fun dataLayerHolds(ticks: List<Tick>) {
        dataLayer.value = dataLayer.value.copy(ticks = ticks.toList())
    }

    // Each tick waits here until the test lets it through, as a Data Layer call would until it finished.
    private var arrival = CompletableDeferred<Unit>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watch(): WatchViewModel {
        val model = WatchViewModel(dataLayer, send = {
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

        phoneSends(WatchList(emptyList()))

        assertTrue(watch.ready.isEmpty)
    }

    @Test
    fun aTickShowsAtOnceAndGoesToThePhone() = runTest {
        phoneSends(WatchList(listOf(item("Milk", 0), item("Eggs", 1))))
        val watch = watch()

        watch.setBought("milk", true)

        assertEquals(listOf("Eggs"), names(watch.ready.needed))
        assertEquals(listOf("Milk"), names(watch.ready.bought))
        assertEquals(listOf(Tick("milk", bought = true, at = 1_000)), sent)
        assertFalse(watch.ready.isEmpty)
    }

    @Test
    fun aTickStaysShownFromTheTapUntilThePhonesListShowsIt() = runTest {
        val before = WatchList(listOf(item("Milk", 0)))
        phoneSends(before)
        val watch = watch()

        watch.setBought("milk", true)
        // The Data Layer reports the tick's data item only after the call that sent it has returned.
        arrival.complete(Unit)
        assertEquals(listOf("Milk"), names(watch.ready.bought))
        dataLayerHolds(sent)
        assertEquals(listOf("Milk"), names(watch.ready.bought))
        // The phone takes the tick and deletes its data item, then sends the list it made with it.
        dataLayerHolds(emptyList())
        assertEquals(listOf("Milk"), names(watch.ready.bought))
        phoneSends(WatchList(listOf(item("Milk", 0, bought = true, changedAt = 1_000))))
        assertEquals(listOf("Milk"), names(watch.ready.bought))

        // Once shown by the phone's list, the tick gives way to a later change made on the phone.
        phoneSends(before.copy(items = listOf(item("Milk", 0, changedAt = 2_000))))
        assertEquals(listOf("Milk"), names(watch.ready.needed))
    }

    @Test
    fun aTickUndoneBeforeThePhoneHasSentItsListStaysUndone() = runTest {
        phoneSends(WatchList(listOf(item("Milk", 0))))
        val watch = watch()
        arrival.complete(Unit)

        watch.setBought("milk", true)
        dataLayerHolds(sent)
        dataLayerHolds(emptyList())
        watch.setBought("milk", false)
        assertEquals(listOf("Milk"), names(watch.ready.needed))
        dataLayerHolds(sent.takeLast(1))
        // The list the phone made with the first tick arrives after the second tick was made.
        phoneSends(WatchList(listOf(item("Milk", 0, bought = true, changedAt = 1_000))))
        assertEquals(listOf("Milk"), names(watch.ready.needed))
        dataLayerHolds(emptyList())
        assertEquals(listOf("Milk"), names(watch.ready.needed))
        phoneSends(WatchList(listOf(item("Milk", 0, changedAt = 1_001))))
        assertEquals(listOf("Milk"), names(watch.ready.needed))
    }

    @Test
    fun aTickThePhoneTakesWithoutChangeIsLetGo() = runTest {
        phoneSends(WatchList(listOf(item("Milk", 0))))
        val watch = watch()
        arrival.complete(Unit)

        // The second tick replaces the first's data item before the phone takes it, so the phone finds Milk already needed.
        watch.setBought("milk", true)
        watch.setBought("milk", false)
        dataLayerHolds(sent.takeLast(1))
        dataLayerHolds(emptyList())
        assertEquals(listOf("Milk"), names(watch.ready.needed))

        // A later change on the phone shows, though the phone's clock is behind the watch's.
        phoneSends(WatchList(listOf(item("Milk", 0, bought = true, changedAt = 500))))
        assertEquals(listOf("Milk"), names(watch.ready.bought))
    }

    @Test
    fun aTickWaitingInTheDataLayerStaysShownUntilThePhonesListShowsIt() = runTest {
        val tick = Tick("milk", bought = true, at = 900)
        dataLayer.value = Received(WatchList(listOf(item("Milk", 0))), listOf(tick))
        val watch = watch()
        assertEquals(listOf("Milk"), names(watch.ready.bought))

        dataLayerHolds(emptyList())
        assertEquals(listOf("Milk"), names(watch.ready.bought))

        // A tick made on the watch follows the one it shows.
        watch.setBought("milk", false)
        assertEquals(listOf(Tick("milk", bought = false, at = 1_000)), sent)
    }

    @Test
    fun aTickThatCannotBeSentIsUndone() = runTest {
        phoneSends(WatchList(listOf(item("Milk", 0))))
        val watch = watch()

        watch.setBought("milk", true)
        arrival.completeExceptionally(IllegalStateException("No Data Layer"))

        assertEquals(listOf("Milk"), names(watch.ready.needed))
    }

    @Test
    fun aTickForNoSuchItemOrBeforeAListArrivesDoesNothing() = runTest {
        val watch = watch()
        watch.setBought("milk", true)

        phoneSends(WatchList(listOf(item("Milk", 0))))
        watch.setBought("tea", true)

        assertEquals(emptyList<Tick>(), sent)
    }

    @Test
    fun boughtItemsComeAPageAtATime() = runTest {
        val count = WatchViewModel.BOUGHT_PAGE + 5
        phoneSends(WatchList((0 until count).map { item("Item $it", it, bought = true, changedAt = it.toLong()) }))
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
