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

   **Tresillos y otros grupos irregulares.** Se escriben con el campo `"tuplet"` y la duración **real** en ticks (la que suena):

   | Grupo | `"tuplet"` | Figura escrita | `durationTicks` |
   |---|---|---|---|
   | Tresillo de corchea (3 en el tiempo de 2) | `"3:2"` | corchea | 160 |
   | Tresillo de negra | `"3:2"` | negra | 320 |
   | Tresillo de semicorchea | `"3:2"` | semicorchea | 80 |
   | Cuatro en el tiempo de tres | `"4:3"` | negra | 360 |

   Regla para calcularlo: `durationTicks` = (figura escrita) × normal ÷ actual. Todas las figuras de un grupo llevan el mismo `"tuplet"`, van seguidas, dentro de un mismo compás, y tienen que sumar grupos enteros (por ejemplo, tres corcheas de tresillo = 480 ticks). Un silencio de tresillo también lleva `"tuplet"`. Un acorde de tresillo lleva el mismo `"tuplet"` en todas sus notas. Ejemplo: `{ "hand": "right", "pitch": "C4", "startTick": 0, "durationTicks": 160, "tuplet": "3:2" }`. La app dibuja el número del grupo ("3", "4:3"), pegado a la barra si todas las figuras están unidas, o con un corchete si no.

   **No se pueden** fusas ni ninguna otra duración suelta: sin `"tuplet"`, la duración tiene que estar en la tabla de arriba. Una duración fuera de la tabla sin `"tuplet"` es un error: o se escribe como grupo irregular, o se aproxima a la figura más cercana y se le avisa a Fede qué se cambió.
3. **Cada mano llena cada compás completo** (en cada voz, ver la regla 5). Las notas y los silencios de una voz en un compás tienen que sumar exactamente la duración del compás (1920 ticks en 4/4, 1440 en 3/4 y 6/8). Si una mano no toca en un compás, lleva un silencio del largo del compás (por ejemplo `{ "hand": "left", "startTick": 0, "durationTicks": 1920 }`). Cada tramo en que una mano espera también lleva su silencio.
4. **Nada cruza una barra de compás.** Una nota que suena de un compás al siguiente se parte en dos: la primera lleva `"tiedToNext": true` y la segunda es la misma altura, misma mano, y empieza justo donde termina la primera. Lo mismo vale para los silencios (se parten sin ligadura).
5. **Una figura por vez en cada voz.** Dentro de una voz, cada nota o silencio empieza justo cuando termina el anterior. Un acorde son varias notas de la misma mano y voz con el mismo `startTick` y el mismo `durationTicks`.

   **Dos líneas a la vez en una misma mano** (una melodía larga arriba y otra que corre abajo; un bajo sostenido bajo acordes que se repiten) se escriben con el campo `"voice"`: la línea principal es `"voice": 1` (si no se escribe, es la 1) y la otra `"voice": 2` (hasta 4). Cada voz tiene su propio ritmo y se escribe como si fuera sola: notas y silencios seguidos, sin superponerse. Las notas de un acorde que se sueltan en momentos distintos son notas de voces distintas que empiezan juntas. La voz 1 llena todos los compases (regla 3); las voces 2 a 4 llenan los compases en los que aparecen, y en los que no aparecen no hace falta nada. Si dos voces tienen que tocar la misma tecla a la vez, no se puede: se une en una sola. Ejemplo: `{ "hand": "left", "pitch": "C2", "startTick": 3840, "durationTicks": 1920, "voice": 2 }`. En el dibujo, la voz 1 lleva las plicas hacia arriba y las otras hacia abajo; los silencios de la voz 1 se dibujan un poco más arriba y los de las otras más abajo, y si las notas de dos voces caen pegadas, la de la voz de número mayor se corre a un costado.
6. **Anacrusa.** Si la pieza empieza con un compás incompleto (por ejemplo, un solo pulso), se escribe `"pickupTicks"` con la duración de ese compás corto (480 para una negra en 4/4; 720 para tres corcheas) y las notas arrancan en el tick 0 como siempre. Ese compás se numera **0** y el primer compás entero es el **1**, igual que en el papel. Tiene que ser más corto que un compás entero, se llena con notas y silencios en las dos manos como cualquier compás (regla 3) y nada cruza su barra. Si la pieza no tiene anacrusa, no se escribe la clave.
7. **Notas de adorno.** Una notita chiquita que se toca justo antes del tiempo (acciaccatura) se escribe como una nota más con `"grace": true`. Va en el **mismo `startTick`** que la nota principal de esa mano (la que cae en el tiempo) y su `durationTicks` es cuánto dura el adorno: lo normal es **60** (hasta 120 como mucho). La app la toca *antes* de ese tick, así el ritmo de la nota principal no se corre. No cuenta para llenar el compás, no puede estar ligada ni llevar `"tuplet"`, y tiene que haber una nota principal de la misma mano en ese tick. Si el adorno cae en el primerísimo instante de la pieza (antes de los 60 ticks), no hay lugar antes y suena sobre el tiempo. Si el original tiene varias notitas seguidas antes de una nota, se deja una sola (la última antes del tiempo) y se avisa a Fede. Ejemplo: `{ "hand": "right", "pitch": "D4", "startTick": 960, "durationTicks": 60, "grace": true }`.
8. **La altura se escribe tal como suena en ese lugar.** `"F#4"` es Fa sostenido, `"Bb3"` es Si bemol, `"F4"` es Fa natural. La app mira la armadura y decide sola si dibuja la alteración: en Re mayor (dos sostenidos) un Fa sostenido se escribe `"F#4"` y no lleva signo en la partitura, y un Fa natural se escribe `"F4"` y la app le pone el becuadro. Usá la ortografía correcta de la tonalidad (Fa sostenido o Sol bemol según corresponda). Rango permitido: de `A0` a `C8`.
9. **Armadura y compás.** `keySignatures` usa `fifths`: positivo = sostenidos, negativo = bemoles (`-3` es Mi bemol mayor, `0` es Do mayor o La menor). Los cambios de armadura y de compás empiezan justo al principio de un compás. Si no cambian, una sola entrada en el tick 0.
10. **Tempo en negras por minuto, siempre.** Un 6/8 "a negra con puntillo = 60" se escribe `"bpm": 90.0` (60 × 1,5). El primer tempo está en el tick 0.
11. **Datos de la partitura.**
    - `title`: obligatorio, corto y en español.
    - `level`: `"principiante"`, `"intermedio"` o `"avanzado"`.
    - `goal`: una frase de qué se practica (por ejemplo "Pasar el pulgar por debajo en la escala de Sol mayor").
    - `composer`: si se conoce.
    - `finger` en las notas: opcional, de 1 (pulgar) a 5 (meñique). Si se escribe, que sea para todas las notas de la pasada.
12. **Repeticiones, da capo y codas se escriben desplegadas**, compás por compás, como si se tocara de corrido: la app no tiene signos de repetición.
13. **Dinámicas y reguladores.** La fuerza se escribe aparte de las notas, en dos listas de la partitura que valen para las dos manos:
    - `"dynamics"`: una letra que vale desde su `startTick` hasta la siguiente. `"level"` es `"ppp"`, `"pp"`, `"p"`, `"mp"`, `"mf"`, `"f"`, `"ff"` o `"fff"`. Ejemplo: `{ "startTick": 0, "level": "mf" }`. Van ordenadas, sin repetir posición y antes del final de la pieza. Antes de la primera letra vale la primera.
    - `"hairpins"`: crescendo o diminuendo entre dos posiciones. `{ "startTick": 1920, "endTick": 5760, "kind": "crescendo" }` (o `"diminuendo"`). La fuerza cambia de a poco desde lo que había hasta la letra que sigue; si no hay letra después, sube o baja un poco y queda ahí. No se pisan entre sí, y una letra no puede caer en el medio de un regulador (va al principio o al final). Si un regulador termina en una letra, se escribe esa letra en su `endTick`.
    - Se dibujan debajo del pentagrama de la izquierda. Si la pieza no trae ninguna, todo suena con una fuerza media. Las notas no tienen fuerza propia: manda siempre la dinámica vigente.
    - Un `sf` no se puede escribir todavía (para un acento sobre una nota, ver la regla 14).
14. **Staccato y acentos.** Son dos marcas de cada nota, `"staccato": true` y `"accent": true` (se pueden combinar; si no se escriben, la nota es normal).
    - **Staccato:** se dibuja un puntito y la nota suena solo la mitad de su duración. El ritmo no cambia: la duración en ticks es la de la figura escrita (una negra staccato sigue siendo de 480 ticks).
    - **Acento:** se dibuja un `>` y la nota suena más fuerte que la dinámica vigente.
    - En un acorde, todas las notas llevan lo mismo (se dibuja una sola marca para el acorde).
    - **Ninguna de las dos** va en un adorno (`grace`).
    - El **staccato** no se puede poner en una nota ligada a la siguiente (`tiedToNext`): tiene que sonar hasta la otra. El **acento** sí: va en la primera nota de la ligadura, que es donde se ataca.

## Lo que la app todavía no representa

Pedal, ornamentos (trinos, mordentes), indicaciones de texto ("swing feel"), fusas, cambios de clave (una nota que el original escribe en clave de sol dentro de la mano izquierda se escribe igual en clave de fa, con líneas adicionales) y el signo de 8va (se escriben las alturas reales, tal como suenan). Si algo de eso está en el original, se deja afuera y se le avisa a Fede en la respuesta, diciendo en qué compases.

## Errores comunes

| Síntoma o aviso | Causa habitual | Cómo se arregla |
|---|---|---|
| Aviso "cruza la barra de compás" | Una nota o un silencio pasa de un compás al otro | Partirla en dos y ligarlas (regla 4) |
| Aviso "Está ligada, pero no hay una nota igual que empiece justo después" | `tiedToNext` sin segunda nota, o la segunda es de otra mano o altura | Agregar la segunda nota con la misma mano y altura, justo donde termina la primera |
| Un compás se ve vacío o corrido | Falta el silencio de una mano, o las duraciones no suman el compás | Completar con silencios (regla 3) |
| Aviso "no es una figura que la app sepa dibujar" | Duración que no está en la tabla y no lleva `"tuplet"` | Si es un tresillo, agregar `"tuplet": "3:2"`; si no, aproximar a una figura de la tabla (regla 2) |
| Aviso "grupo irregular incompleto" | Faltan figuras del tresillo, o el grupo cruza una barra | Completar el grupo con notas o silencios con el mismo `"tuplet"` (regla 2) |
| Sale un signo de alteración de más o de menos | La altura se escribió según el dibujo y no según el sonido | Escribir la altura real (regla 8) |
| Aviso "Fuera del rango del piano" | Octava mal escrita (por ejemplo `C9`) | Revisar la octava: el Do central es `C4` |
| La pieza suena al doble o a la mitad de velocidad | `bpm` en otra figura que no sea negras | Pasar el tempo a negras por minuto (regla 10) |

## Si la pieza viene de otra fuente

- **MIDI.** Los ticks del MIDI no son los de la app: la app usa 480 por negra; si el MIDI usa otra resolución, se reescala. Hay que separar las manos (por pista o canal si el archivo los distingue; si no, por altura, con el Do central como corte aproximado), redondear los tiempos a las figuras de la tabla sin deformar el ritmo, y deducir la armadura y la ortografía de las notas.
- **Foto o PDF de una partitura.** Se transcribe con cuidado y se le avisa a Fede de todo lo que no se pudo leer bien (notas dudosas, compases borrosos) en lugar de adivinar en silencio.
- **MusicXML.** El pentagrama 1 es la mano derecha y el pentagrama 2 la izquierda. Se pasan las `divisions` a ticks (480 por negra) y se aplican las mismas reglas.

## Antes de entregar

1. ¿Cada mano suma exactamente la duración de cada compás? (regla 3)
2. ¿Nada cruza una barra, y toda ligadura tiene su segunda nota? ¿Cada voz va sola, sin figuras superpuestas? (regla 4)
3. ¿Solo hay duraciones de la tabla, o grupos irregulares con su `"tuplet"` y grupos completos? (regla 2)
4. ¿Las alturas están dentro de `A0`–`C8` y escritas según el sonido? (regla 8)
5. ¿El tempo está en negras por minuto? (regla 10)
6. ¿Están `title`, `level` y `goal`?
7. ¿Se avisó qué se simplificó o no se pudo representar?

## El validador

Antes de entregar un archivo, Claude corre el validador (necesita solo Python 3):

```
python3 /mnt/project/piano/validar_partitura.py archivo.json   # en el Proyecto de Claude
python3 tools/validar_partitura.py archivo.json                # en el repo
```

Revisa las reglas 2 a 14: duraciones permitidas, grupos irregulares, anacrusa, notas de adorno, dinámicas y reguladores, staccato y acentos, que cada mano llene cada compás, barras de compás, ligaduras, voces y superposiciones, la misma tecla dos veces a la vez, rango del piano, claves que no existen, tempo, armadura y datos de la partitura. Si encuentra algo, dice qué compás y cómo arreglarlo. Hay que corregir hasta que diga `✓ cumple el contrato`.

Qué **no** puede revisar: que la ortografía de las alturas sea la correcta para la tonalidad (`F#4` y no `Gb4` en Re mayor), que la música sea fiel al original, ni que la dificultad sea la que corresponde. Eso sigue siendo responsabilidad de quien arma el archivo.

La app también detecta varios errores al abrir un archivo (`validate()` devuelve mensajes en español), pero no avisa si falta un silencio o si un compás no suma: eso lo cubre el validador.

## Texto para pegar en las instrucciones del Proyecto "Profesor de piano"

> Cuando te pida una partitura o un ejercicio, entregame un archivo `.json` en el formato de la app de piano: el modelo está en MODELO-PARTITURA.md y las reglas en CONTRATO-ARCHIVOS.md. Los puntos que más importan: mano derecha = clave de sol (`"right"`), mano izquierda = clave de fa (`"left"`); 480 ticks por negra; solo redonda, blanca, negra, corchea y semicorchea (con hasta dos puntillos), tresillos y similares con `"tuplet"` (por ejemplo `"3:2"`, corchea de tresillo = 160 ticks); cada mano llena cada compás completo con notas y silencios; ninguna nota ni silencio cruza una barra de compás (se parte y se liga con `tiedToNext`); dos líneas a la vez en una misma mano con `"voice"` (1 la principal, 2 la otra; cada voz sin superponerse); si empieza con compás incompleto, `"pickupTicks"` (ese compás es el 0); notitas de adorno con `"grace": true` (mismo `startTick` que la nota principal, `durationTicks` 60); dinámicas con `"dynamics"` (`"mf"`, `"f"`...) y reguladores con `"hairpins"` (crescendo/diminuendo entre dos ticks); repeticiones desplegadas; alturas escritas como suenan (`F#4`, `Bb3`, `F4` si es natural) dentro de `A0`–`C8`; tempo en negras por minuto; con `title`, `level` y `goal`. Antes de entregar, corré `python3 /mnt/project/piano/validar_partitura.py archivo.json` hasta que diga que cumple el contrato, y decime qué simplificaste o no pudiste representar. Usá como modelo los archivos de `piano/ejemplos`.

## Ejemplos de referencia

Todos abren sin avisos y los tests lo verifican en cada cambio.

| Archivo | Qué muestra |
|---|---|
| [`escala-do-mayor.json`](ejemplos/escala-do-mayor.json) | Lo mínimo: dos manos, solo negras y una blanca final |
| [`himno-a-la-alegria.json`](ejemplos/himno-a-la-alegria.json) | Una pieza de 16 compases con melodía y acordes de acompañamiento, negras con puntillo y corcheas |
| [`ejercicio-todo-lo-que-dibuja.json`](ejemplos/ejercicio-todo-lo-que-dibuja.json) | Todo junto: alteraciones, silencios, acordes, ligaduras, y cambios de armadura, compás y tempo |
| [`partitura-ejemplo.json`](ejemplos/partitura-ejemplo.json) | Cómo se escribe la digitación (`finger`) |
| [`ejercicio-dos-voces.json`](ejemplos/ejercicio-dos-voces.json) | Dos voces en una mano: melodía larga arriba y línea que corre abajo, y un bajo sostenido bajo acordes |
| [`ejercicio-tresillos.json`](ejemplos/ejercicio-tresillos.json) | Tresillos de corchea y de negra, swing, silencio de tresillo y cuatro en el tiempo de tres |
| [`ejercicio-anacrusa.json`](ejemplos/ejercicio-anacrusa.json) | Anacrusa: compás 0 de tres corcheas (`pickupTicks`) |
| [`ejercicio-adornos.json`](ejemplos/ejercicio-adornos.json) | Notas de adorno (`grace`): con alteración, en acorde y en la izquierda |
| [`ejercicio-dinamicas.json`](ejemplos/ejercicio-dinamicas.json) | Dinámicas (`mp`, `f`, `ff`, `p`) y reguladores: un crescendo y un diminuendo |
| [`ejercicio-articulaciones.json`](ejemplos/ejercicio-articulaciones.json) | Staccato, acentos y las dos marcas juntas, en las dos manos y en acordes |

Este contrato se actualiza cada vez que la app gane o pierda soporte de algo.
