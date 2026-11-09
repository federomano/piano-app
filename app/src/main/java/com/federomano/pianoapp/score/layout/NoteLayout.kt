package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Chord
import com.federomano.pianoapp.score.Clef
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Measure
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Step
import com.federomano.pianoapp.score.Ticks
import com.federomano.pianoapp.score.Tuplet
import com.federomano.pianoapp.score.chords
import kotlin.math.abs
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
    private const val REST_VOICE_SHIFT = 1f
    /** Separación mínima entre un silencio y las cabezas de otra voz, y cuánto puede correrse en pasos de [REST_STEP] como mucho. */
    private const val REST_CLEARANCE = 0.2f
    private const val REST_STEP = 0.5f
    private const val REST_MAX_MOVE = 5f
    private const val DOT_GAP = 0.35f
    private const val DOT_SPACING = 0.45f
    private const val ACCIDENTAL_MARGIN = 0.2f
    private const val ACCIDENTAL_COLUMN_GAP = 0.15f

    // Notas de adorno: chiquitas, con plica hacia arriba, banderita y una rayita cruzada.
    private const val GRACE_SCALE = 0.62f
    private const val GRACE_GAP = 0.9f
    private const val GRACE_STEM_LENGTH = 2.4f
    private const val GRACE_STEM_THICKNESS = 0.1f
    private const val GRACE_SLASH_THICKNESS = 0.12f

    /** Posición diatónica de la línea inferior de cada pentagrama: Mi4 en clave de sol, Sol2 en clave de fa. */
    private const val TREBLE_BOTTOM = 4 * 7 + 2
    private const val BASS_BOTTOM = 2 * 7 + 4

    /** Figura rítmica: la duración base más los puntillos. */
    data class Figure(val base: Int, val dots: Int)

    /** [tuplet]: si la figura es de un grupo irregular, se dibuja la figura escrita (un tresillo de 160 ticks es una corchea). */
    fun figureOf(realTicks: Int, tuplet: Tuplet? = null): Figure {
        val ticks = tuplet?.written(realTicks) ?: realTicks
        val bases = listOf(Ticks.WHOLE, Ticks.HALF, Ticks.QUARTER, Ticks.EIGHTH, Ticks.SIXTEENTH, Ticks.THIRTY_SECOND)
        for (base in bases) {
            if (ticks == base) return Figure(base, 0)
            if (ticks == base * 3 / 2) return Figure(base, 1)
            if (ticks == base * 7 / 4) return Figure(base, 2)
        }
        // Duración rara (tresillos, etc.): se dibuja con la figura más grande que entre, sin puntillos.
        return Figure(bases.firstOrNull { it <= ticks } ?: Ticks.THIRTY_SECOND, 0)
    }

    /** Duración del grupo de barras: un tiempo del compás (la negra, o la negra con puntillo en los compases compuestos). */
    fun beatTicks(numerator: Int, denominator: Int): Int =
        if (denominator >= 8 && numerator % 3 == 0) Ticks.dotted(Ticks.QUARTER) else Ticks.QUARTER

    private val STEP_INDEX = mapOf(Step.C to 0, Step.D to 1, Step.E to 2, Step.F to 3, Step.G to 4, Step.A to 5, Step.B to 6)

    /** Posición diatónica absoluta: Do0 = 0, cada línea o espacio sube 1. */
    fun diatonic(pitch: Pitch): Int = pitch.octave * 7 + STEP_INDEX.getValue(pitch.step)

    /** Posición en el pentagrama: 0 = línea inferior, 1 = primer espacio, 2 = segunda línea, etc. */
    fun staffPosition(pitch: Pitch, clef: Clef): Int = diatonic(pitch) - if (clef == Clef.TREBLE) TREBLE_BOTTOM else BASS_BOTTOM

    fun staffPosition(pitch: Pitch, hand: Hand): Int = staffPosition(pitch, if (hand == Hand.RIGHT) Clef.TREBLE else Clef.BASS)

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
    private class Plan(val chord: Chord, val measureIndex: Int, val measure: Measure, val noteX: Float, val clef: Clef, val octaveShift: Int) {
        val hand = chord.hand
        val figure = figureOf(chord.durationTicks, chord.tuplet)
        val whole = figure.base == Ticks.WHOLE
        val headWidth = if (whole) Smufl.NOTEHEAD_WHOLE_WIDTH else Smufl.NOTEHEAD_WIDTH
        val heads = chord.notes.map { Head(it, staffPosition(it.pitch, clef) - 7 * octaveShift) }.sortedBy { it.position }
        val lowest = heads.first().position
        val highest = heads.last().position
        val beamable = figure.base <= Ticks.EIGHTH

        /** Plica hacia abajo si lo más alejado de la línea central (posición 4) está arriba o a la par. */
        val voice = chord.voice
        /** Con varias voces en la mano, la voz 1 lleva las plicas hacia arriba y las demás hacia abajo. */
        var forcedStemDown: Boolean? = null
        var stemDown = (highest - 4) >= (4 - lowest)
        var group: BeamGroup? = null
        /** Corrimiento horizontal cuando las cabezas chocan con las de otra voz de la misma mano. */
        var shiftX = 0f
        var stemX = 0f
        var leftEdge = 0f
        var rightEdge = 0f

        val beams: Int get() = when (figure.base) { Ticks.THIRTY_SECOND -> 3; Ticks.SIXTEENTH -> 2; else -> 1 }
        fun yOf(position: Int) = NoteLayout.yOf(position, hand)
        val stemAnchorTop: Float get() = yOf(highest) + STEM_ANCHOR
        val stemAnchorBottom: Float get() = yOf(lowest) - STEM_ANCHOR

        /** Donde terminaría la plica si no hubiera barra. */
        fun idealTip(beamLevels: Int = beams): Float {
            val extra = (beamLevels - 1) * (BEAM_THICKNESS + BEAM_SPACING) + if (group == null && beams > 1) 0.5f * (beams - 1) else 0f
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
            val x0 = noteX + shiftX
            stemX = if (stemDown) x0 else x0 + headWidth - STEM_THICKNESS
            for (head in heads) {
                head.x = when {
                    !head.displaced -> x0
                    stemDown -> x0 - headWidth + STEM_THICKNESS
                    else -> x0 + headWidth - STEM_THICKNESS
                }
            }
            leftEdge = heads.minOf { it.x }
            rightEdge = heads.maxOf { it.x + headWidth }
        }
    }

    /** Corcheas y semicorcheas seguidas dentro de un mismo tiempo, unidas por barras. */
    private class BeamGroup(val plans: List<Plan>) {
        val stemDown = plans.first().forcedStemDown ?: (plans.maxOf { it.highest } - 4 >= 4 - plans.minOf { it.lowest })
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
            Plan(chord, index, measures[index].measure, xOfTick(chord.startTick), score.clefAt(chord.hand, chord.startTick), score.octaveShiftAt(chord.hand, chord.startTick))
        }

        // Plicas: con varias voces en una mano, la voz 1 va arriba y las otras abajo.
        for (hand in Hand.entries) {
            if (score.notes.none { it.hand == hand && it.voice > 1 && !it.grace }) continue
            plans.filter { it.hand == hand }.forEach { it.forcedStemDown = it.voice != 1; it.stemDown = it.voice != 1 }
        }

        // Voces que suenan a la vez con cabezas en el mismo lugar o en lugares contiguos: la de número mayor se corre a la derecha.
        for (a in plans) for (b in plans) {
            if (a.hand != b.hand || b.voice <= a.voice) continue
            if (a.chord.startTick >= b.chord.endTick || b.chord.startTick >= a.chord.endTick) continue
            val collide = a.heads.any { ha -> b.heads.any { hb -> kotlin.math.abs(ha.position - hb.position) <= 1 } }
            if (collide) b.shiftX = max(b.shiftX, a.headWidth)
        }

        // 1) Grupos de barras, por mano y voz.
        for ((hand, voice) in plans.map { it.hand to it.voice }.distinct()) {
            val handPlans = plans.filter { it.hand == hand && it.voice == voice }.sortedBy { it.chord.startTick }
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
                fun slot(p: Plan) = (p.chord.startTick - p.measure.startTick + p.measure.gridOffset) / beat
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
        // Cada adorno se dibuja a la izquierda de la nota principal de su mano en ese tick (la de la voz más baja).
        val graceOwner = plans.groupBy { it.hand to it.chord.startTick }.mapValues { (_, group) -> group.minBy { it.voice } }
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
            val ownGraces = if (graceOwner[plan.hand to plan.chord.startTick] === plan) {
                score.notes.filter { it.grace && it.hand == plan.hand && it.startTick == plan.chord.startTick }.sortedBy { it.pitch.midi }
            } else emptyList()
            // La alteración de un adorno vale para el resto del compás, igual que la de cualquier nota.
            val graceAccidentals = ownGraces.map { grace ->
                val key = grace.pitch.step to grace.pitch.octave
                val current = handState[key] ?: keyAlter(plan.measure.keyFifths, grace.pitch.step)
                handState[key] = grace.pitch.alter
                grace to (grace.pitch.alter != current)
            }
            val leftMost = drawChord(ops, plan, plan.measure.keyFifths, handState, tiedFromBefore)
            if (graceAccidentals.isNotEmpty()) drawGrace(ops, plan.hand, plan.clef, plan.octaveShift, graceAccidentals, leftMost)
        }
        for (group in plans.mapNotNull { it.group }.distinct()) drawBeam(ops, group)

        // 4) Ligaduras de prolongación.
        drawTies(ops, plans)

        // 5) Silencios. Se anota hasta dónde llega cada uno (arriba y abajo) para que los corchetes de grupo lo esquiven.
        val restSpans = mutableMapOf<com.federomano.pianoapp.score.Rest, Pair<Float, Float>>()
        for (rest in score.rests) {
            val index = measureIndexAt(rest.startTick)
            // Con varias voces en la mano durante ese compás, el silencio de la voz 1 sube y el de las demás baja.
            val m = measures[index].measure
            val manyVoices = score.notes.any { it.hand == rest.hand && it.voice > 1 && !it.grace && it.startTick < m.endTick && it.endTick > m.startTick } ||
                score.rests.any { it.hand == rest.hand && it.voice > 1 && it.startTick < m.endTick && it.endTick > m.startTick }
            val yShift = if (!manyVoices) 0f else if (rest.voice == 1) -REST_VOICE_SHIFT else REST_VOICE_SHIFT
            // Cabezas de las otras voces de la mano: el silencio se aparta de ellas (PIA-72).
            val others = if (!manyVoices) emptyList() else plans.filter { it.hand == rest.hand && it.voice != rest.voice }
            restSpans[rest] = drawRest(ops, rest.hand, rest.startTick, rest.durationTicks, rest.tuplet, measures[index], xOfTick(rest.startTick), yShift, manyVoices, rest.voice, others)
        }

        // 6) Corchetes y números de los grupos irregulares.
        drawTuplets(ops, score, plans, ::measureIndexAt, xOfTick, restSpans)
        return ops
    }

    // --- Grupos irregulares (tresillos, etc.) ---

    /** Una figura (acorde o silencio) de un grupo irregular, con el ancho que ocupa en el dibujo. */
    private class TupletItem(
        val startTick: Int, val durationTicks: Int, val tuplet: Tuplet, val plan: Plan?, val left: Float, val right: Float,
        /** Solo en los silencios: hasta dónde llega el dibujo hacia arriba y hacia abajo (y crece hacia abajo). */
        val restTop: Float = Float.MAX_VALUE, val restBottom: Float = -Float.MAX_VALUE,
    )

    private const val TUPLET_LINE_THICKNESS = 0.1f
    private const val TUPLET_GAP = 1.1f
    private const val TUPLET_HOOK = 0.7f
    private const val TUPLET_NUMBER_PADDING = 0.3f

    /**
     * Cada grupo irregular completo lleva su número. Si todas sus figuras están unidas por una misma barra, el número va
     * solo junto a la barra; si no (negras de tresillo, silencios, mezclas), lleva además un corchete con el número en el medio.
     * Va del lado de las plicas (arriba si las plicas suben).
     */
    private fun drawTuplets(
        ops: MutableList<DrawOp>, score: Score, plans: List<Plan>, measureIndexAt: (Int) -> Int, xOfTick: (Int) -> Float,
        restSpans: Map<com.federomano.pianoapp.score.Rest, Pair<Float, Float>>,
    ) {
        val lines = (score.notes.filter { it.tuplet != null }.map { it.hand to it.voice } +
            score.rests.filter { it.tuplet != null }.map { it.hand to it.voice }).distinct()
        for ((hand, voice) in lines) {
            val items = (plans.filter { it.hand == hand && it.voice == voice && it.chord.tuplet != null }
                .map { TupletItem(it.chord.startTick, it.chord.durationTicks, it.chord.tuplet!!, it, it.leftEdge, it.rightEdge) } +
                score.rests.filter { it.hand == hand && it.voice == voice && it.tuplet != null }.map { rest ->
                    val x = xOfTick(rest.startTick)
                    val base = figureOf(rest.durationTicks, rest.tuplet).base
                    val span = restSpans[rest]
                    TupletItem(rest.startTick, rest.durationTicks, rest.tuplet!!, null, x, x + restWidthOf(base),
                        span?.first ?: Float.MAX_VALUE, span?.second ?: -Float.MAX_VALUE)
                }).sortedBy { it.startTick }

            // Figuras seguidas del mismo grupo, dentro de un compás.
            val runs = mutableListOf<MutableList<TupletItem>>()
            for (item in items) {
                val run = runs.lastOrNull()
                val last = run?.last()
                if (last != null && last.tuplet == item.tuplet && last.startTick + last.durationTicks == item.startTick &&
                    measureIndexAt(last.startTick) == measureIndexAt(item.startTick)
                ) run += item else runs += mutableListOf(item)
            }
            for (run in runs) {
                val tuplet = run.first().tuplet
                val span = tuplet.normal * run.minOf { tuplet.written(it.durationTicks) ?: it.durationTicks }
                val origin = run.first().startTick
                for (group in run.groupBy { (it.startTick - origin) / span }.values) {
                    if (group.sumOf { it.durationTicks } != span) continue // grupo incompleto: no se dibuja
                    drawTuplet(ops, hand, voice, tuplet, group, plans)
                }
            }
        }
    }

    private fun restWidthOf(base: Int) = when (base) {
        Ticks.WHOLE -> Smufl.REST_WHOLE_WIDTH
        Ticks.HALF -> Smufl.REST_HALF_WIDTH
        Ticks.QUARTER -> Smufl.REST_QUARTER_WIDTH
        Ticks.EIGHTH -> Smufl.REST_8TH_WIDTH
        Ticks.SIXTEENTH -> Smufl.REST_16TH_WIDTH
        else -> Smufl.REST_32ND_WIDTH
    }

    private fun tupletLabel(tuplet: Tuplet): List<Int> =
        if (tuplet.actual == 3 && tuplet.normal == 2) listOf(Smufl.tupletDigit(3))
        else listOf(Smufl.tupletDigit(tuplet.actual), Smufl.TUPLET_COLON, Smufl.tupletDigit(tuplet.normal))

    private fun labelWidth(label: List<Int>) = label.sumOf {
        (if (it == Smufl.TUPLET_COLON) Smufl.TUPLET_COLON_ADVANCE else Smufl.tupletDigitAdvance(it - 0xE880)).toDouble()
    }.toFloat()

    private fun drawTuplet(ops: MutableList<DrawOp>, hand: Hand, voice: Int, tuplet: Tuplet, group: List<TupletItem>, allPlans: List<Plan>) {
        val plans = group.mapNotNull { it.plan }
        // Del lado de las plicas; un grupo de solo silencios va arriba en la voz 1 y abajo en las otras.
        val above = if (plans.isEmpty()) voice == 1 else plans.count { !it.stemDown } >= plans.count { it.stemDown }
        val beamGroup = plans.firstOrNull()?.group
        val fullyBeamed = beamGroup != null && plans.size == group.size && plans.all { it.group === beamGroup } &&
            beamGroup.plans.size == plans.size
        val top = staffTop(hand)

        // Lo más alejado que llega el grupo hacia ese lado (y crece hacia abajo).
        fun planExtreme(plan: Plan): Float {
            val tip = if (plan.whole) null else if (plan.group != null) plan.group!!.beamY(plan.stemX) else plan.idealTip()
            return if (above) minOf(plan.yOf(plan.highest) - 0.5f, tip ?: Float.MAX_VALUE)
            else maxOf(plan.yOf(plan.lowest) + 0.5f, (tip ?: -Float.MAX_VALUE) + if (plan.group != null) BEAM_THICKNESS else 0f)
        }
        val left = group.first().left
        val right = group.last().right
        // Las cabezas de otros grupos o voces de la misma mano que caen debajo del corchete también cuentan (PIA-72).
        val beside = allPlans.filter { it.hand == hand && it !in plans && it.leftEdge < right + 0.2f && it.rightEdge > left - 0.2f }
        // Los silencios que se corrieron (para no pisar las notas de otra voz) también cuentan.
        val extreme = if (above) minOf(top - 0.5f, plans.minOfOrNull(::planExtreme) ?: Float.MAX_VALUE, group.minOf { it.restTop },
            beside.minOfOrNull { it.yOf(it.highest) - 0.5f } ?: Float.MAX_VALUE)
        else maxOf(top + 4.5f, plans.maxOfOrNull(::planExtreme) ?: -Float.MAX_VALUE, group.maxOf { it.restBottom },
            beside.maxOfOrNull { it.yOf(it.lowest) + 0.5f } ?: -Float.MAX_VALUE)

        val label = tupletLabel(tuplet)
        val width = labelWidth(label)
        val mid = if (fullyBeamed) (beamGroup!!.plans.first().stemX + beamGroup.plans.last().stemX) / 2f else (left + right) / 2f

        fun drawLabel(cx: Float, baseline: Float) {
            var x = cx - width / 2f
            for (code in label) {
                ops += GlyphOp(code, x, baseline)
                x += if (code == Smufl.TUPLET_COLON) Smufl.TUPLET_COLON_ADVANCE else Smufl.tupletDigitAdvance(code - 0xE880)
            }
        }

        if (fullyBeamed) {
            // Solo el número, pegado a la barra.
            val baseline = if (above) extreme - 0.4f else extreme + 0.4f + Smufl.TUPLET_DIGIT_HEIGHT
            drawLabel(mid, baseline)
            return
        }
        // Corchete: línea con un hueco para el número y ganchos hacia las figuras.
        val y = if (above) extreme - TUPLET_GAP else extreme + TUPLET_GAP
        val gapLeft = mid - width / 2f - TUPLET_NUMBER_PADDING
        val gapRight = mid + width / 2f + TUPLET_NUMBER_PADDING
        ops += RectOp(left, y - TUPLET_LINE_THICKNESS / 2, gapLeft - left, TUPLET_LINE_THICKNESS)
        ops += RectOp(gapRight, y - TUPLET_LINE_THICKNESS / 2, right - gapRight, TUPLET_LINE_THICKNESS)
        val hookTop = if (above) y - TUPLET_LINE_THICKNESS / 2 else y + TUPLET_LINE_THICKNESS / 2 - TUPLET_HOOK
        ops += RectOp(left, hookTop, TUPLET_LINE_THICKNESS, TUPLET_HOOK)
        ops += RectOp(right - TUPLET_LINE_THICKNESS, hookTop, TUPLET_LINE_THICKNESS, TUPLET_HOOK)
        drawLabel(mid, y + Smufl.TUPLET_DIGIT_HEIGHT / 2f)
    }

    // --- Barras ---

    private fun layoutBeam(group: BeamGroup) {
        val first = group.plans.first()
        val last = group.plans.last()
        // Todas las plicas del grupo usan el mismo margen para las barras (las del grupo con más barras),
        // así la inclinación depende solo de las notas y no de cuántas barras tiene cada una.
        val levels = group.plans.maxOf { it.beams }
        val tips = group.plans.map { it.idealTip(levels) }
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
        // Barras siguientes (semicorcheas: la 2.ª; fusas: la 2.ª y la 3.ª): una sola entre figuras vecinas que las llevan;
        // una figura sola lleva un tramo corto.
        for (level in 1 until plans.maxOf { it.beams }) {
            var i = 0
            while (i < plans.size) {
                if (plans[i].beams <= level) { i++; continue }
                var j = i
                while (j + 1 < plans.size && plans[j + 1].beams > level) j++
                if (j > i) {
                    segment(plans[i].stemX, plans[j].stemX + STEM_THICKNESS, level)
                } else if (i == 0) {
                    segment(plans[i].stemX, plans[i].stemX + STUB_LENGTH, level)
                } else {
                    segment(plans[i].stemX + STEM_THICKNESS - STUB_LENGTH, plans[i].stemX + STEM_THICKNESS, level)
                }
                i = j + 1
            }
        }
    }

    // --- Acordes ---

    private fun drawChord(
        ops: MutableList<DrawOp>,
        plan: Plan,
        fifths: Int,
        handState: MutableMap<Pair<Step, Int>, Int>,
        tiedFromBefore: Set<Note>,
    ): Float {
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
        // Lo más a la izquierda que llega lo dibujado (cabezas, líneas adicionales y alteraciones): ahí termina el lugar del adorno.
        var leftMost = leftEdge

        // Líneas adicionales (una sola vez por línea, a lo ancho de todo el acorde).
        val ledgers = sortedSetOf<Int>()
        for (head in heads) {
            val p = head.position
            if (p <= -2) for (q in p..-2) if (q % 2 == 0) ledgers += q
            if (p >= 10) for (q in 10..p) if (q % 2 == 0) ledgers += q
        }
        if (ledgers.isNotEmpty()) leftMost = minOf(leftMost, leftEdge - LEDGER_OVERHANG)
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
            val accidentalX = leftEdge - ACCIDENTAL_MARGIN - offset.toFloat()
            leftMost = minOf(leftMost, accidentalX)
            ops += GlyphOp(accidentalCode(alter), accidentalX, plan.yOf(head.position))
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
        drawArticulations(ops, plan)
        return leftMost
    }

    /**
     * Staccato (puntito) y acento («>»): del lado de las cabezas, o sea el opuesto a la plica, alejados de la nota
     * para que no se toquen. Si hay los dos, el puntito queda más cerca y el acento más afuera.
     */
    private fun drawArticulations(ops: MutableList<DrawOp>, plan: Plan) {
        val staccato = plan.heads.any { it.note.staccato }
        val accent = plan.heads.any { it.note.accent }
        if (!staccato && !accent) return
        val above = plan.stemDown
        val edge = if (above) plan.heads.last() else plan.heads.first()
        val sign = if (above) 1 else -1
        // Una nota en una línea necesita un espacio más de distancia que una nota en un espacio.
        var position = edge.position + sign * (if (edge.position % 2 == 0) 3 else 2)
        val centerX = edge.x + plan.headWidth / 2
        if (staccato) {
            // El puntito se dibuja encima de su línea de base: se la corre para que el centro caiga en [position].
            ops += GlyphOp(Smufl.STACCATO_ABOVE, centerX - STACCATO_WIDTH / 2, plan.yOf(position) + STACCATO_WIDTH / 2)
            position += sign * 2
        }
        if (accent) {
            if (staccato) position += sign
            val y = plan.yOf(position)
            // El «>» mide casi un espacio de alto: la línea de base se corre medio alto hacia el centro.
            if (above) ops += GlyphOp(Smufl.ACCENT_ABOVE, centerX - ACCENT_WIDTH / 2, y + ACCENT_HEIGHT / 2)
            else ops += GlyphOp(Smufl.ACCENT_BELOW, centerX - ACCENT_WIDTH / 2, y - ACCENT_HEIGHT / 2)
        }
    }

    private const val STACCATO_WIDTH = 0.336f
    private const val ACCENT_WIDTH = 1.356f
    private const val ACCENT_HEIGHT = 0.98f

    /**
     * Adorno (acciaccatura): una o más notas chiquitas, pegadas a la izquierda de [leftMost], con plica hacia arriba, banderita y una
     * rayita en diagonal. [graces] trae cada nota y si lleva alteración.
     */
    private fun drawGrace(ops: MutableList<DrawOp>, hand: Hand, clef: Clef, octaveShift: Int, graces: List<Pair<Note, Boolean>>, leftMost: Float) {
        val headWidth = Smufl.NOTEHEAD_WIDTH * GRACE_SCALE
        val headX = leftMost - GRACE_GAP - headWidth
        val positions = graces.map { staffPosition(it.first.pitch, clef) - 7 * octaveShift }
        fun yOf(position: Int) = NoteLayout.yOf(position, hand)

        for ((note, needsAccidental) in graces) {
            if (!needsAccidental) continue
            val alter = note.pitch.alter
            val x = headX - accidentalAdvance(alter) * GRACE_SCALE - ACCIDENTAL_MARGIN * GRACE_SCALE
            ops += GlyphOp(accidentalCode(alter), x, yOf(staffPosition(note.pitch, clef) - 7 * octaveShift), GRACE_SCALE)
        }
        val ledgers = sortedSetOf<Int>()
        for (p in positions) {
            if (p <= -2) for (q in p..-2) if (q % 2 == 0) ledgers += q
            if (p >= 10) for (q in 10..p) if (q % 2 == 0) ledgers += q
        }
        for (q in ledgers) {
            ops += RectOp(headX - 0.25f, yOf(q) - LEDGER_THICKNESS / 2, headWidth + 0.5f, LEDGER_THICKNESS)
        }
        for (p in positions) ops += GlyphOp(Smufl.NOTEHEAD_BLACK, headX, yOf(p), GRACE_SCALE)

        val stemX = headX + headWidth - GRACE_STEM_THICKNESS
        val bottom = yOf(positions.min()) - STEM_ANCHOR * GRACE_SCALE
        val tip = yOf(positions.max()) - GRACE_STEM_LENGTH
        ops += RectOp(stemX, tip, GRACE_STEM_THICKNESS, bottom - tip)
        ops += GlyphOp(Smufl.FLAG_8TH_UP, stemX, tip, GRACE_SCALE)
        // Rayita en diagonal que cruza la plica (de abajo a la izquierda hacia arriba a la derecha).
        val x1 = stemX - 0.5f
        val y1 = tip + 1.3f
        val x2 = stemX + 0.9f
        val y2 = tip + 0.5f
        ops += PolygonOp(listOf(x1, y1, x2, y2, x2, y2 + GRACE_SLASH_THICKNESS, x1, y1 + GRACE_SLASH_THICKNESS))
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

    private fun drawRest(
        ops: MutableList<DrawOp>, hand: Hand, start: Int, duration: Int, tuplet: Tuplet?, measure: MeasureLayout, x: Float,
        startShift: Float = 0f, manyVoices: Boolean = false, voice: Int = 1, others: List<Plan> = emptyList(),
    ): Pair<Float, Float> {
        val top = staffTop(hand)
        val wholeMeasure = start == measure.measure.startTick && start + duration == measure.measure.endTick && measure.measure.number != 0
        val figure = if (wholeMeasure) Figure(Ticks.WHOLE, 0) else figureOf(duration, tuplet)
        val (code, width) = when (figure.base) {
            Ticks.WHOLE -> Smufl.REST_WHOLE to Smufl.REST_WHOLE_WIDTH
            Ticks.HALF -> Smufl.REST_HALF to Smufl.REST_HALF_WIDTH
            Ticks.QUARTER -> Smufl.REST_QUARTER to Smufl.REST_QUARTER_WIDTH
            Ticks.EIGHTH -> Smufl.REST_8TH to Smufl.REST_8TH_WIDTH
            Ticks.SIXTEENTH -> Smufl.REST_16TH to Smufl.REST_16TH_WIDTH
            else -> Smufl.REST_32ND to Smufl.REST_32ND_WIDTH
        }
        // El silencio de compás completo va centrado en el compás, sea cual sea la firma.
        val left = if (wholeMeasure && !manyVoices) (measure.contentX + measure.x1) / 2 - width / 2 else x
        // Redonda: cuelga de la 4.ª línea. Blanca: se apoya en la línea central. Los demás, centrados en ella.
        val home = if (figure.base == Ticks.WHOLE) top + 1f else top + 2f
        // Cuánto sube y baja el dibujo respecto de su línea de base (aproximado, en espacios).
        val (above, below) = when (figure.base) {
            Ticks.WHOLE -> 0f to 0.5f
            Ticks.HALF -> 0.5f to 0f
            Ticks.EIGHTH -> 1.0f to 1.0f
            Ticks.SIXTEENTH -> 1.5f to 1.5f
            else -> 1.7f to 2.0f
        }
        // La voz 1 sube y las demás bajan hasta quedar libres de las cabezas de las otras voces; si no se logra, queda donde estaba.
        val direction = if (voice == 1) -1f else 1f
        fun collides(shift: Float) = others.any { other ->
            other.heads.any { head ->
                val y = other.yOf(head.position)
                head.x < left + width + REST_CLEARANCE && head.x + other.headWidth > left - REST_CLEARANCE &&
                    y - 0.5f < home + shift + below + REST_CLEARANCE && y + 0.5f > home + shift - above - REST_CLEARANCE
            }
        }
        var yShift = startShift
        if (collides(yShift)) {
            var candidate = yShift
            while (abs(candidate - startShift) < REST_MAX_MOVE && collides(candidate)) candidate += direction * REST_STEP
            if (!collides(candidate)) yShift = candidate
        }
        val baseline = home + yShift
        // Un silencio de una voz de abajo que, para esquivar las notas, tendría que salir del pentagrama (y chocaría con la otra mano) se omite.
        if (voice != 1 && yShift != startShift && baseline + below > top + 4.5f) return Float.MAX_VALUE to -Float.MAX_VALUE
        ops += GlyphOp(code, left, baseline)
        for (i in 0 until figure.dots) {
            ops += GlyphOp(Smufl.AUGMENTATION_DOT, left + width + DOT_GAP + i * DOT_SPACING, top + 1.5f + yShift)
        }
        return (baseline - above) to (baseline + below)
    }

    private fun flagFor(base: Int, down: Boolean): Int? = when (base) {
        Ticks.EIGHTH -> if (down) Smufl.FLAG_8TH_DOWN else Smufl.FLAG_8TH_UP
        Ticks.SIXTEENTH -> if (down) Smufl.FLAG_16TH_DOWN else Smufl.FLAG_16TH_UP
        Ticks.THIRTY_SECOND -> if (down) Smufl.FLAG_32ND_DOWN else Smufl.FLAG_32ND_UP
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
