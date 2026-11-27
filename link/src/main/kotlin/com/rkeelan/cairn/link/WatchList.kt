package com.rkeelan.cairn.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An item as the watch shows it. The phone works out everything the watch needs: [amount] as the list writes it, such as "2 loaves", and [rank],
 * the item's place among the needed items in the phone's order, which it keeps while bought so that unticking puts it back where it was.
 */
@Serializable
data class WatchItem(val key: String, val name: String, val amount: String = "", val bought: Boolean, val rank: Int, val changedAt: Long)

/** The list as the phone sends it to the watch: every item needed or bought. */
@Serializable
data class WatchList(val items: List<WatchItem>) {
    /** The needed items, in the phone's order. */
    val needed: List<WatchItem> by lazy { items.filterNot(WatchItem::bought).sortedBy(WatchItem::rank) }

    /** The bought items, the most recently ticked first. */
    val bought: List<WatchItem> by lazy { items.filter(WatchItem::bought).sortedWith(compareByDescending(WatchItem::changedAt).thenBy(WatchItem::rank)) }

    /** The list as the watch shows it while [ticks] are on their way to the phone. A tick older than the item's last change gives way to it. */
    fun with(ticks: Collection<Tick>): WatchList {
        if (ticks.isEmpty()) return this
        val latest = ticks.groupBy(Tick::key).mapValues { (_, all) -> all.maxBy(Tick::at) }
        return WatchList(
            items.map { item ->
                val tick = latest[item.key]
                if (tick != null && tick.at > item.changedAt) item.copy(bought = tick.bought, changedAt = tick.at) else item
            },
        )
    }

    /** A tick that marks the item with this [key] bought, or needed again, made [now] by the watch's clock, or null if the list has no such item. */
    fun tick(key: String, bought: Boolean, now: Long): Tick? {
        val item = items.firstOrNull { it.key == key } ?: return null
        // Later than the change it follows, whatever the watch's clock says.
        return Tick(key, bought, maxOf(now, item.changedAt + 1))
    }

    fun encode(): ByteArray = json.encodeToString(this).toByteArray()

    companion object {
        fun decode(bytes: ByteArray): WatchList = json.decodeFromString(bytes.decodeToString())
    }
}

/** A tick made on the watch: the item with [key] marked bought, or needed again, at [at] in milliseconds since the epoch. */
@Serializable
data class Tick(val key: String, val bought: Boolean, val at: Long) {
    fun encode(): ByteArray = json.encodeToString(this).toByteArray()

    companion object {
        fun decode(bytes: ByteArray): Tick = json.decodeFromString(bytes.decodeToString())
    }
}

/**
 * What the watch tells the phone about the watch app installed on it: its [versionCode], and whether Google Play installed it, [fromPlay]. The
 * phone offers to update a watch app that Play installed and that is older than the phone app.
 */
@Serializable
data class WatchApp(val versionCode: Long = 0, val fromPlay: Boolean = false) {
    /**
     * Whether this watch app is an older version of Cairn than the phone app whose version code is [phoneVersionCode]. Each version is published
     * to both, with the watch app's code [WATCH_VERSION_CODE_OFFSET] above the phone app's, so Play then has a newer watch app too.
     */
    fun isOlderThan(phoneVersionCode: Long): Boolean = versionCode - WATCH_VERSION_CODE_OFFSET < phoneVersionCode

    fun encode(): ByteArray = json.encodeToString(this).toByteArray()

    companion object {
        fun decode(bytes: ByteArray): WatchApp = json.decodeFromString(bytes.decodeToString())
    }
}

/** How far the watch app's version code is above the phone app's for the same version of Cairn, as the root build.gradle.kts sets it. */
const val WATCH_VERSION_CODE_OFFSET = 1_000_000_000L

// A watch app older or newer than the phone app skips fields it doesn't know.
private val json = Json { ignoreUnknownKeys = true }
