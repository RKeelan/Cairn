package com.rkeelan.cairn.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionTimerTest {
    private val frames = ArrayList<() -> Unit>()
    private val logged = ArrayList<String>()
    private var clockNanos = 0L
    private val timer = InteractionTimer(commits = { frames += it }, now = { clockNanos }, log = { logged += it })

    @Test
    fun aClickWhileItsTouchIsDispatchedIsTimedFromTheTouchToTheNextFramesCommit() {
        clockNanos = 1_004_000_000
        timer.dispatching(eventNanos = 1_000_000_000) {
            timer.start("tick")
            true
        }
        assertTrue("nothing is logged until the frame showing the change is committed", logged.isEmpty())

        clockNanos = 1_012_300_000
        frames.single().invoke()
        assertEquals(listOf("interaction tick 12.3 ms"), logged)
    }

    @Test
    fun anInteractionStartedOutsideATouchsDispatchIsLoggedAsUntimed() {
        timer.dispatching(eventNanos = 1_000_000_000) { true }
        timer.start("save")

        assertEquals(listOf("interaction save untimed: not started by a touch being dispatched"), logged)
        assertTrue(frames.isEmpty())
    }

    @Test
    fun dispatchingReturnsWhatTheDispatchReturnsAndForgetsTheTouchEvenWhenTheDispatchThrows() {
        assertFalse(timer.dispatching(eventNanos = 1_000_000_000) { false })
        assertThrows(IllegalStateException::class.java) { timer.dispatching(eventNanos = 2_000_000_000) { error("dispatch failed") } }

        timer.start("add")
        assertEquals(listOf("interaction add untimed: not started by a touch being dispatched"), logged)
    }
}
