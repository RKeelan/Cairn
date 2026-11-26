package com.rkeelan.cairn.core

/**
 * One device's copy of the shopping list. A copy never changes: each operation returns the copy that results.
 *
 * The server holds the list. A device changes its own copy at once, connected or not, and remembers which items it changed in [unsent]. When it
 * next reaches the server it sends those items and takes back what others changed (see [synced]). Where two changes to an item meet, the one made
 * later wins.
 */
class ShoppingList private constructor(
    private val byKey: Map<String, Item>,
    /** The keys of the items changed on this device that the server has not yet taken. */
    val unsent: Set<String>,
    /** The server's revision of the list when this copy last heard from it. */
    val revision: Long,
) {
    constructor() : this(emptyMap(), emptySet(), 0)

    /** Every item the list knows about, in no particular order, those taken off the list included. */
    val items: Collection<Item> get() = byKey.values

    /** The items still to buy, in the order they were added. */
    val needed: List<Item> by lazy { byKey.values.filter { it.status == Status.NEEDED }.sortedWith(oldestFirst) }

    /** The items ticked off, the most recently ticked first. */
    val bought: List<Item> by lazy { byKey.values.filter { it.status == Status.BOUGHT }.sortedWith(latestFirst) }

    private val latest: Long by lazy { byKey.values.maxOfOrNull { maxOf(it.changedAt, it.addedAt) } ?: 0 }

    /** The item with this [key], if the list knows of one. */
    operator fun get(key: String): Item? = byKey[key]

    /**
     * Puts [name] on the list as an item still to buy. An item already needed takes [amount] if one is given, so adding the same name twice is the
     * same as adding it once. A blank name adds nothing.
     */
    fun add(name: String, now: Long, amount: Amount = Amount.NONE): ShoppingList {
        val tidied = tidy(name)
        if (tidied.isEmpty()) return this
        val key = keyOf(tidied)
        val existing = byKey[key]
        val at = stamp(now)
        return if (existing?.status == Status.NEEDED) {
            if (amount.isNone || amount == existing.amount) this else changed(existing.with(amount).copy(changedAt = at))
        } else {
            changed(Item(key, tidied, amount.quantity, amount.unit, Status.NEEDED, addedAt = at, changedAt = at))
        }
    }

    /** Marks the item with this [key] bought. */
    fun tick(key: String, now: Long): ShoppingList = move(key, now, from = Status.NEEDED, to = Status.BOUGHT)

    /** Makes the bought item with this [key] needed again, in the place it had. */
    fun untick(key: String, now: Long): ShoppingList = move(key, now, from = Status.BOUGHT, to = Status.NEEDED)

    /**
     * Takes a tick made elsewhere that reached this device directly, as a watch's ticks reach the phone: the item with this [key] marked [bought],
     * or needed again, [at] that time. A tick no later than the item's last change gives way to it, as a change from the server would.
     */
    fun ticked(key: String, bought: Boolean, at: Long): ShoppingList {
        val item = byKey[key]?.takeIf { it.status != Status.REMOVED && at > it.changedAt } ?: return this
        val status = if (bought) Status.BOUGHT else Status.NEEDED
        return if (status == item.status) this else changed(item.copy(status = status, changedAt = at))
    }

    /** Takes the item with this [key] off the list, needed or bought, so that the list neither shows nor suggests it. */
    fun remove(key: String, now: Long): ShoppingList {
        val item = byKey[key]?.takeIf { it.status != Status.REMOVED } ?: return this
        return changed(item.copy(status = Status.REMOVED, changedAt = stamp(now)))
    }

    /** Undoes the removal of [taken], the items as they were before. Any since put back on the list is left alone. */
    fun restore(taken: Collection<Item>, now: Long): ShoppingList {
        val at = stamp(now)
        return changed(taken.filter { it.status != Status.REMOVED && byKey[it.key]?.status == Status.REMOVED }.map { it.copy(changedAt = at) })
    }

    /**
     * Gives the needed item with this [key] a new [name] and [amount]. A name that makes it a different item takes the old item off the list and puts
     * the new one in its place; if that item is already needed, the two become one. A blank name changes nothing.
     */
    fun edit(key: String, name: String, amount: Amount, now: Long): ShoppingList {
        val item = byKey[key]?.takeIf { it.status == Status.NEEDED } ?: return this
        val tidied = tidy(name)
        if (tidied.isEmpty()) return this
        val at = stamp(now)
        val newKey = keyOf(tidied)
        if (newKey == key) {
            val wanted = item.copy(name = tidied).with(amount)
            return if (wanted == item) this else changed(wanted.copy(changedAt = at))
        }
        val there = byKey[newKey]?.takeIf { it.status == Status.NEEDED }
        val renamed = there?.with(if (amount.isNone) there.amount else amount)?.copy(changedAt = at)
            ?: Item(newKey, tidied, amount.quantity, amount.unit, Status.NEEDED, addedAt = item.addedAt, changedAt = at)
        return changed(listOf(item.copy(status = Status.REMOVED, changedAt = at), renamed))
    }

    /**
     * Names to offer for what has been [typed] so far: items bought before with a word that starts with it, the most recently bought first. Nothing
     * typed offers nothing.
     */
    fun suggestions(typed: String, limit: Int = 5): List<String> {
        val start = keyOf(typed)
        if (start.isEmpty()) return emptyList()
        return bought
            .filter { it.key.startsWith(start) || " $start" in it.key }
            .take(limit)
            .map(Item::name)
    }

    /** The items to send to the server: those changed here that it has not yet taken. */
    fun outgoing(): List<Item> = unsent.mapNotNull(byKey::get)

    /**
     * This copy after an exchange with the server, which was sent [sent], answered with [received], and is now at [revision]. An item changed here
     * while the exchange was under way stays unsent, and gives way to a received item only if that one is at least as late.
     */
    fun synced(sent: Collection<Item>, received: Collection<Item>, revision: Long): ShoppingList {
        val items = byKey.toMutableMap()
        val waiting = unsent.toMutableSet()
        sent.forEach { if (items[it.key] == it) waiting -= it.key }
        for (theirs in received) {
            val ours = items[theirs.key]
            if (ours == null || theirs.key !in waiting || theirs.changedAt >= ours.changedAt) {
                items[theirs.key] = theirs
                waiting -= theirs.key
            }
        }
        return ShoppingList(items, waiting, revision)
    }

    /** The items whose content or unsent state differs from [before]'s, which is what storage has to write to turn [before] into this copy. */
    fun changedSince(before: ShoppingList): List<Item> = byKey.values.filter { before.byKey[it.key] != it || (it.key in before.unsent) != (it.key in unsent) }

    override fun equals(other: Any?): Boolean = other is ShoppingList && other.byKey == byKey && other.unsent == unsent && other.revision == revision

    override fun hashCode(): Int = 31 * (31 * byKey.hashCode() + unsent.hashCode()) + revision.hashCode()

    override fun toString(): String = "ShoppingList(revision=$revision, unsent=$unsent, items=${byKey.values})"

    private fun move(key: String, now: Long, from: Status, to: Status): ShoppingList {
        val item = byKey[key]?.takeIf { it.status == from } ?: return this
        return changed(item.copy(status = to, changedAt = stamp(now)))
    }

    private fun Item.with(amount: Amount): Item = copy(quantity = amount.quantity, unit = amount.unit)

    private fun changed(item: Item): ShoppingList = changed(listOf(item))

    private fun changed(changes: Collection<Item>): ShoppingList =
        if (changes.isEmpty()) this else ShoppingList(byKey + changes.associateBy(Item::key), unsent + changes.map(Item::key), revision)

    /**
     * The time to give a change made at [now]: later than everything the list has seen. A device whose clock is behind would otherwise make changes
     * that lose to the ones they follow.
     */
    private fun stamp(now: Long): Long = maxOf(now, latest + 1)

    companion object {
        /** A copy as storage holds it: [items], of which those with [unsent] keys are yet to reach the server, last synced at [revision]. */
        fun of(items: Collection<Item>, unsent: Set<String>, revision: Long): ShoppingList {
            val byKey = items.associateBy(Item::key)
            return ShoppingList(byKey, unsent intersect byKey.keys, revision)
        }

        private val oldestFirst = compareBy(Item::addedAt, Item::key)

        private val latestFirst = compareByDescending(Item::changedAt).thenBy(Item::key)
    }
}
