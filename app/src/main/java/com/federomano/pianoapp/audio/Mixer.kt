package com.federomano.pianoapp.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.exp

/**
 * Mezclador de voces: cada nota es una muestra que se lee a cierta velocidad (con interpolación lineal),
 * con volumen propio y un apagado suave cuando se suelta la tecla. Es Kotlin puro (se prueba sin el celular).
 * Los pedidos (tocar / soltar) pueden llegar desde cualquier hilo; [render] los aplica en el hilo de audio.
 */
class Mixer(private val sampleRate: Int = PianoMapping.SAMPLE_RATE, private val maxVoices: Int = 32) {

    class Voice internal constructor(val id: Int, val data: ShortArray, val rate: Float, var gain: Float) {
        internal var position = 0.0
        internal var releasing = false
        internal var done = false
    }

    private sealed interface Command
    private class On(val voice: Voice) : Command
    private class Off(val id: Int) : Command
    private object AllOff : Command

    private val commands = ConcurrentLinkedQueue<Command>()
    private val voices = ArrayList<Voice>()
    private var nextId = 1
    private val releasePerFrame = exp(-1.0 / (RELEASE_SECONDS * sampleRate)).toFloat()
    private val releaseGains = HashMap<Int, Float>()

    @Volatile var activeVoices = 0
        private set

    /** Pide tocar [data] a velocidad [rate] y volumen [gain]. Devuelve el número para soltarla después. */
    fun noteOn(data: ShortArray, rate: Float, gain: Float): Int {
        val id = synchronized(this) { nextId++ }
        commands.add(On(Voice(id, data, rate, gain)))
        return id
    }

    fun noteOff(id: Int) { commands.add(Off(id)) }

    /** Suelta todas las notas que suenan (con el apagado suave de siempre). */
    fun allOff() { commands.add(AllOff) }

    /** Calcula [frames] cuadros de audio mono en [out] (float, -1..1). */
    fun render(out: FloatArray, frames: Int) {
        while (true) {
            when (val c = commands.poll() ?: break) {
                is On -> {
                    if (voices.size >= maxVoices) voices.removeAt(0).also { releaseGains.remove(it.id) }
                    voices.add(c.voice)
                }
                is Off -> voices.firstOrNull { it.id == c.id }?.releasing = true
                AllOff -> voices.forEach { it.releasing = true }
            }
        }
        out.fill(0f, 0, frames)
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            var g = releaseGains[v.id] ?: 1f
            var pos = v.position
            val data = v.data
            val last = data.size - 1
            var i = 0
            while (i < frames) {
                val p = pos.toInt()
                if (p >= last) { v.done = true; break }
                val frac = (pos - p).toFloat()
                val s = (data[p] * (1f - frac) + data[p + 1] * frac) * (1f / 32768f)
                out[i] += s * v.gain * g
                pos += v.rate
                if (v.releasing) {
                    g *= releasePerFrame
                    if (g < SILENCE) { v.done = true; break }
                }
                i++
            }
            v.position = pos
            if (v.done) { it.remove(); releaseGains.remove(v.id) } else if (v.releasing) releaseGains[v.id] = g
        }
        activeVoices = voices.size
        for (i in 0 until frames) out[i] = softLimit(out[i])
    }

    companion object {
        const val RELEASE_SECONDS = 0.18f
        private const val SILENCE = 0.001f

        /** Hasta este nivel el sonido pasa sin tocarse; de ahí para arriba se comprime suave y nunca llega al tope. */
        const val LIMIT_KNEE = 0.6f

        /** Limitador suave: evita el recorte duro (que suena a distorsión) cuando suenan varias notas fuertes a la vez. */
        fun softLimit(x: Float): Float {
            val a = Math.abs(x)
            if (a <= LIMIT_KNEE) return x
            val room = 1f - LIMIT_KNEE
            val y = LIMIT_KNEE + room * Math.tanh(((a - LIMIT_KNEE) / room).toDouble()).toFloat()
            return if (x < 0f) -y else y
        }
    }
}
