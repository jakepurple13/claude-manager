package com.programmersbox.claudemanager.ui.timeline

import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineZoomTest {
    private val m15 = 15L * 60_000
    private val m30 = 30L * 60_000
    private val h2 = 120L * 60_000

    @Test
    fun progressIsMeasuredInLogSpace() {
        // Halfway in log space between 15m and 60m is 30m.
        assertEquals(0.5f, logProgress(m15, 4 * m15, ln(m30.toFloat())), 1e-4f)
        assertEquals(0f, logProgress(m15, h2, ln(m15.toFloat())), 1e-4f)
        assertEquals(1f, logProgress(m15, h2, ln(h2.toFloat())), 1e-4f)
    }

    @Test
    fun progressIsClampedAndSettledWhenWindowUnchanged() {
        assertEquals(1f, logProgress(m30, m30, 0f))
        assertEquals(0f, logProgress(m15, h2, ln(m15.toFloat()) - 1f))
    }

    @Test
    fun gridStepsCrossfadeWhenTheyDiffer() {
        assertEquals(listOf(GridLayer(5, 0.75f), GridLayer(30, 0.25f)), gridLayers(m15, h2, 0.25f))
        assertEquals(listOf(GridLayer(10, 1f)), gridLayers(m30, m30, 1f))
    }
}
