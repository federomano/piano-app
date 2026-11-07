package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.DemoScore
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeNoException
import org.junit.Test

/**
 * Dibuja el layout en imágenes PNG (en app/build/previews) para poder revisar el resultado a ojo,
 * sin celular. Usa las mismas instrucciones de dibujo que la app. Si la máquina no tiene soporte
 * de fuentes, el test se saltea.
 */
class ScorePreviewTest {

    private val paper = Color(0xF6, 0xF3, 0xEA)
    private val ink = Color(0x1A, 0x1A, 0x1A)

    private fun render(layout: ScoreLayout, scrollSp: Float, widthPx: Int, spPx: Float, music: Font): BufferedImage {
        val heightPx = (Geometry.TOTAL_HEIGHT * spPx).toInt()
        val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.color = paper
        g.fillRect(0, 0, widthPx, heightPx)

        fun drawAt(ops: List<DrawOp>, dx: Float, dy: Float) {
            g.color = ink
            for (op in ops) when (op) {
                is RectOp -> g.fill(Rectangle2D.Float(dx + op.x * spPx, dy + op.y * spPx, op.width * spPx, op.height * spPx))
                is GlyphOp -> {
                    g.font = music.deriveFont(4f * spPx * op.scale)
                    g.drawString(String(Character.toChars(op.codepoint)), dx + op.x * spPx, dy + op.y * spPx)
                }
                is PolygonOp -> {
                    val path = java.awt.geom.Path2D.Float()
                    for (i in op.points.indices step 2) {
                        val px = dx + op.points[i] * spPx
                        val py = dy + op.points[i + 1] * spPx
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.closePath()
                    g.fill(path)
                }
                is TextOp -> {
                    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(op.size * spPx)
                    g.drawString(op.text, dx + op.x * spPx, dy + op.y * spPx)
                }
            }
        }

        val dy = Geometry.MARGIN_TOP * spPx
        val headerPx = (layout.headerWidth * spPx).toInt()
        fun draw(ops: List<DrawOp>, dx: Float, alpha: Float) {
            g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
            drawAt(ops, dx, dy)
            g.composite = java.awt.AlphaComposite.SrcOver
        }
        val previousClip = g.clip
        g.clipRect(headerPx, 0, widthPx - headerPx, heightPx)
        draw(layout.staff, headerPx - scrollSp * spPx, 1f)
        g.clip = previousClip
        draw(layout.fixed, 0f, 1f)
        for (d in layout.headerDraws(scrollSp, widthPx / spPx)) draw(d.ops, d.dx * spPx, d.alpha)
        for (op in layout.marks) {
            val x = (when (op) { is RectOp -> op.x; is TextOp -> op.x; is GlyphOp -> op.x; is PolygonOp -> op.minX }) - scrollSp + layout.headerWidth
            if (x < 0f || x > widthPx / spPx) continue
            val alpha = ((x - layout.signatureLeft) / (layout.headerWidth - layout.signatureLeft)).coerceIn(0f, 1f)
            draw(listOf(op), (layout.headerWidth - scrollSp) * spPx, alpha)
        }
        g.dispose()
        return image
    }

    @Test
    fun writePreviewImages() {
        val music = try {
            check(!GraphicsEnvironment.isHeadless() || true)
            Font.createFont(Font.TRUETYPE_FONT, File("src/main/assets/fonts/Bravura.otf"))
        } catch (e: Throwable) {
            assumeNoException(e)
            return
        }
        val layout = ScoreLayout.build(DemoScore.create())
        val out = File("build/previews").apply { mkdirs() }
        val spPx = 26f
        val width = 1900
        val w = layout.headerWidth
        val views = listOf("1" to 0, "2" to 1, "3" to 3, "4" to 4, "5" to 5, "6" to 6, "7" to 7, "8" to 8, "9" to 9, "10" to 10, "11" to 11)
            .map { (name, m) -> "m" + name.padStart(2, '0') to if (m == 0) 0f else layout.measures[m].x0 - w + 0.1f }
        for ((name, scroll) in views) {
            ImageIO.write(render(layout, scroll, width, spPx, music), "png", File(out, "$name.png"))
        }
    }
}
