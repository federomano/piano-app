package com.federomano.pianoapp.score.layout

/**
 * Curva suave y siempre creciente que pasa exactamente por los puntos dados (instante, posición).
 * Entre dos notas la velocidad cambia de a poco en vez de pegar un escalón, pero cada nota sigue
 * cayendo en su posición justa. Es una interpolación cúbica monótona (Fritsch–Carlson).
 */
class Curve(ticks: List<Float>, positions: List<Float>) {
    private val t = DoubleArray(ticks.size) { ticks[it].toDouble() }
    private val x = DoubleArray(positions.size) { positions[it].toDouble() }
    private val d = DoubleArray(t.size)

    init {
        require(t.size >= 2 && t.size == x.size) { "La curva necesita al menos dos puntos." }
        require((1 until t.size).all { t[it] > t[it - 1] && x[it] >= x[it - 1] }) { "Los puntos tienen que ir creciendo." }
        val n = t.size
        val h = DoubleArray(n - 1) { t[it + 1] - t[it] }
        val delta = DoubleArray(n - 1) { (x[it + 1] - x[it]) / h[it] }
        d[0] = delta[0]
        d[n - 1] = delta[n - 2]
        for (k in 1 until n - 1) {
            d[k] = if (delta[k - 1] <= 0.0 || delta[k] <= 0.0) {
                0.0
            } else {
                // Media armónica ponderada: nunca se pasa ni retrocede entre dos puntos.
                val w1 = 2 * h[k] + h[k - 1]
                val w2 = h[k] + 2 * h[k - 1]
                (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k])
            }
        }
    }

    /** Posición en el instante [tick]. Fuera de los extremos sigue recto con la velocidad del extremo. */
    fun positionAt(tick: Float): Float {
        val v = tick.toDouble()
        if (v <= t.first()) return (x.first() + (v - t.first()) * d.first()).toFloat()
        if (v >= t.last()) return x.last().toFloat()
        var lo = 0
        var hi = t.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (t[mid] <= v) lo = mid else hi = mid
        }
        val h = t[hi] - t[lo]
        val s = (v - t[lo]) / h
        val s2 = s * s
        val s3 = s2 * s
        return ((2 * s3 - 3 * s2 + 1) * x[lo] + (s3 - 2 * s2 + s) * h * d[lo] +
            (-2 * s3 + 3 * s2) * x[hi] + (s3 - s2) * h * d[hi]).toFloat()
    }

    /** Lo inverso: el instante que corresponde a una posición. */
    fun tickAt(position: Float): Float {
        val p = position.toDouble()
        if (p <= x.first()) return (t.first() + (p - x.first()) / d.first().coerceAtLeast(1e-9)).toFloat()
        if (p >= x.last()) return t.last().toFloat()
        var lo = t.first()
        var hi = t.last()
        repeat(50) {
            val mid = (lo + hi) / 2
            if (positionAt(mid.toFloat()) < p) lo = mid else hi = mid
        }
        return ((lo + hi) / 2).toFloat()
    }
}
