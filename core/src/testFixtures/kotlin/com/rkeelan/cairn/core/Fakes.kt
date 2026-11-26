package com.rkeelan.cairn.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.io.IOException

/** A [ListStore] that keeps the copy in memory, as storage that outlives a [ListRepository] would. */
class MemoryStore(private var saved: Saved = Saved(null, ShoppingList())) : ListStore {
    /** What each call to [save] was told had changed. */
    val writes = mutableListOf<Collection<Item>>()

    override suspend fun load(): Saved = saved

    override suspend fun save(listId: String?, list: ShoppingList, changed: Collection<Item>) {
        saved = Saved(listId, list)
        writes += changed
    }

    override suspend fun clear() {
        saved = Saved(null, ShoppingList())
    }
}

/**
 * The lists a set of [FakeServer]s share, following the rules of the functions in `supabase/migrations`. Only [invited] accounts are given a list
 * of their own.
 */
class FakeBackend(private val invited: Set<String> = emptySet()) {
    private class Held(
        val id: String,
        val members: MutableList<String>,
        var revision: Long = 0,
        val items: MutableMap<String, Pair<Item, Long>> = mutableMapOf(),
    )

    private val lists = mutableListOf<Held>()
    private var made = 0
    private val revised = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Whether the servers can be reached. While false, every call throws [ServerException.Unreachable]. */
    var reachable = true

    /** How many syncs the servers have answered. */
    var syncs = 0
        private set

    fun changes(listId: String): Flow<Unit> = revised.filter { it == listId }.map { }

    fun lists(email: String): List<RemoteList> {
        check()
        if (lists.none { email in it.members }) {
            if (email !in invited) throw ServerException.NotInvited()
            lists += Held("list-${++made}", mutableListOf(email))
        }
        return lists.filter { email in it.members }.sortedByDescending { it.members.size }.map { RemoteList(it.id, "Shopping list", it.members.toList()) }
    }

    fun sync(email: String, listId: String, since: Long, changes: List<Item>): SyncReply {
        val list = member(email, listId)
        syncs++
        if (changes.isNotEmpty()) {
            list.revision++
            for (change in changes) {
                val held = list.items[change.key]
                if (held == null || change.changedAt > held.first.changedAt) list.items[change.key] = change to list.revision
            }
            revised.tryEmit(listId)
        }
        return SyncReply(list.revision, list.members.toList(), list.items.values.filter { it.second > since }.map { it.first })
    }

    fun addMember(email: String, listId: String, member: String) {
        val list = member(email, listId)
        val address = member.trim().lowercase()
        if (!Regex("[^@\\s]+@[^@\\s]+").matches(address)) throw ServerException.NotAnEmail()
        if (address !in list.members) list.members += address
        list.revision++
        revised.tryEmit(listId)
    }

    fun removeMember(email: String, listId: String, member: String) {
        val list = member(email, listId)
        list.members -= member.trim().lowercase()
        if (list.members.isEmpty()) lists -= list else list.revision++
        revised.tryEmit(listId)
    }

    /** The items the server holds for the list, in no particular order. */
    fun items(listId: String): List<Item> = lists.first { it.id == listId }.items.values.map { it.first }

    private fun member(email: String, listId: String): Held {
        check()
        return lists.firstOrNull { it.id == listId && email in it.members } ?: throw ServerException.NotAMember()
    }

    /** Throws if the servers cannot be reached. */
    fun check() {
        if (!reachable) throw ServerException.Unreachable(IOException("No connection"))
    }
}

/** A [Server] for the account [email], or for nobody if it is null, over the lists in [backend]. */
class FakeServer(private val backend: FakeBackend, email: String? = null) : Server {
    private val signedIn = MutableStateFlow<Account>(email?.let { Account.SignedIn(it) } ?: Account.SignedOut)

    override val account: StateFlow<Account> = signedIn

    /** The password every account has. Any other is refused. */
    val password = "password"

    /** Makes the account [email]'s, or nobody's if it is null, once [reading] the saved session has finished. Until then the account is unknown. */
    fun readSession(email: String?, reading: Boolean = false) {
        signedIn.value = when {
            reading -> Account.Unknown
            email != null -> Account.SignedIn(email)
            else -> Account.SignedOut
        }
    }

    /** Takes the token to be the email address of the account it is for, and refuses a blank one. */
    override suspend fun signInWithGoogle(idToken: String, nonce: String) {
        backend.check()
        if (idToken.isBlank()) throw ServerException.Refused(IllegalArgumentException("A blank token"))
        signedIn.value = Account.SignedIn(idToken)
    }

    override suspend fun signInWithPassword(email: String, password: String) {
        backend.check()
        if (password != this.password) throw ServerException.Refused(IllegalArgumentException("The wrong password"))
        signedIn.value = Account.SignedIn(email)
    }

    override suspend fun lists(): List<RemoteList> = backend.lists(email())

    override suspend fun sync(listId: String, since: Long, changes: List<Item>): SyncReply = backend.sync(email(), listId, since, changes)

    override suspend fun addMember(listId: String, email: String) = backend.addMember(email(), listId, email)

    override suspend fun removeMember(listId: String, email: String) = backend.removeMember(email(), listId, email)

    override fun changes(listId: String): Flow<Unit> = backend.changes(listId)

    override suspend fun signOut() {
        signedIn.value = Account.SignedOut
    }

    private fun email(): String = (signedIn.value as? Account.SignedIn)?.email ?: throw ServerException.SignedOut()
}
