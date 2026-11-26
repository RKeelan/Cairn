package com.rkeelan.cairn.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Decides when the device exchanges changes with the server: after each change made here, when the server reports a change made elsewhere, and
 * now and then regardless, in case a report was missed.
 *
 * Requests made while an exchange is under way are answered by one further exchange, not one each. [whenOffline] is called when an exchange
 * finds the server out of reach, so that the app can arrange another for when the connection returns.
 */
class Sync(
    private val repository: ListRepository,
    private val server: Server,
    scope: CoroutineScope,
    private val every: Duration = 1.minutes,
    private val whenOffline: () -> Unit = {},
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val findList = AtomicBoolean(false)

    init {
        scope.launch {
            for (request in requests) {
                if (server.account.value !is Account.SignedIn) continue
                val synced = repository.sync(findList = findList.getAndSet(false))
                if (!synced && repository.problem.value == Problem.OFFLINE) whenOffline()
            }
        }
    }

    /** Asks for an exchange with the server soon. With [findList], it first asks the server which list the account should be using. */
    fun request(findList: Boolean = false) {
        if (findList) this.findList.set(true)
        requests.trySend(Unit)
    }

    /**
     * Keeps the copy current for as long as it runs, which is for as long as a screen shows the list: an exchange when it starts and whenever
     * someone signs in, then one each time the server reports a change, and one [every] so often.
     */
    suspend fun whileShowing() {
        server.account.collectLatest { account ->
            if (account !is Account.SignedIn) return@collectLatest
            request(findList = true)
            coroutineScope {
                launch {
                    repository.listId.filterNotNull().collectLatest { id -> server.changes(id).collect { request() } }
                }
                while (true) {
                    delay(every)
                    request()
                }
            }
        }
    }
}
