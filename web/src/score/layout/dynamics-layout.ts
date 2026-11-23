/*
 * Dinámicas (letras) y reguladores (PIA-89), debajo del pentagrama de la mano izquierda: valen para las dos manos.
 * Mismo código que DynamicsLayout.kt. Las letras son símbolos de Bravura; los reguladores son dos líneas finas
 * que se abren o se cierran.
 */
import type { DynamicLevel, DynamicMark, Hairpin, Score } from "../model";
import { type DrawOp, glyph, polygon } from "./drawops";
import { Geometry } from "./geometry";

/** Línea de base de las letras, debajo del pentagrama de abajo. */
const BASELINE = Geometry.SYSTEM_BOTTOM + 4.0;
/** Aire entre lo que hay arriba y lo más alto de la letra. */
const CLEARANCE = 2.1;
const HAIRPIN_OPEN = 0.65;
const HAIRPIN_CLOSED = 0.05;
const HAIRPIN_THICKNESS = 0.14;
const GAP = 0.4;

/** Símbolo de cada letra y hasta dónde se extiende a izquierda y derecha de su punto de partida (medido en la fuente). */
interface Letter {
  readonly codepoint: number;
  readonly left: number;
  readonly right: number;
}

const LETTERS: Readonly<Record<DynamicLevel, Letter>> = {
  ppp: { codepoint: 0xe52a, left: -0.368, right: 4.292 },
  pp: { codepoint: 0xe52b, left: -0.328, right: 2.912 },
  p: { codepoint: 0xe520, left: -0.356, right: 1.464 },
  mp: { codepoint: 0xe52c, left: -0.08, right: 3.3 },
  mf: { codepoint: 0xe52d, left: -0.08, right: 3.272 },
  f: { codepoint: 0xe522, left: -0.564, right: 1.456 },
  ff: { codepoint: 0xe52f, left: -0.54, right: 2.44 },
  fff: { codepoint: 0xe530, left: -0.62, right: 3.32 },
};

/**
 * [lowestBetween] dice hasta dónde llega lo dibujado entre dos posiciones: si hay notas graves o acentos debajo del
 * pentagrama, las letras y los reguladores bajan para no pisarlos. Las letras pegadas a un regulador van a su misma altura.
 */
export function build(
  score: Score,
  xOfTick: (tick: number) => number,
  lowestBetween: (from: number, to: number) => number = () => Geometry.SYSTEM_BOTTOM,
): DrawOp[] {
  const ops: DrawOp[] = [];
  // Línea de base que necesita cada letra y cada regulador: la de siempre, o más abajo si hay algo debajo.
  const baselineFor = (from: number, to: number) => Math.max(BASELINE, lowestBetween(from, to) + CLEARANCE);
  const markBaseline = new Map<DynamicMark, number>();
  for (const mark of score.dynamics) {
    const l = LETTERS[mark.level];
    const x = xOfTick(mark.startTick);
    markBaseline.set(mark, baselineFor(x, x + l.right - l.left));
  }
  const hairpinBaseline = new Map<Hairpin, number>();
  for (const bar of score.hairpins) hairpinBaseline.set(bar, baselineFor(xOfTick(bar.startTick), xOfTick(bar.endTick)));
  for (const bar of score.hairpins) {
    const touching = score.dynamics.filter((d) => d.startTick === bar.startTick || d.startTick === bar.endTick);
    const shared = Math.max(...touching.map((d) => markBaseline.get(d)!), hairpinBaseline.get(bar)!);
    hairpinBaseline.set(bar, shared);
    for (const d of touching) markBaseline.set(d, shared);
  }
  for (const mark of score.dynamics) {
    const l = LETTERS[mark.level];
    ops.push(glyph(l.codepoint, xOfTick(mark.startTick) - l.left, markBaseline.get(mark)!));
  }
  for (const bar of score.hairpins) {
    const center = hairpinBaseline.get(bar)! - 0.5;
    let x0 = xOfTick(bar.startTick);
    let x1 = xOfTick(bar.endTick);
    // Si empieza justo después de una letra (p <), arranca pasando la letra; si termina en una letra (< f), le deja lugar.
    const starting = score.dynamics.find((d) => d.startTick === bar.startTick);
    if (starting) {
      const l = LETTERS[starting.level];
      x0 += l.right - l.left + GAP;
    }
    x1 -= GAP;
    if (x1 - x0 < 1) x1 = x0 + 1;
    const [open0, open1] = bar.kind === "crescendo" ? [HAIRPIN_CLOSED, HAIRPIN_OPEN] : [HAIRPIN_OPEN, HAIRPIN_CLOSED];
    for (const side of [-1, 1]) {
      const ya = center + side * open0;
      const yb = center + side * open1;
      const h = HAIRPIN_THICKNESS / 2;
      ops.push(polygon([x0, ya - h, x1, yb - h, x1, yb + h, x0, ya + h]));
    }
  }
  return ops;
}
