package com.federomano.pianoapp.audio

/** El piano que se está grabando. [folder] es el nombre de la carpeta de sus audios. */
enum class PianoKind(val folder: String, val label: String) {
    ACOUSTIC("acustico", "Piano acústico"),
    CASIO("casio", "Casio"),
}

/**
 * Una toma de la lista guiada de grabaciones de prueba (PIA-38): qué tocar y qué suena en ella. [expected] es una lista de
 * momentos en orden; cada momento es el conjunto de notas (MIDI) que suenan juntas. Vacía = silencio.
 * [seconds] es lo que dura la grabación después de la cuenta regresiva.
 */
class Take(
    val id: String,
    val group: String,
    val instruction: String,
    val expected: List<List<Int>>,
    val seconds: Int,
)

/**
 * Lista de lo que Fede graba con cada piano. La misma lista sirve para el piano acústico y para el Casio, así los resultados
 * se pueden comparar. Cubre notas sueltas en distintas octavas, cambios de volumen, repeticiones, intervalos (incluida la octava,
 * que es lo que más se confunde), acordes con una y con las dos manos, pasajes cortos de canciones conocidas y silencio.
 */
object RecordingScript {
    private val names = listOf("do", "do#", "re", "re#", "mi", "fa", "fa#", "sol", "sol#", "la", "la#", "si")

    /** Nombre en Do-Re-Mi con octava científica: 60 -> "Do4". */
    fun noteName(midi: Int): String = names[midi % 12].replaceFirstChar { it.uppercase() } + (midi / 12 - 1)

    private fun idFor(midi: Int) = names[midi % 12].replace("#", "s") + (midi / 12 - 1)

    private fun single(midi: Int) = Take(
        id = "nota-" + idFor(midi),
        group = "Notas sueltas",
        instruction = "Tocá ${noteName(midi)} una vez y dejala sonar.",
        expected = listOf(listOf(midi)),
        seconds = 4,
    )

    private fun together(id: String, group: String, what: String, notes: List<Int>) = Take(
        id = id,
        group = group,
        instruction = "Tocá $what a la vez y dejalo sonar.",
        expected = listOf(notes),
        seconds = 4,
    )

    private fun passage(id: String, what: String, notes: List<Int>, seconds: Int) = Take(
        id = id,
        group = "Pasajes",
        instruction = what,
        expected = notes.map { listOf(it) },
        seconds = seconds,
    )

    private val singles = listOf(36, 43, 48, 52, 55, 60, 64, 67, 72, 76, 79, 84).map(::single)

    val takes: List<Take> = singles + listOf(
        Take("nota-do4-suave", "Volumen", "Tocá Do4 una vez, bien suave, y dejala sonar.", listOf(listOf(60)), 4),
        Take("nota-do4-fuerte", "Volumen", "Tocá Do4 una vez, bien fuerte, y dejala sonar.", listOf(listOf(60)), 4),
        Take(
            "nota-do4-x3", "Repeticiones",
            "Tocá Do4 tres veces seguidas, una por segundo, soltando la tecla entre una y otra.",
            listOf(listOf(60), listOf(60), listOf(60)), 6,
        ),
        together("quinta-do3-sol3", "Intervalos", "Do3 y Sol3 (una quinta grave)", listOf(48, 55)),
        together("quinta-do4-sol4", "Intervalos", "Do4 y Sol4 (una quinta)", listOf(60, 67)),
        together("octava-do3-do4", "Intervalos", "Do3 y Do4 (una octava)", listOf(48, 60)),
        together("acorde-do-mayor", "Acordes", "Do4, Mi4 y Sol4 (Do mayor)", listOf(60, 64, 67)),
        together("acorde-sol-mayor", "Acordes", "Sol3, Si3 y Re4 (Sol mayor)", listOf(55, 59, 62)),
        together("acorde-la-menor", "Acordes", "La3, Do4 y Mi4 (La menor)", listOf(57, 60, 64)),
        together("acorde-fa-mayor", "Acordes", "Fa3, La3 y Do4 (Fa mayor)", listOf(53, 57, 60)),
        together("dos-manos-do", "Dos manos", "Do3 con la izquierda y Do4, Mi4, Sol4 con la derecha", listOf(48, 60, 64, 67)),
        passage(
            "escala-do-mayor", "Tocá la escala de Do mayor subiendo, de Do4 a Do5, una nota por segundo.",
            listOf(60, 62, 64, 65, 67, 69, 71, 72), 10,
        ),
        passage(
            "himno-alegria", "Tocá el comienzo del Himno a la alegría, una nota por segundo: Mi Mi Fa Sol Sol Fa Mi Re Do Do Re Mi Mi Re Re.",
            listOf(64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62), 17,
        ),
        Take("silencio", "Silencio", "No toques nada: que quede el silencio de la habitación.", emptyList(), 5),
        Take("ruido-ambiente", "Silencio", "No toques nada, pero hacé ruido de fondo: hablá, tené la tele o la música baja.", emptyList(), 5),
    )

    fun byId(id: String): Take? = takes.firstOrNull { it.id == id }
}
