package com.federomano.pianoapp.ui

import kotlin.math.exp

/** Cuenta de la inercia del arrastre (ver ScoreView): la velocidad se acerca de a poco a la de reproducción. */
object Inertia {
    /** Velocidad después de [seconds], partiendo de [velocity] y acercándose a [target] a razón de [rate] por segundo. */
    fun step(velocity: Float, target: Float, seconds: Float, rate: Float): Float =
        target + (velocity - target) * exp(-rate * seconds)

    /**
     * Mientras la inercia se acerca a la reproducción, ¿ya va lo bastante parecido al ritmo normal (hacia
     * adelante) como para que suenen las notas? Muy rápido sería una ráfaga; casi quieto o hacia atrás, nada.
     */
    fun soundsAt(velocity: Float, target: Float): Boolean =
        target > 0f && velocity >= SOUND_MIN * target && velocity <= SOUND_MAX * target

    private const val SOUND_MIN = 0.6f
    private const val SOUND_MAX = 1.5f
}
