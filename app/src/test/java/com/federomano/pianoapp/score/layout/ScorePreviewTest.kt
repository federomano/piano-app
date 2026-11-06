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

        fun draw(ops: List<DrawOp>, dx: Float, dy: Float) {
            g.color = ink
            for (op in ops) when (op) {
                is RectOp -> g.fill(Rectangle2D.Float(dx + op.x * spPx, dy + op.y * spPx, op.width * spPx, op.height * spPx))
                is GlyphOp -> {
                    g.font = music.deriveFont(4f * spPx * op.scale)
                    g.drawString(String(Character.toChars(op.codepoint)), dx + op.x * spPx, dy + op.y * spPx)
                }
                is TextOp -> {
                    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(op.size * spPx)
                    g.drawString(op.text, dx + op.x * spPx, dy + op.y * spPx)
                }
            }
        }

        val dy = Geometry.MARGIN_TOP * spPx
        val header = layout.headerAt(scrollSp)
        val headerPx = (header.width * spPx).toInt()
        val previousClip = g.clip
        g.clipRect(headerPx, 0, widthPx - headerPx, heightPx)
        draw(layout.body, headerPx - scrollSp * spPx, dy)
        g.clip = previousClip
        draw(header.ops, 0f, dy)
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
        val views = listOf(
            "1-inicio" to 0f,
            "2-cambio-compas-4" to layout.measures[3].x0 - 14f,
            "3-cambio-compas-7" to layout.measures[6].x0 - 14f,
            "4-cambio-compas-10" to layout.measures[9].x0 - 14f,
            "5-final" to layout.measures[10].x0,
        )
        for ((name, scroll) in views) {
            ImageIO.write(render(layout, scroll, width, spPx, music), "png", File(out, "$name.png"))
        }
    }
}
