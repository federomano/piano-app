/*
 * Marcas del pedal de resonancia (PIA-89), debajo del pentagrama de la izquierda: «Ped.» donde se aprieta y una
 * estrella donde se suelta. Mismo código que PedalLayout.kt. Si se suelta y se vuelve a apretar en el mismo instante
 * (cambio de pedal), va un solo signo en ^. Quedan por debajo de lo ya dibujado en esa zona.
 */
import type { Score } from "../model";
import { type DrawOp, Smufl, glyph } from "./drawops";
import { type Box, lowest } from "./extents";
import { Geometry } from "./geometry";

const GAP = 0.5;
const MIN_BELOW_SYSTEM = 3.2;
/** «Ped.» empieza un poco antes de la nota; la estrella termina un poco antes del instante en que se suelta. */
const PED_LEAD = 0.3;
const UP_LEAD = 0.3;

export function build(score: Score, xOfTick: (tick: number) => number, boxes: readonly Box[]): DrawOp[] {
  const ops: DrawOp[] = [];
  const place = (code: number, left: number, width: number) => {
    const low = lowest(boxes, left, left + width, Geometry.SYSTEM_BOTTOM);
    const baseline = Math.max(Geometry.SYSTEM_BOTTOM + MIN_BELOW_SYSTEM, low + GAP + Smufl.PEDAL_HEIGHT);
    ops.push(glyph(code, left, baseline));
  };
  score.pedals.forEach((pedal, i) => {
    const changesNext = score.pedals[i + 1]?.startTick === pedal.endTick;
    const changesPrevious = score.pedals[i - 1]?.endTick === pedal.startTick;
    if (!changesPrevious) place(Smufl.PEDAL_DOWN, xOfTick(pedal.startTick) - PED_LEAD, Smufl.PEDAL_DOWN_WIDTH);
    if (changesNext) place(Smufl.PEDAL_CHANGE, xOfTick(pedal.endTick) - Smufl.PEDAL_CHANGE_WIDTH / 2, Smufl.PEDAL_CHANGE_WIDTH);
    else place(Smufl.PEDAL_UP, xOfTick(pedal.endTick) - Smufl.PEDAL_UP_WIDTH - UP_LEAD, Smufl.PEDAL_UP_WIDTH);
  });
  return ops;
}
