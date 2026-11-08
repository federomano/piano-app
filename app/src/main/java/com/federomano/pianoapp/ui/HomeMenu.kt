package com.federomano.pianoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.federomano.pianoapp.library.ImportLine
import com.federomano.pianoapp.library.LibraryEntry
import com.federomano.pianoapp.library.subtitleOf
import com.federomano.pianoapp.score.ExampleScore
import com.federomano.pianoapp.score.ExampleScores
import com.federomano.pianoapp.score.Score

/**
 * Menú de inicio: "Mis partituras" (las importadas, que se pueden renombrar y borrar), un botón para
 * abrir archivos y los ejemplos que trae la app. Tocar una partitura la abre.
 */
@Composable
fun HomeMenu(
    entries: List<LibraryEntry>,
    onOpenExample: (ExampleScore) -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    onPickFiles: () -> Unit,
    onRename: (id: String, title: String) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<LibraryEntry?>(null) }
    var deleting by remember { mutableStateOf<LibraryEntry?>(null) }

    Box(modifier = modifier.fillMaxSize().background(PaperColor), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis partituras", color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onPickFiles,
                        colors = ButtonDefaults.buttonColors(containerColor = InkColor, contentColor = PaperColor),
                    ) { Text("Abrir archivo…") }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Todavía no tenés partituras tuyas. Tocá «Abrir archivo» o compartí un archivo .json desde Drive, Descargas o WhatsApp.",
                        color = InkColor.copy(alpha = 0.65f), fontSize = 13.sp,
                    )
                }
            }
            items(entries, key = { "mine-" + it.id }) { entry ->
                ScoreCard(
                    title = entry.score.title,
                    subtitle = subtitleOf(entry.score.composer, entry.score.level, entry.seconds),
                    onClick = { onOpenEntry(entry) },
                    menu = { close ->
                        DropdownMenuItem(text = { Text("Renombrar") }, onClick = { close(); renaming = entry })
                        DropdownMenuItem(text = { Text("Borrar") }, onClick = { close(); deleting = entry })
                    },
                )
            }
            item {
                Text("Ejemplos", color = InkColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            items(ExampleScores.all, key = { "ex-" + it.id }) { example ->
                ScoreCard(example.title, example.description, onClick = { onOpenExample(example) })
            }
        }
    }

    renaming?.let { entry ->
        var text by remember(entry.id) { mutableStateOf(entry.score.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { onRename(entry.id, text); renaming = null }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar «${entry.score.title}»?") },
            text = { Text("Se borra de la app. Si todavía tenés el archivo original, podés volver a abrirlo.") },
            confirmButton = { TextButton(onClick = { onDelete(entry.id); deleting = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ScoreCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkColor.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (menu != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = InkColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = InkColor.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (menu != null) {
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("⋮", color = InkColor, fontSize = 20.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
            }
        }
    }
}

/** Qué pasó con cada archivo que se intentó importar. */
@Composable
fun ImportReportDialog(lines: List<ImportLine>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lines.size == 1) "Archivo importado" else "Archivos importados") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lines) { line ->
                    Column {
                        Text((if (line.ok) "✓ " else "✗ ") + line.name, fontWeight = FontWeight.SemiBold)
                        Text(line.summary, fontSize = 13.sp)
                        line.problems.forEach { Text("• $it", fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

/** Una partitura lista para abrir en el reproductor: [key] identifica cuál es. */
class OpenScore(val key: String, val score: () -> Score)
