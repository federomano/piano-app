package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.DynamicLevel
import com.federomano.pianoapp.score.HairpinKind
import com.federomano.pianoapp.score.Score

/**
 * Dinámicas (letras) y reguladores, debajo del pentagrama de la mano izquierda: valen para las dos manos.
 * Las letras son símbolos de Bravura; los reguladores son dos líneas finas que se abren o se cierran.
 */
object DynamicsLayout {
    /** Línea de base de las letras, debajo del pentagrama de abajo. */
    private const val BASELINE = Geometry.SYSTEM_BOTTOM + 4.0f

    /** Aire entre lo que hay arriba y lo más alto de la letra (que sube casi dos espacios sobre su línea de base). */
    private const val CLEARANCE = 2.1f

    /** Cuánto se abre el regulador de cada lado, y qué tan gruesas son sus líneas. */
    private const val HAIRPIN_OPEN = 0.65f
    private const val HAIRPIN_CLOSED = 0.05f
    private const val HAIRPIN_THICKNESS = 0.14f
    private const val GAP = 0.4f

    /** Símbolo de cada letra y hasta dónde se extiende a izquierda y derecha de su punto de partida (medido en la fuente). */
    private class Letter(val codepoint: Int, val left: Float, val right: Float)

    private fun letter(level: DynamicLevel): Letter = when (level) {
        DynamicLevel.PPP -> Letter(0xE52A, -0.368f, 4.292f)
        DynamicLevel.PP -> Letter(0xE52B, -0.328f, 2.912f)
        DynamicLevel.P -> Letter(0xE520, -0.356f, 1.464f)
        DynamicLevel.MP -> Letter(0xE52C, -0.08f, 3.3f)
        DynamicLevel.MF -> Letter(0xE52D, -0.08f, 3.272f)
        DynamicLevel.F -> Letter(0xE522, -0.564f, 1.456f)
        DynamicLevel.FF -> Letter(0xE52F, -0.54f, 2.44f)
        DynamicLevel.FFF -> Letter(0xE530, -0.62f, 3.32f)
    }

    /**
     * [lowestBetween] dice hasta dónde llega lo dibujado entre dos posiciones: si hay notas graves o acentos debajo del
     * pentagrama, las letras y los reguladores bajan para no pisarlos. Las letras pegadas a un regulador van a su misma altura.
     */
    fun build(score: Score, xOfTick: (Int) -> Float, lowestBetween: (Float, Float) -> Float = { _, _ -> Geometry.SYSTEM_BOTTOM }): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        // Línea de base que necesita cada letra y cada regulador: la de siempre, o más abajo si hay algo debajo.
        fun baselineFor(from: Float, to: Float) = maxOf(BASELINE, lowestBetween(from, to) + CLEARANCE)
        val markBaseline = score.dynamics.associateWith { mark ->
            val l = letter(mark.level)
            val x = xOfTick(mark.startTick)
            baselineFor(x, x + l.right - l.left)
        }.toMutableMap()
        val hairpinBaseline = score.hairpins.associateWith { bar -> baselineFor(xOfTick(bar.startTick), xOfTick(bar.endTick)) }.toMutableMap()
        for (bar in score.hairpins) {
            val touching = score.dynamics.filter { it.startTick == bar.startTick || it.startTick == bar.endTick }
            val shared = (touching.map { markBaseline.getValue(it) } + hairpinBaseline.getValue(bar)).max()
            hairpinBaseline[bar] = shared
            touching.forEach { markBaseline[it] = shared }
        }
        for (mark in score.dynamics) {
            val l = letter(mark.level)
            ops += GlyphOp(l.codepoint, xOfTick(mark.startTick) - l.left, markBaseline.getValue(mark))
        }
        for (bar in score.hairpins) {
            val center = hairpinBaseline.getValue(bar) - 0.5f
            var x0 = xOfTick(bar.startTick)
            var x1 = xOfTick(bar.endTick)
            // Si empieza justo después de una letra (p <), arranca pasando la letra; si termina en una letra (< f), le deja lugar.
            score.dynamics.firstOrNull { it.startTick == bar.startTick }?.let { x0 += letter(it.level).let { l -> l.right - l.left } + GAP }
            x1 -= GAP
            if (x1 - x0 < 1f) x1 = x0 + 1f
            val (open0, open1) = if (bar.kind == HairpinKind.CRESCENDO) HAIRPIN_CLOSED to HAIRPIN_OPEN else HAIRPIN_OPEN to HAIRPIN_CLOSED
            for (side in listOf(-1f, 1f)) {
                val ya = center + side * open0
                val yb = center + side * open1
                val h = HAIRPIN_THICKNESS / 2
                ops += PolygonOp(listOf(x0, ya - h, x1, yb - h, x1, yb + h, x0, ya + h))
            }
        }
        return ops
    }
}
