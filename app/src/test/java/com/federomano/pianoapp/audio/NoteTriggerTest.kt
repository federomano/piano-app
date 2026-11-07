package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.DemoScore
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.TempoMap
import com.federomano.pianoapp.score.layout.Geometry
import com.federomano.pianoapp.score.layout.ScoreLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTriggerTest {
    private fun note(midiStep: Int, start: Int, dur: Int) = DemoScore.create().notes.first().let {
        Note(it.hand, it.pitch.copy(octave = 4 + midiStep), start, dur)
    }

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

    /** Reproduce el recorrido real (tempo, línea fija, suavizado) cuadro a cuadro y mira cuándo suena cada nota. */
    @Test
    fun everyNoteSoundsOnceAtTheMomentItCrossesThePlayhead() {
        val score = DemoScore.create()
        val layout = ScoreLayout.build(score)
        val tempo = TempoMap(score.tempos)
        val playhead = 800f / 17f * Geometry.PLAYHEAD_FRACTION * 2.4f
        val trigger = NoteTrigger(score.notes); val log = Log()
        fun visualTick(tick: Float): Float {
            val scroll = layout.scrollForTick(tick, playhead)
            return layout.tickAtX(scroll + playhead - layout.headerWidth)
        }
        var tick = 0f
        trigger.reset(visualTick(tick) - 24f)
        val frame = 1f / 120f
        val firedAt = HashMap<Note, Float>()
        while (tick < layout.endTick) {
            tick = tempo.advance(tick, frame)
            val before = log.on.size
            trigger.advance(visualTick(tick), log::noteOn, log.off::add)
            for (i in before until log.on.size) firedAt[log.on[i].first] = visualTick(tick)
        }
        assertEquals(score.notes.size, log.on.size)
        for (n in score.notes) {
            val vis = firedAt.getValue(n)
            val perFrame = tempo.ticksPerSecond(vis) * frame * 3f
            assertTrue("nota en ${n.startTick}: sonó con la posición visible en $vis", vis >= n.startTick && vis - n.startTick <= perFrame)
        }
    }
}
