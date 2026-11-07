package com.federomano.pianoapp.audio

import kotlin.math.pow

/**
 * Qué muestra grabada usar para tocar una nota. Hay una muestra cada 3 semitonos (de La0 = 21 a Do8 = 108)
 * en dos intensidades; las notas del medio se obtienen cambiando la velocidad de reproducción (como mucho 1,5 semitonos).
 */
object PianoMapping {
    const val LOWEST = 21
    const val HIGHEST = 108
    const val STEP = 3
    const val SAMPLE_RATE = 44100

    /** Hasta esta intensidad (0..127) se usa la muestra suave; desde acá, la fuerte. */
    private const val LOUD_FROM = 85

    class Pick(val sampleNote: Int, val loud: Boolean, val rate: Float, val gain: Float) {
        val fileName: String get() = "piano/p%03d%s.pcm".format(sampleNote, if (loud) "f" else "s")
    }

    val sampleNotes: List<Int> = (LOWEST..HIGHEST step STEP).toList()

    fun pick(midi: Int, velocity: Int = 90): Pick {
        val note = midi.coerceIn(LOWEST, HIGHEST)
        val v = velocity.coerceIn(1, 127)
        val sample = LOWEST + ((note - LOWEST + 1) / STEP) * STEP
        val loud = v >= LOUD_FROM
        val gain = if (loud) 0.6f + 0.4f * (v - LOUD_FROM) / (127 - LOUD_FROM) else 0.25f + 0.75f * v / LOUD_FROM
        return Pick(sample, loud, 2.0.pow((note - sample) / 12.0).toFloat(), gain)
    }
}
