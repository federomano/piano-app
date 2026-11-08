package com.federomano.pianoapp.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Los archivos de referencia de `docs/ejemplos` (los que se le muestran a Claude como modelo) tienen que
 * abrir siempre sin avisos. Tres de ellos son las partituras de ejemplo de la app: si cambian en el código,
 * se regeneran con `REGENERAR_EJEMPLOS=1 ./gradlew testDebugUnitTest --tests '*ReferenceFilesTest*'`.
 */
class ReferenceFilesTest {
    private val dir = File("../docs/ejemplos")

    private val generated = mapOf(
        "escala-do-mayor.json" to "escala",
        "himno-a-la-alegria.json" to "himno",
        "ejercicio-todo-lo-que-dibuja.json" to "ejercicio",
        "ejercicio-tresillos.json" to "tresillos",
        "ejercicio-dos-voces.json" to "voces",
        "ejercicio-anacrusa.json" to "anacrusa",
        "ejercicio-adornos.json" to "adornos",
        "ejercicio-dinamicas.json" to "dinamicas",
        "ejercicio-articulaciones.json" to "articulaciones",
    )

    @Test
    fun regenerateIfAsked() {
        if (System.getenv("REGENERAR_EJEMPLOS") != "1") return
        for ((file, id) in generated) {
            File(dir, file).writeText(format(ExampleScores.all.first { it.id == id }.create()))
        }
    }

    @Test
    fun everyReferenceFileOpensWithoutWarnings() {
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue("tiene que haber al menos 3 archivos de referencia", files.size >= 3)
        for (file in files) {
            val score = ScoreJson.decode(file.readText())
            assertEquals(file.name, emptyList<String>(), score.validate())
        }
    }

    @Test
    fun theGeneratedFilesMatchTheExampleScoresInTheApp() {
        for ((file, id) in generated) {
            val expected = ExampleScores.all.first { it.id == id }.create()
            assertEquals("$file está desactualizado", ordered(expected), ordered(ScoreJson.decode(File(dir, file).readText())))
        }
    }

    /** El orden de las listas no importa: se compara con las notas y silencios ordenados igual que al escribirlos. */
    private fun ordered(score: Score) = score.copy(
        notes = score.notes.sortedWith(compareBy({ it.hand }, { it.voice }, { it.startTick }, { it.pitch.midi })),
        rests = score.rests.sortedWith(compareBy({ it.hand }, { it.voice }, { it.startTick })),
    )

    /** Mismo JSON que `ScoreJson.encode`, pero con cada nota y silencio en una línea, como los escribiría una persona. */
    private fun format(score: Score): String {
        fun str(text: String) = JsonPrimitive(text).toString()
        fun <T> block(name: String, items: List<T>, groups: (T) -> Any = { 0 }, line: (T) -> String) = buildString {
            append("  \"$name\": [\n")
            var previous: Any? = null
            items.forEachIndexed { i, item ->
                val group = groups(item)
                if (i > 0 && group != previous) append("\n")
                previous = group
                append("    ").append(line(item)).append(if (i < items.lastIndex) ",\n" else "\n")
            }
            append("  ]")
        }
        val head = buildList {
            add("  \"formatVersion\": ${score.formatVersion}")
            add("  \"title\": ${str(score.title)}")
            score.composer?.let { add("  \"composer\": ${str(it)}") }
            score.level?.let { add("  \"level\": ${str(it)}") }
            score.goal?.let { add("  \"goal\": ${str(it)}") }
            if (score.pickupTicks > 0) add("  \"pickupTicks\": ${score.pickupTicks}")
        }
        val parts = head + listOf(
            block("timeSignatures", score.timeSignatures) { "{ \"startTick\": ${it.startTick}, \"numerator\": ${it.numerator}, \"denominator\": ${it.denominator} }" },
            block("keySignatures", score.keySignatures) { "{ \"startTick\": ${it.startTick}, \"fifths\": ${it.fifths} }" },
            block("tempos", score.tempos) { "{ \"startTick\": ${it.startTick}, \"bpm\": ${it.bpm} }" },
        ) + listOfNotNull(
            score.dynamics.takeIf { it.isNotEmpty() }?.let { list ->
                block("dynamics", list) { "{ \"startTick\": ${it.startTick}, \"level\": \"${it.level.name.lowercase()}\" }" }
            },
            score.hairpins.takeIf { it.isNotEmpty() }?.let { list ->
                block("hairpins", list) { "{ \"startTick\": ${it.startTick}, \"endTick\": ${it.endTick}, \"kind\": \"${it.kind.name.lowercase()}\" }" }
            },
        ) + listOf(
            block("notes", score.notes.sortedWith(compareBy({ it.hand }, { it.voice }, { it.startTick }, { it.pitch.midi })), { it.hand to it.voice }) {
                val extra = (if (it.tiedToNext) ", \"tiedToNext\": true" else "") + (it.finger?.let { f -> ", \"finger\": $f" } ?: "") + (it.tuplet?.let { t -> ", \"tuplet\": \"$t\"" } ?: "") + (if (it.voice != 1) ", \"voice\": ${it.voice}" else "") + (if (it.grace) ", \"grace\": true" else "") + (if (it.staccato) ", \"staccato\": true" else "") + (if (it.accent) ", \"accent\": true" else "")
                "{ \"hand\": \"${if (it.hand == Hand.RIGHT) "right" else "left"}\", \"pitch\": \"${it.pitch}\", \"startTick\": ${it.startTick}, \"durationTicks\": ${it.durationTicks}$extra }"
            },
            block("rests", score.rests.sortedWith(compareBy({ it.hand }, { it.voice }, { it.startTick })), { it.hand to it.voice }) {
                "{ \"hand\": \"${if (it.hand == Hand.RIGHT) "right" else "left"}\", \"startTick\": ${it.startTick}, \"durationTicks\": ${it.durationTicks}${it.tuplet?.let { t -> ", \"tuplet\": \"$t\"" } ?: ""}${if (it.voice != 1) ", \"voice\": ${it.voice}" else ""} }"
            },
        )
        return "{\n" + parts.joinToString(",\n") + "\n}\n"
    }
}
