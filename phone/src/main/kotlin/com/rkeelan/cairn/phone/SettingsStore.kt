package com.rkeelan.cairn.phone

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rkeelan.cairn.core.Order
import com.rkeelan.cairn.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the list is shown, kept in [preferences]. The watch follows these settings too. */
class SettingsStore(private val preferences: SharedPreferences) {
    private val current = MutableStateFlow(Settings(order = Order.entries.firstOrNull { it.name == preferences.getString(ORDER, null) } ?: Settings().order))

    val settings: StateFlow<Settings> = current.asStateFlow()

    fun set(settings: Settings) {
        preferences.edit { putString(ORDER, settings.order.name) }
        current.value = settings
    }

    private companion object {
        const val ORDER = "order"
    }
}
