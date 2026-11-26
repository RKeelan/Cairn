package com.rkeelan.cairn.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncTest {
    private val backend = FakeBackend(invited = setOf(ADA))
    private var now = 1_000L
    private var wentOffline = 0

    private class Device(val repository: ListRepository, val server: FakeServer, val sync: Sync)

    private fun TestScope.device(email: String? = ADA): Device {
        val server = FakeServer(backend, email)
        val repository = ListRepository(MemoryStore(), server) { now++ }
        return Device(repository, server, Sync(repository, server, backgroundScope, every = 1.minutes) { wentOffline++ })
    }

    private suspend fun Device.needed(): List<String> = repository.list.first().needed.map(Item::name)

    @Test
    fun aRequestSendsWhatHasChanged() = runTest {
        val phone = device()
        phone.repository.edit { add("Milk", it) }

        phone.sync.request()
        runCurrent()

        assertEquals(listOf("Milk"), backend.items("list-1").map(Item::name))
    }

    @Test
    fun requestsMadeTogetherAreAnsweredByOneExchange() = runTest {
        val phone = device()

        repeat(5) { phone.sync.request() }
        runCurrent()

        assertEquals(1, backend.syncs)
    }

    @Test
    fun nothingIsExchangedWhileSignedOut() = runTest {
        val phone = device(email = null)

        phone.sync.request()
        runCurrent()

        assertEquals(0, backend.syncs)
        assertEquals(null, phone.repository.problem.value)
    }

    @Test
    fun anExchangeThatFindsNoConnectionSaysSo() = runTest {
        val phone = device()
        backend.reachable = false

        phone.sync.request()
        runCurrent()

        assertEquals(1, wentOffline)
    }

    @Test
    fun aShowingDeviceHearsOfChangesMadeElsewhere() = runTest {
        val phone = device()
        val tablet = device()
        backgroundScope.launch { tablet.sync.whileShowing() }
        runCurrent()

        phone.repository.edit { add("Milk", it) }
        phone.sync.request()
        runCurrent()

        assertEquals(listOf("Milk"), tablet.needed())
    }

    @Test
    fun aShowingDeviceExchangesEverySoOftenRegardless() = runTest {
        val phone = device()
        backgroundScope.launch { phone.sync.whileShowing() }
        runCurrent()
        val before = backend.syncs

        advanceTimeBy(2.minutes + 1.seconds)
        runCurrent()

        assertEquals(before + 2, backend.syncs)
    }

    @Test
    fun signingInWhileShowingFetchesTheList() = runTest {
        device().run {
            repository.edit { add("Milk", it) }
            sync.request()
        }
        runCurrent()
        val tablet = device(email = null)
        backgroundScope.launch { tablet.sync.whileShowing() }
        runCurrent()
        assertEquals(emptyList<String>(), tablet.needed())

        tablet.server.signInWithPassword(ADA, tablet.server.password)
        runCurrent()

        assertEquals(listOf("Milk"), tablet.needed())
    }

    private companion object {
        const val ADA = "ada@example.com"
    }
}
