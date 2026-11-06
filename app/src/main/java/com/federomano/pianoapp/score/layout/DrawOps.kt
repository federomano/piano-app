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

    /** Medidas de la llave a tamaño normal: 4 espacios de alto por 0,277 de ancho. */
    const val BRACE_HEIGHT = 4.002f
    const val BRACE_WIDTH = 0.277f

    /** Dígitos de las firmas de compás: centrados verticalmente sobre su línea de base. */
    fun timeDigit(digit: Int): Int = 0xE080 + digit

    private val digitAdvances = floatArrayOf(1.88f, 1.336f, 1.784f, 1.684f, 1.88f, 1.612f, 1.736f, 1.764f, 1.744f, 1.736f)

    fun timeDigitAdvance(digit: Int): Float = digitAdvances[digit]
}
