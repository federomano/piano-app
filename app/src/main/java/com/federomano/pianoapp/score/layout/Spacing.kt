package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Step
import com.federomano.pianoapp.score.Ticks

/**
 * Separación horizontal dentro de un compás. Cada instante en que empieza una nota o un silencio es un
 * "punto de anclaje". Normalmente la distancia entre dos puntos es proporcional al tiempo; si ese
 * espacio no alcanza para dibujar lo que hay (cabeza, puntillo, alteración del siguiente, silencio),
 * se agranda lo justo. Así las figuras cortas no se pisan y las lentas se ven igual que antes.
 */
object Spacing {
    private const val MARGIN = 0.4f
    private const val END_GAP = 1.0f
    private const val LAST_MEASURE_EXTRA_GAP = 1.0f
    private const val ACCIDENTAL = 1.3f
    private const val DOTS = 0.9f

    /** Lugar a la izquierda de una nota para su adorno chiquito (cabeza, plica y un respiro), y un poco más si lleva alteración. */
    private const val GRACE = 1.9f
    private const val GRACE_ACCIDENTAL = 0.8f

    /** Puntos de anclaje de un compás: instantes y su distancia (sp) al comienzo del tiempo del compás. */
    class Anchors(
        val ticks: List<Int>,
        val offsets: List<Float>,
        val noteTicks: Set<Int> = emptySet(),
        /** Lugar extra que hay que dejar antes de la primera nota del compás (para el adorno de esa nota). */
        val leadExtra: Float = 0f,
    ) {
        /** Posición (relativa al comienzo del tiempo del compás) de un instante; entre dos puntos se interpola. */
        fun offsetOf(tick: Float, perTick: Float): Float {
            if (ticks.isEmpty()) return 0f
            if (tick <= ticks.first()) return offsets.first() + (tick - ticks.first()) * perTick
            if (tick >= ticks.last()) return offsets.last()
            val i = ticks.indexOfLast { it <= tick }
            val span = ticks[i + 1] - ticks[i]
            return offsets[i] + (offsets[i + 1] - offsets[i]) * (tick - ticks[i]) / span
        }

        /** Lo inverso: el instante que corresponde a una posición (relativa al comienzo del tiempo del compás). */
        fun tickAt(offset: Float, perTick: Float): Float {
            if (ticks.isEmpty()) return 0f
            if (offset <= offsets.first()) return ticks.first() + (offset - offsets.first()) / perTick
            if (offset >= offsets.last()) return ticks.last().toFloat()
            val i = offsets.indexOfLast { it <= offset }
            return ticks[i] + (ticks[i + 1] - ticks[i]) * (offset - offsets[i]) / (offsets[i + 1] - offsets[i])
        }
    }

    private fun restWidth(base: Int) = when (base) {
        Ticks.WHOLE -> Smufl.REST_WHOLE_WIDTH
        Ticks.HALF -> Smufl.REST_HALF_WIDTH
        Ticks.QUARTER -> Smufl.REST_QUARTER_WIDTH
        Ticks.EIGHTH -> Smufl.REST_8TH_WIDTH
        else -> Smufl.REST_16TH_WIDTH
    }

    fun anchors(score: Score, m: Measure, perTick: Float, isLast: Boolean): Anchors {
        val right = sortedMapOf<Int, Float>()
        val left = sortedMapOf<Int, Float>()
        fun inside(tick: Int) = tick >= m.startTick && tick < m.endTick

        // Alteraciones: se lleva la cuenta de lo que ya valía en el compás, por mano y octava.
        val state = mutableMapOf<Triple<Hand, Step, Int>, Int>()
        for (note in score.notes.filter { !it.grace && inside(it.startTick) }.sortedBy { it.startTick }) {
            val figure = NoteLayout.figureOf(note.durationTicks, note.tuplet)
            val head = if (figure.base == Ticks.WHOLE) Smufl.NOTEHEAD_WHOLE_WIDTH else Smufl.NOTEHEAD_WIDTH
            right.merge(note.startTick, head + if (figure.dots > 0) DOTS else 0f, ::maxOf)
            val key = Triple(note.hand, note.pitch.step, note.pitch.octave)
            val current = state[key] ?: NoteLayout.keyAlter(m.keyFifths, note.pitch.step)
            if (note.pitch.alter != current) left.merge(note.startTick, ACCIDENTAL, ::maxOf)
            state[key] = note.pitch.alter
        }
        // Adornos: lugar extra a la izquierda de la nota a la que llevan (después de su propia alteración).
        val graceSpace = mutableMapOf<Int, Float>()
        for (grace in score.notes.filter { it.grace && inside(it.startTick) }) {
            val need = GRACE + if (grace.pitch.alter != NoteLayout.keyAlter(m.keyFifths, grace.pitch.step)) GRACE_ACCIDENTAL else 0f
            graceSpace.merge(grace.startTick, need, ::maxOf)
        }
        for ((tick, extra) in graceSpace) left[tick] = (left[tick] ?: 0f) + extra
        for (rest in score.rests.filter { inside(it.startTick) }) {
            val figure = NoteLayout.figureOf(rest.durationTicks, rest.tuplet)
            right.merge(rest.startTick, restWidth(figure.base) + if (figure.dots > 0) DOTS else 0f, ::maxOf)
        }

        val ticks = (right.keys + left.keys + m.startTick + m.endTick).toSortedSet().toList()
        val offsets = mutableListOf(0f)
        for (i in 1 until ticks.size) {
            val natural = (ticks[i] - ticks[i - 1]) * perTick
            val toEnd = i == ticks.lastIndex
            val needed = (right[ticks[i - 1]] ?: 0f) + when {
                toEnd -> END_GAP + if (isLast) LAST_MEASURE_EXTRA_GAP else 0f
                else -> MARGIN + (left[ticks[i]] ?: 0f)
            }
            offsets += offsets.last() + maxOf(natural, if (right.containsKey(ticks[i - 1]) || toEnd) needed else 0f)
        }
        return Anchors(ticks, offsets, score.notes.filter { !it.grace && inside(it.startTick) }.map { it.startTick }.toSet(), graceSpace[m.startTick] ?: 0f)
    }
}
