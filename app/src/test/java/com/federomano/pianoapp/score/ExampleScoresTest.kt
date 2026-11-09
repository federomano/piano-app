package com.federomano.pianoapp.score

import com.federomano.pianoapp.score.layout.ScoreLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleScoresTest {
    @Test
    fun thereAreThePracticeScoresWithUniqueIds() {
        val all = ExampleScores.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(listOf("escala", "himno", "voces", "tresillos", "anacrusa", "adornos", "dinamicas", "articulaciones", "fusas", "ejercicio"), all.map { it.id })
    }

    @Test
    fun everyExampleIsAValidScoreThatLaysOutAndSurvivesJson() {
        for (example in ExampleScores.all) {
            val score = example.create()
            assertEquals("${example.title}: ${score.validate()}", emptyList<String>(), score.validate())
            assertTrue(example.title, score.notes.isNotEmpty())
            ScoreLayout.build(score)
            assertEquals(score, ScoreJson.decode(ScoreJson.encode(score)))
        }
    }

    @Test
    fun theScaleIsFourMeasuresForBothHands() {
        val score = ExampleScores.all.first { it.id == "escala" }.create()
        assertEquals(4 * 1920, score.lengthTicks)
        assertEquals(15, score.notes.count { it.hand == Hand.RIGHT })
        assertEquals(15, score.notes.count { it.hand == Hand.LEFT })
        assertEquals("C4", score.notes.first { it.hand == Hand.RIGHT }.pitch.toString())
        assertEquals(Pitch(Step.C, 0, 5).midi, score.notes.filter { it.hand == Hand.RIGHT }.maxOf { it.pitch.midi })
    }

    @Test
    fun odeToJoyIsSixteenMeasuresWithChordsInTheLeftHand() {
        val score = ExampleScores.all.first { it.id == "himno" }.create()
        assertEquals(16 * 1920, score.lengthTicks)
        assertEquals("Ludwig van Beethoven", score.composer)
        // Primera frase: Mi Mi Fa Sol Sol Fa Mi Re Do Do Re Mi Mi. Re Re
        val melody = score.notes.filter { it.hand == Hand.RIGHT }.sortedBy { it.startTick }.take(15).map { it.pitch.toString() }
        assertEquals(listOf("E4", "E4", "F4", "G4", "G4", "F4", "E4", "D4", "C4", "C4", "D4", "E4", "E4", "D4", "D4"), melody)
        val chordsAtStart = score.notes.filter { it.hand == Hand.LEFT && it.startTick == 0 }
        assertEquals(2, chordsAtStart.size)
    }

    @Test
    fun theExerciseHasTies() {
        val score = ExampleScores.all.first { it.id == "ejercicio" }.create()
        assertTrue(score.notes.any { it.tiedToNext })
    }

    @Test
    fun theShorthandRejectsBadFigures() {
        val ok = ExampleScores.all.all { runCatching { it.create() }.isSuccess }
        assertTrue(ok)
    }
}
