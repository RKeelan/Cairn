package com.rkeelan.cairn.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a device keeps between runs: its copy of the list, and which of the server's lists it is a copy of, once that is known. */
data class Saved(val listId: String?, val list: ShoppingList)

/** Where a device keeps its copy of the list between runs. */
interface ListStore {
    /** The copy as last saved, or an empty one. */
    suspend fun load(): Saved

    /** Saves [list] as a copy of the server's list [listId]. Only [changed] differ from what was last saved (see [ShoppingList.changedSince]). */
    suspend fun save(listId: String?, list: ShoppingList, changed: Collection<Item>)

    /** Forgets the copy. */
    suspend fun clear()
}

/** Why the device's copy and the server's list may differ. */
enum class Problem { OFFLINE, NOT_INVITED, SIGNED_OUT, FAILED }

/**
 * The shopping list on this device: the copy the screens show and change, and its exchanges with the server.
 *
 * A change is made to the copy and saved at once, whether or not the server can be reached. [sync] sends what has changed here and takes in what
 * has changed elsewhere.
 */
class ListRepository(
    private val store: ListStore,
    private val server: Server,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Told of each exchange with the server that fails, for the app's log. */
    private val onFailure: (ServerException) -> Unit = {},
) {
    // Guards the copy and its storage.
    private val guard = Mutex()

    // Lets one exchange with the server finish before the next begins.
    private val exchanging = Mutex()

    private val saved = MutableStateFlow<Saved?>(null)
    private val sharedWith = MutableStateFlow<List<String>>(emptyList())
    private val trouble = MutableStateFlow<Problem?>(null)

    /** The device's copy of the list, from what was last saved and then following every change. */
    val list: Flow<ShoppingList> = flow {
        guard.withLock { loaded() }
        emitAll(saved.filterNotNull().map { it.list }.distinctUntilChanged())
    }

    /** The server's list this device holds a copy of, once known. */
    val listId: Flow<String?> = saved.map { it?.listId }.distinctUntilChanged()

    /** The email addresses of the people the list is shared with, as of the last exchange with the server. */
    val members: StateFlow<List<String>> = sharedWith.asStateFlow()

    /** Why the last [sync] failed, or null if it did not. */
    val problem: StateFlow<Problem?> = trouble.asStateFlow()

    /** Changes the copy. [change] is given the time to record the change at. */
    suspend fun edit(change: ShoppingList.(now: Long) -> ShoppingList) {
        withContext(NonCancellable) {
            guard.withLock {
                val current = loaded()
                write(current.listId, current.list, current.list.change(clock()))
            }
        }
    }

    /**
     * Sends this device's changes to the server and takes in the changes made elsewhere. With [findList], first asks the server which list the
     * account should be using, which may have changed since a list was shared with it. Returns whether the exchange succeeded; [problem] says why
     * if it did not.
     */
    suspend fun sync(findList: Boolean = false): Boolean = exchanging.withLock {
        try {
            var id = guard.withLock { loaded().listId }
            if (id == null || findList) id = adopt(server.lists())
            try {
                exchange(id)
            } catch (_: ServerException.NotAMember) {
                exchange(adopt(server.lists()))
            }
            trouble.value = null
            true
        } catch (e: ServerException) {
            onFailure(e)
            trouble.value = when (e) {
                is ServerException.Unreachable -> Problem.OFFLINE
                is ServerException.NotInvited -> Problem.NOT_INVITED
                is ServerException.SignedOut -> Problem.SIGNED_OUT
                else -> Problem.FAILED
            }
            false
        }
    }

    /** Shares the list with whoever signs in with [email]. */
    suspend fun addMember(email: String) {
        server.addMember(knownListId(), email)
        sync()
    }

    /** Stops sharing the list with [email]. Removing the account's own address leaves the list, and the device then takes up another. */
    suspend fun removeMember(email: String) {
        server.removeMember(knownListId(), email)
        sync(findList = true)
    }

    /** Signs out, after a last attempt to send what has changed here, and forgets the copy. */
    suspend fun signOut() {
        sync()
        try {
            server.signOut()
        } catch (_: ServerException) {
            // The session is gone from this device all the same.
        }
        withContext(NonCancellable) {
            guard.withLock {
                store.clear()
                saved.value = Saved(null, ShoppingList())
                sharedWith.value = emptyList()
                trouble.value = null
            }
        }
    }

    private suspend fun knownListId(): String = guard.withLock { loaded().listId } ?: throw ServerException.NotAMember()

    private suspend fun exchange(id: String) {
        val before = guard.withLock { loaded().list }
        val sent = before.outgoing()
        val reply = server.sync(id, before.revision, sent)
        withContext(NonCancellable) {
            guard.withLock {
                val current = loaded()
                if (current.listId == id) {
                    write(id, current.list, current.list.synced(sent, reply.items, reply.revision))
                    sharedWith.value = reply.members
                }
            }
        }
    }

    /**
     * Makes the first of [lists] the list this device holds a copy of, and returns its id. A copy of no list yet keeps what was added to it, to
     * send to this one. A copy of some other list is dropped.
     */
    private suspend fun adopt(lists: List<RemoteList>): String {
        val list = lists.firstOrNull() ?: throw ServerException.NotInvited()
        withContext(NonCancellable) {
            guard.withLock {
                val current = loaded()
                when (current.listId) {
                    list.id -> Unit

                    null -> {
                        saved.value = Saved(list.id, current.list)
                        store.save(list.id, current.list, emptyList())
                    }

                    else -> {
                        store.clear()
                        saved.value = Saved(list.id, ShoppingList())
                        store.save(list.id, ShoppingList(), emptyList())
                    }
                }
                sharedWith.value = list.members
            }
        }
        return list.id
    }

    // Call with the guard held.
    private suspend fun loaded(): Saved = saved.value ?: store.load().also { saved.value = it }

    // Call with the guard held.
    private suspend fun write(listId: String?, before: ShoppingList, after: ShoppingList) {
        if (after == before) return
        saved.value = Saved(listId, after)
        store.save(listId, after, after.changedSince(before))
    }
}
