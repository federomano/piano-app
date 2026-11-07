package com.federomano.pianoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.federomano.pianoapp.audio.PianoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock

private class Note(val midi: Int, val startMs: Long, val durationMs: Long)

private fun scale(): List<Note> =
    listOf(60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60).mapIndexed { i, m -> Note(m, i * 350L, 320L) }

private fun chord(): List<Note> = listOf(48, 55, 60, 64, 67, 72).map { Note(it, 0, 2500) }

/** Arpegio rápido por tres octavas (semicorcheas a 140 negras por minuto): el caso difícil para el audio. */
private fun arpeggio(): List<Note> {
    val step = 60000L / 140 / 4
    val shape = listOf(0, 4, 7, 12, 16, 19, 24, 28, 31, 28, 24, 19, 16, 12, 7, 4)
    return (0 until 48).map { Note(48 + shape[it % shape.size] + (it / 16) * 2, it * step, step * 3) }
}

/** Pantalla de prueba del sonido (PIA-8). Se saca cuando el sonido quede integrado en la partitura. */
@Composable
fun SoundLab(onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember { PianoPlayer(context) }
    DisposableEffect(player) { player.start(); onDispose { player.stop() } }

    var velocity by remember { mutableIntStateOf(90) }
    var status by remember { mutableStateOf("Cargando piano…") }
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(player) {
        while (true) {
            status = when {
                player.error != null -> "Error: ${player.error}"
                !player.ready -> "Cargando piano…"
                else -> "Listo. Cargó en ${player.loadMillis} ms. ${player.info}. Voces: ${player.activeVoices}. Cortes de audio: ${player.underruns}"
            }
            delay(250)
        }
    }

    fun play(notes: List<Note>) {
        playing?.cancel()
        playing = scope.launch { playSequence(player, notes, velocity) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF14110F)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { playing?.cancel(); onClose() }) { Text("← Volver") }
            Text("Prueba de sonido (PIA-8)", color = Color.White, fontSize = 16.sp)
        }
        Text(status, color = Color(0xFFB8B0A8), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Fuerza:", color = Color.White, fontSize = 13.sp)
            for ((label, v) in listOf("Suave" to 45, "Media" to 90, "Fuerte" to 120)) {
                FilterChip(selected = velocity == v, onClick = { velocity = v }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { play(scale()) }) { Text("Escala") }
            Button(onClick = { play(chord()) }) { Text("Acorde de 6 notas") }
            Button(onClick = { play(arpeggio()) }) { Text("Arpegio rápido") }
            Button(onClick = { playing?.cancel() }) { Text("Cortar") }
        }
        Text("Teclas (Do central a Do): tocá y soltá para sentir la demora", color = Color(0xFFB8B0A8), fontSize = 11.sp)
        Keyboard(player, { velocity }, Modifier.fillMaxWidth().weight(1f))
        Text(
            "Piano: Yamaha Disklavier Pro, grabado por Zenph Studios; soundfont de FreePats (Creative Commons Atribución 3.0)",
            color = Color(0xFF8A8279), fontSize = 9.sp,
        )
    }
}

private suspend fun playSequence(player: PianoPlayer, notes: List<Note>, velocity: Int) {
    val start = SystemClock.elapsedRealtime()
    // Eventos (tocar / soltar) ordenados por tiempo absoluto, así no se acumula demora.
    val events = notes.flatMap { listOf(Triple(it.startMs, it, true), Triple(it.startMs + it.durationMs, it, false)) }
        .sortedWith(compareBy({ it.first }, { it.third }))
    val ids = HashMap<Note, Int>()
    for ((time, note, on) in events) {
        val wait = start + time - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        if (on) ids[note] = player.noteOn(note.midi, velocity) else ids[note]?.let(player::noteOff)
    }
}

private val KEYS = listOf(60, 62, 64, 65, 67, 69, 71, 72)
private val NAMES = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si", "Do")

@Composable
private fun Keyboard(player: PianoPlayer, velocity: () -> Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KEYS.forEachIndexed { i, midi ->
            var down by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (down) Color(0xFFE0B04A) else Color(0xFFF2EDE4))
                    .pointerInput(midi) {
                        detectTapGestures(onPress = {
                            down = true
                            val id = player.noteOn(midi, velocity())
                            tryAwaitRelease()
                            player.noteOff(id)
                            down = false
                        })
                    },
                contentAlignment = Alignment.BottomCenter,
            ) { Text(NAMES[i], color = Color.Black, fontSize = 12.sp, modifier = Modifier.padding(6.dp)) }
        }
    }
}
