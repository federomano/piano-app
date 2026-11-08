package com.federomano.pianoapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InertiaTest {
    private fun simulate(start: Float, target: Float, seconds: Float = 8f): List<Float> {
        val out = mutableListOf(start)
        var v = start
        repeat((seconds * 60).toInt()) {
            v = Inertia.step(v, target, 1f / 60f, FLING_RATE)
            out += v
        }
        return out
    }

    @Test
    fun startsAtTheReleaseSpeedAndBrakesSmoothlyToZeroWhenPaused() {
        val v = simulate(70f, 0f)
        assertEquals(70f, v.first(), 0.001f)
        assertTrue("siempre frena", v.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("al final casi quieta", v.last() < 0.4f)
        // El frenado es largo: al segundo todavía se mueve bien, y no dura más de unos tres segundos.
        assertTrue(v[60] > 3f)
        assertTrue(v[180] < 0.4f)
    }

    @Test
    fun aSlowReleaseStartsSlow() {
        val slow = simulate(4f, 0f)
        val fast = simulate(70f, 0f)
        assertTrue(slow[10] < fast[10] / 10)
    }

    @Test
    fun comingBackwardWhilePlayingPassesThroughZeroAndEndsAtThePlaybackSpeed() {
        val v = simulate(-60f, 7f)
        val crossing = v.indexOfFirst { it >= 0f }
        assertTrue("cruza el cero", crossing > 0)
        assertTrue("antes del cruce siempre es negativa y sube", v.take(crossing).all { it < 0f })
        assertTrue("después nunca vuelve a ser negativa", v.drop(crossing).all { it >= 0f })
        assertEquals(7f, v.last(), 0.1f)
        assertTrue("nunca se pasa de la velocidad de reproducción", v.drop(crossing).all { it <= 7.001f })
    }

    @Test
    fun fastForwardWhilePlayingBrakesDownToThePlaybackSpeedNotToZero() {
        val v = simulate(70f, 7f)
        assertTrue(v.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(7f, v.last(), 0.1f)
    }

    @Test
    fun soundsOnlyWhenGoingForwardAtAboutNormalSpeed() {
        assertTrue(Inertia.soundsAt(7f, 7f))
        assertTrue(Inertia.soundsAt(9f, 7f))
        assertTrue(!Inertia.soundsAt(30f, 7f))
        assertTrue(!Inertia.soundsAt(2f, 7f))
        assertTrue(!Inertia.soundsAt(-20f, 7f))
        assertTrue(!Inertia.soundsAt(0f, 0f))
        assertTrue(!Inertia.soundsAt(5f, 0f))
    }

    @Test
    fun comingBackFromARewindStartsSoundingBeforeTheBrakingEnds() {
        val v = simulate(-60f, 7f)
        val sounding = v.indexOfFirst { Inertia.soundsAt(it, 7f) }
        val done = v.indexOfFirst { kotlin.math.abs(it - 7f) < 0.4f }
        assertTrue(sounding in 1 until done)
    }
}
