# Piano (versión web)

Versión web de la app de piano (PIA-86): corre en el navegador de la Mac, la tablet y el celular. La app de Android
queda congelada en la carpeta `app/`.

## Comandos

Hace falta Node 22 o más nuevo. Todo se corre dentro de `web/`.

- `npm ci` instala las dependencias.
- `npm test` corre los tests. Los que comparan contra el validador de Python necesitan `python3` (si no está, se saltean).
- `npm run build` arma la web en `dist/`.
- `npm run dev` abre un servidor local para ir viendo los cambios.

Cada cambio en `main` que toque `web/` se prueba y se publica solo en GitHub Pages (`.github/workflows/web.yml`).

## Qué hay

- `src/score/model.ts`: el modelo de la partitura (el mismo que `Score.kt`).
- `src/score/json.ts`: lector de JSON estricto, con los mismos avisos que Python.
- `src/score/contract.ts`: revisión contra el contrato de archivos. Es el validador `tools/validar_partitura.py` pasado a
  TypeScript, con los mismos mensajes. Si se cambia uno hay que cambiar el otro: los tests los comparan.
- `src/score/read.ts`: de un archivo `.json` a una partitura, o la lista de avisos en español.
