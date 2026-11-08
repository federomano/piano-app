package com.federomano.pianoapp.score

/** Una partitura de ejemplo que trae la app, para el menú de inicio. */
class ExampleScore(val id: String, val title: String, val description: String, val create: () -> Score)

/** Partituras de práctica que vienen en la app desde el primer día (PIA-15). */
object ExampleScores {
    val all: List<ExampleScore> = listOf(
        ExampleScore("escala", "Escala de Do mayor", "Dos manos, solo negras. Subir y bajar un octavo. Para empezar a leer.") { cMajorScale() },
        ExampleScore("himno", "Himno a la alegría", "Beethoven. Melodía en la derecha y acordes sencillos en la izquierda. 16 compases.") { odeToJoy() },
        ExampleScore("voces", "Ejercicio: dos voces en una mano", "Una melodía larga arriba y otra línea que corre abajo, con el bajo sostenido bajo acordes.") { voices() },
        ExampleScore("tresillos", "Ejercicio: tresillos", "Tresillos de corchea y de negra, swing y un grupo de cuatro en el tiempo de tres.") { triplets() },
        ExampleScore("ejercicio", "Ejercicio: todo lo que dibuja la app", "Alteraciones, silencios, acordes, ligaduras y cambios de armadura, compás y tempo.") { DemoScore.create() },
    )

    /**
     * Escritura corta de una mano: figuras separadas por espacios, cada una "nota/duración".
     * Duración: w redonda, h blanca, q negra, e corchea, s semicorchea; un punto al final = puntillo.
     * Un acorde une las notas con "+" (`C3+G3/h`), un silencio es `r/q` y una "~" pegada
     * al final liga la figura con la siguiente (`C3/h~`).
     * Varias voces en una mano: `right(texto, voice = 2, from = tick)` escribe otra línea que suena a la vez; cada voz lleva su propio
     * ritmo y empieza en `from` (por defecto, el principio).
     * Un grupo irregular se marca con `*` después de la duración: `e*3` es una corchea de tresillo (3 en el tiempo de 2),
     * `q*4:3` es una negra de un grupo de 4 en el tiempo de 3. La duración es la de la figura escrita, ajustada al grupo.
     */
    private class Builder {
        val notes = mutableListOf<Note>()
        val rests = mutableListOf<Rest>()
        private val cursor = mutableMapOf<Pair<Hand, Int>, Int>()

        fun hand(hand: Hand, text: String, voice: Int = 1, from: Int = 0) {
            for (token in text.trim().split(Regex("\\s+"))) {
                var body = token
                val tied = body.endsWith("~")
                if (tied) body = body.dropLast(1)
                val (what, code) = body.split("/").also { require(it.size == 2) { "Figura inválida: \"$token\"" } }
                val figureCode = code.substringBefore("*")
                val tuplet = code.substringAfter("*", "").takeIf { it.isNotEmpty() }
                    ?.let { Tuplet.parse(if (":" in it) it else "$it:${if (it == "3") 2 else if (it == "5") 4 else 3}") }
                val dotted = figureCode.endsWith(".")
                val base = when (figureCode.trimEnd('.')) {
                    "w" -> Ticks.WHOLE
                    "h" -> Ticks.HALF
                    "q" -> Ticks.QUARTER
                    "e" -> Ticks.EIGHTH
                    "s" -> Ticks.SIXTEENTH
                    else -> throw IllegalArgumentException("Duración inválida en \"$token\"")
                }
                val written = if (dotted) Ticks.dotted(base) else base
                val duration = tuplet?.let { written * it.normal / it.actual } ?: written
                val start = cursor.getOrPut(hand to voice) { from }
                if (what == "r") rests += Rest(hand, start, duration, tuplet, voice)
                else what.split("+").forEach { notes += Note(hand, Pitch.parse(it), start, duration, tiedToNext = tied, tuplet = tuplet, voice = voice) }
                cursor[hand to voice] = start + duration
            }
        }

        fun right(text: String, voice: Int = 1, from: Int = 0) = hand(Hand.RIGHT, text, voice, from)
        fun left(text: String, voice: Int = 1, from: Int = 0) = hand(Hand.LEFT, text, voice, from)
    }

    private fun cMajorScale(): Score {
        val b = Builder()
        b.right("C4/q D4/q E4/q F4/q  G4/q A4/q B4/q C5/q  B4/q A4/q G4/q F4/q  E4/q D4/q C4/h")
        b.left("C3/q D3/q E3/q F3/q  G3/q A3/q B3/q C4/q  B3/q A3/q G3/q F3/q  E3/q D3/q C3/h")
        return Score(
            title = "Escala de Do mayor",
            level = "principiante",
            goal = "Leer las notas de Do a Do con las dos manos, subiendo y bajando.",
            tempos = listOf(TempoChange(0, 80.0)),
            notes = b.notes,
            rests = b.rests,
        )
    }

    private fun voices(): Score {
        val b = Builder()
        val bar = Ticks.WHOLE
        // Mano derecha: la voz 1 (arriba) es la melodía con notas largas; la voz 2 (abajo) corre con otro ritmo.
        b.right("E5/h D5/h  C5/w  G4/w  C5/w")
        b.right("C4/q E4/q G4/q E4/q  C4/e E4/e G4/e E4/e C4/e E4/e G4/e E4/e", voice = 2)
        b.right("C4/h E4/h", voice = 2, from = 3 * bar)
        // Mano izquierda: acordes que se repiten (voz 1) sobre un bajo que sigue sonando (voz 2).
        b.left("C3/w  G2/w  E3+G3/q E3+G3/q E3+G3/q E3+G3/q  C3+G3/h r/h")
        b.left("C2/w", voice = 2, from = 2 * bar)
        b.left("C2/w", voice = 2, from = 3 * bar)
        return Score(
            title = "Ejercicio: dos voces en una mano",
            level = "intermedio",
            goal = "Tocar dos líneas a la vez con la misma mano: una larga arriba y otra que corre abajo, o un bajo sostenido bajo acordes.",
            tempos = listOf(TempoChange(0, 80.0)),
            notes = b.notes,
            rests = b.rests,
        )
    }

    private fun triplets(): Score {
        val b = Builder()
        b.right(
            // 1: tres corcheas de tresillo por pulso. 2: swing (negra + corchea de tresillo). 3: tres negras de tresillo en dos pulsos.
            "C4/e*3 E4/e*3 G4/e*3  C5/e*3 G4/e*3 E4/e*3  C4/e*3 E4/e*3 G4/e*3  C5/e*3 G4/e*3 E4/e*3  " +
                "E4/q*3 G4/e*3  E4/q*3 G4/e*3  E4/q*3 G4/e*3  E4/q*3 G4/e*3  " +
                "C5/q*3 B4/q*3 A4/q*3  G4/h  " +
                // 4 (en 3/4): cuatro negras en el tiempo de tres. 5: silencio de tresillo.
                "E4/q*4:3 G4/q*4:3 C5/q*4:3 G4/q*4:3  " +
                "r/e*3 C4/e*3 E4/e*3  G4/h",
        )
        b.left("C3+G3/w  G2+D3/h G2+D3/h  C3+G3/w  C3+G3/h.  C3/h.")
        return Score(
            title = "Ejercicio: tresillos",
            level = "intermedio",
            goal = "Sentir el tresillo: tres notas en el tiempo de dos, el swing y un grupo de cuatro en el tiempo de tres.",
            timeSignatures = listOf(TimeSignatureChange(0, 4, 4), TimeSignatureChange(3 * 1920, 3, 4)),
            tempos = listOf(TempoChange(0, 90.0)),
            notes = b.notes,
            rests = b.rests,
        )
    }

    private fun odeToJoy(): Score {
        val b = Builder()
        // Frase que se repite (compases 5-8 y 13-16): termina en Do.
        val phraseRight = "E4/q E4/q F4/q G4/q  G4/q F4/q E4/q D4/q  C4/q C4/q D4/q E4/q  D4/q. C4/e C4/h"
        val phraseLeft = "C3+G3/w  G2+D3/w  C3+G3/w  G2+D3/h C3+G3/h"
        b.right(
            "E4/q E4/q F4/q G4/q  G4/q F4/q E4/q D4/q  C4/q C4/q D4/q E4/q  E4/q. D4/e D4/h  " +
                phraseRight + "  " +
                "D4/q D4/q E4/q C4/q  D4/q E4/e F4/e E4/q C4/q  D4/q E4/e F4/e E4/q D4/q  C4/q D4/q G3/h  " +
                phraseRight,
        )
        b.left(
            "C3+G3/w  G2+D3/w  C3+G3/w  C3+G3/h G2+D3/h  " +
                phraseLeft + "  " +
                "G2+D3/h C3+G3/h  G2+D3/h C3+G3/h  G2+D3/w  C3+G3/h G2+D3/h  " +
                phraseLeft,
        )
        return Score(
            title = "Himno a la alegría",
            composer = "Ludwig van Beethoven",
            level = "principiante",
            goal = "Tocar una melodía conocida con acompañamiento sencillo de acordes.",
            tempos = listOf(TempoChange(0, 100.0)),
            notes = b.notes,
            rests = b.rests,
        )
    }
}
