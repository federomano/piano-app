package com.federomano.pianoapp.library

import java.util.Locale

/** Resultado de importar un archivo, para mostrárselo a Fede. */
class ImportLine(val name: String, val result: ImportResult) {
    val ok: Boolean get() = result !is ImportResult.Failed

    /** Una frase corta de lo que pasó; si falló, sigue la lista de problemas. */
    val summary: String
        get() = when (val r = result) {
            is ImportResult.Added -> "Importada como «${r.entry.score.title}»."
            is ImportResult.AlreadyThere -> "Ya estaba en tu biblioteca («${r.entry.score.title}»)."
            is ImportResult.Failed -> "No se pudo importar:"
        }

    val problems: List<String> get() = (result as? ImportResult.Failed)?.problems.orEmpty()
}

/** "0:12", "1:05": minutos y segundos. */
fun formatDuration(seconds: Float): String {
    val total = Math.round(seconds).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}

/** Subtítulo de una partitura de la lista: compositor, nivel y duración, los que haya. */
fun subtitleOf(composer: String?, level: String?, seconds: Float): String =
    listOfNotNull(composer, level, formatDuration(seconds)).joinToString(" · ")

/**
 * La partitura que se abre directo al recibir archivos: solo si llegó **un** archivo y entró bien
 * (o ya estaba en la biblioteca). Con varios archivos o con errores se muestra el informe.
 */
fun directOpenEntry(lines: List<ImportLine>): LibraryEntry? =
    when (val r = lines.singleOrNull()?.result) {
        is ImportResult.Added -> r.entry
        is ImportResult.AlreadyThere -> r.entry
        else -> null
    }
