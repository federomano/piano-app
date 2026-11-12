package com.federomano.pianoapp.score

/** Repetir un tramo en bucle (PIA-24): el tramo se marca por compases y la cuenta se hace en ticks. */
object Loop {
    /**
     * Inicio y fin (en ticks, el fin no se incluye) del tramo que va del compás [first] al compás [second], en
     * cualquier orden. [measureStarts] son los inicios de todos los compases; el último compás termina en [endTick].
     */
    fun ticks(measureStarts: List<Int>, endTick: Int, first: Int, second: Int): Pair<Int, Int> {
        val a = minOf(first, second).coerceIn(0, measureStarts.lastIndex)
        val b = maxOf(first, second).coerceIn(0, measureStarts.lastIndex)
        return measureStarts[a] to (measureStarts.getOrNull(b + 1) ?: endTick)
    }

    /** Si al pasar de [previous] a [now] se cruzó el final del tramo (y no se empezó ya después de él). */
    fun crossedEnd(previous: Float, now: Float, endTick: Int): Boolean = previous < endTick && now >= endTick

    /** Número del compás para mostrar, dado el índice y el número del primero (0 si hay anacrusa). */
    fun label(index: Int, firstMeasureNumber: Int): Int = index + firstMeasureNumber
}
