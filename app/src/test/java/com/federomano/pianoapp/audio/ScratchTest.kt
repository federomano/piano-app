package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-55: efecto vinilo al arrastrar la partitura. */
class ScratchTest {
    private val rate = 44100
    private val block = 256

    private fun sine(hz: Double, seconds: Double = 4.0) =
        ShortArray((seconds * rate).toInt()) { (sin(2 * PI * hz * it / rate) * 20000).toInt().toShort() }

    /** Mueve la voz a [speed] veces la velocidad normal durante [seconds] segundos y devuelve el audio que sale. */
    private fun scratch(mixer: Mixer, voice: Mixer.ScratchVoice, from: Float, speed: Float, seconds: Float): FloatArray {
        val out = ArrayList<Float>()
        val buf = FloatArray(block)
        var t = from
        val blocks = (seconds * rate / block).toInt()
        repeat(blocks) {
            val dt = block.toFloat() / rate
            t += speed * dt
            voice.move(t, speed)
            mixer.render(buf, block)
            for (x in buf) out += x
        }
        return out.toFloatArray()
    }

    private fun crossings(x: FloatArray, skip: Int = rate / 10) =
        (skip + 1 until x.size).count { (x[it - 1] < 0f) != (x[it] < 0f) }

    private fun rms(x: FloatArray) = Math.sqrt(x.sumOf { (it * it).toDouble() } / x.size)

    @Test
    fun movingAtNormalSpeedKeepsThePitchAndDoublingItRaisesAnOctave() {
        val data = sine(440.0)
        val normal = Mixer(rate).let { m -> scratch(m, m.scratchOn(data, 1f, rate.toDouble(), 1f, 0.5f), 0.5f, 1f, 1f) }
        val double = Mixer(rate).let { m -> scratch(m, m.scratchOn(data, 1f, rate.toDouble(), 1f, 0.5f), 0.5f, 2f, 1f) }
        val seconds = 0.9 // sin el primer décimo
        val hzNormal = crossings(normal) / 2 / seconds
        val hzDouble = crossings(double) / 2 / seconds
        assertEquals(440.0, hzNormal, 440 * 0.1)
        assertEquals(880.0, hzDouble, 880 * 0.1)
    }

    @Test
    fun movingBackwardsPlaysReversedAndStillSounds() {
        val data = sine(440.0)
        val m = Mixer(rate)
        val out = scratch(m, m.scratchOn(data, 1f, rate.toDouble(), 1f, 2f), 2f, -1f, 1f)
        assertTrue("suena", rms(out) > 0.05)
        assertEquals(440.0, crossings(out) / 2 / 0.9, 44.0)
    }

    @Test
    fun aStillVoiceIsSilent() {
        val data = sine(440.0)
        val m = Mixer(rate)
        val v = m.scratchOn(data, 1f, rate.toDouble(), 1f, 1f)
        val out = scratch(m, v, 1f, 0f, 1f)
        assertTrue("quieto no suena (${rms(out)})", rms(out.copyOfRange(rate / 2, out.size)) < 0.001)
    }

    @Test
    fun slowerMovementIsQuieter() {
        val data = sine(440.0)
        val loud = Mixer(rate).let { m -> rms(scratch(m, m.scratchOn(data, 1f, rate.toDouble(), 1f, 0.5f), 0.5f, 1f, 0.5f)) }
        val soft = Mixer(rate).let { m -> rms(scratch(m, m.scratchOn(data, 1f, rate.toDouble(), 1f, 0.5f), 0.5f, 0.05f, 0.5f)) }
        assertTrue("$soft < $loud", soft < loud)
    }

    @Test
    fun releasedVoicesFadeAndDisappearWithoutAJump() {
        val data = sine(440.0)
        val m = Mixer(rate)
        val v = m.scratchOn(data, 1f, rate.toDouble(), 1f, 0.5f)
        scratch(m, v, 0.5f, 1f, 0.3f)
        assertEquals(1, m.activeScratch)
        v.release()
        val buf = FloatArray(block)
        var biggestStep = 0f
        var prev = 0f
        repeat(rate / block) {
            m.render(buf, block)
            for (x in buf) { biggestStep = maxOf(biggestStep, abs(x - prev)); prev = x }
        }
        assertEquals(0, m.activeScratch)
        assertTrue("sin saltos ($biggestStep)", biggestStep < 0.2f)
    }

    @Test
    fun muteAllOffReleasesScratchVoices() {
        val m = Mixer(rate)
        m.scratchOn(sine(440.0), 1f, rate.toDouble(), 1f, 0.5f)
        val buf = FloatArray(block)
        m.render(buf, block)
        assertEquals(1, m.activeScratch)
        m.allOff()
        repeat(rate / block) { m.render(buf, block) }
        assertEquals(0, m.activeScratch)
    }

    // --- Scratcher ---

    private class Fake : ScratchHandle {
        var moves = 0
        var lastSeconds = 0f
        var lastRate = 0f
        var released = false
        override fun move(seconds: Float, rate: Float) { moves++; lastSeconds = seconds; lastRate = rate }
        override fun release() { released = true }
    }

    private fun note(pitch: String, start: Int, duration: Int, hand: Hand = Hand.RIGHT, tied: Boolean = false) =
        Note(hand, Pitch.parse(pitch), start, duration, tiedToNext = tied)

    private class Run(notes: List<Note>) {
        val scratcher = Scratcher(notes)
        val voices = LinkedHashMap<Note, Fake>()
        var silenced: (Note) -> Boolean = { false }

        // 960 ticks por segundo para que las cuentas sean fáciles.
        fun at(tick: Float, frame: Float = 0.016f) = scratcher.update(
            tick, frame, { start, t -> (t - start) / 960f }, { silenced(it) },
            { n, _ -> Fake().also { voices[n] = it } },
        )
    }

    @Test
    fun theNotesUnderThePlayheadSoundAndTheOthersDont() {
        val a = note("C4", 0, 480); val b = note("E4", 480, 480); val c = note("G4", 0, 960)
        val r = Run(listOf(a, b, c))
        r.at(240f)
        assertEquals(setOf(a, c), r.voices.keys)
        r.at(600f)
        assertTrue("a ya terminó", r.voices.getValue(a).released)
        assertNotNull(r.voices[b])
        assertTrue(!r.voices.getValue(c).released)
        r.at(960f)
        assertTrue(r.voices.getValue(c).released && r.voices.getValue(b).released)
    }

    @Test
    fun theRateIsHowFastTheOffsetMoves() {
        val a = note("C4", 0, 1920)
        val r = Run(listOf(a))
        r.at(100f)
        r.at(196f, frame = 0.1f)
        val v = r.voices.getValue(a)
        assertEquals(196f / 960f, v.lastSeconds, 1e-4f)
        assertEquals((96f / 960f) / 0.1f, v.lastRate, 1e-3f)
        r.at(100f, frame = 0.1f)
        assertTrue("hacia atrás: velocidad negativa", v.lastRate < 0f)
    }

    @Test
    fun scrubbingBackwardsPastTheStartOfANoteReleasesIt() {
        val a = note("C4", 480, 480)
        val r = Run(listOf(a))
        r.at(600f)
        r.at(400f)
        assertTrue(r.voices.getValue(a).released)
    }

    @Test
    fun tiedNotesAreOneLongNote() {
        val first = note("C4", 0, 480, tied = true); val second = note("C4", 480, 480)
        val r = Run(listOf(first, second))
        r.at(240f)
        r.at(720f)
        assertEquals("una sola voz, sin reatacar en la unión", 1, r.voices.size)
        assertTrue(!r.voices.getValue(first).released)
        assertEquals(720f / 960f, r.voices.getValue(first).lastSeconds, 1e-4f)
    }

    @Test
    fun silencedHandsDontSound() {
        val right = note("C5", 0, 480); val left = note("C3", 0, 480, Hand.LEFT)
        val r = Run(listOf(right, left))
        r.silenced = { it.hand == Hand.LEFT }
        r.at(100f)
        assertEquals(setOf(right), r.voices.keys)
    }

    @Test
    fun stopReleasesEverything() {
        val r = Run(listOf(note("C4", 0, 480), note("E4", 0, 480)))
        r.at(100f)
        r.scratcher.stop()
        assertTrue(r.voices.values.all { it.released })
    }

    @Test
    fun tooManyNotesKeepTheNewest() {
        val notes = (0 until 30).map { note("C4", it, 4800) }
        val r = Run(notes)
        r.at(100f)
        assertEquals(Scratcher.MAX_VOICES, r.voices.size)
        assertTrue(r.voices.keys.all { it.startTick >= 30 - Scratcher.MAX_VOICES })
    }
}
