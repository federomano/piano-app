package com.federomano.pianoapp.score

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
 * Modelo de datos de la partitura (PIA-9).
 *
 * Toda partitura de la app (importada de MIDI o MusicXML, o armada por Claude) se
 * representa con estas clases. El dibujo, el sonido y el modo escucha leen los mismos datos.
 * Ver docs/MODELO-PARTITURA.md.
 *
 * El tiempo se mide en "ticks": una negra son 480 ticks, siempre.
 */

/** Cantidad de ticks que dura una negra. Es fijo en todo el modelo. */
const val TICKS_PER_QUARTER = 480

/** Versión del formato del archivo JSON. */
const val FORMAT_VERSION = 1

/** Cantidad máxima de voces por mano (líneas que suenan a la vez con ritmos independientes). */
const val MAX_VOICES = 4

/** Duraciones comunes, en ticks. */
object Ticks {
    const val WHOLE = 4 * TICKS_PER_QUARTER
    const val HALF = 2 * TICKS_PER_QUARTER
    const val QUARTER = TICKS_PER_QUARTER
    const val EIGHTH = TICKS_PER_QUARTER / 2
    const val SIXTEENTH = TICKS_PER_QUARTER / 4

    /** Figura con puntillo: dura una vez y media la figura original. */
    fun dotted(ticks: Int): Int = ticks * 3 / 2

    /** Las duraciones que la app sabe dibujar sin grupo irregular: redonda a semicorchea, con hasta dos puntillos. */
    val STANDARD: Set<Int> = buildSet {
        add(WHOLE)
        for (base in listOf(HALF, QUARTER, EIGHTH, SIXTEENTH)) {
            add(base); add(base * 3 / 2); add(base * 7 / 4)
        }
    }
}

/**
 * Grupo irregular: [actual] figuras en el tiempo de [normal] (tresillo = 3:2, cuatro en el tiempo de tres = 4:3).
 * En el archivo se escribe como texto: `"3:2"`. La duración de la nota (`durationTicks`) es siempre la real, la que suena;
 * la figura escrita sale de multiplicarla por actual/normal (un tresillo de corchea dura 160 y se escribe como corchea: 240).
 */
@Serializable(with = TupletSerializer::class)
data class Tuplet(val actual: Int, val normal: Int) {
    init {
        require(actual in 2..9 && normal in 1..8 && actual != normal) {
            "Grupo irregular inválido: $actual:$normal (se escribe como \"3:2\": 3 figuras en el tiempo de 2)."
        }
    }

    /** Duración de la figura tal como se escribe, dada la duración real; null si no da un número entero. */
    fun written(realTicks: Int): Int? = if ((realTicks * actual) % normal == 0) realTicks * actual / normal else null

    override fun toString() = "$actual:$normal"

    companion object {
        fun parse(text: String): Tuplet {
            val m = Regex("^(\\d):(\\d)$").matchEntire(text.trim())
                ?: throw IllegalArgumentException("Grupo irregular inválido: \"$text\". Se escribe como \"3:2\" (3 figuras en el tiempo de 2).")
            return Tuplet(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}

object TupletSerializer : KSerializer<Tuplet> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Tuplet", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Tuplet) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Tuplet = Tuplet.parse(decoder.decodeString())
}

@Serializable
enum class Hand(val label: String) {
    @SerialName("right") RIGHT("mano derecha"),
    @SerialName("left") LEFT("mano izquierda"),
}

/** Nombre de la nota sin alteración. */
enum class Step(val semitone: Int) { C(0), D(2), E(4), F(5), G(7), A(9), B(11) }

/**
 * Altura de una nota escrita, con su ortografía: Fa# y Solb suenan igual pero se escriben distinto.
 * En texto se escribe así: `C4` (Do central), `F#4`, `Bb3`, `C##5`.
 * [alter]: +1 sostenido, -1 bemol, +2 doble sostenido, -2 doble bemol.
 */
@Serializable(with = PitchSerializer::class)
data class Pitch(val step: Step, val alter: Int = 0, val octave: Int) {
    init {
        require(alter in -2..2) { "La alteración tiene que estar entre -2 y 2 (era $alter)." }
        require(octave in -1..9) { "La octava tiene que estar entre -1 y 9 (era $octave)." }
    }

    /** Número de nota MIDI (Do central = 60, La 440 Hz = 69). */
    val midi: Int get() = (octave + 1) * 12 + step.semitone + alter

    override fun toString(): String = buildString {
        append(step.name)
        if (alter > 0) repeat(alter) { append('#') } else repeat(-alter) { append('b') }
        append(octave)
    }

    companion object {
        private val pattern = Regex("^([A-G])(#{1,2}|b{1,2})?(-?[0-9])$")

        fun parse(text: String): Pitch {
            val match = pattern.matchEntire(text.trim())
                ?: throw IllegalArgumentException(
                    "Altura inválida: \"$text\". Se escribe como C4, F#4, Bb3 (letra A-G, alteración opcional y octava).",
                )
            val (letter, accidental, octave) = match.destructured
            val alter = when {
                accidental.startsWith("#") -> accidental.length
                accidental.startsWith("b") -> -accidental.length
                else -> 0
            }
            return Pitch(Step.valueOf(letter), alter, octave.toInt())
        }
    }
}

object PitchSerializer : KSerializer<Pitch> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Pitch", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Pitch) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Pitch = Pitch.parse(decoder.decodeString())
}

/**
 * Una nota. Para un acorde se usan varias notas con la misma mano, el mismo inicio y la misma duración.
 * Una nota no puede cruzar una barra de compás: se parte en dos y la primera se liga ([tiedToNext]).
 */
@Serializable
data class Note(
    val hand: Hand,
    val pitch: Pitch,
    val startTick: Int,
    val durationTicks: Int,
    /** Ligadura de prolongación: sigue sonando con la nota igual que empieza justo después. */
    val tiedToNext: Boolean = false,
    /** Dedo sugerido, de 1 (pulgar) a 5 (meñique). */
    val finger: Int? = null,
    /** Grupo irregular al que pertenece (por ejemplo `3:2` para un tresillo). Null = figura común. */
    val tuplet: Tuplet? = null,
    /** Voz dentro de la mano, de 1 a [MAX_VOICES]. Cada voz tiene su propio ritmo; la voz 1 es la principal. */
    val voice: Int = 1,
) {
    init {
        require(voice in 1..MAX_VOICES) { "La voz tiene que ser de 1 a $MAX_VOICES (era $voice)." }
        require(startTick >= 0) { "El inicio de una nota no puede ser negativo (era $startTick)." }
        require(durationTicks > 0) { "La duración de una nota tiene que ser mayor que 0 (era $durationTicks)." }
        require(finger == null || finger in 1..5) { "El dedo tiene que ser de 1 a 5 (era $finger)." }
    }

    val endTick: Int get() = startTick + durationTicks
}

@Serializable
data class Rest(
    val hand: Hand,
    val startTick: Int,
    val durationTicks: Int,
    val tuplet: Tuplet? = null,
    val voice: Int = 1,
) {
    init {
        require(voice in 1..MAX_VOICES) { "La voz tiene que ser de 1 a $MAX_VOICES (era $voice)." }
        require(startTick >= 0) { "El inicio de un silencio no puede ser negativo (era $startTick)." }
        require(durationTicks > 0) { "La duración de un silencio tiene que ser mayor que 0 (era $durationTicks)." }
    }

    val endTick: Int get() = startTick + durationTicks
}

/** Firma de compás desde [startTick]. Tiene que empezar al comienzo de un compás. */
@Serializable
data class TimeSignatureChange(val startTick: Int, val numerator: Int, val denominator: Int) {
    init {
        require(startTick >= 0) { "La posición de la firma de compás no puede ser negativa." }
        require(numerator >= 1) { "El numerador de la firma de compás tiene que ser 1 o más (era $numerator)." }
        require(denominator in setOf(1, 2, 4, 8, 16, 32)) {
            "El denominador de la firma de compás tiene que ser 1, 2, 4, 8, 16 o 32 (era $denominator)."
        }
    }

    /** Duración de un compás con esta firma. */
    val measureTicks: Int get() = numerator * 4 * TICKS_PER_QUARTER / denominator
}

/** Armadura desde [startTick]: [fifths] positivo = sostenidos, negativo = bemoles (0 = Do mayor / La menor). */
@Serializable
data class KeySignatureChange(val startTick: Int, val fifths: Int) {
    init {
        require(startTick >= 0) { "La posición de la armadura no puede ser negativa." }
        require(fifths in -7..7) { "La armadura tiene que estar entre -7 y 7 (era $fifths)." }
    }
}

/** Tempo desde [startTick], en negras por minuto. */
@Serializable
data class TempoChange(val startTick: Int, val bpm: Double) {
    init {
        require(startTick >= 0) { "La posición del tempo no puede ser negativa." }
        require(bpm > 0.0) { "El tempo tiene que ser mayor que 0 (era $bpm)." }
    }
}

@Serializable
data class Score(
    val formatVersion: Int = FORMAT_VERSION,
    val title: String,
    val composer: String? = null,
    /** Nivel de dificultad, texto libre (por ejemplo "principiante"). */
    val level: String? = null,
    /** Qué se practica con esta pieza, texto libre. */
    val goal: String? = null,
    val timeSignatures: List<TimeSignatureChange> = listOf(TimeSignatureChange(0, 4, 4)),
    val keySignatures: List<KeySignatureChange> = listOf(KeySignatureChange(0, 0)),
    val tempos: List<TempoChange> = listOf(TempoChange(0, 100.0)),
    val notes: List<Note> = emptyList(),
    val rests: List<Rest> = emptyList(),
) {
    init {
        require(formatVersion == FORMAT_VERSION) {
            "Versión de formato no soportada: $formatVersion (esta app lee la $FORMAT_VERSION)."
        }
        require(title.isNotBlank()) { "La partitura necesita un título." }
    }

    /** Fin de la partitura: donde termina la última nota o silencio. */
    val lengthTicks: Int
        get() = maxOf(notes.maxOfOrNull { it.endTick } ?: 0, rests.maxOfOrNull { it.endTick } ?: 0)
}
