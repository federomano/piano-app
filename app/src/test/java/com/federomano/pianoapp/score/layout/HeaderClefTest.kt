package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Clef
import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIA-74: la clave grande fija de cada mano sigue los cambios de clave. */
class HeaderClefTest {
    private val layout = ScoreLayout.build(ExampleScores.all.first { it.id == "claves" }.create())

    private fun clefsOf(ops: List<DrawOp>) = ops.filterIsInstance<GlyphOp>().filter { it.codepoint == Smufl.G_CLEF || it.codepoint == Smufl.F_CLEF }

    private fun clefAtHand(scroll: Float, hand: Hand): Int? {
        val top = if (hand == Hand.RIGHT) Geometry.TREBLE_TOP else Geometry.BASS_TOP
        return clefsOf(layout.headerAt(scroll).ops).single { it.y in top..top + 4f }.codepoint
    }

    private val leftChange get() = layout.clefChanges.first { it.hand == Hand.LEFT && it.clef == Clef.TREBLE }
    private val rightChange get() = layout.clefChanges.first { it.hand == Hand.RIGHT && it.clef == Clef.BASS }

    @Test
    fun oneHeaderChangePerClefChangeAndTheyAreOrdered() {
        assertEquals(4, layout.clefChanges.size)
        for (c in layout.clefChanges) assertTrue(c.endScroll > c.startScroll)
        for (hand in Hand.entries) {
            val mine = layout.clefChanges.filter { it.hand == hand }
            assertEquals(mine.sortedBy { it.startScroll }, mine)
        }
    }

    @Test
    fun startsWithTrebleAndBass() {
        assertEquals(Smufl.G_CLEF, clefAtHand(0f, Hand.RIGHT))
        assertEquals(Smufl.F_CLEF, clefAtHand(0f, Hand.LEFT))
        assertEquals(Smufl.G_CLEF, clefAtHand(-50f, Hand.RIGHT))
    }

    @Test
    fun theFixedClefChangesOnceTheSmallOneArrivesAndComesBackWhenRewinding() {
        val change = leftChange
        assertEquals(Smufl.F_CLEF, clefAtHand(change.startScroll, Hand.LEFT))
        assertEquals(Smufl.G_CLEF, clefAtHand(change.endScroll, Hand.LEFT))
        assertEquals(Smufl.G_CLEF, clefAtHand(change.endScroll + 5f, Hand.LEFT))
        assertEquals(Smufl.F_CLEF, clefAtHand(change.startScroll - 5f, Hand.LEFT))
        // La otra mano no se mueve.
        assertEquals(Smufl.G_CLEF, clefAtHand(change.endScroll, Hand.RIGHT))
    }

    @Test
    fun laterChangesApplyAndTheLastOneRestoresTheOriginals() {
        val right = rightChange
        assertEquals(Smufl.F_CLEF, clefAtHand(right.endScroll + 1f, Hand.RIGHT))
        assertEquals(Smufl.G_CLEF, clefAtHand(right.endScroll + 1f, Hand.LEFT))
        val end = layout.clefChanges.maxOf { it.endScroll } + 1f
        assertEquals(Smufl.G_CLEF, clefAtHand(end, Hand.RIGHT))
        assertEquals(Smufl.F_CLEF, clefAtHand(end, Hand.LEFT))
    }

    @Test
    fun alwaysExactlyTwoFixedClefsOnePerStaff() {
        val ends = layout.clefChanges.flatMap { listOf(it.startScroll, (it.startScroll + it.endScroll) / 2, it.endScroll) }
        for (scroll in listOf(-10f, 0f) + ends + listOf(layout.totalWidth)) {
            assertEquals("scroll $scroll", 2, clefsOf(layout.headerAt(scroll).ops).size)
        }
    }

    @Test
    fun midTransitionBothClefsAreDrawnAndTheirOpacitiesAddUpToOne() {
        val change = leftChange
        val mid = (change.startScroll + change.endScroll) / 2
        val draws = layout.clefDraws(mid)
        val left = draws.filter { d -> clefsOf(d.ops).any { it.y >= Geometry.BASS_TOP } }
        assertEquals(2, left.size)
        assertEquals(1f, left.sumOf { it.alpha.toDouble() }.toFloat(), 1e-4f)
        assertEquals(setOf(Smufl.F_CLEF, Smufl.G_CLEF), left.flatMap { clefsOf(it.ops) }.map { it.codepoint }.toSet())
        // Fuera de una transición, una sola clave por mano y opaca.
        val still = layout.clefDraws(0f)
        assertEquals(2, still.size)
        assertTrue(still.all { it.alpha == 1f })
    }

    @Test
    fun theFixedClefIsDrawnAtTheSamePlaceAsBefore() {
        val g = clefsOf(layout.headerAt(0f).ops).single { it.codepoint == Smufl.G_CLEF }
        assertEquals(Geometry.TREBLE_TOP + 3f, g.y, 1e-4f)
        val f = clefsOf(layout.headerAt(0f).ops).single { it.codepoint == Smufl.F_CLEF }
        assertEquals(Geometry.BASS_TOP + 1f, f.y, 1e-4f)
        assertEquals(g.x, f.x, 0f)
    }
}
