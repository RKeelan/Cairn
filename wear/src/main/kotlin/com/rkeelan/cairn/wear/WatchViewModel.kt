package com.rkeelan.cairn.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rkeelan.cairn.link.Link
import com.rkeelan.cairn.link.Received
import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.link.WatchList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** What the watch shows. */
sealed interface WatchState {
    /** Nothing yet: the list the phone last sent is still being read. */
    data object Starting : WatchState

    /** The phone has sent no list, or has said there is none, as when nobody is signed in on it. */
    data object NoList : WatchState

    /** The [needed] items, then up to as many [bought] ones as have been asked for, and whether there are [moreBought]. */
    data class Ready(val needed: List<WatchItem>, val bought: List<WatchItem>, val moreBought: Boolean) : WatchState {
        val isEmpty: Boolean get() = needed.isEmpty() && bought.isEmpty()

        /** The rows of the list, top to bottom: the needed items, then a heading and the bought items, then a place that asks for more of them. */
        val rows: List<WatchRow> by lazy {
            buildList {
                needed.mapTo(this, WatchRow::Item)
                if (bought.isNotEmpty()) {
                    add(WatchRow.BoughtHeading)
                    bought.mapTo(this, WatchRow::Item)
                }
                if (moreBought) add(WatchRow.MoreBought)
            }
        }
    }
}

/** A row of the watch's list. */
sealed interface WatchRow {
    data class Item(val item: WatchItem) : WatchRow

    data object BoughtHeading : WatchRow

    /** Asks for the next page of bought items when it is shown, near the end of the list. */
    data object MoreBought : WatchRow
}

/**
 * The state behind the watch's one screen. [received] is what the Data Layer holds: the list as the phone last sent it, and the ticks on their way
 * to it. [send] sends a tick. A tick shows at once, and stays shown until the list from the phone shows it.
 */
class WatchViewModel(received: Flow<Received>, private val send: suspend (Tick) -> Unit, private val clock: () -> Long = System::currentTimeMillis) :
    ViewModel() {
    private val waiting = MutableStateFlow(WaitingTicks())
    private val boughtShown = MutableStateFlow(BOUGHT_PAGE)
    private val latest = MutableStateFlow<WatchList?>(null)

    val state: StateFlow<WatchState> = combine(received, waiting, boughtShown) { received, _, shown ->
        // Lets go of the ticks the phone's list now shows. It works on the ticks as they are now, rather than as combine last saw them, so that
        // a tap made in between keeps its tick. Should that change them, this runs once more, and changes nothing.
        val stillWaiting = waiting.updateAndGet { it.after(received) }
        val list = received.list?.with(stillWaiting.ticks)
        latest.value = list
        when (list) {
            null -> WatchState.NoList
            else -> WatchState.Ready(list.needed, list.bought.take(shown), list.bought.size > shown)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), WatchState.Starting)

    /** Marks the item with this [key] bought, or needed again. */
    fun setBought(key: String, bought: Boolean) {
        // With the ticks made since the list was last shown, as when two taps come before the next frame.
        val tick = latest.value?.with(waiting.value.ticks)?.tick(key, bought, clock()) ?: return
        waiting.value += tick
        viewModelScope.launch {
            // A tick that could not be sent is undone. One that was sent stays shown until the phone's list shows it.
            if (runCatching { send(tick) }.isFailure) waiting.update { it.undo(tick) }
        }
    }

    /** Shows another page of bought items, as the list is scrolled towards its end. */
    fun showMoreBought() {
        boughtShown.value += BOUGHT_PAGE
    }

    companion object {
        /** How many bought items the watch shows at first, and how many more it shows each time. */
        const val BOUGHT_PAGE = 20

        // Long enough to keep the state through the screen turning off and on.
        private const val STOP_AFTER_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = this[APPLICATION_KEY]!!
                WatchViewModel(Link.list(context), send = { Link.sendTick(context, it) })
            }
        }
    }
}
