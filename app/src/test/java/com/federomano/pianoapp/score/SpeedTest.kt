package com.federomano.pianoapp.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTest {
    @Test
    fun snapKeepsTheValueInRangeAndInFivePercentSteps() {
        assertEquals(0.25f, Speed.snap(0.1f), 1e-6f)
        assertEquals(2f, Speed.snap(5f), 1e-6f)
        assertEquals(0.75f, Speed.snap(0.7449f), 1e-6f)
        assertEquals(1f, Speed.snap(0.99f), 1e-6f)
    }

    @Test
    fun presetsAreAllValidSpeeds() {
        for (p in Speed.PRESETS) assertEquals(p, Speed.snap(p), 1e-6f)
        assertEquals(listOf(50, 75, 100, 125, 150), Speed.PRESETS.map(Speed::percent))
    }

    @Test
    fun effectiveBpmIsTheTempoTimesTheSpeed() {
        val tempo = TempoMap(listOf(TempoChange(0, 90.0), TempoChange(1920, 60.0)))
        assertEquals(90f, tempo.bpmAt(0f), 1e-4f)
        assertEquals(60f, tempo.bpmAt(2000f), 1e-4f)
        assertEquals(45f, Speed.effectiveBpm(tempo.bpmAt(2000f), 0.75f), 1e-4f)
    }

    @Test
    fun halfSpeedTakesTwiceAsLongToCoverTheSameMusic() {
        val tempo = TempoMap(listOf(TempoChange(0, 90.0), TempoChange(1920, 60.0)))
        fun seconds(speed: Float): Float {
            var tick = 0f; var t = 0f
            while (tick < 4000f) { tick = tempo.advance(tick, 0.01f * speed); t += 0.01f }
            return t
        }
        assertEquals(2f, seconds(0.5f) / seconds(1f), 0.02f)
        assertEquals(0.5f, seconds(2f) / seconds(1f), 0.02f)
    }

    @Test
    fun theLimitsAreAQuarterAndDouble() {
        assertTrue(Speed.MIN == 0.25f && Speed.MAX == 2f)
    }
}
