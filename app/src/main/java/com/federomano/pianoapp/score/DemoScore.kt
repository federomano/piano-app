package com.federomano.pianoapp.score

/**
 * Partitura de demostración, provisoria: sirve para ver el dibujo mientras no se puedan importar
 * archivos ni haya partituras de ejemplo (PIA-15). Son 12 compases con cambios de armadura y de
 * compás, y con todas las figuras, alteraciones y casos de plicas que tiene que dibujar la app.
 */
object DemoScore {
    private val notes = mutableListOf<Note>()

    private fun n(hand: Hand, pitch: String, start: Int, duration: Int) {
        notes += Note(hand, Pitch.parse(pitch), start, duration)
    }

    private fun right(pitches: String, start: Int, duration: Int) = pitches.split(" ").forEach { n(Hand.RIGHT, it, start, duration) }
    private fun left(pitches: String, start: Int, duration: Int) = pitches.split(" ").forEach { n(Hand.LEFT, it, start, duration) }

    fun create(): Score {
        notes.clear()
        val q = Ticks.QUARTER
        val e = Ticks.EIGHTH
        val s = Ticks.SIXTEENTH

        // Compás 1 (4/4, Do mayor): negras con línea adicional, y redonda.
        right("C4", 0, q); right("D4", q, q); right("E4", 2 * q, q); right("F4", 3 * q, q)
        left("C3", 0, Ticks.WHOLE)
        // Compás 2: blancas; la plica depende de la posición (Si4 está en la línea central).
        right("G4", 1920, Ticks.HALF); right("B4", 1920 + Ticks.HALF, Ticks.HALF)
        left("C3", 1920, Ticks.HALF); left("G2", 1920 + Ticks.HALF, Ticks.HALF)
        // Compás 3: corcheas, semicorcheas, puntillo y líneas adicionales arriba y abajo.
        right("C5", 3840, e); right("D5", 3840 + e, e); right("E5", 3840 + 2 * e, s); right("F5", 3840 + 2 * e + s, s)
        right("G5", 3840 + 3 * e, q); right("A5", 3840 + 3 * e + q, Ticks.dotted(q))
        left("E2", 3840, q); left("C2", 3840 + q, q); left("G2", 3840 + 2 * q, Ticks.HALF)

        // Compás 4 (3/4, Re mayor: Fa# y Do# en la armadura): el Fa natural pide becuadro.
        right("F#4", 5760, q); right("F4", 5760 + q, q); right("F4", 5760 + 2 * q, q)
        left("D3 F#3 A3", 5760, Ticks.dotted(Ticks.HALF))
        // Compás 5: Do# (armadura), Do natural (becuadro), Do# otra vez (sostenido).
        right("C#5", 7200, q); right("C5", 7200 + q, q); right("C#5", 7200 + 2 * q, q)
        left("D3", 7200, q); left("A2", 7200 + q, q); left("D3", 7200 + 2 * q, q)
        // Compás 6: acorde con segunda (Mi4 y Fa#4 juntos).
        right("E4 F#4", 8640, Ticks.HALF); right("G4", 8640 + Ticks.HALF, q)
        left("A2", 8640, Ticks.dotted(Ticks.HALF))

        // Compás 7 (6/8, Mi bemol mayor: Si♭, Mi♭ y La♭): negras con puntillo y un Si natural.
        right("Eb5", 10080, Ticks.dotted(q)); right("B4", 10080 + Ticks.dotted(q), Ticks.dotted(q))
        left("Eb3", 10080, Ticks.dotted(Ticks.HALF))
        // Compás 8: seis corcheas.
        listOf("Eb4", "F4", "G4", "Ab4", "Bb4", "C5").forEachIndexed { i, p -> right(p, 11520 + i * e, e) }
        left("Ab2", 11520, Ticks.dotted(Ticks.HALF))
        // Compás 9: La natural entre bemoles, y acorde de tres notas.
        right("Eb4 G4 Bb4", 12960, Ticks.dotted(Ticks.HALF))
        left("Bb2", 12960, q); left("F2", 12960 + q, q); left("Bb2", 12960 + 2 * q, q)

        // Compás 10 (4/4, Do mayor): doble bemol y doble sostenido.
        right("Bbb4", 14400, q); right("F##4", 14400 + q, q); right("A4", 14400 + 2 * q, Ticks.HALF)
        left("C3", 14400, Ticks.WHOLE)
        // Compás 11: ocho corcheas bajando, con plicas para los dos lados.
        listOf("C5", "B4", "A4", "G4", "F4", "E4", "D4", "C4").forEachIndexed { i, p -> right(p, 16320 + i * e, e) }
        left("C3", 16320, q); left("G2", 16320 + q, q); left("C3", 16320 + 2 * q, q); left("E3", 16320 + 3 * q, q)
        // Compás 12: redondas con dos líneas adicionales.
        right("C6", 18240, Ticks.WHOLE); left("C2", 18240, Ticks.WHOLE)

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
        )
    }
}
