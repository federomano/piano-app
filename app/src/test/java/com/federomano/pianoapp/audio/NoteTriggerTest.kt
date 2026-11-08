package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.DemoScore
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.TempoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTriggerTest {
    private fun note(midiStep: Int, start: Int, dur: Int) = DemoScore.create().notes.first().let {
        Note(it.hand, it.pitch.copy(octave = 4 + midiStep), start, dur)
    }

    private fun tied(midiStep: Int, start: Int, dur: Int) = note(midiStep, start, dur).copy(tiedToNext = true)

    private class Log {
        val on = ArrayList<Pair<Note, Int>>()
        val off = ArrayList<Int>()
        var id = 0
        fun noteOn(n: Note): Int { on += n to ++id; return id }
    }

    @Test
    fun notesStartWhenThePositionReachesThemAndEndWhenItPassesTheirEnd() {
        val a = note(0, 0, 480); val b = note(1, 480, 480)
        val t = NoteTrigger(listOf(b, a)); val log = Log()
        t.reset(-24f)
        t.advance(0f, log::noteOn, log.off::add)
        assertEquals(listOf(a), log.on.map { it.first })
        t.advance(479f, log::noteOn, log.off::add)
        assertTrue(log.off.isEmpty())
        t.advance(480f, log::noteOn, log.off::add)
        assertEquals(listOf(a, b), log.on.map { it.first })
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun chordsStartTogetherAndNothingRepeats() {
        val notes = listOf(note(0, 0, 960), note(1, 0, 960), note(2, 0, 960))
        val t = NoteTrigger(notes); val log = Log()
        t.reset(-24f)
        t.advance(1f, log::noteOn, log.off::add)
        t.advance(2f, log::noteOn, log.off::add)
        assertEquals(3, log.on.size)
    }

    @Test
    fun resetSkipsWhatAlreadyHappenedAndReleaseAllLetsGoOfEverything() {
        val notes = listOf(note(0, 0, 480), note(1, 480, 480), note(2, 960, 480))
        val t = NoteTrigger(notes); val log = Log()
        t.reset(500f)
        t.advance(960f, log::noteOn, log.off::add)
        assertEquals(listOf(notes[2]), log.on.map { it.first })
        t.releaseAll(log.off::add)
        assertEquals(listOf(1), log.off)
        t.releaseAll(log.off::add)
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun aShortNotePassedInOneJumpStillSoundsAndIsReleased() {
        val a = note(0, 100, 50)
        val t = NoteTrigger(listOf(a)); val log = Log()
        t.reset(0f)
        t.advance(1000f, log::noteOn, log.off::add)
        assertEquals(1, log.on.size); assertEquals(listOf(1), log.off)
    }

    @Test
    fun aTiedNoteSoundsOnceAndLastsForBothFigures() {
        val a = tied(0, 0, 480); val b = note(0, 480, 480)
        val t = NoteTrigger(listOf(a, b)); val log = Log()
        t.reset(-24f)
        t.advance(0f, log::noteOn, log.off::add)
        t.advance(480f, log::noteOn, log.off::add)
        assertEquals(1, log.on.size)
        assertTrue("sigue sonando en el cambio", log.off.isEmpty())
        t.advance(959f, log::noteOn, log.off::add)
        assertTrue(log.off.isEmpty())
        t.advance(960f, log::noteOn, log.off::add)
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun aTieSurvivesAJumpOverBothNotes() {
        val a = tied(0, 0, 480); val b = note(0, 480, 480)
        val t = NoteTrigger(listOf(a, b)); val log = Log()
        t.reset(-24f)
        t.advance(2000f, log::noteOn, log.off::add)
        assertEquals(1, log.on.size)
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun aRepeatedNoteWithoutATieSoundsTwice() {
        val a = note(0, 0, 480); val b = note(0, 480, 480)
        val t = NoteTrigger(listOf(a, b)); val log = Log()
        t.reset(-24f)
        t.advance(480f, log::noteOn, log.off::add)
        assertEquals(2, log.on.size)
    }

    @Test
    fun aTieOnlyContinuesTheSamePitch() {
        val a = tied(0, 0, 480); val b = note(1, 480, 480)
        val t = NoteTrigger(listOf(a, b)); val log = Log()
        t.reset(-24f)
        t.advance(480f, log::noteOn, log.off::add)
        assertEquals(2, log.on.size)
        assertEquals(listOf(1), log.off)
    }

    @Test
    fun startingRightOnTheSecondNoteOfATieStillPlaysIt() {
        val a = tied(0, 0, 480); val b = note(0, 480, 480)
        val t = NoteTrigger(listOf(a, b)); val log = Log()
        t.reset(470f)
        t.advance(480f, log::noteOn, log.off::add)
        assertEquals(listOf(b), log.on.map { it.first })
    }

    /** Reproduce el recorrido cuadro a cuadro con el reloj del tempo: cada nota suena una vez, a tiempo. */
    @Test
    fun everyNoteSoundsOnceOnTheExactMusicalClock() {
        val score = DemoScore.create()
        val tempo = TempoMap(score.tempos)
        val trigger = NoteTrigger(score.notes); val log = Log()
        var tick = 0f
        trigger.reset(tick - 24f)
        val frame = 1f / 120f
        val firedAt = HashMap<Note, Float>()
        while (tick < score.notes.maxOf { it.endTick } + 10f) {
            tick = tempo.advance(tick, frame)
            val before = log.on.size
            trigger.advance(tick, log::noteOn, log.off::add)
            for (i in before until log.on.size) firedAt[log.on[i].first] = tick
        }
        // Las notas que continúan una ligadura no vuelven a atacar.
        val continued = score.notes.filter { n ->
            score.notes.any { p -> p.tiedToNext && p.hand == n.hand && p.pitch == n.pitch && p.endTick == n.startTick }
        }
        assertTrue("la partitura de práctica tiene ligaduras", continued.isNotEmpty())
        assertEquals(score.notes.size - continued.size, log.on.size)
        for (n in score.notes - continued.toSet()) {
            val at = firedAt.getValue(n)
            val oneFrame = tempo.ticksPerSecond(at) * frame
            assertTrue("nota en ${n.startTick}: sonó en $at", at >= n.startTick && at - n.startTick <= oneFrame + 0.01f)
        }
        assertEquals(log.on.size, log.off.size)
    }
}
