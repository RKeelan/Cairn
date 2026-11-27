package com.rkeelan.cairn.baselineprofile.phone

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MS = 10_000L

/**
 * Records what the phone app runs as it starts, scrolls the list, and ticks an item off and back on. The app must be signed in to a list of more
 * items than fill the screen, so that the list scrolls.
 */
@RunWith(AndroidJUnit4::class)
class PhoneProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = InstrumentationRegistry.getArguments().getString("targetAppId")!!, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        val list = checkNotNull(device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS)) { "The list did not show: is the app signed in?" }
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        list.fling(Direction.UP)
        device.waitForIdle()

        // Ticks the first item off, finds it among the bought ones, and ticks it back on, leaving the list as it was.
        val row = checkNotNull(list.findObject(By.checkable(true))) { "The list has no items" }
        val name = row.findObject(By.textStartsWith("")).text
        row.click()
        device.waitForIdle()
        checkNotNull(list.scrollUntil(Direction.DOWN, Until.findObject(By.checkable(true).hasDescendant(By.text(name)).checked(true)))).click()
        device.waitForIdle()
        list.scrollUntil(Direction.UP, Until.findObject(By.text(name)))
        device.waitForIdle()
    }
}
