# Actualizar la app con pocos toques (PIA-75)

La app se actualiza con **Obtainium**, una app gratuita que mira los releases de GitHub y instala la versión nueva. Cada push al repo genera un release (`0.1.<número de corrida>`) con el APK adjunto.

## Instalación (una sola vez)

1. Bajar Obtainium desde `github.com/ImranR98/Obtainium/releases` (el APK arm64-v8a) e instalarlo.
2. En GitHub crear un token de solo lectura: Settings → Developer settings → Personal access tokens → Fine-grained. Acceso solo al repo `piano-app`, permiso **Contents: Read-only**. El token queda guardado solo en el celu y no va dentro de la app.
3. En Obtainium: Ajustes → Fuente específica → "Token de acceso personal a GitHub" y pegar el token.
4. Añadir aplicación con la URL `https://github.com/federomano/piano-app` y tocar el **+**. Dejar apagadas "Solo para seguimiento" y "No actualizar en segundo plano".

## Actualizar

Abrir Obtainium, deslizar hacia abajo para refrescar y tocar **Actualizar** en la app Piano. Obtainium puede también buscar actualizaciones solo en segundo plano y avisar.

## Si aparece "instalar sin analizar"

Viene de Play Protect. Se puede apagar en Ajustes → Seguridad → Play Protect → ajustes → "Mejorar la detección de apps dañinas". Es reversible.

## Pendiente de confirmar con una actualización real

Cuántos toques lleva actualizar y si queda algún paso de confirmación de Android.
