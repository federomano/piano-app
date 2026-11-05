# Cómo instalar y actualizar la app en el Pixel 8

La app no está en Play Store: se instala descargando el archivo APK desde GitHub.

## Configuración inicial (una sola vez)

Esto lo hacés desde la compu, en el navegador.

### 1. Crear la clave de firma

La clave de firma es lo que permite que cada versión nueva se instale encima de la anterior sin perder tus datos.

1. En el repo, entrá a la pestaña **Actions**.
2. En la lista de la izquierda elegí **Crear clave de firma (una sola vez)**.
3. Tocá **Run workflow** > **Run workflow**. Tarda un minuto.
4. Cuando termine en verde, entrá a esa ejecución. Abajo, en **Artifacts**, descargá **clave-de-firma** (es un zip). Descomprimilo.

### 2. Cargar la clave como secretos del repo

1. Repo > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**.
2. Creá cuatro secretos. El nombre es el del archivo (sin el número y sin `.txt`) y el valor es el contenido del archivo, pegado tal cual, sin espacios ni saltos de línea de más:

| Nombre del secreto | Contenido |
|---|---|
| `PIANO_KEYSTORE_BASE64` | archivo `1-PIANO_KEYSTORE_BASE64.txt` |
| `PIANO_KEYSTORE_PASSWORD` | archivo `2-PIANO_KEYSTORE_PASSWORD.txt` |
| `PIANO_KEY_PASSWORD` | archivo `3-PIANO_KEY_PASSWORD.txt` |
| `PIANO_KEY_ALIAS` | archivo `4-PIANO_KEY_ALIAS.txt` |

### 3. Guardar la clave en un lugar seguro

Guardá la carpeta descomprimida (en tu gestor de contraseñas o en un lugar privado). Si perdés la clave, para instalar una versión nueva vas a tener que desinstalar la app y perder sus datos.

Después borrá el artifact `clave-de-firma` de GitHub (en la página de la ejecución, ícono de tacho) y la carpeta de Descargas.

## Instalar la app en el celular

1. En el Pixel, abrí Chrome con tu cuenta de GitHub iniciada y entrá a `github.com/federomano/piano-app/releases`.
2. Tocá la versión más nueva y descargá el archivo `piano-app-….apk`.
3. Al abrirlo, Android va a avisar que Chrome no puede instalar apps desconocidas. Tocá **Ajustes** y activá **Permitir de esta fuente**.
4. Volvé atrás y tocá **Instalar**. Si Play Protect avisa que la app no es conocida, elegí **Instalar de todas formas**.

## Actualizar

Entrás a la misma página de releases, descargás el APK más nuevo y lo instalás: se instala encima de la anterior y conserva los datos. Si Android dice que la app no se puede instalar, avisame con el mensaje exacto.
