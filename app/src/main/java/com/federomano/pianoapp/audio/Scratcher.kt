package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Note

/** Una nota de piano que suena "frotada" (PIA-55): se le dice en qué punto de la muestra está, y cuánto avanza por segundo. */
interface ScratchHandle {
    /** [seconds]: cuánto hace que empezó la nota (puede ser menos que antes: suena al revés). [rate]: segundos de nota por segundo real. */
    fun move(seconds: Float, rate: Float)
    fun release()
}

/**
 * Efecto vinilo (PIA-55): mientras se arrastra la partitura (o frena por inercia), suena la música de la posición donde
 * está la línea de reproducción, como si la partitura fuera un disco que se frota. Cada nota que cubre esa posición se
 * lee con la velocidad del gesto: más rápido = más agudo, más lento = más grave, hacia atrás = invertido, quieto = silencio.
 *
 * No sabe de audio ni de pantalla: recibe funciones para crear la voz de una nota y para medir segundos. Las ligaduras se
 * tratan como una sola nota larga (no se vuelve a atacar en la unión).
 */
class Scratcher(notes: List<Note>) {
    private class Span(val note: Note, val endTick: Int)

    private val spans: List<Span>
    private val maxLength: Int

    init {
        val sorted = notes.sortedBy { it.startTick }
        val continued = HashSet<Int>()
        val merged = ArrayList<Span>()
        for ((i, note) in sorted.withIndex()) {
            if (i in continued) continue
            var end = note.endTick
            var last = note
            var from = i + 1
            while (last.tiedToNext) {
                val j = (from until sorted.size).firstOrNull {
                    it !in continued && sorted[it].startTick == end && sorted[it].hand == note.hand && sorted[it].pitch == note.pitch
                } ?: break
                continued += j
                last = sorted[j]
                end = last.endTick
                from = j + 1
            }
            merged += Span(note, end)
        }
        spans = merged
        maxLength = merged.maxOfOrNull { it.endTick - it.note.startTick } ?: 0
    }

    private class Entry(val handle: ScratchHandle, var seconds: Float)

    private val active = HashMap<Span, Entry>()

    /**
     * La línea de reproducción está en [tick] y pasaron [frameSeconds] segundos desde la última llamada.
     * [secondsSince]: segundos de música entre el inicio de una nota y ese tick, a la velocidad de reproducción elegida.
     * [silenced]: notas que no deben sonar (mano silenciada). [start]: crea la voz de una nota en ese punto (null si no suena).
     */
    fun update(
        tick: Float,
        frameSeconds: Float,
        secondsSince: (startTick: Int, tick: Float) -> Float,
        silenced: (Note) -> Boolean,
        start: (Note, Float) -> ScratchHandle?,
    ) {
        val now = ArrayList<Span>()
        var i = firstIndexAtLeast(tick - maxLength)
        while (i < spans.size && spans[i].note.startTick <= tick) {
            val s = spans[i]
            if (tick < s.endTick && !silenced(s.note)) now += s
            i++
        }
        // Los que quedaron más cerca de la línea son los que más se oyen: si son demasiados, se descartan los más viejos.
        val keep = if (now.size > MAX_VOICES) now.subList(now.size - MAX_VOICES, now.size).toSet() else now.toSet()
        val gone = active.keys.filter { it !in keep }
        for (s in gone) active.remove(s)?.handle?.release()
        for (s in keep) {
            val seconds = secondsSince(s.note.startTick, tick)
            val entry = active[s]
            if (entry == null) {
                start(s.note, seconds)?.let { active[s] = Entry(it, seconds) }
            } else {
                val rate = if (frameSeconds > 0f) (seconds - entry.seconds) / frameSeconds else 0f
                entry.handle.move(seconds, rate)
                entry.seconds = seconds
            }
        }
    }

    /** Termina el efecto: las voces se apagan suavemente. */
    fun stop() {
        active.values.forEach { it.handle.release() }
        active.clear()
    }

    private fun firstIndexAtLeast(tick: Float): Int {
        var lo = 0
        var hi = spans.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (spans[mid].note.startTick < tick) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        const val MAX_VOICES = 16
    }
}
