package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.DemoScore
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.KeySignatureChange
import com.federomano.pianoapp.score.Rest
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Ticks
import com.federomano.pianoapp.score.TimeSignatureChange
import com.federomano.pianoapp.score.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreLayoutTest {

    private fun scoreOf(measures: Int, numerator: Int = 4, denominator: Int = 4, fifths: Int = 0): Score {
        val signature = TimeSignatureChange(0, numerator, denominator)
        return Score(
            title = "Prueba",
            timeSignatures = listOf(signature),
            keySignatures = listOf(KeySignatureChange(0, fifths)),
            rests = (0 until measures).flatMap { i ->
                Hand.entries.map { Rest(it, i * signature.measureTicks, signature.measureTicks) }
            },
        )
    }

    private fun List<DrawOp>.glyphs(codepoint: Int) = filterIsInstance<GlyphOp>().filter { it.codepoint == codepoint }

    // --- Pentagramas ---

    @Test
    fun bodyHasTenStaffLinesAcrossTheWholePieceInOneContinuousLine() {
        val layout = ScoreLayout.build(scoreOf(measures = 8))
        val lines = layout.body.filterIsInstance<RectOp>().filter { it.height == Geometry.STAFF_LINE_THICKNESS }
        assertEquals(10, lines.size)
        assertTrue(lines.all { it.x == 0f && it.width == layout.totalWidth })
    }

    @Test
    fun staffLinesAreOneSpaceApartAndTheTwoStavesAreSeparated() {
        val layout = ScoreLayout.build(scoreOf(measures = 2))
        val centers = layout.body.filterIsInstance<RectOp>()
            .filter { it.height == Geometry.STAFF_LINE_THICKNESS }
            .map { it.y + it.height / 2 }
            .sorted()
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f, 10f, 11f, 12f, 13f, 14f), centers.map { Math.round(it * 100) / 100f })
    }

    // --- Barras de compás y números ---

    @Test
    fun everyMeasureEndsWithABarlineThatJoinsBothStaves() {
        val layout = ScoreLayout.build(scoreOf(measures = 5))
        val bars = layout.body.filterIsInstance<RectOp>()
            .filter { it.width == Geometry.THIN_BARLINE && it.height == Geometry.SYSTEM_BOTTOM && it.y == 0f }
        // 4 barras comunes + la parte fina de la doble barra final.
        assertEquals(5, bars.size)
    }

    @Test
    fun lastBarlineIsThinAndThick() {
        val layout = ScoreLayout.build(scoreOf(measures = 3))
        val thick = layout.body.filterIsInstance<RectOp>().filter { it.width == Geometry.THICK_BARLINE }
        assertEquals(1, thick.size)
        assertEquals(layout.totalWidth, thick[0].x + thick[0].width, 0.001f)
    }

    @Test
    fun measuresAreNumberedFromOne() {
        val layout = ScoreLayout.build(scoreOf(measures = 6))
        val numbers = layout.body.filterIsInstance<TextOp>().map { it.text }
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), numbers)
        assertTrue(layout.body.filterIsInstance<TextOp>().all { it.y < 0f })
    }

    // --- Espaciado horizontal ---

    @Test
    fun measuresFollowEachOtherWithoutGaps() {
        val layout = ScoreLayout.build(scoreOf(measures = 6))
        layout.measures.zipWithNext().forEach { (a, b) -> assertEquals(a.x1, b.x0, 0.001f) }
        assertEquals(layout.measures.last().x1, layout.totalWidth, 0.001f)
    }

    @Test
    fun horizontalSpaceIsProportionalToTime() {
        val layout = ScoreLayout.build(scoreOf(measures = 2))
        val perQuarter = Geometry.QUARTER_WIDTH
        assertEquals(perQuarter, layout.xOfTick(Ticks.QUARTER) - layout.xOfTick(0), 0.001f)
        assertEquals(perQuarter / 2, layout.xOfTick(Ticks.EIGHTH) - layout.xOfTick(0), 0.001f)
        assertEquals(4 * perQuarter, layout.xOfTick(Ticks.WHOLE) - layout.xOfTick(0) - (layout.measures[1].contentX - layout.measures[0].x1), 0.001f)
    }

    @Test
    fun aThreeFourMeasureIsShorterThanAFourFourMeasure() {
        val four = ScoreLayout.build(scoreOf(1, 4, 4)).measures[0]
        val three = ScoreLayout.build(scoreOf(1, 3, 4)).measures[0]
        assertEquals(four.x1 - four.x0 - Geometry.QUARTER_WIDTH, three.x1 - three.x0, 0.001f)
    }

    // --- Encabezado fijo ---

    @Test
    fun headerHasBraceBothClefsAndTheTimeSignature() {
        val header = ScoreLayout.build(scoreOf(2, 3, 4)).headerAt(0f)
        assertEquals(1, header.ops.glyphs(Smufl.BRACE).size)
        assertEquals(1, header.ops.glyphs(Smufl.G_CLEF).size)
        assertEquals(1, header.ops.glyphs(Smufl.F_CLEF).size)
        // El 3 y el 4, en cada uno de los dos pentagramas.
        assertEquals(2, header.ops.glyphs(Smufl.timeDigit(3)).size)
        assertEquals(2, header.ops.glyphs(Smufl.timeDigit(4)).size)
    }

    @Test
    fun braceCoversBothStaves() {
        val header = ScoreLayout.build(scoreOf(1)).headerAt(0f)
        val brace = header.ops.glyphs(Smufl.BRACE).single()
        assertEquals(Geometry.SYSTEM_BOTTOM, brace.y, 0.001f)
        assertEquals(Geometry.SYSTEM_BOTTOM, Smufl.BRACE_HEIGHT * brace.scale, 0.01f)
    }

    @Test
    fun clefsSitOnTheirReferenceLines() {
        val header = ScoreLayout.build(scoreOf(1)).headerAt(0f)
        // La clave de sol se apoya en la línea de sol (la 2.ª desde abajo) y la de fa en la línea de fa (la 4.ª).
        assertEquals(3f, header.ops.glyphs(Smufl.G_CLEF).single().y, 0.001f)
        assertEquals(Geometry.BASS_TOP + 1f, header.ops.glyphs(Smufl.F_CLEF).single().y, 0.001f)
    }

    @Test
    fun twoDigitTimeSignatureUsesBothDigits() {
        val header = ScoreLayout.build(scoreOf(1, 12, 8)).headerAt(0f)
        assertEquals(2, header.ops.glyphs(Smufl.timeDigit(1)).size)
        assertEquals(2, header.ops.glyphs(Smufl.timeDigit(2)).size)
        assertEquals(2, header.ops.glyphs(Smufl.timeDigit(8)).size)
    }

    @Test
    fun keySignatureSharpsAreInTheRightPlaces() {
        val header = ScoreLayout.build(scoreOf(1, fifths = 2)).headerAt(0f)
        val sharps = header.ops.glyphs(Smufl.SHARP)
        assertEquals(4, sharps.size)
        // Fa# y Do# en clave de sol (línea superior y 3.er espacio)...
        assertEquals(listOf(0f, 1.5f), sharps.filter { it.y < Geometry.BASS_TOP - 2 }.map { it.y })
        // ...y en clave de fa (Fa en la 2.ª línea desde arriba, Do en el 3.er espacio).
        assertEquals(listOf(Geometry.BASS_TOP + 1f, Geometry.BASS_TOP + 2.5f), sharps.filter { it.y > Geometry.BASS_TOP - 2 }.map { it.y })
    }

    @Test
    fun keySignatureFlatsAreInTheRightPlaces() {
        val header = ScoreLayout.build(scoreOf(1, fifths = -2)).headerAt(0f)
        val flats = header.ops.glyphs(Smufl.FLAT)
        assertEquals(4, flats.size)
        // Si bemol (3.ª línea) y Mi bemol (4.º espacio) en clave de sol.
        assertEquals(listOf(2f, 0.5f), flats.filter { it.y < Geometry.BASS_TOP - 2 }.map { it.y })
    }

    @Test
    fun headerGrowsWithMoreAccidentals() {
        val none = ScoreLayout.build(scoreOf(1, fifths = 0)).headerAt(0f).width
        val three = ScoreLayout.build(scoreOf(1, fifths = 3)).headerAt(0f).width
        val seven = ScoreLayout.build(scoreOf(1, fifths = 7)).headerAt(0f).width
        assertTrue(none < three && three < seven)
    }

    @Test
    fun headerHasItsOwnStaffLines() {
        val header = ScoreLayout.build(scoreOf(1)).headerAt(0f)
        val lines = header.ops.filterIsInstance<RectOp>().filter { it.height == Geometry.STAFF_LINE_THICKNESS }
        assertEquals(10, lines.size)
        assertTrue(lines.all { it.x + it.width == header.width })
    }

    // --- Cambios de armadura y de compás en el medio de la pieza ---

    @Test
    fun noInlineSignaturesWhenNothingChanges() {
        val layout = ScoreLayout.build(scoreOf(measures = 6, fifths = 2))
        assertTrue(layout.body.none { it is GlyphOp })
    }

    @Test
    fun demoScoreDrawsEveryInlineChange() {
        val demo = DemoScore.create()
        assertEquals(emptyList<String>(), demo.validate())
        val layout = ScoreLayout.build(demo)
        assertEquals(12, layout.measures.size)
        // Compás 4: 2 sostenidos y 3/4. Compás 7: cancela 2 sostenidos, 3 bemoles y 6/8. Compás 10: cancela 3 bemoles y 4/4.
        assertEquals(4 + 4 + 4 + 6 + 4 + 6 + 4, layout.inlineChanges.size)
        // Becuadros: 2 en cada pentagrama en el compás 7 y 3 en cada uno en el compás 10.
        assertEquals(4 + 6, layout.inlineChanges.glyphs(Smufl.NATURAL).size)
    }

    @Test
    fun cancellingFlatsDrawsOneNaturalPerFlatInBothStaves() {
        val demo = DemoScore.create()
        val layout = ScoreLayout.build(demo)
        val measure10 = layout.measures[9]
        val naturalsInMeasure10 = layout.inlineChanges.glyphs(Smufl.NATURAL).filter { it.x in measure10.x0..measure10.contentX }
        assertEquals(6, naturalsInMeasure10.size)
    }

    @Test
    fun measureWithAChangeHasRoomForItsSignature() {
        val layout = ScoreLayout.build(DemoScore.create())
        val plainLead = layout.measures[1].contentX - layout.measures[1].x0
        val changedLead = layout.measures[3].contentX - layout.measures[3].x0
        assertTrue(changedLead > plainLead + 3f)
    }

    @Test
    fun scrollBeforeTheStartShowsTheFirstHeader() {
        val layout = ScoreLayout.build(DemoScore.create())
        assertEquals(layout.headerAt(0f), layout.headerAt(-5f))
    }

    private val demo get() = ScoreLayout.build(DemoScore.create())
    private fun ScoreLayout.sharpsAt(x: Float) = headerAt(x).ops.glyphs(Smufl.SHARP).size
    private fun ScoreLayout.flatsAt(x: Float) = headerAt(x).ops.glyphs(Smufl.FLAT).size

    @Test
    fun fixedAreaFollowsTheSignaturesOnceTheyArrive() {
        val layout = demo
        val key = layout.changes
        assertEquals(3, key.size)
        assertEquals(0, layout.sharpsAt(0f))
        assertEquals(0, layout.sharpsAt(key[0].pinScroll - 0.01f))
        assertEquals(4, layout.sharpsAt(key[0].pinScroll))
        assertEquals(6, layout.flatsAt(key[1].pinScroll))
        assertEquals(0, layout.flatsAt(key[2].pinScroll))
    }

    @Test
    fun fixedAreaNeverChangesWidth() {
        val layout = demo
        val widths = layout.changes.map { layout.headerAt(it.pinScroll).width }.distinct()
        assertEquals(listOf(layout.headerWidth), widths)
    }

    @Test
    fun braceClefsAndLinesAreNotPartOfAnySignature() {
        val layout = demo
        for (change in layout.changes) {
            assertTrue((change.ops + change.cancel).none { it is RectOp })
            assertTrue((change.ops + change.cancel).filterIsInstance<GlyphOp>().none { it.codepoint == Smufl.BRACE || it.codepoint == Smufl.G_CLEF })
        }
        assertEquals(layout.headerAt(0f).ops.glyphs(Smufl.BRACE), layout.headerAt(1000f).ops.glyphs(Smufl.BRACE))
    }

    @Test
    fun aChangeExistsOnlyOnceOnScreen() {
        // Mientras el cambio viaja con la partitura, el cuerpo no lo dibuja por su cuenta: solo hay una copia.
        val layout = demo
        assertTrue(layout.staff.none { it is GlyphOp })
        val change = layout.changes.first()
        val draws = layout.headerDraws(change.pinScroll - 50f, 200f)
        assertEquals(1, draws.count { it.ops === change.ops })
    }

    @Test
    fun theTravellingChangeLandsExactlyWhereTheFixedOneIs() {
        val layout = demo
        for (change in layout.changes) {
            val landed = layout.headerDraws(change.pinScroll, 200f)
            assertTrue(landed.filter { it.ops === change.ops }.all { it.dx == 0f && it.alpha == 1f })
            assertEquals(change.ops, layout.headerAt(change.pinScroll).ops.filter { it in change.ops })
        }
    }

    @Test
    fun oldSignatureFadesOutWhileTheNewOneArrives() {
        val layout = demo
        val change = layout.changes.first()
        fun oldAlpha(scroll: Float) = layout.headerDraws(scroll, 200f).first { it.dx == 0f && it.ops !== change.ops }.alpha
        val touching = change.pinScroll - (layout.headerWidth - change.anchor)
        assertEquals(1f, oldAlpha(touching - 1f), 0.001f)
        assertEquals(0.5f, oldAlpha((touching + change.pinScroll) / 2), 0.01f)
        assertEquals(0f, layout.progress(change, change.pinScroll - 0.0001f).let { 1f - it }, 0.01f)
        val travelling = layout.headerDraws(touching - 5f, 200f).first { it.ops === change.ops }
        assertEquals(1f, travelling.alpha, 0.001f)
        assertTrue(travelling.dx > layout.headerWidth - change.anchor)
    }

    @Test
    fun cancelledNaturalsFadeWithTheOldSignature() {
        val layout = demo
        val change = layout.changes.first { it.cancel.isNotEmpty() }
        val half = (change.pinScroll - (layout.headerWidth - change.anchor) + change.pinScroll) / 2
        val naturals = layout.headerDraws(half, 200f).first { it.ops === change.cancel }
        assertEquals(0.5f, naturals.alpha, 0.01f)
    }
}
