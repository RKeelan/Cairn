package com.rkeelan.cairn.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class CurveTest {
    @Test
    fun aRowAcrossTheMiddleStaysPut() {
        assertEquals(0f, curveOffset(0f, radius = 200f), 0.001f)
    }

    @Test
    fun rowsAboveAndBelowFollowTheEdge() {
        // 3-4-5: a row 120 from the middle of a screen of radius 200 meets the edge 160 from the middle, so 40 in from the left.
        assertEquals(40f, curveOffset(120f, radius = 200f), 0.001f)
        assertEquals(40f, curveOffset(-120f, radius = 200f), 0.001f)
    }

    @Test
    fun aRowOffTheScreenGoesNoFurtherThanTheMiddle() {
        assertEquals(200f, curveOffset(500f, radius = 200f), 0.001f)
    }
}
