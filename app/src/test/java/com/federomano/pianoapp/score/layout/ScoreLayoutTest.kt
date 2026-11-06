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
        assertEquals(4 + 4 + 4 + 6 + 4 + 6 + 4, layout.body.count { it is GlyphOp })
        // Becuadros: 2 en cada pentagrama en el compás 7 y 3 en cada uno en el compás 10.
        assertEquals(4 + 6, layout.body.glyphs(Smufl.NATURAL).size)
    }

    @Test
    fun cancellingFlatsDrawsOneNaturalPerFlatInBothStaves() {
        val demo = DemoScore.create()
        val layout = ScoreLayout.build(demo)
        val measure10 = layout.measures[9]
        val naturalsInMeasure10 = layout.body.glyphs(Smufl.NATURAL).filter { it.x in measure10.x0..measure10.contentX }
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
    fun headerFollowsTheSignaturesAtTheLeftEdge() {
        val layout = ScoreLayout.build(DemoScore.create())
        fun sharpsAt(x: Float) = layout.headerAt(x).ops.glyphs(Smufl.SHARP).size
        fun flatsAt(x: Float) = layout.headerAt(x).ops.glyphs(Smufl.FLAT).size
        assertEquals(0, sharpsAt(0f))
        assertEquals(0, sharpsAt(layout.measures[3].contentX - 0.01f))
        assertEquals(4, sharpsAt(layout.measures[3].contentX))
        assertEquals(6, flatsAt(layout.measures[6].contentX))
        assertEquals(0, flatsAt(layout.measures[9].contentX))
    }

    @Test
    fun scrollBeforeTheStartShowsTheFirstHeader() {
        val layout = ScoreLayout.build(DemoScore.create())
        assertEquals(layout.headerAt(0f), layout.headerAt(-5f))
    }

    @Test
    fun fixedAreaKeepsTheSameWidthWhenSignaturesChange() {
        val layout = ScoreLayout.build(DemoScore.create())
        val widths = layout.measures.map { layout.headerAt(it.contentX).width }.distinct()
        assertEquals(listOf(layout.headerWidth), widths)
    }

    @Test
    fun noTransitionWhenTheNextChangeIsFarAway() {
        val layout = ScoreLayout.build(DemoScore.create())
        assertEquals(null, layout.transitionAt(0f))
    }

    @Test
    fun transitionStartsWhenTheNewHeaderTouchesTheOldOneAndEndsWhenTheyCoincide() {
        val layout = ScoreLayout.build(DemoScore.create())
        val change = layout.measures[3].contentX
        val start = layout.transitionAt(change - layout.headerWidth + 0.01f)!!
        assertEquals(0f, start.progress, 0.01f)
        assertEquals(layout.headerWidth, start.left, 0.02f)
        val half = layout.transitionAt(change - layout.headerWidth / 2)!!
        assertEquals(0.5f, half.progress, 0.001f)
        val end = layout.transitionAt(change - 0.001f)!!
        assertEquals(1f, end.progress, 0.001f)
        assertEquals(0f, end.left, 0.01f)
        assertEquals(layout.headerAt(change).ops, end.header.ops)
    }

    @Test
    fun transitionIsOnlyAboutTheNextChange() {
        val layout = ScoreLayout.build(DemoScore.create())
        // Pasado el último cambio (compás 10) ya no hay transición.
        assertEquals(null, layout.transitionAt(layout.measures[9].contentX + 1f))
    }

    @Test
    fun onlyTheSignatureIsReplacedNotTheBraceClefsOrLines() {
        val layout = ScoreLayout.build(DemoScore.create())
        val a = layout.headerAt(0f)
        val b = layout.headerAt(layout.measures[6].contentX)
        assertEquals(a.fixed, b.fixed)
        assertTrue(a.signature != b.signature)
        assertTrue(b.signature.none { it is RectOp })
        assertTrue(b.signature.filterIsInstance<GlyphOp>().all { it.x >= b.signatureLeft })
        assertTrue(b.fixed.glyphs(Smufl.BRACE).isNotEmpty() && b.fixed.glyphs(Smufl.G_CLEF).isNotEmpty())
    }
}
