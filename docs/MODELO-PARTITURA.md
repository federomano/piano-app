# Modelo de la partitura

Toda partitura de la app se representa de una sola forma: la que vino de un archivo MIDI, la que vino de MusicXML y la que arma Claude. El dibujo, el sonido y el modo escucha leen los mismos datos.

Qué le pedimos a Claude que genere (reglas y errores comunes): [`CONTRATO-ARCHIVOS.md`](CONTRATO-ARCHIVOS.md).

El código está en `app/src/main/java/com/federomano/pianoapp/score/`. Un ejemplo completo y válido está en [`ejemplos/partitura-ejemplo.json`](ejemplos/partitura-ejemplo.json); los tests lo leen, así que la documentación no se puede desactualizar sin que falle algo.

## Cómo se mide el tiempo

Todo se mide en **ticks**. Una negra siempre son **480 ticks**.

| Figura | Ticks | Figura con puntillo | Ticks |
|---|---|---|---|
| Redonda | 1920 | Blanca con puntillo | 1440 |
| Blanca | 960 | Negra con puntillo | 720 |
| Negra | 480 | Corchea con puntillo | 360 |
| Corchea | 240 | | |
| Semicorchea | 120 | | |

Un compás de 4/4 dura 1920 ticks, uno de 3/4 o 6/8 dura 1440. La posición de cada cosa (`startTick`) se cuenta desde el principio de la pieza, que es el tick 0.

## Elementos

### Partitura (`Score`)

| Campo | Qué es |
|---|---|
| `formatVersion` | Versión del formato. Hoy es `1`. |
| `title` | Título. Obligatorio. |
| `composer`, `level`, `goal` | Compositor, nivel (por ejemplo "principiante") y qué se practica. Opcionales. |
| `timeSignatures` | Firmas de compás. La primera en el tick 0. Si no se indica, 4/4. |
| `keySignatures` | Armaduras. La primera en el tick 0. Si no se indica, sin alteraciones. |
| `tempos` | Cambios de tempo. El primero en el tick 0. Si no se indica, 100. |
| `pickupTicks` | Anacrusa: duración en ticks del primer compás cuando es más corto que los demás (480 = una negra en 4/4). Ese compás es el 0 y el primer compás entero es el 1. Si no se indica, 0 (no hay anacrusa). |
| `notes` | Las notas de las dos manos. |
| `rests` | Los silencios de las dos manos. |

### Nota (`notes`)

| Campo | Qué es |
|---|---|
| `hand` | `"right"` (mano derecha, clave de sol) o `"left"` (mano izquierda, clave de fa). |
| `pitch` | Altura escrita como texto: `C4` es el Do central, `F#4` es Fa sostenido, `Bb3` es Si bemol. Letras en inglés: C=Do, D=Re, E=Mi, F=Fa, G=Sol, A=La, B=Si. El número es la octava (el La de 440 Hz es `A4`). |
| `startTick` | Dónde empieza. |
| `durationTicks` | Cuánto dura. |
| `tiedToNext` | `true` si está ligada con la nota igual que empieza justo después. Opcional. |
| `finger` | Dedo sugerido, de 1 (pulgar) a 5 (meñique). Opcional. |
| `voice` | Voz dentro de la mano, de 1 a 4 (si no se escribe, es la 1). Cada voz tiene su propio ritmo; sirve para dos líneas a la vez en una misma mano. Los silencios también lo llevan. Opcional. |
| `tuplet` | Grupo irregular, como texto: `"3:2"` = tresillo (3 figuras en el tiempo de 2), `"4:3"` = cuatro en el tiempo de tres. Opcional. `durationTicks` es la duración real; la figura escrita sale de multiplicarla por actual/normal (160 ticks con `"3:2"` se escribe como corchea). Los silencios también lo llevan. |

`F#4` y `Gb4` suenan igual pero se escriben distinto en la partitura. Por eso la altura guarda cómo se escribe, no solo qué número MIDI es.

**Acordes:** no hay un elemento aparte. Un acorde son varias notas de la misma mano con el mismo `startTick` y la misma `durationTicks`.

### Silencio (`rests`)

`hand`, `startTick` y `durationTicks`. Una mano que no toca durante un tramo lleva su silencio.

### Firma de compás, armadura y tempo

- **Firma de compás:** `{ "startTick": 0, "numerator": 3, "denominator": 4 }` es un compás de 3/4. El denominador puede ser 1, 2, 4, 8, 16 o 32.
- **Armadura:** `{ "startTick": 0, "fifths": 2 }`. Positivo son sostenidos (2 = Re mayor, dos sostenidos), negativo son bemoles (-3 = Mi bemol mayor), 0 es Do mayor o La menor.
- **Tempo:** `{ "startTick": 0, "bpm": 90.0 }`, en negras por minuto.

Los cambios de firma de compás y de armadura tienen que empezar justo al inicio de un compás.

## Reglas que revisa `validate()`

La función `validate()` devuelve la lista de problemas en español; vacía significa que la partitura está bien. Una nota o un silencio con datos imposibles (duración 0, dedo 6, tempo 0) ni siquiera se puede crear.

- Las listas de firmas, armaduras y tempos empiezan en el tick 0 y están ordenadas, sin repetir posición.
- Los cambios de firma de compás y de armadura caen al inicio de un compás.
- Ninguna nota ni silencio **cruza una barra de compás**. Si una nota tiene que sonar de un compás al otro, se parte en dos y la primera lleva `tiedToNext: true`.
- Una ligadura (`tiedToNext`) tiene a continuación una nota igual, de la misma mano, que empieza justo donde termina la primera.
- Dos notas iguales de la misma mano no se pisan.
- Las notas están dentro del rango del piano, de `A0` a `C8`.

## Cómo se usa en el código

```kotlin
val score = ScoreJson.decode(texto)   // lee el JSON; falla con un mensaje claro si hay un error
val problemas = score.validate()      // reglas entre varios elementos
val compases = score.measures()       // compases calculados, con firma y armadura de cada uno
val acordes = score.chords(Hand.RIGHT) // notas agrupadas en acordes
val json = ScoreJson.encode(score)    // vuelve a escribir el JSON
```

El JSON es estricto: una clave mal escrita, una altura inválida o una versión de formato desconocida son errores. Así los typos en los archivos se detectan en vez de ignorarse.

## Lo que todavía no cubre

- **Dinámicas, articulaciones, pedal, repeticiones y ornamentos:** no se representan.
- **Una nota ligada a varias seguidas:** se encadenan ligaduras, una por una.
