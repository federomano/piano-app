package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Ticks
import com.federomano.pianoapp.score.chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStateTest {
    private fun note(hand: Hand, pitch: String, start: Int, duration: Int = Ticks.QUARTER) = Note(hand, Pitch.parse(pitch), start, duration)

    private fun layoutOf(vararg notes: Note) = ScoreLayout.build(Score(title = "t", notes = notes.toList()))

    private fun heldNotes(layout: ScoreLayout, tick: Float): List<String> =
        NoteStates.current(layout.chordSpans, tick).map { layout.chordSpans[it].chord.notes.joinToString("+") { n -> n.pitch.toString() } }.sorted()

    @Test
    fun spansCoverTheChordDrawingsWithoutOverlapping() {
        for (example in ExampleScores.all) {
            val layout = ScoreLayout.build(example.create())
            val used = BooleanArray(layout.marks.size)
            for (span in layout.chordSpans) {
                assertTrue("${example.id}: rango dentro de las marcas", span.from >= 0 && span.to <= layout.marks.size && span.from < span.to)
                for (i in span.from until span.to) {
                    assertTrue("${example.id}: la marca $i es de dos acordes", !used[i])
                    used[i] = true
                }
                assertEquals("${example.id}: una cabeza por nota", span.chord.notes.size, span.heads.size)
                for ((_, i) in span.heads) {
                    val op = layout.marks[i] as GlyphOp
                    assertTrue(op.codepoint in setOf(Smufl.NOTEHEAD_WHOLE, Smufl.NOTEHEAD_HALF, Smufl.NOTEHEAD_BLACK))
                    assertTrue(i in span.from until span.to)
                }
            }
            assertEquals("${example.id}: hay un acorde por cada acorde de la partitura", example.create().chords().size, layout.chordSpans.size)
        }
    }

    @Test
    fun spansAreSortedByTime() {
        val layout = ScoreLayout.build(ExampleScores.all.first { it.id == "himno" }.create())
        assertEquals(layout.chordSpans.map { it.chord.startTick }.sorted(), layout.chordSpans.map { it.chord.startTick })
    }

    @Test
    fun theSoundingChordOfEachHandIsCurrent() {
        val layout = layoutOf(
            note(Hand.RIGHT, "C5", 0), note(Hand.RIGHT, "D5", 480), note(Hand.RIGHT, "E5", 960),
            note(Hand.LEFT, "C3", 0, 960), note(Hand.LEFT, "G3", 960, 480),
        )
        assertEquals(listOf("C3", "C5"), heldNotes(layout, 0f))
        assertEquals(listOf("C3", "D5"), heldNotes(layout, 600f))
        assertEquals(listOf("E5", "G3"), heldNotes(layout, 960f))
        assertEquals(listOf("C3", "C5"), heldNotes(layout, 479.9f))
    }

    @Test
    fun aLongNoteStaysCurrentWhileShortOnesPassInTheOtherHand() {
        val layout = layoutOf(note(Hand.LEFT, "C3", 0, Ticks.WHOLE), note(Hand.RIGHT, "C5", 0), note(Hand.RIGHT, "D5", 480), note(Hand.RIGHT, "E5", 960), note(Hand.RIGHT, "F5", 1440))
        assertEquals(listOf("C3", "F5"), heldNotes(layout, 1500f))
    }

    @Test
    fun duringARestTheNextChordIsCurrent() {
        val layout = layoutOf(note(Hand.RIGHT, "C5", 0), note(Hand.RIGHT, "D5", 1440), note(Hand.RIGHT, "E5", 1920))
        assertEquals(listOf("D5"), heldNotes(layout, 600f))
        assertEquals(listOf("D5"), heldNotes(layout, 1440f))
    }

    @Test
    fun afterTheLastChordNothingIsCurrentAndEverythingIsPassed() {
        val layout = layoutOf(note(Hand.RIGHT, "C5", 0), note(Hand.RIGHT, "D5", 480))
        val current = NoteStates.current(layout.chordSpans, 5000f)
        assertTrue(current.isEmpty())
        for (i in layout.chordSpans.indices) assertEquals(NoteState.PASSED, NoteStates.playback(layout.chordSpans, i, 5000f, current))
    }

    @Test
    fun playbackStatesAreCurrentPassedAndPending() {
        val layout = layoutOf(note(Hand.RIGHT, "C5", 0), note(Hand.RIGHT, "D5", 480), note(Hand.RIGHT, "E5", 960))
        val current = NoteStates.current(layout.chordSpans, 500f)
        val states = layout.chordSpans.indices.map { NoteStates.playback(layout.chordSpans, it, 500f, current) }
        assertEquals(listOf(NoteState.PASSED, NoteState.CURRENT, NoteState.PENDING), states)
    }

    @Test
    fun emptyScoreHasNoCurrentChords() {
        assertTrue(NoteStates.current(emptyList(), 0f).isEmpty())
    }
}
