package com.rkeelan.cairn.wear

import kotlin.math.sqrt

/**
 * How far right to move a row of the list so that its start follows the left edge of a round screen of [radius]: none for a row across the middle
 * of the screen, and more the further the row is above or below it. [fromCentre] is how far the row's middle is from the screen's, either way.
 */
fun curveOffset(fromCentre: Float, radius: Float): Float {
    val distance = minOf(kotlin.math.abs(fromCentre), radius)
    return radius - sqrt(radius * radius - distance * distance)
}
