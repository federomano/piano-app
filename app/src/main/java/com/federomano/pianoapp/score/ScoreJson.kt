package com.federomano.pianoapp.score

import kotlinx.serialization.json.Json

/**
 * Lee y escribe partituras en JSON. Es estricto: una clave desconocida o un valor inválido
 * es un error (así se detectan los typos en los archivos armados a mano o por Claude).
 * Lanza [IllegalArgumentException] con un mensaje en español o de kotlinx.serialization.
 */
object ScoreJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(score: Score): String = json.encodeToString(Score.serializer(), score)

    fun decode(text: String): Score = json.decodeFromString(Score.serializer(), text)
}
