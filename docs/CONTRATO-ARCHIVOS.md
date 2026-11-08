# Contrato de archivos

Qué tiene que entregar Claude (el profesor de piano) para que la app abra una partitura a la primera, sin avisos ni dibujos raros.

La app lee **un solo formato**: el JSON propio de la partitura, descrito en [`MODELO-PARTITURA.md`](MODELO-PARTITURA.md). No importa de dónde venga la pieza (inventada, un MIDI, una foto, un MusicXML de internet): Claude la convierte a este formato antes de entregarla. Importar MIDI y MusicXML dentro de la app queda como plan B (PIA-26 y PIA-27).

## Qué se entrega

- **Un archivo por partitura**, de texto plano (UTF-8), con extensión `.json`.
- Nombre en minúsculas, sin acentos ni espacios, por ejemplo `escala-re-mayor.json`.
- JSON estricto: sin comentarios, sin comas de más al final de una lista y sin claves que no existan en el modelo (un error de tipeo hace que el archivo se rechace).
- Modelos para copiar: [`ejemplos/`](ejemplos/) (ver el final de este documento).

## Reglas obligatorias

1. **Dos manos.** `"right"` es la mano derecha y se dibuja en clave de sol; `"left"` es la mano izquierda y se dibuja en clave de fa. No hay cambios de clave ni notas que pasen de un pentagrama al otro. Si en la pieza original la melodía la toca la izquierda, va en `"left"`.
2. **Duraciones que la app sabe dibujar.** Una negra son 480 ticks. Se pueden usar redonda, blanca, negra, corchea y semicorchea, con un puntillo o con dos:

   | Figura | Sin puntillo | Un puntillo | Dos puntillos |
   |---|---|---|---|
   | Redonda | 1920 | — | — |
   | Blanca | 960 | 1440 | 1680 |
   | Negra | 480 | 720 | 840 |
   | Corchea | 240 | 360 | 420 |
   | Semicorchea | 120 | 180 | 210 |

   **No se pueden** tresillos, quintillos, fusas ni ninguna otra duración: la app las dibuja mal. Si la pieza los tiene, se aproximan a la figura más cercana y se le avisa a Fede qué se cambió.
3. **Cada mano llena cada compás completo.** Las notas y los silencios de una mano en un compás tienen que sumar exactamente la duración del compás (1920 ticks en 4/4, 1440 en 3/4 y 6/8). Si una mano no toca en un compás, lleva un silencio del largo del compás (por ejemplo `{ "hand": "left", "startTick": 0, "durationTicks": 1920 }`). Cada tramo en que una mano espera también lleva su silencio.
4. **Nada cruza una barra de compás.** Una nota que suena de un compás al siguiente se parte en dos: la primera lleva `"tiedToNext": true` y la segunda es la misma altura, misma mano, y empieza justo donde termina la primera. Lo mismo vale para los silencios (se parten sin ligadura).
5. **Una figura por vez en cada mano.** Dentro de una mano, cada nota o silencio empieza justo cuando termina el anterior: no hay voces superpuestas. Un acorde son varias notas de la misma mano con el mismo `startTick` y el mismo `durationTicks`. Si la pieza tiene dos voces en una misma mano, se simplifican a una sola línea o se reparten entre las manos, y se le avisa a Fede.
6. **El primer compás va entero.** No hay anacrusa: si la pieza empieza a mitad de compás, el compás inicial se completa con un silencio al principio.
7. **La altura se escribe tal como suena en ese lugar.** `"F#4"` es Fa sostenido, `"Bb3"` es Si bemol, `"F4"` es Fa natural. La app mira la armadura y decide sola si dibuja la alteración: en Re mayor (dos sostenidos) un Fa sostenido se escribe `"F#4"` y no lleva signo en la partitura, y un Fa natural se escribe `"F4"` y la app le pone el becuadro. Usá la ortografía correcta de la tonalidad (Fa sostenido o Sol bemol según corresponda). Rango permitido: de `A0` a `C8`.
8. **Armadura y compás.** `keySignatures` usa `fifths`: positivo = sostenidos, negativo = bemoles (`-3` es Mi bemol mayor, `0` es Do mayor o La menor). Los cambios de armadura y de compás empiezan justo al principio de un compás. Si no cambian, una sola entrada en el tick 0.
9. **Tempo en negras por minuto, siempre.** Un 6/8 "a negra con puntillo = 60" se escribe `"bpm": 90.0` (60 × 1,5). El primer tempo está en el tick 0.
10. **Datos de la partitura.**
    - `title`: obligatorio, corto y en español.
    - `level`: `"principiante"`, `"intermedio"` o `"avanzado"`.
    - `goal`: una frase de qué se practica (por ejemplo "Pasar el pulgar por debajo en la escala de Sol mayor").
    - `composer`: si se conoce.
    - `finger` en las notas: opcional, de 1 (pulgar) a 5 (meñique). Si se escribe, que sea para todas las notas de la pasada.
11. **Repeticiones, da capo y codas se escriben desplegadas**, compás por compás, como si se tocara de corrido: la app no tiene signos de repetición.

## Lo que la app todavía no representa

Dinámicas (p, f, crescendo), articulaciones (staccato, acentos), pedal, ornamentos, notas de adorno, letras, tresillos y otros grupos irregulares, voces superpuestas dentro de una mano, anacrusa y cambio de clave. Si algo de eso está en el original, se deja afuera y se le avisa a Fede en la respuesta.

## Errores comunes

| Síntoma o aviso | Causa habitual | Cómo se arregla |
|---|---|---|
| Aviso "cruza la barra de compás" | Una nota o un silencio pasa de un compás al otro | Partirla en dos y ligarlas (regla 4) |
| Aviso "Está ligada, pero no hay una nota igual que empiece justo después" | `tiedToNext` sin segunda nota, o la segunda es de otra mano o altura | Agregar la segunda nota con la misma mano y altura, justo donde termina la primera |
| Un compás se ve vacío o corrido | Falta el silencio de una mano, o las duraciones no suman el compás | Completar con silencios (regla 3) |
| Una nota se dibuja con otra figura | Duración que no está en la tabla (tresillos) | Aproximar a una figura de la tabla (regla 2) |
| Sale un signo de alteración de más o de menos | La altura se escribió según el dibujo y no según el sonido | Escribir la altura real (regla 7) |
| Aviso "Fuera del rango del piano" | Octava mal escrita (por ejemplo `C9`) | Revisar la octava: el Do central es `C4` |
| La pieza suena al doble o a la mitad de velocidad | `bpm` en otra figura que no sea negras | Pasar el tempo a negras por minuto (regla 9) |

## Si la pieza viene de otra fuente

- **MIDI.** Los ticks del MIDI no son los de la app: la app usa 480 por negra; si el MIDI usa otra resolución, se reescala. Hay que separar las manos (por pista o canal si el archivo los distingue; si no, por altura, con el Do central como corte aproximado), redondear los tiempos a las figuras de la tabla sin deformar el ritmo, y deducir la armadura y la ortografía de las notas.
- **Foto o PDF de una partitura.** Se transcribe con cuidado y se le avisa a Fede de todo lo que no se pudo leer bien (notas dudosas, compases borrosos) en lugar de adivinar en silencio.
- **MusicXML.** El pentagrama 1 es la mano derecha y el pentagrama 2 la izquierda. Se pasan las `divisions` a ticks (480 por negra) y se aplican las mismas reglas.

## Antes de entregar

1. ¿Cada mano suma exactamente la duración de cada compás? (regla 3)
2. ¿Nada cruza una barra, y toda ligadura tiene su segunda nota? (regla 4)
3. ¿Solo hay duraciones de la tabla? (regla 2)
4. ¿Las alturas están dentro de `A0`–`C8` y escritas según el sonido? (regla 7)
5. ¿El tempo está en negras por minuto? (regla 9)
6. ¿Están `title`, `level` y `goal`?
7. ¿Se avisó qué se simplificó o no se pudo representar?

Cuando esté el validador (PIA-45), Claude lo corre antes de entregar. La app ya sabe detectar varios errores (`validate()` devuelve mensajes en español) y los va a mostrar cuando pueda abrir archivos (PIA-28), pero **todavía no avisa si falta un silencio o si un compás no suma**: eso se revisa a mano con la lista de arriba.

## Texto para pegar en las instrucciones del Proyecto "Profesor de piano"

> Cuando te pida una partitura o un ejercicio, entregame un archivo `.json` en el formato de la app de piano: el modelo está en MODELO-PARTITURA.md y las reglas en CONTRATO-ARCHIVOS.md. Los puntos que más importan: mano derecha = clave de sol (`"right"`), mano izquierda = clave de fa (`"left"`); 480 ticks por negra; solo redonda, blanca, negra, corchea y semicorchea (con hasta dos puntillos), sin tresillos; cada mano llena cada compás completo con notas y silencios; ninguna nota ni silencio cruza una barra de compás (se parte y se liga con `tiedToNext`); sin voces superpuestas en una mano; sin anacrusa; repeticiones desplegadas; alturas escritas como suenan (`F#4`, `Bb3`, `F4` si es natural) dentro de `A0`–`C8`; tempo en negras por minuto; con `title`, `level` y `goal`. Antes de entregar, revisá que cada compás de cada mano sume lo que corresponde, y decime qué simplificaste o no pudiste representar. Usá como modelo los archivos de `docs/ejemplos`.

## Ejemplos de referencia

Todos abren sin avisos y los tests lo verifican en cada cambio.

| Archivo | Qué muestra |
|---|---|
| [`escala-do-mayor.json`](ejemplos/escala-do-mayor.json) | Lo mínimo: dos manos, solo negras y una blanca final |
| [`himno-a-la-alegria.json`](ejemplos/himno-a-la-alegria.json) | Una pieza de 16 compases con melodía y acordes de acompañamiento, negras con puntillo y corcheas |
| [`ejercicio-todo-lo-que-dibuja.json`](ejemplos/ejercicio-todo-lo-que-dibuja.json) | Todo junto: alteraciones, silencios, acordes, ligaduras, y cambios de armadura, compás y tempo |
| [`partitura-ejemplo.json`](ejemplos/partitura-ejemplo.json) | Cómo se escribe la digitación (`finger`) |

Este contrato se actualiza cada vez que la app gane o pierda soporte de algo.
