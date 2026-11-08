package com.federomano.pianoapp.score

/** Un compás calculado a partir de las firmas de compás y armaduras. */
data class Measure(
    /** Número de compás, desde 1. */
    val number: Int,
    val startTick: Int,
    val endTick: Int,
    val timeSignature: TimeSignatureChange,
    /** Armadura vigente en este compás (ver [KeySignatureChange.fifths]). */
    val keyFifths: Int,
)

/** Notas de una misma mano que empiezan juntas y duran lo mismo. Con una sola nota no es acorde. */
data class Chord(
    val hand: Hand,
    val startTick: Int,
    val durationTicks: Int,
    /** Ordenadas de grave a aguda. */
    val notes: List<Note>,
    /** Grupo irregular de la figura (tresillo, etc.); null si es una figura común. */
    val tuplet: Tuplet? = null,
) {
    val endTick: Int get() = startTick + durationTicks
    val isChord: Boolean get() = notes.size > 1
}

/** Compases de la partitura, de 1 hasta el que contiene el final. Siempre hay al menos uno. */
fun Score.measures(): List<Measure> {
    val result = mutableListOf<Measure>()
    val end = lengthTicks
    var tick = 0
    var number = 1
    do {
        val signature = timeSignatures.lastOrNull { it.startTick <= tick } ?: TimeSignatureChange(0, 4, 4)
        val key = keySignatures.lastOrNull { it.startTick <= tick }?.fifths ?: 0
        val length = signature.measureTicks
        result += Measure(number, tick, tick + length, signature, key)
        tick += length
        number++
    } while (tick < end)
    return result
}

/** Acordes y notas sueltas, ordenados por inicio (y mano derecha antes que izquierda). */
fun Score.chords(hand: Hand? = null): List<Chord> =
    notes.filter { hand == null || it.hand == hand }
        .groupBy { Triple(it.hand, it.startTick, it.durationTicks) }
        .map { (key, group) -> Chord(key.first, key.second, key.third, group.sortedBy { it.pitch.midi }, group.first().tuplet) }
        .sortedWith(compareBy({ it.startTick }, { it.hand }))

/** Rango del piano: de La0 (MIDI 21) a Do8 (MIDI 108). */
val PIANO_RANGE = 21..108

/**
 * Revisa las reglas que dependen de varios elementos a la vez. Devuelve los problemas en español;
 * una lista vacía significa que la partitura está bien.
 */
fun Score.validate(): List<String> {
    val errors = mutableListOf<String>()

    fun checkOrder(name: String, ticks: List<Int>) {
        if (ticks.firstOrNull() != 0) errors += "$name: el primero tiene que estar en el tick 0."
        if (ticks.zipWithNext().any { (a, b) -> b <= a }) {
            errors += "$name: tienen que estar ordenados y sin repetir posición."
        }
    }
    checkOrder("Firmas de compás", timeSignatures.map { it.startTick })
    checkOrder("Armaduras", keySignatures.map { it.startTick })
    checkOrder("Cambios de tempo", tempos.map { it.startTick })

    val measures = measures()
    val measureStarts = measures.map { it.startTick }.toSet()
    fun measureAt(tick: Int) = measures.first { tick >= it.startTick && tick < it.endTick }

    timeSignatures.filter { it.startTick < lengthTicks && it.startTick !in measureStarts }.forEach {
        errors += "El cambio de compás del tick ${it.startTick} no cae al inicio de un compás."
    }
    keySignatures.filter { it.startTick < lengthTicks && it.startTick !in measureStarts }.forEach {
        errors += "El cambio de armadura del tick ${it.startTick} no cae al inicio de un compás."
    }

    fun describe(note: Note) = "La nota ${note.pitch} (${note.hand.label}, compás ${measureAt(note.startTick).number})"

    for (note in notes) {
        if (note.pitch.midi !in PIANO_RANGE) {
            errors += "${describe(note)} está fuera del rango del piano (de A0 a C8)."
        }
        if (note.endTick > measureAt(note.startTick).endTick) {
            errors += "${describe(note)} cruza la barra de compás: partila en dos y unilas con una ligadura."
        }
        if (note.tiedToNext && notes.none {
                it.hand == note.hand && it.pitch.midi == note.pitch.midi && it.startTick == note.endTick
            }
        ) {
            errors += "${describe(note)} está ligada, pero no hay una nota igual que empiece justo después."
        }
    }

    for (rest in rests) {
        val measure = measureAt(rest.startTick)
        if (rest.endTick > measure.endTick) {
            errors += "Un silencio de la ${rest.hand.label} en el compás ${measure.number} cruza la barra de compás: partilo en dos."
        }
    }

    notes.groupBy { it.hand to it.pitch.midi }.values.forEach { sameKey ->
        sameKey.sortedBy { it.startTick }.zipWithNext().forEach { (a, b) ->
            if (b.startTick < a.endTick) {
                errors += "Dos notas ${a.pitch} de la ${a.hand.label} se pisan en el compás ${measureAt(b.startTick).number}."
            }
        }
    }

    // Duraciones: figuras comunes, o un grupo irregular cuya figura escrita sea una figura común.
    fun durationProblem(durationTicks: Int, tuplet: Tuplet?): String? {
        if (tuplet == null) {
            return if (durationTicks in Ticks.STANDARD) null
            else "dura $durationTicks ticks, que no es una figura que la app sepa dibujar. Si es un tresillo o similar, " +
                "agregá \"tuplet\": \"3:2\" (3 en el tiempo de 2) y dejá la duración real en ticks."
        }
        val written = tuplet.written(durationTicks)
        return if (written != null && written in Ticks.STANDARD) null
        else "con el grupo $tuplet no puede durar $durationTicks ticks: la figura escrita tendría que ser redonda, blanca, negra, " +
            "corchea o semicorchea (con hasta dos puntillos)."
    }
    for (note in notes) {
        durationProblem(note.durationTicks, note.tuplet)?.let { errors += "${describe(note)} $it" }
    }
    for (rest in rests) {
        durationProblem(rest.durationTicks, rest.tuplet)?.let {
            errors += "Un silencio de la ${rest.hand.label} (compás ${measureAt(rest.startTick).number}) $it"
        }
    }
    for (chord in chords().filter { it.isChord }) {
        if (chord.notes.map { it.tuplet }.distinct().size > 1) {
            errors += "Las notas del acorde de la ${chord.hand.label} del compás ${measureAt(chord.startTick).number} " +
                "tienen que tener el mismo grupo irregular (\"tuplet\")."
        }
    }

    // Grupos irregulares completos: figuras seguidas del mismo grupo, dentro de un compás, que sumen grupos enteros.
    class Figure(val startTick: Int, val durationTicks: Int, val tuplet: Tuplet?)
    for (hand in Hand.entries) {
        val figures = (chords(hand).map { Figure(it.startTick, it.durationTicks, it.tuplet) } +
            rests.filter { it.hand == hand }.map { Figure(it.startTick, it.durationTicks, it.tuplet) })
            .sortedBy { it.startTick }
        var run = mutableListOf<Figure>()
        fun closeRun() {
            val tuplet = run.firstOrNull()?.tuplet
            if (tuplet != null) {
                val smallest = run.minOf { tuplet.written(it.durationTicks) ?: it.durationTicks }
                val total = run.sumOf { it.durationTicks }
                if (total % (tuplet.normal * smallest) != 0) {
                    errors += "Un grupo irregular $tuplet de la ${hand.label} en el compás ${measureAt(run.first().startTick).number} " +
                        "está incompleto: las figuras suman $total ticks y tendrían que formar grupos enteros " +
                        "(de ${tuplet.normal * smallest} ticks)."
                }
            }
            run = mutableListOf()
        }
        for (figure in figures) {
            val last = run.lastOrNull()
            val continues = last != null && last.tuplet != null && last.tuplet == figure.tuplet &&
                last.startTick + last.durationTicks == figure.startTick && figure.startTick !in measureStarts
            if (!continues) closeRun()
            run += figure
        }
        closeRun()
    }

    return errors
}
