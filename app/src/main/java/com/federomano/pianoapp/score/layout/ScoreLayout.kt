package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.TICKS_PER_QUARTER
import com.federomano.pianoapp.score.measures
import kotlin.math.abs
import kotlin.math.max

/** Medidas fijas del dibujo, en espacios de pentagrama (sp). */
object Geometry {
    const val STAFF_HEIGHT = 4f

    /** Línea superior del pentagrama de la mano derecha (clave de sol). */
    const val TREBLE_TOP = 0f

    /** Línea superior del pentagrama de la mano izquierda (clave de fa). */
    const val BASS_TOP = 10f
    const val SYSTEM_BOTTOM = BASS_TOP + STAFF_HEIGHT

    /** Espacio libre arriba y abajo para líneas adicionales, notas agudas o graves y números de compás. */
    const val MARGIN_TOP = 5.5f
    const val MARGIN_BOTTOM = 5.5f
    const val TOTAL_HEIGHT = MARGIN_TOP + SYSTEM_BOTTOM + MARGIN_BOTTOM

    const val STAFF_LINE_THICKNESS = 0.13f
    const val THIN_BARLINE = 0.16f
    const val THICK_BARLINE = 0.5f

    /** Ancho de una negra. Un compás de 4/4 mide 4 veces esto. */
    const val QUARTER_WIDTH = 4.5f
}

/** Un compás ya ubicado en el eje horizontal. */
data class MeasureLayout(
    val measure: Measure,
    /** Donde está la barra de compás de la izquierda (en el primero, el comienzo de la pieza). */
    val x0: Float,
    /** Donde empieza el tiempo del compás (después de un eventual cambio de armadura o de compás). */
    val contentX: Float,
    /** Barra de compás de la derecha. */
    val x1: Float,
)

/**
 * Lo que queda fijo a la izquierda de la pantalla. [fixed] es lo que no cambia nunca (líneas, llave, claves);
 * [signature] es la armadura y la firma de compás, que es lo único que se reemplaza cuando cambian.
 * [signatureLeft] es donde empieza [signature].
 */
data class Header(val fixed: List<DrawOp>, val signature: List<DrawOp>, val width: Float, val signatureLeft: Float) {
    val ops: List<DrawOp> get() = fixed + signature
}

/**
 * Cambio de encabezado en curso: el [header] nuevo entra como capa opaca por encima del viejo.
 * [left] es su borde izquierdo en pantalla (en sp, desde el borde de la pantalla; baja hasta 0 y ahí coincide
 * con el área fija) y [progress] va de 0 a 1. El viejo se dibuja con opacidad 1 - [progress].
 */
data class HeaderTransition(val header: Header, val left: Float, val progress: Float)

/**
 * Cálculo del dibujo de la partitura: dos pentagramas unidos con llave, en una sola línea continua.
 * [body] es todo lo que se desplaza (líneas, barras de compás, números y cambios de armadura o
 * compás); [headerAt] da lo que queda fijo a la izquierda.
 */
class ScoreLayout private constructor(
    val measures: List<MeasureLayout>,
    val body: List<DrawOp>,
    val totalWidth: Float,
    private val quarterWidth: Float,
) {
    /** Ancho del área fija. Es el mismo en toda la pieza (el del encabezado más ancho) para que al cambiar no haya saltos. */
    val headerWidth: Float = measures.map { Triple(it.measure.keyFifths, it.measure.timeSignature.numerator, it.measure.timeSignature.denominator) }
        .distinct().maxOf { buildHeader(it.first, it.second, it.third, 0f).width }

    private val headers = HashMap<Triple<Int, Int, Int>, Header>()

    /** Posición horizontal de un instante de la pieza. Es proporcional al tiempo dentro de cada compás. */
    fun xOfTick(tick: Int): Float {
        val index = measures.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)
        val m = measures[index]
        val clamped = tick.coerceAtMost(m.measure.endTick)
        return m.contentX + (clamped - m.measure.startTick) * quarterWidth / TICKS_PER_QUARTER
    }

    /** Encabezado que corresponde a lo que está en el borde izquierdo de la zona que se desplaza. */
    fun headerAt(scrollX: Float): Header {
        val index = measures.indexOfLast { it.contentX <= scrollX }.coerceAtLeast(0)
        val m = measures[index].measure
        return headers.getOrPut(Triple(m.keyFifths, m.timeSignature.numerator, m.timeSignature.denominator)) {
            buildHeader(m.keyFifths, m.timeSignature.numerator, m.timeSignature.denominator, headerWidth)
        }
    }

    /**
     * Transición hacia el próximo cambio de encabezado, o null si no hay ninguna en curso.
     * Empieza cuando el encabezado nuevo toca el borde del viejo y termina cuando coinciden,
     * justo en el momento en que [headerAt] pasa al nuevo.
     */
    fun transitionAt(scrollX: Float): HeaderTransition? {
        val index = measures.indexOfLast { it.contentX <= scrollX }.coerceAtLeast(0)
        val current = measures[index].measure
        val next = measures.drop(index + 1).firstOrNull {
            it.measure.keyFifths != current.keyFifths ||
                it.measure.timeSignature.numerator != current.timeSignature.numerator ||
                it.measure.timeSignature.denominator != current.timeSignature.denominator
        } ?: return null
        val left = next.contentX - scrollX
        if (left >= headerWidth) return null
        val target = headerAt(next.contentX)
        return HeaderTransition(target, left.coerceAtLeast(0f), (1f - left / headerWidth).coerceIn(0f, 1f))
    }

    companion object {
        private const val LEFT_PAD = 0.8f
        private const val MEASURE_LEAD = 1.0f
        private const val SIGNATURE_LEAD = 0.8f
        private const val ACCIDENTAL_GAP = 0.15f
        private const val SIGNATURE_GAP = 0.9f

        // Altura de cada alteración de la armadura, en sp desde la línea superior de su pentagrama.
        private val SHARPS_TREBLE = floatArrayOf(0f, 1.5f, -0.5f, 1f, 2.5f, 0.5f, 2f)
        private val SHARPS_BASS = floatArrayOf(1f, 2.5f, 0.5f, 2f, 3.5f, 1.5f, 3f)
        private val FLATS_TREBLE = floatArrayOf(2f, 0.5f, 2.5f, 1f, 3f, 1.5f, 3.5f)
        private val FLATS_BASS = floatArrayOf(3f, 1.5f, 3.5f, 2f, 4f, 2.5f, 4.5f)

        fun build(score: Score, quarterWidth: Float = Geometry.QUARTER_WIDTH): ScoreLayout {
            val measures = score.measures()
            val body = mutableListOf<DrawOp>()
            val laidOut = mutableListOf<MeasureLayout>()
            val perTick = quarterWidth / TICKS_PER_QUARTER

            var x = 0f
            val perMeasure = mutableListOf<DrawOp>()
            for ((index, m) in measures.withIndex()) {
                val x0 = x
                var cursor = x0
                val previous = measures.getOrNull(index - 1)
                val keyChanged = previous != null && previous.keyFifths != m.keyFifths
                val timeChanged = previous != null && (
                    previous.timeSignature.numerator != m.timeSignature.numerator ||
                        previous.timeSignature.denominator != m.timeSignature.denominator
                    )
                if (keyChanged || timeChanged) {
                    cursor += SIGNATURE_LEAD
                    if (keyChanged) {
                        cursor = addKeySignature(perMeasure, cursor, previous!!.keyFifths, m.keyFifths) + SIGNATURE_GAP
                    }
                    if (timeChanged) {
                        cursor = addTimeSignature(
                            perMeasure, cursor, m.timeSignature.numerator, m.timeSignature.denominator,
                        ) + SIGNATURE_GAP
                    }
                    cursor += MEASURE_LEAD - SIGNATURE_GAP
                } else {
                    cursor += MEASURE_LEAD
                }
                val contentX = cursor
                val x1 = contentX + (m.endTick - m.startTick) * perTick

                // Número de compás, arriba del pentagrama de la mano derecha.
                perMeasure += TextOp((index + 1).toString(), x0 + 0.35f, -1.6f, 1.8f)

                // Barra de compás que une los dos pentagramas. La última es doble: fina y gruesa.
                if (index == measures.lastIndex) {
                    val thickLeft = x1 - Geometry.THICK_BARLINE
                    perMeasure += RectOp(thickLeft - 0.4f - Geometry.THIN_BARLINE, 0f, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM)
                    perMeasure += RectOp(thickLeft, 0f, Geometry.THICK_BARLINE, Geometry.SYSTEM_BOTTOM)
                } else {
                    perMeasure += RectOp(x1 - Geometry.THIN_BARLINE / 2, 0f, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM)
                }

                laidOut += MeasureLayout(m, x0, contentX, x1)
                x = x1
            }

            val totalWidth = x
            body += staffLines(0f, totalWidth)
            body += perMeasure
            return ScoreLayout(laidOut, body, totalWidth, quarterWidth)
        }

        /** Las diez líneas de los dos pentagramas, entre [fromX] y [toX]. */
        private fun staffLines(fromX: Float, toX: Float): List<DrawOp> {
            val t = Geometry.STAFF_LINE_THICKNESS
            return listOf(Geometry.TREBLE_TOP, Geometry.BASS_TOP).flatMap { top ->
                (0..4).map { line -> RectOp(fromX, top + line - t / 2, toX - fromX, t) }
            }
        }

        private fun buildHeader(keyFifths: Int, numerator: Int, denominator: Int, minWidth: Float): Header {
            val ops = mutableListOf<DrawOp>()
            val signature = mutableListOf<DrawOp>()

            // Llave: se agranda hasta cubrir los dos pentagramas.
            val braceScale = Geometry.SYSTEM_BOTTOM / Smufl.BRACE_HEIGHT
            ops += GlyphOp(Smufl.BRACE, LEFT_PAD, Geometry.SYSTEM_BOTTOM, braceScale)
            val braceRight = LEFT_PAD + Smufl.BRACE_WIDTH * braceScale

            // Línea que une los dos pentagramas, pegada a la llave.
            val systemLeft = braceRight + 0.1f
            ops += RectOp(systemLeft, 0f, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM)
            val staffStart = systemLeft

            // Claves: sol en la línea de sol (la 2.ª desde abajo), fa en la línea de fa (la 4.ª desde abajo).
            var x = systemLeft + Geometry.THIN_BARLINE + 0.7f
            ops += GlyphOp(Smufl.G_CLEF, x, Geometry.TREBLE_TOP + 3f)
            ops += GlyphOp(Smufl.F_CLEF, x, Geometry.BASS_TOP + 1f)
            x += Smufl.CLEF_ADVANCE + SIGNATURE_GAP

            val signatureLeft = x
            if (keyFifths != 0) {
                x = addKeySignature(signature, x, 0, keyFifths) + SIGNATURE_GAP
            }
            x = addTimeSignature(signature, x, numerator, denominator)

            val width = max(x + 1.0f, minWidth)
            val fixed = mutableListOf<DrawOp>()
            fixed += staffLines(staffStart, width)
            fixed += ops
            return Header(fixed, signature, width, signatureLeft)
        }

        /**
         * Dibuja la armadura en los dos pentagramas. Si cambia de otra anterior, primero van los
         * becuadros que cancelan las alteraciones que ya no valen. Devuelve donde termina.
         */
        private fun addKeySignature(ops: MutableList<DrawOp>, startX: Float, oldFifths: Int, newFifths: Int): Float {
            var x = startX
            var placed = false

            fun place(codepoint: Int, advance: Float, trebleY: Float, bassY: Float) {
                ops += GlyphOp(codepoint, x, Geometry.TREBLE_TOP + trebleY)
                ops += GlyphOp(codepoint, x, Geometry.BASS_TOP + bassY)
                x += advance + ACCIDENTAL_GAP
                placed = true
            }

            val oldCount = abs(oldFifths)
            val newCount = abs(newFifths)
            val sameKind = oldFifths != 0 && newFifths != 0 && (oldFifths > 0) == (newFifths > 0)
            val cancelFrom = if (sameKind) newCount else 0
            for (i in cancelFrom until oldCount) {
                val trebleY = (if (oldFifths > 0) SHARPS_TREBLE else FLATS_TREBLE)[i]
                val bassY = (if (oldFifths > 0) SHARPS_BASS else FLATS_BASS)[i]
                place(Smufl.NATURAL, Smufl.NATURAL_ADVANCE, trebleY, bassY)
            }
            for (i in 0 until newCount) {
                if (newFifths > 0) {
                    place(Smufl.SHARP, Smufl.SHARP_ADVANCE, SHARPS_TREBLE[i], SHARPS_BASS[i])
                } else {
                    place(Smufl.FLAT, Smufl.FLAT_ADVANCE, FLATS_TREBLE[i], FLATS_BASS[i])
                }
            }
            return if (placed) x - ACCIDENTAL_GAP else x
        }

        /** Firma de compás con el numerador arriba y el denominador abajo, en los dos pentagramas. Devuelve donde termina. */
        private fun addTimeSignature(ops: MutableList<DrawOp>, startX: Float, numerator: Int, denominator: Int): Float {
            val top = numerator.toString()
            val bottom = denominator.toString()
            val topWidth = digitsWidth(top)
            val bottomWidth = digitsWidth(bottom)
            val width = max(topWidth, bottomWidth)
            for (staffTop in listOf(Geometry.TREBLE_TOP, Geometry.BASS_TOP)) {
                placeDigits(ops, top, startX + (width - topWidth) / 2, staffTop + 1f)
                placeDigits(ops, bottom, startX + (width - bottomWidth) / 2, staffTop + 3f)
            }
            return startX + width
        }

        private fun digitsWidth(text: String): Float = text.sumOf { Smufl.timeDigitAdvance(it - '0').toDouble() }.toFloat()

        private fun placeDigits(ops: MutableList<DrawOp>, text: String, startX: Float, baseline: Float) {
            var x = startX
            for (c in text) {
                val digit = c - '0'
                ops += GlyphOp(Smufl.timeDigit(digit), x, baseline)
                x += Smufl.timeDigitAdvance(digit)
            }
        }
    }
}
