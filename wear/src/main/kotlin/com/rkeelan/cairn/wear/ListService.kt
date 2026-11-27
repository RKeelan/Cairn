package com.rkeelan.cairn.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService as Tiles

/**
 * Redraws Cairn's tile when the list it shows changes: when the phone sends a new list, and when a tick is made on the watch or taken by the
 * phone. Android starts the service for such a change whether or not the app is open; the manifest names the paths.
 */
class ListService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.release()
        Tiles.getUpdater(this).requestUpdate(TileService::class.java)
    }
}
