package com.federomano.pianoapp.score

import com.federomano.pianoapp.audio.NoteTrigger
import com.federomano.pianoapp.score.layout.NoteLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TupletTest {
    private val triplet = Tuplet(3, 2)

    private fun score(notes: List<Note>, rests: List<Rest> = emptyList()) =
        Score(title = "T", notes = notes + Note(Hand.LEFT, Pitch.parse("C3"), 0, Ticks.WHOLE), rests = rests)

    private fun tripletBeat(start: Int = 0, count: Int = 3, tuplet: Tuplet? = triplet) =
        (0 until count).map { Note(Hand.RIGHT, Pitch.parse("C4"), start + it * 160, 160, tuplet = tuplet) }

    @Test
    fun parsesAndWritesTheTextForm() {
        assertEquals(Tuplet(3, 2), Tuplet.parse("3:2"))
        assertEquals("4:3", Tuplet(4, 3).toString())
        listOf("3", "3:3", "x:2", "10:2", "3:0", "").forEach { bad ->
            assertThrows(bad, IllegalArgumentException::class.java) { Tuplet.parse(bad) }
        }
    }

    @Test
    fun writtenFigureIsTheRealDurationTimesActualOverNormal() {
        assertEquals(240, triplet.written(160))
        assertEquals(480, triplet.written(320))
        assertEquals(480, Tuplet(4, 3).written(360))
        assertNull(triplet.written(161))
    }

    @Test
    fun theJsonKeepsTheTuplet() {
        val s = score(tripletBeat(), listOf(Rest(Hand.RIGHT, 480, 1440)))
        assertTrue(ScoreJson.encode(s).contains("\"tuplet\": \"3:2\""))
        assertEquals(s, ScoreJson.decode(ScoreJson.encode(s)))
        assertThrows(IllegalArgumentException::class.java) {
            ScoreJson.decode("""{ "title": "X", "notes": [ { "hand": "right", "pitch": "C4", "startTick": 0, "durationTicks": 160, "tuplet": "tres" } ] }""")
        }
    }

    private val restOfBar = listOf(Rest(Hand.RIGHT, 480, 1440))

    @Test
    fun aCompleteTripletValidates() {
        assertEquals(emptyList<String>(), score(tripletBeat(), restOfBar).validate())
    }

    @Test
    fun swingPairsAndQuarterTripletsValidate() {
        val swing = listOf(
            Note(Hand.RIGHT, Pitch.parse("E4"), 0, 320, tuplet = triplet),
            Note(Hand.RIGHT, Pitch.parse("G4"), 320, 160, tuplet = triplet),
        )
        assertEquals(emptyList<String>(), score(swing, restOfBar).validate())
        val quarters = (0 until 3).map { Note(Hand.RIGHT, Pitch.parse("C5"), it * 320, 320, tuplet = triplet) }
        assertEquals(emptyList<String>(), score(quarters, listOf(Rest(Hand.RIGHT, 960, 960))).validate())
    }

    @Test
    fun aTripletWithoutTupletIsRejectedWithAHint() {
        val problems = score(tripletBeat(tuplet = null), restOfBar).validate()
        assertTrue(problems.toString(), problems.any { it.contains("tuplet") })
    }

    @Test
    fun aDurationThatIsNotAFigureInsideTheGroupIsRejected() {
        val odd = listOf(Note(Hand.RIGHT, Pitch.parse("C4"), 0, 200, tuplet = triplet))
        assertTrue(score(odd, listOf(Rest(Hand.RIGHT, 200, 1720))).validate().any { it.contains("200") })
    }

    @Test
    fun anIncompleteGroupIsRejected() {
        val problems = score(tripletBeat(count = 2), listOf(Rest(Hand.RIGHT, 320, 1600))).validate()
        assertTrue(problems.toString(), problems.any { it.contains("incompleto") })
    }

    @Test
    fun aGroupOfFourInTheTimeOfThreeFillsABar() {
        val four = Tuplet(4, 3)
        val notes = (0 until 4).map { Note(Hand.RIGHT, Pitch.parse("C4"), it * 360, 360, tuplet = four) }
        val s = Score(
            title = "T",
            timeSignatures = listOf(TimeSignatureChange(0, 3, 4)),
            notes = notes + Note(Hand.LEFT, Pitch.parse("C3"), 0, 1440),
        )
        assertEquals(emptyList<String>(), s.validate())
    }

    @Test
    fun theFigureIsDrawnAsTheWrittenOne() {
        assertEquals(NoteLayout.Figure(Ticks.EIGHTH, 0), NoteLayout.figureOf(160, triplet))
        assertEquals(NoteLayout.Figure(Ticks.QUARTER, 0), NoteLayout.figureOf(320, triplet))
        assertEquals(NoteLayout.Figure(Ticks.QUARTER, 0), NoteLayout.figureOf(360, Tuplet(4, 3)))
    }

    @Test
    fun tripletsLayOutAndNotesSoundOnTheExactTicks() {
        val s = score(tripletBeat(), restOfBar)
        com.federomano.pianoapp.score.layout.ScoreLayout.build(s)
        val trigger = NoteTrigger(s.notes.filter { it.hand == Hand.RIGHT })
        val started = ArrayList<Int>()
        trigger.reset(-24f)
        // Se avanza de a un tick: cada nota tiene que empezar justo en su tick.
        for (tick in 0..480) trigger.advance(tick.toFloat(), { started += tick; tick }, { })
        assertEquals(listOf(0, 160, 320), started)
    }

    @Test
    fun theTripletExampleIsValidAndHasTheThreeKindsOfGroup() {
        val s = ExampleScores.all.first { it.id == "tresillos" }.create()
        assertEquals(emptyList<String>(), s.validate())
        assertEquals(setOf(160, 320, 360), s.notes.filter { it.tuplet != null }.map { it.durationTicks }.toSet())
        assertTrue(s.rests.any { it.tuplet != null })
    }
}
