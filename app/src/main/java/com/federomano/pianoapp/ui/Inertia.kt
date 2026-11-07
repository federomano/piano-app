package com.federomano.pianoapp.ui

import kotlin.math.exp

/** Cuenta de la inercia del arrastre (ver ScoreView): la velocidad se acerca de a poco a la de reproducción. */
object Inertia {
    /** Velocidad después de [seconds], partiendo de [velocity] y acercándose a [target] a razón de [rate] por segundo. */
    fun step(velocity: Float, target: Float, seconds: Float, rate: Float): Float =
        target + (velocity - target) * exp(-rate * seconds)
}
