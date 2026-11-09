package com.federomano.pianoapp.score.layout

/*
 * Instrucciones de dibujo (PIA-10).
 *
 * El motor de layout no dibuja nada: calcula una lista de instrucciones simples (rectángulos,
 * símbolos musicales y textos) en "espacios de pentagrama". Un espacio de pentagrama (sp) es la
 * distancia entre dos líneas del pentagrama. Así el cálculo se puede probar sin pantalla y el
 * dibujo en Android es una capa finita.
 *
 * Coordenadas: x crece hacia la derecha; y crece hacia abajo; y = 0 es la línea superior del
 * pentagrama de la mano derecha.
 */

sealed interface DrawOp

/** Rectángulo relleno. Las líneas del pentagrama y las barras de compás son rectángulos finos. */
data class RectOp(val x: Float, val y: Float, val width: Float, val height: Float) : DrawOp

/**
 * Símbolo de la fuente Bravura (estándar SMuFL). [x] es el borde izquierdo del símbolo e [y] su
 * línea de base. [scale] agranda el símbolo en forma pareja (se usa para la llave).
 */
data class GlyphOp(val codepoint: Int, val x: Float, val y: Float, val scale: Float = 1f) : DrawOp

/** Texto común (números de compás). [x] es el borde izquierdo, [y] la línea de base, [size] el alto de letra. */
data class TextOp(val text: String, val x: Float, val y: Float, val size: Float) : DrawOp

/**
 * Polígono relleno, con puntos [x0, y0, x1, y1, ...]. Se usa para las barras (vigas) inclinadas y las
 * ligaduras, que no se pueden hacer con rectángulos.
 */
data class PolygonOp(val points: List<Float>) : DrawOp {
    val minX: Float get() = points.filterIndexed { i, _ -> i % 2 == 0 }.min()
}

/** Códigos de los símbolos de Bravura que usa el dibujo (https://w3c.github.io/smufl/). */
object Smufl {
    const val BRACE = 0xE000
    const val G_CLEF = 0xE050
    const val F_CLEF = 0xE062
    const val FLAT = 0xE260
    const val NATURAL = 0xE261
    const val SHARP = 0xE262

    /** Ancho de cada símbolo, en espacios de pentagrama, medido en la fuente. */
    const val FLAT_ADVANCE = 0.904f
    const val NATURAL_ADVANCE = 0.672f
    const val SHARP_ADVANCE = 0.996f
    const val CLEF_ADVANCE = 2.74f

    /** Signos de octava: "8va" y "8vb" (la base está en la línea de base; miden 1,85 de alto). */
    const val OTTAVA_ALTA = 0xE511
    const val OTTAVA_BASSA = 0xE512
    const val OTTAVA_ALTA_WIDTH = 3.54f
    const val OTTAVA_BASSA_WIDTH = 3.356f
    const val OTTAVA_HEIGHT = 1.85f

    /** Medidas de la llave a tamaño normal: 4 espacios de alto por 0,277 de ancho. */
    const val BRACE_HEIGHT = 4.002f
    const val BRACE_WIDTH = 0.277f

    // Notas (medidas en espacios de pentagrama; la cabeza se centra verticalmente sobre su línea de base).
    const val NOTEHEAD_WHOLE = 0xE0A2
    const val NOTEHEAD_HALF = 0xE0A3
    const val NOTEHEAD_BLACK = 0xE0A4
    const val NOTEHEAD_WHOLE_WIDTH = 1.688f
    const val NOTEHEAD_WIDTH = 1.18f
    const val FLAG_8TH_UP = 0xE240
    const val FLAG_8TH_DOWN = 0xE241
    const val FLAG_16TH_UP = 0xE242
    const val FLAG_16TH_DOWN = 0xE243
    const val FLAG_32ND_UP = 0xE244
    const val FLAG_32ND_DOWN = 0xE245
    const val AUGMENTATION_DOT = 0xE1E7
    const val STACCATO_ABOVE = 0xE4A2
    const val ACCENT_ABOVE = 0xE4A0
    const val ACCENT_BELOW = 0xE4A1
    const val DOUBLE_SHARP = 0xE263
    const val DOUBLE_FLAT = 0xE264
    const val DOUBLE_SHARP_ADVANCE = 1.0f
    const val DOUBLE_FLAT_ADVANCE = 1.652f

    // Silencios. La redonda cuelga de su línea de base; la blanca se apoya en ella; los demás se centran.
    const val REST_WHOLE = 0xE4E3
    const val REST_HALF = 0xE4E4
    const val REST_QUARTER = 0xE4E5
    const val REST_8TH = 0xE4E6
    const val REST_16TH = 0xE4E7
    const val REST_32ND = 0xE4E8
    const val REST_WHOLE_WIDTH = 1.128f
    const val REST_HALF_WIDTH = 1.128f
    const val REST_QUARTER_WIDTH = 1.08f
    const val REST_8TH_WIDTH = 0.988f
    const val REST_16TH_WIDTH = 1.28f
    const val REST_32ND_WIDTH = 1.452f

    /** Números de los grupos irregulares (tresillos, etc.): 0xE880 + dígito; los dos puntos son 0xE88A. */
    fun tupletDigit(digit: Int): Int = 0xE880 + digit
    const val TUPLET_COLON = 0xE88A
    const val TUPLET_COLON_ADVANCE = 0.444f
    /** Alto de los números de grupo irregular, en espacios de pentagrama (la línea de base está abajo). */
    const val TUPLET_DIGIT_HEIGHT = 1.5f

    private val tupletAdvances = floatArrayOf(1.276f, 0.984f, 1.276f, 1.184f, 1.212f, 1.268f, 1.216f, 1.212f, 1.252f, 1.216f)

    fun tupletDigitAdvance(digit: Int): Float = tupletAdvances[digit]

    /** Dígitos de las firmas de compás: centrados verticalmente sobre su línea de base. */
    fun timeDigit(digit: Int): Int = 0xE080 + digit

    private val digitAdvances = floatArrayOf(1.88f, 1.336f, 1.784f, 1.684f, 1.88f, 1.612f, 1.736f, 1.764f, 1.744f, 1.736f)

    fun timeDigitAdvance(digit: Int): Float = digitAdvances[digit]
}
