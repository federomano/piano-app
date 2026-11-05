# Piano

App Android para aprender piano con partitura continua a dos manos, reproducción con sonido de piano y un modo escucha que usa el micrófono y espera a que toques la nota correcta.

Uso personal en un Pixel 8, instalada por APK (sin Play Store).

## Qué hace (plan)

- Partitura tradicional (clave de sol y fa) en una sola línea continua que se desliza en pantalla completa horizontal.
- Sonido de piano de lo que muestra la partitura, con mute y velocidad de 0,25x a 2x.
- Modo escucha: la partitura espera tu ritmo y se frena si te frenás.
- Importa archivos MIDI y MusicXML.
- Las lecciones y arreglos los arma Claude en un proyecto aparte y se importan a la app.

El detalle está en Linear, en el proyecto **App de Piano** del team Piano-Fede.

## Estado

Base del proyecto lista: abre en pantalla completa, bloqueada en horizontal. Todavía no muestra partitura, pero ya existe el modelo de datos de la partitura (ver [docs/MODELO-PARTITURA.md](docs/MODELO-PARTITURA.md)).

## Instalar en el celular

Ver [docs/INSTALAR.md](docs/INSTALAR.md).

## Para desarrollar

- Kotlin y Jetpack Compose. Gradle con el wrapper incluido.
- Compilar: `./gradlew assembleDebug` (necesita Java 21 y el SDK de Android; `ANDROID_HOME` o `local.properties` con `sdk.dir`).
- GitHub Actions compila cada cambio en `main` y, con la clave de firma cargada, publica el APK en Releases.
- Todo cambio de código parte de una historia de Linear.
