package com.federomano.pianoapp.score

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTest {
    @Test
    fun oneQuarterAtSixtyIsOneSecond() {
        val map = TempoMap(listOf(TempoChange(0, 60.0)))
        assertEquals(Ticks.QUARTER.toFloat(), map.advance(0f, 1f), 0.01f)
    }

    @Test
    fun advanceCrossesTempoChangesUsingEachTempo() {
        // 60 bpm hasta el tick 960 (2 s), después 120 bpm (1 negra cada 0,5 s).
        val map = TempoMap(listOf(TempoChange(0, 60.0), TempoChange(960, 120.0)))
        assertEquals(960f + 960f, map.advance(0f, 3f), 0.01f)
        assertEquals(960f + 960f, map.advance(480f, 2f), 0.01f)
    }

    @Test
    fun advanceAndSecondsBetweenAreInverse() {
        val map = TempoMap(listOf(TempoChange(0, 90.0), TempoChange(5760, 60.0), TempoChange(10080, 80.0)))
        val seconds = map.secondsBetween(1000f, 12000f)
        assertEquals(12000f, map.advance(1000f, seconds), 0.5f)
    }

    @Test
    fun smallStepsGiveTheSameAsOneBigStep() {
        val map = TempoMap(listOf(TempoChange(0, 90.0), TempoChange(5760, 60.0)))
        var tick = 0f
        repeat(600) { tick = map.advance(tick, 1f / 60f) }
        assertEquals(map.advance(0f, 10f), tick, 1f)
    }
}
