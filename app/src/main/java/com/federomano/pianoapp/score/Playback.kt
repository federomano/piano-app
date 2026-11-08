package com.federomano.pianoapp.score

/**
 * Cuenta de tiempo de la reproducción: pasa de segundos a posición en la partitura (ticks) respetando
 * los cambios de tempo. El tempo está en negras por minuto.
 */
class TempoMap(tempos: List<TempoChange>) {
    private val changes = tempos.sortedBy { it.startTick }.ifEmpty { listOf(TempoChange(0, 100.0)) }

    private fun segmentAt(tick: Float): Int = changes.indexOfLast { it.startTick <= tick }.coerceAtLeast(0)

    /** Tempo (negras por minuto) en ese instante. */
    fun bpmAt(tick: Float): Float = changes[segmentAt(tick)].bpm.toFloat()

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

/** Velocidad de reproducción: 1 = el tempo de la partitura. Solo cambia el tiempo; el tono de las notas no. */
object Speed {
    const val MIN = 0.25f
    const val MAX = 2f
    const val STEP = 0.05f
    val PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)

    /** Deja la velocidad dentro del rango y en pasos de 5%. */
    fun snap(value: Float): Float = (Math.round(value / STEP) * STEP).coerceIn(MIN, MAX)

    /** Negras por minuto que se escuchan a esa velocidad. */
    fun effectiveBpm(bpm: Float, speed: Float): Float = bpm * speed

    fun percent(speed: Float): Int = Math.round(speed * 100f)
}
