package com.rkeelan.cairn.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rkeelan.cairn.core.Order
import com.rkeelan.cairn.phone.R
import com.rkeelan.cairn.phone.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val interactions = LocalInteractions.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = {
                interactions.start("back")
                onBack()
            }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
        },
    )
}

/** The people the list is shared with. Anyone on it can add someone by email address, remove someone, or leave. */
@Composable
fun MembersScreen(state: UiState.Ready, snackbars: SnackbarHostState, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onBack: () -> Unit) {
    var removing by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { TopBar(stringResource(R.string.members), onBack) },
        bottomBar = { AddMemberBar(onAdd) },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(state.members, key = { it }) { email ->
                val own = email == state.email
                ListItem(
                    supportingContent = if (own) ({ Text(stringResource(R.string.you)) }) else null,
                    trailingContent = {
                        IconButton(onClick = { removing = email }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(if (own) R.string.leave else R.string.remove))
                        }
                    },
                ) { Text(email) }
            }
        }
    }
    removing?.let { email ->
        val own = email == state.email
        AlertDialog(
            onDismissRequest = { removing = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        onRemove(email)
                    },
                ) { Text(stringResource(if (own) R.string.leave else R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(if (own) stringResource(R.string.leave_question) else stringResource(R.string.remove_question, email)) },
        )
    }
}

@Composable
private fun AddMemberBar(onAdd: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val add = {
        if (typed.isNotBlank()) {
            onAdd(typed)
            typed = ""
        }
    }
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.add_member)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            FilledIconButton(onClick = add, enabled = typed.isNotBlank()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
            }
        }
    }
}

/** The order this phone shows the list in, and signing out. */
@Composable
fun SettingsScreen(state: UiState.Ready, snackbars: SnackbarHostState, onOrder: (Order) -> Unit, onSignOut: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopBar(stringResource(R.string.settings), onBack) }, snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Heading(stringResource(R.string.order))
            Order.entries.forEach { order ->
                val chosen = order == state.settings.order
                ListItem(selected = chosen, onClick = { onOrder(order) }, leadingContent = { RadioButton(selected = chosen, onClick = null) }) {
                    Text(
                        stringResource(
                            when (order) {
                                Order.OLDEST_FIRST -> R.string.order_oldest_first
                                Order.NEWEST_FIRST -> R.string.order_newest_first
                                Order.ALPHABETICAL -> R.string.order_alphabetical
                            },
                        ),
                    )
                }
            }
            Heading(stringResource(R.string.account))
            ListItem { Text(state.email) }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.sign_out)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
