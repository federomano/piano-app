> **Nota:** documento de trabajo histórico. Muchas de estas necesidades ya están resueltas (tresillos, voces, anacrusa, adornos). Lo vigente para transcribir la pieza está en `GUIA-TRANSCRIPCION-BACH-A-LA-JAZZ.md`; lo que falta (dinámicas, articulaciones, pedal, cambios de clave, 8va, fusas) está en PIA-58.

# Qué le falta a la app para reproducir "Bach à la Jazz" tal cual está escrita

Pieza: *Bach à la Jazz*, arr. Matt Herskowitz (PDF de 5 páginas, 59 compases, 3/4, Do menor, negra = 140). Se revisó contra `CONTRATO-ARCHIVOS.md` y `MODELO-PARTITURA.md`. Hoy el contrato solo permite figuras binarias (redonda a semicorchea con hasta dos puntillos), una figura por vez en cada mano, compás completo y sin anacrusa. La partitura rompe esas reglas en varios lugares.

Aclaración: se leyó la partitura completa, pero todavía no se transcribió compás por compás. Los números de compás son los del PDF (el primer compás de un pulso es el "0", sin número). Lo marcado con (verificar) hay que confirmarlo al transcribir.

## 1. Imprescindible para que SUENE exacto

### 1.1 Tresillos y grupos irregulares (el problema principal)
Casi toda la pieza está escrita en tresillos de swing (negra de tresillo + corchea de tresillo por pulso, y tres corcheas de tresillo por pulso en varios compases).
- Duraciones que hacen falta con 480 ticks por negra: corchea de tresillo = 160, negra de tresillo = 320. Ninguna está en la tabla de duraciones permitidas.
- Compases 54-55 tienen un grupo 4:3 (cuatro acordes en el lugar de tres negras) sobre un compás entero. Con 480 ticks por negra da 360 ticks por acorde.
- Lo que se pide: aceptar duraciones arbitrarias en ticks, o un campo de tuplet (por ejemplo 3:2 y 4:3) que el dibujo sepa mostrar con su corchete.
- Aproximar con corchea con puntillo + semicorchea (360 + 120) cambia el ritmo. No sirve si se quiere el sonido exacto.

### 1.2 Fusas (verificar)
En el compás 58 las corridas de ambas manos parecen tener tres barras, o sea fusas (60 ticks). Hoy lo más corto es la semicorchea (120).

### 1.3 Notas de adorno (apoyaturas)
Aparecen en los compases 16, 22, 23, 24, 31 (mano izquierda), entre otros. Hace falta un tipo de nota de adorno (`grace: true`) que se toque justo antes del tiempo fuerte, o al menos aceptar notas muy cortas sin que rompan la regla de "llenar el compás".

### 1.4 Voces superpuestas en una misma mano
- Compases 11 a 16 (mano derecha): una blanca de arriba suena mientras abajo corre otra línea de negras y corcheas. Las dos tienen la misma altura de inicio.
- Mano izquierda: notas largas del bajo que siguen sonando mientras un acorde se repite arriba. Ejemplo claro: compás 31, donde el Re grave y el Si♭ siguen ligados mientras sobre ellos entran acordes en tresillo.
- Lo que se pide: permitir más de una voz por mano, con ritmos independientes, sin la regla de "una figura por vez".

### 1.5 Acordes cuyas notas duran distinto
Va con el punto anterior. Hoy un acorde exige el mismo `startTick` y la misma `durationTicks` en todas sus notas, y en esta pieza hay notas de un acorde que se sueltan antes que otras.

## 2. Necesario para la lectura y el dibujo (la reproducción no cambia)

### 2.1 Anacrusa
El primer compás tiene un solo pulso (silencio de negra arriba, y abajo un silencio de tresillo más una corchea de tresillo en el Sol grave). Hoy hay que rellenarlo con silencios, y eso corre la numeración y el compás 1 original queda en el 2.

### 2.2 Cambios de clave y cruce de manos
En los compases 54 a 58 la mano izquierda pasa a clave de sol y, en el 57, la derecha baja a clave de fa. El contrato dice "no hay cambios de clave ni notas que pasen de un pentagrama al otro". Hace falta: clave por compás y mano, o que la altura decida en qué pentagrama se dibuja.

### 2.3 Signo de octava (8va)
Los compases 55 y 56 (mano derecha) llevan "8va" y un "(8)". Las alturas del archivo ya son las que suenan, así que alcanza con dibujar el signo y la línea punteada. Es opcional.

## 3. Expresión (mejora el sonido, no cambia las notas)

- **Dinámicas:** mf (compás 0), mp (7), f (51), ff (55), crescendo (compases 47 a 48) y regulador (compás 54).
- **Articulaciones:** staccato (puntos, casi todos los acordes de la mano izquierda de la intro y de los compases 17 a 30) y acentos (">", compases 7 y 56 a 58).
- **Pedal:** "Ped." en el compás 24 y al final (compases 58 a 59).
- **Indicación de carácter:** "light, staccato" al principio y "swing feel", que no hace falta implementar porque los tresillos ya vienen escritos.
- **Tempo:** negra = 140, que entra sin cambios.

## 4. Lo que SÍ está soportado y se usa
Compás 3/4, armadura de tres bemoles, alturas de A0 a C8 (la pieza va de Re grave a Re agudo), ligaduras, silencios, acordes de misma duración, alteraciones accidentales.

## Orden sugerido
1. Duraciones arbitrarias o tuplets (1.1), con fusas (1.2) y apoyaturas (1.3).
2. Más de una voz por mano y acordes con duraciones distintas (1.4 y 1.5).
3. Anacrusa (2.1), claves (2.2) y 8va (2.3).
4. Dinámicas, articulaciones y pedal (3).

Con 1 y 2 el archivo ya suena igual que la partitura. El resto es presentación.
