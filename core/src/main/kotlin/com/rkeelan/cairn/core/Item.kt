package com.rkeelan.cairn.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where an item stands. */
@Serializable
enum class Status {
    /** Still to buy. */
    @SerialName("needed")
    NEEDED,

    /** Ticked off. The list shows bought items after the needed ones, so that a tick can be undone, and suggests them when a name is typed. */
    @SerialName("bought")
    BOUGHT,

    /** Taken off the list without being bought, as a mistyped item is. The list neither shows nor suggests these. */
    @SerialName("removed")
    REMOVED,
}

/**
 * An item the list knows about. An item taken off the list is kept, with a status that says so: that is how the change reaches the other devices.
 *
 * [key] identifies the item within its list. It is [keyOf] the name the item was first added with, so two names that differ only in case or spacing
 * are one item. [quantity] and [unit] say how much to buy, and either may be null (see [Amount]). [addedAt] is when the item was last put on the
 * list, and gives the list its order. [changedAt] is when the item last changed in any way, which for a bought item is when it was ticked: where two
 * changes to an item meet, the later one wins. Both are in milliseconds since the epoch.
 */
@Serializable
data class Item(
    val key: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val status: Status,
    val addedAt: Long,
    val changedAt: Long,
) {
    /** How much of the item to buy. */
    val amount: Amount get() = Amount(quantity, unit)
}

/** The longest name an item can have. Longer names are cut to this length. */
const val MAX_NAME_LENGTH = 100

private val spaces = Regex("""[\s\p{Z}]+""")

/** [text] as the list shows it: without spaces around it, with single spaces inside it, and no longer than [maxLength]. */
fun tidy(text: String, maxLength: Int = MAX_NAME_LENGTH): String {
    val spaced = text.replace(spaces, " ").trim()
    if (spaced.length <= maxLength) return spaced
    // Cutting between the two halves of a surrogate pair would leave half a character.
    val end = if (spaced[maxLength - 1].isHighSurrogate()) maxLength - 1 else maxLength
    return spaced.substring(0, end).trimEnd()
}

/** What identifies the item called [name]: names that differ only in case or spacing give the same key. */
fun keyOf(name: String): String = tidy(name).lowercase()
