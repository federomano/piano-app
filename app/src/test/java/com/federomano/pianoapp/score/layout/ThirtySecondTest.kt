package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Ticks
import com.federomano.pianoapp.score.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** PIA-62: fusas (60 ticks) con tres barras, banderitas y silencio propio. */
class ThirtySecondTest {
    private val score = ExampleScores.all.first { it.id == "fusas" }.create()

    @Test
    fun thirtySecondsAreStandardFigures() {
        assertTrue(Ticks.THIRTY_SECOND in Ticks.STANDARD)
        assertTrue(Ticks.dotted(Ticks.THIRTY_SECOND) in Ticks.STANDARD)
        assertEquals(60, Ticks.THIRTY_SECOND)
        assertEquals(Ticks.THIRTY_SECOND, NoteLayout.figureOf(60).base)
        assertEquals(1, NoteLayout.figureOf(90).dots)
        assertEquals(emptyList<String>(), score.validate())
    }

    @Test
    fun aRunOfEightIsDrawnWithThreeBeams() {
        val beams = ScoreLayout.build(score).body.filterIsInstance<PolygonOp>()
        val first = beams.minOf { it.minX }
        assertEquals("tres barras apiladas en la primera corrida", 3, beams.count { abs(it.minX - first) < 0.01f })
    }

    @Test
    fun singleOnesGetThreeFlagsAndTheRestHasItsOwnSymbol() {
        val glyphs = ScoreLayout.build(score).body.filterIsInstance<GlyphOp>().map { it.codepoint }
        assertTrue("banderita de fusa", glyphs.any { it == Smufl.FLAG_32ND_UP || it == Smufl.FLAG_32ND_DOWN })
        assertTrue("silencio de fusa", Smufl.REST_32ND in glyphs)
    }
}
