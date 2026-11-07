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
