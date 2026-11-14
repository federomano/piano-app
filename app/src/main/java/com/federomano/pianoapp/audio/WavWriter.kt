package com.federomano.pianoapp.audio

import java.io.OutputStream

/** Escribe audio PCM de 16 bits, mono, en formato WAV (el estándar de siempre, que lee cualquier programa). */
object WavWriter {
    const val HEADER_BYTES = 44

    fun write(out: OutputStream, samples: ShortArray, count: Int, sampleRate: Int) {
        val dataBytes = count * 2
        val header = ByteArray(HEADER_BYTES)
        fun put(offset: Int, text: String) = text.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        fun put32(offset: Int, value: Int) { for (i in 0 until 4) header[offset + i] = (value shr (8 * i)).toByte() }
        fun put16(offset: Int, value: Int) { for (i in 0 until 2) header[offset + i] = (value shr (8 * i)).toByte() }
        put(0, "RIFF"); put32(4, 36 + dataBytes); put(8, "WAVE")
        put(12, "fmt "); put32(16, 16); put16(20, 1) /* PCM */; put16(22, 1) /* mono */
        put32(24, sampleRate); put32(28, sampleRate * 2); put16(32, 2); put16(34, 16)
        put(36, "data"); put32(40, dataBytes)
        out.write(header)
        val chunk = ByteArray(8192)
        var i = 0
        while (i < count) {
            val n = minOf(chunk.size / 2, count - i)
            for (j in 0 until n) {
                val v = samples[i + j].toInt()
                chunk[2 * j] = v.toByte()
                chunk[2 * j + 1] = (v shr 8).toByte()
            }
            out.write(chunk, 0, n * 2)
            i += n
        }
    }

    /** Lee de vuelta un WAV hecho con [write]: (muestras, frecuencia). Solo entiende ese formato. */
    fun read(bytes: ByteArray): Pair<ShortArray, Int> {
        require(bytes.size >= HEADER_BYTES && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "No es un WAV" }
        fun int32(o: Int) = (0 until 4).sumOf { (bytes[o + it].toInt() and 0xFF) shl (8 * it) }
        val rate = int32(24)
        val count = int32(40) / 2
        val samples = ShortArray(count) { ((bytes[HEADER_BYTES + 2 * it].toInt() and 0xFF) or (bytes[HEADER_BYTES + 2 * it + 1].toInt() shl 8)).toShort() }
        return samples to rate
    }
}
