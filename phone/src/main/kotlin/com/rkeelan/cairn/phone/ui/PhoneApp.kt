package com.rkeelan.cairn.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rkeelan.cairn.phone.CairnViewModel
import com.rkeelan.cairn.phone.Notice
import com.rkeelan.cairn.phone.R
import com.rkeelan.cairn.phone.SignInOptions
import com.rkeelan.cairn.phone.UiState
import com.rkeelan.cairn.phone.googleToken

/** The phone app: whichever screen the state calls for, in Cairn's colours. */
@Composable
fun PhoneApp(model: CairnViewModel, signIn: SignInOptions) {
    val state by model.state.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val activity = LocalContext.current
    CairnTheme {
        val snackbars = remember { SnackbarHostState() }
        Notices(notice, snackbars, onSaid = model::dismissNotice)
        when (val current = state) {
            UiState.Starting -> Surface(Modifier.fillMaxSize()) { }

            is UiState.SignedOut -> SignInScreen(
                signingIn = current.signingIn,
                options = signIn,
                snackbars = snackbars,
                onGoogle = { model.signInWithGoogle { googleToken(activity, signIn.googleClientId) } },
                onDevAccount = { model.signInWithPassword(it, signIn.devPassword) },
            )

            is UiState.NotInvited -> NotInvitedScreen(current.email, snackbars, onRetry = model::refresh, onSignOut = model::signOut)

            is UiState.Ready -> SignedIn(current, model, snackbars)
        }
    }
}

private enum class Screen { LIST, MEMBERS, SETTINGS }

/** The list, with the members and the settings a step away from it. Back returns to the list. */
@Composable
private fun SignedIn(state: UiState.Ready, model: CairnViewModel, snackbars: SnackbarHostState) {
    var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
    BackHandler(enabled = screen != Screen.LIST) { screen = Screen.LIST }
    when (screen) {
        Screen.LIST -> ListScreen(
            state = state,
            snackbars = snackbars,
            onAdd = model::add,
            onTick = model::setTicked,
            onRemove = model::remove,
            onMoreBought = model::showMoreBought,
            onRestore = model::restore,
            onEdit = model::edit,
            onMembers = { screen = Screen.MEMBERS },
            onSettings = { screen = Screen.SETTINGS },
        )

        Screen.MEMBERS -> MembersScreen(
            state = state,
            snackbars = snackbars,
            onAdd = model::addMember,
            onRemove = { email ->
                model.removeMember(email)
                // Having left the list, there are no members to look at.
                if (email == state.email) screen = Screen.LIST
            },
            onBack = { screen = Screen.LIST },
        )

        Screen.SETTINGS -> SettingsScreen(state, snackbars, onOrder = model::setOrder, onSignOut = model::signOut, onBack = { screen = Screen.LIST })
    }
}

/** Says each notice once, in a snackbar. */
@Composable
private fun Notices(notice: Notice?, snackbars: SnackbarHostState, onSaid: () -> Unit) {
    val message = when (notice) {
        null -> return
        Notice.SIGN_IN_FAILED -> stringResource(R.string.notice_sign_in_failed)
        Notice.NO_CONNECTION -> stringResource(R.string.notice_no_connection)
        Notice.NOT_AN_EMAIL -> stringResource(R.string.notice_not_an_email)
        Notice.FAILED -> stringResource(R.string.notice_failed)
    }
    LaunchedEffect(notice) {
        // The notice is cleared only once it has been said: clearing it first would end this effect before the snackbar showed.
        snackbars.showSnackbar(message)
        onSaid()
    }
}

/** One button for each way this build can sign in. */
@Composable
fun SignInScreen(signingIn: Boolean, options: SignInOptions, snackbars: SnackbarHostState, onGoogle: () -> Unit, onDevAccount: (String) -> Unit) {
    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            if (signingIn) {
                CircularProgressIndicator(Modifier.padding(top = 24.dp))
                return@Column
            }
            if (options.googleClientId.isNotEmpty()) {
                Button(onClick = onGoogle, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text(stringResource(R.string.sign_in_with_google)) }
            }
            options.devAccounts.forEach { account ->
                OutlinedButton(onClick = { onDevAccount(account) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_in_as, account)) }
            }
            if (options.googleClientId.isEmpty() && options.devAccounts.isEmpty()) {
                Text(stringResource(R.string.no_sign_in), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Says that the account belongs to no list and may not start one. */
@Composable
fun NotInvitedScreen(email: String, snackbars: SnackbarHostState, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.not_invited, email), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text(stringResource(R.string.try_again)) }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out)) }
        }
    }
}
