package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.KeySignatureChange
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Step
import com.federomano.pianoapp.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLayoutTest {

    private fun note(pitch: String, start: Int = 0, duration: Int = Ticks.QUARTER, hand: Hand = Hand.RIGHT, tied: Boolean = false) =
        Note(hand, Pitch.parse(pitch), start, duration, tiedToNext = tied)

    private fun layoutOf(vararg notes: Note, fifths: Int = 0): ScoreLayout =
        ScoreLayout.build(Score(title = "Prueba", keySignatures = listOf(KeySignatureChange(0, fifths)), notes = notes.toList()))

    private fun List<DrawOp>.heads() = filterIsInstance<GlyphOp>().filter {
        it.codepoint == Smufl.NOTEHEAD_BLACK || it.codepoint == Smufl.NOTEHEAD_HALF || it.codepoint == Smufl.NOTEHEAD_WHOLE
    }

    private fun List<DrawOp>.stems() = filterIsInstance<RectOp>().filter { it.width == NoteLayout.STEM_THICKNESS }
    private fun List<DrawOp>.ledgers() = filterIsInstance<RectOp>().filter { it.height == NoteLayout.LEDGER_THICKNESS && it.width > 1f }
    private fun List<DrawOp>.glyphs(code: Int) = filterIsInstance<GlyphOp>().filter { it.codepoint == code }

    // --- Figuras y posiciones ---

    @Test
    fun figuresAndDots() {
        assertEquals(NoteLayout.Figure(Ticks.QUARTER, 0), NoteLayout.figureOf(480))
        assertEquals(NoteLayout.Figure(Ticks.QUARTER, 1), NoteLayout.figureOf(720))
        assertEquals(NoteLayout.Figure(Ticks.QUARTER, 2), NoteLayout.figureOf(840))
        assertEquals(NoteLayout.Figure(Ticks.WHOLE, 0), NoteLayout.figureOf(1920))
        assertEquals(NoteLayout.Figure(Ticks.HALF, 1), NoteLayout.figureOf(1440))
        assertEquals(NoteLayout.Figure(Ticks.SIXTEENTH, 0), NoteLayout.figureOf(120))
    }

    @Test
    fun staffPositionsMatchTheClefs() {
        assertEquals(0, NoteLayout.staffPosition(Pitch.parse("E4"), Hand.RIGHT))
        assertEquals(-2, NoteLayout.staffPosition(Pitch.parse("C4"), Hand.RIGHT))
        assertEquals(8, NoteLayout.staffPosition(Pitch.parse("F5"), Hand.RIGHT))
        assertEquals(0, NoteLayout.staffPosition(Pitch.parse("G2"), Hand.LEFT))
        assertEquals(10, NoteLayout.staffPosition(Pitch.parse("C4"), Hand.LEFT))
        assertEquals(8, NoteLayout.staffPosition(Pitch.parse("A3"), Hand.LEFT))
    }

    @Test
    fun keySignatureAlterations() {
        assertEquals(1, NoteLayout.keyAlter(2, Step.F))
        assertEquals(1, NoteLayout.keyAlter(2, Step.C))
        assertEquals(0, NoteLayout.keyAlter(2, Step.G))
        assertEquals(-1, NoteLayout.keyAlter(-3, Step.E))
        assertEquals(-1, NoteLayout.keyAlter(-3, Step.A))
        assertEquals(0, NoteLayout.keyAlter(-3, Step.C))
        assertEquals(0, NoteLayout.keyAlter(0, Step.F))
    }

    @Test
    fun headsSitOnTheirLineOrSpace() {
        val ops = layoutOf(note("E4"), note("G4", hand = Hand.RIGHT, start = 480)).marks
        val ys = ops.heads().map { it.y }
        assertEquals(listOf(4f, 3f), ys)
        val bass = layoutOf(note("G2", hand = Hand.LEFT)).marks.heads().single()
        assertEquals(Geometry.BASS_TOP + 4f, bass.y, 0.001f)
    }

    @Test
    fun noteHeadShapeFollowsTheDuration() {
        val ops = layoutOf(
            note("E4", 0, Ticks.WHOLE).copy(hand = Hand.LEFT, pitch = Pitch.parse("C3")),
            note("E4", 0, Ticks.HALF),
        ).marks
        assertEquals(1, ops.glyphs(Smufl.NOTEHEAD_WHOLE).size)
        assertEquals(1, ops.glyphs(Smufl.NOTEHEAD_HALF).size)
    }

    // --- Plicas y banderas ---

    @Test
    fun stemGoesUpBelowTheMiddleLineAndDownFromIt() {
        val low = layoutOf(note("D4"))
        val noteX = low.measures[0].contentX
        val upStem = low.marks.stems().single()
        assertTrue(upStem.x > noteX)
        assertTrue(upStem.y < 4f)

        val middle = layoutOf(note("B4"))
        val downStem = middle.marks.stems().single()
        assertEquals(middle.measures[0].contentX, downStem.x, 0.001f)
        assertTrue(downStem.y + downStem.height > 2f)
    }

    @Test
    fun wholeNotesHaveNoStem() {
        assertEquals(0, layoutOf(note("C3", 0, Ticks.WHOLE, Hand.LEFT)).marks.stems().size)
    }

    @Test
    fun eighthsAndSixteenthsGetFlagsPointingTheRightWay() {
        val up = layoutOf(note("D4", 0, Ticks.EIGHTH), note("E4", 240, Ticks.SIXTEENTH)).marks
        assertEquals(1, up.glyphs(Smufl.FLAG_8TH_UP).size)
        assertEquals(1, up.glyphs(Smufl.FLAG_16TH_UP).size)
        val down = layoutOf(note("C5", 0, Ticks.EIGHTH), note("D5", 240, Ticks.SIXTEENTH)).marks
        assertEquals(1, down.glyphs(Smufl.FLAG_8TH_DOWN).size)
        assertEquals(1, down.glyphs(Smufl.FLAG_16TH_DOWN).size)
        assertEquals(0, layoutOf(note("D4", 0, Ticks.QUARTER)).marks.glyphs(Smufl.FLAG_8TH_UP).size)
    }

    @Test
    fun chordStemFollowsTheNoteFarthestFromTheMiddleLine() {
        // Do4 y Mi4 (abajo, lejos) con Re5 (poco arriba): manda lo de abajo, plica hacia arriba.
        val up = layoutOf(note("C4"), note("E4"), note("C5")).marks
        assertTrue(up.stems().single().x > 0f)
        val layout = layoutOf(note("G4"), note("B4"), note("F5"))
        val x = layout.measures[0].contentX
        assertEquals(x, layout.marks.stems().single().x, 0.001f)
    }

    // --- Líneas adicionales ---

    @Test
    fun ledgerLines() {
        assertEquals(0, layoutOf(note("E4")).marks.ledgers().size)
        assertEquals(0, layoutOf(note("F5")).marks.ledgers().size)
        // Do central: una línea adicional debajo, a la altura del Do.
        val c4 = layoutOf(note("C4")).marks.ledgers().single()
        assertEquals(5f, c4.y + c4.height / 2, 0.001f)
        // Si3 queda debajo de esa línea, que es la única que necesita.
        assertEquals(1, layoutOf(note("B3")).marks.ledgers().size)
        assertEquals(2, layoutOf(note("A3")).marks.ledgers().size)
        // Arriba: La5 tiene una, Do6 tiene dos.
        assertEquals(1, layoutOf(note("A5")).marks.ledgers().size)
        assertEquals(2, layoutOf(note("C6", 0, Ticks.WHOLE)).marks.ledgers().size)
        // En la mano izquierda, el Do central está por arriba.
        assertEquals(1, layoutOf(note("C4", hand = Hand.LEFT)).marks.ledgers().size)
    }

    @Test
    fun ledgerLinesAreSharedInAChord() {
        assertEquals(2, layoutOf(note("A3"), note("C4")).marks.ledgers().size)
    }

    // --- Alteraciones ---

    @Test
    fun accidentalsAreWrittenOnlyWhenTheyChangeWhatAlreadyApplies() {
        // Sin armadura: Fa# lleva sostenido; Fa natural no lleva nada.
        assertEquals(1, layoutOf(note("F#4")).marks.glyphs(Smufl.SHARP).size)
        assertEquals(0, layoutOf(note("F4")).marks.glyphs(Smufl.NATURAL).size)
        // Con armadura de Re mayor, Fa# ya lo da la armadura; Fa natural pide becuadro.
        assertEquals(0, layoutOf(note("F#4"), fifths = 2).marks.glyphs(Smufl.SHARP).size)
        assertEquals(1, layoutOf(note("F4"), fifths = 2).marks.glyphs(Smufl.NATURAL).size)
        // Con bemoles, Si natural pide becuadro y Si bemol no.
        assertEquals(1, layoutOf(note("B4"), fifths = -3).marks.glyphs(Smufl.NATURAL).size)
        assertEquals(0, layoutOf(note("Bb4"), fifths = -3).marks.glyphs(Smufl.FLAT).size)
    }

    @Test
    fun accidentalLastsUntilTheEndOfTheMeasure() {
        val ops = layoutOf(note("F#4", 0), note("F#4", 480), note("F4", 960), note("F4", 1440)).marks
        assertEquals(1, ops.glyphs(Smufl.SHARP).size)
        assertEquals(1, ops.glyphs(Smufl.NATURAL).size)
    }

    @Test
    fun accidentalAppliesOnlyToItsOwnOctaveAndHand() {
        val ops = layoutOf(note("F#4", 0), note("F#5", 480), note("F#4", 960, hand = Hand.LEFT)).marks
        assertEquals(3, ops.glyphs(Smufl.SHARP).size)
    }

    @Test
    fun accidentalIsRepeatedInTheNextMeasure() {
        val ops = layoutOf(note("F#4", 0), note("F#4", 1920)).marks
        assertEquals(2, ops.glyphs(Smufl.SHARP).size)
    }

    @Test
    fun tiedNoteAcrossTheBarlineDoesNotRepeatTheAccidental() {
        val ops = layoutOf(note("F#4", 1440, tied = true, duration = 480), note("F#4", 1920)).marks
        assertEquals(1, ops.glyphs(Smufl.SHARP).size)
    }

    @Test
    fun doubleAccidentals() {
        val ops = layoutOf(note("F##4"), note("B" + "bb" + "4", 480)).marks
        assertEquals(1, ops.glyphs(Smufl.DOUBLE_SHARP).size)
        assertEquals(1, ops.glyphs(Smufl.DOUBLE_FLAT).size)
    }

    @Test
    fun accidentalsSitToTheLeftOfTheHead() {
        val layout = layoutOf(note("F#4"))
        val sharp = layout.marks.glyphs(Smufl.SHARP).single()
        assertTrue(sharp.x + Smufl.SHARP_ADVANCE < layout.measures[0].contentX)
        assertTrue(sharp.x > layout.measures[0].x0)
    }

    @Test
    fun chordAccidentalsMoveToAnotherColumnWhenTheyWouldTouch() {
        val ops = layoutOf(note("C#4"), note("E#4")).marks
        val xs = ops.glyphs(Smufl.SHARP).map { it.x }.distinct()
        assertEquals(2, xs.size)
        val far = layoutOf(note("C#4"), note("C#5")).marks.glyphs(Smufl.SHARP).map { it.x }.distinct()
        assertEquals(1, far.size)
    }

    // --- Acordes ---

    @Test
    fun secondsInAChordAreDrawnSideBySide() {
        val layout = layoutOf(note("E4"), note("F4"))
        val heads = layout.marks.heads()
        assertEquals(2, heads.size)
        assertEquals(1.18f, kotlin.math.abs(heads[0].x - heads[1].x), 0.2f)
        // Una tercera no se desplaza.
        val third = layoutOf(note("E4"), note("G4")).marks.heads()
        assertEquals(third[0].x, third[1].x, 0.001f)
    }

    @Test
    fun threeNotesInSecondsAlternateSides() {
        val heads = layoutOf(note("C4"), note("D4"), note("E4")).marks.heads()
        val xs = heads.sortedBy { it.y }.map { it.x }
        // De abajo hacia arriba: normal, desplazada, normal.
        assertEquals(xs[0], xs[2], 0.001f)
    }

    // --- Puntillos ---

    @Test
    fun dotGoesInTheSpaceAboveWhenTheNoteIsOnALine() {
        val onLine = layoutOf(note("E4", duration = 720)).marks.glyphs(Smufl.AUGMENTATION_DOT).single()
        assertEquals(3.5f, onLine.y, 0.001f)
        val inSpace = layoutOf(note("F4", duration = 720)).marks.glyphs(Smufl.AUGMENTATION_DOT).single()
        assertEquals(3.5f, inSpace.y, 0.001f)
        val head = layoutOf(note("F4", duration = 720))
        assertTrue(head.marks.glyphs(Smufl.AUGMENTATION_DOT).single().x > head.measures[0].contentX + 1.18f)
    }

    @Test
    fun doubleDottedNoteHasTwoDots() {
        assertEquals(2, layoutOf(note("F4", duration = 840)).marks.glyphs(Smufl.AUGMENTATION_DOT).size)
    }

    @Test
    fun undottedNotesHaveNoDots() {
        assertEquals(0, layoutOf(note("F4")).marks.glyphs(Smufl.AUGMENTATION_DOT).size)
    }

    // --- Posición horizontal ---

    @Test
    fun notesOfBothHandsAtTheSameTimeLineUp() {
        val layout = layoutOf(note("G4", hand = Hand.RIGHT), note("C3", hand = Hand.LEFT))
        val xs = layout.marks.heads().map { it.x }
        assertEquals(xs[0], xs[1], 0.001f)
    }

    @Test
    fun notesAreSpacedByTime() {
        val layout = layoutOf(note("G4", 0), note("G4", 480))
        val xs = layout.marks.heads().map { it.x }
        assertEquals(Geometry.QUARTER_WIDTH, xs[1] - xs[0], 0.001f)
    }
}
