package com.rkeelan.cairn.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rkeelan.cairn.link.Link
import com.rkeelan.cairn.link.Tick
import com.rkeelan.cairn.link.WatchItem
import com.rkeelan.cairn.link.WatchList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
 * The state behind the watch's one screen. [lists] is the list as the phone last sent it, with the ticks on their way to it laid over it, and
 * [send] sends a tick. A tick shows at once, without waiting for the Data Layer to report it.
 */
class WatchViewModel(lists: Flow<WatchList?>, private val send: suspend (Tick) -> Unit, private val clock: () -> Long = System::currentTimeMillis) :
    ViewModel() {
    private val sending = MutableStateFlow<List<Tick>>(emptyList())
    private val boughtShown = MutableStateFlow(BOUGHT_PAGE)
    private val latest = MutableStateFlow<WatchList?>(null)

    val state: StateFlow<WatchState> = combine(lists, sending, boughtShown) { list, sending, shown ->
        latest.value = list
        when (list) {
            null -> WatchState.NoList

            else -> {
                val shownList = list.with(sending)
                WatchState.Ready(shownList.needed, shownList.bought.take(shown), shownList.bought.size > shown)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), WatchState.Starting)

    /** Marks the item with this [key] bought, or needed again. */
    fun setBought(key: String, bought: Boolean) {
        val list = latest.value?.with(sending.value) ?: return
        val tick = list.tick(key, bought, clock()) ?: return
        sending.value += tick
        viewModelScope.launch {
            // A tick sent is a data item, which the list the Data Layer reports lays over itself. A tick that could not be sent is undone.
            runCatching { send(tick) }
            sending.value -= tick
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
