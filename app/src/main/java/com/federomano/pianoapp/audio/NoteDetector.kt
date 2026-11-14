package com.federomano.pianoapp.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Qué encontró el detector para la nota esperada en el último pedacito de audio (PIA-32). */
class NoteDetection(
    /** ¿Suena la nota esperada? */
    val sounding: Boolean,
    /** Qué tanto suena la nota esperada (0 = nada). Sin unidad: sirve para comparar. */
    val score: Double,
    /** El mayor puntaje entre las notas con las que se suele confundir (octavas, vecinas, quintas). */
    val rival: Double,
)

/**
 * Detecta si suena una nota puntual. Mira el último tramo de audio (unos 190 ms) y, para la nota esperada, busca energía en su
 * frecuencia y en las de sus armónicos (los pitidos más agudos que acompañan a cada nota de piano). Mirar los armónicos hace falta
 * porque en las notas graves el celu casi no capta la nota de base. La nota cuenta como «suena» si:
 *  - tiene bastante más energía que el ruido de fondo, y
 *  - tiene más puntaje que sus confusiones típicas: la misma nota una octava más arriba o abajo, las vecinas, y las quintas.
 *
 * La afinación se tolera: cada frecuencia se busca en un margen de ±[TOLERANCE_CENTS] centésimas de semitono.
 * No guarda audio: solo conserva la ventana de análisis.
 */
class NoteDetector(private val sampleRate: Int = 44100) {
    private val window = ShortArray(WINDOW)
    private var filled = 0
    private var next = 0
    private var added = 0L

    /** Cuántas muestras nuevas entraron desde la última vez que se llamó a [consumeFresh]. */
    private var fresh = 0

    /** Mete audio nuevo. Se queda con los últimos [WINDOW] datos. */
    fun process(samples: ShortArray, count: Int) {
        for (i in 0 until count) {
            window[next] = samples[i]
            next = (next + 1) % WINDOW
        }
        filled = minOf(WINDOW, filled + count)
        added += count
        fresh += count
    }

    /** ¿Ya hay suficiente audio acumulado como para analizar? */
    val ready: Boolean get() = filled >= WINDOW

    /** Devuelve true si entró audio nuevo suficiente (un [HOP]) como para volver a analizar, y lo marca como usado. */
    fun consumeFresh(): Boolean {
        if (!ready || fresh < HOP) return false
        fresh = 0
        return true
    }

    fun reset() {
        filled = 0; next = 0; added = 0; fresh = 0
    }

    /** Analiza la ventana actual para la nota [expectedMidi]. Si todavía no hay ventana completa responde «no suena». */
    fun detect(expectedMidi: Int): NoteDetection {
        if (!ready) return NoteDetection(false, 0.0, 0.0)
        val ordered = ShortArray(WINDOW) { window[(next + it) % WINDOW] }
        return analyze(ordered, sampleRate).detect(expectedMidi)
    }

    /** El espectro de un tramo de audio, listo para consultar varias notas sin repetir la cuenta. */
    class Spectrum internal constructor(private val magnitude: FloatArray, private val binHz: Double, private val floor: Double) {
        private fun band(frequency: Double): Double {
            val lo = (frequency * 2.0.pow(-TOLERANCE_CENTS / 1200.0) / binHz).toInt().coerceAtLeast(0)
            val hi = (frequency * 2.0.pow(TOLERANCE_CENTS / 1200.0) / binHz).toInt().coerceAtMost(magnitude.size - 1)
            var best = 0f
            for (k in lo..hi) if (magnitude[k] > best) best = magnitude[k]
            return best.toDouble()
        }

        /** Puntaje de una nota: promedio de cuánto sobresale de la base de ruido cada uno de sus primeros armónicos. */
        fun score(midi: Int): Double {
            val f0 = 440.0 * 2.0.pow((midi - 69) / 12.0)
            var sum = 0.0
            var weights = 0.0
            for (h in 1..HARMONICS) {
                val frequency = f0 * h
                val l = if (frequency > Nyquist * 0.9) 0.0 else ln(max(band(frequency) / floor, 1.0))
                val w = if (h == 1 && midi >= FUNDAMENTAL_FROM_MIDI) FUNDAMENTAL_WEIGHT else 1.0
                sum += w * l
                weights += w
            }
            return sum / weights
        }

        private val Nyquist get() = binHz * (magnitude.size - 1)

        fun detect(expectedMidi: Int): NoteDetection {
            val own = score(expectedMidi)
            var rival = 0.0
            for (d in RIVALS) {
                val m = expectedMidi + d
                if (m in LOWEST_MIDI..HIGHEST_MIDI) rival = max(rival, score(m))
            }
            return NoteDetection(own >= MIN_SCORE && own >= rival + MARGIN, own, rival)
        }
    }

    companion object {
        /** Muestras que se miran (unos 186 ms a 44,1 kHz): alcanza para las notas más graves. */
        const val WINDOW = 8192

        /** Cada cuántas muestras nuevas conviene volver a analizar (unos 46 ms). */
        const val HOP = 2048

        const val TOLERANCE_CENTS = 35.0
        const val HARMONICS = 6
        const val FUNDAMENTAL_FROM_MIDI = 48
        const val FUNDAMENTAL_WEIGHT = 2.0

        /** Puntaje mínimo para decir que la nota suena (en unidades de «veces sobre el ruido», en logaritmo). */
        const val MIN_SCORE = 2.0

        /** Cuánto más que sus rivales tiene que sacar la nota esperada. */
        const val MARGIN = 0.15

        /** Diferencias en semitonos con las notas con las que se confunde: octavas, vecinas, quintas y cuartas. */
        val RIVALS = intArrayOf(-12, 12, -1, 1, -2, 2, -7, 7, -5, 5)

        const val LOWEST_MIDI = 21
        const val HIGHEST_MIDI = 108

        private const val PADDING = 4
        private const val NOISE_FROM_HZ = 50.0
        private const val NOISE_TO_HZ = 4000.0

        private val hann = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * it / (WINDOW - 1)) }

        /** Analiza un tramo de exactamente [WINDOW] muestras. */
        fun analyze(samples: ShortArray, sampleRate: Int): Spectrum {
            require(samples.size == WINDOW) { "el tramo tiene que tener $WINDOW muestras" }
            val n = WINDOW * PADDING
            val re = DoubleArray(n)
            val im = DoubleArray(n)
            for (i in 0 until WINDOW) re[i] = samples[i] / 32768.0 * hann[i]
            fft(re, im)
            val bins = n / 2 + 1
            val magnitude = FloatArray(bins) { (sqrt(re[it] * re[it] + im[it] * im[it]) / WINDOW).toFloat() }
            val binHz = sampleRate.toDouble() / n
            val from = (NOISE_FROM_HZ / binHz).toInt() + 1
            val to = (NOISE_TO_HZ / binHz).toInt()
            val sorted = magnitude.copyOfRange(from, to)
            sorted.sort()
            val floor = sorted[sorted.size / 2].toDouble() + 1e-6
            return Spectrum(magnitude, binHz, floor)
        }

        /** FFT en el lugar (Cooley-Tukey, tamaño potencia de 2). */
        private fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wr = cos(angle)
                val wi = sin(angle)
                var i = 0
                while (i < n) {
                    var cr = 1.0
                    var ci = 0.0
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val xr = re[b] * cr - im[b] * ci
                        val xi = re[b] * ci + im[b] * cr
                        re[b] = re[a] - xr; im[b] = im[a] - xi
                        re[a] += xr; im[a] += xi
                        val nr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
