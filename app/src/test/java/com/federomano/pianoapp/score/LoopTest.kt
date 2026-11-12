package com.federomano.pianoapp.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-24: cuentas del bucle A-B. */
class LoopTest {
    private val starts = listOf(0, 1920, 3840, 5760)

    @Test
    fun theRangeGoesFromTheStartOfTheFirstMeasureToTheEndOfTheLast() {
        assertEquals(1920 to 5760, Loop.ticks(starts, 7680, 1, 2))
        assertEquals(0 to 1920, Loop.ticks(starts, 7680, 0, 0))
    }

    @Test
    fun theOrderOfTheTwoTapsDoesNotMatter() {
        assertEquals(Loop.ticks(starts, 7680, 1, 3), Loop.ticks(starts, 7680, 3, 1))
    }

    @Test
    fun theLastMeasureEndsAtTheEndOfThePiece() {
        assertEquals(5760 to 7680, Loop.ticks(starts, 7680, 3, 3))
    }

    @Test
    fun itWrapsOnlyWhenTheEndIsCrossed() {
        assertTrue(Loop.crossedEnd(5700f, 5760f, 5760))
        assertTrue(Loop.crossedEnd(5759f, 5800f, 5760))
        assertFalse(Loop.crossedEnd(5000f, 5700f, 5760))
        assertFalse("si se empieza después del final, no salta", Loop.crossedEnd(6000f, 6050f, 5760))
    }

    @Test
    fun measureLabelsStartAtZeroWithAPickup() {
        assertEquals(0, Loop.label(0, 0))
        assertEquals(3, Loop.label(2, 1))
    }
}
