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
import com.rkeelan.cairn.link.InteractionTimer
import com.rkeelan.cairn.phone.ui.LocalInteractions
import com.rkeelan.cairn.phone.ui.PhoneApp
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
        lifecycleScope.launch {
            // The list follows the server for as long as it is on screen.
            repeatOnLifecycle(Lifecycle.State.STARTED) { application.sync.whileShowing() }
        }
        setContent {
            CompositionLocalProvider(LocalInteractions provides interactions) {
                PhoneApp(viewModel(factory = CairnViewModel.Factory), application.signIn)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = interactions.dispatching(ev.eventTimeNanos) { super.dispatchTouchEvent(ev) }
}
