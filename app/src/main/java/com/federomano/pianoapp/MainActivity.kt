package com.federomano.pianoapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import com.federomano.pianoapp.library.ImportLine
import com.federomano.pianoapp.library.ImportResult
import com.federomano.pianoapp.library.LibraryEntry
import com.federomano.pianoapp.library.ScoreLibrary
import com.federomano.pianoapp.library.directOpenEntry
import com.federomano.pianoapp.ui.ImportReportDialog
import com.federomano.pianoapp.ui.OpenScore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
import com.federomano.pianoapp.ui.HomeMenu
import com.federomano.pianoapp.ui.InkColor
import com.federomano.pianoapp.ui.PaperColor
import com.federomano.pianoapp.ui.ScoreView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    // Las partituras importadas viven en la carpeta privada de la app: sobreviven a las versiones nuevas.
    private lateinit var library: ScoreLibrary
    private val entries = mutableStateListOf<LibraryEntry>()
    private val report = mutableStateOf<List<ImportLine>?>(null)
    // null = menú de inicio; si no, la partitura abierta. Vive acá para poder abrirla al recibir un archivo.
    private val open = mutableStateOf<OpenScore?>(null)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        library = ScoreLibrary(File(filesDir, "library"))
        refreshLibrary()
        if (savedInstanceState == null) handleIncoming(intent)

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
                HomeScreen(
                    version = version,
                    entries = entries,
                    open = open.value,
                    onOpenChange = { open.value = it },
                    report = report.value,
                    onDismissReport = { report.value = null },
                    onPickFiles = { picker.launch(arrayOf("*/*")) },
                    onRename = { id, title -> library.rename(id, title); refreshLibrary() },
                    onDelete = { id -> library.delete(id); refreshLibrary() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun refreshLibrary() {
        entries.clear()
        entries.addAll(library.list())
    }

    /** "Abrir con" y "Compartir" desde otras apps: uno o varios archivos, o texto. */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
            Intent.ACTION_SEND_MULTIPLE -> streamsExtra(intent)
            else -> return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        when {
            uris.isNotEmpty() -> importUris(uris)
            !text.isNullOrBlank() -> finishImport(listOf(ImportLine("Texto compartido", library.import(text))))
        }
        // Para no volver a importar lo mismo si la actividad se recrea.
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamsExtra(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()

    private fun importUris(uris: List<Uri>) {
        finishImport(uris.map { uri ->
            val name = displayName(uri)
            val result = try {
                library.import(readText(uri))
            } catch (e: IOException) {
                ImportResult.Failed(listOf("No se pudo leer el archivo."))
            } catch (e: SecurityException) {
                ImportResult.Failed(listOf("No se pudo leer el archivo: la app de origen no dio permiso."))
            }
            ImportLine(name, result)
        })
    }

    private fun finishImport(lines: List<ImportLine>) {
        refreshLibrary()
        // Un solo archivo que entró bien: se abre directo, listo para tocar. Si no, se muestra el informe.
        val entry = directOpenEntry(lines)
        if (entry != null) {
            report.value = null
            open.value = OpenScore("mine-" + entry.id + "-" + entry.modified) { entry.score }
        } else {
            report.value = lines
        }
    }

    /** Lee el archivo completo, hasta un poco más del tope; si se pasa, la biblioteca lo rechaza por grande. */
    private fun readText(uri: Uri): String {
        val out = ByteArrayOutputStream()
        val stream = contentResolver.openInputStream(uri) ?: throw IOException("sin contenido")
        stream.use {
            val buffer = ByteArray(8192)
            while (out.size() <= ScoreLibrary.MAX_BYTES) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "archivo"
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
private fun HomeScreen(
    version: String,
    entries: List<LibraryEntry>,
    open: OpenScore?,
    onOpenChange: (OpenScore?) -> Unit,
    report: List<ImportLine>?,
    onDismissReport: () -> Unit,
    onPickFiles: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    // El botón Atrás del celu vuelve al menú.
    BackHandler(enabled = open != null) { onOpenChange(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColor)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        val selected = open
        if (selected == null) {
            HomeMenu(
                entries = entries,
                onOpenExample = { onOpenChange(OpenScore("ex-" + it.id, it.create)) },
                onOpenEntry = { entry -> onOpenChange(OpenScore("mine-" + entry.id + "-" + entry.modified) { entry.score }) },
                onPickFiles = onPickFiles,
                onRename = onRename,
                onDelete = onDelete,
            )
        } else {
            key(selected.key) {
                val score = remember { selected.score() }
                ScoreView(score = score, modifier = Modifier.fillMaxSize())
            }
            TextButton(onClick = { onOpenChange(null) }, modifier = Modifier.align(Alignment.TopStart).height(32.dp)) {
                Text("‹ Menú", fontSize = 11.sp, color = InkColor.copy(alpha = 0.65f))
            }
        }
        if (report != null) ImportReportDialog(report, onDismissReport)
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
