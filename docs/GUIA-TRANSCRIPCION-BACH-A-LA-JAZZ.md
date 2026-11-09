# Guía: transcribir «Bach à la Jazz» a la app

Para el profesor de piano. La meta es **un solo archivo `bach-a-la-jazz.json`** con la pieza entera (59 compases), lo más completa posible con lo que la app representa hoy. Todo lo demás se deja afuera y se informa. Reglas generales: `CONTRATO-ARCHIVOS.md`; modelo: `MODELO-PARTITURA.md`.

## Datos de la pieza

- Compás 3/4 (`numerator: 3, denominator: 4`), armadura de dos bemoles, Si bemol y Mi bemol (`"fifths": -2`; la que se ve en el PDF), negra = 140 (`"bpm": 140.0`).
- `level`: `"avanzado"`. `title`: «Bach à la Jazz». `goal`: una frase.
- Un compás de 3/4 son 1440 ticks.

## Cómo se escribe cada cosa que tiene la pieza

| En la partitura | En el archivo |
|---|---|
| Anacrusa de un pulso (compás 0) | `"pickupTicks": 480`. Las notas arrancan en el tick 0; el primer compás entero empieza en el tick 480 y es el 1. Llenar el compás 0 con notas/silencios en las dos manos. |
| Tresillos (casi toda la pieza) | `"tuplet": "3:2"`: corchea de tresillo = 160 ticks, negra de tresillo = 320. Grupos completos dentro del compás; silencios de tresillo también llevan `"tuplet"`. |
| Cuatro en el tiempo de tres (compases 54–55) | `"tuplet": "4:3"`, negra = 360 ticks. |
| Notas de adorno (según el PDF: compases 16, 22, 23, 24 y 37 en la mano derecha, y el 31 en la izquierda) | `"grace": true`, mismo `startTick` que la nota principal, `durationTicks` 60. Una sola por nota. |
| Dos líneas a la vez en una mano (derecha 11–16, izquierda 31) | `"voice"`: 1 la principal, 2 la otra. Cada voz sin superponerse y llenando sus compases. |
| Ligaduras de duración | Partir en la barra de compás con `tiedToNext`. |
| Cambios de clave (compases 54–58: la izquierda pasa a clave de sol; la derecha a clave de fa en el 57) | **Sí existen:** lista `"clefs"` con `{ "hand": "left", "startTick": …, "clef": "treble" }` en el tick donde empieza el compás 54 (y `"bass"` para volver a fa donde corresponda); lo mismo con `"hand": "right"` y `"bass"` desde el 57. Cada nota sigue en la mano que la toca, con su altura real: la clave solo cambia cómo se dibuja. Mirar en el PDF/XML dónde vuelve cada clave. |
| Signo 8va (compases 55–56) | **Sí existe:** lista `"ottavas"` con `{ "hand": "right", "startTick": …, "endTick": …, "kind": "8va" }`. Las alturas se escriben como suenan (igual que antes); la app las dibuja una octava más abajo y pone el signo con la línea punteada. `startTick` es el inicio de la primera nota del tramo y `endTick` donde termina la última nota con 8va (sin incluirla). Mirar en el PDF/XML hasta dónde llega la línea punteada y en qué mano está. |
| Compás 58, posibles fusas | **Sí se pueden:** una fusa dura 60 ticks y se escribe sin `"tuplet"`. Reemplazar el grupo "4:2" de semicorcheas que se usaba antes por fusas comunes de 60 ticks (se dibujan con tres barras). El sonido es el mismo. |
| Dinámicas (mf en el compás 0, mp en el 7, f en el 51, ff en el 55) | `"dynamics"`: `{ "startTick": …, "level": "mf" }` en el tick donde empieza ese compás. Cuenta de ticks: el compás 0 empieza en 0; el compás N (de 1 en adelante) empieza en `480 + (N − 1) × 1440`. |
| Crescendo que empieza en el compás 47 (dice «cresc.» escrito: llega hasta el f del 51) y regulador del 54 (termina en el ff del 55) | `"hairpins"`: `{ "startTick": …, "endTick": …, "kind": "crescendo" }` (o `"diminuendo"`; mirar en el PDF para qué lado abre). Si termina en una dinámica (por ejemplo el f del 51), esa dinámica va en el `endTick`. Sin superponerse. |
| Staccato (mirar el PDF punto por punto: en el 17–22 está solo en el primer acorde de cada compás; revisar si sigue en el 23–30) | `"staccato": true` en cada nota del acorde (todas iguales). La duración en ticks es la de la figura escrita; la app se encarga de acortar el sonido. |
| Acentos (compases 7 y 56–58) | `"accent": true` en la nota (o en todas las del acorde). Se puede combinar con `"staccato": true`. No va en notas ligadas (staccato) ni en adornos. |
| Repeticiones | Desplegadas, compás por compás. |

## Lo que se deja afuera (y hay que avisar)

(Las dinámicas, los reguladores, el staccato y los acentos **sí** se escriben, ver arriba.)

Pedal (24 y 58–59) y los textos «light, staccato» / «swing feel». Decir qué se omitió y dónde.

## Cómo trabajar

1. Leer la partitura (PDF) compás por compás. Si algo no se lee bien, **no adivinar**: anotarlo en el aviso final con el número de compás.
2. Escribir el JSON y correr `python3 /mnt/project/piano/validar_partitura.py bach-a-la-jazz.json` hasta que diga «cumple el contrato».
3. Revisar a mano lo que el validador no mira: ortografía de las alturas según la armadura (Si bemol y Mi bemol vienen de la armadura; Si o Mi natural llevan becuadro y se escriben `B` y `E`; los bemoles y sostenidos de paso se escriben como en el PDF), y que la música sea fiel.
4. Entregar un solo archivo y, después, una lista corta: qué se simplificó, qué se omitió (con compases) y qué dudas quedan.
