package com.federomano.pianoapp.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportReportTest {
    @Test
    fun durationsShowMinutesAndSeconds() {
        assertEquals("0:12", formatDuration(12f))
        assertEquals("1:05", formatDuration(64.6f))
        assertEquals("10:00", formatDuration(600f))
        assertEquals("0:00", formatDuration(-3f))
    }

    @Test
    fun subtitleSkipsWhatTheFileDoesNotHave() {
        assertEquals("Beethoven · principiante · 0:40", subtitleOf("Beethoven", "principiante", 40f))
        assertEquals("0:40", subtitleOf(null, null, 40f))
        assertEquals("avanzado · 1:00", subtitleOf(null, "avanzado", 60f))
    }

    @Test
    fun aFailedLineListsTheProblems() {
        val line = ImportLine("x.json", ImportResult.Failed(listOf("uno", "dos")))
        assertFalse(line.ok)
        assertEquals(listOf("uno", "dos"), line.problems)
        assertTrue(line.summary.startsWith("No se pudo importar"))
    }
}
