package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-22: cada cosa dibujada se asigna a la mano correcta. */
class HandBandsTest {
    @Test
    fun noteHeadsFollowTheirStaff() {
        val score = ExampleScores.all.first { it.id == "escala" }.create()
        val layout = ScoreLayout.build(score)
        val heads = layout.marks.filterIsInstance<GlyphOp>().filter { it.codepoint == Smufl.NOTEHEAD_BLACK || it.codepoint == Smufl.NOTEHEAD_HALF || it.codepoint == Smufl.NOTEHEAD_WHOLE }
        assertTrue(heads.isNotEmpty())
        for (h in heads) {
            val expected = if (h.y < Geometry.TREBLE_TOP + 7f) HandBands.RIGHT else HandBands.LEFT
            assertEquals(expected, HandBands.of(h))
        }
        assertTrue("hay cabezas de las dos manos", heads.any { HandBands.of(it) == HandBands.RIGHT } && heads.any { HandBands.of(it) == HandBands.LEFT })
        assertEquals(score.notes.count { it.hand == Hand.RIGHT }, heads.count { HandBands.of(it) == HandBands.RIGHT })
    }

    @Test
    fun barlinesNumbersAndDynamicsBelongToNoOne() {
        val layout = ScoreLayout.build(ExampleScores.all.first { it.id == "dinamicas" }.create())
        val bars = layout.marks.filterIsInstance<RectOp>().filter { it.height >= Geometry.SYSTEM_BOTTOM - 0.01f }
        assertTrue(bars.isNotEmpty())
        bars.forEach { assertEquals(HandBands.NONE, HandBands.of(it)) }
        layout.marks.filterIsInstance<TextOp>().forEach { assertEquals(HandBands.NONE, HandBands.of(it)) }
        val letters = layout.marks.filterIsInstance<GlyphOp>().filter { it.codepoint in 0xE520..0xE53F }
        assertTrue(letters.isNotEmpty())
        letters.forEach { assertEquals(HandBands.NONE, HandBands.of(it)) }
    }
}
