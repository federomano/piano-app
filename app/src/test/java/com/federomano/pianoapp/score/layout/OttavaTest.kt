package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.OttavaKind
import com.federomano.pianoapp.score.OttavaMark
import com.federomano.pianoapp.score.ScoreJson
import com.federomano.pianoapp.score.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-65: signos de octava (8va / 8vb). */
class OttavaTest {
    private val score = ExampleScores.all.first { it.id == "octavas" }.create()

    @Test
    fun octaveShiftFollowsTheMarks() {
        assertEquals(0, score.octaveShiftAt(Hand.RIGHT, 0))
        assertEquals(1, score.octaveShiftAt(Hand.RIGHT, 1920))
        assertEquals(1, score.octaveShiftAt(Hand.RIGHT, 3839))
        assertEquals(0, score.octaveShiftAt(Hand.RIGHT, 3840))
        assertEquals(-1, score.octaveShiftAt(Hand.LEFT, 2 * 1920))
        assertEquals(0, score.octaveShiftAt(Hand.LEFT, 1920))
    }

    @Test
    fun theExampleIsValidAndSurvivesJson() {
        assertEquals(emptyList<String>(), score.validate())
        assertEquals(score, ScoreJson.decode(ScoreJson.encode(score)))
    }

    @Test
    fun badMarksAreRejected() {
        val overlapping = score.copy(ottavas = listOf(
            OttavaMark(Hand.RIGHT, 1920, 3840, OttavaKind.OTTAVA_ALTA),
            OttavaMark(Hand.RIGHT, 3000, 4000, OttavaKind.OTTAVA_ALTA),
        ))
        assertTrue(overlapping.validate().any { "superponerse" in it })
        val empty = score.copy(ottavas = listOf(OttavaMark(Hand.LEFT, 0, 1920, OttavaKind.OTTAVA_ALTA)).let { it })
        // La izquierda sí tiene notas en el compás 1; una marca sobre un tramo sin notas de esa mano no vale.
        val nothing = score.copy(ottavas = listOf(OttavaMark(Hand.RIGHT, 100000, 100100, OttavaKind.OTTAVA_ALTA)))
        assertTrue(nothing.validate().any { "ninguna nota" in it })
        assertTrue(empty.validate().none { "ninguna nota" in it })
    }

    @Test
    fun anOttavaMustCoverBothNotesOfATie() {
        val notes = score.notes.toMutableList()
        val i = notes.indexOfFirst { it.hand == Hand.RIGHT && it.startTick == 1920 + 1440 }
        notes[i] = notes[i].copy(durationTicks = 240, tiedToNext = true)
        // La nota ligada empieza dentro de la 8va, pero la que sigue (en 3840) ya quedó afuera.
        val tied = notes[i].copy(startTick = 1920 + 1440 + 240)
        val broken = score.copy(notes = notes + tied.copy(startTick = 3840, durationTicks = 240, tiedToNext = false))
        assertTrue(broken.validate().isNotEmpty())
    }

    @Test
    fun highNotesAreWrittenAnOctaveLowerUnderAn8va() {
        val plain = ScoreLayout.build(score.copy(ottavas = emptyList()))
        val with = ScoreLayout.build(score)
        fun heads(l: ScoreLayout, bar: Int): List<Float> {
            val m = l.measures[bar]
            return l.body.filterIsInstance<GlyphOp>().filter { it.codepoint == Smufl.NOTEHEAD_BLACK && it.x >= m.contentX - 0.01f && it.x < m.x1 && it.y < Geometry.TREBLE_TOP + 5f }.map { it.y }
        }
        val a = heads(plain, 1)
        val b = heads(with, 1)
        assertEquals(4, a.size)
        assertEquals(4, b.size)
        // Una octava son 3,5 espacios de pentagrama: las cabezas bajan eso.
        a.sortedDescending().zip(b.sortedDescending()).forEach { (x, y) -> assertEquals(3.5f, y - x, 0.01f) }
    }

    @Test
    fun theSignsAreDrawnAboveAndBelowAndFlagTheLine() {
        val layout = ScoreLayout.build(score)
        val signs = layout.marks.filterIsInstance<GlyphOp>().filter { it.codepoint == Smufl.OTTAVA_ALTA || it.codepoint == Smufl.OTTAVA_BASSA }
        assertEquals(1, signs.count { it.codepoint == Smufl.OTTAVA_ALTA })
        assertEquals(1, signs.count { it.codepoint == Smufl.OTTAVA_BASSA })
        val up = signs.first { it.codepoint == Smufl.OTTAVA_ALTA }
        val down = signs.first { it.codepoint == Smufl.OTTAVA_BASSA }
        assertTrue("el 8va va arriba del pentagrama", up.y <= Geometry.TREBLE_TOP)
        assertTrue("el 8vb va abajo del pentagrama", down.y - Smufl.OTTAVA_HEIGHT >= Geometry.BASS_TOP + 4f)
        val dashes = layout.marks.filterIsInstance<RectOp>().filter { it.height < 0.2f && it.width in 0.3f..0.6f && it.x > up.x + 3f }
        assertTrue("línea punteada", dashes.size > 5)
    }
}
