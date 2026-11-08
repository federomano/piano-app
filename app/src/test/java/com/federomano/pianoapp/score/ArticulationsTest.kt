package com.federomano.pianoapp.score

import com.federomano.pianoapp.score.layout.GlyphOp
import com.federomano.pianoapp.score.layout.ScoreLayout
import com.federomano.pianoapp.score.layout.Smufl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticulationsTest {
    private fun note(pitch: String, start: Int, duration: Int = 480, staccato: Boolean = false, accent: Boolean = false, hand: Hand = Hand.RIGHT) =
        Note(hand, Pitch.parse(pitch), start, duration, staccato = staccato, accent = accent)

    private fun score(vararg notes: Note, dynamics: List<DynamicMark> = emptyList()) = Score(
        title = "t",
        dynamics = dynamics,
        notes = notes.toList(),
        rests = listOf(Rest(Hand.LEFT, 0, 1920)) + if (notes.sumOf { it.durationTicks } < 1920) listOf(Rest(Hand.RIGHT, notes.sumOf { it.durationTicks }, 1920 - notes.sumOf { it.durationTicks })) else emptyList(),
    )

    @Test
    fun staccatoSoundsHalfAndKeepsTheStartOfTheNext() {
        val s = score(note("C4", 0, staccato = true), note("D4", 480), note("E4", 960, 960))
        val played = s.playedNotes()
        assertEquals(240, played[0].durationTicks)
        assertEquals(0, played[0].startTick)
        assertEquals(480, played[1].startTick)
        assertEquals(480, played[1].durationTicks)
    }

    @Test
    fun accentAddsToTheCurrentDynamic() {
        val s = score(note("C4", 0, accent = true), note("D4", 480), dynamics = listOf(DynamicMark(0, DynamicLevel.MP)))
        assertEquals(DynamicLevel.MP.velocity + ACCENT_BOOST, s.velocityOf(s.notes[0]))
        assertEquals(DynamicLevel.MP.velocity, s.velocityOf(s.notes[1]))
    }

    @Test
    fun accentNeverPassesTheLimit() {
        val s = score(note("C4", 0, accent = true), dynamics = listOf(DynamicMark(0, DynamicLevel.FFF)))
        assertEquals(127, s.velocityOf(s.notes[0]))
    }

    @Test
    fun withoutDynamicsAnAccentStillStandsOut() {
        val s = score(note("C4", 0, accent = true), note("D4", 480))
        assertTrue(s.velocityOf(s.notes[0]) > s.velocityOf(s.notes[1]))
        assertEquals(DEFAULT_VELOCITY, s.velocityOf(s.notes[1]))
    }

    @Test
    fun tiedOrGraceNotesCannotBeStaccato() {
        assertTrue(runCatching { Note(Hand.RIGHT, Pitch.parse("C4"), 0, 480, tiedToNext = true, staccato = true) }.isFailure)
        assertTrue(runCatching { Note(Hand.RIGHT, Pitch.parse("C4"), 0, 60, grace = true, accent = true) }.isFailure)
    }

    @Test
    fun aChordHasToAgreeOnItsMarks() {
        val bad = score(note("C4", 0, 1920, staccato = true), note("E4", 0, 1920))
        assertTrue(bad.validate().any { it.contains("mismo staccato") })
        val good = score(note("C4", 0, 1920, staccato = true), note("E4", 0, 1920, staccato = true))
        assertEquals(emptyList<String>(), good.validate())
    }

    @Test
    fun jsonRoundTrip() {
        val s = score(note("C4", 0, 960, staccato = true, accent = true), note("D4", 960, 960))
        assertEquals(s, ScoreJson.decode(ScoreJson.encode(s)))
    }

    @Test
    fun layoutDrawsTheDotAndTheAccent() {
        val s = score(note("C4", 0, 960, staccato = true, accent = true), note("D4", 960, 960))
        val ops = ScoreLayout.build(s).body
        assertEquals(1, ops.filterIsInstance<GlyphOp>().count { it.codepoint == Smufl.STACCATO_ABOVE })
        assertEquals(1, ops.filterIsInstance<GlyphOp>().count { it.codepoint == Smufl.ACCENT_ABOVE || it.codepoint == Smufl.ACCENT_BELOW })
        val plain = ScoreLayout.build(score(note("C4", 0, 960), note("D4", 960, 960))).body
        assertFalse(plain.filterIsInstance<GlyphOp>().any { it.codepoint == Smufl.STACCATO_ABOVE })
    }
}
