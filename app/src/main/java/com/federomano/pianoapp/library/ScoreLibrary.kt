package com.federomano.pianoapp.library

import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.ScoreJson
import com.federomano.pianoapp.score.TempoMap
import com.federomano.pianoapp.score.validate
import java.io.File
import java.text.Normalizer

/** Una partitura guardada en la biblioteca. [id] es el nombre del archivo, sin la extensión. */
class LibraryEntry(val id: String, val score: Score, val modified: Long) {
    /** Cuánto dura a velocidad normal, en segundos. */
    val seconds: Float get() = TempoMap(score.tempos).secondsBetween(0f, score.lengthTicks.toFloat())
}

sealed interface ImportResult {
    class Added(val entry: LibraryEntry) : ImportResult
    /** El archivo ya estaba en la biblioteca (misma partitura): no se agrega otra copia. */
    class AlreadyThere(val entry: LibraryEntry) : ImportResult
    /** No se pudo importar; [problems] dice por qué, en español. */
    class Failed(val problems: List<String>) : ImportResult
}

/**
 * Biblioteca local: cada partitura es un archivo JSON dentro de [dir] (la carpeta privada de la app).
 * Sobrevive a cerrar la app y a instalar una versión nueva. No necesita permisos del sistema.
 */
class ScoreLibrary(private val dir: File) {
    init { dir.mkdirs() }

    /** Las más recientes primero. Un archivo ilegible se ignora. */
    fun list(): List<LibraryEntry> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty()
            .mapNotNull { file ->
                runCatching { LibraryEntry(file.nameWithoutExtension, ScoreJson.decode(file.readText()), file.lastModified()) }.getOrNull()
            }
            .sortedWith(compareByDescending<LibraryEntry> { it.modified }.thenBy { it.id })

    /** Revisa el texto de un archivo de partitura y, si está bien, lo guarda. */
    fun import(text: String): ImportResult {
        if (text.toByteArray().size > MAX_BYTES) {
            return ImportResult.Failed(listOf("El archivo es demasiado grande para ser una partitura (más de ${MAX_BYTES / 1_000_000} MB)."))
        }
        val score = try {
            ScoreJson.decode(text.removePrefix("﻿"))
        } catch (e: IllegalArgumentException) {
            return ImportResult.Failed(listOf("No tiene el formato de una partitura de la app: ${shorten(e.message)}"))
        }
        val problems = score.validate()
        if (problems.isNotEmpty()) return ImportResult.Failed(problems)

        list().firstOrNull { it.score == score }?.let { return ImportResult.AlreadyThere(it) }

        val id = freeId(slug(score.title))
        val file = File(dir, "$id.json")
        file.writeText(ScoreJson.encode(score))
        return ImportResult.Added(LibraryEntry(id, score, file.lastModified()))
    }

    /** Cambia el título. Devuelve false si el título está vacío o la partitura no existe. */
    fun rename(id: String, title: String): Boolean {
        val file = File(dir, "$id.json")
        if (title.isBlank() || !file.isFile) return false
        val score = runCatching { ScoreJson.decode(file.readText()) }.getOrNull() ?: return false
        file.writeText(ScoreJson.encode(score.copy(title = title.trim())))
        return true
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun freeId(base: String): String {
        var id = base
        var n = 2
        while (File(dir, "$id.json").exists()) id = "$base-${n++}"
        return id
    }

    private fun slug(title: String): String {
        val plain = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "partitura" }
    }

    private fun shorten(message: String?): String {
        val first = message.orEmpty().lineSequence().firstOrNull().orEmpty().trim()
        return if (first.length > 200) first.take(200) + "…" else first
    }

    companion object {
        const val MAX_BYTES = 2_000_000
    }
}
