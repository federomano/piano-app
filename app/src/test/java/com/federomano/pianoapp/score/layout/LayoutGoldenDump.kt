package com.federomano.pianoapp.score.layout

import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.ScoreJson
import com.federomano.pianoapp.score.playedNotes
import com.federomano.pianoapp.score.velocityAt
import com.federomano.pianoapp.score.velocityOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File

/**
 * Valores de referencia para la versión web (PIA-89): calcula el layout de cada archivo de `docs/ejemplos` con el motor de
 * Kotlin y lo guarda en `web/src/score/layout/fixtures/`. Las pruebas de la web (TypeScript) comparan su propio cálculo
 * contra estos archivos. Solo corre si se pide:
 *   GENERAR_LAYOUT_DORADO=1 ./gradlew testDebugUnitTest --tests '*LayoutGoldenDump*'
 */
class LayoutGoldenDump {
    private val examples = File("../docs/ejemplos")
    private val out = File("../web/src/score/layout/fixtures")

    private fun num(v: Float) = JsonPrimitive(v)

    private fun op(o: DrawOp): JsonElement = when (o) {
        is RectOp -> buildJsonArray { add(JsonPrimitive("r")); add(num(o.x)); add(num(o.y)); add(num(o.width)); add(num(o.height)) }
        is GlyphOp -> buildJsonArray { add(JsonPrimitive("g")); add(JsonPrimitive(o.codepoint)); add(num(o.x)); add(num(o.y)); add(num(o.scale)) }
        is TextOp -> buildJsonArray { add(JsonPrimitive("t")); add(JsonPrimitive(o.text)); add(num(o.x)); add(num(o.y)); add(num(o.size)) }
        is PolygonOp -> buildJsonArray { add(JsonPrimitive("p")); add(JsonArray(o.points.map { num(it) })) }
    }

    private fun ops(list: List<DrawOp>) = JsonArray(list.map { op(it) })

    private fun hand(h: Hand) = JsonPrimitive(if (h == Hand.RIGHT) "right" else "left")

    private fun dump(layout: ScoreLayout, score: Score): JsonObject {
        val end = layout.endTick
        val step = maxOf(60, end / 300 / 60 * 60)
        val ticks = (0..end step step).toList() + end
        val playheadX = layout.totalWidth.coerceAtMost(30f) * Geometry.PLAYHEAD_FRACTION
        val scrolls = listOf(-5f, 0f, layout.totalWidth * 0.25f, layout.totalWidth * 0.5f, layout.totalWidth * 0.75f, layout.totalWidth)
        return buildJsonObject {
            put("headerWidth", num(layout.headerWidth))
            put("signatureLeft", num(layout.signatureLeft))
            put("totalWidth", num(layout.totalWidth))
            put("marginTop", num(layout.marginTop))
            put("marginBottom", num(layout.marginBottom))
            put("endTick", JsonPrimitive(end))
            put("measures", buildJsonArray {
                for (m in layout.measures) add(buildJsonObject {
                    put("number", JsonPrimitive(m.measure.number))
                    put("startTick", JsonPrimitive(m.measure.startTick))
                    put("endTick", JsonPrimitive(m.measure.endTick))
                    put("keyFifths", JsonPrimitive(m.measure.keyFifths))
                    put("gridOffset", JsonPrimitive(m.measure.gridOffset))
                    put("x0", num(m.x0)); put("contentX", num(m.contentX)); put("x1", num(m.x1)); put("timeEndX", num(m.timeEndX))
                    put("anchorTicks", JsonArray((m.anchors?.ticks ?: emptyList()).map { JsonPrimitive(it) }))
                    put("anchorOffsets", JsonArray((m.anchors?.offsets ?: emptyList()).map { num(it) }))
                    put("leadExtra", num(m.anchors?.leadExtra ?: 0f))
                })
            })
            put("staff", ops(layout.staff))
            put("marks", ops(layout.marks))
            put("inlineChanges", ops(layout.inlineChanges))
            put("fixed", ops(layout.fixed))
            put("changes", buildJsonArray {
                for (c in layout.changes) add(buildJsonObject {
                    put("measureIndex", JsonPrimitive(c.measureIndex))
                    put("ops", ops(c.ops)); put("cancel", ops(c.cancel))
                    put("anchor", num(c.anchor)); put("shift", num(c.shift)); put("pinScroll", num(c.pinScroll))
                })
            })
            put("clefChanges", buildJsonArray {
                for (c in layout.clefChanges) add(buildJsonObject {
                    put("hand", hand(c.hand)); put("clef", JsonPrimitive(c.clef.name.lowercase()))
                    put("startScroll", num(c.startScroll)); put("endScroll", num(c.endScroll))
                })
            })
            put("chordSpans", buildJsonArray {
                for (s in layout.chordSpans) add(buildJsonObject {
                    put("hand", hand(s.chord.hand)); put("voice", JsonPrimitive(s.chord.voice))
                    put("startTick", JsonPrimitive(s.chord.startTick)); put("durationTicks", JsonPrimitive(s.chord.durationTicks))
                    put("notes", JsonPrimitive(s.chord.notes.size))
                    put("from", JsonPrimitive(s.from)); put("to", JsonPrimitive(s.to))
                    put("heads", JsonArray(s.heads.map { (n, i) -> buildJsonArray { add(JsonPrimitive(n.pitch.midi)); add(JsonPrimitive(i)) } }))
                })
            })
            put("labels", buildJsonObject {
                for ((key, args) in listOf("names" to (true to false), "fingers" to (false to true), "both" to (true to true))) {
                    put(key, buildJsonArray {
                        for (l in layout.labels(args.first, args.second)) add(buildJsonArray { add(hand(l.hand)); add(op(l.op)) })
                    })
                }
            })
            val labelsBoth = layout.labels(true, true)
            put("fit", buildJsonArray {
                for (fit in listOf(layout.fit(800f, 40f, 120f), layout.fit(300f, 40f, 150f), layout.fit(800f, 40f, 120f, labelsBoth))) {
                    add(buildJsonArray { add(num(fit.spPx)); add(num(fit.topPx)) })
                }
            })
            put("samples", buildJsonObject {
                put("ticks", JsonArray(ticks.map { JsonPrimitive(it) }))
                put("xOfTick", JsonArray(ticks.map { num(layout.xOfTick(it)) }))
                put("tickAtX", JsonArray(ticks.map { num(layout.tickAtX(layout.xOfTick(it) + 0.3f)) }))
                put("playheadX", num(playheadX))
                put("scrollForTick", JsonArray(ticks.map { num(layout.scrollForTick(it.toFloat(), playheadX)) }))
                put("tickAtScroll", JsonArray(ticks.map { num(layout.tickAtScroll(layout.scrollForTick(it.toFloat(), playheadX) + 0.7f, playheadX)) }))
                put("scrolls", JsonArray(scrolls.map { num(it) }))
                put("headerDraws", buildJsonArray {
                    for (s in scrolls) add(JsonArray(layout.headerDraws(s, 60f).map { d ->
                        buildJsonObject { put("ops", ops(d.ops)); put("dx", num(d.dx)); put("alpha", num(d.alpha)) }
                    }))
                })
                put("clefDraws", buildJsonArray {
                    for (s in scrolls) add(JsonArray(layout.clefDraws(s).map { d ->
                        buildJsonObject { put("ops", ops(d.ops)); put("dx", num(d.dx)); put("alpha", num(d.alpha)) }
                    }))
                })
                put("velocityAt", JsonArray(ticks.map { JsonPrimitive(score.velocityAt(it)) }))
                put("velocityOfNotes", JsonArray(score.notes.map { JsonPrimitive(score.velocityOf(it)) }))
                put("playedNotes", JsonArray(score.playedNotes().map { n ->
                    buildJsonArray { add(JsonPrimitive(n.pitch.midi)); add(JsonPrimitive(n.startTick)); add(JsonPrimitive(n.durationTicks)) }
                }))
                val spans = layout.chordSpans
                put("current", JsonArray(ticks.map { t ->
                    JsonArray(NoteStates.current(spans, t.toFloat()).sorted().map { JsonPrimitive(it) })
                }))
            })
        }
    }

    @Test
    fun dumpIfAsked() {
        if (System.getenv("GENERAR_LAYOUT_DORADO") != "1") return
        out.mkdirs()
        val compact = Json { prettyPrint = false }
        for (file in examples.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }) {
            val score = ScoreJson.decode(file.readText())
            val layout = ScoreLayout.build(score)
            File(out, file.name).writeText(compact.encodeToString(JsonObject.serializer(), dump(layout, score)))
        }
    }
}
