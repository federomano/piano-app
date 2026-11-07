package com.federomano.pianoapp.score

/**
 * Cuenta de tiempo de la reproducción: pasa de segundos a posición en la partitura (ticks) respetando
 * los cambios de tempo. El tempo está en negras por minuto.
 */
class TempoMap(tempos: List<TempoChange>) {
    private val changes = tempos.sortedBy { it.startTick }.ifEmpty { listOf(TempoChange(0, 100.0)) }

    private fun segmentAt(tick: Float): Int = changes.indexOfLast { it.startTick <= tick }.coerceAtLeast(0)

    /** Cuántos ticks pasan por segundo en ese instante. */
    fun ticksPerSecond(tick: Float): Float = (changes[segmentAt(tick)].bpm * Ticks.QUARTER / 60.0).toFloat()

    /** Posición a la que se llega [seconds] segundos después de [tick], cruzando los cambios de tempo que haya. */
    fun advance(tick: Float, seconds: Float): Float {
        var position = tick
        var left = seconds
        while (left > 0f) {
            val i = segmentAt(position)
            val perSecond = ticksPerSecond(position)
            val segmentEnd = changes.getOrNull(i + 1)?.startTick?.toFloat() ?: return position + left * perSecond
            val secondsToEnd = (segmentEnd - position) / perSecond
            if (left < secondsToEnd) return position + left * perSecond
            left -= secondsToEnd
            position = segmentEnd
        }
        return position
    }

    /** Segundos que pasan entre dos posiciones. */
    fun secondsBetween(from: Float, to: Float): Float {
        var position = from
        var total = 0f
        while (position < to) {
            val i = segmentAt(position)
            val end = minOf(to, changes.getOrNull(i + 1)?.startTick?.toFloat() ?: to)
            total += (end - position) / ticksPerSecond(position)
            position = end
        }
        return total
    }
}
