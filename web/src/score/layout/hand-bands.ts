/*
 * De qué mano es cada cosa dibujada (PIA-89), para poder atenuar la mano silenciada. Mismo código que HandBands.kt.
 * Se decide por la altura: lo que cae en la mitad de arriba del sistema es de la derecha y lo de abajo, de la izquierda.
 * Las barras de compás, los números, las dinámicas y los reguladores no son de ninguna mano.
 */
import type { DrawOp } from "./drawops";
import { Geometry } from "./geometry";

export const NONE = 0;
export const RIGHT = 1;
export const LEFT = 2;

/** Altura que separa las dos manos: la mitad del espacio entre los dos pentagramas. */
const SPLIT = (Geometry.TREBLE_TOP + 4 + Geometry.BASS_TOP) / 2;

const side = (y: number): number => (y < SPLIT ? RIGHT : LEFT);

export function of(op: DrawOp): number {
  switch (op.kind) {
    case "text":
      return NONE;
    case "rect":
      return op.height >= Geometry.SYSTEM_BOTTOM - 0.01 ? NONE : side(op.y + op.height / 2);
    case "glyph":
      return op.codepoint >= 0xe520 && op.codepoint <= 0xe53f ? NONE : side(op.y);
    case "polygon": {
      let min = Infinity;
      let max = -Infinity;
      for (let i = 1; i < op.points.length; i += 2) {
        min = Math.min(min, op.points[i]!);
        max = Math.max(max, op.points[i]!);
      }
      return min > Geometry.SYSTEM_BOTTOM + 0.3 ? NONE : side((min + max) / 2);
    }
  }
}
