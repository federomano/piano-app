package com.federomano.pianoapp.score

/** Un compás calculado a partir de las firmas de compás y armaduras. */
data class Measure(
    /** Número de compás: desde 1; la anacrusa (si hay) es el 0. */
    val number: Int,
    val startTick: Int,
    val endTick: Int,
    val timeSignature: TimeSignatureChange,
    /** Armadura vigente en este compás (ver [KeySignatureChange.fifths]). */
    val keyFifths: Int,
    /**
     * Cuántos ticks antes del inicio del compás "empieza" su grilla de tiempos. Solo la anacrusa lo usa: un compás de un
     * pulso que cierra un 4/4 tiene sus tiempos alineados con el final, no con el principio.
     */
    val gridOffset: Int = 0,
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
    /** Voz de la mano a la que pertenece (1 = principal). */
    val voice: Int = 1,
) {
    val endTick: Int get() = startTick + durationTicks
    val isChord: Boolean get() = notes.size > 1
}

/** Compases de la partitura, de 1 (o 0, si hay anacrusa) hasta el que contiene el final. Siempre hay al menos uno. */
fun Score.measures(): List<Measure> {
    val result = mutableListOf<Measure>()
    val end = lengthTicks
    var tick = 0
    var number = if (pickupTicks > 0) 0 else 1
    do {
        val signature = timeSignatures.lastOrNull { it.startTick <= tick } ?: TimeSignatureChange(0, 4, 4)
        val key = keySignatures.lastOrNull { it.startTick <= tick }?.fifths ?: 0
        val isPickup = pickupTicks > 0 && tick == 0
        val length = if (isPickup) minOf(pickupTicks, signature.measureTicks) else signature.measureTicks
        result += Measure(number, tick, tick + length, signature, key, if (isPickup) signature.measureTicks - length else 0)
        tick += length
        number++
    } while (tick < end)
    return result
}

/**
 * Acordes y notas sueltas, ordenados por inicio (y mano derecha antes que izquierda, voz 1 antes que 2).
 * Un acorde son notas de la misma mano y voz que empiezan juntas y duran lo mismo.
 */
fun Score.chords(hand: Hand? = null): List<Chord> =
    notes.filter { !it.grace && (hand == null || it.hand == hand) }
        .groupBy { listOf(it.hand, it.voice, it.startTick, it.durationTicks) }
        .map { (_, group) ->
            val first = group.first()
            Chord(first.hand, first.startTick, first.durationTicks, group.sortedBy { it.pitch.midi }, first.tuplet, first.voice)
        }
        .sortedWith(compareBy({ it.startTick }, { it.hand }, { it.voice }))

/** Las notas como suenan: un adorno se toca justo antes de su tiempo, con su duración corta. Es lo que lee el sonido. */
fun Score.playedNotes(): List<Note> = notes.map {
    when {
        it.grace -> it.copy(startTick = it.soundStartTick, durationTicks = it.soundEndTick - it.soundStartTick, grace = false)
        // Staccato: suena la mitad; el resto es silencio, y la siguiente nota arranca en su tick de siempre.
        it.staccato -> it.copy(durationTicks = maxOf(1, it.durationTicks / 2))
        else -> it
    }
}

/** Cuánto más fuerte suena una nota con acento que la dinámica vigente. */
const val ACCENT_BOOST = 20

/** Con qué intensidad suena [note]: la dinámica vigente en su inicio y, si lleva acento, un empujón más. */
fun Score.velocityOf(note: Note): Int =
    (velocityAt(note.startTick) + if (note.accent) ACCENT_BOOST else 0).coerceIn(1, 127)

/** Intensidad con la que suena todo si la partitura no trae dinámicas (la de siempre). */
const val DEFAULT_VELOCITY = 90

/** Intensidad con la que se arranca si hay reguladores pero ninguna dinámica antes (mf). */
private const val FALLBACK_VELOCITY = 80

/** Cuánto cambia un regulador si no hay una dinámica que diga adónde llega. */
private const val HAIRPIN_STEP = 24

/**
 * Con qué intensidad (1..127) suena una nota que empieza en [tick]. Cada dinámica vale hasta la siguiente; un regulador
 * va cambiando de a poco, en línea recta, desde lo que había hasta la dinámica que le sigue (o ±24 si no hay), y
 * al terminar queda en ese valor, sin saltos. Antes de la primera dinámica vale la primera. Las notas no traen
 * intensidad propia: manda siempre la dinámica.
 */
fun Score.velocityAt(tick: Int): Int {
    if (dynamics.isEmpty() && hairpins.isEmpty()) return DEFAULT_VELOCITY
    return velocityInside(dynamics.sortedBy { it.startTick }, hairpins.sortedBy { it.startTick }, tick)
}

private fun hairpinTarget(bar: Hairpin, marks: List<DynamicMark>, settledAt: (Int) -> Int): Int {
    val from = settledAt(bar.startTick)
    val next = marks.firstOrNull { it.startTick >= bar.endTick }?.level?.velocity
    val up = bar.kind == HairpinKind.CRESCENDO
    val target = if (next != null && (if (up) next >= from else next <= from)) next else from + if (up) HAIRPIN_STEP else -HAIRPIN_STEP
    return target.coerceIn(1, 127)
}

private fun settledAt(marks: List<DynamicMark>, bars: List<Hairpin>, at: Int): Int {
    val mark = marks.lastOrNull { it.startTick <= at }
    val bar = bars.filter { it.endTick <= at }.maxByOrNull { it.endTick }
    return when {
        bar != null && (mark == null || bar.endTick > mark.startTick) -> hairpinTarget(bar, marks) { settledAt(marks, bars, it) }
        mark != null -> mark.level.velocity
        else -> marks.firstOrNull()?.level?.velocity ?: FALLBACK_VELOCITY
    }
}

private fun velocityInside(marks: List<DynamicMark>, bars: List<Hairpin>, tick: Int): Int {
    val inside = bars.firstOrNull { tick >= it.startTick && tick < it.endTick }
        ?: return settledAt(marks, bars, tick)
    val from = settledAt(marks, bars, inside.startTick)
    val to = hairpinTarget(inside, marks) { settledAt(marks, bars, it) }
    val fraction = (tick - inside.startTick).toFloat() / (inside.endTick - inside.startTick)
    return (from + (to - from) * fraction).toInt().coerceIn(1, 127)
}

/** Rango del piano: de La0 (MIDI 21) a Do8 (MIDI 108). */
val PIANO_RANGE = 21..108

/**
 * Revisa las reglas que dependen de varios elementos a la vez. Devuelve los problemas en español;
 * una lista vacía significa que la partitura está bien.
 */
fun Score.validate(): List<String> {
    val errors = mutableListOf<String>()
    val graceNotes = notes.filter { it.grace }
    // Las notas de adorno no llenan el compás ni se pisan con nada: se revisan aparte, más abajo.
    val notes = notes.filter { !it.grace }

    fun checkOrder(name: String, ticks: List<Int>) {
        if (ticks.firstOrNull() != 0) errors += "$name: el primero tiene que estar en el tick 0."
        if (ticks.zipWithNext().any { (a, b) -> b <= a }) {
            errors += "$name: tienen que estar ordenados y sin repetir posición."
        }
    }
    checkOrder("Firmas de compás", timeSignatures.map { it.startTick })
    checkOrder("Armaduras", keySignatures.map { it.startTick })
    checkOrder("Cambios de tempo", tempos.map { it.startTick })

    if (pickupTicks > 0) {
        val full = (timeSignatures.firstOrNull() ?: TimeSignatureChange(0, 4, 4)).measureTicks
        if (pickupTicks >= full) {
            errors += "La anacrusa (pickupTicks: $pickupTicks) tiene que ser más corta que un compás entero ($full ticks)."
        }
    }

    val measures = measures()
    val measureStarts = measures.map { it.startTick }.toSet()
    fun measureAt(tick: Int) = measures.first { tick >= it.startTick && tick < it.endTick }

    timeSignatures.filter { it.startTick < lengthTicks && it.startTick !in measureStarts }.forEach {
        errors += "El cambio de compás del tick ${it.startTick} no cae al inicio de un compás."
    }
    keySignatures.filter { it.startTick < lengthTicks && it.startTick !in measureStarts }.forEach {
        errors += "El cambio de armadura del tick ${it.startTick} no cae al inicio de un compás."
    }

    for (hand in Hand.entries) {
        val own = clefs.filter { it.hand == hand }
        if (own.zipWithNext().any { (a, b) -> b.startTick <= a.startTick }) {
            errors += "Claves de la ${hand.label}: tienen que estar ordenadas y sin repetir posición."
        }
    }
    clefs.filter { it.startTick !in measureStarts && it.startTick < lengthTicks }.forEach {
        errors += "El cambio de clave de la ${it.hand.label} del tick ${it.startTick} no cae al inicio de un compás."
    }

    if (pedals.zipWithNext().any { (a, b) -> b.startTick < a.endTick }) {
        errors += "Pedal: tiene que estar ordenado y sin superponerse (uno puede empezar justo donde termina el anterior, que es un cambio de pedal)."
    }
    pedals.filter { it.endTick > lengthTicks }.forEach {
        errors += "El pedal que se suelta en el tick ${it.endTick} termina después del final de la partitura (tick $lengthTicks)."
    }

    for (hand in Hand.entries) {
        val own = ottavas.filter { it.hand == hand }.sortedBy { it.startTick }
        if (own.zipWithNext().any { (a, b) -> b.startTick < a.endTick }) {
            errors += "Signos de octava de la ${hand.label}: no pueden superponerse."
        }
        for (o in own) {
            if (notes.none { it.hand == hand && it.startTick >= o.startTick && it.startTick < o.endTick }) {
                errors += "El signo de octava de la ${hand.label} (ticks ${o.startTick} a ${o.endTick}) no tiene ninguna nota de esa mano adentro."
            }
        }
    }
    for (note in notes.filter { it.tiedToNext }) {
        val next = notes.firstOrNull { it.hand == note.hand && it.pitch.midi == note.pitch.midi && it.startTick == note.endTick } ?: continue
        if (octaveShiftAt(note.hand, note.startTick) != octaveShiftAt(note.hand, next.startTick)) {
            errors += "La nota ${note.pitch} (${note.hand.label}, tick ${note.startTick}) está ligada a otra que cae fuera de su signo de octava: el signo tiene que cubrir las dos."
        }
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

    // Las notas de un mismo acorde llevan las mismas articulaciones: se dibuja una sola vez para todo el acorde.
    chords().filter { it.isChord }.forEach { chord ->
        if (chord.notes.map { it.staccato }.distinct().size > 1 || chord.notes.map { it.accent }.distinct().size > 1) {
            errors += "Las notas del acorde de la ${chord.hand.label} del compás ${measureAt(chord.startTick).number} " +
                "tienen que llevar todas el mismo staccato (\"staccato\") y el mismo acento (\"accent\")."
        }
    }

    for (grace in graceNotes) {
        val where = "El adorno ${grace.pitch} (${grace.hand.label}, tick ${grace.startTick})"
        if (grace.pitch.midi !in PIANO_RANGE) errors += "$where está fuera del rango del piano (de A0 a C8)."
        if (notes.none { it.hand == grace.hand && it.startTick == grace.startTick }) {
            errors += "$where no tiene una nota principal de la misma mano que empiece en ese tick: " +
                "el adorno va en el mismo tick que la nota a la que lleva."
        }
    }
    graceNotes.groupBy { Triple(it.hand, it.pitch.midi, it.startTick) }.filterValues { it.size > 1 }.keys.forEach { (hand, _, tick) ->
        errors += "Hay dos adornos iguales de la ${hand.label} en el tick $tick."
    }

    checkDynamics(this, errors)

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
            "corchea, semicorchea o fusa (con hasta dos puntillos)."
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
    class Figure(val hand: Hand, val voice: Int, val startTick: Int, val durationTicks: Int, val tuplet: Tuplet?, val pitches: Set<Int> = emptySet())
    val allFigures = chords().map { Figure(it.hand, it.voice, it.startTick, it.durationTicks, it.tuplet, it.notes.map { n -> n.pitch.midi }.toSet()) } +
        rests.map { Figure(it.hand, it.voice, it.startTick, it.durationTicks, it.tuplet) }

    // Una figura por vez en cada voz: lo que suena a la vez en una mano tiene que ir en voces distintas.
    for ((key, line) in allFigures.groupBy { it.hand to it.voice }) {
        val (hand, voice) = key
        line.sortedBy { it.startTick }.zipWithNext().forEach { (a, b) ->
            // Dos notas iguales que se pisan ya se avisaron arriba.
            if (b.startTick < a.startTick + a.durationTicks && a.pitches.intersect(b.pitches).isEmpty()) {
                errors += "En la ${hand.label} (voz $voice) hay figuras que se pisan en el compás ${measureAt(b.startTick).number}. " +
                    "Si tienen que sonar a la vez, una va en otra voz (\"voice\": ${voice + 1})."
            }
        }
    }
    for ((hand, voice) in allFigures.map { it.hand to it.voice }.distinct()) {
        val figures = allFigures.filter { it.hand == hand && it.voice == voice }.sortedBy { it.startTick }
        var run = mutableListOf<Figure>()
        fun closeRun() {
            val tuplet = run.firstOrNull()?.tuplet
            if (tuplet != null) {
                val smallest = run.minOf { tuplet.written(it.durationTicks) ?: it.durationTicks }
                val total = run.sumOf { it.durationTicks }
                if (total % (tuplet.normal * smallest) != 0) {
                    errors += "Un grupo irregular $tuplet de la ${hand.label}${if (voice > 1) " (voz $voice)" else ""} en el compás ${measureAt(run.first().startTick).number} " +
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

/** Revisa dinámicas y reguladores: en orden, dentro de la pieza y sin pisarse. */
private fun checkDynamics(score: Score, errors: MutableList<String>) {
    val end = score.lengthTicks
    val marks = score.dynamics
    if (marks.zipWithNext().any { (a, b) -> b.startTick <= a.startTick }) {
        errors += "Dinámicas: tienen que estar ordenadas y sin repetir posición."
    }
    marks.filter { it.startTick >= end }.forEach { errors += "La dinámica del tick ${it.startTick} cae después del final de la pieza." }
    val bars = score.hairpins.sortedBy { it.startTick }
    bars.filter { it.startTick >= end }.forEach { errors += "El regulador del tick ${it.startTick} empieza después del final de la pieza." }
    bars.zipWithNext().filter { (a, b) -> b.startTick < a.endTick }.forEach { (_, b) ->
        errors += "El regulador que empieza en el tick ${b.startTick} se pisa con el anterior."
    }
    for (bar in bars) {
        marks.filter { it.startTick > bar.startTick && it.startTick < bar.endTick }.forEach {
            errors += "La dinámica del tick ${it.startTick} cae en el medio de un regulador (${bar.startTick}-${bar.endTick}): " +
                "una dinámica va al principio o al final del regulador."
        }
    }
}
