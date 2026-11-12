package com.federomano.pianoapp.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.exp

/**
 * Mezclador de voces: cada nota es una muestra que se lee a cierta velocidad (con interpolación lineal),
 * con volumen propio y un apagado suave cuando se suelta la tecla. Es Kotlin puro (se prueba sin el celular).
 * Los pedidos (tocar / soltar) pueden llegar desde cualquier hilo; [render] los aplica en el hilo de audio.
 */
class Mixer(internal val sampleRate: Int = PianoMapping.SAMPLE_RATE, private val maxVoices: Int = 32) {

    class Voice internal constructor(val id: Int, val data: ShortArray, val rate: Float, var gain: Float) {
        internal var position = 0.0
        internal var releasing = false
        internal var done = false
    }

    /**
     * Voz de efecto vinilo (PIA-55): en vez de avanzar sola, sigue un punto de la muestra que le pasan desde afuera
     * ([moveTo]). Lo que se oye es la velocidad con que ese punto se mueve: rápido = agudo, lento = grave, para atrás = al revés,
     * quieto = silencio.
     */
    class ScratchVoice internal constructor(
        private val mixer: Mixer,
        internal val data: ShortArray,
        /** Cuadros de muestra que avanza por cuadro de salida a velocidad normal. */
        internal val nominal: Float,
        /** Cuadros de muestra por segundo de música a velocidad normal. */
        private val perSecond: Double,
        internal val gain: Float,
        startSeconds: Double,
    ) : ScratchHandle {
        @Volatile internal var target = startSeconds * perSecond
        /** Cuadros de muestra por cuadro de salida con que se sigue moviendo el punto hasta la próxima actualización. */
        @Volatile internal var drift = 0.0
        internal var position = target
        internal var sinceUpdate = 0
        internal var releasing = false
        internal var done = false
        internal var releaseGain = 1f

        override fun move(seconds: Float, rate: Float) {
            target = seconds * perSecond
            drift = rate * perSecond / mixer.sampleRate
            sinceUpdate = 0
        }

        override fun release() = mixer.scratchOff(this)
    }

    private sealed interface Command
    private class On(val voice: Voice) : Command
    private class Off(val id: Int) : Command
    private object AllOff : Command
    private class ScratchOn(val voice: ScratchVoice) : Command
    private class ScratchOff(val voice: ScratchVoice) : Command

    private val commands = ConcurrentLinkedQueue<Command>()
    private val voices = ArrayList<Voice>()
    private var nextId = 1
    private val releasePerFrame = exp(-1.0 / (RELEASE_SECONDS * sampleRate)).toFloat()
    private val releaseGains = HashMap<Int, Float>()

    private val scratch = ArrayList<ScratchVoice>()
    private var bus = FloatArray(0)
    private val followPerFrame = 1.0 - exp(-1.0 / (SCRATCH_FOLLOW_SECONDS * sampleRate))
    private val scratchReleasePerFrame = exp(-1.0 / (SCRATCH_RELEASE_SECONDS * sampleRate)).toFloat()
    private val staleFrames = (SCRATCH_STALE_SECONDS * sampleRate).toInt()
    private val highPass = Math.exp(-2.0 * Math.PI * SCRATCH_LOW_CUT_HZ / sampleRate).toFloat()
    private val lowPass = (1.0 - exp(-2.0 * Math.PI * SCRATCH_HIGH_CUT_HZ / sampleRate)).toFloat()
    private var hpIn = 0f
    private var hpOut = 0f
    private var lp1 = 0f
    private var lp2 = 0f

    @Volatile var activeVoices = 0
        private set

    @Volatile var activeScratch = 0
        private set

    /**
     * Pide una voz de efecto vinilo sobre [data]: [nominal] es cuánto avanza la muestra por cuadro de salida a velocidad normal,
     * [perSecond] cuántos cuadros de muestra son un segundo de música, [startSeconds] el punto de arranque.
     */
    fun scratchOn(data: ShortArray, nominal: Float, perSecond: Double, gain: Float, startSeconds: Float): ScratchVoice {
        val voice = ScratchVoice(this, data, nominal, perSecond, gain, startSeconds.toDouble())
        commands.add(ScratchOn(voice))
        return voice
    }

    fun scratchOff(voice: ScratchVoice) { commands.add(ScratchOff(voice)) }

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
                AllOff -> { voices.forEach { it.releasing = true }; scratch.forEach { it.releasing = true } }
                is ScratchOn -> {
                    if (scratch.size >= MAX_SCRATCH_VOICES) scratch.removeAt(0)
                    scratch.add(c.voice)
                }
                is ScratchOff -> c.voice.releasing = true
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
        renderScratch(out, frames)
        for (i in 0 until frames) out[i] = softLimit(out[i])
    }

    /** Mezcla las voces de vinilo en [out], con su ecualización propia (sin graves ni agudos extremos, como un scratch). */
    private fun renderScratch(out: FloatArray, frames: Int) {
        if (scratch.isEmpty()) { activeScratch = 0; return }
        if (bus.size < frames) bus = FloatArray(frames)
        bus.fill(0f, 0, frames)
        val it = scratch.iterator()
        while (it.hasNext()) {
            val v = it.next()
            val data = v.data
            val last = data.size - 1
            var pos = v.position
            var i = 0
            while (i < frames) {
                // Si dejan de llegar actualizaciones, el punto no sigue avanzando para siempre.
                if (v.sinceUpdate < staleFrames) { v.target += v.drift; v.sinceUpdate++ }
                val before = pos
                pos += (v.target - pos) * followPerFrame
                val speed = Math.abs(pos - before).toFloat() / v.nominal
                val p = pos.toInt()
                if (pos >= 0.0 && p < last) {
                    val frac = (pos - p).toFloat()
                    val s = (data[p] * (1f - frac) + data[p + 1] * frac) * (1f / 32768f)
                    bus[i] += s * v.gain * v.releaseGain * minOf(1f, speed / SCRATCH_FULL_SPEED)
                }
                if (v.releasing) {
                    v.releaseGain *= scratchReleasePerFrame
                    if (v.releaseGain < SILENCE) { v.done = true; break }
                }
                i++
            }
            v.position = pos
            if (v.done) it.remove()
        }
        activeScratch = scratch.size
        for (i in 0 until frames) {
            val x = bus[i]
            hpOut = highPass * (hpOut + x - hpIn)
            hpIn = x
            lp1 += lowPass * (hpOut - lp1)
            lp2 += lowPass * (lp1 - lp2)
            out[i] += lp2 * SCRATCH_GAIN
        }
    }

    companion object {
        const val RELEASE_SECONDS = 0.18f

        /** Qué tan rápido el punto de la muestra alcanza al que le piden (más chico = más pegado al dedo). */
        const val SCRATCH_FOLLOW_SECONDS = 0.01f
        const val SCRATCH_RELEASE_SECONDS = 0.04f
        const val SCRATCH_STALE_SECONDS = 0.08f

        /** A esta velocidad (1 = normal) o más, la voz suena a todo volumen; más lento se va apagando hasta el silencio. */
        const val SCRATCH_FULL_SPEED = 0.12f
        const val SCRATCH_LOW_CUT_HZ = 280.0
        const val SCRATCH_HIGH_CUT_HZ = 3800.0
        const val SCRATCH_GAIN = 1.5f
        const val MAX_SCRATCH_VOICES = 24
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
