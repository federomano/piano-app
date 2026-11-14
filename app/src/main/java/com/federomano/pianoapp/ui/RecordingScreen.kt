package com.federomano.pianoapp.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.federomano.pianoapp.audio.LevelAnalyzer
import com.federomano.pianoapp.audio.MicCapture
import com.federomano.pianoapp.audio.PianoKind
import com.federomano.pianoapp.audio.RecordingBank
import com.federomano.pianoapp.audio.RecordingScript
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { IDLE, COUNTDOWN, RECORDING }

/** Resultado de una toma, para mostrárselo a Fede apenas termina. */
private class TakeResult(val ok: Boolean, val text: String)

/** Lo que va juntando la captura mientras se graba una toma. Lo escribe el hilo de captura y lo lee la pantalla. */
private class TakeBuffer(val capacity: Int) {
    val samples = ShortArray(capacity)
    @Volatile var size = 0

    fun add(data: ShortArray, count: Int) {
        val n = minOf(count, capacity - size)
        if (n > 0) {
            System.arraycopy(data, 0, samples, size, n)
            size += n
        }
    }
}

/**
 * Grabar audios de prueba (PIA-38): una lista guiada de qué tocar en cada piano. Cada toma empieza con una cuenta regresiva, se
 * graba sola durante unos segundos y se guarda en el celu. Al final se comparten todas juntas en un .zip. Solo graba cuando
 * Fede toca «Grabar».
 */
@Composable
fun RecordingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    MicPermissionGate("Grabar audios de prueba", onBack, modifier) { RecordingContent(onBack, modifier) }
}

@Composable
private fun RecordingContent(onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val live = remember { LiveMic() }
    MicCaptureEffect(live)
    val bank = remember { RecordingBank(File(context.filesDir, "test-recordings")) }
    val scope = rememberCoroutineScope()

    var piano by remember { mutableStateOf(PianoKind.ACOUSTIC) }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var countdown by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<TakeResult?>(null) }
    var recorded by remember { mutableStateOf(bank.recordedIds(piano)) }
    var job by remember { mutableStateOf<Job?>(null) }

    val takes = RecordingScript.takes
    val take = takes[index]
    val busy = phase != Phase.IDLE

    fun cancel() {
        job?.cancel()
        live.tap = null
        phase = Phase.IDLE
    }

    DisposableEffect(Unit) { onDispose { job?.cancel(); live.tap = null } }

    fun record() {
        job = scope.launch {
            result = null
            phase = Phase.COUNTDOWN
            for (n in 3 downTo 1) { countdown = n; delay(1000) }
            val sampleRate = MicCapture.SAMPLE_RATE
            val buffer = TakeBuffer(sampleRate * take.seconds)
            val source = live.source.value?.first
            if (live.state.value != MicCapture.State.LISTENING || source == null) {
                phase = Phase.IDLE
                result = TakeResult(false, "El micrófono no estaba escuchando. Esperá un momento y probá de nuevo.")
                return@launch
            }
            live.tap = { data, count -> buffer.add(data, count) }
            phase = Phase.RECORDING
            while (buffer.size < buffer.capacity) {
                progress = buffer.size.toFloat() / buffer.capacity
                delay(80)
            }
            live.tap = null
            phase = Phase.IDLE
            progress = 0f
            var peak = 0
            for (i in 0 until buffer.size) peak = maxOf(peak, abs(buffer.samples[i].toInt()))
            val peakDb = LevelAnalyzer.toDb(peak.toDouble())
            val problem = when {
                take.expected.isNotEmpty() && peakDb < LevelAnalyzer.LOW_DB -> "Quedó muy bajito: acercá el celu al piano o tocá un poco más fuerte, y repetí la toma."
                take.expected.isNotEmpty() && peak >= LevelAnalyzer.CLIP_LEVEL -> "Se saturó: alejá el celu o tocá más suave, y repetí la toma."
                else -> null
            }
            // Se guarda igual (también las tomas con aviso): Fede decide si repite.
            bank.save(piano, take, buffer.samples, buffer.size, source.name, sampleRate)
            recorded = bank.recordedIds(piano)
            result = if (problem != null) TakeResult(false, "Guardada, pero: $problem")
            else TakeResult(true, "Guardada. Nivel máximo: ${peakDb.toInt()} dB.")
        }
    }

    fun share() {
        val file = File(context.cacheDir, "exports/audios-de-prueba-${piano.folder}.zip")
        val count = bank.exportZip(piano, file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Audios de prueba (${piano.label}, $count tomas)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir las grabaciones"))
    }

    Box(modifier = modifier.fillMaxSize().background(PaperColor), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = { cancel(); onBack() }) { Text("‹ Menú", fontSize = 12.sp, color = InkColor.copy(alpha = 0.65f)) }
            Text("Grabar audios de prueba", color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in PianoKind.entries) {
                    val selected = kind == piano
                    Button(
                        enabled = !busy,
                        onClick = { piano = kind; recorded = bank.recordedIds(kind); result = null },
                        colors = if (selected) ButtonDefaults.buttonColors(containerColor = InkColor, contentColor = PaperColor)
                        else ButtonDefaults.buttonColors(containerColor = InkColor.copy(alpha = 0.1f), contentColor = InkColor),
                    ) { Text(kind.label) }
                }
            }
            Text(
                "Grabadas con este piano: ${recorded.size} de ${takes.size}",
                color = InkColor.copy(alpha = 0.75f), fontSize = 13.sp,
            )

            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkColor.copy(alpha = 0.07f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Toma ${index + 1} de ${takes.size} · ${take.group}" + if (take.id in recorded) "  ✓ grabada" else "",
                    color = InkColor.copy(alpha = 0.7f), fontSize = 12.sp,
                )
                Text(take.instruction, color = InkColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                val notes = take.expected.flatten().distinct()
                if (notes.isNotEmpty() && take.expected.size == 1) {
                    Text("Notas: " + notes.joinToString(" ") { RecordingScript.noteName(it) }, color = InkColor.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                Text("Dura ${take.seconds} segundos. Empezá a tocar cuando termine la cuenta.", color = InkColor.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            LiveLevelBlock(live)

            when (phase) {
                Phase.COUNTDOWN -> Text("$countdown", color = InkColor, fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Phase.RECORDING -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Grabando… tocá ahora", color = BadColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = BadColor)
                }
                Phase.IDLE -> result?.let {
                    Text(it.text, color = if (it.ok) GoodColor else BadColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !busy && index > 0, onClick = { index--; result = null }) { Text("‹ Anterior") }
                if (busy) {
                    Button(onClick = { cancel() }, colors = ButtonDefaults.buttonColors(containerColor = BadColor, contentColor = PaperColor)) { Text("Cancelar") }
                } else {
                    Button(onClick = { record() }, colors = ButtonDefaults.buttonColors(containerColor = InkColor, contentColor = PaperColor)) {
                        Text(if (take.id in recorded) "Repetir" else "Grabar")
                    }
                }
                OutlinedButton(enabled = !busy && index < takes.lastIndex, onClick = { index++; result = null }) { Text("Siguiente ›") }
            }

            OutlinedButton(enabled = !busy && recorded.isNotEmpty(), onClick = { share() }) {
                Text("Compartir las grabaciones de este piano (.zip)")
            }
            Text(
                "Las grabaciones se quedan solo en tu celu y solo se hacen cuando tocás «Grabar». Cuando termines con un piano, compartí el .zip y mandámelo.",
                color = InkColor.copy(alpha = 0.6f), fontSize = 12.sp,
            )
        }
    }
}
