package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.DemoScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingTest {
    private val layout = ScoreLayout.build(DemoScore.create())

    @Test
    fun shortFiguresNeverGetCloserThanTheirHeadsPlusAMargin() {
        // Compás 3 de la demo: semicorcheas seguidas y un silencio de semicorchea al final.
        val offsets = layout.measures[2].anchors!!.offsets
        offsets.zipWithNext().forEach { (a, b) -> assertTrue("gap ${b - a}", b - a >= Smufl.REST_8TH_WIDTH + 0.4f - 0.001f) }
    }

    @Test
    fun lastElementKeepsAGapBeforeTheBarline() {
        val m = layout.measures[2]
        val a = m.anchors!!
        assertTrue(a.offsets.last() - a.offsets[a.offsets.size - 2] >= 1.0f + Smufl.REST_16TH_WIDTH - 0.001f)
    }

    @Test
    fun slowMeasuresKeepTheirProportionalWidth() {
        // Compases 1 y 2 de la demo: negras y blancas, no hace falta agrandar nada.
        for (i in 0..1) {
            val m = layout.measures[i]
            assertEquals(4 * Geometry.QUARTER_WIDTH, m.x1 - m.contentX, 0.001f)
        }
    }

    @Test
    fun positionsKeepTheOrderOfTime() {
        val ticks = (0..layout.measures.last().measure.endTick step 120).toList()
        val xs = ticks.map { layout.xOfTick(it) }
        xs.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }
}

class PlayheadMappingTest {
    private val layout = ScoreLayout.build(DemoScore.create())

    @Test
    fun tickAtXIsTheInverseOfXOfTick() {
        for (tick in 0..layout.endTick step 60) {
            assertEquals(tick.toFloat(), layout.tickAtX(layout.xOfTick(tick)), 0.05f)
        }
    }

    @Test
    fun scrollPutsTheStartAndTheEndExactlyOnThePlayhead() {
        val playhead = 16f
        assertEquals(playhead, layout.xOfTick(0) + layout.headerWidth - layout.scrollForTick(0f, playhead), 0.01f)
        val end = layout.endTick.toFloat()
        assertEquals(playhead, layout.xOfTick(end) + layout.headerWidth - layout.scrollForTick(end, playhead), 0.01f)
    }

    @Test
    fun scrollAndTickAreInverse() {
        val playhead = 16f
        for (tick in listOf(0f, 500f, 3840f + 240f, 8000f, 15000f)) {
            assertEquals(tick, layout.tickAtScroll(layout.scrollForTick(tick, playhead), playhead), 0.05f)
        }
    }

    @Test
    fun scrollNeverGoesBackwardsAsTimeAdvances() {
        var previous = layout.scrollForTick(0f, 16f)
        for (tick in 0..layout.endTick step 30) {
            val scroll = layout.scrollForTick(tick.toFloat(), 16f)
            assertTrue(scroll >= previous)
            previous = scroll
        }
    }
}

class MeasureBoundaryTest {
    private val layout = ScoreLayout.build(DemoScore.create())

    @Test
    fun noJumpWhenOneMeasureEndsAndTheNextBegins() {
        // Justo antes del fin de un compás y justo en el comienzo del siguiente, la posición casi no cambia
        // (también donde hay cambio de armadura y de compás: compases 4, 7 y 10 de la demo).
        for (i in 1 until layout.measures.size) {
            val start = layout.measures[i].measure.startTick
            val before = layout.xOfTick(start - 1f)
            val after = layout.xOfTick(start.toFloat())
            assertTrue("compás ${i + 1}: salto de ${after - before} sp", after - before < 0.1f)
        }
    }

    @Test
    fun theBarlineFallsInsideTheTimeOfTheLastNote() {
        for (i in 0 until layout.measures.size - 1) {
            val m = layout.measures[i]
            val end = m.measure.endTick
            val barline = m.x1
            // La barra está entre la última nota y la primera del compás siguiente.
            assertTrue(layout.xOfTick(end - 1f) < layout.measures[i + 1].contentX)
            assertTrue(barline < layout.measures[i + 1].contentX)
            // Y la posición del medio del tramo cae pasada la barra o antes, pero siempre creciendo.
            assertTrue(layout.xOfTick(end - 60f) <= layout.xOfTick(end - 1f))
        }
    }

    @Test
    fun theLastMeasureStillEndsAtItsBarline() {
        val last = layout.measures.last()
        assertEquals(last.x1, layout.xOfTick(last.measure.endTick), 0.001f)
    }
}

class SmoothPathTest {
    private val score = DemoScore.create()
    private val tempo = com.federomano.pianoapp.score.TempoMap(score.tempos)

    private fun peakSpeed(layout: ScoreLayout): Float {
        var peak = 0f
        var t = 0
        while (t < layout.endTick - 30) {
            val v = (layout.scrollForTick(t + 30f, 16f) - layout.scrollForTick(t.toFloat(), 16f)) / tempo.secondsBetween(t.toFloat(), t + 30f)
            peak = maxOf(peak, v)
            t += 30
        }
        return peak
    }

    @Test
    fun smoothingLowersTheBurstsOfSpeedWhenCrossingASignatureChange() {
        val exact = ScoreLayout.build(score, smoothingSeconds = 0f)
        val smooth = ScoreLayout.build(score)
        assertTrue(peakSpeed(smooth) < peakSpeed(exact) * 0.7f)
    }

    @Test
    fun notesStayCloseToThePlayheadAtTheirTime() {
        val layout = ScoreLayout.build(score)
        for (tick in layout.measures.flatMap { it.anchors!!.noteTicks }) {
            val onPlayhead = layout.scrollForTick(tick.toFloat(), 16f) + 16f - layout.headerWidth
            assertTrue("tick $tick a ${kotlin.math.abs(onPlayhead - layout.xOfTick(tick))} sp", kotlin.math.abs(onPlayhead - layout.xOfTick(tick)) < 3f)
        }
    }
}
