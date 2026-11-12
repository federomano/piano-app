package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Clef
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.TICKS_PER_QUARTER
import com.federomano.pianoapp.score.TempoMap
import com.federomano.pianoapp.score.measures
import kotlin.math.abs
import kotlin.math.max

/** Medidas fijas del dibujo, en espacios de pentagrama (sp). */
object Geometry {
    const val STAFF_HEIGHT = 4f

    /** Línea superior del pentagrama de la mano derecha (clave de sol). */
    const val TREBLE_TOP = 0f

    /** Línea superior del pentagrama de la mano izquierda (clave de fa). */
    const val BASS_TOP = 10f
    const val SYSTEM_BOTTOM = BASS_TOP + STAFF_HEIGHT

    /** Espacio libre arriba y abajo para líneas adicionales, notas agudas o graves y números de compás. */
    const val MARGIN_TOP = 5.5f
    const val MARGIN_BOTTOM = 5.5f
    const val TOTAL_HEIGHT = MARGIN_TOP + SYSTEM_BOTTOM + MARGIN_BOTTOM

    const val STAFF_LINE_THICKNESS = 0.13f
    const val THIN_BARLINE = 0.16f
    const val THICK_BARLINE = 0.5f

    /** Ancho de una negra. Un compás de 4/4 mide 4 veces esto. */
    const val QUARTER_WIDTH = 5f

    /** Dónde está la línea de reproducción, como fracción del ancho de la pantalla desde la izquierda. */
    const val PLAYHEAD_FRACTION = 0.3f
}

/** Un compás ya ubicado en el eje horizontal. */
data class MeasureLayout(
    val measure: Measure,
    /** Donde está la barra de compás de la izquierda (en el primero, el comienzo de la pieza). */
    val x0: Float,
    /** Donde empieza el tiempo del compás (después de un eventual cambio de armadura o de compás). */
    val contentX: Float,
    /** Barra de compás de la derecha. */
    val x1: Float,
    /** Puntos de anclaje del tiempo del compás (ver [Spacing]). */
    val anchors: Spacing.Anchors? = null,
    private val perTick: Float = 0f,
    /**
     * Posición que corresponde al final del compás en el tiempo: la primera nota del compás siguiente.
     * El tramo entre la última nota y ese punto incluye la barra de compás (y un eventual cambio de
     * armadura), así la partitura avanza sin saltos al pasar de un compás al otro.
     */
    val timeEndX: Float = x1,
) {
    /** Posición horizontal de un instante dentro de este compás. */
    fun xOfTick(tick: Int): Float = xOfTick(tick.toFloat())

    fun xOfTick(tick: Float): Float {
        val t = tick.coerceAtMost(measure.endTick.toFloat())
        val a = anchors ?: return contentX + (t - measure.startTick) * perTick
        val last = a.ticks.lastIndex
        if (last >= 1 && t > a.ticks[last - 1]) {
            // Último tramo: de la última nota a la primera del compás siguiente.
            val fromX = contentX + a.offsets[last - 1]
            return fromX + (timeEndX - fromX) * (t - a.ticks[last - 1]) / (a.ticks[last] - a.ticks[last - 1])
        }
        return contentX + a.offsetOf(t, perTick)
    }

    /** El instante que corresponde a una posición horizontal dentro de este compás. */
    fun tickAtX(x: Float): Float {
        val a = anchors ?: return measure.startTick + (x - contentX) / perTick
        val last = a.ticks.lastIndex
        if (last >= 1 && x > contentX + a.offsets[last - 1]) {
            val fromX = contentX + a.offsets[last - 1]
            val fraction = ((x - fromX) / (timeEndX - fromX)).coerceIn(0f, 1f)
            return a.ticks[last - 1] + (a.ticks[last] - a.ticks[last - 1]) * fraction
        }
        return a.tickAt(x - contentX, perTick).coerceIn(measure.startTick.toFloat(), measure.endTick.toFloat())
    }
}

/** Lo que queda fijo a la izquierda en un instante: líneas, llave, claves, armadura y compás vigentes. */
data class Header(val ops: List<DrawOp>, val width: Float)

/**
 * Un cambio de armadura o de firma de compás. Existe una sola vez: viaja con la partitura (dentro del
 * compás donde ocurre) y al llegar al área fija se queda ahí, reemplazando a la anterior.
 *
 * Los dibujos ([ops] y [cancel]) están en coordenadas del área fija. En pantalla, el cambio se dibuja
 * corrido [dx] hacia la derecha mientras viaja (dx baja hasta 0 y ahí queda fijo).
 */
class SignatureChange(
    val measureIndex: Int,
    /** La armadura y la firma de compás nuevas, pegadas (así quedan en el área fija). */
    val ops: List<DrawOp>,
    /** Becuadros que cancelan la armadura anterior. Solo se ven mientras viaja. */
    val cancel: List<DrawOp>,
    /** Donde empieza [ops] en el área fija. */
    val anchor: Float,
    /** Posición en la partitura menos posición en el área fija: pantalla = x + shift + headerWidth - scroll. */
    val shift: Float,
    /** Desplazamiento (scroll) en el que llega a su lugar fijo. */
    val pinScroll: Float,
)

/** Un grupo de dibujos del área fija, corrido [dx] y con opacidad [alpha] (de 0 a 1). */
data class HeaderDraw(val ops: List<DrawOp>, val dx: Float, val alpha: Float)

/**
 * Cálculo del dibujo de la partitura: dos pentagramas unidos con llave, en una sola línea continua.
 *
 * - [staff]: líneas de los pentagramas, a todo lo largo.
 * - [marks]: barras de compás y números. Pasan por encima del área fija y se desvanecen.
 * - [fixed]: lo que nunca cambia a la izquierda (líneas, llave, claves).
 * - [changes]: cambios de armadura y de compás; [headerDraws] dice cómo dibujarlos en cada momento.
 */
class ScoreLayout private constructor(
    val measures: List<MeasureLayout>,
    val staff: List<DrawOp>,
    val marks: List<DrawOp>,
    /** Cambios de armadura y compás en su lugar dentro del compás (solo para pruebas: se dibujan con [headerDraws]). */
    val inlineChanges: List<DrawOp>,
    val fixed: List<DrawOp>,
    val changes: List<SignatureChange>,
    private val initial: List<DrawOp>,
    val headerWidth: Float,
    val signatureLeft: Float,
    val totalWidth: Float,
    private val quarterWidth: Float,
    /** Recorrido suave tiempo → posición, solo para mover la vista en la reproducción (ver [SmoothPath]). */
    private val smooth: SmoothPath,
    /** Espacio libre arriba del pentagrama de la derecha y abajo del de la izquierda (alcanza para todo lo dibujado). */
    val marginTop: Float = Geometry.MARGIN_TOP,
    val marginBottom: Float = Geometry.MARGIN_BOTTOM,
    private val labelBuilder: (names: Boolean, fingers: Boolean) -> List<NoteLabel> = { _, _ -> emptyList() },
    /** Qué marcas son de cada acorde (ordenados por tiempo), para resaltar el actual y atenuar lo ya tocado (PIA-16). */
    val chordSpans: List<ChordSpan> = emptyList(),
) {
    /** Nombres de nota y dedos (PIA-18) según lo que se quiera ver; viajan con la partitura pero no forman parte de [marks]. */
    fun labels(names: Boolean, fingers: Boolean): List<NoteLabel> = labelBuilder(names, fingers)

    /** Alto total que hay que mostrar, en espacios de pentagrama. */
    val totalHeight: Float get() = marginTop + Geometry.SYSTEM_BOTTOM + marginBottom

    /** Tamaño de un espacio de pentagrama y dónde cae la línea superior del pentagrama de la derecha, en píxeles de pantalla. */
    class Fit(val spPx: Float, val topPx: Float)

    /**
     * Cómo acomodar la partitura en una pantalla de [heightPx] de alto, con espacios de a lo sumo [maxSpPx]. Abajo, [controlsPx] los tapan
     * los controles de reproducción: si lo dibujado llega hasta ahí (notas muy graves, dinámicas), se achica un poco para que quede arriba.
     * Las partituras comunes (con el margen de siempre) quedan exactamente como estaban.
     */
    fun fit(heightPx: Float, maxSpPx: Float, controlsPx: Float, labels: List<NoteLabel> = emptyList()): Fit {
        // Los nombres de nota y dedos, si se ven, pueden pedir más lugar arriba o abajo.
        val boxes = Extents.boxes(labels.map { it.op })
        val mt = if (boxes.isEmpty()) marginTop else max(marginTop, -boxes.minOf { it.top } + EDGE_PAD)
        val mb = if (boxes.isEmpty()) marginBottom else max(marginBottom, boxes.maxOf { it.bottom } - Geometry.SYSTEM_BOTTOM + EDGE_PAD)
        val total = mt + Geometry.SYSTEM_BOTTOM + mb
        fun topFor(sp: Float) = (heightPx - total * sp) / 2f + mt * sp
        val belowSystem = Geometry.SYSTEM_BOTTOM + mb - EDGE_PAD
        val plain = minOf(maxSpPx, heightPx / total)
        if (mb <= Geometry.MARGIN_BOTTOM || topFor(plain) + belowSystem * plain <= heightPx - controlsPx) return Fit(plain, topFor(plain))
        val sp = minOf(plain, (heightPx - controlsPx) / (mt + belowSystem))
        return Fit(sp, mt * sp)
    }

    /** Todo lo que viaja con la partitura (para pruebas): líneas, marcas y cambios en su lugar dentro del compás. */
    val body: List<DrawOp> = staff + marks + inlineChanges

    /** Posición horizontal de un instante de la pieza. Es proporcional al tiempo, salvo donde hace falta más lugar. */
    fun xOfTick(tick: Int): Float = xOfTick(tick.toFloat())

    fun xOfTick(tick: Float): Float =
        measures[measures.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)].xOfTick(tick)

    /** El instante que corresponde a una posición horizontal de la partitura (al revés de [xOfTick]). */
    fun tickAtX(x: Float): Float {
        val m = measures.lastOrNull { it.contentX <= x } ?: return measures.first().measure.startTick.toFloat()
        return m.tickAtX(x)
    }

    /** Fin de la pieza, en ticks. */
    val endTick: Int get() = measures.last().measure.endTick

    /**
     * Desplazamiento (scroll) con el que el instante [tick] queda justo en la línea de reproducción,
     * que está [playheadX] (sp) desde el borde izquierdo de la pantalla.
     */
    fun scrollForTick(tick: Float, playheadX: Float): Float = smooth.positionAt(tick) + headerWidth - playheadX

    /** Al revés: qué instante está sobre la línea de reproducción con ese desplazamiento. */
    fun tickAtScroll(scroll: Float, playheadX: Float): Float = smooth.tickAt(scroll + playheadX - headerWidth)

    private fun currentOps(scrollX: Float): List<DrawOp> =
        changes.lastOrNull { it.pinScroll <= scrollX }?.ops ?: initial

    /** Lo que está fijo a la izquierda con ese desplazamiento, sin contar transiciones en curso. */
    fun headerAt(scrollX: Float): Header = Header(fixed + currentOps(scrollX), headerWidth)

    /** Avance de un cambio: 0 cuando toca el borde del área fija, 1 cuando coincide con su lugar fijo. */
    fun progress(change: SignatureChange, scrollX: Float): Float {
        val dx = change.pinScroll - scrollX
        return (1f - dx / (headerWidth - change.anchor)).coerceIn(0f, 1f)
    }

    /**
     * Armadura y compás a dibujar sobre [fixed]. Los vigentes se desvanecen a medida que el próximo
     * cambio, que viaja con la partitura, se les acerca y los reemplaza. [viewWidth] es el ancho visible (sp).
     */
    fun headerDraws(scrollX: Float, viewWidth: Float): List<HeaderDraw> {
        val upcoming = changes.filter { it.pinScroll > scrollX }
        val fade = upcoming.firstOrNull()?.let { progress(it, scrollX) } ?: 0f
        val out = mutableListOf(HeaderDraw(currentOps(scrollX), 0f, 1f - fade))
        for (change in upcoming) {
            val dx = change.pinScroll - scrollX
            if (dx > viewWidth) break
            out += HeaderDraw(change.ops, dx, 1f)
            if (change.cancel.isNotEmpty()) out += HeaderDraw(change.cancel, dx, 1f - progress(change, scrollX))
        }
        return out
    }

    companion object {
        /** Las líneas siguen hacia la izquierda del comienzo, para cuando la línea de reproducción está en la primera nota. */
        const val STAFF_LEAD = 60f

        /** Ancho (en segundos) de la ventana con que se suaviza el avance de la vista. */
        const val PLAYHEAD_SMOOTHING_SECONDS = 0.3f
        private const val LEFT_PAD = 0.8f
        private const val MEASURE_LEAD = 2.2f
        private const val SIGNATURE_LEAD = 0.8f
        private const val ACCIDENTAL_GAP = 0.15f
        private const val SIGNATURE_GAP = 0.9f
        private const val CLEF_CHANGE_SCALE = 0.8f
        private const val CLEF_CHANGE_LEAD = 0.6f
        private const val CLEF_CHANGE_GAP = 0.3f

        private val BRACE_SCALE = Geometry.SYSTEM_BOTTOM / Smufl.BRACE_HEIGHT
        private val SYSTEM_LEFT = LEFT_PAD + Smufl.BRACE_WIDTH * BRACE_SCALE + 0.1f
        private val CLEF_X = SYSTEM_LEFT + Geometry.THIN_BARLINE + 0.7f

        /** Donde empieza la armadura en el área fija. */
        private val SIGNATURE_LEFT = CLEF_X + Smufl.CLEF_ADVANCE + SIGNATURE_GAP

        // Altura de cada alteración de la armadura, en sp desde la línea superior de su pentagrama.
        private val SHARPS_TREBLE = floatArrayOf(0f, 1.5f, -0.5f, 1f, 2.5f, 0.5f, 2f)
        private val SHARPS_BASS = floatArrayOf(1f, 2.5f, 0.5f, 2f, 3.5f, 1.5f, 3f)
        private val FLATS_TREBLE = floatArrayOf(2f, 0.5f, 2.5f, 1f, 3f, 1.5f, 3.5f)
        private val FLATS_BASS = floatArrayOf(3f, 1.5f, 3.5f, 2f, 4f, 2.5f, 4.5f)

        fun build(
            score: Score,
            quarterWidth: Float = Geometry.QUARTER_WIDTH,
            smoothingSeconds: Float = PLAYHEAD_SMOOTHING_SECONDS,
        ): ScoreLayout {
            val measures = score.measures()
            val marks = mutableListOf<DrawOp>()
            // Números de compás: (número, x). Se ubican al final, esquivando lo que tengan arriba.
            val labelAt = mutableListOf<Pair<Int, Float>>()
            val inline = mutableListOf<DrawOp>()
            val changes = mutableListOf<SignatureChange>()
            val laidOut = mutableListOf<MeasureLayout>()
            val perTick = quarterWidth / TICKS_PER_QUARTER

            // El área fija tiene lugar para la armadura y el compás más anchos de la pieza, así no cambia
            // de tamaño (y la partitura no pega saltos) cuando hay cambios.
            fun piece(fifths: Int, numerator: Int, denominator: Int): Pair<List<DrawOp>, Float> {
                val ops = mutableListOf<DrawOp>()
                var end = addAccidentals(ops, SIGNATURE_LEFT, fifths)
                if (fifths != 0) end += SIGNATURE_GAP
                end = addTimeSignature(ops, end, numerator, denominator)
                return ops to end
            }
            val headerWidth = measures.maxOf { piece(it.keyFifths, it.timeSignature.numerator, it.timeSignature.denominator).second } + 1.0f

            fun pin(shift: Float) = shift + headerWidth

            var x = 0f
            for ((index, m) in measures.withIndex()) {
                val x0 = x
                var cursor = x0
                val previous = measures.getOrNull(index - 1)
                // Cambio de clave: una clave chica al principio del compás, en el pentagrama de cada mano que cambia.
                val clefChanges = if (index == 0) emptyList() else score.clefs.filter { it.startTick == m.startTick && score.clefAt(it.hand, m.startTick - 1) != it.clef }
                if (clefChanges.isNotEmpty()) {
                    for (c in clefChanges) {
                        val top = if (c.hand == Hand.RIGHT) Geometry.TREBLE_TOP else Geometry.BASS_TOP
                        marks += if (c.clef == Clef.TREBLE) GlyphOp(Smufl.G_CLEF, cursor + CLEF_CHANGE_LEAD, top + 3f, CLEF_CHANGE_SCALE)
                        else GlyphOp(Smufl.F_CLEF, cursor + CLEF_CHANGE_LEAD, top + 1f, CLEF_CHANGE_SCALE)
                    }
                    cursor += CLEF_CHANGE_LEAD + Smufl.CLEF_ADVANCE * CLEF_CHANGE_SCALE + CLEF_CHANGE_GAP
                }
                val keyChanged = previous != null && previous.keyFifths != m.keyFifths
                val timeChanged = previous != null && (
                    previous.timeSignature.numerator != m.timeSignature.numerator ||
                        previous.timeSignature.denominator != m.timeSignature.denominator
                    )
                if (keyChanged || timeChanged) {
                    // Becuadros + armadura + compás, pegados: es un solo bloque que después se queda fijo a la izquierda.
                    cursor += SIGNATURE_LEAD
                    val n = m.timeSignature.numerator
                    val d = m.timeSignature.denominator
                    val anchorInline = addNaturals(inline, cursor, previous!!.keyFifths, m.keyFifths)
                    var end = addAccidentals(inline, anchorInline, m.keyFifths)
                    if (m.keyFifths != 0) end += SIGNATURE_GAP
                    end = addTimeSignature(inline, end, n, d)
                    val cancel = mutableListOf<DrawOp>().also {
                        addNaturals(it, SIGNATURE_LEFT - (anchorInline - cursor), previous.keyFifths, m.keyFifths)
                    }
                    val shift = anchorInline - SIGNATURE_LEFT
                    changes += SignatureChange(index, piece(m.keyFifths, n, d).first, cancel, SIGNATURE_LEFT, shift, pin(shift))
                    cursor = end + SIGNATURE_GAP
                    cursor += MEASURE_LEAD + 0.4f - SIGNATURE_GAP
                } else {
                    cursor += MEASURE_LEAD
                }
                val anchors = Spacing.anchors(score, m, perTick, isLast = index == measures.lastIndex)
                val contentX = cursor + anchors.leadExtra
                val x1 = contentX + anchors.offsets.last()

                labelAt += m.number to (x0 + 0.35f)

                // Barra de compás que une los dos pentagramas. La última es doble: fina y gruesa.
                if (index == measures.lastIndex) {
                    val thickLeft = x1 - Geometry.THICK_BARLINE
                    marks += RectOp(thickLeft - 0.4f - Geometry.THIN_BARLINE, 0f, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM)
                    marks += RectOp(thickLeft, 0f, Geometry.THICK_BARLINE, Geometry.SYSTEM_BOTTOM)
                } else {
                    marks += RectOp(x1 - Geometry.THIN_BARLINE / 2, 0f, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM)
                }

                laidOut += MeasureLayout(m, x0, contentX, x1, anchors, perTick)
                x = x1
            }

            val totalWidth = x
            // El final de cada compás, en el tiempo, es la primera nota del siguiente (ver MeasureLayout.timeEndX).
            val timed = laidOut.mapIndexed { i, m -> m.copy(timeEndX = laidOut.getOrNull(i + 1)?.contentX ?: m.x1) }
            val notesFrom = marks.size
            val builtNotes = NoteLayout.buildWithSpans(score, timed) { tick ->
                timed[timed.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)].xOfTick(tick)
            }
            marks += builtNotes.ops
            // Qué marcas son de cada acorde, con los números ya referidos a [marks].
            val chordSpans = builtNotes.spans
                .map { ChordSpan(it.chord, it.from + notesFrom, it.to + notesFrom, it.heads.map { (n, i) -> n to i + notesFrom }) }
                .sortedBy { it.chord.startTick }
            // Signos de octava: arriba o abajo de lo que hayan dibujado las notas (sin contar las barras de compás, que cruzan todo).
            val beforeOttavas = Extents.boxes(marks.filterNot { it is RectOp && it.height >= Geometry.SYSTEM_BOTTOM - 0.01f })
            marks += OttavaLayout.build(score, { tick ->
                timed[timed.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)].xOfTick(tick)
            }, beforeOttavas)
            // Las letras de dinámica esquivan lo que haya abajo (notas graves, acentos); los números de compás, lo que haya arriba.
            val noteBoxes = Extents.boxes(marks)
            marks += DynamicsLayout.build(score, { tick ->
                timed[timed.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)].xOfTick(tick)
            }) { from, to -> Extents.lowest(noteBoxes, from, to, Geometry.SYSTEM_BOTTOM) }
            val aboveBoxes = Extents.boxes(marks)
            for ((number, labelX) in labelAt) {
                val text = number.toString()
                val width = BAR_LABEL_SIZE * 0.6f * text.length
                val clearTop = Extents.highest(aboveBoxes, labelX - 0.3f, labelX + width + 0.3f, 0f)
                marks += TextOp(text, labelX, minOf(BAR_LABEL_BASELINE, clearTop - 0.4f), BAR_LABEL_SIZE)
            }
            // Nombres de nota y dedos (PIA-18): se calculan al mostrarse; si hace falta más margen, lo pide [fit].
            val labelObstacles = Extents.boxes(marks)
            val xOf = { tick: Int -> timed[timed.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)].xOfTick(tick) }
            val labelBuilder = { names: Boolean, fingers: Boolean -> NoteLabelLayout.build(score, xOf, labelObstacles, names, fingers) }
            // Margen arriba y abajo según lo que de verdad se dibujó: nada queda cortado ni tapado por los controles.
            val all = Extents.boxes(marks)
            val marginTop = max(Geometry.MARGIN_TOP, -all.minOf { it.top } + EDGE_PAD)
            val marginBottom = max(Geometry.MARGIN_BOTTOM, all.maxOf { it.bottom } - Geometry.SYSTEM_BOTTOM + EDGE_PAD)
            val fixed = mutableListOf<DrawOp>()
            fixed += staffLines(SYSTEM_LEFT, headerWidth)
            fixed += GlyphOp(Smufl.BRACE, LEFT_PAD, Geometry.SYSTEM_BOTTOM, BRACE_SCALE)
            fixed += RectOp(SYSTEM_LEFT, 0f, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM)
            // Claves: sol en la línea de sol (la 2.ª desde abajo), fa en la línea de fa (la 4.ª desde abajo).
            fixed += GlyphOp(Smufl.G_CLEF, CLEF_X, Geometry.TREBLE_TOP + 3f)
            fixed += GlyphOp(Smufl.F_CLEF, CLEF_X, Geometry.BASS_TOP + 1f)

            val path = SmoothPath.build(
                endTick = timed.last().measure.endTick.toFloat(),
                exactX = { tick -> timed[timed.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)].xOfTick(tick) },
                tempo = TempoMap(score.tempos),
                windowSeconds = smoothingSeconds,
            )

            val first = measures.first()
            val initial = piece(first.keyFifths, first.timeSignature.numerator, first.timeSignature.denominator).first
            return ScoreLayout(
                timed, staffLines(-STAFF_LEAD, totalWidth), marks, inline, fixed, changes, initial,
                headerWidth, SIGNATURE_LEFT, totalWidth, quarterWidth, path, marginTop, marginBottom, labelBuilder, chordSpans,
            )
        }

        /** Las diez líneas de los dos pentagramas, entre [fromX] y [toX]. */
        private const val BAR_LABEL_SIZE = 1.8f
        private const val BAR_LABEL_BASELINE = -3.0f
        private const val EDGE_PAD = 0.5f

        private fun staffLines(fromX: Float, toX: Float): List<DrawOp> {
            val t = Geometry.STAFF_LINE_THICKNESS
            return listOf(Geometry.TREBLE_TOP, Geometry.BASS_TOP).flatMap { top ->
                (0..4).map { line -> RectOp(fromX, top + line - t / 2, toX - fromX, t) }
            }
        }

        private fun place(ops: MutableList<DrawOp>, x: Float, codepoint: Int, trebleY: Float, bassY: Float) {
            ops += GlyphOp(codepoint, x, Geometry.TREBLE_TOP + trebleY)
            ops += GlyphOp(codepoint, x, Geometry.BASS_TOP + bassY)
        }

        /** Dibuja las alteraciones de la armadura [fifths] en los dos pentagramas. Devuelve donde terminan. */
        private fun addAccidentals(ops: MutableList<DrawOp>, startX: Float, fifths: Int): Float {
            var x = startX
            for (i in 0 until abs(fifths)) {
                if (fifths > 0) {
                    place(ops, x, Smufl.SHARP, SHARPS_TREBLE[i], SHARPS_BASS[i])
                    x += Smufl.SHARP_ADVANCE + ACCIDENTAL_GAP
                } else {
                    place(ops, x, Smufl.FLAT, FLATS_TREBLE[i], FLATS_BASS[i])
                    x += Smufl.FLAT_ADVANCE + ACCIDENTAL_GAP
                }
            }
            return if (fifths != 0) x - ACCIDENTAL_GAP else x
        }

        /**
         * Becuadros que cancelan las alteraciones de la armadura anterior [oldFifths] que ya no valen con
         * la nueva [newFifths]. Devuelve donde tiene que empezar lo que sigue (si hay becuadros, con su separación).
         */
        private fun addNaturals(ops: MutableList<DrawOp>, startX: Float, oldFifths: Int, newFifths: Int): Float {
            var x = startX
            val sameKind = oldFifths != 0 && newFifths != 0 && (oldFifths > 0) == (newFifths > 0)
            val from = if (sameKind) abs(newFifths) else 0
            for (i in from until abs(oldFifths)) {
                val trebleY = (if (oldFifths > 0) SHARPS_TREBLE else FLATS_TREBLE)[i]
                val bassY = (if (oldFifths > 0) SHARPS_BASS else FLATS_BASS)[i]
                place(ops, x, Smufl.NATURAL, trebleY, bassY)
                x += Smufl.NATURAL_ADVANCE + ACCIDENTAL_GAP
            }
            // Si no sigue ninguna alteración, lo que viene (el compás) va con la separación normal.
            return if (newFifths == 0 && x > startX) x - ACCIDENTAL_GAP + SIGNATURE_GAP else x
        }

        private fun digitsWidth(text: String): Float = text.sumOf { Smufl.timeDigitAdvance(it - '0').toDouble() }.toFloat()

        /** Firma de compás con el numerador arriba y el denominador abajo, en los dos pentagramas. Devuelve donde termina. */
        private fun addTimeSignature(ops: MutableList<DrawOp>, startX: Float, numerator: Int, denominator: Int): Float {
            val top = numerator.toString()
            val bottom = denominator.toString()
            val topWidth = digitsWidth(top)
            val bottomWidth = digitsWidth(bottom)
            val width = max(topWidth, bottomWidth)
            for (staffTop in listOf(Geometry.TREBLE_TOP, Geometry.BASS_TOP)) {
                placeDigits(ops, top, startX + (width - topWidth) / 2, staffTop + 1f)
                placeDigits(ops, bottom, startX + (width - bottomWidth) / 2, staffTop + 3f)
            }
            return startX + width
        }

        private fun placeDigits(ops: MutableList<DrawOp>, text: String, startX: Float, baseline: Float) {
            var x = startX
            for (c in text) {
                val digit = c - '0'
                ops += GlyphOp(Smufl.timeDigit(digit), x, baseline)
                x += Smufl.timeDigitAdvance(digit)
            }
        }
    }
}
