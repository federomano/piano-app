package com.federomano.pianoapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.federomano.pianoapp.audio.LevelAnalyzer
import com.federomano.pianoapp.audio.LevelReading
import com.federomano.pianoapp.audio.LevelStatus
import com.federomano.pianoapp.audio.MicCapture

/** Mensaje en palabras simples para cada estado de la señal. */
fun levelMessage(status: LevelStatus): String = when (status) {
    LevelStatus.SILENT -> "No llega sonido. Tocá una nota. Si sigue sin moverse, revisá que no haya otra app usando el micrófono y que no esté tapado."
    LevelStatus.TOO_LOW -> "Se escucha muy bajito. Acercá el celu al piano o tocá un poco más fuerte."
    LevelStatus.GOOD -> "Nivel bien. Así la app puede escuchar tu piano."
    LevelStatus.CLIPPING -> "Se satura y el sonido se distorsiona. Alejá el celu del piano o tocá más suave."
}

/** La línea "Captura: …": cómo se está tomando el audio y, si no es sin procesar, por qué. */
fun captureDescription(source: MicCapture.Source, fallback: MicCapture.Fallback?): String =
    "Captura: " + source.label + (fallback?.let { " (${it.explanation})" } ?: "") + "."

/** Dónde cae un nivel en dB sobre el medidor: -60 dB (izquierda) a 0 dB (derecha). */
fun meterFraction(db: Float): Float = ((db - METER_MIN_DB) / -METER_MIN_DB).coerceIn(0f, 1f)

private const val METER_MIN_DB = -60f
internal val GoodColor = Color(0xFF2E7D32)
internal val WarnColor = Color(0xFFF9A825)
internal val BadColor = Color(0xFFC62828)

/**
 * Probar el micrófono (PIA-30): explica para qué se usa, pide el permiso y muestra con qué nivel la app escucha el piano.
 * El audio solo se analiza en el momento: no se graba ni se guarda.
 */
@Composable
fun MicTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    MicPermissionGate("Probar el micrófono", onBack, modifier) {
        val live = remember { LiveMic() }
        MicCaptureEffect(live)
        Box(modifier = modifier.fillMaxSize().background(PaperColor), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onBack) { Text("‹ Menú", fontSize = 12.sp, color = InkColor.copy(alpha = 0.65f)) }
                Text("Probar el micrófono", color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Tocá el piano y mirá la barra: tiene que quedar en la zona del medio.", color = InkColor.copy(alpha = 0.75f), fontSize = 13.sp)
                LiveLevelBlock(live)
            }
        }
    }
}

/** La barra de nivel con sus rótulos, el mensaje de estado y la línea "Captura: …". */
@Composable
fun LiveLevelBlock(live: LiveMic) {
    val current = live.reading.value
    val state = live.state.value
    val status = current?.status ?: LevelStatus.SILENT
    val color = when (status) {
        LevelStatus.GOOD -> GoodColor
        LevelStatus.CLIPPING -> BadColor
        else -> InkColor.copy(alpha = 0.55f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LevelMeter(current, color)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("muy bajo", color = InkColor.copy(alpha = 0.6f), fontSize = 11.sp)
            Text("bien", color = GoodColor, fontSize = 11.sp)
            Text("muy fuerte", color = InkColor.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        val message = when (state) {
            MicCapture.State.FAILED -> "No se pudo abrir el micrófono. Cerrá otras apps que lo estén usando y volvé a entrar a esta pantalla."
            MicCapture.State.INTERRUPTED -> "El micrófono se cortó (por ejemplo por una llamada u otra app que lo usa). Se reconecta solo."
            MicCapture.State.LISTENING -> levelMessage(status)
        }
        Text(
            message,
            color = when {
                state != MicCapture.State.LISTENING -> BadColor
                status == LevelStatus.GOOD -> GoodColor
                status == LevelStatus.CLIPPING -> BadColor
                else -> InkColor
            },
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
        live.source.value?.let { (src, why) ->
            Text(captureDescription(src, why), color = InkColor.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

/** Barra de nivel: el pico actual y una marca con el pico más alto de los últimos segundos. Las zonas son las del aviso. */
@Composable
fun LevelMeter(reading: LevelReading?, fillColor: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        val w = size.width
        val h = size.height
        fun x(db: Float) = meterFraction(db) * w
        drawRect(InkColor.copy(alpha = 0.08f), Offset.Zero, Size(w, h))
        drawRect(InkColor.copy(alpha = 0.06f), Offset(x(-60f), 0f), Size(x(LevelAnalyzer.LOW_DB) - x(-60f), h))
        drawRect(GoodColor.copy(alpha = 0.18f), Offset(x(LevelAnalyzer.LOW_DB), 0f), Size(x(-6f) - x(LevelAnalyzer.LOW_DB), h))
        drawRect(WarnColor.copy(alpha = 0.28f), Offset(x(-6f), 0f), Size(w - x(-6f), h))
        if (reading != null) {
            drawRect(fillColor, Offset.Zero, Size(x(reading.peakDb), h * 0.7f))
            val hold = x(reading.holdDb)
            drawRect(InkColor, Offset(hold - 1.5f, 0f), Size(3f, h))
        }
    }
}
