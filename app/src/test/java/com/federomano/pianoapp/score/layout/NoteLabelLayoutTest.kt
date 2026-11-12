package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLabelLayoutTest {
    private fun note(hand: Hand, pitch: String, start: Int, duration: Int = Ticks.QUARTER, tied: Boolean = false, finger: Int? = null) =
        Note(hand, Pitch.parse(pitch), start, duration, tied, finger)

    private fun score(vararg notes: Note) = Score(title = "t", notes = notes.toList())

    @Test
    fun namesAreInDoReMi() {
        assertEquals("Do", NoteLabelLayout.name(Pitch.parse("C4")))
        assertEquals("Sol", NoteLabelLayout.name(Pitch.parse("G2")))
        assertEquals("Fa#", NoteLabelLayout.name(Pitch.parse("F#5")))
        assertEquals("Sib", NoteLabelLayout.name(Pitch.parse("Bb3")))
        assertEquals("Si", NoteLabelLayout.name(Pitch.parse("B4")))
    }

    @Test
    fun textDependsOnWhatIsShown() {
        val n = note(Hand.RIGHT, "E4", 0, finger = 3)
        assertEquals("Mi", NoteLabelLayout.text(n, names = true, fingers = false))
        assertEquals("3", NoteLabelLayout.text(n, names = false, fingers = true))
        assertEquals("Mi 3", NoteLabelLayout.text(n, names = true, fingers = true))
        assertEquals("Mi", NoteLabelLayout.text(note(Hand.RIGHT, "E4", 0), names = true, fingers = true))
        assertEquals("", NoteLabelLayout.text(note(Hand.RIGHT, "E4", 0), names = false, fingers = true))
    }

    @Test
    fun nothingShownMeansNoLabels() {
        val layout = ScoreLayout.build(ExampleScores.all.first { it.id == "himno" }.create())
        assertTrue(layout.labels(names = false, fingers = false).isEmpty())
    }

    @Test
    fun rightHandLabelsGoAboveTheStaffAndLeftHandBelow() {
        val layout = ScoreLayout.build(score(note(Hand.RIGHT, "E4", 0), note(Hand.LEFT, "C3", 0), note(Hand.RIGHT, "G4", 480), note(Hand.LEFT, "G2", 480)))
        val labels = layout.labels(names = true, fingers = false)
        assertEquals(4, labels.size)
        for (l in labels) {
            if (l.hand == Hand.RIGHT) assertTrue(l.op.y <= Geometry.TREBLE_TOP) else assertTrue(l.op.y - l.op.size >= Geometry.SYSTEM_BOTTOM)
        }
    }

    @Test
    fun tiedContinuationsAreNotRepeatedAndFingersOnlyWhereGiven() {
        val s = score(
            note(Hand.RIGHT, "C5", 0, Ticks.QUARTER, tied = true, finger = 1),
            note(Hand.RIGHT, "C5", 480, Ticks.QUARTER, finger = 1),
            note(Hand.RIGHT, "D5", 960),
        )
        val layout = ScoreLayout.build(s)
        assertEquals(listOf("Do 1", "Re"), layout.labels(names = true, fingers = true).map { it.op.text })
        assertEquals(listOf("1"), layout.labels(names = false, fingers = true).map { it.op.text })
    }

    @Test
    fun chordNotesAreStackedOutwardWithoutOverlapping() {
        val layout = ScoreLayout.build(score(note(Hand.RIGHT, "C4", 0), note(Hand.RIGHT, "E4", 0), note(Hand.RIGHT, "G4", 0)))
        val labels = layout.labels(names = true, fingers = false)
        assertEquals(listOf("Do", "Mi", "Sol"), labels.map { it.op.text })
        assertTrue("cada una más arriba que la anterior", labels[0].op.y > labels[1].op.y && labels[1].op.y > labels[2].op.y)
        assertTrue(labels[0].op.y - labels[1].op.y >= NoteLabelLayout.SIZE)
    }

    @Test
    fun labelsNeverTouchWhatIsDrawnOrEachOther() {
        for (id in listOf("himno", "tresillos", "voces", "fusas", "dinamicas", "octavas", "articulaciones")) {
            val layout = ScoreLayout.build(ExampleScores.all.first { it.id == id }.create())
            // Barras de compás que cruzan todo el sistema: no cuentan (los textos las cruzan sin tapar nada).
            val content = Extents.boxes(layout.marks.filterNot { it is RectOp && it.height >= Geometry.SYSTEM_BOTTOM - 0.01f })
            val labels = layout.labels(names = true, fingers = true).map { Extents.box(it.op) }
            for ((i, l) in labels.withIndex()) {
                val hit = content.firstOrNull { c -> c.right - 0.05f > l.left && c.left + 0.05f < l.right && c.bottom - 0.05f > l.top && c.top + 0.05f < l.bottom }
                assertTrue("$id: el texto $i choca con $hit", hit == null)
                for (j in i + 1 until minOf(labels.size, i + 12)) {
                    val o = labels[j]
                    assertFalse("$id: los textos $i y $j se pisan", o.right - 0.01f > l.left && o.left + 0.01f < l.right && o.bottom - 0.01f > l.top && o.top + 0.01f < l.bottom)
                }
            }
        }
    }

    @Test
    fun fitMakesRoomOnlyWhenLabelsNeedIt() {
        val layout = ScoreLayout.build(ExampleScores.all.first { it.id == "himno" }.create())
        val plain = layout.fit(1000f, 100f, 60f)
        val withLabels = layout.fit(1000f, 100f, 60f, layout.labels(names = true, fingers = true))
        assertTrue(withLabels.spPx <= plain.spPx)
    }
}
