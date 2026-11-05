package com.federomano.pianoapp.score

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTest {

    private fun note(
        hand: Hand,
        pitch: String,
        start: Int,
        duration: Int = Ticks.QUARTER,
        tied: Boolean = false,
        finger: Int? = null,
    ) = Note(hand, Pitch.parse(pitch), start, duration, tied, finger)

    private fun score(
        notes: List<Note> = emptyList(),
        rests: List<Rest> = emptyList(),
        timeSignatures: List<TimeSignatureChange> = listOf(TimeSignatureChange(0, 4, 4)),
        keySignatures: List<KeySignatureChange> = listOf(KeySignatureChange(0, 0)),
        tempos: List<TempoChange> = listOf(TempoChange(0, 100.0)),
    ) = Score(
        title = "Prueba",
        timeSignatures = timeSignatures,
        keySignatures = keySignatures,
        tempos = tempos,
        notes = notes,
        rests = rests,
    )

    // --- Altura (pitch) ---

    @Test
    fun pitchMidiNumbers() {
        assertEquals(60, Pitch.parse("C4").midi)
        assertEquals(69, Pitch.parse("A4").midi)
        assertEquals(21, Pitch.parse("A0").midi)
        assertEquals(108, Pitch.parse("C8").midi)
        assertEquals(0, Pitch.parse("C-1").midi)
    }

    @Test
    fun enharmonicNotesSoundTheSameButKeepTheirSpelling() {
        val sharp = Pitch.parse("F#4")
        val flat = Pitch.parse("Gb4")
        assertEquals(66, sharp.midi)
        assertEquals(sharp.midi, flat.midi)
        assertTrue(sharp != flat)
        assertEquals("F#4", sharp.toString())
        assertEquals("Gb4", flat.toString())
    }

    @Test
    fun doubleAccidentalsRoundTrip() {
        assertEquals(Pitch(Step.C, 2, 5), Pitch.parse("C##5"))
        assertEquals("Bbb3", Pitch.parse("Bbb3").toString())
        assertEquals(Pitch.parse("A3").midi, Pitch.parse("Bbb3").midi)
    }

    @Test
    fun invalidPitchTextIsRejected() {
        listOf("H4", "C", "4", "C#", "c4", "C###4", "").forEach {
            assertThrows("\"$it\" debería fallar", IllegalArgumentException::class.java) { Pitch.parse(it) }
        }
    }

    // --- Notas, manos, duraciones y digitación ---

    @Test
    fun noteHasStartDurationAndEnd() {
        val n = note(Hand.RIGHT, "E4", start = 960, duration = Ticks.HALF)
        assertEquals(960, n.startTick)
        assertEquals(1920, n.endTick)
    }

    @Test
    fun durationsAndDots() {
        assertEquals(1920, Ticks.WHOLE)
        assertEquals(480, Ticks.QUARTER)
        assertEquals(240, Ticks.EIGHTH)
        assertEquals(120, Ticks.SIXTEENTH)
        assertEquals(720, Ticks.dotted(Ticks.QUARTER))
        assertEquals(1440, Ticks.dotted(Ticks.HALF))
    }

    @Test
    fun bothHandsAreRepresented() {
        val s = score(listOf(note(Hand.RIGHT, "C4", 0), note(Hand.LEFT, "C3", 0)))
        assertEquals(listOf(Hand.RIGHT), s.chords(Hand.RIGHT).map { it.hand })
        assertEquals(listOf(Hand.LEFT), s.chords(Hand.LEFT).map { it.hand })
        assertEquals(2, s.chords().size)
    }

    @Test
    fun fingeringIsOptionalAndLimitedToOneToFive() {
        assertNull(note(Hand.RIGHT, "C4", 0).finger)
        assertEquals(3, note(Hand.RIGHT, "C4", 0, finger = 3).finger)
        listOf(0, 6, -1).forEach {
            assertThrows(IllegalArgumentException::class.java) { note(Hand.RIGHT, "C4", 0, finger = it) }
        }
    }

    @Test
    fun invalidNotesAndRestsCannotBeBuilt() {
        assertThrows(IllegalArgumentException::class.java) { note(Hand.RIGHT, "C4", -1) }
        assertThrows(IllegalArgumentException::class.java) { note(Hand.RIGHT, "C4", 0, duration = 0) }
        assertThrows(IllegalArgumentException::class.java) { Rest(Hand.LEFT, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { Rest(Hand.LEFT, -5, 480) }
    }

    // --- Acordes ---

    @Test
    fun notesStartingTogetherInTheSameHandFormAChord() {
        val s = score(
            listOf(
                note(Hand.RIGHT, "G4", 0, Ticks.HALF),
                note(Hand.RIGHT, "C4", 0, Ticks.HALF),
                note(Hand.RIGHT, "E4", 0, Ticks.HALF),
                note(Hand.RIGHT, "D4", Ticks.HALF, Ticks.HALF),
            ),
        )
        val chords = s.chords(Hand.RIGHT)
        assertEquals(2, chords.size)
        assertTrue(chords[0].isChord)
        assertEquals(listOf("C4", "E4", "G4"), chords[0].notes.map { it.pitch.toString() })
        assertFalse(chords[1].isChord)
        assertEquals(Ticks.HALF, chords[0].endTick)
    }

    @Test
    fun sameStartInDifferentHandsIsNotOneChord() {
        val s = score(listOf(note(Hand.RIGHT, "C4", 0), note(Hand.LEFT, "C3", 0)))
        assertEquals(2, s.chords().size)
        assertTrue(s.chords().none { it.isChord })
    }

    // --- Silencios ---

    @Test
    fun restsCountTowardsTheLengthOfTheScore() {
        val s = score(
            notes = listOf(note(Hand.RIGHT, "C4", 0)),
            rests = listOf(Rest(Hand.RIGHT, Ticks.QUARTER, Ticks.dotted(Ticks.HALF))),
        )
        assertEquals(Ticks.QUARTER + Ticks.dotted(Ticks.HALF), s.lengthTicks)
        assertEquals(Hand.RIGHT, s.rests.single().hand)
    }

    // --- Ligaduras de prolongación ---

    @Test
    fun tieAcrossABarlineIsValid() {
        val s = score(
            listOf(
                note(Hand.RIGHT, "G4", 1440, tied = true),
                note(Hand.RIGHT, "G4", 1920, Ticks.HALF),
            ),
            rests = listOf(Rest(Hand.RIGHT, 2880, 960)),
        )
        assertEquals(emptyList<String>(), s.validate())
    }

    @Test
    fun tieWithoutAFollowingNoteIsReported() {
        val s = score(listOf(note(Hand.RIGHT, "G4", 0, tied = true), note(Hand.RIGHT, "A4", 480)))
        assertTrue(s.validate().single().contains("ligada"))
    }

    // --- Compases, firma de compás, armadura y tempo ---

    @Test
    fun measureLengthDependsOnTheTimeSignature() {
        assertEquals(1920, TimeSignatureChange(0, 4, 4).measureTicks)
        assertEquals(1440, TimeSignatureChange(0, 3, 4).measureTicks)
        assertEquals(1440, TimeSignatureChange(0, 6, 8).measureTicks)
        assertEquals(960, TimeSignatureChange(0, 2, 4).measureTicks)
    }

    @Test
    fun measuresFollowTimeAndKeySignatureChanges() {
        val s = score(
            rests = listOf(
                Rest(Hand.RIGHT, 0, 1920),
                Rest(Hand.RIGHT, 1920, 1440),
                Rest(Hand.RIGHT, 3360, 1440),
            ),
            timeSignatures = listOf(TimeSignatureChange(0, 4, 4), TimeSignatureChange(1920, 3, 4)),
            keySignatures = listOf(KeySignatureChange(0, 0), KeySignatureChange(1920, 2)),
        )
        val measures = s.measures()
        assertEquals(3, measures.size)
        assertEquals(listOf(1, 2, 3), measures.map { it.number })
        assertEquals(listOf(0, 1920, 3360), measures.map { it.startTick })
        assertEquals(listOf(1920, 3360, 4800), measures.map { it.endTick })
        assertEquals(listOf(4, 3, 3), measures.map { it.timeSignature.numerator })
        assertEquals(listOf(0, 2, 2), measures.map { it.keyFifths })
        assertEquals(emptyList<String>(), s.validate())
    }

    @Test
    fun emptyScoreHasOneMeasure() {
        assertEquals(1, score().measures().size)
    }

    @Test
    fun tempoChangesAreKept() {
        val s = score(tempos = listOf(TempoChange(0, 90.0), TempoChange(1920, 120.0)))
        assertEquals(listOf(90.0, 120.0), s.tempos.map { it.bpm })
        assertEquals(1920, s.tempos[1].startTick)
        assertThrows(IllegalArgumentException::class.java) { TempoChange(0, 0.0) }
    }

    @Test
    fun invalidSignaturesCannotBeBuilt() {
        assertThrows(IllegalArgumentException::class.java) { TimeSignatureChange(0, 0, 4) }
        assertThrows(IllegalArgumentException::class.java) { TimeSignatureChange(0, 4, 5) }
        assertThrows(IllegalArgumentException::class.java) { KeySignatureChange(0, 8) }
        assertThrows(IllegalArgumentException::class.java) { KeySignatureChange(0, -8) }
    }

    // --- Validación ---

    @Test
    fun noteCrossingABarlineIsReported() {
        val s = score(listOf(note(Hand.RIGHT, "C4", 1680, Ticks.HALF)))
        assertTrue(s.validate().single().contains("cruza la barra de compás"))
    }

    @Test
    fun restCrossingABarlineIsReported() {
        val s = score(rests = listOf(Rest(Hand.LEFT, 1680, Ticks.HALF)))
        assertTrue(s.validate().single().contains("cruza la barra de compás"))
    }

    @Test
    fun overlappingSamePitchInTheSameHandIsReported() {
        val s = score(listOf(note(Hand.RIGHT, "C4", 0, Ticks.HALF), note(Hand.RIGHT, "C4", Ticks.QUARTER)))
        assertTrue(s.validate().single().contains("se pisan"))
    }

    @Test
    fun samePitchInDifferentHandsCanSoundTogether() {
        val s = score(listOf(note(Hand.RIGHT, "C4", 0), note(Hand.LEFT, "C4", 0)))
        assertEquals(emptyList<String>(), s.validate())
    }

    @Test
    fun notesOutsideThePianoRangeAreReported() {
        val s = score(listOf(note(Hand.RIGHT, "G0", 0), note(Hand.RIGHT, "C#8", 480)))
        assertEquals(2, s.validate().size)
        assertTrue(s.validate().all { it.contains("fuera del rango") })
    }

    @Test
    fun timeSignatureChangeInTheMiddleOfAMeasureIsReported() {
        val s = score(
            rests = listOf(Rest(Hand.RIGHT, 0, 1920), Rest(Hand.RIGHT, 1920, 1920)),
            timeSignatures = listOf(TimeSignatureChange(0, 4, 4), TimeSignatureChange(960, 3, 4)),
        )
        assertTrue(s.validate().any { it.contains("no cae al inicio de un compás") })
    }

    @Test
    fun unorderedChangesAreReported() {
        val s = score(tempos = listOf(TempoChange(480, 90.0), TempoChange(0, 100.0)))
        assertEquals(2, s.validate().size)
    }

    // --- JSON ---

    @Test
    fun jsonRoundTripKeepsEverything() {
        val original = Score(
            title = "Completa",
            composer = "Alguien",
            level = "principiante",
            goal = "Probar",
            timeSignatures = listOf(TimeSignatureChange(0, 4, 4), TimeSignatureChange(1920, 3, 4)),
            keySignatures = listOf(KeySignatureChange(0, -2), KeySignatureChange(1920, 1)),
            tempos = listOf(TempoChange(0, 90.0), TempoChange(1920, 120.5)),
            notes = listOf(
                note(Hand.RIGHT, "Bb4", 0, Ticks.QUARTER, tied = true, finger = 2),
                note(Hand.RIGHT, "Bb4", 480, Ticks.HALF),
                note(Hand.LEFT, "C#3", 0, Ticks.WHOLE),
            ),
            rests = listOf(Rest(Hand.RIGHT, 1440, 480)),
        )
        val decoded = ScoreJson.decode(ScoreJson.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun pitchIsWrittenAsReadableText() {
        val json = ScoreJson.encode(score(listOf(note(Hand.RIGHT, "F#4", 0))))
        assertTrue(json.contains("\"pitch\": \"F#4\""))
        assertTrue(json.contains("\"hand\": \"right\""))
    }

    @Test
    fun jsonWithMissingOptionalFieldsUsesDefaults() {
        val s = ScoreJson.decode("""{ "title": "Mínima" }""")
        assertEquals("Mínima", s.title)
        assertEquals(1, s.timeSignatures.size)
        assertEquals(4, s.timeSignatures[0].numerator)
        assertNull(s.composer)
        assertTrue(s.notes.isEmpty())
    }

    @Test
    fun jsonWithATypoInAKeyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ScoreJson.decode("""{ "title": "X", "nots": [] }""")
        }
    }

    @Test
    fun jsonWithAnInvalidPitchIsRejected() {
        val text = """{ "title": "X", "notes": [ { "hand": "right", "pitch": "H9", "startTick": 0, "durationTicks": 480 } ] }"""
        assertThrows(IllegalArgumentException::class.java) { ScoreJson.decode(text) }
    }

    @Test
    fun jsonWithAnUnknownFormatVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ScoreJson.decode("""{ "formatVersion": 99, "title": "X" }""")
        }
    }

    // --- El ejemplo de la documentación ---

    @Test
    fun documentationExampleIsValidAndHasEveryElement() {
        // Los tests de Gradle corren dentro de la carpeta app/.
        val file = File("../docs/ejemplos/partitura-ejemplo.json")
        assertTrue("No encuentro ${file.absolutePath}", file.exists())
        val s = ScoreJson.decode(file.readText())

        assertEquals(emptyList<String>(), s.validate())
        assertEquals(2, s.measures().size)
        assertEquals(2, s.tempos.size)
        assertTrue(s.notes.any { it.hand == Hand.RIGHT } && s.notes.any { it.hand == Hand.LEFT })
        assertTrue(s.chords().any { it.isChord })
        assertTrue(s.rests.isNotEmpty())
        assertTrue(s.notes.any { it.tiedToNext })
        assertTrue(s.notes.any { it.finger != null })
        assertTrue(s.notes.any { it.pitch.alter != 0 })
    }
}
