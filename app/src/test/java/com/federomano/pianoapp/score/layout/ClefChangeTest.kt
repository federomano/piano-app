package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Clef
import com.federomano.pianoapp.score.ClefChange
import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.ScoreJson
import com.federomano.pianoapp.score.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-64: cambios de clave por mano. */
class ClefChangeTest {
    private val score = ExampleScores.all.first { it.id == "claves" }.create()

    @Test
    fun clefAtFollowsTheChangesAndDefaultsToTrebleAndBass() {
        assertEquals(Clef.TREBLE, score.clefAt(Hand.RIGHT, 0))
        assertEquals(Clef.BASS, score.clefAt(Hand.LEFT, 0))
        assertEquals(Clef.TREBLE, score.clefAt(Hand.LEFT, 1920))
        assertEquals(Clef.BASS, score.clefAt(Hand.RIGHT, 2 * 1920 + 100))
        assertEquals(Clef.TREBLE, score.clefAt(Hand.RIGHT, 3 * 1920))
    }

    @Test
    fun staffPositionDependsOnTheClef() {
        assertEquals(-2, NoteLayout.staffPosition(Pitch.parse("C4"), Clef.TREBLE))
        assertEquals(10, NoteLayout.staffPosition(Pitch.parse("C4"), Clef.BASS))
        assertEquals(0, NoteLayout.staffPosition(Pitch.parse("G2"), Clef.BASS))
    }

    @Test
    fun theExampleIsValidAndSurvivesJson() {
        assertEquals(emptyList<String>(), score.validate())
        assertEquals(score, ScoreJson.decode(ScoreJson.encode(score)))
    }

    @Test
    fun aClefChangeNotAtTheStartOfAMeasureIsRejected() {
        val bad = score.copy(clefs = listOf(ClefChange(Hand.LEFT, 480, Clef.TREBLE)))
        assertTrue(bad.validate().any { "clave" in it && "inicio de un compás" in it })
        val unordered = score.copy(clefs = listOf(ClefChange(Hand.LEFT, 1920, Clef.TREBLE), ClefChange(Hand.LEFT, 1920, Clef.BASS)))
        assertTrue(unordered.validate().any { "ordenadas" in it })
    }

    @Test
    fun theRightHandInBassClefDrawsItsNotesOnTheUpperStaffWithBassPositions() {
        val changed = ScoreLayout.build(score)
        val m = changed.measures[2]
        val heads = changed.body.filterIsInstance<GlyphOp>().filter { it.x >= m.contentX - 0.01f && it.x < m.x1 && it.codepoint == Smufl.NOTEHEAD_BLACK }
        val upper = heads.filter { it.y < Geometry.TREBLE_TOP + 5f }
        assertEquals("cuatro cabezas de la derecha en el pentagrama de arriba", 4, upper.size)
        // C3 en clave de fa está en el segundo espacio de arriba del pentagrama (posición 8 - 4 = ...): dentro de las cinco líneas, sin líneas adicionales.
        assertTrue(upper.all { it.y >= Geometry.TREBLE_TOP && it.y <= Geometry.TREBLE_TOP + 4f })
    }

    @Test
    fun aSmallClefIsDrawnAtEachChangeAndTheMeasureMakesRoomForIt() {
        val layout = ScoreLayout.build(score)
        val glyphs = layout.marks.filterIsInstance<GlyphOp>()
        assertEquals("una clave de sol y una de fa por cambio en el compás 4 (y una por cada cambio anterior)", 2, glyphs.count { it.codepoint == Smufl.G_CLEF })
        assertEquals(2, glyphs.count { it.codepoint == Smufl.F_CLEF })
        assertTrue(glyphs.filter { it.codepoint == Smufl.G_CLEF || it.codepoint == Smufl.F_CLEF }.all { it.scale < 1f })
        val plain = ScoreLayout.build(score.copy(clefs = emptyList()))
        assertTrue("el compás 2 es más ancho por la clave", layout.measures[1].contentX - layout.measures[1].x0 > plain.measures[1].contentX - plain.measures[1].x0)
    }
}
