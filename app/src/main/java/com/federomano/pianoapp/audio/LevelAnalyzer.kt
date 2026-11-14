package com.federomano.pianoapp.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/** Cómo viene la señal del micrófono, en palabras simples (PIA-30). */
enum class LevelStatus {
    /** No llega casi nada: no se tocó, o el micrófono está tapado, o lo usa otra app. */
    SILENT,

    /** Llega, pero tan bajito que la detección va a fallar. */
    TOO_LOW,

    /** Nivel bueno para analizar. */
    GOOD,

    /** Se pasa del máximo: el sonido se distorsiona. */
    CLIPPING,
}

/**
 * [peakDb] y [rmsDb]: nivel del último bloque, en dB respecto del máximo posible (0 = tope, -60 = bajísimo).
 * [holdDb]: el pico más alto de los últimos segundos (para que el medidor no parpadee entre nota y nota).
 */
class LevelReading(val peakDb: Float, val rmsDb: Float, val holdDb: Float, val status: LevelStatus)

/**
 * Mide el nivel de lo que entra por el micrófono. No guarda el audio: solo se queda con unos pocos números por bloque.
 * El estado se decide mirando una ventana de pocos segundos, para que el silencio entre dos notas no dispare avisos.
 */
class LevelAnalyzer(private val sampleRate: Int) {
    private class Block(val endFrame: Long, val peak: Int, val clipped: Boolean)

    private val blocks = ArrayDeque<Block>()
    private var frames = 0L

    fun process(samples: ShortArray, count: Int): LevelReading {
        var peak = 0
        var sumSquares = 0.0
        var nearFull = 0
        for (i in 0 until count) {
            val v = abs(samples[i].toInt())
            if (v > peak) peak = v
            if (v >= CLIP_LEVEL) nearFull++
            sumSquares += v.toDouble() * v
        }
        frames += count
        blocks.addLast(Block(frames, peak, nearFull >= CLIP_SAMPLES))
        val oldest = frames - (WINDOW_SECONDS * sampleRate).toLong()
        while (blocks.isNotEmpty() && blocks.first().endFrame < oldest) blocks.removeFirst()

        var hold = 0
        var clipped = false
        val clipFrom = frames - (CLIP_MEMORY_SECONDS * sampleRate).toLong()
        for (b in blocks) {
            hold = max(hold, b.peak)
            if (b.clipped && b.endFrame >= clipFrom) clipped = true
        }
        val rms = if (count > 0) Math.sqrt(sumSquares / count) else 0.0
        val holdDb = toDb(hold.toDouble())
        val status = when {
            clipped -> LevelStatus.CLIPPING
            holdDb < SILENT_DB -> LevelStatus.SILENT
            holdDb < LOW_DB -> LevelStatus.TOO_LOW
            else -> LevelStatus.GOOD
        }
        return LevelReading(toDb(peak.toDouble()), toDb(rms), holdDb, status)
    }

    fun reset() {
        blocks.clear()
        frames = 0
    }

    companion object {
        /** Más bajo que esto (dB respecto del tope): no se oye nada. */
        const val SILENT_DB = -55f

        /** Más bajo que esto: se oye, pero demasiado bajito. */
        const val LOW_DB = -36f

        const val WINDOW_SECONDS = 2.0
        const val CLIP_MEMORY_SECONDS = 1.5

        /** Una muestra tan cerca del tope se cuenta como "pegada al techo"; con 3 en un bloque, es saturación. */
        const val CLIP_LEVEL = 32000
        const val CLIP_SAMPLES = 3

        const val FLOOR_DB = -120f

        fun toDb(amplitude: Double): Float =
            if (amplitude <= 0.0) FLOOR_DB else maxOf(FLOOR_DB, (20 * log10(amplitude / 32768.0)).toFloat())
    }
}
