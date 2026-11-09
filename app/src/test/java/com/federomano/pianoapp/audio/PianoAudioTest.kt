package com.federomano.pianoapp.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PianoMappingTest {
    @Test
    fun everyPianoKeyUsesASampleAtMostOneAndAHalfSemitonesAway() {
        for (midi in PianoMapping.LOWEST..PianoMapping.HIGHEST) {
            val p = PianoMapping.pick(midi)
            assertTrue("nota $midi", p.sampleNote in PianoMapping.sampleNotes)
            assertTrue("nota $midi: ${p.sampleNote}", abs(midi - p.sampleNote) <= 1)
        }
    }

    @Test
    fun rateIsOneOnTheSampleAndShiftsByAnEqualTemperedStep() {
        assertEquals(1f, PianoMapping.pick(60).rate, 1e-6f)
        assertEquals(Math.pow(2.0, 1 / 12.0).toFloat(), PianoMapping.pick(61).rate, 1e-5f)
        assertEquals(Math.pow(2.0, -1 / 12.0).toFloat(), PianoMapping.pick(59).rate, 1e-5f)
    }

    @Test
    fun velocityPicksTheLayerAndRaisesTheGain() {
        assertTrue(!PianoMapping.pick(60, 40).loud)
        assertTrue(PianoMapping.pick(60, 110).loud)
        assertTrue(PianoMapping.pick(60, 30).gain < PianoMapping.pick(60, 80).gain)
        assertTrue(PianoMapping.pick(60, 90).gain < PianoMapping.pick(60, 127).gain)
        assertTrue(PianoMapping.pick(60, 127).gain <= 1f)
    }

    @Test
    fun bassIsCutOnlyBelowE3AndMoreAtLowerLevels() {
        for (level in 0 until PianoMapping.BASS_LEVELS) assertEquals(1f, PianoMapping.bassFactor(52, level), 1e-6f)
        for (note in 21..108) assertEquals(1f, PianoMapping.bassFactor(note, 0), 1e-6f)
        assertEquals(0.25f, PianoMapping.bassFactor(21, 1), 0.01f)   // -12 dB
        assertEquals(0.063f, PianoMapping.bassFactor(21, 2), 0.01f)  // -24 dB
        assertTrue(PianoMapping.bassFactor(30, 1) > PianoMapping.bassFactor(24, 1))
        assertTrue(PianoMapping.bassFactor(36, 2) < PianoMapping.bassFactor(36, 1))
        assertEquals(PianoMapping.pick(60, 90, 0).gain, PianoMapping.pick(60, 90, 2).gain, 1e-6f)
        assertTrue(PianoMapping.pick(30, 110, 1).gain < PianoMapping.pick(30, 110, 0).gain)
    }

    @Test
    fun dynamicsSurviveTheBassCut() {
        for (level in 0 until PianoMapping.BASS_LEVELS) assertTrue(PianoMapping.pick(30, 50, level).gain < PianoMapping.pick(30, 110, level).gain)
    }

    @Test
    fun softLimiterKeepsNormalLevelsAndNeverReachesFullScale() {
        assertEquals(0.5f, Mixer.softLimit(0.5f), 0f)
        assertEquals(-0.3f, Mixer.softLimit(-0.3f), 0f)
        assertTrue(Mixer.softLimit(3f) < 1f)
        assertEquals(-Mixer.softLimit(2f), Mixer.softLimit(-2f), 0f)
        assertTrue(Mixer.softLimit(0.9f) < Mixer.softLimit(1.5f))
    }

    @Test
    fun outOfRangeNotesAreClamped() {
        assertEquals(21, PianoMapping.pick(0).sampleNote)
        assertEquals(108, PianoMapping.pick(127).sampleNote)
    }

    @Test
    fun fileNamesMatchTheAssets() {
        assertEquals("piano/p060s.pcm", PianoMapping.pick(60, 40).fileName)
        assertEquals("piano/p060f.pcm", PianoMapping.pick(60, 120).fileName)
    }
}

class MixerTest {
    private fun ramp(n: Int, value: Short = 10000) = ShortArray(n) { value }

    private fun renderAll(mixer: Mixer, frames: Int): FloatArray {
        val out = FloatArray(frames)
        var done = 0
        while (done < frames) {
            val block = FloatArray(minOf(256, frames - done))
            mixer.render(block, block.size)
            block.copyInto(out, done); done += block.size
        }
        return out
    }

    @Test
    fun aVoicePlaysItsSampleAndThenEnds() {
        val m = Mixer()
        m.noteOn(ramp(1000), 1f, 1f)
        val out = renderAll(m, 2000)
        assertEquals(10000 / 32768f, out[10], 1e-4f)
        assertEquals(0f, out[1500], 0f)
        assertEquals(0, m.activeVoices)
    }

    @Test
    fun doubleRateEndsInHalfTheTime() {
        val m = Mixer()
        m.noteOn(ramp(1000), 2f, 1f)
        val out = renderAll(m, 1000)
        assertTrue(out[400] > 0f)
        assertEquals(0f, out[600], 0f)
    }

    @Test
    fun gainScalesTheLevel() {
        val m = Mixer()
        m.noteOn(ramp(1000), 1f, 0.5f)
        assertEquals(0.5f * 10000 / 32768f, renderAll(m, 100)[5], 1e-4f)
    }

    @Test
    fun noteOffFadesToSilenceAndFreesTheVoice() {
        val m = Mixer()
        val id = m.noteOn(ramp(PianoMapping.SAMPLE_RATE * 5), 1f, 1f)
        renderAll(m, 1000)
        m.noteOff(id)
        val out = renderAll(m, PianoMapping.SAMPLE_RATE * 2)
        assertTrue("baja de a poco", out[100] < out[0] && out[1000] < out[100])
        assertEquals(0f, out.last(), 0f)
        assertEquals(0, m.activeVoices)
    }

    @Test
    fun releaseTakesAboutTheConfiguredTime() {
        val m = Mixer()
        val id = m.noteOn(ramp(PianoMapping.SAMPLE_RATE * 5), 1f, 1f)
        m.noteOff(id)
        val out = renderAll(m, PianoMapping.SAMPLE_RATE * 2)
        val silentAt = out.indexOfFirst { it == 0f } / PianoMapping.SAMPLE_RATE.toFloat()
        assertEquals(Mixer.RELEASE_SECONDS * 6.9f, silentAt, 0.05f) // exp(-t/τ)=0.001 para t≈6,9τ
    }

    @Test
    fun chordsAreSoftLimitedInsteadOfClipping() {
        val m = Mixer()
        repeat(2) { m.noteOn(ramp(1000, 30000), 1f, 1f) }
        val out = renderAll(m, 100)
        assertTrue("el limitador no llega al tope", out[10] < 1f && out[10] > 0.9f)
        assertEquals(2, m.activeVoices)
    }

    @Test
    fun stealsTheOldestVoiceWhenThereAreTooMany() {
        val m = Mixer(maxVoices = 4)
        repeat(6) { m.noteOn(ramp(10000), 1f, 0.1f) }
        renderAll(m, 10)
        assertEquals(4, m.activeVoices)
    }
}
