package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Step
import com.federomano.pianoapp.score.chords
import kotlin.math.max
import kotlin.math.min

/** Un texto de ayuda (nombre de nota y/o dedo) y la mano a la que pertenece (para atenuarlo junto con ella). */
class NoteLabel(val op: TextOp, val hand: Hand)

/**
 * Ayudas para aprender a leer (PIA-18): el nombre de cada nota en Do-Re-Mi y el número de dedo. Van en una fila arriba del
 * pentagrama de la derecha y abajo del de la izquierda, alineadas con la nota, siempre por fuera de lo ya dibujado
 * (notas, plicas, ligaduras, dinámicas, números de compás): nunca los tapan. Si dos textos chocan, el segundo se aleja.
 * En un acorde, un texto por nota, apilados de la más cercana al pentagrama hacia afuera.
 */
object NoteLabelLayout {
    const val SIZE = 1.1f
    private const val GAP = 0.35f
    private const val CHAR_WIDTH = 0.6f
    private const val HEAD_CENTER = 0.6f
    private const val PAD = 0.1f

    /** Más ancho que esto: se revisa siempre (hairpins, ligaduras largas); lo demás se busca por cercanía. */
    private const val WIDE = 8f

    private val NAMES = mapOf(Step.C to "Do", Step.D to "Re", Step.E to "Mi", Step.F to "Fa", Step.G to "Sol", Step.A to "La", Step.B to "Si")

    /** Nombre en Do-Re-Mi, con alteración: Do#, Sib, Fa## (la octava no se escribe). */
    fun name(pitch: Pitch): String = buildString {
        append(NAMES.getValue(pitch.step))
        if (pitch.alter > 0) repeat(pitch.alter) { append('#') } else repeat(-pitch.alter) { append('b') }
    }

    fun text(note: Note, names: Boolean, fingers: Boolean): String {
        val parts = mutableListOf<String>()
        if (names) parts += name(note.pitch)
        if (fingers) note.finger?.let { parts += it.toString() }
        return parts.joinToString(" ")
    }

    /** Lo que ya hay dibujado, buscado por cercanía horizontal. */
    private class Obstacles(boxes: List<Box>) {
        private val wide = boxes.filter { it.right - it.left > WIDE }
        private val narrow = boxes.filter { it.right - it.left <= WIDE }.sortedBy { it.left }
        private val lefts = narrow.map { it.left }
        private val placed = ArrayList<Box>()

        private fun firstIndexFrom(x: Float): Int {
            var lo = 0
            var hi = lefts.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (lefts[mid] < x) lo = mid + 1 else hi = mid
            }
            return lo
        }

        private inline fun forEachNear(from: Float, to: Float, action: (Box) -> Unit) {
            wide.forEach { if (it.overlapsX(from, to)) action(it) }
            var i = firstIndexFrom(from - WIDE)
            while (i < narrow.size && narrow[i].left < to) {
                if (narrow[i].overlapsX(from, to)) action(narrow[i])
                i++
            }
            // Los textos se colocan de izquierda a derecha: los que quedaron muy atrás ya no estorban.
            placed.removeAll { it.right < from - WIDE }
            placed.forEach { if (it.overlapsX(from, to)) action(it) }
        }

        fun highest(from: Float, to: Float, atMost: Float): Float {
            var result = atMost
            forEachNear(from, to) { result = min(result, it.top) }
            return result
        }

        fun lowest(from: Float, to: Float, atLeast: Float): Float {
            var result = atLeast
            forEachNear(from, to) { result = max(result, it.bottom) }
            return result
        }

        fun add(box: Box) { placed += box }
    }

    /**
     * [boxes]: lo ya dibujado. [names] y [fingers] dicen qué mostrar; las notas ligadas a la anterior no se repiten
     * (es la misma nota) y las que no traen dedo solo muestran el nombre.
     */
    fun build(score: Score, xOfTick: (Int) -> Float, boxes: List<Box>, names: Boolean, fingers: Boolean): List<NoteLabel> {
        if (!names && !fingers) return emptyList()
        val continuations = HashSet<Triple<Hand, Pitch, Int>>()
        for (n in score.notes) if (n.tiedToNext) continuations += Triple(n.hand, n.pitch, n.endTick)
        val obstacles = Obstacles(boxes)
        val out = ArrayList<NoteLabel>()
        for (chord in score.chords()) {
            val above = chord.hand == Hand.RIGHT
            // Arriba: de la nota más grave a la más aguda (la más grave queda pegada al pentagrama). Abajo, al revés.
            val ordered = if (above) chord.notes else chord.notes.reversed()
            val cx = xOfTick(chord.startTick) + HEAD_CENTER
            for (note in ordered) {
                if (Triple(note.hand, note.pitch, note.startTick) in continuations) continue
                val text = text(note, names, fingers)
                if (text.isEmpty()) continue
                val width = SIZE * CHAR_WIDTH * text.length
                val left = cx - width / 2
                val from = left - PAD
                val to = left + width + PAD
                val baseline = if (above) {
                    obstacles.highest(from, to, Geometry.TREBLE_TOP) - GAP
                } else {
                    obstacles.lowest(from, to, Geometry.SYSTEM_BOTTOM) + GAP + SIZE
                }
                val op = TextOp(text, left, baseline, SIZE)
                obstacles.add(Extents.box(op))
                out += NoteLabel(op, chord.hand)
            }
        }
        return out
    }
}
