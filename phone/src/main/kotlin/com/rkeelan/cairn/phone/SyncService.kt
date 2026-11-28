package com.rkeelan.cairn.phone

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.rkeelan.cairn.link.Link

/**
 * Syncs when the watch asks, as it does each time its app opens. Android starts the service when the message arrives, whether or not the app is
 * open. A sync that changes the list sends the watch the new one, as any change does.
 */
class SyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == Link.SYNC) (application as CairnApplication).sync.request()
    }
}
