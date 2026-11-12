package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.TimeSignatureChange
import com.federomano.pianoapp.score.Ticks
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Metrónomo (PIA-23): dónde caen los clics según la firma de compás. Va por posición en la partitura (ticks), así
 * sigue solo el tempo y la velocidad elegida. Es Kotlin puro (se prueba sin el celular).
 *
 * Un clic por tiempo; el primero de cada compás es el acentuado. En compases compuestos (6/8, 9/8, 12/8) el tiempo
 * es la negra con puntillo, y en los demás es la figura de abajo de la firma (2/2 → blanca, 4/4 → negra).
 */
class Metronome(measures: List<Measure>) {
    class Click(val tick: Int, val accent: Boolean)

    val clicks: List<Click> = buildList {
        for (m in measures) {
            val beat = beatTicks(m.timeSignature)
            // La grilla de la anacrusa se alinea con el final del compás, no con el principio.
            var t = m.startTick - m.gridOffset
            while (t < m.endTick) {
                if (t >= m.startTick) add(Click(t, accent = t == m.startTick && m.gridOffset == 0))
                t += beat
            }
        }
    }

    private var next = 0

    /** Empieza a mirar desde [tick]: los clics que caen antes no suenan; el que cae justo en [tick] sí. */
    fun reset(tick: Float) {
        next = clicks.indexOfFirst { it.tick >= tick }.let { if (it < 0) clicks.size else it }
    }

    /** La posición llegó a [tick]: avisa de cada clic que cayó hasta ahí (true = acentuado). */
    fun advance(tick: Float, onClick: (accent: Boolean) -> Unit) {
        while (next < clicks.size && clicks[next].tick <= tick) onClick(clicks[next++].accent)
    }

    companion object {
        /** Ticks que dura un tiempo en esa firma. */
        fun beatTicks(signature: TimeSignatureChange): Int {
            val compound = signature.denominator >= 8 && signature.numerator % 3 == 0
            return if (compound) Ticks.dotted(Ticks.QUARTER) else 4 * Ticks.QUARTER / signature.denominator
        }

        /** Cuántos clics tiene la cuenta de un compás antes de empezar (4 en un 4/4, 3 en un 3/4, 2 en un 6/8). */
        fun countInBeats(signature: TimeSignatureChange): Int =
            maxOf(1, signature.measureTicks / beatTicks(signature))
    }
}

/** El sonido del clic: un tono corto que se apaga rápido, más agudo el del primer tiempo. */
object ClickSound {
    private const val ACCENT_HZ = 1700.0
    private const val NORMAL_HZ = 1100.0
    private const val SECONDS = 0.05
    private const val DECAY_SECONDS = 0.009
    private const val ATTACK_SECONDS = 0.0008
    private const val PEAK = 0.9

    fun render(accent: Boolean, sampleRate: Int = PianoMapping.SAMPLE_RATE): ShortArray {
        val hz = if (accent) ACCENT_HZ else NORMAL_HZ
        val attack = (ATTACK_SECONDS * sampleRate).toInt().coerceAtLeast(1)
        return ShortArray((SECONDS * sampleRate).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t / DECAY_SECONDS) * minOf(1.0, (i + 1).toDouble() / attack)
            (sin(2 * PI * hz * t) * envelope * PEAK * 32767).toInt().toShort()
        }
    }
}
