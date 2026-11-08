package com.federomano.pianoapp.library

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Pitch
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.ScoreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScoreLibraryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scale = File("../docs/ejemplos/escala-do-mayor.json").readText()
    private val himno = File("../docs/ejemplos/himno-a-la-alegria.json").readText()
    private fun library() = ScoreLibrary(File(tmp.root, "library"))

    @Test
    fun importingAValidFileSavesItAndListsIt() {
        val lib = library()
        val result = lib.import(scale)
        assertTrue(result is ImportResult.Added)
        val entries = lib.list()
        assertEquals(listOf("Escala de Do mayor"), entries.map { it.score.title })
        assertEquals("escala-de-do-mayor", entries.single().id)
    }

    @Test
    fun theDurationIsComputedFromTheTempo() {
        val entry = (library().import(scale) as ImportResult.Added).entry
        // 4 compases de 4/4 a 80 negras por minuto: 16 negras * 0,75 s.
        assertEquals(12f, entry.seconds, 0.01f)
    }

    @Test
    fun theLibraryPersistsInANewInstance() {
        library().import(scale)
        library().import(himno)
        assertEquals(setOf("Escala de Do mayor", "Himno a la alegría"), library().list().map { it.score.title }.toSet())
    }

    @Test
    fun theSameScoreIsNotAddedTwice() {
        val lib = library()
        lib.import(scale)
        assertTrue(lib.import(scale) is ImportResult.AlreadyThere)
        assertEquals(1, lib.list().size)
    }

    @Test
    fun twoDifferentScoresWithTheSameTitleGetDifferentFiles() {
        val lib = library()
        lib.import(scale)
        val other = ScoreJson.decode(scale).copy(tempos = listOf(com.federomano.pianoapp.score.TempoChange(0, 60.0)))
        val result = lib.import(ScoreJson.encode(other))
        assertTrue(result is ImportResult.Added)
        assertEquals(setOf("escala-de-do-mayor", "escala-de-do-mayor-2"), lib.list().map { it.id }.toSet())
    }

    @Test
    fun textThatIsNotAScoreFailsWithAClearMessage() {
        val result = library().import("esto no es json")
        assertTrue(result is ImportResult.Failed)
        assertTrue((result as ImportResult.Failed).problems.single().startsWith("No tiene el formato de una partitura"))
    }

    @Test
    fun aMisspelledKeyIsRejected() {
        val result = library().import(scale.replace("\"title\"", "\"titulo\""))
        assertTrue(result is ImportResult.Failed)
    }

    @Test
    fun aScoreWithMusicalErrorsReportsThemInSpanishAndIsNotSaved() {
        val lib = library()
        val crossing = Score(title = "Cruza", notes = listOf(Note(Hand.RIGHT, Pitch.parse("C4"), 1440, 960)))
        val result = lib.import(ScoreJson.encode(crossing))
        assertTrue(result is ImportResult.Failed)
        assertTrue((result as ImportResult.Failed).problems.any { "cruza la barra de compás" in it })
        assertTrue(lib.list().isEmpty())
    }

    @Test
    fun aFileWithAByteOrderMarkIsAccepted() {
        assertTrue(library().import("﻿" + scale) is ImportResult.Added)
    }

    @Test
    fun aHugeFileIsRejected() {
        val result = library().import(" ".repeat(ScoreLibrary.MAX_BYTES + 1))
        assertTrue(result is ImportResult.Failed)
    }

    @Test
    fun renameChangesTheTitleAndKeepsEverythingElse() {
        val lib = library()
        val entry = (lib.import(scale) as ImportResult.Added).entry
        assertTrue(lib.rename(entry.id, "  Mi escala  "))
        val renamed = lib.list().single()
        assertEquals("Mi escala", renamed.score.title)
        assertEquals(entry.score.notes, renamed.score.notes)
        assertEquals(entry.id, renamed.id)
        assertFalse(lib.rename(entry.id, "   "))
        assertFalse(lib.rename("no-existe", "X"))
    }

    @Test
    fun deleteRemovesIt() {
        val lib = library()
        val entry = (lib.import(scale) as ImportResult.Added).entry
        lib.delete(entry.id)
        assertTrue(lib.list().isEmpty())
    }

    @Test
    fun aBrokenFileInTheFolderIsIgnored() {
        val lib = library()
        lib.import(scale)
        File(tmp.root, "library/roto.json").writeText("{ no es una partitura")
        assertEquals(1, lib.list().size)
    }

    @Test
    fun theTestFileWithSeveralErrorsReportsAllOfThem() {
        val text = """
            { "formatVersion": 1, "title": "Ejemplo con errores", "level": "principiante",
              "notes": [
                { "hand": "right", "pitch": "C4", "startTick": 1440, "durationTicks": 960 },
                { "hand": "right", "pitch": "D4", "startTick": 0, "durationTicks": 480, "tiedToNext": true },
                { "hand": "left", "pitch": "C0", "startTick": 0, "durationTicks": 480 }
              ] }
        """
        val problems = (library().import(text) as ImportResult.Failed).problems
        assertEquals(3, problems.size)
        assertTrue(problems.any { "cruza la barra de compás" in it })
        assertTrue(problems.any { "está ligada" in it })
        assertTrue(problems.any { "fuera del rango del piano" in it })
    }
}
