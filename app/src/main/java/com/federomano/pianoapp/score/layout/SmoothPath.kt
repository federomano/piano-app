package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.TempoMap

/**
 * El recorrido de la vista durante la reproducción: la posición "exacta" de cada instante, promediada
 * en una pequeña ventana de tiempo. Así la velocidad cambia de forma gradual (sin escalones ni
 * ráfagas) y cada nota pasa casi sobre la línea de reproducción; solo se aleja un poco donde hay un
 * cambio muy brusco de espacio por tiempo, como al cruzar un cambio de armadura.
 *
 * La ventana es simétrica, así que no atrasa ni adelanta el avance en general, y en el comienzo y el
 * final de la pieza el recorrido coincide exactamente con la posición exacta.
 */
class SmoothPath private constructor(
    private val ticks: FloatArray,
    private val positions: FloatArray,
) {
    /** Posición en el instante [tick]. */
    fun positionAt(tick: Float): Float {
        if (tick <= ticks.first()) return positions.first()
        if (tick >= ticks.last()) return positions.last()
        val i = firstIndexAbove(ticks, tick)
        val span = ticks[i] - ticks[i - 1]
        return positions[i - 1] + (positions[i] - positions[i - 1]) * (tick - ticks[i - 1]) / span
    }

    /** Lo inverso: el instante que corresponde a una posición. */
    fun tickAt(position: Float): Float {
        if (position <= positions.first()) return ticks.first()
        if (position >= positions.last()) return ticks.last()
        val i = firstIndexAbove(positions, position)
        val span = positions[i] - positions[i - 1]
        if (span <= 0f) return ticks[i]
        return ticks[i - 1] + (ticks[i] - ticks[i - 1]) * (position - positions[i - 1]) / span
    }

    private fun firstIndexAbove(values: FloatArray, v: Float): Int {
        var lo = 0
        var hi = values.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (values[mid] <= v) lo = mid else hi = mid
        }
        return hi
    }

    companion object {
        private const val STEP_SECONDS = 1f / 120f

        /**
         * [exactX] da la posición exacta de un instante; [tempo] pasa de segundos a instantes;
         * [windowSeconds] es el ancho de la ventana de promedio.
         */
        fun build(endTick: Float, exactX: (Float) -> Float, tempo: TempoMap, windowSeconds: Float): SmoothPath {
            val total = tempo.secondsBetween(0f, endTick)
            val steps = maxOf(2, kotlin.math.ceil(total / STEP_SECONDS).toInt())
            val half = maxOf(1, (windowSeconds / STEP_SECONDS / 2f).toInt())

            // Instante y posición exacta en cada paso; el último paso cae justo en el final.
            val tickAt = FloatArray(steps + 1)
            for (i in 1..steps) tickAt[i] = if (i == steps) endTick else tempo.advance(tickAt[i - 1], STEP_SECONDS).coerceAtMost(endTick)
            val exact = FloatArray(steps + 1) { exactX(tickAt[it]) }

            // Fuera de la pieza se sigue con el reflejo de la posición respecto del extremo (así el promedio
            // en el comienzo y en el final da justo la posición exacta).
            fun valueAt(i: Int): Double = when {
                i < 0 -> 2.0 * exact[0] - exact[minOf(-i, steps)]
                i > steps -> 2.0 * exact[steps] - exact[maxOf(2 * steps - i, 0)]
                else -> exact[i].toDouble()
            }
            val prefix = DoubleArray(steps + 2 * half + 2)
            for (k in 0 until prefix.size - 1) prefix[k + 1] = prefix[k] + valueAt(k - half)
            val smooth = FloatArray(steps + 1) { i ->
                // Promedio de los valores en [i - half, i + half].
                ((prefix[i + 2 * half + 1] - prefix[i]) / (2 * half + 1)).toFloat()
            }
            // Se asegura que siga siempre creciendo.
            for (i in 1..steps) if (smooth[i] < smooth[i - 1]) smooth[i] = smooth[i - 1]
            return SmoothPath(tickAt, smooth)
        }
    }
}
