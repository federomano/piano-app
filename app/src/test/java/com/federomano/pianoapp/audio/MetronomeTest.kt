package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.TimeSignatureChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetronomeTest {
    private fun measures(sig: TimeSignatureChange, count: Int, from: Int = 0) =
        (0 until count).map { Measure(it + 1, from + it * sig.measureTicks, from + (it + 1) * sig.measureTicks, sig, 0) }

    @Test
    fun fourFourClicksEveryQuarterWithTheFirstAccented() {
        val clicks = Metronome(measures(TimeSignatureChange(0, 4, 4), 2)).clicks
        assertEquals(listOf(0, 480, 960, 1440, 1920, 2400, 2880, 3360), clicks.map { it.tick })
        assertEquals(listOf(true, false, false, false, true, false, false, false), clicks.map { it.accent })
    }

    @Test
    fun threeFourHasThreeBeatsAndSixEightHasTwo() {
        assertEquals(3, Metronome(measures(TimeSignatureChange(0, 3, 4), 1)).clicks.size)
        val six = Metronome(measures(TimeSignatureChange(0, 6, 8), 1)).clicks
        assertEquals(listOf(0, 720), six.map { it.tick })
        assertEquals(listOf(true, false), six.map { it.accent })
    }

    @Test
    fun beatDependsOnTheBottomNumber() {
        assertEquals(480, Metronome.beatTicks(TimeSignatureChange(0, 4, 4)))
        assertEquals(960, Metronome.beatTicks(TimeSignatureChange(0, 2, 2)))
        assertEquals(240, Metronome.beatTicks(TimeSignatureChange(0, 5, 8)))
        assertEquals(720, Metronome.beatTicks(TimeSignatureChange(0, 12, 8)))
    }

    @Test
    fun timeSignatureChangesAreFollowed() {
        val ms = measures(TimeSignatureChange(0, 4, 4), 1) + measures(TimeSignatureChange(1920, 3, 4), 1, from = 1920)
        val clicks = Metronome(ms).clicks
        assertEquals(listOf(0, 480, 960, 1440, 1920, 2400, 2880), clicks.map { it.tick })
        assertTrue(clicks.single { it.tick == 1920 }.accent)
    }

    @Test
    fun pickupBeatsLineUpWithTheEndOfThePickup() {
        val sig = TimeSignatureChange(0, 4, 4)
        // Anacrusa de una negra: un solo clic, sin acento; después, el primer compás entero.
        val pickup = Measure(0, 0, 480, sig, 0, gridOffset = 1440)
        val full = Measure(1, 480, 2400, sig, 0)
        val clicks = Metronome(listOf(pickup, full)).clicks
        assertEquals(listOf(0, 480, 960, 1440, 1920), clicks.map { it.tick })
        assertFalse(clicks[0].accent)
        assertTrue(clicks[1].accent)
        // Con la anacrusa de 720 (una negra y media) la grilla se corre: el clic cae a mitad de la anacrusa.
        val odd = Metronome(listOf(Measure(0, 0, 720, sig, 0, gridOffset = 1200), Measure(1, 720, 2640, sig, 0))).clicks
        assertEquals(240, odd.first().tick)
        assertEquals(720, odd[1].tick)
    }

    @Test
    fun advanceFiresEachClickOnceAndResetSkipsTheOnesBefore() {
        val m = Metronome(measures(TimeSignatureChange(0, 4, 4), 1))
        val heard = mutableListOf<Boolean>()
        m.reset(0f)
        m.advance(0f) { heard += it }
        assertEquals(listOf(true), heard)
        m.advance(100f) { heard += it }
        assertEquals(1, heard.size)
        m.advance(960f) { heard += it }
        assertEquals(listOf(true, false, false), heard)
        heard.clear()
        m.reset(500f)
        m.advance(1440f) { heard += it }
        assertEquals(listOf(false, false), heard) // 960 y 1440
    }

    @Test
    fun countInIsOneFullMeasureOfBeats() {
        assertEquals(4, Metronome.countInBeats(TimeSignatureChange(0, 4, 4)))
        assertEquals(3, Metronome.countInBeats(TimeSignatureChange(0, 3, 4)))
        assertEquals(2, Metronome.countInBeats(TimeSignatureChange(0, 6, 8)))
    }
}

class ClickSoundTest {
    @Test
    fun clickIsShortLoudEnoughAndFadesOut() {
        for (accent in listOf(true, false)) {
            val c = ClickSound.render(accent)
            assertTrue("dura menos de 80 ms", c.size < PianoMapping.SAMPLE_RATE * 0.08)
            val peak = c.maxOf { abs(it.toInt()) }
            assertTrue("se oye", peak > 10000)
            assertTrue("se apaga", c.takeLast(50).all { abs(it.toInt()) < 400 })
        }
    }

    @Test
    fun accentedClickIsHigherPitched() {
        fun crossings(c: ShortArray) = (1 until c.size).count { (c[it - 1] < 0) != (c[it] < 0) }
        assertTrue(crossings(ClickSound.render(true)) > crossings(ClickSound.render(false)))
    }
}
