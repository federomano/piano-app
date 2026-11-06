package com.federomano.pianoapp.score

/**
 * Partitura de demostración, provisoria: sirve para ver el dibujo mientras no se puedan importar
 * archivos ni haya partituras de ejemplo (PIA-15). Son 12 compases vacíos con cambios de armadura
 * y de firma de compás, para comprobar que se dibujan bien.
 */
object DemoScore {
    private data class Section(val measures: Int, val numerator: Int, val denominator: Int, val fifths: Int)

    fun create(): Score {
        val sections = listOf(
            Section(3, 4, 4, 0),
            Section(3, 3, 4, 2),
            Section(3, 6, 8, -3),
            Section(3, 4, 4, 0),
        )
        val timeSignatures = mutableListOf<TimeSignatureChange>()
        val keySignatures = mutableListOf<KeySignatureChange>()
        val rests = mutableListOf<Rest>()
        var tick = 0
        for (section in sections) {
            val signature = TimeSignatureChange(tick, section.numerator, section.denominator)
            timeSignatures += signature
            keySignatures += KeySignatureChange(tick, section.fifths)
            repeat(section.measures) {
                for (hand in Hand.entries) rests += Rest(hand, tick, signature.measureTicks)
                tick += signature.measureTicks
            }
        }
        return Score(
            title = "Demostración del dibujo",
            timeSignatures = timeSignatures,
            keySignatures = keySignatures,
            rests = rests,
        )
    }
}
