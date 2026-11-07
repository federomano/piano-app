package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Chord
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Step
import com.federomano.pianoapp.score.Ticks
import com.federomano.pianoapp.score.chords
import kotlin.math.max
import kotlin.math.min

/**
 * Dibujo de las notas, los silencios y las ligaduras (PIA-11 y PIA-12). Calcula instrucciones de dibujo
 * en espacios de pentagrama, igual que el resto del layout.
 *
 * Las reglas son las de la notación tradicional: las plicas van hacia arriba si la nota está por debajo
 * de la línea central y hacia abajo si está en ella o por encima; en un acorde (o en un grupo unido por
 * una barra) manda la nota más alejada de la línea central. Una alteración se escribe solo si cambia lo
 * que ya valía en ese compás (o lo que da la armadura). Las corcheas y semicorcheas seguidas se unen con
 * barras dentro de cada tiempo del compás.
 */
object NoteLayout {
    const val STEM_THICKNESS = 0.12f
    const val LEDGER_THICKNESS = 0.16f
    const val BEAM_THICKNESS = 0.5f
    private const val BEAM_SPACING = 0.25f
    private const val MAX_BEAM_SLOPE = 0.3f
    private const val STUB_LENGTH = 1.1f
    private const val LEDGER_OVERHANG = 0.4f
    private const val STEM_LENGTH = 3.5f
    private const val STEM_ANCHOR = 0.168f
    private const val DOT_GAP = 0.35f
    private const val DOT_SPACING = 0.45f
    private const val ACCIDENTAL_MARGIN = 0.2f
    private const val ACCIDENTAL_COLUMN_GAP = 0.15f

    /** Posición diatónica de la línea inferior de cada pentagrama: Mi4 en clave de sol, Sol2 en clave de fa. */
    private const val TREBLE_BOTTOM = 4 * 7 + 2
    private const val BASS_BOTTOM = 2 * 7 + 4

    /** Figura rítmica: la duración base más los puntillos. */
    data class Figure(val base: Int, val dots: Int)

    fun figureOf(ticks: Int): Figure {
        val bases = listOf(Ticks.WHOLE, Ticks.HALF, Ticks.QUARTER, Ticks.EIGHTH, Ticks.SIXTEENTH)
        for (base in bases) {
            if (ticks == base) return Figure(base, 0)
            if (ticks == base * 3 / 2) return Figure(base, 1)
            if (ticks == base * 7 / 4) return Figure(base, 2)
        }
        // Duración rara (tresillos, etc.): se dibuja con la figura más grande que entre, sin puntillos.
        return Figure(bases.firstOrNull { it <= ticks } ?: Ticks.SIXTEENTH, 0)
    }

    /** Duración del grupo de barras: un tiempo del compás (la negra, o la negra con puntillo en los compases compuestos). */
    fun beatTicks(numerator: Int, denominator: Int): Int =
        if (denominator >= 8 && numerator % 3 == 0) Ticks.dotted(Ticks.QUARTER) else Ticks.QUARTER

    private val STEP_INDEX = mapOf(Step.C to 0, Step.D to 1, Step.E to 2, Step.F to 3, Step.G to 4, Step.A to 5, Step.B to 6)

    /** Posición diatónica absoluta: Do0 = 0, cada línea o espacio sube 1. */
    fun diatonic(pitch: Pitch): Int = pitch.octave * 7 + STEP_INDEX.getValue(pitch.step)

    /** Posición en el pentagrama: 0 = línea inferior, 1 = primer espacio, 2 = segunda línea, etc. */
    fun staffPosition(pitch: Pitch, hand: Hand): Int = diatonic(pitch) - if (hand == Hand.RIGHT) TREBLE_BOTTOM else BASS_BOTTOM

    private fun staffTop(hand: Hand) = if (hand == Hand.RIGHT) Geometry.TREBLE_TOP else Geometry.BASS_TOP

    /** Altura (y) de una posición del pentagrama. */
    fun yOf(position: Int, hand: Hand): Float = staffTop(hand) + 4f - position * 0.5f

    private val SHARP_ORDER = listOf(Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B)
    private val FLAT_ORDER = listOf(Step.B, Step.E, Step.A, Step.D, Step.G, Step.C, Step.F)

    /** Alteración que la armadura da a cada nota. */
    fun keyAlter(fifths: Int, step: Step): Int = when {
        fifths > 0 && step in SHARP_ORDER.take(fifths) -> 1
        fifths < 0 && step in FLAT_ORDER.take(-fifths) -> -1
        else -> 0
    }

    private class Head(val note: Note, val position: Int) {
        var displaced = false
        var x = 0f
    }

    /** Todo lo que hace falta saber de un acorde (o nota suelta) para dibujarlo. */
    private class Plan(val chord: Chord, val measureIndex: Int, val measure: Measure, val noteX: Float) {
        val hand = chord.hand
        val figure = figureOf(chord.durationTicks)
        val whole = figure.base == Ticks.WHOLE
        val headWidth = if (whole) Smufl.NOTEHEAD_WHOLE_WIDTH else Smufl.NOTEHEAD_WIDTH
        val heads = chord.notes.map { Head(it, staffPosition(it.pitch, hand)) }.sortedBy { it.position }
        val lowest = heads.first().position
        val highest = heads.last().position
        val beamable = figure.base <= Ticks.EIGHTH

        /** Plica hacia abajo si lo más alejado de la línea central (posición 4) está arriba o a la par. */
        var stemDown = (highest - 4) >= (4 - lowest)
        var group: BeamGroup? = null
        var stemX = 0f
        var leftEdge = 0f
        var rightEdge = 0f

        val beams: Int get() = if (figure.base == Ticks.SIXTEENTH) 2 else 1
        fun yOf(position: Int) = NoteLayout.yOf(position, hand)
        val stemAnchorTop: Float get() = yOf(highest) + STEM_ANCHOR
        val stemAnchorBottom: Float get() = yOf(lowest) - STEM_ANCHOR

        /** Donde terminaría la plica si no hubiera barra. */
        fun idealTip(): Float {
            val extra = (beams - 1) * (BEAM_THICKNESS + BEAM_SPACING) + if (group == null && beams > 1) 0.5f else 0f
            val middle = staffTop(hand) + 2f
            return if (stemDown) max(yOf(lowest) + STEM_LENGTH + extra, middle) else min(yOf(highest) - STEM_LENGTH - extra, middle)
        }

        fun arrange() {
            // Cabezas de notas contiguas (segundas) van a lados opuestos de la plica.
            if (stemDown) {
                for (i in heads.indices.reversed()) {
                    val above = heads.getOrNull(i + 1)
                    heads[i].displaced = above != null && above.position - heads[i].position == 1 && !above.displaced
                }
            } else {
                for (i in heads.indices) {
                    val below = heads.getOrNull(i - 1)
                    heads[i].displaced = below != null && heads[i].position - below.position == 1 && !below.displaced
                }
            }
            stemX = if (stemDown) noteX else noteX + headWidth - STEM_THICKNESS
            for (head in heads) {
                head.x = when {
                    !head.displaced -> noteX
                    stemDown -> noteX - headWidth + STEM_THICKNESS
                    else -> noteX + headWidth - STEM_THICKNESS
                }
            }
            leftEdge = heads.minOf { it.x }
            rightEdge = heads.maxOf { it.x + headWidth }
        }
    }

    /** Corcheas y semicorcheas seguidas dentro de un mismo tiempo, unidas por barras. */
    private class BeamGroup(val plans: List<Plan>) {
        val stemDown = plans.maxOf { it.highest } - 4 >= 4 - plans.minOf { it.lowest }
        var y0 = 0f
        var slope = 0f
        fun beamY(x: Float) = y0 + slope * (x - plans.first().stemX)
    }

    /** Dibuja todas las notas, silencios y ligaduras de la partitura. [xOfTick] da la posición horizontal de cada instante. */
    fun build(score: Score, measures: List<MeasureLayout>, xOfTick: (Int) -> Float): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        fun measureIndexAt(tick: Int) = measures.indexOfLast { it.measure.startTick <= tick }.coerceAtLeast(0)

        val plans = score.chords().map { chord ->
            val index = measureIndexAt(chord.startTick)
            Plan(chord, index, measures[index].measure, xOfTick(chord.startTick))
        }

        // 1) Grupos de barras, por mano.
        for (hand in Hand.entries) {
            val handPlans = plans.filter { it.hand == hand }.sortedBy { it.chord.startTick }
            var current = mutableListOf<Plan>()
            fun close() {
                if (current.size >= 2) {
                    val group = BeamGroup(current.toList())
                    current.forEach { it.group = group; it.stemDown = group.stemDown }
                }
                current = mutableListOf()
            }
            for (plan in handPlans) {
                if (!plan.beamable) { close(); continue }
                val last = current.lastOrNull()
                val m = plan.measure
                val beat = beatTicks(m.timeSignature.numerator, m.timeSignature.denominator)
                fun slot(p: Plan) = (p.chord.startTick - p.measure.startTick) / beat
                val continues = last != null && last.measureIndex == plan.measureIndex &&
                    last.chord.endTick == plan.chord.startTick && slot(last) == slot(plan)
                if (!continues) close()
                current += plan
            }
            close()
        }

        // 2) Geometría de cada acorde y de cada barra.
        plans.forEach { it.arrange() }
        for (group in plans.mapNotNull { it.group }.distinct()) layoutBeam(group)

        // 3) Dibujo de acordes, en orden, para llevar la cuenta de las alteraciones de cada compás.
        var stateMeasure = -1
        val state = mutableMapOf<Hand, MutableMap<Pair<Step, Int>, Int>>()
        for (plan in plans) {
            if (plan.measureIndex != stateMeasure) {
                stateMeasure = plan.measureIndex
                state.clear()
            }
            val handState = state.getOrPut(plan.hand) { mutableMapOf() }
            val tiedFromBefore = plan.chord.notes.filter { note ->
                note.startTick == plan.measure.startTick &&
                    score.notes.any { it.hand == note.hand && it.pitch.midi == note.pitch.midi && it.endTick == note.startTick && it.tiedToNext }
            }.toSet()
            drawChord(ops, plan, plan.measure.keyFifths, handState, tiedFromBefore)
        }
        for (group in plans.mapNotNull { it.group }.distinct()) drawBeam(ops, group)

        // 4) Ligaduras de prolongación.
        drawTies(ops, plans)

        // 5) Silencios.
        for (rest in score.rests) {
            val index = measureIndexAt(rest.startTick)
            drawRest(ops, rest.hand, rest.startTick, rest.durationTicks, measures[index], xOfTick(rest.startTick))
        }
        return ops
    }

    // --- Barras ---

    private fun layoutBeam(group: BeamGroup) {
        val first = group.plans.first()
        val last = group.plans.last()
        val tips = group.plans.map { it.idealTip() }
        val dx = last.stemX - first.stemX
        val slope = if (dx > 0f) ((tips.last() - tips.first()) / dx).coerceIn(-MAX_BEAM_SLOPE, MAX_BEAM_SLOPE) else 0f
        group.slope = slope
        group.y0 = tips.first()
        // Que ninguna plica quede más corta que lo normal: se desplaza la barra hacia afuera.
        val violations = group.plans.mapIndexed { i, plan -> tips[i] - group.beamY(plan.stemX) }
        group.y0 += if (group.stemDown) max(0f, violations.max()) else min(0f, violations.min())
    }

    private fun drawBeam(ops: MutableList<DrawOp>, group: BeamGroup) {
        val plans = group.plans
        // Plicas hasta la barra.
        for (plan in plans) {
            val beamY = group.beamY(plan.stemX)
            if (group.stemDown) {
                ops += RectOp(plan.stemX, plan.stemAnchorTop, STEM_THICKNESS, beamY - plan.stemAnchorTop)
            } else {
                ops += RectOp(plan.stemX, beamY, STEM_THICKNESS, plan.stemAnchorBottom - beamY)
            }
        }
        fun segment(x1: Float, x2: Float, level: Int) {
            // La barra exterior queda en la punta de la plica; las siguientes se apilan hacia las cabezas.
            val shift = if (group.stemDown) -level * (BEAM_THICKNESS + BEAM_SPACING) else level * (BEAM_THICKNESS + BEAM_SPACING)
            val outer1 = group.beamY(x1) + shift
            val outer2 = group.beamY(x2) + shift
            val top1 = if (group.stemDown) outer1 - BEAM_THICKNESS else outer1
            val top2 = if (group.stemDown) outer2 - BEAM_THICKNESS else outer2
            ops += PolygonOp(listOf(x1, top1, x2, top2, x2, top2 + BEAM_THICKNESS, x1, top1 + BEAM_THICKNESS))
        }
        // Primera barra: de la primera a la última plica.
        segment(plans.first().stemX, plans.last().stemX + STEM_THICKNESS, 0)
        // Segunda barra: una sola entre semicorcheas vecinas; una semicorchea sola lleva un tramo corto.
        var i = 0
        while (i < plans.size) {
            if (plans[i].beams < 2) { i++; continue }
            var j = i
            while (j + 1 < plans.size && plans[j + 1].beams >= 2) j++
            if (j > i) {
                segment(plans[i].stemX, plans[j].stemX + STEM_THICKNESS, 1)
            } else if (i == 0) {
                segment(plans[i].stemX, plans[i].stemX + STUB_LENGTH, 1)
            } else {
                segment(plans[i].stemX + STEM_THICKNESS - STUB_LENGTH, plans[i].stemX + STEM_THICKNESS, 1)
            }
            i = j + 1
        }
    }

    // --- Acordes ---

    private fun drawChord(
        ops: MutableList<DrawOp>,
        plan: Plan,
        fifths: Int,
        handState: MutableMap<Pair<Step, Int>, Int>,
        tiedFromBefore: Set<Note>,
    ) {
        val hand = plan.hand
        val figure = plan.figure
        val headCode = when (figure.base) {
            Ticks.WHOLE -> Smufl.NOTEHEAD_WHOLE
            Ticks.HALF -> Smufl.NOTEHEAD_HALF
            else -> Smufl.NOTEHEAD_BLACK
        }
        val heads = plan.heads
        val leftEdge = plan.leftEdge
        val rightEdge = plan.rightEdge

        // Líneas adicionales (una sola vez por línea, a lo ancho de todo el acorde).
        val ledgers = sortedSetOf<Int>()
        for (head in heads) {
            val p = head.position
            if (p <= -2) for (q in p..-2) if (q % 2 == 0) ledgers += q
            if (p >= 10) for (q in 10..p) if (q % 2 == 0) ledgers += q
        }
        for (q in ledgers) {
            ops += RectOp(leftEdge - LEDGER_OVERHANG, plan.yOf(q) - LEDGER_THICKNESS / 2, rightEdge - leftEdge + 2 * LEDGER_OVERHANG, LEDGER_THICKNESS)
        }

        // Plica y bandera (si el acorde va en un grupo de barras, la plica la dibuja el grupo).
        if (!plan.whole && plan.group == null) {
            val tip = plan.idealTip()
            if (plan.stemDown) {
                val top = plan.stemAnchorTop
                ops += RectOp(plan.stemX, top, STEM_THICKNESS, tip - top)
                flagFor(figure.base, down = true)?.let { ops += GlyphOp(it, plan.stemX, tip) }
            } else {
                val bottom = plan.stemAnchorBottom
                ops += RectOp(plan.stemX, tip, STEM_THICKNESS, bottom - tip)
                flagFor(figure.base, down = false)?.let { ops += GlyphOp(it, plan.stemX, tip) }
            }
        }

        // Cabezas.
        for (head in heads) ops += GlyphOp(headCode, head.x, plan.yOf(head.position))

        // Alteraciones, en columnas hacia la izquierda si las notas están cerca.
        val accidentals = mutableListOf<Pair<Head, Int>>()
        for (head in heads.sortedByDescending { it.position }) {
            val pitch = head.note.pitch
            val key = pitch.step to pitch.octave
            val current = handState[key] ?: keyAlter(fifths, pitch.step)
            if (pitch.alter != current && head.note !in tiedFromBefore) accidentals += head to pitch.alter
            handState[key] = pitch.alter
        }
        val columnLastPosition = mutableListOf<Int>()
        val placed = accidentals.map { (head, alter) ->
            var column = columnLastPosition.indexOfFirst { last -> last - head.position >= 6 }
            if (column < 0) {
                column = columnLastPosition.size
                columnLastPosition += head.position
            } else {
                columnLastPosition[column] = head.position
            }
            Triple(head, alter, column)
        }
        val columnWidth = DoubleArray(columnLastPosition.size)
        for ((_, alter, column) in placed) columnWidth[column] = max(columnWidth[column], accidentalAdvance(alter).toDouble())
        for ((head, alter, column) in placed) {
            val offset = (0..column).sumOf { columnWidth[it] } + column * ACCIDENTAL_COLUMN_GAP
            ops += GlyphOp(accidentalCode(alter), leftEdge - ACCIDENTAL_MARGIN - offset.toFloat(), plan.yOf(head.position))
        }

        // Puntillos: a la derecha de todas las cabezas, en el espacio (si la nota está en una línea, el de arriba).
        if (figure.dots > 0) {
            val dotYs = heads.map { head ->
                if (head.position % 2 == 0) plan.yOf(head.position) - 0.5f else plan.yOf(head.position)
            }.distinct()
            for (y in dotYs) {
                for (i in 0 until figure.dots) {
                    ops += GlyphOp(Smufl.AUGMENTATION_DOT, rightEdge + DOT_GAP + i * DOT_SPACING, y)
                }
            }
        }
    }

    // --- Ligaduras de prolongación ---

    private fun drawTies(ops: MutableList<DrawOp>, plans: List<Plan>) {
        val byNote = plans.flatMap { plan -> plan.heads.map { it.note to (plan to it) } }.toMap()
        for (plan in plans) {
            for ((index, head) in plan.heads.withIndex()) {
                if (!head.note.tiedToNext) continue
                val target = byNote.entries.firstOrNull { (n, _) ->
                    n.hand == head.note.hand && n.pitch.midi == head.note.pitch.midi && n.startTick == head.note.endTick
                }?.value ?: continue
                val (targetPlan, targetHead) = target
                // Opuesta a la plica; en un acorde, las notas de arriba van hacia arriba y las de abajo hacia abajo.
                val above = if (plan.heads.size == 1) plan.stemDown else index >= plan.heads.size / 2
                val y = plan.yOf(head.position)
                val x1 = head.x + plan.headWidth * 0.85f
                val x2 = targetHead.x + targetPlan.headWidth * 0.15f
                if (x2 > x1) ops += tie(x1, x2, y, above)
            }
        }
    }

    /** Ligadura: una media luna entre dos puntos, más gruesa en el medio. */
    fun tie(x1: Float, x2: Float, headY: Float, above: Boolean): PolygonOp {
        val dir = if (above) -1f else 1f
        val width = x2 - x1
        val height = (0.3f + width * 0.05f).coerceIn(0.5f, 1.1f)
        val thickness = 0.22f
        val y0 = headY + dir * 0.55f
        val steps = 16
        val outer = (0..steps).map { i ->
            val t = i / steps.toFloat()
            x1 + width * t to y0 + dir * height * 4f * t * (1f - t)
        }
        val inner = (steps downTo 0).map { i ->
            val t = i / steps.toFloat()
            x1 + width * t to y0 + dir * (height - thickness) * 4f * t * (1f - t)
        }
        return PolygonOp((outer + inner).flatMap { listOf(it.first, it.second) })
    }

    // --- Silencios ---

    private fun drawRest(ops: MutableList<DrawOp>, hand: Hand, start: Int, duration: Int, measure: MeasureLayout, x: Float) {
        val top = staffTop(hand)
        val wholeMeasure = start == measure.measure.startTick && start + duration == measure.measure.endTick
        val figure = if (wholeMeasure) Figure(Ticks.WHOLE, 0) else figureOf(duration)
        val (code, width) = when (figure.base) {
            Ticks.WHOLE -> Smufl.REST_WHOLE to Smufl.REST_WHOLE_WIDTH
            Ticks.HALF -> Smufl.REST_HALF to Smufl.REST_HALF_WIDTH
            Ticks.QUARTER -> Smufl.REST_QUARTER to Smufl.REST_QUARTER_WIDTH
            Ticks.EIGHTH -> Smufl.REST_8TH to Smufl.REST_8TH_WIDTH
            else -> Smufl.REST_16TH to Smufl.REST_16TH_WIDTH
        }
        // El silencio de compás completo va centrado en el compás, sea cual sea la firma.
        val left = if (wholeMeasure) (measure.contentX + measure.x1) / 2 - width / 2 else x
        // Redonda: cuelga de la 4.ª línea. Blanca: se apoya en la línea central. Los demás, centrados en ella.
        val baseline = if (figure.base == Ticks.WHOLE) top + 1f else top + 2f
        ops += GlyphOp(code, left, baseline)
        for (i in 0 until figure.dots) {
            ops += GlyphOp(Smufl.AUGMENTATION_DOT, left + width + DOT_GAP + i * DOT_SPACING, top + 1.5f)
        }
    }

    private fun flagFor(base: Int, down: Boolean): Int? = when (base) {
        Ticks.EIGHTH -> if (down) Smufl.FLAG_8TH_DOWN else Smufl.FLAG_8TH_UP
        Ticks.SIXTEENTH -> if (down) Smufl.FLAG_16TH_DOWN else Smufl.FLAG_16TH_UP
        else -> null
    }

    private fun accidentalCode(alter: Int): Int = when (alter) {
        2 -> Smufl.DOUBLE_SHARP
        1 -> Smufl.SHARP
        0 -> Smufl.NATURAL
        -1 -> Smufl.FLAT
        else -> Smufl.DOUBLE_FLAT
    }

    private fun accidentalAdvance(alter: Int): Float = when (alter) {
        2 -> Smufl.DOUBLE_SHARP_ADVANCE
        1 -> Smufl.SHARP_ADVANCE
        0 -> Smufl.NATURAL_ADVANCE
        -1 -> Smufl.FLAT_ADVANCE
        else -> Smufl.DOUBLE_FLAT_ADVANCE
    }
}
