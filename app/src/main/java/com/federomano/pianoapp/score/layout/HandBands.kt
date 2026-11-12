package com.federomano.pianoapp.score.layout

/**
 * De qué mano es cada cosa dibujada (PIA-22), para poder atenuar la mano silenciada. Se decide por la altura:
 * lo que cae en la mitad de arriba del sistema es de la derecha y lo de abajo, de la izquierda. Las barras de
 * compás, los números, las dinámicas y los reguladores no son de ninguna mano.
 */
object HandBands {
    const val NONE = 0
    const val RIGHT = 1
    const val LEFT = 2

    /** Altura que separa las dos manos: la mitad del espacio entre los dos pentagramas. */
    private const val SPLIT = (Geometry.TREBLE_TOP + 4f + Geometry.BASS_TOP) / 2f

    fun of(op: DrawOp): Int = when (op) {
        is TextOp -> NONE
        is RectOp -> if (op.height >= Geometry.SYSTEM_BOTTOM - 0.01f) NONE else side(op.y + op.height / 2)
        is GlyphOp -> if (op.codepoint in 0xE520..0xE53F) NONE else side(op.y)
        is PolygonOp -> {
            val ys = op.points.filterIndexed { i, _ -> i % 2 == 1 }
            if (ys.min() > Geometry.SYSTEM_BOTTOM + 0.3f) NONE else side((ys.min() + ys.max()) / 2)
        }
    }

    private fun side(y: Float) = if (y < SPLIT) RIGHT else LEFT
}
