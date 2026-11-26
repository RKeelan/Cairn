package com.rkeelan.cairn.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * [SupabaseServer] against the local Supabase stack, and through it the functions in `supabase/migrations`. The stack keeps what a run leaves in
 * it, so each test makes item keys and members that no earlier run can have made.
 */
class SupabaseServerIntegrationTest {
    private val run = UUID.randomUUID().toString().take(8)

    private suspend fun signedIn(email: String): SupabaseServer {
        val server = SupabaseServer(URL, KEY, rememberSession = false)
        server.signInWithPassword(email, PASSWORD)
        withTimeout(10.seconds) { server.account.first { it == Account.SignedIn(email) } }
        return server
    }

    private fun item(name: String, changedAt: Long, status: Status = Status.NEEDED) =
        Item(key = "$name $run", name = "$name $run", quantity = 2.0, unit = "loaves", status = status, addedAt = changedAt, changedAt = changedAt)

    private suspend fun failure(call: suspend () -> Unit): Throwable? = runCatching { call() }.exceptionOrNull()

    @Test
    fun anInvitedAccountIsGivenAListAndExchangesItems() = runBlocking {
        val owner = signedIn(OWNER)

        val list = owner.lists().first()
        val milk = item("milk", changedAt = 1_000)
        val reply = owner.sync(list.id, since = 0, changes = listOf(milk))

        assertTrue(OWNER in list.members)
        assertTrue(OWNER in reply.members)
        assertTrue(milk in reply.items)
        assertEquals(emptyList<Item>(), owner.sync(list.id, since = reply.revision, changes = emptyList()).items)
        assertEquals(list.id, owner.lists().first().id)
    }

    @Test
    fun anItemWithAPartialOrNoAmountIsExchangedLikeAnyOther() = runBlocking {
        val owner = signedIn(OWNER)
        val list = owner.lists().first()
        val salt = item("salt", changedAt = 1_500).copy(quantity = null, unit = null)
        val flour = item("flour", changedAt = 1_500).copy(quantity = 0.5, unit = "kg")
        val eggs = item("eggs", changedAt = 1_500).copy(quantity = null, unit = "box")
        val limes = item("limes", changedAt = 1_500).copy(unit = null)

        val reply = owner.sync(list.id, since = 0, changes = listOf(salt, flour, eggs, limes))

        assertTrue(reply.items.containsAll(listOf(salt, flour, eggs, limes)))
    }

    @Test
    fun theLaterChangeToAnItemWins() = runBlocking {
        val owner = signedIn(OWNER)
        val list = owner.lists().first()
        val needed = item("eggs", changedAt = 2_000)
        val start = owner.sync(list.id, since = 0, changes = listOf(needed)).revision

        val earlier = owner.sync(list.id, since = start, changes = listOf(needed.copy(status = Status.REMOVED, changedAt = 1_999)))
        val later = owner.sync(list.id, since = start, changes = listOf(needed.copy(status = Status.BOUGHT, changedAt = 2_001)))

        // The earlier change bumped the revision and changed nothing.
        assertEquals(emptyList<Item>(), earlier.items)
        assertEquals(listOf(needed.copy(status = Status.BOUGHT, changedAt = 2_001)), later.items)
    }

    @Test
    fun aSharedListIsTheMembersUntilTheyAreRemoved() = runBlocking {
        val owner = signedIn(OWNER)
        val list = owner.lists().first()
        val bread = item("bread", changedAt = 3_000)
        owner.sync(list.id, since = 0, changes = listOf(bread))
        val address = "member-$run@example.com"

        owner.addMember(list.id, " ${address.uppercase()} ")
        val member = signedIn(address)

        assertEquals(list.id, member.lists().first().id)
        assertTrue(address in member.lists().first().members)
        assertTrue(bread in member.sync(list.id, since = 0, changes = emptyList()).items)

        owner.removeMember(list.id, address)

        assertTrue(failure { member.sync(list.id, since = 0, changes = emptyList()) } is ServerException.NotAMember)
        assertTrue(failure { member.addMember(list.id, "other-$run@example.com") } is ServerException.NotAMember)
        assertTrue(failure { member.lists() } is ServerException.NotInvited)
        assertTrue(address !in owner.lists().first().members)
    }

    @Test
    fun anAccountNotInvitedIsRefusedAList() = runBlocking {
        val stranger = signedIn("stranger-$run@example.com")

        assertTrue(failure { stranger.lists() } is ServerException.NotInvited)
    }

    @Test
    fun aMemberMustBeAnEmailAddress() = runBlocking {
        val owner = signedIn(OWNER)

        assertTrue(failure { owner.addMember(owner.lists().first().id, "not an address") } is ServerException.NotAnEmail)
    }

    @Test
    fun realtimeReportsAChangeToTheList() = runBlocking {
        val phone = signedIn(OWNER)
        val tablet = signedIn(OWNER)
        val list = phone.lists().first()

        val heard = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(30.seconds) { tablet.changes(list.id).first() } }
        // The subscription takes a moment to reach the server, and a change made before it does goes unreported.
        delay(3.seconds)
        phone.sync(list.id, since = 0, changes = listOf(item("tea", changedAt = 4_000)))

        heard.await()
    }

    @Test
    fun signingOutEndsTheSession() = runBlocking {
        val owner = signedIn(OWNER)

        owner.signOut()

        withTimeout(10.seconds) { owner.account.first { it == Account.SignedOut } }
        assertTrue(failure { owner.lists() } is ServerException.SignedOut)
    }

    @Test
    fun aWrongPasswordIsRefused() = runBlocking {
        signedIn(OWNER)
        val server = SupabaseServer(URL, KEY, rememberSession = false)

        assertTrue(failure { server.signInWithPassword(OWNER, "not the password") } is ServerException.Refused)
    }

    @Test
    fun aServerThatIsNotThereIsUnreachable() = runBlocking {
        val server = SupabaseServer("http://127.0.0.1:9", KEY, rememberSession = false)

        assertTrue(failure { server.signInWithPassword(OWNER, PASSWORD) } is ServerException.Unreachable)
    }

    private companion object {
        // The local stack's address and its publishable key, which is the same in every installation of the Supabase CLI.
        const val URL = "http://127.0.0.1:54321"
        const val KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"

        // The account supabase/seed.sql invites to start a list.
        const val OWNER = "owner@example.com"
        const val PASSWORD = "cairn-dev-password"
    }
}
