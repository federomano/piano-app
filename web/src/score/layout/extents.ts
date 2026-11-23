/*
 * Cuánto lugar ocupa lo dibujado (PIA-89). Mismo código que app/.../score/layout/Extents.kt.
 * Sirve para dejar margen arriba y abajo según lo que de verdad hay y para que las letras de las
 * dinámicas y los números de compás esquiven lo que tienen cerca.
 */
import { type DrawOp, type GlyphOp, Smufl } from "./drawops";

/** Caja que ocupa una instrucción de dibujo, en espacios de pentagrama. Es una estimación algo generosa. */
export interface Box {
  readonly left: number;
  readonly top: number;
  readonly right: number;
  readonly bottom: number;
}

export const overlapsX = (box: Box, from: number, to: number): boolean => box.right > from && box.left < to;

const DEFAULT_GLYPH_WIDTH = 1.6;
const DEFAULT_GLYPH_REACH = 1.2;

function glyphBox(op: GlyphOp): Box {
  const s = op.scale;
  const c = op.codepoint;
  const { x, y } = op;
  if (c >= 0xe880 && c <= 0xe88a) return { left: x, top: y - Smufl.TUPLET_DIGIT_HEIGHT, right: x + 1.4, bottom: y };
  if (c >= 0xe520 && c <= 0xe53f) return { left: x, top: y - 1.8, right: x + 3.4, bottom: y + 0.7 };
  if (c === Smufl.OTTAVA_ALTA) return { left: x, top: y - Smufl.OTTAVA_HEIGHT, right: x + Smufl.OTTAVA_ALTA_WIDTH, bottom: y };
  if (c === Smufl.OTTAVA_BASSA) return { left: x, top: y - Smufl.OTTAVA_HEIGHT, right: x + Smufl.OTTAVA_BASSA_WIDTH, bottom: y };
  if (c === Smufl.PEDAL_DOWN) return { left: x, top: y - Smufl.PEDAL_HEIGHT, right: x + Smufl.PEDAL_DOWN_WIDTH, bottom: y + 0.03 };
  if (c === Smufl.PEDAL_UP) return { left: x, top: y - Smufl.PEDAL_UP_WIDTH, right: x + Smufl.PEDAL_UP_WIDTH, bottom: y };
  if (c === Smufl.PEDAL_CHANGE) return { left: x, top: y - Smufl.PEDAL_HEIGHT, right: x + Smufl.PEDAL_CHANGE_WIDTH, bottom: y };
  if (c === Smufl.ACCENT_ABOVE) return { left: x, top: y - 0.98, right: x + 1.36, bottom: y };
  if (c === Smufl.ACCENT_BELOW) return { left: x, top: y, right: x + 1.36, bottom: y + 0.98 };
  if (c === Smufl.STACCATO_ABOVE) return { left: x, top: y - 0.34, right: x + 0.34, bottom: y };
  return { left: x, top: y - DEFAULT_GLYPH_REACH * s, right: x + DEFAULT_GLYPH_WIDTH * s, bottom: y + DEFAULT_GLYPH_REACH * s };
}

export function box(op: DrawOp): Box {
  switch (op.kind) {
    case "rect":
      return { left: op.x, top: op.y, right: op.x + op.width, bottom: op.y + op.height };
    case "polygon": {
      let left = Infinity;
      let right = -Infinity;
      let top = Infinity;
      let bottom = -Infinity;
      for (let i = 0; i < op.points.length; i += 2) {
        left = Math.min(left, op.points[i]!);
        right = Math.max(right, op.points[i]!);
        top = Math.min(top, op.points[i + 1]!);
        bottom = Math.max(bottom, op.points[i + 1]!);
      }
      return { left, top, right, bottom };
    }
    case "text":
      return { left: op.x, top: op.y - op.size, right: op.x + op.size * 0.6 * op.text.length, bottom: op.y };
    case "glyph":
      return glyphBox(op);
  }
}

export const boxes = (ops: readonly DrawOp[]): Box[] => ops.map(box);

/** Lo más bajo que llega lo dibujado entre [from] y [to] (nunca menos que [atLeast]). */
export function lowest(list: readonly Box[], from: number, to: number, atLeast: number): number {
  let result = atLeast;
  for (const b of list) if (overlapsX(b, from, to)) result = Math.max(result, b.bottom);
  return result;
}

/** Lo más alto que llega lo dibujado entre [from] y [to] (nunca más abajo que [atMost]). */
export function highest(list: readonly Box[], from: number, to: number, atMost: number): number {
  let result = atMost;
  for (const b of list) if (overlapsX(b, from, to)) result = Math.min(result, b.top);
  return result;
}
