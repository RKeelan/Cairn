package com.rkeelan.cairn.link

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The link between the phone app and the watch app, over the Wearable Data Layer. The phone publishes the list as one data item, which the watch
 * reads; the watch publishes each tick as a data item of its own, which the phone takes and then deletes. Data items wait while the two are apart,
 * so a tick made out of the phone's range reaches it later.
 */
object Link {
    /** The path of the data item that holds the list. */
    const val LIST = "/list"

    /** The start of the path of each tick's data item. */
    const val TICKS = "/ticks/"

    fun tickPath(key: String): String = TICKS + Uri.encode(key)

    private fun client(context: Context): DataClient = Wearable.getDataClient(context)

    /** On the phone: makes [list] the list the watch shows, or, when it is null, tells the watch there is none. */
    suspend fun publish(context: Context, list: WatchList?) {
        val client = client(context)
        if (list == null) {
            client.deleteDataItems(Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(LIST).build()).await()
        } else {
            client.putDataItem(PutDataRequest.create(LIST).setData(list.encode()).setUrgent()).await()
        }
    }

    /** On the phone: deletes a tick's data item once the tick has been taken, so the watch stops showing it as on its way. */
    suspend fun taken(context: Context, tick: Uri) {
        client(context).deleteDataItems(tick).await()
    }

    /** On the watch: sends [tick] to the phone. */
    suspend fun sendTick(context: Context, tick: Tick) {
        client(context).putDataItem(PutDataRequest.create(tickPath(tick.key)).setData(tick.encode()).setUrgent()).await()
    }

    /**
     * On the watch: the list as the phone last sent it, with the ticks still on their way laid over it. It is null until the phone has sent a list,
     * and after it has said there is none.
     */
    fun list(context: Context): Flow<WatchList?> = callbackFlow {
        val client = client(context)
        suspend fun read() {
            var list: WatchList? = null
            val ticks = mutableListOf<Tick>()
            val items = client.dataItems.await()
            try {
                for (item in items) {
                    val path = item.uri.path ?: continue
                    val data = item.data ?: continue
                    when {
                        path == LIST -> list = WatchList.decode(data)
                        path.startsWith(TICKS) -> ticks += Tick.decode(data)
                    }
                }
            } finally {
                items.release()
            }
            send(list?.with(ticks))
        }
        val listener = DataClient.OnDataChangedListener { events ->
            val relevant = events.any { relevant(it) }
            events.release()
            if (relevant) launch { read() }
        }
        client.addListener(listener).await()
        read()
        awaitClose { client.removeListener(listener) }
    }

    private fun relevant(event: DataEvent): Boolean = event.dataItem.isLinkItem()

    private fun DataItem.isLinkItem(): Boolean = uri.path?.let { it == LIST || it.startsWith(TICKS) } == true
}
