package com.rkeelan.cairn.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
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
import java.util.concurrent.ExecutionException

/**
 * The link between the phone app and the watch app, over the Wearable Data Layer. The phone publishes the list as one data item, which the watch
 * reads; the watch publishes each tick as a data item of its own, which the phone takes and then deletes. Data items wait while the two are apart,
 * so a tick made out of the phone's range reaches it later. The watch also publishes which watch app it has, so that the phone can offer to update
 * an old one.
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

    /** On the watch: the list as the phone last sent it, and the ticks still on their way, each time either changes. */
    fun list(context: Context): Flow<Received> = callbackFlow {
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
            send(Received(list, ticks))
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

    /** The path of the data item in which the watch says which watch app it has. */
    const val WATCH_APP = "/watch-app"

    /** On the watch: tells the phone which watch app is installed, this one. */
    suspend fun publishWatchApp(context: Context) {
        val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val app = WatchApp(versionCode, installedByPlay(context))
        client(context).putDataItem(PutDataRequest.create(WATCH_APP).setData(app.encode())).await()
    }

    /** On the phone: the watch app on each watch that has said, keyed by the watch's node ID. */
    fun watchApps(context: Context): Flow<Map<String, WatchApp>> = callbackFlow {
        val client = client(context)
        suspend fun read() {
            val apps = mutableMapOf<String, WatchApp>()
            val items = client.dataItems.await()
            try {
                for (item in items) {
                    val node = item.uri.host ?: continue
                    val data = item.data ?: continue
                    if (item.uri.path == WATCH_APP) apps[node] = WatchApp.decode(data)
                }
            } finally {
                items.release()
            }
            send(apps)
        }
        val listener = DataClient.OnDataChangedListener { events ->
            val relevant = events.any { it.dataItem.uri.path == WATCH_APP }
            events.release()
            if (relevant) launch { read() }
        }
        client.addListener(listener).await()
        read()
        awaitClose { client.removeListener(listener) }
    }

    /**
     * On the phone: opens the Play Store's page for the app [appId] on the watch [node], where Update installs a newer version. [onFailure] hears
     * if the watch could not be asked.
     */
    fun openStorePageOnWatch(context: Context, node: String, appId: String, onFailure: (Throwable) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$appId".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val result = RemoteActivityHelper(context).startRemoteActivity(intent, node)
        result.addListener(
            {
                try {
                    result.get()
                } catch (e: ExecutionException) {
                    onFailure(e.cause ?: e)
                } catch (e: Exception) {
                    onFailure(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Whether Google Play installed this copy of the app. */
    fun installedByPlay(context: Context): Boolean = runCatching {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName == PLAY_STORE
    }.getOrDefault(false)

    private const val PLAY_STORE = "com.android.vending"

    private fun relevant(event: DataEvent): Boolean = event.dataItem.isLinkItem()

    private fun DataItem.isLinkItem(): Boolean = uri.path?.let { it == LIST || it.startsWith(TICKS) } == true
}
