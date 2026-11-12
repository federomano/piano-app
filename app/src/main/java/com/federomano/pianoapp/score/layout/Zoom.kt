package com.federomano.pianoapp.score.layout

/**
 * Tamaño de la partitura a gusto (PIA-17): un factor que multiplica el tamaño "justo" que calcula [ScoreLayout.fit].
 * Todo se dibuja con vectores y letras del tamaño pedido, así que se ve nítido a cualquier tamaño.
 * Achicar (hasta la mitad) deja ver más compases; agrandar tiene un tope para que los dos pentagramas sigan entrando
 * en pantalla sin quedar tapados por los controles de abajo.
 */
object Zoom {
    const val MIN = 0.5f
    const val MAX = 1.6f
    const val DEFAULT = 1f

    /** Espacios que hace falta ver arriba del pentagrama de la derecha (números de compás) y abajo del de la izquierda. */
    private const val ABOVE = 3.2f
    private const val BELOW = 0.5f

    /** El mayor factor que deja entrar la partitura entre el borde de arriba y los controles. Nunca menos que 1 ni más que [MAX]. */
    fun max(heightPx: Float, controlsPx: Float, baseSpPx: Float): Float =
        ((heightPx - controlsPx) / ((ABOVE + Geometry.SYSTEM_BOTTOM + BELOW) * baseSpPx)).coerceIn(DEFAULT, MAX)

    fun clamp(zoom: Float, max: Float): Float = zoom.coerceIn(MIN, max)

    /** El mismo acomodo pero con el tamaño multiplicado por [zoom] (ya limitado con [clamp]), alrededor del centro de los pentagramas. */
    fun apply(base: ScoreLayout.Fit, zoom: Float, heightPx: Float, controlsPx: Float): ScoreLayout.Fit {
        if (zoom == DEFAULT) return base
        val sp = base.spPx * zoom
        val middle = Geometry.SYSTEM_BOTTOM / 2f
        val centered = base.topPx + middle * base.spPx - middle * sp
        val top = if (zoom < DEFAULT) centered else centered.coerceAtLeast(ABOVE * sp).coerceAtMost(heightPx - controlsPx - (Geometry.SYSTEM_BOTTOM + BELOW) * sp)
        return ScoreLayout.Fit(sp, top)
    }
}
