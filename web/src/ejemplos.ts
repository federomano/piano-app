/*
 * Las partituras de ejemplo de docs/ejemplos, incluidas en la web (PIA-90) para poder verlas sin abrir ningún archivo.
 * Abrir un archivo propio es otra historia (PIA-92).
 */
const archivos = import.meta.glob("../../docs/ejemplos/*.json", { query: "?raw", import: "default", eager: true }) as Record<
  string,
  string
>;

export interface Ejemplo {
  /** Nombre sin la extensión, por ejemplo `ejercicio-tresillos`. */
  readonly nombre: string;
  readonly texto: string;
}

export const EJEMPLOS: readonly Ejemplo[] = Object.entries(archivos)
  .map(([ruta, texto]) => ({ nombre: ruta.replace(/^.*\//, "").replace(/\.json$/, ""), texto }))
  .sort((a, b) => a.nombre.localeCompare(b.nombre));

/** El que se muestra al abrir: el que tiene de todo. */
export const EJEMPLO_INICIAL = "ejercicio-todo-lo-que-dibuja";
