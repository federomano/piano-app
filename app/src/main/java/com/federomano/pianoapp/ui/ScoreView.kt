package com.federomano.pianoapp.ui

import android.graphics.Paint
import android.graphics.Typeface
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

        val draggable = rememberDraggableState { delta ->
            if (!playing) {
                val now = if (scrollSp.isNaN()) minScroll else scrollSp
                scrollSp = (now - delta / spPx).coerceIn(minScroll, maxScroll)
            }
        }

        // Reproducción: en cada cuadro avanza el tiempo según el tempo y la partitura se desliza para que
        // ese instante quede sobre la línea.
        LaunchedEffect(playing, layout, seek) {
            if (!playing) return@LaunchedEffect
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
                .pointerInput(Unit) { detectTapGestures { playing = !playing } }
                .draggable(draggable, Orientation.Horizontal),
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
                scrollSp = layout.scrollForTick(tick.toFloat(), playheadSp)
                seek++
            },
            onRestart = {
                scrollSp = minScroll
                seek++
            },
        )
    }
}
