package com.federomano.pianoapp.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/**
 * Captura del micrófono (PIA-30). Pide el audio sin procesar (sin control automático de ganancia ni supresión de ruido) cuando el
 * celu lo permite; si no, usa la fuente de reconocimiento de voz y apaga esos filtros a mano. Si el micrófono se corta (llamada,
 * otra app, un cambio del sistema) vuelve a abrirlo solo. No guarda nada: cada bloque llega a quien escucha y se descarta.
 *
 * Necesita el permiso RECORD_AUDIO ya concedido. Las llamadas al [Listener] llegan desde un hilo propio.
 */
class MicCapture(context: Context, val sampleRate: Int = SAMPLE_RATE) {
    enum class Source(val label: String, val raw: Boolean) {
        UNPROCESSED("sin procesar", true),
        VOICE_RECOGNITION("modo reconocimiento de voz (este celu no ofrece el audio sin procesar)", false),
    }

    enum class State { LISTENING, INTERRUPTED, FAILED }

    interface Listener {
        fun onSamples(samples: ShortArray, count: Int)
        fun onState(state: State, source: Source?)
    }

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var running = false
    private var thread: Thread? = null

    private val supportsUnprocessed: Boolean
        get() = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    @Synchronized
    fun start(listener: Listener) {
        if (running) return
        running = true
        thread = Thread({ loop(listener) }, "mic-capture").also { it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.let {
            it.interrupt()
            runCatching { it.join(800) }
        }
        thread = null
    }

    private class Opened(val record: AudioRecord, val source: Source, val effects: List<AudioEffect>)

    @SuppressLint("MissingPermission")
    private fun open(): Opened? {
        val sources = if (supportsUnprocessed) listOf(Source.UNPROCESSED, Source.VOICE_RECOGNITION) else listOf(Source.VOICE_RECOGNITION)
        for (source in sources) {
            val androidSource = if (source == Source.UNPROCESSED) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val record = try {
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                AudioRecord.Builder()
                    .setAudioSource(androidSource)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minimum * 2, BLOCK_FRAMES * 4))
                    .build()
            } catch (e: Exception) {
                null
            } ?: continue
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            return Opened(record, source, if (source.raw) emptyList() else switchOffFilters(record.audioSessionId))
        }
        return null
    }

    /** Con la fuente de voz, el sistema puede aplicar filtros pensados para hablar: se apagan (si el celu los tiene). */
    private fun switchOffFilters(session: Int): List<AudioEffect> {
        val effects = ArrayList<AudioEffect>()
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.let { it.enabled = false; effects += it } }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.let { it.enabled = false; effects += it } }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.let { it.enabled = false; effects += it } }
        return effects
    }

    private fun loop(listener: Listener) {
        var failures = 0
        val buffer = ShortArray(BLOCK_FRAMES)
        while (running) {
            val opened = open()
            if (opened == null) {
                failures++
                if (failures >= MAX_FAILURES) {
                    listener.onState(State.FAILED, null)
                    running = false
                    break
                }
                listener.onState(State.INTERRUPTED, null)
                if (!pause(RETRY_MILLIS)) break
                continue
            }
            val record = opened.record
            try {
                record.startRecording()
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    failures = 0
                    listener.onState(State.LISTENING, opened.source)
                    while (running) {
                        val n = record.read(buffer, 0, buffer.size)
                        if (n > 0) listener.onSamples(buffer, n) else if (n < 0) break
                    }
                } else {
                    failures++
                }
            } catch (e: IllegalStateException) {
                failures++
            } finally {
                runCatching { record.stop() }
                opened.effects.forEach { runCatching { it.release() } }
                record.release()
            }
            if (running) {
                if (failures >= MAX_FAILURES) {
                    listener.onState(State.FAILED, null)
                    running = false
                    break
                }
                // El micrófono se cortó (llamada, otra app): se intenta de nuevo en un momento.
                listener.onState(State.INTERRUPTED, opened.source)
                if (!pause(RETRY_MILLIS)) break
            }
        }
    }

    private fun pause(millis: Long): Boolean =
        try { Thread.sleep(millis); running } catch (e: InterruptedException) { false }

    companion object {
        const val SAMPLE_RATE = 44100
        const val BLOCK_FRAMES = 1024
        private const val RETRY_MILLIS = 400L
        private const val MAX_FAILURES = 6
    }
}
