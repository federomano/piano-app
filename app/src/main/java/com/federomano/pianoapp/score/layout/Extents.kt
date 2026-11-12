package com.federomano.pianoapp.score.layout

import kotlin.math.max
import kotlin.math.min

/** Caja que ocupa una instrucción de dibujo, en espacios de pentagrama. Es una estimación algo generosa. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlapsX(from: Float, to: Float) = right > from && left < to
}

/**
 * Cuánto lugar ocupa lo dibujado (PIA-71): sirve para dejar margen arriba y abajo según lo que de verdad hay (notas muy
 * agudas o graves, números de grupo, dinámicas) y para que las letras de las dinámicas y los números de compás
 * esquiven lo que tienen cerca.
 */
object Extents {
    private const val DEFAULT_GLYPH_WIDTH = 1.6f
    private const val DEFAULT_GLYPH_REACH = 1.2f

    fun box(op: DrawOp): Box = when (op) {
        is RectOp -> Box(op.x, op.y, op.x + op.width, op.y + op.height)
        is PolygonOp -> {
            val xs = op.points.filterIndexed { i, _ -> i % 2 == 0 }
            val ys = op.points.filterIndexed { i, _ -> i % 2 == 1 }
            Box(xs.min(), ys.min(), xs.max(), ys.max())
        }
        is TextOp -> Box(op.x, op.y - op.size, op.x + op.size * 0.6f * op.text.length, op.y)
        is GlyphOp -> glyphBox(op)
    }

    private fun glyphBox(op: GlyphOp): Box {
        val s = op.scale
        val c = op.codepoint
        return when {
            // Números de grupo irregular: se apoyan en su línea de base.
            c in 0xE880..0xE88A -> Box(op.x, op.y - Smufl.TUPLET_DIGIT_HEIGHT, op.x + 1.4f, op.y)
            // Letras de dinámica (p, f, mf, ff...): suben casi dos espacios y la p baja un poco.
            c in 0xE520..0xE53F -> Box(op.x, op.y - 1.8f, op.x + 3.4f, op.y + 0.7f)
            c == Smufl.OTTAVA_ALTA -> Box(op.x, op.y - Smufl.OTTAVA_HEIGHT, op.x + Smufl.OTTAVA_ALTA_WIDTH, op.y)
            c == Smufl.OTTAVA_BASSA -> Box(op.x, op.y - Smufl.OTTAVA_HEIGHT, op.x + Smufl.OTTAVA_BASSA_WIDTH, op.y)
            c == Smufl.PEDAL_DOWN -> Box(op.x, op.y - Smufl.PEDAL_HEIGHT, op.x + Smufl.PEDAL_DOWN_WIDTH, op.y + 0.03f)
            c == Smufl.PEDAL_UP -> Box(op.x, op.y - Smufl.PEDAL_UP_WIDTH, op.x + Smufl.PEDAL_UP_WIDTH, op.y)
            c == Smufl.PEDAL_CHANGE -> Box(op.x, op.y - Smufl.PEDAL_HEIGHT, op.x + Smufl.PEDAL_CHANGE_WIDTH, op.y)
            c == Smufl.ACCENT_ABOVE -> Box(op.x, op.y - 0.98f, op.x + 1.36f, op.y)
            c == Smufl.ACCENT_BELOW -> Box(op.x, op.y, op.x + 1.36f, op.y + 0.98f)
            c == Smufl.STACCATO_ABOVE -> Box(op.x, op.y - 0.34f, op.x + 0.34f, op.y)
            else -> Box(op.x, op.y - DEFAULT_GLYPH_REACH * s, op.x + DEFAULT_GLYPH_WIDTH * s, op.y + DEFAULT_GLYPH_REACH * s)
        }
    }

    fun boxes(ops: List<DrawOp>): List<Box> = ops.map(::box)

    /** Lo más bajo que llega lo dibujado entre [from] y [to] (nunca menos que [atLeast]). */
    fun lowest(boxes: List<Box>, from: Float, to: Float, atLeast: Float): Float =
        boxes.filter { it.overlapsX(from, to) }.fold(atLeast) { acc, b -> max(acc, b.bottom) }

    /** Lo más alto que llega lo dibujado entre [from] y [to] (nunca más abajo que [atMost]). */
    fun highest(boxes: List<Box>, from: Float, to: Float, atMost: Float): Float =
        boxes.filter { it.overlapsX(from, to) }.fold(atMost) { acc, b -> min(acc, b.top) }
}
