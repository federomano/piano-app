package com.federomano.pianoapp.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-32: detectar si suena la nota esperada. */
class NoteDetectorTest {
    private val rate = 44100

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Una nota «de piano» sintética: armónicos que decaen, con una desafinación opcional en centésimas. */
    private fun note(midi: Int, cents: Double = 0.0, frames: Int = NoteDetector.WINDOW, gain: Double = 0.3): ShortArray {
        val f0 = hz(midi) * 2.0.pow(cents / 1200)
        return ShortArray(frames) { i ->
            val t = i.toDouble() / rate
            var v = 0.0
            for (h in 1..8) v += sin(2 * PI * f0 * h * t) / h.toDouble().pow(1.2)
            (v * gain * 0.5 * 32767).toInt().toShort()
        }
    }

    private fun mix(a: ShortArray, b: ShortArray) = ShortArray(a.size) { (a[it] + b[it]).coerceIn(-32768, 32767).toShort() }

    private fun detect(window: ShortArray, expected: Int) = NoteDetector.analyze(window, rate).detect(expected)

    @Test
    fun aPlayedNoteIsDetected() {
        for (m in listOf(48, 55, 60, 64, 67, 72, 79, 84)) {
            assertTrue("debería detectar ${RecordingScript.noteName(m)}", detect(note(m), m).sounding)
        }
    }

    @Test
    fun silenceDetectsNothing() {
        assertFalse(detect(ShortArray(NoteDetector.WINDOW), 60).sounding)
    }

    @Test
    fun theSameNoteInAnotherOctaveIsNotConfused() {
        assertFalse("tocó Do4 y se espera Do5", detect(note(60), 72).sounding)
        assertFalse("tocó Do4 y se espera Do3", detect(note(60), 48).sounding)
        assertFalse("tocó Do5 y se espera Do4", detect(note(72), 60).sounding)
    }

    @Test
    fun neighbourNotesAreNotConfused() {
        for (d in listOf(-2, -1, 1, 2)) assertFalse("tocó Do4 y se espera ${60 + d}", detect(note(60), 60 + d).sounding)
    }

    @Test
    fun aFifthIsNotTheRootNote() {
        assertFalse("tocó Sol4 y se espera Do4", detect(note(67), 60).sounding)
    }

    @Test
    fun anOutOfTunePianoStillCounts() {
        assertTrue(detect(note(60, cents = 25.0), 60).sounding)
        assertTrue(detect(note(60, cents = -25.0), 60).sounding)
    }

    @Test
    fun theExpectedNoteIsFoundInsideAChord() {
        val chord = mix(mix(note(60), note(64)), note(67))
        assertTrue(detect(chord, 64).sounding)
        assertFalse("el acorde no tiene Re", detect(chord, 62).sounding)
    }

    @Test
    fun streamingNeedsAFullWindowFirst() {
        val d = NoteDetector(rate)
        assertFalse(d.ready)
        assertFalse(d.detect(60).sounding)
        val block = note(60, frames = 1024)
        repeat(7) { d.process(block, block.size) }
        assertFalse("con 7168 muestras todavía no alcanza", d.ready)
        d.process(block, block.size)
        assertTrue(d.ready)
    }

    @Test
    fun streamingDetectsTheNoteFromTheMostRecentAudio() {
        val d = NoteDetector(rate)
        val all = note(72, frames = 4 * NoteDetector.WINDOW) // Do5 largo
        val tail = note(60, frames = 2 * NoteDetector.WINDOW) // y después Do4
        for (chunk in (all + tail).toList().chunked(1024)) d.process(chunk.toShortArray(), chunk.size)
        assertTrue("al final suena Do4", d.detect(60).sounding)
        assertFalse("y Do5 ya no", d.detect(72).sounding)
    }

    @Test
    fun theHopSaysWhenToAnalyseAgain() {
        val d = NoteDetector(rate)
        val block = ShortArray(1024)
        repeat(8) { d.process(block, block.size) }
        assertTrue(d.consumeFresh())
        assertFalse(d.consumeFresh())
        repeat(2) { d.process(block, block.size) }
        assertTrue(d.consumeFresh())
    }

    @Test
    fun oneDecisionIsFastEnoughToRunOnPhone() {
        val w = note(60)
        detect(w, 60) // calentar
        val start = System.nanoTime()
        repeat(10) { detect(w, 60) }
        val millis = (System.nanoTime() - start) / 1e6 / 10
        // En un celu es más lento que en esta máquina: se deja un margen grande para detectar cálculos desmedidos.
        assertTrue("tarda ${millis.toInt()} ms por análisis", millis < 60)
    }

    // ---- El banco real de Fede ----

    private class Bank(val takes: List<TakeLabel>, val dir: File) {
        fun samples(id: String) = WavWriter.read(File(dir, "$id.wav").readBytes()).first
    }

    private fun bank(piano: PianoKind): Bank? {
        val root = javaClass.classLoader?.getResource("audios")?.let { File(it.toURI()) } ?: return null
        val dir = File(root, piano.folder)
        val labels = File(dir, RecordingBank.LABELS_NAME)
        if (!labels.exists()) return null
        return Bank(Json.decodeFromString<LabelFile>(labels.readText()).takes, dir)
    }

    /** Dónde empieza a sonar la nota: el primer bloque que llega a la cuarta parte del máximo. */
    private fun onsetOf(x: ShortArray): Int {
        val hop = 512
        val rms = (0 until (x.size - hop) / hop).map { b ->
            var s = 0.0
            for (i in b * hop until (b + 1) * hop) s += (x[i] / 32768.0).let { it * it }
            sqrt(s / hop)
        }
        val limit = maxOf(rms.max() * 0.25, 1e-3)
        return rms.indexOfFirst { it > limit } * hop
    }

    private fun windowEndingAt(x: ShortArray, end: Int) =
        ShortArray(NoteDetector.WINDOW) { val i = end - NoteDetector.WINDOW + it; if (i in x.indices) x[i] else 0 }

    /**
     * Con las notas sueltas del piano acústico: después de tocarla (entre 0,2 y 0,8 s) tiene que detectarla en 90% o más, y
     * no tiene que dar «suena» para ninguna de las notas con las que se suele confundir ni en los silencios.
     */
    @Test
    fun itFindsTheNotesInTheRealBank() {
        val b = bank(PianoKind.ACOUSTIC) ?: return
        var positives = 0
        var found = 0
        var negatives = 0
        val falseAlarms = ArrayList<String>()
        val missed = ArrayList<String>()
        for (take in b.takes.filter { it.id.startsWith("nota-") && it.expected.size == 1 }) {
            val x = b.samples(take.id)
            val n = take.expected[0][0]
            val onset = onsetOf(x)
            for (delay in listOf(0.2, 0.35, 0.5, 0.8)) {
                val w = windowEndingAt(x, onset + (delay * rate).toInt())
                val spectrum = NoteDetector.analyze(w, rate)
                positives++
                if (spectrum.detect(n).sounding) found++ else missed += "${take.id}@$delay"
                for (d in NoteDetector.RIVALS) {
                    val m = n + d
                    if (m < 36 || m > 84) continue
                    negatives++
                    if (spectrum.detect(m).sounding) falseAlarms += "${take.id}@$delay como ${RecordingScript.noteName(m)}"
                }
            }
        }
        for (take in b.takes.filter { it.expected.isEmpty() }) {
            val x = b.samples(take.id)
            for (second in listOf(0.5, 1.5, 2.5, 3.5)) {
                val spectrum = NoteDetector.analyze(windowEndingAt(x, ((second + 0.19) * rate).toInt()), rate)
                for (m in 36..84 step 3) {
                    negatives++
                    if (spectrum.detect(m).sounding) falseAlarms += "${take.id}@$second como ${RecordingScript.noteName(m)}"
                }
            }
        }
        val rateFound = found.toDouble() / positives
        assertTrue("detectó $found de $positives (${(rateFound * 100).toInt()}%). Fallas: $missed", rateFound >= 0.9)
        assertTrue("falsas alarmas: $falseAlarms de $negatives", falseAlarms.size <= negatives / 100)
    }

    /** Las canciones: cada nota de la melodía tiene que aparecer, y las notas que no están en la melodía casi no. */
    @Test
    fun itFollowsTheMelodiesInTheRealBank() {
        val b = bank(PianoKind.ACOUSTIC) ?: return
        for (id in listOf("escala-do-mayor", "himno-alegria")) {
            val take = b.takes.firstOrNull { it.id == id } ?: continue
            val x = b.samples(id)
            val inSong = take.expected.flatten().toSet()
            val hits = HashMap<Int, Int>()
            var windows = 0
            var end = NoteDetector.WINDOW
            while (end <= x.size) {
                val spectrum = NoteDetector.analyze(windowEndingAt(x, end), rate)
                windows++
                for (m in 48..84) if (spectrum.detect(m).sounding) hits[m] = (hits[m] ?: 0) + 1
                end += 4410
            }
            for (m in inSong) assertTrue("$id: nunca detectó ${RecordingScript.noteName(m)}", (hits[m] ?: 0) >= 3)
            val stray = hits.filterKeys { it !in inSong }.values.sum()
            assertTrue("$id: $stray detecciones de notas que no están en la melodía, en $windows ventanas", stray <= windows / 10)
        }
    }

}
