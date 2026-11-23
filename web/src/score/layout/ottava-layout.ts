/*
 * Signos de octava (PIA-89): el "8va" (o "8vb") con una línea punteada que llega hasta la última nota y un
 * remate que apunta a las notas. Mismo código que OttavaLayout.kt. Va arriba del pentagrama (8va) o abajo (8vb),
 * esquivando lo que haya ahí.
 */
import type { Score } from "../model";
import { type DrawOp, Smufl, glyph, rect } from "./drawops";
import { type Box, highest, lowest } from "./extents";
import { Geometry } from "./geometry";

const GAP = 0.6;
const DASH = 0.55;
const DASH_GAP = 0.4;
const LINE_THICKNESS = 0.12;
const HOOK = 1.0;
const TEXT_TO_LINE = 0.3;
const END_PAD = 0.5;

/** [boxes]: lo ya dibujado (sin las barras de compás, que cruzan todo el sistema). */
export function build(score: Score, xOfTick: (tick: number) => number, boxes: readonly Box[]): DrawOp[] {
  const ops: DrawOp[] = [];
  for (const mark of score.ottavas) {
    const staffTop = mark.hand === "right" ? Geometry.TREBLE_TOP : Geometry.BASS_TOP;
    const above = mark.kind === "8va";
    const textWidth = above ? Smufl.OTTAVA_ALTA_WIDTH : Smufl.OTTAVA_BASSA_WIDTH;
    const x0 = xOfTick(mark.startTick) - 0.2;
    const x1 = Math.max(xOfTick(mark.endTick) - END_PAD, x0 + textWidth + 1);
    // Línea de base del signo: sobre lo más alto que haya (8va) o debajo de lo más bajo (8vb).
    const signBaseline = above
      ? highest(boxes, x0, x1, staffTop) - GAP
      : lowest(boxes, x0, x1, staffTop + 4) + GAP + Smufl.OTTAVA_HEIGHT;
    ops.push(glyph(above ? Smufl.OTTAVA_ALTA : Smufl.OTTAVA_BASSA, x0, signBaseline));
    const lineY = signBaseline - Smufl.OTTAVA_HEIGHT / 2;
    let x = x0 + textWidth + TEXT_TO_LINE;
    while (x < x1) {
      const w = Math.min(DASH, x1 - x);
      ops.push(rect(x, lineY - LINE_THICKNESS / 2, w, LINE_THICKNESS));
      x += DASH + DASH_GAP;
    }
    // Remate: una rayita que apunta a las notas.
    ops.push(
      above
        ? rect(x1 - LINE_THICKNESS, lineY - LINE_THICKNESS / 2, LINE_THICKNESS, HOOK)
        : rect(x1 - LINE_THICKNESS, lineY + LINE_THICKNESS / 2 - HOOK, LINE_THICKNESS, HOOK),
    );
  }
  return ops;
}
