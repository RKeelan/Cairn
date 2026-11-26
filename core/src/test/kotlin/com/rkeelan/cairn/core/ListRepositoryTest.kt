package com.rkeelan.cairn.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListRepositoryTest {
    private val backend = FakeBackend(invited = setOf(ADA))
    private var now = 1_000L

    /** A device signed in as [email], whose clock gives each change its own time. */
    private fun device(email: String = ADA, store: ListStore = MemoryStore()) = ListRepository(store, FakeServer(backend, email)) { now++ }

    private suspend fun ListRepository.needed(): List<String> = list.first().needed.map(Item::name)

    private suspend fun ListRepository.add(vararg names: String) = names.forEach { name -> edit { add(name, it) } }

    @Test
    fun anEditShowsAtOnceAndIsSavedBeforeAnySync() = runTest {
        val store = MemoryStore()
        val phone = device(store = store)

        phone.add("Milk")

        assertEquals(listOf("Milk"), phone.needed())
        assertEquals(setOf("milk"), store.load().list.unsent)
        assertEquals(listOf("milk"), store.writes.single().map(Item::key))
        assertNull(store.load().listId)
    }

    @Test
    fun anEditThatChangesNothingIsNotSaved() = runTest {
        val store = MemoryStore()
        val phone = device(store = store)
        phone.add("Milk")

        phone.add("milk")

        assertEquals(1, store.writes.size)
    }

    @Test
    fun theFirstSyncFindsTheAccountsListAndSendsWhatWasAddedBeforeIt() = runTest {
        val store = MemoryStore()
        val phone = device(store = store)
        phone.add("Milk")

        assertTrue(phone.sync())

        assertEquals("list-1", phone.listId.first())
        assertEquals(listOf("Milk"), backend.items("list-1").map(Item::name))
        assertEquals(emptySet<String>(), store.load().list.unsent)
        assertEquals(listOf(ADA), phone.members.value)
        assertNull(phone.problem.value)
    }

    @Test
    fun twoDevicesOfOneAccountSeeEachOthersChanges() = runTest {
        val phone = device()
        val tablet = device()
        phone.add("Milk", "Eggs")
        phone.sync()
        tablet.sync()

        tablet.edit { tick("milk", it) }
        tablet.sync()
        phone.sync()

        assertEquals(listOf("Eggs"), phone.needed())
        assertEquals(listOf("Milk"), phone.list.first().bought.map(Item::name))
    }

    @Test
    fun whereTwoDevicesChangeTheSameItemTheLaterChangeWins() = runTest {
        val phone = device()
        val tablet = device()
        phone.add("Milk")
        phone.sync()
        tablet.sync()

        // Both change the item while apart: the tablet first, the phone after.
        tablet.edit { tick("milk", it) }
        phone.edit { remove("milk", it) }
        // The earlier change reaches the server last, and loses all the same.
        phone.sync()
        tablet.sync()
        phone.sync()

        assertEquals(Status.REMOVED, phone.list.first()["milk"]?.status)
        assertEquals(Status.REMOVED, tablet.list.first()["milk"]?.status)
        assertEquals(emptySet<String>(), tablet.list.first().unsent)
    }

    @Test
    fun aDeviceWithNoConnectionKeepsItsChangesAndSendsThemLater() = runTest {
        val phone = device()
        phone.sync()
        backend.reachable = false

        phone.add("Milk")

        assertFalse(phone.sync())
        assertEquals(Problem.OFFLINE, phone.problem.value)
        assertEquals(listOf("Milk"), phone.needed())

        backend.reachable = true

        assertTrue(phone.sync())
        assertNull(phone.problem.value)
        assertEquals(listOf("Milk"), backend.items("list-1").map(Item::name))
    }

    @Test
    fun aCopySurvivesARestart() = runTest {
        val store = MemoryStore()
        device(store = store).run {
            add("Milk")
            sync()
            add("Eggs")
        }

        val restarted = device(store = store)

        assertEquals(listOf("Milk", "Eggs"), restarted.needed())
        assertEquals(setOf("eggs"), restarted.list.first().unsent)
        assertEquals("list-1", restarted.listId.first())
    }

    @Test
    fun anAccountNotInvitedGetsNoList() = runTest {
        val phone = device(email = EVE)

        assertFalse(phone.sync())

        assertEquals(Problem.NOT_INVITED, phone.problem.value)
        assertNull(phone.listId.first())
    }

    @Test
    fun aSignedOutDeviceCannotSync() = runTest {
        val phone = ListRepository(MemoryStore(), FakeServer(backend))

        assertFalse(phone.sync())

        assertEquals(Problem.SIGNED_OUT, phone.problem.value)
    }

    @Test
    fun sharingAListGivesTheNewMemberItsItems() = runTest {
        val ada = device()
        ada.add("Milk")
        ada.sync()

        ada.addMember(" Ben@Example.com ")
        val ben = device(email = BEN)
        ben.sync()

        assertEquals(listOf(ADA, BEN), ada.members.value)
        assertEquals(listOf("Milk"), ben.needed())
        assertEquals(listOf(ADA, BEN), ben.members.value)
    }

    @Test
    fun anAddressThatIsNotOneIsRefused() = runTest {
        val ada = device()
        ada.sync()

        assertTrue(runCatching { ada.addMember("ben") }.exceptionOrNull() is ServerException.NotAnEmail)
        assertTrue(runCatching { device(email = EVE).addMember(BEN) }.exceptionOrNull() is ServerException.NotAMember)
    }

    @Test
    fun aMemberSharedWithLaterMovesToTheSharedListWhenItLooksForOne() = runTest {
        val backend = FakeBackend(invited = setOf(ADA, BEN))
        val ben = ListRepository(MemoryStore(), FakeServer(backend, BEN)) { now++ }
        ben.add("Tea")
        ben.sync()
        val ada = ListRepository(MemoryStore(), FakeServer(backend, ADA)) { now++ }
        ada.add("Milk")
        ada.sync()
        ada.addMember(BEN)

        // An ordinary sync stays with the list the device has.
        ben.sync()
        assertEquals(listOf("Tea"), ben.needed())

        ben.sync(findList = true)

        assertEquals("list-2", ben.listId.first())
        assertEquals(listOf("Milk"), ben.needed())
    }

    @Test
    fun aMemberRemovedFromAListMovesToAnotherAndDropsItsCopy() = runTest {
        val backend = FakeBackend(invited = setOf(ADA, BEN))
        val ada = ListRepository(MemoryStore(), FakeServer(backend, ADA)) { now++ }
        ada.add("Milk")
        ada.sync()
        ada.addMember(BEN)
        val ben = ListRepository(MemoryStore(), FakeServer(backend, BEN)) { now++ }
        ben.sync()
        assertEquals(listOf("Milk"), ben.needed())

        ada.removeMember(BEN)
        ben.add("Tea")

        assertTrue(ben.sync())
        assertEquals("list-2", ben.listId.first())
        assertEquals(emptyList<String>(), ben.needed())
        assertEquals(listOf(BEN), ben.members.value)
        assertEquals(listOf(ADA), ada.members.value)
    }

    @Test
    fun leavingAListTakesUpAnother() = runTest {
        val ada = device()
        ada.add("Milk")
        ada.sync()

        ada.removeMember(ADA)

        assertEquals("list-2", ada.listId.first())
        assertEquals(emptyList<String>(), ada.needed())
        assertEquals(emptyList<Item>(), backend.items("list-2"))
    }

    @Test
    fun signingOutSendsWhatIsUnsentAndForgetsTheCopy() = runTest {
        val store = MemoryStore()
        val server = FakeServer(backend, ADA)
        val phone = ListRepository(store, server) { now++ }
        phone.sync()
        phone.add("Milk")

        phone.signOut()

        assertEquals(Account.SignedOut, server.account.value)
        assertEquals(listOf("Milk"), backend.items("list-1").map(Item::name))
        assertEquals(Saved(null, ShoppingList()), store.load())
        assertEquals(emptyList<String>(), phone.needed())
        assertEquals(emptyList<String>(), phone.members.value)
        assertNull(phone.problem.value)
    }

    @Test
    fun aChangeMadeWhileAnExchangeIsUnderWayIsSentByTheNext() = runTest {
        val store = MemoryStore()
        val backend = FakeBackend(invited = setOf(ADA))
        lateinit var phone: ListRepository
        var edited = false
        val real = FakeServer(backend, ADA)
        val server = object : Server by real {
            override suspend fun sync(listId: String, since: Long, changes: List<Item>): SyncReply {
                // The user ticks the item just as the request goes out.
                if (!edited) {
                    edited = true
                    phone.edit { tick("milk", it) }
                }
                return real.sync(listId, since, changes)
            }
        }
        phone = ListRepository(store, server) { now++ }
        phone.add("Milk")

        phone.sync()

        assertEquals(setOf("milk"), phone.list.first().unsent)
        assertEquals(Status.NEEDED, backend.items("list-1").single().status)

        phone.sync()

        assertEquals(emptySet<String>(), phone.list.first().unsent)
        assertEquals(Status.BOUGHT, backend.items("list-1").single().status)
    }

    private companion object {
        const val ADA = "ada@example.com"
        const val BEN = "ben@example.com"
        const val EVE = "eve@example.com"
    }
}
