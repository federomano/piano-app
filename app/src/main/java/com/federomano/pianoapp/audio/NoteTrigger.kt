package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Note

/**
 * Decide qué notas tocar y soltar a medida que la posición avanza. No sabe de audio: recibe funciones para
 * tocar y soltar. La posición que se le da es el tiempo musical exacto (en ticks), así el ritmo que se
 * escucha es siempre el de la partitura.
 *
 * Una nota ligada a la siguiente (misma mano y altura, que empieza justo donde termina) no vuelve a atacar:
 * el sonido se sostiene hasta el final de la segunda.
 */
class NoteTrigger(notes: List<Note>) {
    private val byStart = notes.sortedBy { it.startTick }
    private var next = 0

    private class Sounding(var note: Note, val id: Int)
    private val sounding = ArrayList<Sounding>()

    /** Empieza a mirar desde [tick]: las notas que empezaron antes no suenan. */
    fun reset(tick: Float) {
        next = byStart.indexOfFirst { it.startTick >= tick }.let { if (it < 0) byStart.size else it }
    }

    /** La posición llegó a [tick]: toca las notas que empiezan hasta ahí y suelta las que terminan hasta ahí. */
    fun advance(tick: Float, noteOn: (Note) -> Int, noteOff: (Int) -> Unit) {
        while (next < byStart.size && byStart[next].startTick <= tick) {
            val note = byStart[next++]
            val held = sounding.firstOrNull { it.note.continuesInto(note) }
            if (held != null) held.note = note else sounding += Sounding(note, noteOn(note))
        }
        val it = sounding.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.note.endTick <= tick) { noteOff(s.id); it.remove() }
        }
    }

    /** Suelta todo lo que esté sonando (pausa, salto, arrastre). */
    fun releaseAll(noteOff: (Int) -> Unit) {
        sounding.forEach { noteOff(it.id) }
        sounding.clear()
    }

    private fun Note.continuesInto(other: Note) =
        tiedToNext && hand == other.hand && pitch == other.pitch && endTick == other.startTick
}
