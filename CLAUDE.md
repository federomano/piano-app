# piano-app

App Android (Kotlin + Jetpack Compose) para que Fede aprenda piano. Uso personal, Pixel 8, instalación por APK. Fede no programa: explicale las cosas en español rioplatense, simple y directo.

## Reglas de trabajo

- No se programa nada que no esté en una historia de Linear (team Piano-Fede, proyecto App de Piano, prefijo PIA). Si hace falta algo nuevo, primero se carga la historia.
- Una historia a la vez; varias solo si son muy fáciles. Pasar la historia a In Progress al empezar y a Done cuando Fede lo confirme probando en el celular.
- Presupuesto: 0 USD. Nada que se pague (sin API de Anthropic dentro de la app, sin Play Store).
- Decisiones de diseño: documento "Decisiones y alcance v1" en Linear.

## Compilar

- `./gradlew assembleDebug` o `assembleRelease`. Java 21, SDK de Android con `compileSdk = 37` y `targetSdk = 36`.
- Sin variables de firma, release se firma con la clave de debug (solo para pruebas locales). El APK que se instala sale de GitHub Actions, firmado con la clave de los secretos.
- En el entorno de Claude Code, Maven Central devuelve 429; se resuelve con un init script local en `~/.gradle/init.d/mirror.gradle` que usa el espejo de Google. No va al repo.
- `versionCode` sube con `GITHUB_RUN_NUMBER`: Android no deja instalar encima una versión con versionCode menor.

## Convenciones

- Paquete: `com.federomano.pianoapp`.
- Textos de la app y documentación en español.
- Commits en español, en tiempo presente, con el identificador de Linear (por ejemplo `PIA-5: proyecto Android base`).
