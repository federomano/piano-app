package com.federomano.pianoapp.score

/**
 * Partitura de demostración, provisoria: sirve para ver el dibujo mientras no se puedan importar
 * archivos ni haya partituras de ejemplo (PIA-15). Son 12 compases con cambios de armadura y de
 * compás, y con todas las figuras, silencios, ligaduras, alteraciones y casos de plicas y barras que
 * tiene que dibujar la app.
 */
object DemoScore {
    private val notes = mutableListOf<Note>()
    private val rests = mutableListOf<Rest>()

    private fun n(hand: Hand, pitch: String, start: Int, duration: Int, tied: Boolean = false) {
        notes += Note(hand, Pitch.parse(pitch), start, duration, tiedToNext = tied)
    }

    private fun right(pitches: String, start: Int, duration: Int, tied: Boolean = false) =
        pitches.split(" ").forEach { n(Hand.RIGHT, it, start, duration, tied) }

    private fun left(pitches: String, start: Int, duration: Int, tied: Boolean = false) =
        pitches.split(" ").forEach { n(Hand.LEFT, it, start, duration, tied) }

    private fun rest(hand: Hand, start: Int, duration: Int) {
        rests += Rest(hand, start, duration)
    }

    fun create(): Score {
        notes.clear()
        rests.clear()
        val q = Ticks.QUARTER
        val e = Ticks.EIGHTH
        val s = Ticks.SIXTEENTH
        val h = Ticks.HALF
        val w = Ticks.WHOLE

        // Compás 1 (4/4, Do mayor): negras con línea adicional; la redonda grave se liga con el compás 2.
        right("C4", 0, q); right("D4", q, q); right("E4", 2 * q, q); right("F4", 3 * q, q)
        left("C3", 0, w, tied = true)
        // Compás 2: blancas; la plica depende de la posición (Si4 está en la línea central). Silencio de blanca.
        right("G4", 1920, h); right("B4", 1920 + h, h)
        left("C3", 1920, h); rest(Hand.LEFT, 1920 + h, h)
        // Compás 3: corcheas y semicorcheas con barras por tiempo, puntillo, y silencios cortos.
        right("C5", 3840, e); right("D5", 3840 + e, e)
        right("E5", 4320, s); right("F5", 4320 + s, s); right("G5", 4320 + 2 * s, s); right("F5", 4320 + 3 * s, s)
        right("G5", 4800, Ticks.dotted(e)); right("A5", 4800 + Ticks.dotted(e), s)
        right("F5", 5280, q)
        left("E2", 3840, q); rest(Hand.LEFT, 3840 + q, q)
        left("G2", 4800, e); rest(Hand.LEFT, 4800 + e, e)
        left("G2", 5280, e); left("C2", 5280 + e, s); rest(Hand.LEFT, 5280 + e + s, s)

        // Compás 4 (3/4, Re mayor: Fa# y Do# en la armadura): el Fa natural pide becuadro.
        right("F#4", 5760, q); right("F4", 5760 + q, q); right("F4", 5760 + 2 * q, q)
        left("D3", 5760, Ticks.dotted(h), tied = true); left("F#3 A3", 5760, Ticks.dotted(h))
        // Compás 5: Do# (armadura), Do natural (becuadro), Do# otra vez (sostenido). La ligadura viene del compás 4.
        right("C#5", 7200, q); right("C5", 7200 + q, q); right("C#5", 7200 + 2 * q, q)
        left("D3", 7200, q); left("A2", 7200 + q, q); rest(Hand.LEFT, 7200 + 2 * q, q)
        // Compás 6: acorde con segunda (Mi4 y Fa#4 juntos).
        right("E4 F#4", 8640, h); right("G4", 8640 + h, q)
        left("A2", 8640, Ticks.dotted(h))

        // Compás 7 (6/8, Mi bemol mayor: Si♭, Mi♭ y La♭): negras con puntillo, un Si natural y un silencio con puntillo.
        right("Eb5", 10080, Ticks.dotted(q)); right("B4", 10080 + Ticks.dotted(q), Ticks.dotted(q))
        rest(Hand.LEFT, 10080, Ticks.dotted(q)); left("Eb3", 10080 + Ticks.dotted(q), Ticks.dotted(q))
        // Compás 8: seis corcheas en dos grupos de tres; la última se liga con la anterior.
        listOf("Eb4", "F4", "G4", "Ab4").forEachIndexed { i, p -> right(p, 11520 + i * e, e) }
        right("Bb4", 11520 + 4 * e, e, tied = true); right("Bb4", 11520 + 5 * e, e)
        left("Ab2", 11520, Ticks.dotted(h))
        // Compás 9: acorde de tres notas.
        right("Eb4 G4 Bb4", 12960, Ticks.dotted(h))
        left("Bb2", 12960, q); left("F2", 12960 + q, q); left("Bb2", 12960 + 2 * q, q)

        // Compás 10 (4/4, Do mayor): doble bemol y doble sostenido; silencio de blanca.
        right("Bbb4", 14400, q); right("F##4", 14400 + q, q); right("A4", 14400 + 2 * q, h)
        rest(Hand.LEFT, 14400, h); left("C3", 14400 + h, h)
        // Compás 11: ocho corcheas bajando, en cuatro grupos de dos, con plicas para los dos lados.
        listOf("C5", "B4", "A4", "G4", "F4", "E4", "D4", "C4").forEachIndexed { i, p -> right(p, 16320 + i * e, e) }
        left("C3", 16320, q); left("G2", 16320 + q, q); left("C3", 16320 + 2 * q, q); left("E3", 16320 + 3 * q, q)
        // Compás 12: redonda con dos líneas adicionales y silencio de compás completo.
        right("C6", 18240, w); rest(Hand.LEFT, 18240, w)

        val sections = listOf(
            Triple(3, TimeSignatureChange(0, 4, 4), 0),
            Triple(3, TimeSignatureChange(5760, 3, 4), 2),
            Triple(3, TimeSignatureChange(10080, 6, 8), -3),
            Triple(3, TimeSignatureChange(14400, 4, 4), 0),
        )
        return Score(
            title = "Demostración del dibujo",
            timeSignatures = sections.map { it.second },
            keySignatures = sections.map { KeySignatureChange(it.second.startTick, it.third) },
            notes = notes.toList(),
            rests = rests.toList(),
        )
    }
}
