package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.KeySignatureChange
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Rest
import com.federomano.pianoapp.score.TimeSignatureChange
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
        val up = layoutOf(note("D4", 0, Ticks.EIGHTH), note("E4", 960, Ticks.SIXTEENTH)).marks
        assertEquals(1, up.glyphs(Smufl.FLAG_8TH_UP).size)
        assertEquals(1, up.glyphs(Smufl.FLAG_16TH_UP).size)
        val down = layoutOf(note("C5", 0, Ticks.EIGHTH), note("D5", 960, Ticks.SIXTEENTH)).marks
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

    // --- Barras ---

    private fun List<DrawOp>.beams() = filterIsInstance<PolygonOp>().filter { it.points.size == 8 }

    private fun eighths(vararg pitches: String, start: Int = 0, duration: Int = Ticks.EIGHTH, hand: Hand = Hand.RIGHT) =
        pitches.mapIndexed { i, p -> note(p, start + i * duration, duration, hand) }.toTypedArray()

    @Test
    fun twoEighthsInTheSameBeatAreJoinedByABeamWithoutFlags() {
        val ops = layoutOf(*eighths("C5", "D5")).marks
        assertEquals(1, ops.beams().size)
        assertEquals(0, ops.glyphs(Smufl.FLAG_8TH_DOWN).size + ops.glyphs(Smufl.FLAG_8TH_UP).size)
        assertEquals(2, ops.stems().size)
    }

    @Test
    fun eighthsInDifferentBeatsAreNotJoined() {
        val ops = layoutOf(note("C5", 240, Ticks.EIGHTH), note("D5", 480, Ticks.EIGHTH)).marks
        assertEquals(0, ops.beams().size)
        assertEquals(2, ops.glyphs(Smufl.FLAG_8TH_DOWN).size)
    }

    @Test
    fun fourEighthsInFourFourMakeTwoGroupsOfTwo() {
        assertEquals(2, layoutOf(*eighths("C5", "D5", "C5", "D5")).marks.beams().size)
    }

    @Test
    fun sixEighthsInSixEightMakeTwoGroupsOfThree() {
        val signature = TimeSignatureChange(0, 6, 8)
        val score = Score(
            title = "Prueba", timeSignatures = listOf(signature),
            notes = eighths("E4", "F4", "G4", "A4", "B4", "C5").toList(),
        )
        val beams = ScoreLayout.build(score).marks.beams()
        assertEquals(2, beams.size)
        // Cada barra abarca tres plicas: es más larga que la de dos corcheas.
        val two = layoutOf(*eighths("E4", "F4")).marks.beams().single()
        val widthOf = { b: PolygonOp -> b.points[2] - b.points[0] }
        assertTrue(widthOf(beams[0]) > widthOf(two))
    }

    @Test
    fun aRestBreaksTheBeamGroup() {
        val score = Score(
            title = "Prueba",
            notes = listOf(note("C5", 0, Ticks.EIGHTH), note("D5", 360, Ticks.EIGHTH)),
            rests = listOf(Rest(Hand.RIGHT, 240, 120)),
        )
        assertEquals(0, ScoreLayout.build(score).marks.beams().size)
    }

    @Test
    fun sixteenthsGetASecondBeam() {
        val ops = layoutOf(*eighths("C5", "D5", "E5", "F5", duration = Ticks.SIXTEENTH)).marks
        assertEquals(2, ops.beams().size)
    }

    @Test
    fun loneSixteenthNextToAnEighthGetsAShortSecondBeam() {
        val ops = layoutOf(note("C5", 0, Ticks.EIGHTH), note("D5", 240, Ticks.SIXTEENTH)).marks
        val beams = ops.beams()
        assertEquals(2, beams.size)
        val widths = beams.map { it.points[2] - it.points[0] }
        assertTrue(widths[1] < widths[0])
    }

    @Test
    fun allStemsOfAGroupPointTheSameWay() {
        // Dos notas bajas con una alta: manda la más alejada de la línea central.
        val ops = layoutOf(note("E4", 0, Ticks.EIGHTH), note("C6", 240, Ticks.EIGHTH)).marks
        val stems = ops.stems()
        assertEquals(2, stems.size)
        val layout = layoutOf(note("E4", 0, Ticks.EIGHTH), note("C6", 240, Ticks.EIGHTH))
        // C6 está mucho más lejos de la línea central que E4: las dos plicas hacia abajo, a la izquierda de cada cabeza.
        val heads = layout.marks.heads()
        assertEquals(heads[0].x, layout.marks.stems()[0].x, 0.001f)
    }

    @Test
    fun beamsFollowTheMelodyButWithALimitedSlope() {
        val rising = layoutOf(*eighths("E4", "G4", "B4", "D5", "F5", "A5", duration = Ticks.SIXTEENTH).take(2).toTypedArray()).marks.beams().first()
        val slope = (rising.points[3] - rising.points[1]) / (rising.points[2] - rising.points[0])
        assertTrue(slope <= 0.3f + 1e-4f && slope >= -0.3f - 1e-4f)
    }

    @Test
    fun beamedStemsReachTheBeam() {
        val layout = layoutOf(*eighths("C5", "D5"))
        val beam = layout.marks.beams().single()
        val stems = layout.marks.stems()
        val beamTops = listOf(beam.points[1], beam.points[3])
        // Plicas hacia abajo: terminan en el borde inferior de la barra.
        stems.forEachIndexed { i, stem -> assertEquals(beamTops[i] + 0f, stem.y + stem.height - 0.0f, 0.6f) }
    }

    // --- Silencios ---

    private fun restLayout(duration: Int, hand: Hand = Hand.RIGHT, start: Int = 0) =
        ScoreLayout.build(Score(title = "Prueba", rests = listOf(Rest(hand, start, duration)))).marks

    @Test
    fun eachDurationHasItsOwnRest() {
        assertEquals(1, restLayout(Ticks.WHOLE).glyphs(Smufl.REST_WHOLE).size)
        assertEquals(1, restLayout(Ticks.HALF).glyphs(Smufl.REST_HALF).size)
        assertEquals(1, restLayout(Ticks.QUARTER).glyphs(Smufl.REST_QUARTER).size)
        assertEquals(1, restLayout(Ticks.EIGHTH).glyphs(Smufl.REST_8TH).size)
        assertEquals(1, restLayout(Ticks.SIXTEENTH).glyphs(Smufl.REST_16TH).size)
    }

    @Test
    fun restsSitOnTheRightLineOfTheirStaff() {
        assertEquals(1f, restLayout(Ticks.WHOLE).glyphs(Smufl.REST_WHOLE).single().y, 0.001f)
        assertEquals(2f, restLayout(Ticks.HALF).glyphs(Smufl.REST_HALF).single().y, 0.001f)
        assertEquals(Geometry.BASS_TOP + 2f, restLayout(Ticks.QUARTER, Hand.LEFT).glyphs(Smufl.REST_QUARTER).single().y, 0.001f)
    }

    @Test
    fun aWholeMeasureRestIsCenteredInTheMeasureWhateverTheTimeSignature() {
        val score = Score(
            title = "Prueba", timeSignatures = listOf(TimeSignatureChange(0, 3, 4)),
            rests = listOf(Rest(Hand.RIGHT, 0, 1440)),
        )
        val layout = ScoreLayout.build(score)
        val rest = layout.marks.glyphs(Smufl.REST_WHOLE).single()
        val m = layout.measures[0]
        assertEquals((m.contentX + m.x1) / 2, rest.x + Smufl.REST_WHOLE_WIDTH / 2, 0.01f)
    }

    @Test
    fun dottedRestHasADot() {
        assertEquals(1, restLayout(720).glyphs(Smufl.AUGMENTATION_DOT).size)
        assertEquals(1, restLayout(720).glyphs(Smufl.REST_QUARTER).size)
    }

    // --- Ligaduras ---

    private fun ties(vararg notes: Note) = layoutOf(*notes).marks.filterIsInstance<PolygonOp>().filter { it.points.size > 8 }

    @Test
    fun tieJoinsTwoEqualNotes() {
        val tie = ties(note("G4", 0, tied = true), note("G4", 480)).single()
        val layout = layoutOf(note("G4", 0, tied = true), note("G4", 480))
        val heads = layout.marks.heads()
        val xs = tie.points.filterIndexed { i, _ -> i % 2 == 0 }
        assertTrue(xs.min() > heads[0].x)
        assertTrue(xs.max() < heads[1].x + 1.18f)
    }

    @Test
    fun tieGoesOppositeToTheStem() {
        // Sol4 tiene la plica hacia arriba: la ligadura va por debajo de la cabeza (y mayor).
        val below = ties(note("G4", 0, tied = true), note("G4", 480)).single()
        val ys = below.points.filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue(ys.min() > 3f - 0.01f)
        // Do5 tiene la plica hacia abajo: la ligadura va por encima.
        val above = ties(note("C5", 0, tied = true), note("C5", 480)).single()
        assertTrue(above.points.filterIndexed { i, _ -> i % 2 == 1 }.max() < 1.5f + 0.01f)
    }

    @Test
    fun tieCrossesTheBarline() {
        val layout = layoutOf(note("C4", 1440, tied = true), note("C4", 1920))
        val tie = layout.marks.filterIsInstance<PolygonOp>().single { it.points.size > 8 }
        val barline = layout.measures[0].x1
        val xs = tie.points.filterIndexed { i, _ -> i % 2 == 0 }
        assertTrue(xs.min() < barline && xs.max() > barline)
    }

    @Test
    fun untiedNotesHaveNoTie() {
        assertEquals(0, ties(note("G4", 0), note("G4", 480)).size)
    }

    @Test
    fun tieOnOneNoteOfAChordOnlyTiesThatNote() {
        val first = note("C4", 0, tied = true)
        val second = note("E4", 0)
        assertEquals(1, ties(first, second, note("C4", 480)).size)
    }
}
