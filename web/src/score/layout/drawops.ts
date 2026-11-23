/*
 * Instrucciones de dibujo (PIA-89). Mismo modelo que app/.../score/layout/DrawOps.kt.
 *
 * El motor de layout no dibuja nada: calcula una lista de instrucciones simples (rectángulos, símbolos
 * musicales, textos y polígonos) en "espacios de pentagrama" (sp: la distancia entre dos líneas).
 * x crece hacia la derecha; y crece hacia abajo; y = 0 es la línea superior del pentagrama de la derecha.
 */

/** Rectángulo relleno (líneas del pentagrama, barras de compás, plicas). */
export interface RectOp {
  readonly kind: "rect";
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/** Símbolo de la fuente Bravura (SMuFL). x es el borde izquierdo e y la línea de base. */
export interface GlyphOp {
  readonly kind: "glyph";
  readonly codepoint: number;
  readonly x: number;
  readonly y: number;
  readonly scale: number;
}

/** Texto común (números de compás). x es el borde izquierdo, y la línea de base, size el alto de letra. */
export interface TextOp {
  readonly kind: "text";
  readonly text: string;
  readonly x: number;
  readonly y: number;
  readonly size: number;
}

/** Polígono relleno con puntos [x0, y0, x1, y1, ...] (vigas inclinadas y ligaduras). */
export interface PolygonOp {
  readonly kind: "polygon";
  readonly points: readonly number[];
}

export type DrawOp = RectOp | GlyphOp | TextOp | PolygonOp;

export const rect = (x: number, y: number, width: number, height: number): RectOp => ({ kind: "rect", x, y, width, height });
export const glyph = (codepoint: number, x: number, y: number, scale = 1): GlyphOp => ({ kind: "glyph", codepoint, x, y, scale });
export const text = (value: string, x: number, y: number, size: number): TextOp => ({ kind: "text", text: value, x, y, size });
export const polygon = (points: readonly number[]): PolygonOp => ({ kind: "polygon", points });

/** El menor x de un polígono. */
export function polygonMinX(op: PolygonOp): number {
  let min = Infinity;
  for (let i = 0; i < op.points.length; i += 2) min = Math.min(min, op.points[i]!);
  return min;
}

/** Códigos de los símbolos de Bravura que usa el dibujo (https://w3c.github.io/smufl/). */
export const Smufl = {
  BRACE: 0xe000,
  G_CLEF: 0xe050,
  F_CLEF: 0xe062,
  FLAT: 0xe260,
  NATURAL: 0xe261,
  SHARP: 0xe262,

  /** Ancho de cada símbolo, en espacios de pentagrama, medido en la fuente. */
  FLAT_ADVANCE: 0.904,
  NATURAL_ADVANCE: 0.672,
  SHARP_ADVANCE: 0.996,
  CLEF_ADVANCE: 2.74,

  OTTAVA_ALTA: 0xe511,
  OTTAVA_BASSA: 0xe512,
  OTTAVA_ALTA_WIDTH: 3.54,
  OTTAVA_BASSA_WIDTH: 3.356,
  OTTAVA_HEIGHT: 1.85,

  PEDAL_DOWN: 0xe650,
  PEDAL_UP: 0xe655,
  PEDAL_CHANGE: 0xe657,
  PEDAL_DOWN_WIDTH: 4.08,
  PEDAL_UP_WIDTH: 1.8,
  PEDAL_CHANGE_WIDTH: 1.16,
  PEDAL_HEIGHT: 2.22,

  BRACE_HEIGHT: 4.002,
  BRACE_WIDTH: 0.277,

  NOTEHEAD_WHOLE: 0xe0a2,
  NOTEHEAD_HALF: 0xe0a3,
  NOTEHEAD_BLACK: 0xe0a4,
  NOTEHEAD_WHOLE_WIDTH: 1.688,
  NOTEHEAD_WIDTH: 1.18,
  FLAG_8TH_UP: 0xe240,
  FLAG_8TH_DOWN: 0xe241,
  FLAG_16TH_UP: 0xe242,
  FLAG_16TH_DOWN: 0xe243,
  FLAG_32ND_UP: 0xe244,
  FLAG_32ND_DOWN: 0xe245,
  AUGMENTATION_DOT: 0xe1e7,
  STACCATO_ABOVE: 0xe4a2,
  ACCENT_ABOVE: 0xe4a0,
  ACCENT_BELOW: 0xe4a1,
  DOUBLE_SHARP: 0xe263,
  DOUBLE_FLAT: 0xe264,
  DOUBLE_SHARP_ADVANCE: 1.0,
  DOUBLE_FLAT_ADVANCE: 1.652,

  REST_WHOLE: 0xe4e3,
  REST_HALF: 0xe4e4,
  REST_QUARTER: 0xe4e5,
  REST_8TH: 0xe4e6,
  REST_16TH: 0xe4e7,
  REST_32ND: 0xe4e8,
  REST_WHOLE_WIDTH: 1.128,
  REST_HALF_WIDTH: 1.128,
  REST_QUARTER_WIDTH: 1.08,
  REST_8TH_WIDTH: 0.988,
  REST_16TH_WIDTH: 1.28,
  REST_32ND_WIDTH: 1.452,

  TUPLET_COLON: 0xe88a,
  TUPLET_COLON_ADVANCE: 0.444,
  /** Alto de los números de grupo irregular (la línea de base está abajo). */
  TUPLET_DIGIT_HEIGHT: 1.5,

  /** Números de los grupos irregulares: 0xE880 + dígito. */
  tupletDigit: (digit: number): number => 0xe880 + digit,
  tupletDigitAdvance: (digit: number): number => TUPLET_ADVANCES[digit]!,
  /** Dígitos de las firmas de compás: centrados verticalmente sobre su línea de base. */
  timeDigit: (digit: number): number => 0xe080 + digit,
  timeDigitAdvance: (digit: number): number => DIGIT_ADVANCES[digit]!,
} as const;

const TUPLET_ADVANCES = [1.276, 0.984, 1.276, 1.184, 1.212, 1.268, 1.216, 1.212, 1.252, 1.216];
const DIGIT_ADVANCES = [1.88, 1.336, 1.784, 1.684, 1.88, 1.612, 1.736, 1.764, 1.744, 1.736];
