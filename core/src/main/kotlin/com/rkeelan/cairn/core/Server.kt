package com.rkeelan.cairn.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Who is signed in on this device. */
sealed interface Account {
    /** Not yet known: the saved session is still being read. */
    data object Unknown : Account

    data object SignedOut : Account

    data class SignedIn(val email: String) : Account
}

/** A list on the server and the email addresses of the people it is shared with. */
@Serializable
data class RemoteList(val id: String, val name: String, val members: List<String>)

/** The server's answer to a sync: the list's [revision], its [members], and the [items] changed after the revision the device last saw. */
@Serializable
data class SyncReply(val revision: Long, val members: List<String>, val items: List<Item>)

/** The server that holds the lists, as the signed-in account sees it. Every call but [account] and [changes] throws [ServerException] on failure. */
interface Server {
    val account: StateFlow<Account>

    /** Signs in with an ID token from Google. [nonce] is the nonce the token was asked for with, before hashing. */
    suspend fun signInWithGoogle(idToken: String, nonce: String)

    /** Signs in with an email address and a password, which only a server for development accepts. */
    suspend fun signInWithPassword(email: String, password: String)

    /** The account's lists. The apps show the first. An account that belongs to no list is given one, if it has been invited. */
    suspend fun lists(): List<RemoteList>

    /** Sends [changes] to the list and returns what changed there after revision [since]. */
    suspend fun sync(listId: String, since: Long, changes: List<Item>): SyncReply

    /** Shares the list with whoever signs in with [email]. */
    suspend fun addMember(listId: String, email: String)

    /** Stops sharing the list with [email], which may be the account's own. */
    suspend fun removeMember(listId: String, email: String)

    /** Emits whenever the list may have changed on the server, for as long as it is collected. */
    fun changes(listId: String): Flow<Unit>

    suspend fun signOut()
}

/** Why a call to the server failed. */
sealed class ServerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The server could not be reached, as when the device has no connection. */
    class Unreachable(cause: Throwable) : ServerException("The server could not be reached", cause)

    /** Nobody is signed in, or the session is no longer good. */
    class SignedOut(cause: Throwable? = null) : ServerException("Not signed in", cause)

    /** The account belongs to no list and has not been invited to start one. */
    class NotInvited : ServerException("This account has not been invited")

    /** The account is not a member of the list, or the list is gone. */
    class NotAMember : ServerException("Not a member of this list")

    /** What was given as a member's email address is not one. */
    class NotAnEmail : ServerException("Not an email address")

    /** The sign-in was refused: a wrong password, or a token the server does not accept. */
    class Refused(cause: Throwable) : ServerException("The sign-in was refused", cause)

    class Failed(cause: Throwable) : ServerException(cause.message ?: "The server failed", cause)
}
