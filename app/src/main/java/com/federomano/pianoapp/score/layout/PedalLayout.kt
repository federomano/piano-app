package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Score

/**
 * Marcas del pedal de resonancia (PIA-68), debajo del pentagrama de la izquierda: «Ped.» donde se aprieta y una estrella donde
 * se suelta. Si se suelta y se vuelve a apretar en el mismo instante (cambio de pedal), va un solo signo en ^ en lugar de los dos.
 * Quedan por debajo de lo ya dibujado en esa zona (notas graves con líneas adicionales, dinámicas).
 */
object PedalLayout {
    private const val GAP = 0.5f
    private const val MIN_BELOW_SYSTEM = 3.2f

    fun build(score: Score, xOfTick: (Int) -> Float, boxes: List<Box>): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        fun place(code: Int, left: Float, width: Float) {
            val lowest = Extents.lowest(boxes, left, left + width, Geometry.SYSTEM_BOTTOM)
            val baseline = maxOf(Geometry.SYSTEM_BOTTOM + MIN_BELOW_SYSTEM, lowest + GAP + Smufl.PEDAL_HEIGHT)
            ops += GlyphOp(code, left, baseline)
        }
        for ((i, pedal) in score.pedals.withIndex()) {
            val changesNext = score.pedals.getOrNull(i + 1)?.startTick == pedal.endTick
            val changesPrevious = score.pedals.getOrNull(i - 1)?.endTick == pedal.startTick
            if (!changesPrevious) place(Smufl.PEDAL_DOWN, xOfTick(pedal.startTick) - PED_LEAD, Smufl.PEDAL_DOWN_WIDTH)
            if (changesNext) place(Smufl.PEDAL_CHANGE, xOfTick(pedal.endTick) - Smufl.PEDAL_CHANGE_WIDTH / 2, Smufl.PEDAL_CHANGE_WIDTH)
            else place(Smufl.PEDAL_UP, xOfTick(pedal.endTick) - Smufl.PEDAL_UP_WIDTH - UP_LEAD, Smufl.PEDAL_UP_WIDTH)
        }
        return ops
    }

    /** «Ped.» empieza un poco antes de la nota; la estrella termina un poco antes del instante en que se suelta. */
    private const val PED_LEAD = 0.3f
    private const val UP_LEAD = 0.3f
}
