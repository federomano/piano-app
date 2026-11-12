package com.federomano.pianoapp.score.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomTest {
    private val base = ScoreLayout.Fit(spPx = 40f, topPx = 220f)

    @Test
    fun normalSizeChangesNothing() {
        assertEquals(base, Zoom.apply(base, 1f, 1000f, 150f))
    }

    @Test
    fun shrinkingKeepsTheCenterOfTheStavesInPlace() {
        val z = Zoom.apply(base, 0.5f, 1000f, 150f)
        assertEquals(20f, z.spPx, 1e-4f)
        assertEquals(base.topPx + 7 * 40f, z.topPx + 7 * 20f, 1e-3f)
    }

    @Test
    fun maxZoomNeverGoesBelowOneNorAboveTheCap() {
        assertEquals(1f, Zoom.max(500f, 150f, 40f), 0f)
        assertEquals(Zoom.MAX, Zoom.max(5000f, 150f, 10f), 0f)
        val m = Zoom.max(1000f, 150f, 40f)
        assertTrue(m in 1f..Zoom.MAX)
    }

    @Test
    fun enlargedScoreStaysBetweenTheTopEdgeAndTheControls() {
        val heightPx = 1000f
        val controls = 150f
        val b = ScoreLayout.Fit(spPx = 30f, topPx = 200f)
        val m = Zoom.max(heightPx, controls, b.spPx)
        val z = Zoom.apply(b, m, heightPx, controls)
        assertTrue("arriba entran los números de compás", z.topPx >= 3.2f * z.spPx - 0.01f)
        assertTrue("abajo no lo tapan los controles", z.topPx + Geometry.SYSTEM_BOTTOM * z.spPx <= heightPx - controls + 0.01f)
    }

    @Test
    fun clampKeepsTheValueInRange() {
        assertEquals(0.5f, Zoom.clamp(0.1f, 1.3f), 0f)
        assertEquals(1.3f, Zoom.clamp(2f, 1.3f), 0f)
        assertEquals(0.8f, Zoom.clamp(0.8f, 1.3f), 0f)
    }
}
