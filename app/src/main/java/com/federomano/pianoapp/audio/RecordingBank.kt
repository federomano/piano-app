package com.federomano.pianoapp.audio

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Qué suena en una grabación del banco de pruebas, y cómo se grabó. Es lo que se guarda en `labels.json`. */
@Serializable
class TakeLabel(
    val id: String,
    val piano: String,
    val group: String,
    val instruction: String,
    /** Momentos en orden; cada uno es el conjunto de notas (MIDI) que suenan juntas. Vacío = silencio. */
    val expected: List<List<Int>>,
    /** Fuente del micrófono que se usó (UNPROCESSED o VOICE_RECOGNITION). */
    val source: String,
    val sampleRate: Int,
    val seconds: Double,
    val file: String,
)

@Serializable
class LabelFile(val version: Int = 1, val takes: List<TakeLabel>)

/**
 * Las grabaciones de prueba que Fede hace con la app (PIA-38): una carpeta por piano con un .wav por toma y un `labels.json` que
 * dice qué suena en cada una. Se guardan solo cuando Fede las pide. [root] es la carpeta privada de la app.
 */
class RecordingBank(private val root: File) {
    private val json = Json { prettyPrint = true }

    private fun dir(piano: PianoKind) = File(root, piano.folder)

    private fun labelsFile(piano: PianoKind) = File(dir(piano), LABELS_NAME)

    fun wavFile(piano: PianoKind, takeId: String) = File(dir(piano), "$takeId.wav")

    /** Guarda (o reemplaza) la grabación de una toma y actualiza las etiquetas. */
    fun save(piano: PianoKind, take: Take, samples: ShortArray, count: Int, source: String, sampleRate: Int) {
        dir(piano).mkdirs()
        wavFile(piano, take.id).outputStream().buffered().use { WavWriter.write(it, samples, count, sampleRate) }
        val label = TakeLabel(
            id = take.id, piano = piano.folder, group = take.group, instruction = take.instruction, expected = take.expected,
            source = source, sampleRate = sampleRate, seconds = count.toDouble() / sampleRate, file = "${piano.folder}/${take.id}.wav",
        )
        val others = labels(piano).filter { it.id != take.id }
        writeLabels(piano, (others + label).sortedBy { orderOf(it.id) })
    }

    fun labels(piano: PianoKind): List<TakeLabel> {
        val f = labelsFile(piano)
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString<LabelFile>(f.readText()).takes }.getOrDefault(emptyList())
            .filter { wavFile(piano, it.id).exists() }
    }

    /** Las tomas de la lista que ya tienen grabación. */
    fun recordedIds(piano: PianoKind): Set<String> = labels(piano).map { it.id }.toSet()

    fun delete(piano: PianoKind, takeId: String) {
        wavFile(piano, takeId).delete()
        writeLabels(piano, labels(piano).filter { it.id != takeId })
    }

    /** Arma un .zip con las grabaciones de un piano y sus etiquetas, listo para compartir. Devuelve la cantidad de tomas. */
    fun exportZip(piano: PianoKind, target: File): Int {
        val labels = labels(piano)
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("${piano.folder}/$LABELS_NAME"))
            zip.write(json.encodeToString(LabelFile(takes = labels)).toByteArray())
            zip.closeEntry()
            for (label in labels) {
                zip.putNextEntry(ZipEntry(label.file))
                wavFile(piano, label.id).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return labels.size
    }

    private fun writeLabels(piano: PianoKind, labels: List<TakeLabel>) {
        dir(piano).mkdirs()
        labelsFile(piano).writeText(json.encodeToString(LabelFile(takes = labels)))
    }

    private fun orderOf(id: String) = RecordingScript.takes.indexOfFirst { it.id == id }.let { if (it < 0) Int.MAX_VALUE else it }

    companion object {
        const val LABELS_NAME = "labels.json"
    }
}
