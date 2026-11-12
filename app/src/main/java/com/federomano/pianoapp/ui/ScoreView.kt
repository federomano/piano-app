package com.federomano.pianoapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.SideEffect
import com.federomano.pianoapp.audio.Metronome
import com.federomano.pianoapp.score.layout.NoteState
import com.federomano.pianoapp.score.layout.NoteStates
import com.federomano.pianoapp.score.layout.Zoom
import com.federomano.pianoapp.audio.NoteTrigger
import com.federomano.pianoapp.audio.PianoMapping
import com.federomano.pianoapp.audio.PianoPlayer
import com.federomano.pianoapp.score.Hand
import com.federomano.pianoapp.score.Loop
import com.federomano.pianoapp.score.Note
import com.federomano.pianoapp.score.Score
import com.federomano.pianoapp.score.playedNotes
import com.federomano.pianoapp.score.velocityOf
import com.federomano.pianoapp.score.Speed
import com.federomano.pianoapp.score.TempoMap
import com.federomano.pianoapp.score.layout.DrawOp
import com.federomano.pianoapp.score.layout.Geometry
import com.federomano.pianoapp.score.layout.GlyphOp
import com.federomano.pianoapp.score.layout.HandBands
import com.federomano.pianoapp.score.layout.PolygonOp
import com.federomano.pianoapp.score.layout.RectOp
import com.federomano.pianoapp.score.layout.ScoreLayout
import com.federomano.pianoapp.score.layout.TextOp

val PaperColor = Color(0xFFF6F3EA)
val InkColor = Color(0xFF1A1A1A)

val PlayheadColor = Color(0xFFB3261E)

/** Al empezar a reproducir, cuántos ticks hacia atrás se mira para no perder la nota que está justo en la línea. */
private const val SOUND_START_TOLERANCE_TICKS = 24f

/** En pausa, velocidad mínima (espacios por segundo) con la que soltar el dedo deja a la partitura deslizándose. */
private const val MIN_FLING_SPEED = 1.5f

/** Cuánto se ve la mano silenciada cuando se atenúa. */
private const val MUTED_HAND_ALPHA = 0.28f

/** Alto que ocupan los controles de reproducción abajo (botones, barra y el texto del compás), en dp. */
private const val CONTROLS_RESERVE_DP = 60f

/** Cuánto se atenúa una nota que ya pasó (1 = igual que antes). */
private const val PASSED_ALPHA = 0.35f

private val CurrentNoteColor = Color(0xFF1565C0)
private val HitNoteColor = Color(0xFF2E7D32)
private val MissNoteColor = Color(0xFFD84315)

/** Color de una nota según su estado; null = el negro de siempre. */
private fun noteColor(state: NoteState): Int? = when (state) {
    NoteState.CURRENT -> CurrentNoteColor.toArgb()
    NoteState.HIT -> HitNoteColor.toArgb()
    NoteState.MISS -> MissNoteColor.toArgb()
    NoteState.PENDING, NoteState.PASSED -> null
}

/** Qué tan rápido se acerca la inercia a la velocidad de reproducción (por segundo): más chico, frena más despacio. */
internal const val FLING_RATE = 3.8f

/** La inercia termina cuando la velocidad ya está así de cerca de la de reproducción (espacios por segundo). */
private const val FLING_END_SPEED = 0.4f

/**
 * Partitura de dos pentagramas en una línea continua. La clave, la armadura y el compás quedan fijos
 * a la izquierda, y la línea de reproducción queda fija cerca del 30% de la pantalla: la partitura se
 * desliza hacia la izquierda y la nota que toca pasa justo por esa línea.
 *
 * Un toque en la partitura reproduce o pausa. En pausa se puede arrastrar con el dedo para cambiar la
 * posición. Abajo, siempre visibles, quedan el botón de reiniciar y la barra de progreso (tocar un
 * compás salta hasta ahí).
 */
@Composable
fun ScoreView(
    score: Score,
    modifier: Modifier = Modifier,
    maxStaffSpaceDp: Float = 17f,
    /** Pintar notas sueltas con un estado (acierto, error...): lo usa el modo escucha. Sin entrada, la nota se ve según la reproducción. */
    noteStates: Map<Note, NoteState> = emptyMap(),
) {
    val context = LocalContext.current
    val layout = remember(score) { ScoreLayout.build(score) }
    val markHands = remember(layout) { layout.marks.map { HandBands.of(it) } }
    val musicFont = remember { Typeface.createFromAsset(context.assets, "fonts/Bravura.otf") }
    // NaN = todavía no se movió: se queda en el comienzo de la pieza.
    var scrollSp by remember { mutableFloatStateOf(Float.NaN) }
    var playing by remember { mutableStateOf(false) }
    // Sube cada vez que la posición cambia por un salto (barra, reiniciar): la reproducción retoma desde ahí.
    var seek by remember { mutableIntStateOf(0) }
    val tempoMap = remember(score) { TempoMap(score.tempos) }
    // Velocidad de reproducción (1 = tempo de la partitura). Se puede cambiar mientras suena.
    var speed by remember { mutableFloatStateOf(1f) }
    val currentSpeed by rememberUpdatedState(speed)

    // Sonido: el piano se carga en segundo plano al abrir la partitura. Las notas suenan según el tiempo
    // musical exacto (el reloj del tempo), aunque el dibujo, que se mueve suavizado, llegue a la línea
    // unas milésimas después en los cambios de compás o de armadura.
    val prefs = remember { context.getSharedPreferences("piano", android.content.Context.MODE_PRIVATE) }
    var muted by remember { mutableStateOf(prefs.getBoolean("muted", false)) }
    var bass by remember { mutableStateOf(prefs.getInt("bass", PianoMapping.DEFAULT_BASS).coerceIn(0, PianoMapping.BASS_LEVELS - 1)) }
    val player = remember { PianoPlayer(context).also { it.muted = muted; it.bass = bass } }
    LaunchedEffect(bass) {
        player.bass = bass
        prefs.edit().putInt("bass", bass).apply()
    }
    LaunchedEffect(muted) {
        player.muted = muted
        prefs.edit().putBoolean("muted", muted).apply()
    }
    DisposableEffect(player) {
        player.start()
        onDispose { player.stop() }
    }
    val trigger = remember(score) { NoteTrigger(score.playedNotes()) }

    // Practicar una mano sola (PIA-22): cada mano se puede silenciar; la silenciada se atenúa en pantalla (opcional).
    var rightMuted by remember(score) { mutableStateOf(false) }
    var leftMuted by remember(score) { mutableStateOf(false) }
    var dimMuted by remember { mutableStateOf(prefs.getBoolean("dimMuted", true)) }
    LaunchedEffect(dimMuted) { prefs.edit().putBoolean("dimMuted", dimMuted).apply() }
    val currentRightMuted by rememberUpdatedState(rightMuted)
    val currentLeftMuted by rememberUpdatedState(leftMuted)
    val playNote: (Note) -> Int = { note ->
        val silenced = if (note.hand == Hand.RIGHT) currentRightMuted else currentLeftMuted
        if (silenced) -1 else player.noteOn(note.pitch.midi, score.velocityOf(note))
    }

    // Ayudas para leer (PIA-18): nombre de las notas y dedos, cada una con su interruptor.
    var showNames by remember { mutableStateOf(prefs.getBoolean("showNames", false)) }
    var showFingers by remember { mutableStateOf(prefs.getBoolean("showFingers", false)) }
    LaunchedEffect(showNames) { prefs.edit().putBoolean("showNames", showNames).apply() }
    LaunchedEffect(showFingers) { prefs.edit().putBoolean("showFingers", showFingers).apply() }
    val noteLabels = remember(layout, showNames, showFingers) { layout.labels(showNames, showFingers) }
    val hasFingers = remember(score) { score.notes.any { it.finger != null } }

    // Nota actual (PIA-16): la que suena (o la que viene) se pinta de color y lo ya tocado se atenúa.
    var highlight by remember { mutableStateOf(prefs.getBoolean("highlight", true)) }
    LaunchedEffect(highlight) { prefs.edit().putBoolean("highlight", highlight).apply() }
    val markSpan = remember(layout) {
        IntArray(layout.marks.size) { -1 }.also { map -> for ((k, span) in layout.chordSpans.withIndex()) for (i in span.from until span.to) map[i] = k }
    }
    val markHead = remember(layout) {
        HashMap<Int, Note>().also { map -> for (span in layout.chordSpans) for ((note, i) in span.heads) map[i] = note }
    }
    val longestChord = remember(layout) { NoteStates.longestOf(layout.chordSpans) }

    // Tamaño de la partitura (PIA-17).
    var zoom by remember { mutableFloatStateOf(prefs.getFloat("zoom", Zoom.DEFAULT)) }
    LaunchedEffect(zoom) { prefs.edit().putFloat("zoom", zoom).apply() }
    var zoomLimit by remember { mutableFloatStateOf(Zoom.MAX) }

    // Metrónomo y cuenta (PIA-23): el clic cae en cada tiempo según la firma de compás; la cuenta es un compás de clics
    // antes de arrancar. El volumen del clic es aparte del piano.
    var metronome by remember { mutableStateOf(prefs.getBoolean("metronome", false)) }
    var countIn by remember { mutableStateOf(prefs.getBoolean("countIn", false)) }
    var clickVolume by remember { mutableFloatStateOf(prefs.getFloat("clickVolume", PianoPlayer.DEFAULT_CLICK_VOLUME)) }
    LaunchedEffect(metronome) { prefs.edit().putBoolean("metronome", metronome).apply() }
    LaunchedEffect(countIn) { prefs.edit().putBoolean("countIn", countIn).apply() }
    LaunchedEffect(clickVolume) {
        player.clickVolume = clickVolume
        prefs.edit().putFloat("clickVolume", clickVolume).apply()
    }
    val currentMetronome by rememberUpdatedState(metronome)
    val currentCountIn by rememberUpdatedState(countIn)
    val metro = remember(layout) { Metronome(layout.measures.map { it.measure }) }
    // Número del tiempo de la cuenta que se está haciendo (0 = no hay cuenta).
    var countInBeat by remember { mutableIntStateOf(0) }
    // La cuenta se hace una vez por cada vez que se pone play (no al saltar o arrastrar con la reproducción andando).
    val counted = remember { object { var done = false } }
    LaunchedEffect(playing) { if (!playing) counted.done = false }

    // Bucle A-B (PIA-24): se marcan dos compases sobre la barra; el tramo se repite mientras reproduce.
    var loopPicking by remember(score) { mutableStateOf(false) }
    var loopFirst by remember(score) { mutableStateOf<Int?>(null) }
    var loopMeasures by remember(score) { mutableStateOf<Pair<Int, Int>?>(null) }
    val measureStartTicks = remember(layout) { layout.measures.map { it.measure.startTick } }
    val loopTicks = loopMeasures?.let { (a, b) -> Loop.ticks(measureStartTicks, layout.endTick, a, b) }
    val currentLoop by rememberUpdatedState(loopTicks)

    BoxWithConstraints(modifier = modifier.background(PaperColor)) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Un espacio de pentagrama: lo que entre en el alto, sin pasarse de un tamaño cómodo de leer.
        // Los controles de reproducción tapan el borde de abajo: si la partitura llega hasta ahí, se achica un poco (PIA-72).
        val controlsPx = CONTROLS_RESERVE_DP * density
        val base = layout.fit(heightPx, maxStaffSpaceDp * density, controlsPx, noteLabels)
        // Tamaño a gusto (PIA-17): multiplica el tamaño justo; se recuerda.
        val zoomMax = Zoom.max(heightPx, controlsPx, base.spPx)
        val zoomNow = Zoom.clamp(zoom, zoomMax)
        SideEffect { zoomLimit = zoomMax }
        val fit = Zoom.apply(base, zoomNow, heightPx, controlsPx)
        val spPx = fit.spPx

        // Línea de reproducción: fija en pantalla. El desplazamiento mínimo deja el comienzo de la pieza
        // sobre la línea; el máximo, el final.
        val playheadSp = widthPx / spPx * Geometry.PLAYHEAD_FRACTION
        val minScroll = layout.scrollForTick(0f, playheadSp)
        val maxScroll = layout.scrollForTick(layout.endTick.toFloat(), playheadSp)
        val currentPlayhead by rememberUpdatedState(playheadSp)
        val currentMin by rememberUpdatedState(minScroll)

        // Al cambiar el tamaño (o girar el celular) la línea de reproducción cambia de lugar en espacios de pentagrama:
        // se reacomoda para que siga sobre la misma nota. Sonando, el avance ya lo hace solo.
        val lastPlayhead = remember { floatArrayOf(Float.NaN) }
        LaunchedEffect(playheadSp) {
            val before = lastPlayhead[0]
            if (!before.isNaN() && before != playheadSp && !scrollSp.isNaN()) {
                scrollSp = layout.scrollForTick(layout.tickAtScroll(scrollSp, before), playheadSp)
            }
            lastPlayhead[0] = playheadSp
        }

        // Arrastre tipo vinilo: mientras el dedo está abajo, o la partitura sigue deslizándose por inercia,
        // la reproducción se suspende ([holding]); al terminar retoma desde donde quedó, y si estaba en
        // pausa sigue en pausa.
        val scope = rememberCoroutineScope()
        var holding by remember { mutableStateOf(false) }
        var flingJob by remember { mutableStateOf<Job?>(null) }
        var stoppedFling by remember { mutableStateOf(false) }
        // Verdadero si la inercia ya venía tocando las notas y la reproducción sigue sin repetirlas.
        val sound = remember { object { var armed = false } }

        fun stopFling(): Boolean {
            val job = flingJob
            flingJob = null
            if (job?.isActive != true) return false
            job.cancel()
            holding = false
            return true
        }

        // Cuántos espacios por segundo avanza la partitura al reproducir en esa posición.
        fun playbackSpeedAt(position: Float): Float {
            val tick = layout.tickAtScroll(position, currentPlayhead)
            val step = 0.02f
            val next = tempoMap.advance(tick, step * currentSpeed)
            return (layout.scrollForTick(next, currentPlayhead) - layout.scrollForTick(tick, currentPlayhead)) / step
        }

        val draggable = rememberDraggableState { delta ->
            val now = if (scrollSp.isNaN()) minScroll else scrollSp
            scrollSp = (now - delta / spPx).coerceIn(minScroll, maxScroll)
        }

        // Reproducción: en cada cuadro avanza el tiempo según el tempo y la partitura se desliza para que
        // ese instante quede sobre la línea.
        LaunchedEffect(playing, holding, layout, seek) {
            if (!playing || holding) return@LaunchedEffect
            val end = layout.endTick.toFloat()
            val from = if (scrollSp.isNaN()) currentMin else scrollSp
            var tick = layout.tickAtScroll(from, currentPlayhead).coerceAtLeast(0f)
            if (tick >= end - 1f) tick = 0f
            if (!counted.done && currentCountIn) {
                // Cuenta de un compás: la partitura espera quieta; si se pausa o se salta antes de terminar, se vuelve a hacer.
                val signature = (layout.measures.lastOrNull { it.measure.startTick <= tick } ?: layout.measures.first()).measure.timeSignature
                val beats = Metronome.countInBeats(signature)
                val beatTicks = Metronome.beatTicks(signature)
                try {
                    var beat = 0
                    var waited = 0f
                    var before = withFrameNanos { it }
                    player.click(true)
                    countInBeat = 1
                    while (beat < beats) {
                        val now = withFrameNanos { it }
                        waited += ((now - before) / 1_000_000_000f).coerceAtMost(0.1f)
                        before = now
                        val beatSeconds = beatTicks / (tempoMap.ticksPerSecond(tick) * currentSpeed)
                        if (waited >= beatSeconds) {
                            waited -= beatSeconds
                            beat++
                            if (beat < beats) {
                                player.click(false)
                                countInBeat = beat + 1
                            }
                        }
                    }
                } finally {
                    countInBeat = 0
                }
                counted.done = true
            }
            // Un poco antes, para no perder la nota que justo está en la línea al retomar o saltar.
            if (sound.armed) sound.armed = false else trigger.reset(tick - SOUND_START_TOLERANCE_TICKS)
            metro.reset(tick)
            var last = withFrameNanos { it }
            try {
                while (true) {
                    val now = withFrameNanos { it }
                    val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    last = now
                    val before = tick
                    tick = tempoMap.advance(tick, seconds * currentSpeed)
                    // Bucle: al llegar al final del tramo se vuelve a su inicio sin cortar la reproducción.
                    currentLoop?.let { (loopStart, loopEnd) ->
                        if (Loop.crossedEnd(before, tick, loopEnd)) {
                            trigger.releaseAll(player::noteOff)
                            tick = loopStart.toFloat()
                            trigger.reset(tick)
                            metro.reset(tick)
                        }
                    }
                    if (tick >= end) {
                        scrollSp = layout.scrollForTick(end, currentPlayhead)
                        trigger.advance(Float.MAX_VALUE, playNote, player::noteOff)
                        playing = false
                        break
                    }
                    scrollSp = layout.scrollForTick(tick, currentPlayhead)
                    trigger.advance(tick, playNote, player::noteOff)
                    // Apagado también avanza, así al prenderlo en medio no suenan de golpe los clics atrasados.
                    metro.advance(tick) { accent -> if (currentMetronome) player.click(accent) }
                }
            } finally {
                // Pausa, salto o arrastre: lo que suena se apaga suavemente.
                trigger.releaseAll(player::noteOff)
            }
        }

        val musicPaint = remember(musicFont) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = musicFont
                color = InkColor.toArgb()
            }
        }
        val textPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.SANS_SERIF
                color = InkColor.toArgb()
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Tocar durante la inercia la frena en seco, sin cambiar play ni pausa.
                        onPress = { stoppedFling = stopFling() },
                        onTap = { if (!stoppedFling) playing = !playing },
                    )
                }
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        stopFling()
                        holding = true
                    },
                    onDragStopped = { velocityPx ->
                        // La partitura se mueve al revés que el dedo; la velocidad se pasa a espacios por segundo.
                        val velocity = -velocityPx / spPx
                        if (!playing && kotlin.math.abs(velocity) < MIN_FLING_SPEED) {
                            holding = false
                        } else {
                            flingJob = scope.launch {
                                // Inercia: arranca a la velocidad con que se soltó y se va acercando, con
                                // frenado suave, a la velocidad de la reproducción (cero si está en pausa).
                                // Si venía para atrás mientras reproduce, frena, pasa por cero y arranca
                                // hacia adelante.
                                var position = if (scrollSp.isNaN()) minScroll else scrollSp
                                var v = velocity
                                var last = withFrameNanos { it }
                                var handOff = false
                                try {
                                while (true) {
                                    val now = withFrameNanos { it }
                                    val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                                    last = now
                                    val target = if (playing) playbackSpeedAt(position) else 0f
                                    v = Inertia.step(v, target, seconds, FLING_RATE)
                                    position += v * seconds
                                    if (position <= minScroll) {
                                        position = minScroll
                                        if (v < 0f) v = 0f
                                    }
                                    var atEnd = false
                                    if (position >= maxScroll) {
                                        position = maxScroll
                                        atEnd = true
                                    }
                                    scrollSp = position
                                    if (atEnd) {
                                        playing = false
                                        break
                                    }
                                    // Cuando ya va cerca del ritmo normal, hacia adelante, las notas empiezan a sonar
                                    // sin esperar a que termine el frenado.
                                    if (Inertia.soundsAt(v, target)) {
                                        val clock = layout.tickAtScroll(position, currentPlayhead)
                                        if (!sound.armed) {
                                            trigger.reset(clock - SOUND_START_TOLERANCE_TICKS)
                                            sound.armed = true
                                        }
                                        trigger.advance(clock, playNote, player::noteOff)
                                    } else if (sound.armed) {
                                        trigger.releaseAll(player::noteOff)
                                        sound.armed = false
                                    }
                                    if (kotlin.math.abs(v - target) < FLING_END_SPEED) {
                                        handOff = playing && sound.armed
                                        break
                                    }
                                }
                                } finally {
                                    // Si la reproducción sigue sola, ya tiene las notas en marcha; si no, se apaga todo.
                                    if (!handOff) {
                                        trigger.releaseAll(player::noteOff)
                                        sound.armed = false
                                    }
                                }
                                holding = false
                            }
                        }
                    },
                ),
        ) {
            val scroll = if (scrollSp.isNaN()) minScroll else scrollSp
            val top = fit.topPx
            val headerPx = layout.headerWidth * spPx

            fun paintOp(op: DrawOp, dx: Float, alpha: Float, color: Int? = null) {
                if (alpha <= 0f) return
                val native = drawContext.canvas.nativeCanvas
                if (color != null) { musicPaint.color = color; textPaint.color = color }
                val a = (alpha * 255).toInt().coerceIn(0, 255)
                when (op) {
                    is RectOp -> {
                        musicPaint.alpha = a
                        native.drawRect(
                            dx + op.x * spPx, top + op.y * spPx,
                            dx + (op.x + op.width) * spPx, top + (op.y + op.height) * spPx,
                            musicPaint,
                        )
                    }
                    is GlyphOp -> {
                        musicPaint.alpha = a
                        musicPaint.textSize = 4f * spPx * op.scale
                        native.drawText(String(Character.toChars(op.codepoint)), dx + op.x * spPx, top + op.y * spPx, musicPaint)
                    }
                    is PolygonOp -> {
                        musicPaint.alpha = a
                        val path = android.graphics.Path()
                        for (i in op.points.indices step 2) {
                            val px = dx + op.points[i] * spPx
                            val py = top + op.points[i + 1] * spPx
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        native.drawPath(path, musicPaint)
                    }
                    is TextOp -> {
                        textPaint.alpha = a
                        textPaint.textSize = op.size * spPx
                        native.drawText(op.text, dx + op.x * spPx, top + op.y * spPx, textPaint)
                    }
                }
                if (color != null) { musicPaint.color = InkColor.toArgb(); textPaint.color = InkColor.toArgb() }
            }

            fun paint(ops: List<DrawOp>, dx: Float, alpha: Float = 1f) = ops.forEach { paintOp(it, dx, alpha) }

            // 1) Líneas de los pentagramas, a la derecha del área fija.
            clipRect(left = headerPx, top = 0f, right = widthPx, bottom = heightPx) {
                paint(layout.staff, headerPx - scroll * spPx)
            }

            // 2) Área fija: líneas, llave y claves (no cambian nunca), y encima la armadura y el compás.
            paint(layout.fixed, 0f)
            for (draw in layout.clefDraws(scroll)) paint(draw.ops, draw.dx * spPx, draw.alpha)
            for (draw in layout.headerDraws(scroll, widthPx / spPx)) paint(draw.ops, draw.dx * spPx, draw.alpha)

            // 3) Barras de compás y números: pasan por encima del área fija y se desvanecen
            //    al acercarse a la clave.
            val fadeEnd = layout.signatureLeft
            val tickNow = layout.tickAtScroll(scroll, playheadSp)
            val currentChords = if (highlight && layout.chordSpans.isNotEmpty()) NoteStates.current(layout.chordSpans, tickNow, longestChord) else emptySet()
            for ((i, op) in layout.marks.withIndex()) {
                val x = (when (op) { is RectOp -> op.x; is TextOp -> op.x; is GlyphOp -> op.x; is PolygonOp -> op.minX }) - scroll + layout.headerWidth
                if (x < 0f || x > widthPx / spPx) continue
                var alpha = ((x - fadeEnd) / (layout.headerWidth - fadeEnd)).coerceIn(0f, 1f)
                if (dimMuted) {
                    val hand = markHands[i]
                    if ((hand == HandBands.RIGHT && rightMuted) || (hand == HandBands.LEFT && leftMuted)) alpha *= MUTED_HAND_ALPHA
                }
                // Color según el estado de la nota: la que toca va de color, lo ya tocado se atenúa; el modo escucha puede pintar cabezas sueltas.
                var state = NoteState.PENDING
                val span = markSpan[i]
                if (highlight && span >= 0) state = NoteStates.playback(layout.chordSpans, span, tickNow, currentChords)
                val forced = markHead[i]?.let { noteStates[it] }
                if (forced != null) state = forced
                paintOp(op, (layout.headerWidth - scroll) * spPx, if (state == NoteState.PASSED) alpha * PASSED_ALPHA else alpha, noteColor(state))
            }

            // 3b) Nombres de nota y dedos: viajan con la partitura, se desvanecen igual y siguen a su mano si está atenuada.
            for (label in noteLabels) {
                val x = label.op.x - scroll + layout.headerWidth
                if (x < 0f || x > widthPx / spPx) continue
                var alpha = ((x - fadeEnd) / (layout.headerWidth - fadeEnd)).coerceIn(0f, 1f)
                if (dimMuted && ((label.hand == Hand.RIGHT && rightMuted) || (label.hand == Hand.LEFT && leftMuted))) alpha *= MUTED_HAND_ALPHA
                paintOp(label.op, (layout.headerWidth - scroll) * spPx, alpha)
            }

            // 4) Línea de reproducción, siempre en el mismo lugar de la pantalla.
            val lineX = playheadSp * spPx
            drawLine(
                color = PlayheadColor.copy(alpha = 0.65f),
                start = Offset(lineX, top - 0.6f * spPx),
                end = Offset(lineX, top + (Geometry.SYSTEM_BOTTOM + 0.6f) * spPx),
                strokeWidth = 0.14f * spPx,
            )
        }

        PlaybackControls(
            measureStarts = layout.measures.map { it.measure.startTick },
            firstMeasureNumber = layout.measures.first().measure.number,
            endTick = layout.endTick,
            currentTick = { layout.tickAtScroll(if (scrollSp.isNaN()) minScroll else scrollSp, playheadSp) },
            onSeek = { tick ->
                if (loopPicking) {
                    // Marcando el bucle: el toque elige el compás en vez de saltar.
                    val index = measureStartTicks.indexOfLast { it <= tick }.coerceAtLeast(0)
                    val first = loopFirst
                    if (first == null) {
                        loopFirst = index
                    } else {
                        val range = Loop.ticks(measureStartTicks, layout.endTick, first, index)
                        loopMeasures = minOf(first, index) to maxOf(first, index)
                        loopPicking = false
                        loopFirst = null
                        stopFling()
                        scrollSp = layout.scrollForTick(range.first.toFloat(), playheadSp)
                        seek++
                    }
                } else {
                    stopFling()
                    scrollSp = layout.scrollForTick(tick.toFloat(), playheadSp)
                    seek++
                }
            },
            loopStartTick = loopTicks?.first,
            loopEndTick = loopTicks?.second,
            loopPicking = loopPicking,
            loopFirstLabel = loopFirst?.let { Loop.label(it, layout.measures.first().measure.number) },
            onLoopButton = {
                when {
                    loopPicking -> { loopPicking = false; loopFirst = null }
                    loopMeasures != null -> loopMeasures = null
                    else -> loopPicking = true
                }
            },
            rightMuted = rightMuted,
            leftMuted = leftMuted,
            onRightMuted = { rightMuted = !rightMuted },
            onLeftMuted = { leftMuted = !leftMuted },
            dimMuted = dimMuted,
            onDimMuted = { dimMuted = !dimMuted },
            onRestart = {
                stopFling()
                scrollSp = minScroll
                seek++
            },
            speed = speed,
            onSpeed = { speed = Speed.snap(it) },
            bpm = {
                val tick = layout.tickAtScroll(if (scrollSp.isNaN()) minScroll else scrollSp, playheadSp)
                Speed.effectiveBpm(tempoMap.bpmAt(tick), speed)
            },
            muted = muted,
            onMuteToggle = { muted = !muted },
            bass = bass,
            onBass = { bass = it },
            zoom = Zoom.clamp(zoom, zoomLimit),
            zoomMax = zoomLimit,
            onZoom = { zoom = it },
            highlight = highlight,
            onHighlight = { highlight = !highlight },
            showNames = showNames,
            onShowNames = { showNames = !showNames },
            showFingers = showFingers,
            hasFingers = hasFingers,
            onShowFingers = { showFingers = !showFingers },
            metronome = metronome,
            onMetronome = { metronome = !metronome },
            countIn = countIn,
            onCountIn = { countIn = !countIn },
            clickVolume = clickVolume,
            onClickVolume = { clickVolume = it },
        )
        if (countInBeat > 0) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Text("$countInBeat", color = PlayheadColor.copy(alpha = 0.75f), fontSize = 96.sp)
            }
        }
    }
}
