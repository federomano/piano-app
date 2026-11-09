package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.DynamicLevel
import com.federomano.pianoapp.score.DynamicMark
import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hairpin
import com.federomano.pianoapp.score.HairpinKind
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Rest
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.TimeSignatureChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtentsTest {
    private val bar = 1440

    private fun extremes(withDynamics: Boolean = true) = Score(
        title = "t",
        timeSignatures = listOf(TimeSignatureChange(0, 3, 4)),
        dynamics = if (withDynamics) listOf(DynamicMark(0, DynamicLevel.MF), DynamicMark(bar, DynamicLevel.FF)) else emptyList(),
        hairpins = if (withDynamics) listOf(Hairpin(0, bar, HairpinKind.CRESCENDO)) else emptyList(),
        notes = listOf(
            Note(Hand.RIGHT, Pitch.parse("C7"), 0, bar, accent = true),
            Note(Hand.RIGHT, Pitch.parse("C4"), bar, bar),
            Note(Hand.LEFT, Pitch.parse("C1"), 0, bar, accent = true),
            Note(Hand.LEFT, Pitch.parse("C3"), bar, bar),
        ),
        rests = emptyList(),
    )

    @Test
    fun marginsGrowToFitExtremeNotes() {
        val layout = ScoreLayout.build(extremes())
        assertTrue(layout.marginTop > Geometry.MARGIN_TOP)
        assertTrue(layout.marginBottom > Geometry.MARGIN_BOTTOM)
        val boxes = Extents.boxes(layout.body)
        assertTrue("nada se pasa por arriba", boxes.all { it.top >= -layout.marginTop })
        assertTrue("nada se pasa por abajo", boxes.all { it.bottom <= Geometry.SYSTEM_BOTTOM + layout.marginBottom })
    }

    @Test
    fun plainScoresKeepTheirUsualHeight() {
        for (id in listOf("escala", "himno", "voces")) {
            val layout = ScoreLayout.build(ExampleScores.all.first { it.id == id }.create())
            assertEquals("$id: marginTop", Geometry.MARGIN_TOP, layout.marginTop, 0.001f)
            assertEquals("$id: marginBottom", Geometry.MARGIN_BOTTOM, layout.marginBottom, 0.001f)
            assertEquals(Geometry.TOTAL_HEIGHT, layout.totalHeight, 0.001f)
        }
    }

    @Test
    fun dynamicsStayBelowTheLowNotesAndTheirAccents() {
        val ops = ScoreLayout.build(extremes()).body
        val letters = ops.filterIsInstance<GlyphOp>().filter { it.codepoint in 0xE520..0xE53F }
        assertEquals(2, letters.size)
        val others = Extents.boxes(ops.filter { !(it is GlyphOp && it.codepoint in 0xE520..0xE53F) && !(it is PolygonOp && it.points.filterIndexed { i, _ -> i % 2 == 1 }.min() > Geometry.SYSTEM_BOTTOM + 3f) })
        for (letter in letters) {
            val box = Extents.box(letter)
            val clash = others.filter { it.overlapsX(box.left, box.right) && it.bottom > box.top && it.top < box.bottom }
            assertTrue("la letra no pisa nada: $clash", clash.isEmpty())
        }
    }

    @Test
    fun barNumbersClearWhateverIsAboveThem() {
        val ops = ScoreLayout.build(extremes()).body
        val labels = ops.filterIsInstance<TextOp>()
        val rest = Extents.boxes(ops.filter { it !is TextOp })
        for (label in labels) {
            val box = Extents.box(label)
            val clash = rest.filter { it.overlapsX(box.left, box.right) && it.bottom > box.top && it.top < box.bottom && it.top < 0f }
            assertTrue("el número ${label.text} no pisa nada: $clash", clash.isEmpty())
        }
    }

    @Test
    fun layoutWithoutDynamicsStillWorks() {
        val layout = ScoreLayout.build(extremes(withDynamics = false))
        assertTrue(layout.totalHeight > Geometry.TOTAL_HEIGHT)
    }
}
