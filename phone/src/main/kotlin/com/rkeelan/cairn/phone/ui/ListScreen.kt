package com.rkeelan.cairn.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rkeelan.cairn.core.Item
import com.rkeelan.cairn.core.MAX_NAME_LENGTH
import com.rkeelan.cairn.core.Problem
import com.rkeelan.cairn.core.ShoppingList
import com.rkeelan.cairn.core.Status
import com.rkeelan.cairn.core.keyOf
import com.rkeelan.cairn.phone.R
import com.rkeelan.cairn.phone.UiState
import kotlinx.coroutines.launch

// The most an amount can take to type, such as "2 1/2 " and a unit of the longest length.
private const val MAX_AMOUNT_LENGTH = 30

/**
 * The list: the items still needed, then those bought, the most recently ticked first, with a bar at the bottom for adding to it. A tap ticks an
 * item off or back on, a swipe removes it, and a long press edits its name and amount. Whatever is removed can be put back from the snackbar that
 * says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    state: UiState.Ready,
    snackbars: SnackbarHostState,
    onAdd: (String) -> Unit,
    onTick: (key: String, ticked: Boolean) -> Unit,
    onRemove: (key: String) -> Unit,
    onRestore: (List<Item>) -> Unit,
    onEdit: (key: String, name: String, amount: String) -> Unit,
    onMoreBought: () -> Unit,
    onMembers: () -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val interactions = LocalInteractions.current
    val rows = rememberLazyListState()
    var editing by remember { mutableStateOf<Item?>(null) }
    var added by remember { mutableStateOf<String?>(null) }

    // Says what came off the list, in place of whatever the snackbar was saying, and puts it back if asked to.
    fun offerUndo(message: String, taken: List<Item>) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val answer = snackbars.showSnackbar(message, actionLabel = resources.getString(R.string.undo), duration = SnackbarDuration.Short)
            if (answer == SnackbarResult.ActionPerformed) onRestore(taken)
        }
    }

    // Brings an item just added into view, once the list has it.
    LaunchedEffect(added, state.arrangement) {
        val index = state.arrangement.needed.indexOfFirst { it.key == added }
        if (index >= 0) {
            rows.animateScrollToItem(index)
            added = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                val updates = LocalUpdates.current
                TopAppBar(
                    title = { Text(stringResource(R.string.list_title)) },
                    actions = {
                        updates.phone?.let { TextButton(onClick = it) { Text(stringResource(R.string.update)) } }
                        updates.watch?.let { TextButton(onClick = it) { Text(stringResource(R.string.update_watch)) } }
                        Menu(onMembers, onSettings)
                    },
                )
                SyncNote(state.problem, state.list.unsent.size)
            }
        },
        bottomBar = {
            AddBar(
                list = state.list,
                onAdd = { name ->
                    onAdd(name)
                    added = keyOf(name)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        if (state.arrangement.isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.nothing_to_buy), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val row: @Composable (Item, Modifier) -> Unit = { item, modifier ->
            ItemRow(
                item = item,
                modifier = modifier,
                onTick = {
                    interactions.start("tick")
                    onTick(item.key, it)
                },
                onRemove = {
                    onRemove(item.key)
                    offerUndo(resources.getString(R.string.removed, item.name), listOf(item))
                },
                onEdit = { editing = item },
            )
        }
        LazyColumn(state = rows, modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            items(state.arrangement.needed, key = { "item:${it.key}" }) { row(it, Modifier.animateItem()) }
            if (state.arrangement.bought.isNotEmpty()) {
                item(key = "bought") { BoughtHeading(Modifier.animateItem()) }
                items(state.arrangement.bought, key = { "item:${it.key}" }) { row(it, Modifier.animateItem()) }
            }
            if (state.arrangement.moreBought) {
                // Composed only as the list is scrolled near its end, which is when the next page of bought items is wanted.
                item(key = "more") { LaunchedEffect(state.arrangement.bought.size) { onMoreBought() } }
            }
        }
    }

    editing?.let { item ->
        EditDialog(
            item = item,
            onSave = { name, quantity ->
                onEdit(item.key, name, quantity)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Menu(onMembers: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.members)) },
            onClick = {
                open = false
                onMembers()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            onClick = {
                open = false
                onSettings()
            },
        )
    }
}

/** Says why the list may be behind the server's, when it may be. */
@Composable
private fun SyncNote(problem: Problem?, unsent: Int) {
    val words = when (problem) {
        null, Problem.NOT_INVITED -> return
        Problem.OFFLINE -> if (unsent > 0) pluralStringResource(R.plurals.offline_with_changes, unsent, unsent) else stringResource(R.string.offline)
        Problem.SIGNED_OUT, Problem.FAILED -> stringResource(R.string.sync_failed)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(words, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ItemRow(item: Item, onTick: (Boolean) -> Unit, onRemove: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val ticked = item.status == Status.BOUGHT
    // The list keeps a row's state under the item's key, so an item put back by Undo would return already swiped away, and be removed again.
    // A new state for each change to the item starts it settled.
    val swipe = key(item.changedAt) { rememberSwipeToDismissBoxState() }
    val remove = stringResource(R.string.remove)
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            // Nothing shows behind a row at rest, whose rounded corners would let it.
            if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                    contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        // A swipe is out of reach of a screen reader, which offers this in its place.
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(remove) {
                    onRemove()
                    true
                },
            )
        },
        onDismiss = { onRemove() },
    ) {
        ListItem(
            checked = ticked,
            onCheckedChange = onTick,
            leadingContent = { Checkbox(checked = ticked, onCheckedChange = null) },
            trailingContent = item.amount.toString().takeIf(String::isNotEmpty)?.let { amount -> { Text(amount, style = MaterialTheme.typography.bodyLarge) } },
            onLongClick = onEdit,
            onLongClickLabel = stringResource(R.string.edit),
        ) {
            Text(
                text = item.name,
                textDecoration = if (ticked) TextDecoration.LineThrough else null,
                color = if (ticked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun BoughtHeading(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.bought),
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Where an item is typed and added. While something is typed, the bar offers the items bought before that match it. The keyboard stays up after
 * an item is added, ready for the next.
 */
@Composable
private fun AddBar(list: ShoppingList, onAdd: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val suggestions = remember(list, typed) { list.suggestions(typed) }
    val interactions = LocalInteractions.current
    val add = { name: String ->
        if (name.isNotBlank()) {
            onAdd(name)
            typed = ""
        }
    }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            if (suggestions.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions, key = { it }) { name ->
                        SuggestionChip(onClick = {
                            interactions.start("add")
                            add(name)
                        }, label = { Text(name) })
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_NAME_LENGTH) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.add_item)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add(typed) }),
                )
                FilledIconButton(onClick = {
                    interactions.start("add")
                    add(typed)
                }, enabled = typed.isNotBlank()) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                }
            }
        }
    }
}

@Composable
private fun EditDialog(item: Item, onSave: (name: String, amount: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(item.key) { mutableStateOf(item.name) }
    var quantity by rememberSaveable(item.key) { mutableStateOf(item.amount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(name, quantity) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.take(MAX_AMOUNT_LENGTH) },
                    label = { Text(stringResource(R.string.amount)) },
                    placeholder = { Text(stringResource(R.string.amount_example)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name, quantity) }),
                )
            }
        },
    )
}
