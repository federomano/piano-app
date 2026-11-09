package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Rest
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.TimeSignatureChange
import com.federomano.pianoapp.score.Tuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** PIA-72: el silencio de una voz no se dibuja encima de las cabezas de otra voz de la misma mano. */
class RestClearanceTest {
    private val triplet = Tuplet.parse("3:2")

    /** Como los compases 11–14 de Bach: tresillos en la voz 1 (con silencio al principio) y blanca + silencio de negra en la voz 2. */
    private fun twoVoices(): Score {
        val bar = 1440
        val notes = mutableListOf<Note>()
        val rests = mutableListOf<Rest>()
        for (i in 0 until 2) {
            val t = i * bar
            rests += Rest(Hand.RIGHT, t, 320, triplet, 1)
            notes += Note(Hand.RIGHT, Pitch.parse("F4"), t + 320, 160, tuplet = triplet, voice = 1)
            notes += Note(Hand.RIGHT, Pitch.parse("Eb4"), t + 480, 320, tuplet = triplet, voice = 1)
            notes += Note(Hand.RIGHT, Pitch.parse("F4"), t + 800, 160, tuplet = triplet, voice = 1)
            notes += Note(Hand.RIGHT, Pitch.parse("D4"), t + 960, 320, tuplet = triplet, voice = 1)
            notes += Note(Hand.RIGHT, Pitch.parse("F4"), t + 1280, 160, tuplet = triplet, voice = 1)
            notes += Note(Hand.RIGHT, Pitch.parse("B4"), t, 960, voice = 2)
            rests += Rest(Hand.RIGHT, t + 960, 480, null, 2)
            notes += Note(Hand.LEFT, Pitch.parse("Eb3"), t, 960)
            rests += Rest(Hand.LEFT, t + 960, 480, null, 1)
        }
        return Score(title = "t", timeSignatures = listOf(TimeSignatureChange(0, 3, 4)), notes = notes, rests = rests)
    }

    @Test
    fun restsStayClearOfTheOtherVoiceHeads() {
        val ops = ScoreLayout.build(twoVoices()).body.filterIsInstance<GlyphOp>()
        val rests = ops.filter { it.codepoint in Smufl.REST_WHOLE..Smufl.REST_16TH && it.y < Geometry.BASS_TOP }
        val heads = ops.filter { it.codepoint in Smufl.NOTEHEAD_WHOLE..Smufl.NOTEHEAD_BLACK && it.y < Geometry.BASS_TOP }
        assertEquals(4, rests.size)
        for (rest in rests) for (head in heads) {
            val touchX = head.x < rest.x + Smufl.REST_QUARTER_WIDTH && head.x + Smufl.NOTEHEAD_WIDTH > rest.x
            // El silencio de negra mide unos 3 espacios de alto y la cabeza 1, ambos centrados en su línea de base.
            assertTrue("silencio en (${rest.x}, ${rest.y}) pisa la cabeza en (${head.x}, ${head.y})", !(touchX && abs(head.y - rest.y) < 2f))
        }
    }

    @Test
    fun theBracketStaysAboveTheRestThatMovedUp() {
        val body = ScoreLayout.build(twoVoices()).body
        val rest = body.filterIsInstance<GlyphOp>().filter { it.codepoint == Smufl.REST_QUARTER && it.y < Geometry.BASS_TOP }.minBy { it.x }
        val brackets = body.filterIsInstance<RectOp>().filter { it.y < Geometry.TREBLE_TOP - 1f && it.height < 0.2f && it.width > 1f }
        val above = brackets.filter { it.x <= rest.x + 1f && it.x + it.width >= rest.x }
        assertTrue("hay un corchete sobre el silencio", above.isNotEmpty())
        assertTrue("el corchete queda arriba del silencio", above.all { it.y < rest.y - 1.5f })
    }
}
