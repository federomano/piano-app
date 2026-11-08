package com.federomano.pianoapp.score

import com.federomano.pianoapp.audio.NoteTrigger
import com.federomano.pianoapp.score.layout.ScoreLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicesTest {
    private fun n(hand: Hand, pitch: String, start: Int, dur: Int, voice: Int = 1) =
        Note(hand, Pitch.parse(pitch), start, dur, voice = voice)

    private val left = n(Hand.LEFT, "C3", 0, 1920)

    private fun score(vararg notes: Note, rests: List<Rest> = emptyList()) =
        Score(title = "T", notes = listOf(*notes), rests = rests)

    @Test
    fun voiceDefaultsToOneAndHasALimit() {
        assertEquals(1, n(Hand.RIGHT, "C4", 0, 480).voice)
        assertThrows(IllegalArgumentException::class.java) { n(Hand.RIGHT, "C4", 0, 480, voice = 0) }
        assertThrows(IllegalArgumentException::class.java) { n(Hand.RIGHT, "C4", 0, 480, voice = MAX_VOICES + 1) }
        assertThrows(IllegalArgumentException::class.java) {
            ScoreJson.decode("""{ "title": "X", "notes": [ { "hand": "right", "pitch": "C4", "startTick": 0, "durationTicks": 480, "voice": 9 } ] }""")
        }
    }

    @Test
    fun twoVoicesInOneHandValidateAndSurviveJson() {
        val s = score(
            n(Hand.RIGHT, "E5", 0, 1920),
            n(Hand.RIGHT, "C4", 0, 960, voice = 2), n(Hand.RIGHT, "E4", 960, 960, voice = 2),
            left,
        )
        assertEquals(emptyList<String>(), s.validate())
        assertEquals(s, ScoreJson.decode(ScoreJson.encode(s)))
        ScoreLayout.build(s)
    }

    @Test
    fun overlappingFiguresInTheSameVoiceAreReportedWithAHint() {
        val s = score(n(Hand.RIGHT, "E5", 0, 1920), n(Hand.RIGHT, "C4", 0, 960), left)
        val problems = s.validate()
        assertTrue(problems.toString(), problems.any { it.contains("se pisan") && it.contains("voice") })
    }

    @Test
    fun chordNotesOfDifferentLengthInOneVoiceAreReportedButNotAcrossVoices() {
        val same = score(n(Hand.RIGHT, "C4", 0, 960), n(Hand.RIGHT, "E4", 0, 480), n(Hand.RIGHT, "E4", 480, 480).copy(pitch = Pitch.parse("G4")), left)
        assertTrue(same.validate().isNotEmpty())
        val apart = score(n(Hand.RIGHT, "C4", 0, 960), n(Hand.RIGHT, "E4", 0, 480, voice = 2), left)
        assertTrue(apart.validate().none { it.contains("pisan") })
    }

    @Test
    fun chordsAreGroupedPerVoice() {
        val s = score(n(Hand.RIGHT, "C4", 0, 960), n(Hand.RIGHT, "E4", 0, 960, voice = 2), left)
        assertEquals(listOf(1, 2), s.chords(Hand.RIGHT).map { it.voice })
        assertTrue(s.chords(Hand.RIGHT).none { it.isChord })
    }

    @Test
    fun aHeldBassKeepsSoundingUnderRepeatedChords() {
        val bass = n(Hand.LEFT, "C2", 0, 1920, voice = 2)
        val chords = (0 until 4).flatMap { i -> listOf("E3", "G3").map { n(Hand.LEFT, it, i * 480, 480) } }
        val trigger = NoteTrigger(listOf(bass) + chords)
        val on = ArrayList<Pair<Int, Int>>(); val off = ArrayList<Int>()
        var id = 0
        trigger.reset(-24f)
        for (tick in 0..1920) trigger.advance(tick.toFloat(), { note -> on += tick to note.pitch.midi; ++id }, { off += tick })
        assertEquals(1, on.count { it.second == Pitch.parse("C2").midi })
        assertEquals(listOf(0, 480, 960, 1440), on.filter { it.second == Pitch.parse("E3").midi }.map { it.first })
        assertEquals(9, on.size)
        assertEquals(9, off.size)
    }

    @Test
    fun theVoicesExampleIsValidAndHasTwoVoicesInBothHands() {
        val s = ExampleScores.all.first { it.id == "voces" }.create()
        assertEquals(emptyList<String>(), s.validate())
        assertTrue(s.notes.any { it.hand == Hand.RIGHT && it.voice == 2 })
        assertTrue(s.notes.any { it.hand == Hand.LEFT && it.voice == 2 })
    }
}
