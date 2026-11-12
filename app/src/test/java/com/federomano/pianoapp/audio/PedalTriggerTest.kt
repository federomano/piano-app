package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.PedalMark
import com.federomano.pianoapp.score.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-68: el pedal de resonancia prolonga las notas que terminan con el pedal apretado. */
class PedalTriggerTest {
    private fun note(pitch: String, start: Int, duration: Int) = Note(Hand.RIGHT, Pitch.parse(pitch), start, duration)

    private class Log {
        val on = ArrayList<Pair<Note, Int>>()
        val off = ArrayList<Int>()
        var id = 0
        fun noteOn(n: Note): Int { on += n to ++id; return id }
    }

    private fun run(t: NoteTrigger, log: Log, vararg ticks: Float) = ticks.forEach { t.advance(it, log::noteOn, log.off::add) }

    @Test
    fun withoutPedalNotesEndOnTime() {
        val t = NoteTrigger(listOf(note("C4", 0, 480))); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 480f)
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun aNoteThatEndsInsideThePedalKeepsSoundingUntilItIsReleased() {
        val t = NoteTrigger(listOf(note("C4", 0, 480)), listOf(PedalMark(0, 1920))); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 480f, 1000f, 1919f)
        assertTrue("sigue sonando", log.off.isEmpty())
        run(t, log, 1920f)
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun aNoteThatEndsWhereThePedalIsReleasedOrPressedStopsThere() {
        val pedal = listOf(PedalMark(480, 1440))
        val t = NoteTrigger(listOf(note("C4", 0, 480), note("D4", 960, 480)), pedal); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 480f)
        assertEquals("termina justo cuando se aprieta: no se sostiene", listOf(1), log.off)
        run(t, log, 960f, 1440f)
        assertEquals("termina justo cuando se suelta: se apaga", listOf(1, 2), log.off)
    }

    @Test
    fun aPedalChangeSilencesWhatWasRingingButKeepsTheNewChord() {
        val pedal = listOf(PedalMark(0, 1920), PedalMark(1920, 3840))
        val t = NoteTrigger(listOf(note("C4", 0, 960), note("E4", 1920, 960)), pedal); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 960f, 1919f)
        assertTrue(log.off.isEmpty())
        run(t, log, 1920f)
        assertEquals("el do se apaga con el cambio de pedal", listOf(1), log.off)
        run(t, log, 2880f, 3000f)
        assertEquals("el mi sigue con el pedal nuevo", listOf(1), log.off)
        run(t, log, 3840f)
        assertEquals(listOf(1, 2), log.off)
    }

    @Test
    fun strikingTheSameKeyAgainReleasesTheOldSoundInsteadOfPilingUp() {
        val t = NoteTrigger(listOf(note("C4", 0, 480), note("C4", 960, 480)), listOf(PedalMark(0, 3840))); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 480f, 700f)
        assertTrue(log.off.isEmpty())
        run(t, log, 960f)
        assertEquals("la voz vieja se apaga al volver a tocar la tecla", listOf(1), log.off)
        assertEquals(2, log.on.size)
        run(t, log, 1440f, 3839f)
        assertEquals("la nueva sigue con el pedal", listOf(1), log.off)
    }

    @Test
    fun releaseAllAlsoStopsPedalSustainedNotes() {
        val t = NoteTrigger(listOf(note("C4", 0, 480)), listOf(PedalMark(0, 1920))); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 600f)
        t.releaseAll(log.off::add)
        assertEquals(listOf(1), log.off)
        run(t, log, 1920f)
        assertEquals("no se suelta dos veces", listOf(1), log.off)
    }

    @Test
    fun aBigJumpPastThePedalEndReleasesImmediately() {
        val t = NoteTrigger(listOf(note("C4", 0, 480)), listOf(PedalMark(0, 960))); val log = Log()
        t.reset(0f)
        run(t, log, 0f, 5000f)
        assertEquals(listOf(1), log.off)
    }
}
