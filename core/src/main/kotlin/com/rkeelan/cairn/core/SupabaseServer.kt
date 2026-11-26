package com.rkeelan.cairn.core

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * The Supabase project that holds the lists, at [url], reached with its publishable [key].
 *
 * The project's database does the work: `supabase/migrations` defines the tables and the functions called here. A session is kept on the device
 * and refreshed as needed, unless [rememberSession] is false, as it is in tests.
 */
class SupabaseServer(url: String, key: String, rememberSession: Boolean = true) : Server {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val client = createSupabaseClient(url, key) {
        install(Auth) {
            // A sync in the background needs the session as much as one on screen does.
            enableLifecycleCallbacks = false
            if (!rememberSession) sessionManager = MemorySessionManager()
        }
        install(Postgrest)
        install(Realtime)
    }

    override val account: StateFlow<Account> = client.auth.sessionStatus
        .map { status ->
            when (status) {
                SessionStatus.Initializing -> Account.Unknown

                is SessionStatus.NotAuthenticated -> Account.SignedOut

                // A session that could not be refreshed, as when there is no connection, is still a session.
                is SessionStatus.Authenticated, is SessionStatus.RefreshFailure ->
                    client.auth.currentUserOrNull()?.email?.let { Account.SignedIn(it.lowercase()) } ?: Account.SignedOut
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, Account.Unknown)

    override suspend fun signInWithGoogle(idToken: String, nonce: String) {
        calling {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.nonce = nonce
                provider = Google
            }
        }
    }

    /** Makes the account first if there is none. Only a local Supabase stack has accounts with passwords: a hosted project signs in with Google alone. */
    override suspend fun signInWithPassword(email: String, password: String) {
        calling {
            try {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            } catch (_: AuthRestException) {
                client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
        }
    }

    override suspend fun lists(): List<RemoteList> = calling { client.postgrest.rpc("my_lists").decodeList<RemoteList>() }

    override suspend fun sync(listId: String, since: Long, changes: List<Item>): SyncReply = calling {
        val parameters = buildJsonObject {
            put("list", listId)
            put("since", since)
            put("changes", wholeItems.encodeToJsonElement(changes))
        }
        client.postgrest.rpc("sync", parameters).decodeAs<SyncReply>()
    }

    override suspend fun addMember(listId: String, email: String) {
        calling {
            client.postgrest.rpc(
                "add_member",
                buildJsonObject {
                    put("list", listId)
                    put("member", email)
                },
            )
        }
    }

    override suspend fun removeMember(listId: String, email: String) {
        calling {
            client.postgrest.rpc(
                "remove_member",
                buildJsonObject {
                    put("list", listId)
                    put("member", email)
                },
            )
        }
    }

    /**
     * Emits when Realtime reports that the list's revision has changed, and when the subscription comes back after a break, since changes made
     * during the break went unreported.
     */
    override fun changes(listId: String): Flow<Unit> = flow {
        val channel = client.channel("list-$listId")
        val revisions = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "lists"
            filter("id", FilterOperator.EQ, listId)
        }
        val resubscriptions = channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.drop(1)
        try {
            channel.subscribe()
            emitAll(merge(revisions.map { }, resubscriptions.map { }))
        } finally {
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }.retry { cause ->
        // Realtime could not be reached. Whatever is missed meanwhile, the next sync brings.
        (cause is IOException || cause is RestException).also { if (it) delay(RETRY_AFTER) }
    }

    override suspend fun signOut() {
        calling { client.auth.signOut() }
    }

    private suspend fun <T> calling(call: suspend () -> T): T = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PostgrestRestException) {
        throw when {
            e.code == NOT_INVITED -> ServerException.NotInvited()
            e.code == NOT_A_MEMBER -> ServerException.NotAMember()
            e.code == NOT_AN_EMAIL -> ServerException.NotAnEmail()
            e.statusCode == UNAUTHORIZED -> ServerException.SignedOut(e)
            else -> ServerException.Failed(e)
        }
    } catch (e: AuthRestException) {
        throw ServerException.Refused(e)
    } catch (e: RestException) {
        throw if (e.statusCode == UNAUTHORIZED) ServerException.SignedOut(e) else ServerException.Failed(e)
    } catch (e: IOException) {
        throw ServerException.Unreachable(e)
    }

    private companion object {
        // The SQLSTATEs the functions in supabase/migrations raise.
        const val NOT_INVITED = "CA001"
        const val NOT_A_MEMBER = "CA002"
        const val NOT_AN_EMAIL = "CA003"

        const val UNAUTHORIZED = 401

        // Writes every field of an item, a missing quantity or unit included, which Json leaves out by default.
        val wholeItems = Json { encodeDefaults = true }

        val RETRY_AFTER = 10.seconds
    }
}
