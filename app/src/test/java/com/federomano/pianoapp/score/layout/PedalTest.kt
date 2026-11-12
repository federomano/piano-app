package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.PedalMark
import com.federomano.pianoapp.score.ScoreJson
import com.federomano.pianoapp.score.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-68: pedal de resonancia, dibujo y modelo. */
class PedalTest {
    private val score = ExampleScores.all.first { it.id == "pedal" }.create()
    private val layout = ScoreLayout.build(score)

    private fun glyphs(code: Int) = layout.marks.filterIsInstance<GlyphOp>().filter { it.codepoint == code }

    @Test
    fun theExampleIsValid() {
        assertTrue(score.validate().isEmpty())
    }

    @Test
    fun drawsPedWhereItIsPressedAStarWhereItIsReleasedAndAChangeSignBetweenTwoPedals() {
        // Pedales: 0-1920, 1920-3840, 4800-5760, 5760-7680.
        assertEquals("Ped. al aprietar: al inicio y en el tercer tiempo del compás 3", 2, glyphs(Smufl.PEDAL_DOWN).size)
        assertEquals("un signo de cambio entre los pedales pegados (compás 2 y compás 4)", 2, glyphs(Smufl.PEDAL_CHANGE).size)
        assertEquals("estrella al soltar: fin del compás 2 y fin de la pieza", 2, glyphs(Smufl.PEDAL_UP).size)
    }

    @Test
    fun theMarksAreBelowTheLeftHandStaffAndDoNotTouchTheNotes() {
        val pedalOps = layout.marks.filterIsInstance<GlyphOp>().filter { it.codepoint in setOf(Smufl.PEDAL_DOWN, Smufl.PEDAL_UP, Smufl.PEDAL_CHANGE) }
        val others = layout.marks.filterNot { it in pedalOps }.filterNot { it is RectOp && it.height >= Geometry.SYSTEM_BOTTOM - 0.01f }.let { Extents.boxes(it) }
        for (op in pedalOps) {
            val box = Extents.box(op)
            assertTrue("debajo del sistema", box.top >= Geometry.SYSTEM_BOTTOM)
            assertTrue("no pisa nada: $box", others.none { o -> o.right > box.left && o.left < box.right && o.bottom > box.top && o.top < box.bottom })
        }
    }

    @Test
    fun pedalMarksStayAlignedWithTheirTick() {
        val down = glyphs(Smufl.PEDAL_DOWN).minBy { it.x }
        val firstNoteX = layout.measures.first().xOfTick(0)
        assertEquals(firstNoteX - 0.3f, down.x, 0.01f)
    }

    @Test
    fun aScoreWithoutPedalDrawsNothingAndKeepsItsHeight() {
        val plain = ScoreLayout.build(ExampleScores.all.first { it.id == "himno" }.create())
        assertTrue(plain.marks.filterIsInstance<GlyphOp>().none { it.codepoint in setOf(Smufl.PEDAL_DOWN, Smufl.PEDAL_UP, Smufl.PEDAL_CHANGE) })
    }

    @Test
    fun theFileFormatReadsAndWritesPedals() {
        val json = ScoreJson.encode(score)
        assertTrue(json.contains("\"pedals\""))
        assertEquals(score.pedals, ScoreJson.decode(json).pedals)
    }

    @Test
    fun invalidPedalsAreRejected() {
        assertTrue(runCatching { PedalMark(100, 100) }.isFailure)
        assertTrue(runCatching { PedalMark(-1, 10) }.isFailure)
        val overlapping = score.copy(pedals = listOf(PedalMark(0, 1000), PedalMark(500, 2000)))
        assertTrue(overlapping.validate().any { it.contains("Pedal") })
        val pastTheEnd = score.copy(pedals = listOf(PedalMark(0, 99999)))
        assertTrue(pastTheEnd.validate().any { it.contains("después del final") })
        assertFalse(score.copy(pedals = listOf(PedalMark(0, 960), PedalMark(960, 1920))).validate().any { it.contains("Pedal") })
    }
}
