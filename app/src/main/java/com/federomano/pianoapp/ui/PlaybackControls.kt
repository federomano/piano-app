package com.federomano.pianoapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Controles siempre visibles abajo: el botón de reiniciar y la barra de progreso. No hay botones de
 * play ni pausa: eso se hace con un toque en la partitura.
 *
 * [currentTick] se lee al dibujar (no al componer), así la barra se mueve sin recomponer en cada cuadro.
 */
@Composable
fun BoxScope.PlaybackControls(
    measureStarts: List<Int>,
    endTick: Int,
    currentTick: () -> Float,
    onSeek: (Int) -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RestartButton(onRestart)
        Spacer(Modifier.width(8.dp))
        ProgressBar(Modifier.weight(1f), measureStarts, endTick, currentTick, onSeek)
    }
}

@Composable
private fun RestartButton(onRestart: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onRestart),
    ) {
        val c = center
        drawCircle(InkColor.copy(alpha = 0.08f), radius = 22.dp.toPx(), center = c)
        val ink = InkColor.copy(alpha = 0.8f)
        // Símbolo de "volver al principio": una barra y un triángulo que apunta a la izquierda.
        val h = 7.dp.toPx()
        drawRect(ink, topLeft = Offset(c.x - 8.dp.toPx(), c.y - h), size = Size(2.5.dp.toPx(), 2 * h))
        val triangle = Path().apply {
            moveTo(c.x - 4.dp.toPx(), c.y)
            lineTo(c.x + 7.dp.toPx(), c.y - h)
            lineTo(c.x + 7.dp.toPx(), c.y + h)
            close()
        }
        drawPath(triangle, ink, style = Fill)
    }
}

@Composable
private fun ProgressBar(
    modifier: Modifier,
    measureStarts: List<Int>,
    endTick: Int,
    currentTick: () -> Float,
    onSeek: (Int) -> Unit,
) {
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            color = InkColor.copy(alpha = 0.65f).toArgb()
            textAlign = Paint.Align.RIGHT
        }
    }
    Canvas(
        modifier = modifier
            .height(48.dp)
            .pointerInput(measureStarts, endTick) {
                val pad = 10.dp.toPx()
                detectTapGestures { offset ->
                    val fraction = ((offset.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    val tick = fraction * endTick
                    val index = measureStarts.indexOfLast { it <= tick }.coerceAtLeast(0)
                    onSeek(measureStarts[index])
                }
            },
    ) {
        val pad = 10.dp.toPx()
        val x0 = pad
        val x1 = size.width - pad
        val y = size.height / 2f + 6.dp.toPx()
        val tick = currentTick().coerceIn(0f, endTick.toFloat())
        val fraction = if (endTick > 0) tick / endTick else 0f
        val thickness = 6.dp.toPx()
        val xNow = x0 + (x1 - x0) * fraction

        // Pista y parte ya recorrida.
        drawRoundRect(InkColor.copy(alpha = 0.14f), Offset(x0, y - thickness / 2), Size(x1 - x0, thickness), androidx.compose.ui.geometry.CornerRadius(thickness / 2))
        drawRoundRect(InkColor.copy(alpha = 0.55f), Offset(x0, y - thickness / 2), Size(maxOf(xNow - x0, thickness), thickness), androidx.compose.ui.geometry.CornerRadius(thickness / 2))

        // Una marca por compás (si no quedan demasiado juntas).
        var lastX = -100f
        for (start in measureStarts.drop(1)) {
            val x = x0 + (x1 - x0) * start / endTick
            if (x - lastX >= 4.dp.toPx()) {
                drawLine(InkColor.copy(alpha = 0.45f), Offset(x, y - 7.dp.toPx()), Offset(x, y + 7.dp.toPx()), strokeWidth = 1.2.dp.toPx())
                lastX = x
            }
        }

        // Posición actual.
        drawCircle(PlayheadColor, radius = 8.dp.toPx(), center = Offset(xNow, y))

        // Compás actual, sobre la barra.
        val measure = measureStarts.indexOfLast { it <= tick }.coerceAtLeast(0) + 1
        textPaint.textSize = 12.dp.toPx()
        drawContext.canvas.nativeCanvas.drawText("Compás $measure de ${measureStarts.size}", x1, y - 14.dp.toPx(), textPaint)
    }
}
