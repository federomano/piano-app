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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.federomano.pianoapp.audio.MicCapture

internal fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Lo que sale de la captura del micrófono, listo para mostrar en pantalla: nivel, estado y fuente. Quien quiera además los
 * datos crudos (para grabar una toma) se engancha con [tap], que se llama desde el hilo de captura.
 */
class LiveMic {
    val reading = mutableStateOf<LevelReading?>(null)
    val state = mutableStateOf(MicCapture.State.INTERRUPTED)
    val source = mutableStateOf<Pair<MicCapture.Source, MicCapture.Fallback?>?>(null)

    @Volatile var tap: ((ShortArray, Int) -> Unit)? = null
    @Volatile private var analyzer = LevelAnalyzer(MicCapture.SAMPLE_RATE)
    private val main = Handler(Looper.getMainLooper())

    val listener = object : MicCapture.Listener {
        override fun onSamples(samples: ShortArray, count: Int) {
            val r = analyzer.process(samples, count)
            tap?.invoke(samples, count)
            main.post { reading.value = r }
        }

        override fun onState(state: MicCapture.State, source: MicCapture.Source?, fallback: MicCapture.Fallback?) {
            main.post {
                this@LiveMic.state.value = state
                if (source != null) this@LiveMic.source.value = source to fallback
            }
        }
    }

    fun reset() {
        analyzer = LevelAnalyzer(MicCapture.SAMPLE_RATE)
        reading.value = null
    }
}

/**
 * Mantiene la captura del micrófono corriendo mientras la pantalla está a la vista. Si Android manda la app a segundo plano
 * (llamada, otra app) se corta, y al volver se reanuda sola. Al salir de la pantalla se apaga.
 */
@Composable
fun MicCaptureEffect(live: LiveMic) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle ?: return@DisposableEffect onDispose {}
        val capture = MicCapture(context)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { live.reset(); capture.start(live.listener) }
                Lifecycle.Event.ON_PAUSE -> { capture.stop(); live.reading.value = null }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            capture.stop()
        }
    }
}

/**
 * Pide el permiso de micrófono explicando para qué, y recién con el permiso concedido muestra [content].
 * Si el permiso quedó bloqueado, manda a los ajustes de Android.
 */
@Composable
fun MicPermissionGate(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(granted()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPermission = ok
        denied = !ok
    }
    // Al volver de los ajustes de Android el permiso puede haber cambiado.
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hasPermission = granted() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    if (hasPermission) {
        content()
        return
    }
    Box(modifier = modifier.fillMaxSize().background(PaperColor), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Menú", fontSize = 12.sp, color = InkColor.copy(alpha = 0.65f)) }
            Text(title, color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val blocked = denied && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            Text(
                "Para escuchar tu piano, la app necesita usar el micrófono. Solo analiza el sonido en el momento: no graba ni guarda nada por su cuenta.",
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
        }
    }
}
