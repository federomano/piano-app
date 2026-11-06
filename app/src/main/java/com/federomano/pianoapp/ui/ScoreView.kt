package com.federomano.pianoapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.layout.DrawOp
import com.federomano.pianoapp.score.layout.Geometry
import com.federomano.pianoapp.score.layout.GlyphOp
import com.federomano.pianoapp.score.layout.RectOp
import com.federomano.pianoapp.score.layout.ScoreLayout
import com.federomano.pianoapp.score.layout.TextOp

val PaperColor = Color(0xFFF6F3EA)
val InkColor = Color(0xFF1A1A1A)

/**
 * Partitura de dos pentagramas en una línea continua. Se arrastra con el dedo hacia los costados;
 * la clave, la armadura y el compás quedan fijos a la izquierda.
 */
@Composable
fun ScoreView(score: Score, modifier: Modifier = Modifier, maxStaffSpaceDp: Float = 17f) {
    val context = LocalContext.current
    val layout = remember(score) { ScoreLayout.build(score) }
    val musicFont = remember { Typeface.createFromAsset(context.assets, "fonts/Bravura.otf") }
    var scrollSp by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.background(PaperColor)) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Un espacio de pentagrama: lo que entre en el alto, sin pasarse de un tamaño cómodo de leer.
        val spPx = minOf(maxStaffSpaceDp * density, heightPx / Geometry.TOTAL_HEIGHT)

        val maxScroll = maxOf(0f, layout.totalWidth - widthPx / spPx * 0.3f)
        val draggable = rememberDraggableState { delta ->
            scrollSp = (scrollSp - delta / spPx).coerceIn(0f, maxScroll)
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
                .draggable(draggable, Orientation.Horizontal),
        ) {
            val top = ((heightPx - Geometry.TOTAL_HEIGHT * spPx) / 2f) + Geometry.MARGIN_TOP * spPx
            val header = layout.headerAt(scrollSp)
            val headerPx = header.width * spPx

            fun paint(ops: List<DrawOp>, dx: Float) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    for (op in ops) when (op) {
                        is RectOp -> native.drawRect(
                            dx + op.x * spPx, top + op.y * spPx,
                            dx + (op.x + op.width) * spPx, top + (op.y + op.height) * spPx,
                            musicPaint,
                        )
                        is GlyphOp -> {
                            musicPaint.textSize = 4f * spPx * op.scale
                            native.drawText(String(Character.toChars(op.codepoint)), dx + op.x * spPx, top + op.y * spPx, musicPaint)
                        }
                        is TextOp -> {
                            textPaint.textSize = op.size * spPx
                            native.drawText(op.text, dx + op.x * spPx, top + op.y * spPx, textPaint)
                        }
                    }
                }
            }

            clipRect(left = headerPx, top = 0f, right = widthPx, bottom = heightPx) {
                paint(layout.body, headerPx - scrollSp * spPx)
            }
            paint(header.ops, 0f)
        }
    }
}
