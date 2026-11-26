package com.rkeelan.cairn.phone

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.rkeelan.cairn.link.Link
import com.rkeelan.cairn.link.Tick
import kotlinx.coroutines.runBlocking

/**
 * Takes the ticks the watch sends. Android starts the service when one arrives, whether or not the app is open. Each tick goes into the phone's
 * copy, as a change made on the phone would, and its data item is then deleted, which tells the watch the tick has arrived.
 */
class TickService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val ticks = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(Link.TICKS) == true }
            .mapNotNull { event -> event.dataItem.data?.let { event.dataItem.uri to Tick.decode(it) } }
        events.release()
        if (ticks.isEmpty()) return
        val application = application as CairnApplication
        // The service's callbacks run on a thread of their own, which may block until the ticks are saved.
        runBlocking {
            application.repository.edit { _ -> ticks.fold(this) { list, (_, tick) -> list.with(tick) } }
            for ((uri, _) in ticks) {
                try {
                    Link.taken(this@TickService, uri)
                } catch (e: Exception) {
                    // The tick stays, to be taken again; taking it twice changes nothing.
                    Log.w(CairnApplication.TAG, "Could not delete a tick", e)
                }
            }
        }
        application.sync.request()
    }
}
