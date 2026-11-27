package com.rkeelan.cairn.phone

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.common.api.ApiException
import com.rkeelan.cairn.link.InteractionTimer
import com.rkeelan.cairn.link.Link
import com.rkeelan.cairn.link.WatchApp
import com.rkeelan.cairn.phone.ui.LocalInteractions
import com.rkeelan.cairn.phone.ui.LocalUpdates
import com.rkeelan.cairn.phone.ui.PhoneApp
import com.rkeelan.cairn.phone.ui.Updates
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Times taps from the touch to the frame that shows their result, and logs them under the tag Cairn (README, "Measure speed").
    private val interactions = InteractionTimer(
        commits = { action -> window.decorView.viewTreeObserver.registerFrameCommitCallback(action) },
        now = System::nanoTime,
        log = { Log.i(CairnApplication.TAG, it) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as CairnApplication
        val faked = fakesUpdate(intent.getBooleanExtra("fakeUpdate", false))
        val updates = if (faked) AppUpdates.faked(this) else AppUpdates(this)
        val watchUpdates = WatchUpdates(BuildConfig.VERSION_CODE.toLong()) { node ->
            Link.openStorePageOnWatch(this, node, PLAY_ID) { Log.w(CairnApplication.TAG, "update: could not open the Play Store on the watch", it) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updates.check()
                // The list follows the server for as long as it is on screen.
                launch { application.sync.whileShowing() }
                if (faked) {
                    watchUpdates.seen(mapOf("fake" to WatchApp(versionCode = 0, fromPlay = true)))
                } else {
                    Link.watchApps(this@MainActivity)
                        // As on a phone without Google Play services, which can have no watch.
                        .catch { if (it is ApiException) Log.w(CairnApplication.TAG, "Could not ask the watch for its version", it) else throw it }
                        .onEach { apps -> apps.forEach { (node, app) -> Log.i(CairnApplication.TAG, "update: watch $node has $app") } }
                        .collect(watchUpdates::seen)
                }
            }
        }
        setContent {
            CompositionLocalProvider(
                LocalInteractions provides interactions,
                LocalUpdates provides Updates(phone = updates.install, watch = watchUpdates.update),
            ) {
                PhoneApp(viewModel(factory = CairnViewModel.Factory), application.signIn)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = interactions.dispatching(ev.eventTimeNanos) { super.dispatchTouchEvent(ev) }

    private companion object {
        /** The ID Play lists Cairn under, which the dev flavour's adds `.dev` to. */
        const val PLAY_ID = "com.rkeelan.cairn"
    }
}
