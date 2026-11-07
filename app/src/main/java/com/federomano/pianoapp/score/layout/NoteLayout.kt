package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Chord
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Step
import com.federomano.pianoapp.score.Ticks
import com.federomano.pianoapp.score.chords
import kotlin.math.max
import kotlin.math.min

/**
 * Dibujo de las notas (PIA-11): cabezas, plicas, banderas, líneas adicionales, alteraciones y puntillos.
 * Calcula instrucciones de dibujo en espacios de pentagrama, igual que el resto del layout.
 *
 * Las reglas son las de la notación tradicional: las plicas van hacia arriba si la nota está por debajo
 * de la línea central y hacia abajo si está en ella o por encima; en un acorde manda la nota más alejada
 * de la línea central. Una alteración se escribe solo si cambia lo que ya valía en ese compás (o lo que
 * da la armadura).
 */
object NoteLayout {
    const val STEM_THICKNESS = 0.12f
    const val LEDGER_THICKNESS = 0.16f
    private const val LEDGER_OVERHANG = 0.4f
    private const val STEM_LENGTH = 3.5f
    private const val STEM_ANCHOR = 0.168f
    private const val DOT_GAP = 0.35f
    private const val DOT_SPACING = 0.45f
    private const val ACCIDENTAL_MARGIN = 0.2f
    private const val ACCIDENTAL_COLUMN_GAP = 0.15f

    /** Posición diatónica de la línea inferior de cada pentagrama: Mi4 en clave de sol, Sol2 en clave de fa. */
    private const val TREBLE_BOTTOM = 4 * 7 + 2
    private const val BASS_BOTTOM = 2 * 7 + 4

    /** Figura rítmica: la duración base más los puntillos. */
    data class Figure(val base: Int, val dots: Int)

    fun figureOf(ticks: Int): Figure {
        val bases = listOf(Ticks.WHOLE, Ticks.HALF, Ticks.QUARTER, Ticks.EIGHTH, Ticks.SIXTEENTH)
        for (base in bases) {
            if (ticks == base) return Figure(base, 0)
            if (ticks == base * 3 / 2) return Figure(base, 1)
            if (ticks == base * 7 / 4) return Figure(base, 2)
        }
        // Duración rara (tresillos, etc.): se dibuja con la figura más grande que entre, sin puntillos.
        return Figure(bases.firstOrNull { it <= ticks } ?: Ticks.SIXTEENTH, 0)
    }

    private val STEP_INDEX = mapOf(Step.C to 0, Step.D to 1, Step.E to 2, Step.F to 3, Step.G to 4, Step.A to 5, Step.B to 6)

    /** Posición diatónica absoluta: Do0 = 0, cada línea o espacio sube 1. */
    fun diatonic(pitch: Pitch): Int = pitch.octave * 7 + STEP_INDEX.getValue(pitch.step)

    /** Posición en el pentagrama: 0 = línea inferior, 1 = primer espacio, 2 = segunda línea, etc. */
    fun staffPosition(pitch: Pitch, hand: Hand): Int = diatonic(pitch) - if (hand == Hand.RIGHT) TREBLE_BOTTOM else BASS_BOTTOM

    private fun staffTop(hand: Hand) = if (hand == Hand.RIGHT) Geometry.TREBLE_TOP else Geometry.BASS_TOP

    /** Altura (y) de una posición del pentagrama. */
    fun yOf(position: Int, hand: Hand): Float = staffTop(hand) + 4f - position * 0.5f

    private val SHARP_ORDER = listOf(Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B)
    private val FLAT_ORDER = listOf(Step.B, Step.E, Step.A, Step.D, Step.G, Step.C, Step.F)

    /** Alteración que la armadura da a cada nota. */
    fun keyAlter(fifths: Int, step: Step): Int = when {
        fifths > 0 && step in SHARP_ORDER.take(fifths) -> 1
        fifths < 0 && step in FLAT_ORDER.take(-fifths) -> -1
        else -> 0
    }

    private class Head(val note: Note, val position: Int, var displaced: Boolean = false, var x: Float = 0f)

    /** Dibuja todas las notas de la partitura. [xOfTick] da la posición horizontal de cada instante. */
    fun build(score: Score, measures: List<MeasureLayout>, xOfTick: (Int) -> Float): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        // Alteraciones vigentes en el compás, por mano y por nota (nombre + octava).
        var stateMeasure = -1
        val state = mutableMapOf<Hand, MutableMap<Pair<Step, Int>, Int>>()
        for (chord in score.chords()) {
            val measureIndex = measures.indexOfLast { it.measure.startTick <= chord.startTick }.coerceAtLeast(0)
            if (measureIndex != stateMeasure) {
                stateMeasure = measureIndex
                state.clear()
            }
            val fifths = measures[measureIndex].measure.keyFifths
            val handState = state.getOrPut(chord.hand) { mutableMapOf() }
            val tiedFromBefore = chord.notes.filter { note ->
                note.startTick == measures[measureIndex].measure.startTick &&
                    score.notes.any { it.hand == note.hand && it.pitch.midi == note.pitch.midi && it.endTick == note.startTick && it.tiedToNext }
            }.toSet()
            drawChord(ops, chord, xOfTick(chord.startTick), fifths, handState, tiedFromBefore)
        }
        return ops
    }

    private fun drawChord(
        ops: MutableList<DrawOp>,
        chord: Chord,
        noteX: Float,
        fifths: Int,
        handState: MutableMap<Pair<Step, Int>, Int>,
        tiedFromBefore: Set<Note>,
    ) {
        val hand = chord.hand
        val figure = figureOf(chord.durationTicks)
        val whole = figure.base == Ticks.WHOLE
        val headWidth = if (whole) Smufl.NOTEHEAD_WHOLE_WIDTH else Smufl.NOTEHEAD_WIDTH
        val headCode = when (figure.base) {
            Ticks.WHOLE -> Smufl.NOTEHEAD_WHOLE
            Ticks.HALF -> Smufl.NOTEHEAD_HALF
            else -> Smufl.NOTEHEAD_BLACK
        }

        val heads = chord.notes.map { Head(it, staffPosition(it.pitch, hand)) }.sortedBy { it.position }
        val lowest = heads.first().position
        val highest = heads.last().position

        // Plica hacia abajo si la nota más alejada de la línea central está arriba o a la par (la línea central es la posición 4).
        val stemDown = (highest - 4) >= (4 - lowest)

        // Cabezas de notas contiguas (segundas) van a lados opuestos de la plica.
        if (stemDown) {
            for (i in heads.indices.reversed()) {
                val above = heads.getOrNull(i + 1)
                heads[i].displaced = above != null && above.position - heads[i].position == 1 && !above.displaced
            }
        } else {
            for (i in heads.indices) {
                val below = heads.getOrNull(i - 1)
                heads[i].displaced = below != null && heads[i].position - below.position == 1 && !below.displaced
            }
        }
        val stemX = if (stemDown) noteX else noteX + headWidth - STEM_THICKNESS
        for (head in heads) {
            head.x = when {
                !head.displaced -> noteX
                stemDown -> noteX - headWidth + STEM_THICKNESS
                else -> noteX + headWidth - STEM_THICKNESS
            }
        }
        val leftEdge = heads.minOf { it.x }
        val rightEdge = heads.maxOf { it.x + headWidth }

        // Líneas adicionales (una sola vez por línea, a lo ancho de todo el acorde).
        val ledgers = sortedSetOf<Int>()
        for (head in heads) {
            val p = head.position
            if (p <= -2) for (q in p..-2) if (q % 2 == 0) ledgers += q
            if (p >= 10) for (q in 10..p) if (q % 2 == 0) ledgers += q
        }
        for (q in ledgers) {
            ops += RectOp(leftEdge - LEDGER_OVERHANG, yOf(q, hand) - LEDGER_THICKNESS / 2, rightEdge - leftEdge + 2 * LEDGER_OVERHANG, LEDGER_THICKNESS)
        }

        // Plica y bandera.
        if (!whole) {
            val middle = staffTop(hand) + 2f
            val length = STEM_LENGTH + if (figure.base == Ticks.SIXTEENTH) 0.5f else 0f
            if (stemDown) {
                val top = yOf(highest, hand) + STEM_ANCHOR
                val tip = max(yOf(lowest, hand) + length, middle)
                ops += RectOp(stemX, top, STEM_THICKNESS, tip - top)
                flagFor(figure.base, down = true)?.let { ops += GlyphOp(it, stemX, tip) }
            } else {
                val bottom = yOf(lowest, hand) - STEM_ANCHOR
                val tip = min(yOf(highest, hand) - length, middle)
                ops += RectOp(stemX, tip, STEM_THICKNESS, bottom - tip)
                flagFor(figure.base, down = false)?.let { ops += GlyphOp(it, stemX, tip) }
            }
        }

        // Cabezas.
        for (head in heads) ops += GlyphOp(headCode, head.x, yOf(head.position, hand))

        // Alteraciones, en columnas hacia la izquierda si las notas están cerca.
        val accidentals = mutableListOf<Pair<Head, Int>>()
        for (head in heads.sortedByDescending { it.position }) {
            val pitch = head.note.pitch
            val key = pitch.step to pitch.octave
            val current = handState[key] ?: keyAlter(fifths, pitch.step)
            if (pitch.alter != current && head.note !in tiedFromBefore) accidentals += head to pitch.alter
            handState[key] = pitch.alter
        }
        val columnLastPosition = mutableListOf<Int>()
        val placed = accidentals.map { (head, alter) ->
            var column = columnLastPosition.indexOfFirst { last -> last - head.position >= 6 }
            if (column < 0) {
                column = columnLastPosition.size
                columnLastPosition += head.position
            } else {
                columnLastPosition[column] = head.position
            }
            Triple(head, alter, column)
        }
        val columnWidth = DoubleArray(columnLastPosition.size)
        for ((_, alter, column) in placed) columnWidth[column] = max(columnWidth[column], accidentalAdvance(alter).toDouble())
        for ((head, alter, column) in placed) {
            val offset = (0..column).sumOf { columnWidth[it] } + column * ACCIDENTAL_COLUMN_GAP
            ops += GlyphOp(accidentalCode(alter), leftEdge - ACCIDENTAL_MARGIN - offset.toFloat(), yOf(head.position, hand))
        }

        // Puntillos: a la derecha de todas las cabezas, en el espacio (si la nota está en una línea, el de arriba).
        if (figure.dots > 0) {
            val dotYs = heads.map { head ->
                if (head.position % 2 == 0) yOf(head.position, hand) - 0.5f else yOf(head.position, hand)
            }.distinct()
            for (y in dotYs) {
                for (i in 0 until figure.dots) {
                    ops += GlyphOp(Smufl.AUGMENTATION_DOT, rightEdge + DOT_GAP + i * DOT_SPACING, y)
                }
            }
        }
    }

    private fun flagFor(base: Int, down: Boolean): Int? = when (base) {
        Ticks.EIGHTH -> if (down) Smufl.FLAG_8TH_DOWN else Smufl.FLAG_8TH_UP
        Ticks.SIXTEENTH -> if (down) Smufl.FLAG_16TH_DOWN else Smufl.FLAG_16TH_UP
        else -> null
    }

    private fun accidentalCode(alter: Int): Int = when (alter) {
        2 -> Smufl.DOUBLE_SHARP
        1 -> Smufl.SHARP
        0 -> Smufl.NATURAL
        -1 -> Smufl.FLAT
        else -> Smufl.DOUBLE_FLAT
    }

    private fun accidentalAdvance(alter: Int): Float = when (alter) {
        2 -> Smufl.DOUBLE_SHARP_ADVANCE
        1 -> Smufl.SHARP_ADVANCE
        0 -> Smufl.NATURAL_ADVANCE
        -1 -> Smufl.FLAT_ADVANCE
        else -> Smufl.DOUBLE_FLAT_ADVANCE
    }

}
