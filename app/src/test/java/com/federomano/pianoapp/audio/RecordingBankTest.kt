package com.federomano.pianoapp.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-38: lista guiada, archivos WAV y banco de grabaciones de prueba. */
class RecordingBankTest {
    private fun tempDir(): File = Files.createTempDirectory("banco").toFile().also { it.deleteOnExit() }

    @Test
    fun theScriptHasUniqueIdsAndRealNotes() {
        val ids = RecordingScript.takes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (take in RecordingScript.takes) {
            assertTrue(take.id, Regex("[a-z0-9-]+").matches(take.id))
            assertTrue(take.instruction.isNotBlank())
            take.expected.flatten().forEach { assertTrue("${take.id}: $it", it in 21..108) }
            assertTrue(take.seconds in 3..30)
        }
    }

    @Test
    fun theScriptCoversWhatTheDetectionNeeds() {
        val groups = RecordingScript.takes.map { it.group }.toSet()
        assertTrue(groups.containsAll(listOf("Notas sueltas", "Acordes", "Intervalos", "Pasajes", "Silencio", "Dos manos", "Volumen")))
        assertTrue("hay silencio para medir falsos positivos", RecordingScript.takes.any { it.expected.isEmpty() })
        assertTrue("la octava, que es lo que más se confunde", RecordingScript.byId("octava-do3-do4") != null)
    }

    @Test
    fun notesAreNamedInDoReMi() {
        assertEquals("Do4", RecordingScript.noteName(60))
        assertEquals("Sol3", RecordingScript.noteName(55))
        assertEquals("Do2", RecordingScript.noteName(36))
        assertEquals("Fa#4", RecordingScript.noteName(66))
    }

    @Test
    fun aWavReadsBackIdentical() {
        val samples = ShortArray(1000) { (it * 37 - 16000).toShort() }
        val out = ByteArrayOutputStream()
        WavWriter.write(out, samples, samples.size, 44100)
        val bytes = out.toByteArray()
        assertEquals(WavWriter.HEADER_BYTES + 2000, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        val (back, rate) = WavWriter.read(bytes)
        assertEquals(44100, rate)
        assertTrue(samples.contentEquals(back))
    }

    @Test
    fun savingKeepsTheAudioAndItsLabel() {
        val bank = RecordingBank(tempDir())
        val take = RecordingScript.byId("nota-do4")!!
        val samples = ShortArray(44100 * 2) { 100 }
        bank.save(PianoKind.CASIO, take, samples, samples.size, "VOICE_RECOGNITION", 44100)
        assertTrue(bank.wavFile(PianoKind.CASIO, "nota-do4").exists())
        val label = bank.labels(PianoKind.CASIO).single()
        assertEquals("nota-do4", label.id)
        assertEquals("casio", label.piano)
        assertEquals(listOf(listOf(60)), label.expected)
        assertEquals("VOICE_RECOGNITION", label.source)
        assertEquals(2.0, label.seconds, 1e-9)
        assertEquals(setOf("nota-do4"), bank.recordedIds(PianoKind.CASIO))
        assertTrue("el otro piano no se mezcla", bank.recordedIds(PianoKind.ACOUSTIC).isEmpty())
    }

    @Test
    fun recordingAgainReplacesTheOldTake() {
        val bank = RecordingBank(tempDir())
        val take = RecordingScript.byId("nota-do4")!!
        bank.save(PianoKind.ACOUSTIC, take, ShortArray(100), 100, "UNPROCESSED", 44100)
        bank.save(PianoKind.ACOUSTIC, take, ShortArray(300), 300, "UNPROCESSED", 44100)
        assertEquals(1, bank.labels(PianoKind.ACOUSTIC).size)
        assertEquals(300.0 / 44100, bank.labels(PianoKind.ACOUSTIC).single().seconds, 1e-9)
    }

    @Test
    fun labelsFollowTheOrderOfTheScript() {
        val bank = RecordingBank(tempDir())
        for (id in listOf("silencio", "nota-do2", "acorde-do-mayor")) {
            bank.save(PianoKind.ACOUSTIC, RecordingScript.byId(id)!!, ShortArray(10), 10, "UNPROCESSED", 44100)
        }
        assertEquals(listOf("nota-do2", "acorde-do-mayor", "silencio"), bank.labels(PianoKind.ACOUSTIC).map { it.id })
    }

    @Test
    fun deletingRemovesTheAudioAndTheLabel() {
        val bank = RecordingBank(tempDir())
        bank.save(PianoKind.ACOUSTIC, RecordingScript.byId("nota-do4")!!, ShortArray(10), 10, "UNPROCESSED", 44100)
        bank.delete(PianoKind.ACOUSTIC, "nota-do4")
        assertFalse(bank.wavFile(PianoKind.ACOUSTIC, "nota-do4").exists())
        assertTrue(bank.labels(PianoKind.ACOUSTIC).isEmpty())
    }

    @Test
    fun theZipHasTheAudiosAndTheLabelsOfThatPianoOnly() {
        val bank = RecordingBank(tempDir())
        bank.save(PianoKind.ACOUSTIC, RecordingScript.byId("nota-do4")!!, ShortArray(100), 100, "UNPROCESSED", 44100)
        bank.save(PianoKind.ACOUSTIC, RecordingScript.byId("nota-sol4")!!, ShortArray(100), 100, "UNPROCESSED", 44100)
        bank.save(PianoKind.CASIO, RecordingScript.byId("nota-do4")!!, ShortArray(100), 100, "UNPROCESSED", 44100)
        val zipFile = File(tempDir(), "x.zip")
        assertEquals(2, bank.exportZip(PianoKind.ACOUSTIC, zipFile))
        ZipFile(zipFile).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertEquals(setOf("acustico/labels.json", "acustico/nota-do4.wav", "acustico/nota-sol4.wav"), names)
            val labels = Json.decodeFromString<LabelFile>(zip.getInputStream(zip.getEntry("acustico/labels.json")).readBytes().decodeToString())
            assertEquals(2, labels.takes.size)
        }
    }

    /**
     * El banco de verdad (lo que Fede grabó y está en el repo): cada audio tiene su etiqueta y cada etiqueta su audio, las etiquetas
     * coinciden con la lista guiada y los archivos son WAV válidos. Si todavía no hay audios, no hay nada que revisar.
     */
    @Test
    fun theRealBankIsConsistent() {
        val root = javaClass.classLoader?.getResource("audios")?.let { File(it.toURI()) } ?: return
        for (piano in PianoKind.entries) {
            val dir = File(root, piano.folder)
            if (!dir.isDirectory) continue
            val labelsFile = File(dir, RecordingBank.LABELS_NAME)
            assertTrue("falta labels.json en ${piano.folder}", labelsFile.exists())
            val labels = Json.decodeFromString<LabelFile>(labelsFile.readText()).takes
            val wavs = dir.listFiles { f -> f.extension == "wav" }!!.map { it.nameWithoutExtension }.toSet()
            assertEquals("audios sin etiqueta o etiquetas sin audio en ${piano.folder}", wavs, labels.map { it.id }.toSet())
            for (label in labels) {
                val take = RecordingScript.byId(label.id)
                assertTrue("la toma ${label.id} ya no está en la lista guiada", take != null)
                assertEquals(take!!.expected, label.expected)
                assertEquals(piano.folder, label.piano)
                val (samples, rate) = WavWriter.read(File(dir, "${label.id}.wav").readBytes())
                assertEquals(label.sampleRate, rate)
                assertEquals(label.seconds, samples.size.toDouble() / rate, 1e-6)
            }
        }
    }
}
