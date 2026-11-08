package com.federomano.pianoapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Piano con muestras grabadas: carga las 60 muestras de assets/piano, las mezcla con [Mixer] y las manda a
 * un AudioTrack de baja latencia. Corre a la frecuencia nativa del celular para no perder el camino rápido de Android.
 */
class PianoPlayer(private val context: Context) {
    private val mixer = Mixer(nativeRate(context))
    private val samples = HashMap<String, ShortArray>()
    private val ratio = PianoMapping.SAMPLE_RATE.toFloat() / nativeRate(context)

    @Volatile var ready = false
        private set
    @Volatile var loadMillis = 0L
        private set
    @Volatile var error: String? = null
        private set
    @Volatile var info = ""
        private set
    @Volatile var underruns = 0
        private set
    val activeVoices: Int get() = mixer.activeVoices

    /** Silenciado: las notas no suenan (y lo que sonaba se apaga), pero todo lo demás sigue igual. */
    var muted = false
        set(value) {
            field = value
            if (value) mixer.allOff()
        }

    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "piano-audio", priority = Thread.MAX_PRIORITY) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                val t0 = SystemClock.elapsedRealtime()
                val rate = nativeRate(context)
                val burst = framesPerBurst(context)
                val track = createTrack(rate, burst)
                // Primero el audio (sin sonido), así el sistema ya lo tiene andando cuando termina la carga.
                load()
                loadMillis = SystemClock.elapsedRealtime() - t0
                ready = true
                info = "Audio: $rate Hz, bloque de $burst cuadros (${"%.1f".format(burst * 1000f / rate)} ms), " +
                    "colchón de ${track.bufferSizeInFrames} cuadros (${"%.1f".format(track.bufferSizeInFrames * 1000f / rate)} ms)"
                track.play()
                val block = FloatArray(burst)
                while (running) {
                    mixer.render(block, burst)
                    track.write(block, 0, burst, AudioTrack.WRITE_BLOCKING)
                    underruns = track.underrunCount
                }
                track.stop()
                track.release()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
        ready = false
    }

    /** Toca una nota (número MIDI, 60 = Do central) con intensidad 1..127. Devuelve un número para soltarla. */
    fun noteOn(midi: Int, velocity: Int = 90): Int {
        if (muted) return -1
        val pick = PianoMapping.pick(midi, velocity)
        val data = samples[pick.fileName] ?: return -1
        return mixer.noteOn(data, pick.rate * ratio, pick.gain)
    }

    fun noteOff(id: Int) { if (id >= 0) mixer.noteOff(id) }

    fun allOff() = mixer.allOff()

    private fun load() {
        for (note in PianoMapping.sampleNotes) for (loud in listOf(false, true)) {
            val name = PianoMapping.Pick(note, loud, 1f, 1f).fileName
            val bytes = context.assets.open(name).use { it.readBytes() }
            val out = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
            samples[name] = out
        }
    }

    private fun createTrack(rate: Int, burst: Int): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBytes, burst * 2 * 4))
            .build()
        track.setBufferSizeInFrames(burst * 2)
        return track
    }

    companion object {
        fun nativeRate(context: Context): Int =
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

        fun framesPerBurst(context: Context): Int =
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
    }
}
