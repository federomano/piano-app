package com.federomano.pianoapp.audio

import com.federomano.pianoapp.ui.levelMessage
import com.federomano.pianoapp.ui.meterFraction
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-30: el medidor de nivel del micrófono y sus avisos. */
class LevelAnalyzerTest {
    private val rate = 44100
    private val block = 1024

    private fun sine(amplitude: Double, frames: Int = block) =
        ShortArray(frames) { (sin(2 * PI * 440 * it / rate) * amplitude * 32767).toInt().toShort() }

    private fun squareFullScale() = ShortArray(block) { if (it % 40 < 20) Short.MAX_VALUE else Short.MIN_VALUE }

    /** Alimenta [seconds] segundos de la señal dada y devuelve la última lectura. */
    private fun feed(a: LevelAnalyzer, seconds: Double, make: () -> ShortArray): LevelReading {
        var last: LevelReading? = null
        repeat((seconds * rate / block).toInt().coerceAtLeast(1)) { last = a.process(make(), block) }
        return last!!
    }

    @Test
    fun silenceIsSilent() {
        val r = feed(LevelAnalyzer(rate), 1.0) { ShortArray(block) }
        assertEquals(LevelStatus.SILENT, r.status)
        assertTrue(r.peakDb <= -100f)
    }

    @Test
    fun aVeryQuietSignalIsTooLow() {
        val r = feed(LevelAnalyzer(rate), 1.0) { sine(0.01) } // unos -40 dB
        assertEquals(LevelStatus.TOO_LOW, r.status)
        assertEquals(-40f, r.peakDb, 1f)
    }

    @Test
    fun aNormalSignalIsGood() {
        val r = feed(LevelAnalyzer(rate), 1.0) { sine(0.3) }
        assertEquals(LevelStatus.GOOD, r.status)
        assertEquals(-10.5f, r.peakDb, 1f)
    }

    @Test
    fun aSignalGluedToTheCeilingIsClipping() {
        val r = feed(LevelAnalyzer(rate), 0.5) { squareFullScale() }
        assertEquals(LevelStatus.CLIPPING, r.status)
    }

    @Test
    fun aStrongButNotClippedSignalIsNotClipping() {
        val r = feed(LevelAnalyzer(rate), 0.5) { sine(0.8) } // unos -2 dB, sin llegar al techo
        assertEquals(LevelStatus.GOOD, r.status)
    }

    @Test
    fun theWarningGoesAwayOnceTheSignalIsFine() {
        val a = LevelAnalyzer(rate)
        feed(a, 0.2) { squareFullScale() }
        val r = feed(a, 2.5) { sine(0.3) }
        assertEquals(LevelStatus.GOOD, r.status)
    }

    @Test
    fun silenceBetweenNotesDoesNotTriggerTheWarning() {
        val a = LevelAnalyzer(rate)
        feed(a, 0.3) { sine(0.3) }
        val r = feed(a, 1.0) { ShortArray(block) }
        assertEquals("un segundo de pausa entre notas no es 'no llega sonido'", LevelStatus.GOOD, r.status)
    }

    @Test
    fun afterLongSilenceTheLoudPastIsForgotten() {
        val a = LevelAnalyzer(rate)
        feed(a, 0.3) { sine(0.3) }
        val r = feed(a, 2.5) { ShortArray(block) }
        assertEquals(LevelStatus.SILENT, r.status)
    }

    @Test
    fun holdKeepsTheRecentPeakWhileThePeakFalls() {
        val a = LevelAnalyzer(rate)
        feed(a, 0.2) { sine(0.5) }
        val r = feed(a, 0.2) { sine(0.01) }
        assertTrue("el pico actual bajó", r.peakDb < -35f)
        assertEquals("la marca recuerda el pico reciente", -6f, r.holdDb, 1f)
    }

    @Test
    fun resetForgetsEverything() {
        val a = LevelAnalyzer(rate)
        feed(a, 0.5) { squareFullScale() }
        a.reset()
        assertEquals(LevelStatus.SILENT, feed(a, 0.1) { ShortArray(block) }.status)
    }

    @Test
    fun decibelsAreRelativeToTheCeiling() {
        assertEquals(0f, LevelAnalyzer.toDb(32768.0), 0.01f)
        assertEquals(-6.02f, LevelAnalyzer.toDb(16384.0), 0.01f)
        assertEquals(LevelAnalyzer.FLOOR_DB, LevelAnalyzer.toDb(0.0), 0f)
    }

    @Test
    fun theMeterMapsMinus60ToZeroAndZeroDbToFull() {
        assertEquals(0f, meterFraction(-60f), 1e-6f)
        assertEquals(0.5f, meterFraction(-30f), 1e-6f)
        assertEquals(1f, meterFraction(0f), 1e-6f)
        assertEquals(0f, meterFraction(-120f), 1e-6f)
        assertEquals(1f, meterFraction(6f), 1e-6f)
    }

    @Test
    fun everyStatusHasItsOwnMessage() {
        val messages = LevelStatus.entries.map { levelMessage(it) }
        assertEquals(messages.size, messages.toSet().size)
        assertTrue(messages.all { it.isNotBlank() })
        assertNotEquals(levelMessage(LevelStatus.TOO_LOW), levelMessage(LevelStatus.CLIPPING))
    }
}
