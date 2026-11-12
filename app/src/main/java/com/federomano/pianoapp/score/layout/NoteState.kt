package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand

/**
 * Estado de una nota para pintarla (PIA-16). Hoy el reproductor usa [PENDING], [CURRENT] y [PASSED]; el modo escucha va a usar
 * [HIT] y [MISS] para marcar aciertos y errores (cada nota se puede pintar por separado, ver `noteStates` en ScoreView).
 */
enum class NoteState { PENDING, CURRENT, PASSED, HIT, MISS }

object NoteStates {
    /**
     * Qué acordes son "los de ahora" en [tick]: en cada mano, el que está sonando (con todas sus voces) o, si la mano tiene un
     * silencio, el próximo que viene. Devuelve los números de [spans] (que tienen que estar ordenados por tiempo de inicio).
     */
    fun current(spans: List<ChordSpan>, tick: Float, longest: Int = longestOf(spans)): Set<Int> {
        if (spans.isEmpty()) return emptySet()
        val firstAfter = firstStartingAfter(spans, tick)
        val result = mutableSetOf<Int>()
        for (hand in Hand.entries) {
            var covering = false
            // Hacia atrás, mientras un acorde todavía pueda estar sonando.
            var i = firstAfter - 1
            while (i >= 0 && spans[i].chord.startTick > tick - longest) {
                val c = spans[i].chord
                if (c.hand == hand && c.startTick <= tick && tick < c.endTick) { result += i; covering = true }
                i--
            }
            if (covering) continue
            // Silencio en esa mano: el próximo acorde (y los que empiezan con él, de otras voces).
            var next = firstAfter
            while (next < spans.size && spans[next].chord.hand != hand) next++
            if (next < spans.size) {
                val start = spans[next].chord.startTick
                var j = next
                while (j < spans.size && spans[j].chord.startTick == start) {
                    if (spans[j].chord.hand == hand) result += j
                    j++
                }
            }
        }
        return result
    }

    /** Estado de reproducción del acorde número [index]: el de ahora, ya tocado (atenuado) o pendiente. */
    fun playback(spans: List<ChordSpan>, index: Int, tick: Float, current: Set<Int>): NoteState = when {
        index in current -> NoteState.CURRENT
        spans[index].chord.endTick <= tick -> NoteState.PASSED
        else -> NoteState.PENDING
    }

    /** El acorde más largo (para saber hasta dónde mirar hacia atrás); conviene calcularlo una sola vez. */
    fun longestOf(spans: List<ChordSpan>): Int = spans.maxOfOrNull { it.chord.durationTicks } ?: 0

    /** Primer acorde que empieza después de [tick] (o el tamaño de la lista si no hay). */
    private fun firstStartingAfter(spans: List<ChordSpan>, tick: Float): Int {
        var lo = 0
        var hi = spans.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (spans[mid].chord.startTick <= tick) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
