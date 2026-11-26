package com.rkeelan.cairn.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rkeelan.cairn.phone.ui.PhoneApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as CairnApplication
        lifecycleScope.launch {
            // The list follows the server for as long as it is on screen.
            repeatOnLifecycle(Lifecycle.State.STARTED) { application.sync.whileShowing() }
        }
        setContent { PhoneApp(viewModel(factory = CairnViewModel.Factory), application.signIn) }
    }
}
