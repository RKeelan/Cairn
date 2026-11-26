package com.rkeelan.cairn.phone

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.common.api.ApiException
import com.rkeelan.cairn.core.Account
import com.rkeelan.cairn.core.ListRepository
import com.rkeelan.cairn.core.Problem
import com.rkeelan.cairn.core.Server
import com.rkeelan.cairn.core.SupabaseServer
import com.rkeelan.cairn.core.Sync
import com.rkeelan.cairn.link.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** How this build's accounts sign in: with Google, through [googleClientId], and in the dev flavour as one of [devAccounts] with [devPassword]. */
class SignInOptions(val googleClientId: String, val devAccounts: List<String>, val devPassword: String)

/** The app's one copy of everything that outlives a screen. */
class CairnApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        feedTheWatch()
    }

    /**
     * Sends the watch the list whenever it or the order changes, for as long as the app runs, and tells the watch there is none while nobody is
     * signed in. A phone without a watch takes the list all the same, and passes it on if a watch is paired later.
     */
    @OptIn(FlowPreview::class)
    private fun feedTheWatch() {
        scope.launch {
            combine(server.account, repository.listId, repository.list, settings.settings) { account, listId, list, settings ->
                if (account is Account.SignedIn && listId != null) watchList(list, settings) else null
            }
                .distinctUntilChanged()
                // A burst of changes, as when a sync brings many, goes as one.
                .debounce(FEED_AFTER)
                .collect { watchList ->
                    try {
                        Link.publish(this@CairnApplication, watchList)
                    } catch (e: ApiException) {
                        // As on a phone without Google Play services, which can have no watch.
                        Log.w(TAG, "Could not send the watch the list", e)
                    }
                }
        }
    }

    val signIn = SignInOptions(
        googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
        devAccounts = BuildConfig.DEV_ACCOUNTS.split(',').filter(String::isNotBlank),
        devPassword = BuildConfig.DEV_PASSWORD,
    )

    val server: Server by lazy { SupabaseServer(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) }

    val repository: ListRepository by lazy {
        val database = Room.databaseBuilder(this, ListDatabase::class.java, "list.db").build()
        ListRepository(RoomListStore(database.lists()), server, onFailure = { Log.w(TAG, "Sync failed", it) })
    }

    val settings: SettingsStore by lazy { SettingsStore(getSharedPreferences("settings", MODE_PRIVATE)) }

    val sync: Sync by lazy { Sync(repository, server, scope, whenOffline = ::syncWhenConnected) }

    /** Has WorkManager run a sync once the device has a connection, even if the app has been closed by then. */
    private fun syncWhenConnected() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork("sync", ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        /** The tag the app logs under: `adb logcat -s Cairn`. */
        const val TAG = "Cairn"

        private val FEED_AFTER = 500.milliseconds
    }
}

/** Sends what the device changed while it had no connection. WorkManager tries again later if there is still none. */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as CairnApplication).repository
        return if (repository.sync() || repository.problem.value != Problem.OFFLINE) Result.success() else Result.retry()
    }
}
