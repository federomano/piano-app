package com.federomano.pianoapp

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.federomano.pianoapp.score.ExampleScore
import com.federomano.pianoapp.ui.HomeMenu
import com.federomano.pianoapp.ui.InkColor
import com.federomano.pianoapp.ui.PaperColor
import com.federomano.pianoapp.ui.ScoreView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // La pantalla no se apaga mientras la app está abierta.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Usar también la zona de la cámara frontal del Pixel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        hideSystemBars()

        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(version = version)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Al volver de otra app o de un diálogo, las barras vuelven a ocultarse.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun HomeScreen(version: String) {
    // null = menú de inicio; si no, la partitura abierta. El botón Atrás del celu vuelve al menú.
    var open by remember { mutableStateOf<ExampleScore?>(null) }
    BackHandler(enabled = open != null) { open = null }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColor)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        val selected = open
        if (selected == null) {
            HomeMenu(onOpen = { open = it })
        } else {
            key(selected.id) {
                val score = remember { selected.create() }
                ScoreView(score = score, modifier = Modifier.fillMaxSize())
            }
            TextButton(onClick = { open = null }, modifier = Modifier.align(Alignment.TopStart).height(32.dp)) {
                Text("‹ Menú", fontSize = 11.sp, color = InkColor.copy(alpha = 0.65f))
            }
        }
        Text(
            text = "Versión $version",
            color = InkColor.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}
