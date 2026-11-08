package com.federomano.pianoapp.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anacrusa (PIA-63): un primer compás más corto, numerado 0. */
class PickupTest {
    private fun score(pickup: Int, vararg notes: Note, rests: List<Rest> = emptyList()) =
        Score(title = "t", pickupTicks = pickup, notes = notes.toList(), rests = rests)

    private fun n(tick: Int, dur: Int) = Note(Hand.RIGHT, Pitch.parse("C4"), tick, dur)

    @Test
    fun withoutPickupMeasuresStartAtOne() {
        val s = score(0, n(0, 1920))
        assertEquals(listOf(1), s.measures().map { it.number })
    }

    @Test
    fun pickupIsMeasureZeroAndShort() {
        val s = score(480, n(0, 480), n(480, 1920), n(2400, 1920))
        val m = s.measures()
        assertEquals(listOf(0, 1, 2), m.map { it.number })
        assertEquals(listOf(0, 480, 2400), m.map { it.startTick })
        assertEquals(480, m[0].endTick)
        assertEquals(1440, m[0].gridOffset)
        assertEquals(0, m[1].gridOffset)
        assertTrue(s.validate().isEmpty())
    }

    @Test
    fun noteCannotCrossPickupBar() {
        val s = score(480, n(0, 960), n(960, 1920))
        assertTrue(s.validate().any { "cruza la barra" in it })
    }

    @Test
    fun pickupMustBeShorterThanAMeasure() {
        val s = score(1920, n(0, 1920))
        assertTrue(s.validate().any { "anacrusa" in it })
    }

    @Test
    fun negativePickupIsRejected() {
        val e = runCatching { score(-1, n(0, 480)) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun jsonRoundTripKeepsPickup() {
        val s = score(480, n(0, 480), n(480, 1920))
        assertEquals(480, ScoreJson.decode(ScoreJson.encode(s)).pickupTicks)
    }

    @Test
    fun exampleIsValidAndNumberedFromZero() {
        val s = ExampleScores.all.first { it.id == "anacrusa" }.create()
        assertTrue(s.validate().toString(), s.validate().isEmpty())
        assertEquals(listOf(0, 1, 2, 3), s.measures().map { it.number })
        assertEquals(720, s.measures().first().endTick)
    }
}
