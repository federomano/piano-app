package com.federomano.pianoapp.score

import com.federomano.pianoapp.audio.NoteTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notas de adorno (PIA-61): suenan justo antes del tiempo y no cuentan para llenar el compás. */
class GraceTest {
    private fun note(pitch: String, start: Int, dur: Int, grace: Boolean = false, hand: Hand = Hand.RIGHT) =
        Note(hand, Pitch.parse(pitch), start, dur, grace = grace)

    private fun score(vararg notes: Note) = Score(title = "t", notes = notes.toList())

    private val base = arrayOf(
        note("C4", 0, 960),
        note("D4", 960, 960),
        note("D4", 960, Ticks.GRACE, grace = true), // adorno en el tick 960, un instante antes
        note("C3", 0, 1920, hand = Hand.LEFT),
    )

    @Test
    fun graceDoesNotCountForTheMeasure() {
        val s = score(*base)
        assertTrue(s.validate().toString(), s.validate().isEmpty())
        assertEquals(1920, s.lengthTicks)
        assertEquals(3, s.chords().size) // el adorno no es un acorde
    }

    @Test
    fun graceSoundsJustBeforeItsBeat() {
        val s = score(*base)
        val played = s.playedNotes().filter { it.pitch.toString() == "D4" }.sortedBy { it.startTick }
        assertEquals(listOf(900, 960), played.map { it.startTick })
        assertEquals(60, played[0].durationTicks)
        assertEquals(960, played[1].startTick) // el ritmo de la nota principal no se corre
    }

    @Test
    fun triggerPlaysTheGraceBeforeTheMainNote() {
        val s = score(*base)
        val trigger = NoteTrigger(s.playedNotes())
        val on = mutableListOf<Pair<Float, String>>()
        var now = 0f
        for (tick in 0..1920 step 10) {
            now = tick.toFloat()
            trigger.advance(now, { on += now to it.pitch.toString(); on.size }, { })
        }
        val d = on.filter { it.second == "D4" }.map { it.first }
        assertEquals(listOf(900f, 960f), d)
    }

    @Test
    fun graceAtTheVeryStartSoundsOnTheBeat() {
        // No hay lugar antes del tick 0: el adorno suena sobre el tiempo y nunca queda con duración 0.
        val g = note("B3", 0, Ticks.GRACE, grace = true)
        assertEquals(0, g.soundStartTick)
        assertEquals(Ticks.GRACE, g.soundEndTick)
        val s = score(g, note("C4", 0, 1920))
        assertTrue(s.playedNotes().all { it.durationTicks > 0 })
    }

    @Test
    fun everyExampleCanBePlayedAndLaidOut() {
        for (example in ExampleScores.all) {
            val s = example.create()
            assertTrue(example.id, s.playedNotes().all { it.durationTicks > 0 })
            com.federomano.pianoapp.score.layout.ScoreLayout.build(s)
        }
    }

    @Test
    fun graceNeedsAMainNoteInTheSameHandAndTick() {
        val s = score(note("C4", 0, 1920), note("E4", 960, Ticks.GRACE, grace = true), note("C3", 0, 1920, hand = Hand.LEFT))
        assertTrue(s.validate().any { "nota principal" in it })
        val other = score(note("C4", 0, 1920), note("E3", 0, Ticks.GRACE, grace = true, hand = Hand.LEFT), note("C3", 960, 960, hand = Hand.LEFT), note("C3", 0, 960, hand = Hand.LEFT))
        assertTrue(other.validate().isEmpty())
    }

    @Test
    fun graceCannotBeLongTiedOrTuplet() {
        assertTrue(runCatching { note("C4", 0, 480, grace = true) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { Note(Hand.RIGHT, Pitch.parse("C4"), 0, 60, tiedToNext = true, grace = true) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun jsonRoundTripKeepsGrace() {
        val s = score(*base)
        val back = ScoreJson.decode(ScoreJson.encode(s))
        assertEquals(1, back.notes.count { it.grace })
    }

    @Test
    fun exampleIsValid() {
        val s = ExampleScores.all.first { it.id == "adornos" }.create()
        assertTrue(s.validate().toString(), s.validate().isEmpty())
        assertTrue(s.notes.count { it.grace } >= 5)
    }
}
