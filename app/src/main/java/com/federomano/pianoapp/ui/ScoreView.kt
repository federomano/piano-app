package com.federomano.pianoapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.TempoMap
import com.federomano.pianoapp.score.layout.DrawOp
import com.federomano.pianoapp.score.layout.Geometry
import com.federomano.pianoapp.score.layout.GlyphOp
import com.federomano.pianoapp.score.layout.PolygonOp
import com.federomano.pianoapp.score.layout.RectOp
import com.federomano.pianoapp.score.layout.ScoreLayout
import com.federomano.pianoapp.score.layout.TextOp

val PaperColor = Color(0xFFF6F3EA)
val InkColor = Color(0xFF1A1A1A)

val PlayheadColor = Color(0xFFB3261E)

/** Velocidad mínima (espacios por segundo) con la que soltar el dedo deja a la partitura deslizándose. */
private const val MIN_FLING_SPEED = 8f

/** Cuánto frena la inercia: más chico, se desliza más lejos (1 es el valor normal de Android). */
private const val FLING_FRICTION = 0.7f

/**
 * Partitura de dos pentagramas en una línea continua. La clave, la armadura y el compás quedan fijos
 * a la izquierda, y la línea de reproducción queda fija cerca del 30% de la pantalla: la partitura se
 * desliza hacia la izquierda y la nota que toca pasa justo por esa línea.
 *
 * Un toque en la partitura reproduce o pausa. En pausa se puede arrastrar con el dedo para cambiar la
 * posición. Abajo, siempre visibles, quedan el botón de reiniciar y la barra de progreso (tocar un
 * compás salta hasta ahí).
 */
@Composable
fun ScoreView(score: Score, modifier: Modifier = Modifier, maxStaffSpaceDp: Float = 17f) {
    val context = LocalContext.current
    val layout = remember(score) { ScoreLayout.build(score) }
    val musicFont = remember { Typeface.createFromAsset(context.assets, "fonts/Bravura.otf") }
    // NaN = todavía no se movió: se queda en el comienzo de la pieza.
    var scrollSp by remember { mutableFloatStateOf(Float.NaN) }
    var playing by remember { mutableStateOf(false) }
    // Sube cada vez que la posición cambia por un salto (barra, reiniciar): la reproducción retoma desde ahí.
    var seek by remember { mutableIntStateOf(0) }
    val tempoMap = remember(score) { TempoMap(score.tempos) }

    BoxWithConstraints(modifier = modifier.background(PaperColor)) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Un espacio de pentagrama: lo que entre en el alto, sin pasarse de un tamaño cómodo de leer.
        val spPx = minOf(maxStaffSpaceDp * density, heightPx / Geometry.TOTAL_HEIGHT)

        // Línea de reproducción: fija en pantalla. El desplazamiento mínimo deja el comienzo de la pieza
        // sobre la línea; el máximo, el final.
        val playheadSp = widthPx / spPx * Geometry.PLAYHEAD_FRACTION
        val minScroll = layout.scrollForTick(0f, playheadSp)
        val maxScroll = layout.scrollForTick(layout.endTick.toFloat(), playheadSp)
        val currentPlayhead by rememberUpdatedState(playheadSp)
        val currentMin by rememberUpdatedState(minScroll)

        // Arrastre tipo vinilo: mientras el dedo está abajo, o la partitura sigue deslizándose por inercia,
        // la reproducción se suspende ([holding]); al terminar retoma desde donde quedó, y si estaba en
        // pausa sigue en pausa.
        val scope = rememberCoroutineScope()
        var holding by remember { mutableStateOf(false) }
        var flingJob by remember { mutableStateOf<Job?>(null) }
        var stoppedFling by remember { mutableStateOf(false) }

        fun stopFling(): Boolean {
            val job = flingJob
            flingJob = null
            if (job?.isActive != true) return false
            job.cancel()
            holding = false
            return true
        }

        val draggable = rememberDraggableState { delta ->
            val now = if (scrollSp.isNaN()) minScroll else scrollSp
            scrollSp = (now - delta / spPx).coerceIn(minScroll, maxScroll)
        }

        // Reproducción: en cada cuadro avanza el tiempo según el tempo y la partitura se desliza para que
        // ese instante quede sobre la línea.
        LaunchedEffect(playing, holding, layout, seek) {
            if (!playing || holding) return@LaunchedEffect
            val end = layout.endTick.toFloat()
            val from = if (scrollSp.isNaN()) currentMin else scrollSp
            var tick = layout.tickAtScroll(from, currentPlayhead).coerceAtLeast(0f)
            if (tick >= end - 1f) tick = 0f
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                tick = tempoMap.advance(tick, seconds)
                if (tick >= end) {
                    scrollSp = layout.scrollForTick(end, currentPlayhead)
                    playing = false
                    break
                }
                scrollSp = layout.scrollForTick(tick, currentPlayhead)
            }
        }

        val musicPaint = remember(musicFont) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = musicFont
                color = InkColor.toArgb()
            }
        }
        val textPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.SANS_SERIF
                color = InkColor.toArgb()
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Tocar durante la inercia la frena en seco, sin cambiar play ni pausa.
                        onPress = { stoppedFling = stopFling() },
                        onTap = { if (!stoppedFling) playing = !playing },
                    )
                }
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        stopFling()
                        holding = true
                    },
                    onDragStopped = { velocityPx ->
                        // La partitura se mueve al revés que el dedo; la velocidad se pasa a espacios por segundo.
                        val velocity = -velocityPx / spPx
                        if (kotlin.math.abs(velocity) < MIN_FLING_SPEED) {
                            holding = false
                        } else {
                            flingJob = scope.launch {
                                // Con límites: al llegar al principio o al final la animación se detiene ahí, sin rebotes.
                                val animation = Animatable(if (scrollSp.isNaN()) minScroll else scrollSp)
                                animation.updateBounds(minScroll, maxScroll)
                                animation.animateDecay(velocity, exponentialDecay(frictionMultiplier = FLING_FRICTION)) {
                                    scrollSp = value
                                }
                                holding = false
                            }
                        }
                    },
                ),
        ) {
            val scroll = if (scrollSp.isNaN()) minScroll else scrollSp
            val top = ((heightPx - Geometry.TOTAL_HEIGHT * spPx) / 2f) + Geometry.MARGIN_TOP * spPx
            val headerPx = layout.headerWidth * spPx

            fun paintOp(op: DrawOp, dx: Float, alpha: Float) {
                if (alpha <= 0f) return
                val native = drawContext.canvas.nativeCanvas
                val a = (alpha * 255).toInt().coerceIn(0, 255)
                when (op) {
                    is RectOp -> {
                        musicPaint.alpha = a
                        native.drawRect(
                            dx + op.x * spPx, top + op.y * spPx,
                            dx + (op.x + op.width) * spPx, top + (op.y + op.height) * spPx,
                            musicPaint,
                        )
                    }
                    is GlyphOp -> {
                        musicPaint.alpha = a
                        musicPaint.textSize = 4f * spPx * op.scale
                        native.drawText(String(Character.toChars(op.codepoint)), dx + op.x * spPx, top + op.y * spPx, musicPaint)
                    }
                    is PolygonOp -> {
                        musicPaint.alpha = a
                        val path = android.graphics.Path()
                        for (i in op.points.indices step 2) {
                            val px = dx + op.points[i] * spPx
                            val py = top + op.points[i + 1] * spPx
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        native.drawPath(path, musicPaint)
                    }
                    is TextOp -> {
                        textPaint.alpha = a
                        textPaint.textSize = op.size * spPx
                        native.drawText(op.text, dx + op.x * spPx, top + op.y * spPx, textPaint)
                    }
                }
            }

            fun paint(ops: List<DrawOp>, dx: Float, alpha: Float = 1f) = ops.forEach { paintOp(it, dx, alpha) }

            // 1) Líneas de los pentagramas, a la derecha del área fija.
            clipRect(left = headerPx, top = 0f, right = widthPx, bottom = heightPx) {
                paint(layout.staff, headerPx - scroll * spPx)
            }

            // 2) Área fija: líneas, llave y claves (no cambian nunca), y encima la armadura y el compás.
            paint(layout.fixed, 0f)
            for (draw in layout.headerDraws(scroll, widthPx / spPx)) paint(draw.ops, draw.dx * spPx, draw.alpha)

            // 3) Barras de compás y números: pasan por encima del área fija y se desvanecen
            //    al acercarse a la clave.
            val fadeEnd = layout.signatureLeft
            for (op in layout.marks) {
                val x = (when (op) { is RectOp -> op.x; is TextOp -> op.x; is GlyphOp -> op.x; is PolygonOp -> op.minX }) - scroll + layout.headerWidth
                if (x < 0f || x > widthPx / spPx) continue
                val alpha = ((x - fadeEnd) / (layout.headerWidth - fadeEnd)).coerceIn(0f, 1f)
                paintOp(op, (layout.headerWidth - scroll) * spPx, alpha)
            }

            // 4) Línea de reproducción, siempre en el mismo lugar de la pantalla.
            val lineX = playheadSp * spPx
            drawLine(
                color = PlayheadColor.copy(alpha = 0.65f),
                start = Offset(lineX, top - 0.6f * spPx),
                end = Offset(lineX, top + (Geometry.SYSTEM_BOTTOM + 0.6f) * spPx),
                strokeWidth = 0.14f * spPx,
            )
        }

        PlaybackControls(
            measureStarts = layout.measures.map { it.measure.startTick },
            endTick = layout.endTick,
            currentTick = { layout.tickAtScroll(if (scrollSp.isNaN()) minScroll else scrollSp, playheadSp) },
            onSeek = { tick ->
                stopFling()
                scrollSp = layout.scrollForTick(tick.toFloat(), playheadSp)
                seek++
            },
            onRestart = {
                stopFling()
                scrollSp = minScroll
                seek++
            },
        )
    }
}
