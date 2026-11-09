package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.OttavaKind
import com.federomano.pianoapp.score.Score

/**
 * Signos de octava (PIA-65): el "8va" (o "8vb") con una línea punteada que llega hasta la última nota y un
 * remate que apunta a las notas. Va arriba del pentagrama (8va) o abajo (8vb), esquivando lo que haya ahí
 * (notas, plicas, líneas adicionales, números de grupo).
 */
object OttavaLayout {
    private const val GAP = 0.6f
    private const val DASH = 0.55f
    private const val DASH_GAP = 0.4f
    private const val LINE_THICKNESS = 0.12f
    private const val HOOK = 1.0f
    private const val TEXT_TO_LINE = 0.3f
    private const val END_PAD = 0.5f

    /** [boxes]: lo ya dibujado (sin las barras de compás, que cruzan todo el sistema). */
    fun build(score: Score, xOfTick: (Int) -> Float, boxes: List<Box>): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        for (mark in score.ottavas) {
            val staffTop = if (mark.hand == Hand.RIGHT) Geometry.TREBLE_TOP else Geometry.BASS_TOP
            val above = mark.kind == OttavaKind.OTTAVA_ALTA
            val textWidth = if (above) Smufl.OTTAVA_ALTA_WIDTH else Smufl.OTTAVA_BASSA_WIDTH
            val x0 = xOfTick(mark.startTick) - 0.2f
            val x1 = maxOf(xOfTick(mark.endTick) - END_PAD, x0 + textWidth + 1f)
            // Línea de base del signo: sobre lo más alto que haya (8va) o debajo de lo más bajo (8vb).
            val signBaseline = if (above) {
                Extents.highest(boxes, x0, x1, staffTop) - GAP
            } else {
                Extents.lowest(boxes, x0, x1, staffTop + 4f) + GAP + Smufl.OTTAVA_HEIGHT
            }
            ops += GlyphOp(if (above) Smufl.OTTAVA_ALTA else Smufl.OTTAVA_BASSA, x0, signBaseline)
            val lineY = signBaseline - Smufl.OTTAVA_HEIGHT / 2
            var x = x0 + textWidth + TEXT_TO_LINE
            while (x < x1) {
                val w = minOf(DASH, x1 - x)
                ops += RectOp(x, lineY - LINE_THICKNESS / 2, w, LINE_THICKNESS)
                x += DASH + DASH_GAP
            }
            // Remate: una rayita que apunta a las notas.
            ops += if (above) RectOp(x1 - LINE_THICKNESS, lineY - LINE_THICKNESS / 2, LINE_THICKNESS, HOOK)
            else RectOp(x1 - LINE_THICKNESS, lineY + LINE_THICKNESS / 2 - HOOK, LINE_THICKNESS, HOOK)
        }
        return ops
    }
}
