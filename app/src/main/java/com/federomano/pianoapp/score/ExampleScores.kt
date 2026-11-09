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
        ExampleScore("anacrusa", "Ejercicio: anacrusa", "Empieza con un compás corto (tres corcheas): el compás 0. Después, los compases enteros desde el 1.") { pickup() },
        ExampleScore("adornos", "Ejercicio: notas de adorno", "Notas chiquitas que se tocan justo antes del tiempo: con alteración, con línea adicional, en acorde y en la mano izquierda.") { graceNotes() },
        ExampleScore("dinamicas", "Ejercicio: dinámicas", "Del mp al ff con un crescendo, y de vuelta al p con un diminuendo. Se escucha cómo cambia la fuerza.") { dynamics() },
        ExampleScore("articulaciones", "Ejercicio: staccato y acentos", "Notas cortitas y separadas (puntito) y notas más fuertes (>), con las dos manos.") { articulations() },
        ExampleScore("fusas", "Ejercicio: fusas", "Notas muy rápidas, de a ocho por negra, con tres barras. Para probar el dibujo y el sonido.") { thirtySeconds() },
        ExampleScore("ejercicio", "Ejercicio: todo lo que dibuja la app", "Alteraciones, silencios, acordes, ligaduras y cambios de armadura, compás y tempo.") { DemoScore.create() },
    )

    /**
     * Escritura corta de una mano: figuras separadas por espacios, cada una "nota/duración".
     * Duración: w redonda, h blanca, q negra, e corchea, s semicorchea, t fusa; un punto al final = puntillo.
     * Un acorde une las notas con "+" (`C3+G3/h`), un silencio es `r/q` y una "~" pegada
     * al final liga la figura con la siguiente (`C3/h~`).
     * Varias voces en una mano: `right(texto, voice = 2, from = tick)` escribe otra línea que suena a la vez; cada voz lleva su propio
     * ritmo y empieza en `from` (por defecto, el principio).
     * Un adorno es `D4/g`: una nota de adorno que se toca justo antes de la nota que escribas a continuación (no avanza el tiempo).
     * Staccato: un apóstrofo al final de la duración (`C4/q'`). Acento: un `>` (`C4/q>`). Los dos juntos: `C4/q'>`.
     * Un grupo irregular se marca con `*` después de la duración: `e*3` es una corchea de tresillo (3 en el tiempo de 2),
     * `q*4:3` es una negra de un grupo de 4 en el tiempo de 3. La duración es la de la figura escrita, ajustada al grupo.
     */
    private class Builder {
        val notes = mutableListOf<Note>()
        val rests = mutableListOf<Rest>()
        private val cursor = mutableMapOf<Pair<Hand, Int>, Int>()

        fun hand(hand: Hand, text: String, voice: Int = 1, from: Int? = null) {
            if (from != null) cursor[hand to voice] = from
            for (token in text.trim().split(Regex("\\s+"))) {
                var body = token
                val tied = body.endsWith("~")
                if (tied) body = body.dropLast(1)
                var staccato = false
                var accent = false
                while (body.endsWith("'") || body.endsWith(">")) {
                    if (body.endsWith("'")) staccato = true else accent = true
                    body = body.dropLast(1)
                }
                val (what, code) = body.split("/").also { require(it.size == 2) { "Figura inválida: \"$token\"" } }
                if (code == "g") {
                    // Adorno: va en el tick de la nota que sigue, sin avanzar el cursor.
                    val at = cursor.getOrPut(hand to voice) { 0 }
                    what.split("+").forEach { notes += Note(hand, Pitch.parse(it), at, Ticks.GRACE, grace = true, voice = voice) }
                    continue
                }
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
                    "t" -> Ticks.THIRTY_SECOND
                    else -> throw IllegalArgumentException("Duración inválida en \"$token\"")
                }
                val written = if (dotted) Ticks.dotted(base) else base
                val duration = tuplet?.let { written * it.normal / it.actual } ?: written
                val start = cursor.getOrPut(hand to voice) { 0 }
                if (what == "r") rests += Rest(hand, start, duration, tuplet, voice)
                else what.split("+").forEach { notes += Note(hand, Pitch.parse(it), start, duration, tiedToNext = tied, tuplet = tuplet, voice = voice, staccato = staccato, accent = accent) }
                cursor[hand to voice] = start + duration
            }
        }

        fun right(text: String, voice: Int = 1, from: Int? = null) = hand(Hand.RIGHT, text, voice, from)
        fun left(text: String, voice: Int = 1, from: Int? = null) = hand(Hand.LEFT, text, voice, from)
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

    private fun thirtySeconds(): Score {
        val b = Builder()
        // Compás 1: una corrida de ocho fusas, una negra, una figura mezclada (corchea, semicorchea y dos fusas) y otra negra.
        b.right("C5/t D5/t E5/t F5/t G5/t A5/t B5/t C6/t  C6/q  B5/e A5/s G5/t F5/t  E5/q")
        // Compás 2: una negra, un silencio de fusa antes de la corrida que baja y una blanca.
        // Compás 3: en cada tiempo, una fusa, un silencio de semicorchea con puntillo y una corchea.
        b.right("G5/q  r/t F5/t E5/t D5/t C5/e  C5/h")
        b.right("E5/t r/s. E5/e  G5/t r/s. G5/e  F5/t r/s. F5/e  D5/t r/s. D5/e")
        b.left("C3/w  C3/q G2/q C3/h  C3/h G2/h")
        return Score(
            title = "Ejercicio: fusas",
            level = "intermedio",
            goal = "Leer y tocar figuras muy rápidas (fusas): empezá lento, a la mitad de la velocidad o menos.",
            tempos = listOf(TempoChange(0, 60.0)),
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
        // Compás 5: dos voces que chocan (La y Sol a la vez) y silencios en cada voz.
        b.right("A4/h r/h", voice = 1, from = 4 * bar)
        b.right("G4/h G4/q r/q", voice = 2, from = 4 * bar)
        // Mano izquierda: acordes que se repiten (voz 1) sobre un bajo que sigue sonando (voz 2).
        b.left("C3/w  G2/w  E3+G3/q E3+G3/q E3+G3/q E3+G3/q  C3+G3/h r/h  C3+G3/w")
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

    private fun dynamics(): Score {
        val b = Builder()
        val bar = Ticks.WHOLE
        b.right(
            "C4/q E4/q G4/q E4/q  D4/q F4/q A4/q F4/q  E4/q G4/q C5/q G4/q  F4/q A4/q C5/q A4/q  " +
                "G4/q B4/q D5/q B4/q  E5/q C5/q G4/q E4/q  D4/q G4/q B4/q G4/q  C4/w",
        )
        b.left("C3/w  F2/w  C3/w  F2/w  G2/w  C3/w  G2/w  C3/w")
        return Score(
            title = "Ejercicio: dinámicas",
            level = "principiante",
            goal = "Tocar con la fuerza que pide cada letra: mp al principio, crescendo hasta f y ff, y diminuendo hasta p.",
            tempos = listOf(TempoChange(0, 80.0)),
            dynamics = listOf(
                DynamicMark(0, DynamicLevel.MP),
                DynamicMark(3 * bar, DynamicLevel.F),
                DynamicMark(4 * bar, DynamicLevel.FF),
                DynamicMark(7 * bar, DynamicLevel.P),
            ),
            hairpins = listOf(
                Hairpin(bar, 3 * bar, HairpinKind.CRESCENDO),
                Hairpin(5 * bar, 7 * bar, HairpinKind.DIMINUENDO),
            ),
            notes = b.notes,
            rests = b.rests,
        )
    }

    private fun articulations(): Score {
        val b = Builder()
        b.right(
            "C5/e' G4/e' E5/e' G4/e'  C5/e' G4/e' E5/e' G4/e'  " +
                "F5/q> E5/q D5/q C5/q  " +
                "G4/q'> B4/q' D5/q'> B4/q'  " +
                "C5/h r/h",
        )
        b.left("C3+G3/q' r/q C3+G3/q' r/q  F2+C3/h> F2+C3/h  G2+D3/q' G2+D3/q' G2+D3/q' G2+D3/q'  C3+G3/w")
        return Score(
            title = "Ejercicio: staccato y acentos",
            level = "intermedio",
            goal = "Soltar rápido las notas con puntito (staccato) y tocar más fuerte las que llevan «>» (acento), sin correr el pulso.",
            tempos = listOf(TempoChange(0, 80.0)),
            notes = b.notes,
            rests = b.rests,
        )
    }

    private fun graceNotes(): Score {
        val b = Builder()
        // Cada adorno va en el tick de la nota que le sigue: suena un instante antes y no corre el ritmo.
        b.right("D4/g E4/q  C4/g E4/q  F#4/g G4/h  G4+B4/g C5/h  E5/g D5/q  C5/q  C5/w")
        b.left("B2/g C3/h  C3/h  Bb2/g A2/h  F2/h  C3/w")
        return Score(
            title = "Ejercicio: notas de adorno",
            level = "intermedio",
            goal = "Tocar la notita de adorno rápido y justo antes del tiempo, sin correr el pulso de la nota que sigue.",
            tempos = listOf(TempoChange(0, 70.0)),
            notes = b.notes,
            rests = b.rests,
        )
    }

    private fun pickup(): Score {
        val b = Builder()
        // Compás 0 (anacrusa): tres corcheas, el final de un compás de 4/4. Siguen tres compases enteros.
        b.right("D4/e G4/e A4/e  B4/q D5/q G4/q B4/q  A4/q F#4/q D4/h  G4/w")
        b.left("r/q.  G2+D3/h G2+D3/h  D3+A3/w  G2+D3/w")
        return Score(
            title = "Ejercicio: anacrusa",
            level = "principiante",
            goal = "Arrancar en el aire: tres corcheas antes del primer compás (el 0) y caer en el tiempo fuerte del compás 1.",
            keySignatures = listOf(KeySignatureChange(0, 1)),
            tempos = listOf(TempoChange(0, 80.0)),
            pickupTicks = 3 * Ticks.EIGHTH,
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
