package com.rkeelan.cairn.phone

import android.os.Looper
import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.rkeelan.cairn.link.WatchApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Offering a newer phone app, with Play's fake in Play's place, and a newer watch app. */
@RunWith(RobolectricTestRunner::class)
class UpdatesTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private fun fake() = FakeAppUpdateManager(activity)

    private fun check(updates: AppUpdates) {
        updates.check()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a newer phone app is offered, and only a tap starts the update`() {
        val fake = fake().apply { setUpdateAvailable(2, AppUpdateType.IMMEDIATE) }
        val updates = AppUpdates(activity, fake, fromPlay = true)
        check(updates)
        assertNotNull(updates.install)
        assertFalse(fake.isImmediateFlowVisible)
        updates.install!!()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.isImmediateFlowVisible)
    }

    @Test
    fun `nothing is offered without a newer phone app`() {
        val updates = AppUpdates(activity, fake().apply { setUpdateNotAvailable() }, fromPlay = true)
        check(updates)
        assertNull(updates.install)
    }

    @Test
    fun `a phone app Play did not install never asks`() {
        val updates = AppUpdates(activity, fake().apply { setUpdateAvailable(2, AppUpdateType.IMMEDIATE) }, fromPlay = false)
        check(updates)
        assertNull(updates.install)
    }

    @Test
    fun `the dev flavour fakes an update when asked to, and the prod flavour never does`() {
        val faked = AppUpdates.faked(activity)
        check(faked)
        assertNotNull(faked.install)
        assertTrue(fakesUpdate(true, flavour = "dev"))
        assertFalse(fakesUpdate(false, flavour = "dev"))
        assertFalse(fakesUpdate(true, flavour = "prod"))
    }

    @Test
    fun `Play's answers become what to do`() {
        assertEquals(UpdateAction.Offer, updateAction(UpdateAvailability.UPDATE_AVAILABLE, immediateAllowed = true))
        assertEquals(UpdateAction.None, updateAction(UpdateAvailability.UPDATE_AVAILABLE, immediateAllowed = false))
        assertEquals(UpdateAction.Resume, updateAction(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS, immediateAllowed = false))
        assertEquals(UpdateAction.None, updateAction(UpdateAvailability.UPDATE_NOT_AVAILABLE, immediateAllowed = true))
        assertEquals(UpdateAction.None, updateAction(UpdateAvailability.UNKNOWN, immediateAllowed = true))
    }

    @Test
    fun `an older watch app that Play installed is offered an update on its own watch`() {
        val opened = mutableListOf<String>()
        val watches = WatchUpdates(phoneVersionCode = 1_001) { opened += it }
        watches.seen(mapOf("current" to WatchApp(1_000_001_001, fromPlay = true), "old" to WatchApp(1_000_001_000, fromPlay = true)))
        watches.update!!()
        assertEquals(listOf("old"), opened)
    }

    @Test
    fun `a current watch app, or one Play did not install, is not offered an update`() {
        val watches = WatchUpdates(phoneVersionCode = 1_001) { }
        watches.seen(mapOf("current" to WatchApp(1_000_001_001, fromPlay = true)))
        assertNull(watches.update)
        watches.seen(mapOf("adb" to WatchApp(1_000_001_000, fromPlay = false)))
        assertNull(watches.update)
        watches.seen(mapOf("old" to WatchApp(1_000_001_000, fromPlay = true)))
        assertNotNull(watches.update)
        watches.seen(emptyMap())
        assertNull("once the watch has updated, or gone", watches.update)
    }
}
