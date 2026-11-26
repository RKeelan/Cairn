package com.rkeelan.cairn.link

import java.util.Locale

/** Times a tap, such as ticking an item off, from the touch that caused it to the frame that shows its result. */
fun interface Interactions {
    /**
     * Called from a touch's handler, such as a click, that changes what is shown, before or after it changes the state. [name] is a word or two:
     * "tick". The change must reach the state before the next frame is drawn, as a change the view model makes on the main thread does, since the
     * time ends at that frame. Only a touch in the activity's own window, handled while it is dispatched, is timed. Anything else, such as a key, a
     * click in a dialog or a menu, which are windows of their own, or a long press, which fires while the finger is still down, is logged as
     * untimed.
     */
    fun start(name: String)
}

/** Runs actions once a frame has been committed: Android has submitted it for display. */
fun interface FrameCommits {
    /** Runs [action] on the main thread once the next frame drawn has been committed. */
    fun afterCommit(action: () -> Unit)
}

/**
 * Times interactions from the touch event that caused them to the commit of the next frame, the frame that shows the change, and logs each as
 * `interaction <name> <ms> ms`. An app's activity dispatches each touch event through [dispatching], and Compose handles a click while the touch
 * that ends it, the finger lifting, is being dispatched, so [start] then knows which touch it is timing. An interaction started outside a touch's
 * dispatch is logged as `interaction <name> untimed`.
 */
class InteractionTimer(
    /** The window's frames, whose commits end the timings. */
    private val commits: FrameCommits,
    /** The time in nanoseconds on input events' clock, which on Android is `System.nanoTime`'s. */
    private val now: () -> Long,
    private val log: (String) -> Unit,
) : Interactions {
    /** When the touch being dispatched happened, on [now]'s clock; 0 between touches. */
    private var touchNanos = 0L

    /** Dispatches the touch event that happened at [eventNanos] with [dispatch], so that an interaction started meanwhile is timed from it. */
    fun dispatching(eventNanos: Long, dispatch: () -> Boolean): Boolean {
        touchNanos = eventNanos
        try {
            return dispatch()
        } finally {
            touchNanos = 0L
        }
    }

    override fun start(name: String) {
        val from = touchNanos
        if (from == 0L) {
            log("interaction $name untimed: not started by a touch being dispatched")
            return
        }
        commits.afterCommit { log("interaction $name ${formatMillis(now() - from)}") }
    }
}

/** [nanos] in milliseconds to one decimal place: "12.3 ms". */
internal fun formatMillis(nanos: Long): String = String.format(Locale.ROOT, "%.1f ms", nanos / 1_000_000.0)
