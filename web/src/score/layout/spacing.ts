/*
 * Separación horizontal dentro de un compás (PIA-89). Mismo código que app/.../score/layout/Spacing.kt.
 *
 * Cada instante en que empieza una nota o un silencio es un "punto de anclaje". Normalmente la distancia
 * entre dos puntos es proporcional al tiempo; si ese espacio no alcanza para dibujar lo que hay (cabeza,
 * puntillo, alteración del siguiente, silencio), se agranda lo justo.
 */
import { type Score, Ticks } from "../model";
import type { Measure } from "../tools";
import { Smufl } from "./drawops";
import { figureOf, keyAlter } from "./note-layout";

const MARGIN = 0.4;
const END_GAP = 1.0;
const LAST_MEASURE_EXTRA_GAP = 1.0;
const ACCIDENTAL = 1.3;
const DOTS = 0.9;
/** Lugar a la izquierda de una nota para su adorno chiquito, y un poco más si lleva alteración. */
const GRACE = 1.9;
const GRACE_ACCIDENTAL = 0.8;

/** Puntos de anclaje de un compás: instantes y su distancia (sp) al comienzo del tiempo del compás. */
export class Anchors {
  constructor(
    readonly ticks: readonly number[],
    readonly offsets: readonly number[],
    readonly noteTicks: ReadonlySet<number> = new Set(),
    /** Lugar extra que hay que dejar antes de la primera nota del compás (para el adorno de esa nota). */
    readonly leadExtra = 0,
  ) {}

  /** Posición (relativa al comienzo del tiempo del compás) de un instante; entre dos puntos se interpola. */
  offsetOf(tick: number, perTick: number): number {
    const { ticks, offsets } = this;
    if (ticks.length === 0) return 0;
    if (tick <= ticks[0]!) return offsets[0]! + (tick - ticks[0]!) * perTick;
    if (tick >= ticks[ticks.length - 1]!) return offsets[offsets.length - 1]!;
    let i = ticks.length - 1;
    while (i > 0 && ticks[i]! > tick) i--;
    const span = ticks[i + 1]! - ticks[i]!;
    return offsets[i]! + ((offsets[i + 1]! - offsets[i]!) * (tick - ticks[i]!)) / span;
  }

  /** Lo inverso: el instante que corresponde a una posición (relativa al comienzo del tiempo del compás). */
  tickAt(offset: number, perTick: number): number {
    const { ticks, offsets } = this;
    if (ticks.length === 0) return 0;
    if (offset <= offsets[0]!) return ticks[0]! + (offset - offsets[0]!) / perTick;
    if (offset >= offsets[offsets.length - 1]!) return ticks[ticks.length - 1]!;
    let i = offsets.length - 1;
    while (i > 0 && offsets[i]! > offset) i--;
    return ticks[i]! + ((ticks[i + 1]! - ticks[i]!) * (offset - offsets[i]!)) / (offsets[i + 1]! - offsets[i]!);
  }
}

function restWidth(base: number): number {
  switch (base) {
    case Ticks.WHOLE:
      return Smufl.REST_WHOLE_WIDTH;
    case Ticks.HALF:
      return Smufl.REST_HALF_WIDTH;
    case Ticks.QUARTER:
      return Smufl.REST_QUARTER_WIDTH;
    case Ticks.EIGHTH:
      return Smufl.REST_8TH_WIDTH;
    case Ticks.SIXTEENTH:
      return Smufl.REST_16TH_WIDTH;
    default:
      return Smufl.REST_32ND_WIDTH;
  }
}

function mergeMax(map: Map<number, number>, key: number, value: number): void {
  const old = map.get(key);
  map.set(key, old === undefined ? value : Math.max(old, value));
}

export function anchors(score: Score, m: Measure, perTick: number, isLast: boolean): Anchors {
  const right = new Map<number, number>();
  const left = new Map<number, number>();
  const inside = (tick: number) => tick >= m.startTick && tick < m.endTick;

  // Alteraciones: se lleva la cuenta de lo que ya valía en el compás, por mano y octava.
  const state = new Map<string, number>();
  const ordered = score.notes.filter((n) => !n.grace && inside(n.startTick)).sort((a, b) => a.startTick - b.startTick);
  for (const note of ordered) {
    const figure = figureOf(note.durationTicks, note.tuplet);
    const head = figure.base === Ticks.WHOLE ? Smufl.NOTEHEAD_WHOLE_WIDTH : Smufl.NOTEHEAD_WIDTH;
    mergeMax(right, note.startTick, head + (figure.dots > 0 ? DOTS : 0));
    const key = `${note.hand}|${note.pitch.step}|${note.pitch.octave}`;
    const current = state.get(key) ?? keyAlter(m.keyFifths, note.pitch.step);
    if (note.pitch.alter !== current) mergeMax(left, note.startTick, ACCIDENTAL);
    state.set(key, note.pitch.alter);
  }
  // Adornos: lugar extra a la izquierda de la nota a la que llevan (después de su propia alteración).
  const graceSpace = new Map<number, number>();
  for (const grace of score.notes.filter((n) => n.grace && inside(n.startTick))) {
    const need = GRACE + (grace.pitch.alter !== keyAlter(m.keyFifths, grace.pitch.step) ? GRACE_ACCIDENTAL : 0);
    mergeMax(graceSpace, grace.startTick, need);
  }
  for (const [tick, extra] of graceSpace) left.set(tick, (left.get(tick) ?? 0) + extra);
  for (const rest of score.rests.filter((r) => inside(r.startTick))) {
    const figure = figureOf(rest.durationTicks, rest.tuplet);
    mergeMax(right, rest.startTick, restWidth(figure.base) + (figure.dots > 0 ? DOTS : 0));
  }

  const ticks = [...new Set([...right.keys(), ...left.keys(), m.startTick, m.endTick])].sort((a, b) => a - b);
  const offsets = [0];
  for (let i = 1; i < ticks.length; i++) {
    const natural = (ticks[i]! - ticks[i - 1]!) * perTick;
    const toEnd = i === ticks.length - 1;
    const needed =
      (right.get(ticks[i - 1]!) ?? 0) +
      (toEnd ? END_GAP + (isLast ? LAST_MEASURE_EXTRA_GAP : 0) : MARGIN + (left.get(ticks[i]!) ?? 0));
    offsets.push(offsets[offsets.length - 1]! + Math.max(natural, right.has(ticks[i - 1]!) || toEnd ? needed : 0));
  }
  const noteTicks = new Set(ordered.map((n) => n.startTick));
  return new Anchors(ticks, offsets, noteTicks, graceSpace.get(m.startTick) ?? 0);
}
