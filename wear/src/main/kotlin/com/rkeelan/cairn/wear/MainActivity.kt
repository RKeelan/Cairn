package com.rkeelan.cairn.wear

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rkeelan.cairn.link.InteractionTimer
import com.rkeelan.cairn.link.Link
import com.rkeelan.cairn.wear.ui.WatchApp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    // Times taps from the touch to the frame that shows their result, and logs them under the tag Cairn (README, "Measure speed").
    private val interactions = InteractionTimer(
        commits = { action -> window.decorView.viewTreeObserver.registerFrameCommitCallback(action) },
        now = System::nanoTime,
        log = { Log.i(TAG, it) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tells the phone which watch app this is, so that it can offer to update an old one.
        lifecycleScope.launch {
            try {
                Link.publishWatchApp(this@MainActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not tell the phone the watch app's version", e)
            }
        }
        setContent { WatchApp(viewModel(factory = WatchViewModel.Factory), interactions) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = interactions.dispatching(ev.eventTimeNanos) { super.dispatchTouchEvent(ev) }

    private companion object {
        const val TAG = "Cairn"
    }
}
