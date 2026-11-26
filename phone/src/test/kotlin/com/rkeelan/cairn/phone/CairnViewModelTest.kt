package com.rkeelan.cairn.phone

import android.content.Context
import com.rkeelan.cairn.core.Amount
import com.rkeelan.cairn.core.Arrangement
import com.rkeelan.cairn.core.FakeBackend
import com.rkeelan.cairn.core.FakeServer
import com.rkeelan.cairn.core.Item
import com.rkeelan.cairn.core.ListRepository
import com.rkeelan.cairn.core.MemoryStore
import com.rkeelan.cairn.core.Order
import com.rkeelan.cairn.core.Problem
import com.rkeelan.cairn.core.Settings
import com.rkeelan.cairn.core.Status
import com.rkeelan.cairn.core.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CairnViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val backend = FakeBackend(invited = setOf(ADA))
    private val settings = SettingsStore(RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE))
    private var now = 1_000L

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    /** A device's view model, signed in as [email] if one is given, with its state collected as a screen would. */
    private fun TestScope.device(email: String? = null, server: FakeServer = FakeServer(backend, email)): CairnViewModel {
        val repository = ListRepository(MemoryStore(), server) { now++ }
        val model = CairnViewModel(repository, server, settings, Sync(repository, server, backgroundScope))
        backgroundScope.launch { model.state.collect { } }
        if (email != null) model.refresh()
        return model
    }

    private val CairnViewModel.ready: UiState.Ready get() = state.value as UiState.Ready

    private fun CairnViewModel.items(): List<String> = ready.arrangement.needed.map(Item::name)

    private fun CairnViewModel.bought(): List<String> = ready.arrangement.bought.map(Item::name)

    @Test
    fun aDeviceWithNoSessionIsSignedOut() = test {
        assertEquals(UiState.SignedOut(signingIn = false), device().state.value)
    }

    @Test
    fun aDeviceThatWasSignedInShowsItsCopyWhileTheSessionIsRead() = test {
        val store = MemoryStore()
        ListRepository(store, FakeServer(backend, ADA)) { now++ }.run {
            edit { add("Milk", it) }
            sync()
        }
        // The app starts again: the copy is in storage, and the session is not yet known.
        val server = FakeServer(backend).apply { readSession(ADA, reading = true) }
        val repository = ListRepository(store, server) { now++ }
        val tablet = CairnViewModel(repository, server, settings, Sync(repository, server, backgroundScope))
        backgroundScope.launch { tablet.state.collect { } }

        assertEquals(listOf("Milk"), tablet.items())
        assertEquals("", tablet.ready.email)

        server.readSession(ADA)

        assertEquals(ADA, tablet.ready.email)
    }

    @Test
    fun aDeviceThatWasSignedOutShowsNothingWhileTheSessionIsRead() = test {
        val server = FakeServer(backend).apply { readSession(null, reading = true) }
        val phone = device(server = server)

        assertEquals(UiState.Starting, phone.state.value)

        server.readSession(null)

        assertEquals(UiState.SignedOut(signingIn = false), phone.state.value)
    }

    @Test
    fun signingInShowsTheAccountsList() = test {
        device(ADA).run {
            add("Milk")
            add("Eggs", amount = "a dozen")
        }

        val tablet = device()
        tablet.signInWithPassword(ADA, "password")

        assertEquals(ADA, tablet.ready.email)
        assertEquals(listOf("Milk", "Eggs"), tablet.items())
        assertEquals(Amount(unit = "dozen"), tablet.ready.list["eggs"]?.amount)
        assertEquals(listOf(ADA), tablet.ready.members)
        assertNull(tablet.ready.problem)
        assertNull(tablet.notice.value)
    }

    @Test
    fun aRefusedSignInSaysSoAndStaysSignedOut() = test {
        val phone = device()

        phone.signInWithPassword(ADA, "not the password")

        assertEquals(UiState.SignedOut(signingIn = false), phone.state.value)
        assertEquals(Notice.SIGN_IN_FAILED, phone.notice.value)

        phone.dismissNotice()

        assertNull(phone.notice.value)
    }

    @Test
    fun signingInWithNoConnectionSaysSo() = test {
        val phone = device()
        backend.reachable = false

        phone.signInWithPassword(ADA, "password")

        assertEquals(Notice.NO_CONNECTION, phone.notice.value)
    }

    @Test
    fun signingInWithGoogleUsesTheTokenTheUserObtains() = test {
        val phone = device()

        phone.signInWithGoogle { GoogleToken(idToken = ADA, nonce = "nonce") }

        assertEquals(ADA, phone.ready.email)
    }

    @Test
    fun aCancelledGoogleSignInSaysNothingAndAFailedOneDoes() = test {
        val phone = device()

        phone.signInWithGoogle { throw GoogleSignInException(cancelled = true) }
        assertNull(phone.notice.value)

        phone.signInWithGoogle { throw GoogleSignInException(cancelled = false) }
        assertEquals(Notice.SIGN_IN_FAILED, phone.notice.value)
        assertEquals(UiState.SignedOut(signingIn = false), phone.state.value)
    }

    @Test
    fun anAccountNotInvitedIsToldSoUntilItIsSharedWith() = test {
        val ada = device(ADA)
        val ben = device()

        ben.signInWithPassword(BEN, "password")
        assertEquals(UiState.NotInvited(BEN), ben.state.value)

        ada.add("Milk")
        ada.addMember(BEN)
        ben.refresh()

        assertEquals(listOf("Milk"), ben.items())
        assertEquals(listOf(ADA, BEN), ben.ready.members)
    }

    @Test
    fun changesShowAtOnceAndReachTheServer() = test {
        val phone = device(ADA)

        phone.add("Milk")
        phone.add("Eggs")
        phone.add("Bread")
        phone.setTicked("eggs", true)
        assertEquals(listOf("Milk", "Bread"), phone.items())
        assertEquals(listOf("Eggs"), phone.bought())

        phone.setTicked("eggs", false)
        phone.setTicked("bread", true)
        phone.edit("milk", "Oat milk", "1 litre")
        assertEquals(listOf("Oat milk", "Eggs"), phone.items())
        assertEquals("1 L", phone.ready.list["oat milk"]?.amount.toString())

        val bread = phone.ready.list["bread"]!!
        phone.remove("bread")
        assertEquals(emptyList<String>(), phone.bought())
        phone.restore(listOf(bread))
        assertEquals(listOf("Bread"), phone.bought())

        phone.remove("eggs")
        assertEquals(listOf("Oat milk"), phone.items())
        assertEquals(
            mapOf("oat milk" to Status.NEEDED, "milk" to Status.REMOVED, "eggs" to Status.REMOVED, "bread" to Status.BOUGHT),
            backend.items("list-1").associate { it.key to it.status },
        )
    }

    @Test
    fun theOrderRearrangesTheListAndIsKept() = test {
        val tablet = device(ADA)
        tablet.add("Milk")
        tablet.add("Apples")

        tablet.setOrder(Order.NEWEST_FIRST)

        assertEquals(listOf("Apples", "Milk"), tablet.items())
        assertEquals(Settings(Order.NEWEST_FIRST), tablet.ready.settings)
        assertEquals(Settings(Order.NEWEST_FIRST), settings.settings.value)
    }

    @Test
    fun boughtItemsComeAPageAtATime() = test {
        val phone = device(ADA)
        repeat(Arrangement.BOUGHT_PAGE + 5) { n ->
            phone.add("Item $n")
            phone.setTicked("item $n", true)
        }

        assertEquals(Arrangement.BOUGHT_PAGE, phone.bought().size)
        assertEquals("Item ${Arrangement.BOUGHT_PAGE + 4}", phone.bought().first())
        assertTrue(phone.ready.arrangement.moreBought)

        phone.showMoreBought()

        assertEquals(Arrangement.BOUGHT_PAGE + 5, phone.bought().size)
        assertFalse(phone.ready.arrangement.moreBought)
    }

    @Test
    fun aMemberThatIsNotAnAddressIsRefused() = test {
        val phone = device(ADA)

        phone.addMember("ben")

        assertEquals(Notice.NOT_AN_EMAIL, phone.notice.value)
        assertEquals(listOf(ADA), phone.ready.members)
    }

    @Test
    fun aMemberCanBeRemoved() = test {
        val phone = device(ADA)
        phone.addMember(BEN)
        assertEquals(listOf(ADA, BEN), phone.ready.members)

        phone.removeMember(BEN)

        assertEquals(listOf(ADA), phone.ready.members)
    }

    @Test
    fun changingMembersWithNoConnectionSaysSo() = test {
        val phone = device(ADA)
        backend.reachable = false

        phone.addMember(BEN)

        assertEquals(Notice.NO_CONNECTION, phone.notice.value)
    }

    @Test
    fun aDeviceWithNoConnectionShowsItsOwnChangesAndTheProblem() = test {
        val phone = device(ADA)
        backend.reachable = false

        phone.add("Milk")

        assertEquals(listOf("Milk"), phone.items())
        assertEquals(Problem.OFFLINE, phone.ready.problem)
        assertEquals(setOf("milk"), phone.ready.list.unsent)
    }

    @Test
    fun signingOutForgetsTheList() = test {
        val phone = device(ADA)
        phone.add("Milk")

        phone.signOut()

        assertEquals(UiState.SignedOut(signingIn = false), phone.state.value)

        phone.signInWithPassword(ADA, "password")

        assertEquals(listOf("Milk"), phone.items())
    }

    private companion object {
        const val ADA = "ada@example.com"
        const val BEN = "ben@example.com"
    }
}
