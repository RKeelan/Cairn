package com.rkeelan.cairn.baselineprofile.wear

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
 * Records what the watch app runs as it starts, scrolls the list, and ticks an item off and back on. The watch must already hold a list from the
 * phone of more items than fill the screen, so that the list scrolls.
 */
@RunWith(AndroidJUnit4::class)
class WatchProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = InstrumentationRegistry.getArguments().getString("targetAppId")!!, includeInStartupProfile = true) {
        device.wakeUp()
        pressHome()
        startActivityAndWait()
        val list = checkNotNull(device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS)) { "The list did not show: has the phone sent one?" }
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        list.fling(Direction.UP)
        device.waitForIdle()

        // Ticks the first item off and back on, leaving the list as it was. A ticked row stays where it was until the list is next shown.
        val row = checkNotNull(list.findObject(By.clickable(true).hasDescendant(By.textStartsWith("")))) { "The list has no items" }
        val name = row.findObject(By.textStartsWith("")).text
        row.click()
        device.waitForIdle()
        checkNotNull(list.scrollUntil(Direction.DOWN, Until.findObject(By.text(name)))).click()
        device.waitForIdle()
    }
}
