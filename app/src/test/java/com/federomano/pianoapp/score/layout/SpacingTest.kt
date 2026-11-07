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
    fun scrollPutsEveryNoteExactlyOnThePlayheadAtItsTime() {
        val playhead = 16f
        // En el instante en que empieza cada nota o silencio, la nota está justo en la línea.
        val onsets = layout.measures.flatMap { it.anchors!!.ticks.dropLast(1) }
        for (tick in onsets) {
            val scroll = layout.scrollForTick(tick.toFloat(), playhead)
            assertEquals(playhead, layout.xOfTick(tick) + layout.headerWidth - scroll, 0.002f)
        }
    }

    @Test
    fun scrollAndTickAreInverse() {
        val playhead = 16f
        for (tick in listOf(0f, 500f, 3840f + 240f, 8000f, 15000f)) {
            assertEquals(tick, layout.tickAtScroll(layout.scrollForTick(tick, playhead), playhead), 0.05f)
        }
    }

    @Test
    fun theSmoothCurveStaysCloseToTheStraightOneAndChangesSpeedGently() {
        val playhead = 16f
        var worstDistance = 0f
        var smoothJump = 0f
        var straightJump = 0f
        var previousSmooth = 0f
        var previousStraight = 0f
        var first = true
        for (tick in 0 until layout.endTick - 10 step 10) {
            val smooth = layout.scrollForTick(tick + 10f, playhead) - layout.scrollForTick(tick.toFloat(), playhead)
            val straight = layout.xOfTick(tick + 10f) - layout.xOfTick(tick.toFloat())
            worstDistance = maxOf(worstDistance, kotlin.math.abs(layout.scrollForTick(tick.toFloat(), playhead) + playhead - layout.headerWidth - layout.xOfTick(tick)))
            if (!first) {
                smoothJump = maxOf(smoothJump, kotlin.math.abs(smooth - previousSmooth))
                straightJump = maxOf(straightJump, kotlin.math.abs(straight - previousStraight))
            }
            first = false
            previousSmooth = smooth
            previousStraight = straight
        }
        assertTrue("se aleja $worstDistance sp de la recta", worstDistance < 2.0f)
        assertTrue("salto de velocidad $smoothJump contra $straightJump", smoothJump < straightJump * 0.6f)
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
