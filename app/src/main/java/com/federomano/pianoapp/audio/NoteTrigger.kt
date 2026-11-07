package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Note

/**
 * Decide qué notas tocar y soltar a medida que la posición avanza. No sabe de audio: recibe funciones para
 * tocar y soltar. La posición que se le da es la que se ve bajo la línea de reproducción, así lo que se
 * escucha coincide con lo que se ve.
 */
class NoteTrigger(notes: List<Note>) {
    private val byStart = notes.sortedBy { it.startTick }
    private var next = 0
    private class Sounding(val endTick: Int, val id: Int)
    private val sounding = ArrayList<Sounding>()

    /** Empieza a mirar desde [tick]: las notas que empezaron antes no suenan. */
    fun reset(tick: Float) {
        next = byStart.indexOfFirst { it.startTick >= tick }.let { if (it < 0) byStart.size else it }
    }

    /** La posición llegó a [tick]: toca las notas que empiezan hasta ahí y suelta las que terminan hasta ahí. */
    fun advance(tick: Float, noteOn: (Note) -> Int, noteOff: (Int) -> Unit) {
        val it = sounding.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.endTick <= tick) { noteOff(s.id); it.remove() }
        }
        while (next < byStart.size && byStart[next].startTick <= tick) {
            val note = byStart[next++]
            if (note.endTick > tick) sounding += Sounding(note.endTick, noteOn(note)) else noteOn(note).also(noteOff)
        }
    }

    /** Suelta todo lo que esté sonando (pausa, salto, arrastre). */
    fun releaseAll(noteOff: (Int) -> Unit) {
        sounding.forEach { noteOff(it.id) }
        sounding.clear()
    }
}
