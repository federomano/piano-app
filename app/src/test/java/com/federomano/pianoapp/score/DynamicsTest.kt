package com.federomano.pianoapp.score

import com.federomano.pianoapp.audio.PianoMapping
import com.federomano.pianoapp.score.layout.GlyphOp
import com.federomano.pianoapp.score.layout.PolygonOp
import com.federomano.pianoapp.score.layout.ScoreLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicsTest {
    private val bar = 1920

    private fun score(dynamics: List<DynamicMark> = emptyList(), hairpins: List<Hairpin> = emptyList(), bars: Int = 8) = Score(
        title = "t",
        dynamics = dynamics,
        hairpins = hairpins,
        notes = listOf(Note(Hand.RIGHT, Pitch.parse("C4"), 0, bar)),
        rests = (0 until bars).map { Rest(Hand.RIGHT, it * bar, bar) }.drop(1) + (0 until bars).map { Rest(Hand.LEFT, it * bar, bar) },
    )

    @Test
    fun withoutDynamicsEverythingSoundsAsBefore() {
        assertEquals(DEFAULT_VELOCITY, score().velocityAt(0))
        assertEquals(DEFAULT_VELOCITY, score().velocityAt(5000))
    }

    @Test
    fun eachMarkHoldsUntilTheNext() {
        val s = score(listOf(DynamicMark(0, DynamicLevel.P), DynamicMark(2 * bar, DynamicLevel.FF)))
        assertEquals(DynamicLevel.P.velocity, s.velocityAt(0))
        assertEquals(DynamicLevel.P.velocity, s.velocityAt(2 * bar - 1))
        assertEquals(DynamicLevel.FF.velocity, s.velocityAt(2 * bar))
        assertEquals(DynamicLevel.FF.velocity, s.velocityAt(7 * bar))
    }

    @Test
    fun beforeTheFirstMarkTheFirstOneApplies() {
        val s = score(listOf(DynamicMark(bar, DynamicLevel.F)))
        assertEquals(DynamicLevel.F.velocity, s.velocityAt(0))
    }

    @Test
    fun aCrescendoGrowsGraduallyUpToTheNextMark() {
        val s = score(
            listOf(DynamicMark(0, DynamicLevel.P), DynamicMark(3 * bar, DynamicLevel.F)),
            listOf(Hairpin(bar, 3 * bar, HairpinKind.CRESCENDO)),
        )
        assertEquals(DynamicLevel.P.velocity, s.velocityAt(bar))
        val mid = s.velocityAt(2 * bar)
        assertEquals((DynamicLevel.P.velocity + DynamicLevel.F.velocity) / 2, mid)
        val ticks = (bar until 3 * bar step 120).map { s.velocityAt(it) }
        assertTrue("sube sin bajar", ticks.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(DynamicLevel.F.velocity, s.velocityAt(3 * bar))
    }

    @Test
    fun aDiminuendoFallsAndTheLevelStaysAfterIt() {
        val s = score(
            listOf(DynamicMark(0, DynamicLevel.FF)),
            listOf(Hairpin(bar, 2 * bar, HairpinKind.DIMINUENDO)),
        )
        // Sin dinámica que diga adónde llega, baja 24 y se queda ahí (no vuelve a ff).
        assertEquals(DynamicLevel.FF.velocity - 24, s.velocityAt(2 * bar))
        assertEquals(DynamicLevel.FF.velocity - 24, s.velocityAt(6 * bar))
    }

    @Test
    fun aHairpinThatContradictsTheNextMarkUsesTheDefaultStep() {
        val s = score(
            listOf(DynamicMark(0, DynamicLevel.F), DynamicMark(2 * bar, DynamicLevel.P)),
            listOf(Hairpin(bar, 2 * bar, HairpinKind.CRESCENDO)),
        )
        // El crescendo no puede terminar en algo más suave: sube 24 y recién ahí la dinámica siguiente (p) manda.
        assertTrue(s.velocityAt(2 * bar - 1) in (DynamicLevel.F.velocity + 22)..(DynamicLevel.F.velocity + 24))
        assertEquals(DynamicLevel.P.velocity, s.velocityAt(2 * bar))
    }

    @Test
    fun loudnessGrowsSmoothlyWithVelocityEvenAcrossTheLayerChange() {
        val gains = (1..127).map { PianoMapping.pick(60, it).gain }
        assertTrue(gains.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(gains.zipWithNext().all { (a, b) -> b - a < 0.03f })
        assertTrue(gains.last() <= 1f)
    }

    @Test
    fun validateFlagsBadDynamics() {
        assertEquals(emptyList<String>(), score(listOf(DynamicMark(0, DynamicLevel.MF)), listOf(Hairpin(bar, 2 * bar, HairpinKind.CRESCENDO))).validate())
        val outOfOrder = score(listOf(DynamicMark(bar, DynamicLevel.P), DynamicMark(0, DynamicLevel.F)))
        assertTrue(outOfOrder.validate().any { it.contains("ordenadas") })
        val overlap = score(hairpins = listOf(Hairpin(0, 2 * bar, HairpinKind.CRESCENDO), Hairpin(bar, 3 * bar, HairpinKind.DIMINUENDO)))
        assertTrue(overlap.validate().any { it.contains("se pisa") })
        val inside = score(listOf(DynamicMark(bar, DynamicLevel.F)), listOf(Hairpin(0, 2 * bar, HairpinKind.CRESCENDO)))
        assertTrue(inside.validate().any { it.contains("en el medio de un regulador") })
        val past = score(listOf(DynamicMark(20 * bar, DynamicLevel.F)))
        assertTrue(past.validate().any { it.contains("después del final") })
    }

    @Test
    fun hairpinsNeedAnEndAfterTheStart() {
        val failed = runCatching { Hairpin(100, 100, HairpinKind.CRESCENDO) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun jsonRoundTripAndStrictness() {
        val s = score(listOf(DynamicMark(0, DynamicLevel.PP)), listOf(Hairpin(bar, 2 * bar, HairpinKind.DIMINUENDO)))
        assertEquals(s, ScoreJson.decode(ScoreJson.encode(s)))
        val bad = ScoreJson.encode(s).replace("\"pp\"", "\"forte\"")
        assertTrue(runCatching { ScoreJson.decode(bad) }.isFailure)
    }

    @Test
    fun layoutDrawsTheLettersAndTwoLinesPerHairpin() {
        val s = score(listOf(DynamicMark(0, DynamicLevel.P), DynamicMark(3 * bar, DynamicLevel.F)), listOf(Hairpin(bar, 3 * bar, HairpinKind.CRESCENDO)))
        val layout = ScoreLayout.build(s)
        val letters = layout.body.filterIsInstance<GlyphOp>().filter { it.codepoint == 0xE520 || it.codepoint == 0xE522 }
        assertEquals(listOf(0xE520, 0xE522), letters.map { it.codepoint })
        assertTrue(letters.all { it.y > com.federomano.pianoapp.score.layout.Geometry.SYSTEM_BOTTOM })
        val lines = layout.body.filterIsInstance<PolygonOp>().filter { p -> p.points.filterIndexed { i, _ -> i % 2 == 1 }.all { it > com.federomano.pianoapp.score.layout.Geometry.SYSTEM_BOTTOM } }
        assertEquals(2, lines.size)
    }
}
