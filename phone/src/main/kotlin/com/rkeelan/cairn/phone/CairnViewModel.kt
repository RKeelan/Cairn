package com.rkeelan.cairn.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rkeelan.cairn.core.Account
import com.rkeelan.cairn.core.Amount
import com.rkeelan.cairn.core.Arrangement
import com.rkeelan.cairn.core.Item
import com.rkeelan.cairn.core.ListRepository
import com.rkeelan.cairn.core.Order
import com.rkeelan.cairn.core.Problem
import com.rkeelan.cairn.core.Server
import com.rkeelan.cairn.core.ServerException
import com.rkeelan.cairn.core.Settings
import com.rkeelan.cairn.core.ShoppingList
import com.rkeelan.cairn.core.Sync
import com.rkeelan.cairn.core.arranged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the app shows. */
sealed interface UiState {
    /** Nothing yet: the saved session and the saved list are still being read. */
    data object Starting : UiState

    data class SignedOut(val signingIn: Boolean) : UiState

    /** [email] is signed in but belongs to no list and may not start one. */
    data class NotInvited(val email: String) : UiState

    /**
     * The list, with [arrangement] laying its items out as [settings] say. [members] are the people it is shared with, and [problem] says why
     * the copy may be behind the server's, if it may be. [email] is empty for the moment after the app starts when the list is on screen and the
     * saved session is still being read.
     */
    data class Ready(
        val email: String,
        val list: ShoppingList,
        val arrangement: Arrangement,
        val settings: Settings,
        val members: List<String>,
        val problem: Problem?,
    ) : UiState
}

/** Something that went wrong, for a screen to say once. */
enum class Notice { SIGN_IN_FAILED, NO_CONNECTION, NOT_AN_EMAIL, FAILED }

/** The state behind every screen, and what the screens can do to it. */
class CairnViewModel(private val repository: ListRepository, private val server: Server, private val settingsStore: SettingsStore, private val sync: Sync) :
    ViewModel() {
    private val signingIn = MutableStateFlow(false)
    private val said = MutableStateFlow<Notice?>(null)
    private val boughtShown = MutableStateFlow(Arrangement.BOUGHT_PAGE)

    private class Sharing(val listId: String?, val members: List<String>, val problem: Problem?, val signingIn: Boolean, val boughtShown: Int)

    private val sharing = combine(repository.listId, repository.members, repository.problem, signingIn, boughtShown, ::Sharing)

    val state: StateFlow<UiState> = combine(server.account, repository.list, settingsStore.settings, sharing) { account, list, settings, sharing ->
        fun ready(email: String) = UiState.Ready(email, list, list.arranged(settings, sharing.boughtShown), settings, sharing.members, sharing.problem)
        when (account) {
            // A device with a copy of a list was signed in when it last ran, so it shows the copy without waiting for the session.
            Account.Unknown -> if (sharing.listId != null) ready(email = "") else UiState.Starting

            Account.SignedOut -> UiState.SignedOut(sharing.signingIn)

            is Account.SignedIn -> if (sharing.problem == Problem.NOT_INVITED) UiState.NotInvited(account.email) else ready(account.email)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), UiState.Starting)

    /** What went wrong most recently, until [dismissNotice]. */
    val notice: StateFlow<Notice?> = said.asStateFlow()

    fun dismissNotice() {
        said.value = null
    }

    /** Adds [name], with [amount] if it is not none. */
    fun add(name: String, amount: Amount = Amount.NONE) = edit { add(name, it, amount) }

    fun setTicked(key: String, ticked: Boolean) = edit { if (ticked) tick(key, it) else untick(key, it) }

    fun remove(key: String) = edit { remove(key, it) }

    /** Undoes the taking of [taken] off the list. They are the items as they were before. */
    fun restore(taken: List<Item>) = edit { restore(taken, it) }

    fun edit(key: String, name: String, amount: String) = edit { edit(key, name, Amount.parse(amount), it) }

    /** Shows another page of bought items, as the list is scrolled towards its end. */
    fun showMoreBought() {
        boughtShown.value += Arrangement.BOUGHT_PAGE
    }

    fun setOrder(order: Order) = settingsStore.set(settingsStore.settings.value.copy(order = order))

    fun addMember(email: String) = attempt { repository.addMember(email) }

    fun removeMember(email: String) = attempt { repository.removeMember(email) }

    /** Asks the server again, as after an account has been invited. */
    fun refresh() = sync.request(findList = true)

    fun signOut() = attempt { repository.signOut() }

    fun signInWithPassword(email: String, password: String) = signIn { server.signInWithPassword(email, password) }

    /** Signs in with the token [token] obtains, which is where the user chooses a Google account. */
    fun signInWithGoogle(token: suspend () -> GoogleToken) = signIn {
        val google = token()
        server.signInWithGoogle(google.idToken, google.nonce)
    }

    private fun signIn(with: suspend () -> Unit) {
        if (signingIn.value) return
        signingIn.value = true
        viewModelScope.launch {
            try {
                with()
                repository.sync(findList = true)
            } catch (e: ServerException) {
                said.value = if (e is ServerException.Unreachable) Notice.NO_CONNECTION else Notice.SIGN_IN_FAILED
            } catch (e: GoogleSignInException) {
                if (!e.cancelled) said.value = Notice.SIGN_IN_FAILED
            } finally {
                signingIn.value = false
            }
        }
    }

    private fun edit(change: ShoppingList.(now: Long) -> ShoppingList) {
        viewModelScope.launch {
            repository.edit(change)
            sync.request()
        }
    }

    private fun attempt(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: ServerException) {
                said.value = when (e) {
                    is ServerException.Unreachable -> Notice.NO_CONNECTION
                    is ServerException.NotAnEmail -> Notice.NOT_AN_EMAIL
                    else -> Notice.FAILED
                }
            }
        }
    }

    companion object {
        // Long enough to keep the state through a rotation.
        private const val STOP_AFTER_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as CairnApplication
                CairnViewModel(application.repository, application.server, application.settings, application.sync)
            }
        }
    }
}
