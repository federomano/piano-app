package com.federomano.pianoapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.federomano.pianoapp.score.Speed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Controles siempre visibles abajo: el botón de reiniciar, la barra de progreso, la velocidad y el botón
 * de silenciar. No hay botones de play ni pausa: eso se hace con un toque en la partitura. Tocar la
 * velocidad abre un panel con atajos y un deslizador.
 *
 * [currentTick] y [bpm] se leen al dibujar (no al componer), así se mueven sin recomponer en cada cuadro.
 */
@Composable
fun BoxScope.PlaybackControls(
    measureStarts: List<Int>,
    /** Número del primer compás: 1, o 0 si la pieza empieza con anacrusa. */
    firstMeasureNumber: Int,
    endTick: Int,
    currentTick: () -> Float,
    onSeek: (Int) -> Unit,
    onRestart: () -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    bpm: () -> Float,
    muted: Boolean,
    onMuteToggle: () -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    if (panelOpen) SpeedPanel(speed, onSpeed)
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RestartButton(onRestart)
        Spacer(Modifier.width(8.dp))
        ProgressBar(Modifier.weight(1f), measureStarts, firstMeasureNumber, endTick, currentTick, onSeek)
        Spacer(Modifier.width(8.dp))
        SpeedButton(speed, bpm, open = panelOpen) { panelOpen = !panelOpen }
        Spacer(Modifier.width(4.dp))
        MuteButton(muted, onMuteToggle)
    }
}

/** Panel de velocidad: atajos (50% a 150%) y deslizador de 25% a 200%. */
@Composable
private fun BoxScope.SpeedPanel(speed: Float, onSpeed: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 62.dp)
            .width(340.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PaperColor)
            .background(InkColor.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (preset in Speed.PRESETS) {
                val selected = Speed.percent(preset) == Speed.percent(speed)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) InkColor.copy(alpha = 0.8f) else InkColor.copy(alpha = 0.08f))
                        .clickable { onSpeed(preset) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${Speed.percent(preset)}%",
                        color = if (selected) PaperColor else InkColor,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("25%", color = InkColor.copy(alpha = 0.6f), fontSize = 11.sp)
            Slider(
                value = speed,
                onValueChange = onSpeed,
                valueRange = Speed.MIN..Speed.MAX,
                steps = Math.round((Speed.MAX - Speed.MIN) / Speed.STEP) - 1,
                colors = SliderDefaults.colors(
                    thumbColor = PlayheadColor,
                    activeTrackColor = InkColor.copy(alpha = 0.6f),
                    inactiveTrackColor = InkColor.copy(alpha = 0.18f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            Text("200%", color = InkColor.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

/** Muestra la velocidad y, abajo, el tempo que se escucha en este momento (negras por minuto). */
@Composable
private fun SpeedButton(speed: Float, bpm: () -> Float, open: Boolean, onClick: () -> Unit) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(
        modifier = Modifier
            .width(68.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
    ) {
        drawRoundRect(
            InkColor.copy(alpha = if (open) 0.18f else 0.08f),
            topLeft = Offset(0f, 2.dp.toPx()),
            size = Size(size.width, size.height - 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        )
        val canvas = drawContext.canvas.nativeCanvas
        paint.color = InkColor.copy(alpha = 0.85f).toArgb()
        paint.isFakeBoldText = true
        paint.textSize = 15.dp.toPx()
        canvas.drawText("${Speed.percent(speed)}%", center.x, center.y - 1.dp.toPx(), paint)
        paint.isFakeBoldText = false
        paint.color = InkColor.copy(alpha = 0.6f).toArgb()
        paint.textSize = 10.dp.toPx()
        canvas.drawText("${Math.round(bpm())} bpm", center.x, center.y + 13.dp.toPx(), paint)
    }
}

/** Altavoz: con ondas suena, tachado está silenciado. */
@Composable
private fun MuteButton(muted: Boolean, onToggle: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onToggle),
    ) {
        val c = center
        drawCircle(InkColor.copy(alpha = if (muted) 0.16f else 0.08f), radius = 22.dp.toPx(), center = c)
        val ink = InkColor.copy(alpha = if (muted) 0.45f else 0.8f)
        val body = Path().apply {
            moveTo(c.x - 9.dp.toPx(), c.y - 3.5.dp.toPx())
            lineTo(c.x - 4.dp.toPx(), c.y - 3.5.dp.toPx())
            lineTo(c.x + 1.dp.toPx(), c.y - 8.dp.toPx())
            lineTo(c.x + 1.dp.toPx(), c.y + 8.dp.toPx())
            lineTo(c.x - 4.dp.toPx(), c.y + 3.5.dp.toPx())
            lineTo(c.x - 9.dp.toPx(), c.y + 3.5.dp.toPx())
            close()
        }
        drawPath(body, ink, style = Fill)
        if (muted) {
            drawLine(PlayheadColor, Offset(c.x - 10.dp.toPx(), c.y + 10.dp.toPx()), Offset(c.x + 10.dp.toPx(), c.y - 10.dp.toPx()), strokeWidth = 2.5.dp.toPx())
        } else {
            for (r in listOf(5f, 9f)) {
                drawArc(
                    ink, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(c.x + 1.dp.toPx() - (r - 3).dp.toPx(), c.y - r.dp.toPx()),
                    size = Size(2 * r.dp.toPx() - 6.dp.toPx() + 6.dp.toPx(), 2 * r.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }
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
    firstMeasureNumber: Int,
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
        val measure = measureStarts.indexOfLast { it <= tick }.coerceAtLeast(0) + firstMeasureNumber
        val lastMeasure = measureStarts.size - 1 + firstMeasureNumber
        textPaint.textSize = 12.dp.toPx()
        drawContext.canvas.nativeCanvas.drawText("Compás $measure de $lastMeasure", x1, y - 14.dp.toPx(), textPaint)
    }
}
