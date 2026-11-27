package com.rkeelan.cairn.phone

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rkeelan.cairn.link.WatchApp

/**
 * Offers to update the watch app on a paired watch when it is older than this phone app. Play's in-app update API does not work on Wear OS, and
 * nothing lets the phone install on the watch, so [update] has [open] show Cairn's page in the Play Store on the watch, where one tap on Update
 * installs the newer version.
 *
 * Only a watch app that Play installed is offered an update, since Play does not know a copy installed with adb, nor the dev flavour.
 */
class WatchUpdates(private val phoneVersionCode: Long, private val open: (node: String) -> Unit) {
    /** Opens the Play Store on a watch whose watch app is out of date, or null if none is. */
    var update: (() -> Unit)? by mutableStateOf(null)
        private set

    /** Takes what the watches say of their watch apps, keyed by each watch's node ID. */
    fun seen(apps: Map<String, WatchApp>) {
        val node = apps.entries.firstOrNull { (_, app) -> app.fromPlay && app.isOlderThan(phoneVersionCode) }?.key
        update = node?.let { { open(it) } }
    }
}
