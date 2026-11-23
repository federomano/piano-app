/** Medidas fijas del dibujo, en espacios de pentagrama (sp). Mismo objeto Geometry de ScoreLayout.kt. */
const STAFF_HEIGHT = 4;
const BASS_TOP = 10;
const MARGIN_TOP = 5.5;
const MARGIN_BOTTOM = 5.5;
const SYSTEM_BOTTOM = BASS_TOP + STAFF_HEIGHT;

export const Geometry = {
  STAFF_HEIGHT,
  /** Línea superior del pentagrama de la mano derecha (clave de sol). */
  TREBLE_TOP: 0,
  /** Línea superior del pentagrama de la mano izquierda (clave de fa). */
  BASS_TOP,
  SYSTEM_BOTTOM,
  /** Espacio libre arriba y abajo para líneas adicionales, notas agudas o graves y números de compás. */
  MARGIN_TOP,
  MARGIN_BOTTOM,
  TOTAL_HEIGHT: MARGIN_TOP + SYSTEM_BOTTOM + MARGIN_BOTTOM,
  STAFF_LINE_THICKNESS: 0.13,
  THIN_BARLINE: 0.16,
  THICK_BARLINE: 0.5,
  /** Ancho de una negra. Un compás de 4/4 mide 4 veces esto. */
  QUARTER_WIDTH: 5,
  /** Dónde está la línea de reproducción, como fracción del ancho de la pantalla desde la izquierda. */
  PLAYHEAD_FRACTION: 0.3,
} as const;
