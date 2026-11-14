package com.federomano.pianoapp.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/** Dónde cae un nivel en dB sobre el medidor: -60 dB (izquierda) a 0 dB (derecha). */
fun meterFraction(db: Float): Float = ((db - METER_MIN_DB) / -METER_MIN_DB).coerceIn(0f, 1f)

private const val METER_MIN_DB = -60f
private val GoodColor = Color(0xFF2E7D32)
private val WarnColor = Color(0xFFF9A825)
private val BadColor = Color(0xFFC62828)

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Probar el micrófono (PIA-30): explica para qué se usa, pide el permiso y muestra con qué nivel la app escucha el piano.
 * El audio solo se analiza en el momento: no se graba ni se guarda.
 */
@Composable
fun MicTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(granted()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPermission = ok
        denied = !ok
    }

    val reading = remember { mutableStateOf<LevelReading?>(null) }
    val captureState = remember { mutableStateOf(MicCapture.State.INTERRUPTED) }
    val sourceState = remember { mutableStateOf<MicCapture.Source?>(null) }

    // Al volver de los ajustes de Android el permiso puede haber cambiado.
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hasPermission = granted() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // La captura corre solo con esta pantalla a la vista y el permiso concedido. Si Android manda la app a segundo plano
    // (llamada, otra app) se corta, y al volver se reanuda sola. Al salir de la pantalla se apaga.
    DisposableEffect(hasPermission, activity) {
        val lifecycle = activity?.lifecycle
        if (!hasPermission || lifecycle == null) return@DisposableEffect onDispose {}
        val main = Handler(Looper.getMainLooper())
        val analyzer = LevelAnalyzer(MicCapture.SAMPLE_RATE)
        val capture = MicCapture(context)
        val listener = object : MicCapture.Listener {
            override fun onSamples(samples: ShortArray, count: Int) {
                val r = analyzer.process(samples, count)
                main.post { reading.value = r }
            }

            override fun onState(state: MicCapture.State, source: MicCapture.Source?) {
                main.post {
                    captureState.value = state
                    if (source != null) sourceState.value = source
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { analyzer.reset(); capture.start(listener) }
                Lifecycle.Event.ON_PAUSE -> { capture.stop(); main.post { reading.value = null } }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            capture.stop()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(PaperColor), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Menú", fontSize = 12.sp, color = InkColor.copy(alpha = 0.65f)) }
            Text("Probar el micrófono", color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!hasPermission) {
                val blocked = denied && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
                Text(
                    "Para escuchar tu piano, la app necesita usar el micrófono. Solo analiza el sonido en el momento: no graba ni guarda nada.",
                    color = InkColor, fontSize = 15.sp,
                )
                if (blocked) {
                    Text(
                        "El permiso quedó bloqueado. Se activa desde los ajustes de Android: Permisos → Micrófono → Permitir solo mientras usás la app.",
                        color = InkColor.copy(alpha = 0.75f), fontSize = 13.sp,
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InkColor, contentColor = PaperColor),
                    ) { Text("Abrir los ajustes de la app") }
                } else {
                    if (denied) Text("Sin el permiso la app no puede escuchar. Podés volver a pedirlo.", color = InkColor.copy(alpha = 0.75f), fontSize = 13.sp)
                    Button(
                        onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = InkColor, contentColor = PaperColor),
                    ) { Text(if (denied) "Pedir el permiso de nuevo" else "Permitir el micrófono") }
                }
            } else {
                val current = reading.value
                val state = captureState.value
                val status = current?.status ?: LevelStatus.SILENT
                val color = when (status) {
                    LevelStatus.GOOD -> GoodColor
                    LevelStatus.CLIPPING -> BadColor
                    else -> InkColor.copy(alpha = 0.55f)
                }
                Text("Tocá el piano y mirá la barra: tiene que quedar en la zona del medio.", color = InkColor.copy(alpha = 0.75f), fontSize = 13.sp)
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
                sourceState.value?.let {
                    Text("Captura: ${it.label}.", color = InkColor.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

/** Barra de nivel: el pico actual y una marca con el pico más alto de los últimos segundos. Las zonas son las del aviso. */
@Composable
private fun LevelMeter(reading: LevelReading?, fillColor: Color) {
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
