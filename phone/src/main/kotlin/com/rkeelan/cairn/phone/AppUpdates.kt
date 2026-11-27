package com.rkeelan.cairn.phone

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.rkeelan.cairn.link.Link

/**
 * Offers the newer version of the phone app that Google Play has, through Play's in-app update API, when Play installed this copy. [check] asks
 * Play, and is called each time [activity] starts. While Play has a newer version, [install] starts Play's full-screen update, which downloads
 * and installs it and restarts the app. An update already under way, as when the app was killed during one, is resumed as soon as a check finds
 * it.
 *
 * A copy installed any other way, as with adb, and the dev flavour, which Play does not know, never ask Play, and so are never offered an
 * update. Failures are logged and otherwise ignored.
 */
class AppUpdates(
    private val activity: ComponentActivity,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
    private val fromPlay: Boolean = Link.installedByPlay(activity),
) {
    // Whether a request to Play is under way. Main thread only.
    private var asking = false

    /** Starts installing the newer version Play has, or null if it has none, or this copy did not come from Play. */
    var install: (() -> Unit)? by mutableStateOf(null)
        private set

    /** Asks Play whether it has a newer version, unless a request is already under way. Main thread only. */
    fun check() {
        if (!fromPlay || asking) return
        ask { info, action -> if (action == UpdateAction.Resume) startFlow(info) }
    }

    // Each AppUpdateInfo starts at most one update, so a tap asks Play again for a fresh one.
    private val start: () -> Unit = {
        if (fromPlay && !asking) ask { info, action -> if (action != UpdateAction.None) startFlow(info) }
    }

    /** Asks Play about an update, offers it if there is one, and hands [then] what Play said and what to do about it. */
    private fun ask(then: (AppUpdateInfo, UpdateAction) -> Unit) {
        asking = true
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                asking = false
                val action = updateAction(info.updateAvailability(), info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
                if (action != UpdateAction.None) Log.i(CairnApplication.TAG, "update: version ${info.availableVersionCode()} $action")
                install = if (action == UpdateAction.None) null else start
                then(info, action)
            }
            .addOnFailureListener { e ->
                asking = false
                Log.w(CairnApplication.TAG, "update: asking Play failed", e)
            }
    }

    private fun startFlow(info: AppUpdateInfo) {
        // Play's update is started over the activity, so only while it is in front of the user. Its result is not needed: a cancelled update
        // stays on offer, and one that succeeds restarts the app.
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        try {
            manager.startUpdateFlowForResult(info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE), UPDATE_REQUEST)
        } catch (e: Exception) {
            Log.w(CairnApplication.TAG, "update: starting failed", e)
        }
    }

    companion object {
        /**
         * An update that Play's fake says is waiting, as if Play had installed this copy and had a newer version, so that the dev flavour can
         * show Update (see [fakesUpdate]). Tapping it starts nothing.
         */
        fun faked(activity: ComponentActivity): AppUpdates {
            val fake = FakeAppUpdateManager(activity).apply { setUpdateAvailable(BuildConfig.VERSION_CODE + 1, AppUpdateType.IMMEDIATE) }
            return AppUpdates(activity, fake, fromPlay = true)
        }
    }
}

/** What to do about what Play says of an update. */
internal enum class UpdateAction {
    /** Nothing: there is no newer version, or it cannot be installed in the foreground. */
    None,

    /** Offer the newer version. */
    Offer,

    /** Resume the update already under way. */
    Resume,
}

/**
 * What to do given Play's [availability], one of [UpdateAvailability]'s values, and whether Play allows an immediate update, [immediateAllowed].
 * An immediate update that was started and not finished is resumed, as Play's guidance asks.
 */
internal fun updateAction(availability: Int, immediateAllowed: Boolean): UpdateAction = when (availability) {
    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> UpdateAction.Resume
    UpdateAvailability.UPDATE_AVAILABLE -> if (immediateAllowed) UpdateAction.Offer else UpdateAction.None
    else -> UpdateAction.None
}

/** The request code Play's update is started with, whose result nothing reads. */
private const val UPDATE_REQUEST = 0xCA1

/**
 * Whether the activity was started with the `fakeUpdate` extra, which only the dev flavour reads, so that the list shows Update and Update watch,
 * as it does when Play has newer versions of copies it installed:
 * `adb shell am start -n com.rkeelan.cairn.dev/com.rkeelan.cairn.phone.MainActivity --ez fakeUpdate true`.
 */
fun fakesUpdate(extra: Boolean, flavour: String = BuildConfig.FLAVOR): Boolean = flavour == "dev" && extra
