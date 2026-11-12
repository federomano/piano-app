package com.federomano.pianoapp.audio

import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.PedalMark

/**
 * Decide qué notas tocar y soltar a medida que la posición avanza. No sabe de audio: recibe funciones para
 * tocar y soltar. La posición que se le da es el tiempo musical exacto (en ticks), así el ritmo que se
 * escucha es siempre el de la partitura.
 *
 * Una nota ligada a la siguiente (misma mano y altura, que empieza justo donde termina) no vuelve a atacar:
 * el sonido se sostiene hasta el final de la segunda.
 *
 * Pedal de resonancia (PIA-68): una nota que termina con el pedal apretado (entre que se aprieta y se suelta, sin contar los
 * extremos) sigue sonando hasta que se suelta. Si se vuelve a tocar la misma tecla mientras tanto, la anterior se apaga
 * (no se acumulan voces).
 */
class NoteTrigger(notes: List<Note>, private val pedals: List<PedalMark> = emptyList()) {
    private val byStart = notes.sortedBy { it.startTick }
    private var next = 0

    /** [sustainedUntil]: si la nota ya terminó y solo la sostiene el pedal, el instante en que se suelta; si no, -1. */
    private class Sounding(var note: Note, val id: Int, var sustainedUntil: Int = -1)
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
            if (held != null) {
                held.note = note
            } else {
                // La misma tecla otra vez: lo que seguía sonando por el pedal se apaga antes del nuevo ataque.
                val again = sounding.iterator()
                while (again.hasNext()) {
                    val old = again.next()
                    if (old.sustainedUntil >= 0 && old.note.pitch.midi == note.pitch.midi) { noteOff(old.id); again.remove() }
                }
                sounding += Sounding(note, noteOn(note))
            }
        }
        val it = sounding.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.sustainedUntil >= 0) {
                if (tick >= s.sustainedUntil) { noteOff(s.id); it.remove() }
            } else if (s.note.endTick <= tick) {
                val pedal = pedals.firstOrNull { it.startTick < s.note.endTick && s.note.endTick < it.endTick }
                if (pedal != null && tick < pedal.endTick) s.sustainedUntil = pedal.endTick else { noteOff(s.id); it.remove() }
            }
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
