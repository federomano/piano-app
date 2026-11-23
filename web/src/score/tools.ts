/*
 * Herramientas sobre la partitura (PIA-89): compases, acordes, notas como suenan e intensidad.
 * Es el mismo código que app/.../score/ScoreTools.kt, pasado a TypeScript.
 */
import {
  type DynamicMark,
  type Hairpin,
  type Hand,
  type Note,
  type Score,
  type TimeSignatureChange,
  type Tuplet,
  DYNAMIC_VELOCITY,
  lengthTicks,
  soundEndTick,
  soundStartTick,
} from "./model";

/** Cuántos ticks dura un compás de esta firma. */
export function measureTicks(signature: TimeSignatureChange): number {
  return Math.floor((signature.numerator * 1920) / signature.denominator);
}

/** Un compás calculado a partir de las firmas de compás y armaduras. */
export interface Measure {
  /** Número de compás: desde 1; la anacrusa (si hay) es el 0. */
  readonly number: number;
  readonly startTick: number;
  readonly endTick: number;
  readonly timeSignature: TimeSignatureChange;
  /** Armadura vigente en este compás. */
  readonly keyFifths: number;
  /** Cuántos ticks antes del inicio del compás "empieza" su grilla de tiempos (solo la anacrusa lo usa). */
  readonly gridOffset: number;
}

/** Notas de una misma mano que empiezan juntas y duran lo mismo. */
export interface Chord {
  readonly hand: Hand;
  readonly startTick: number;
  readonly durationTicks: number;
  /** Ordenadas de grave a aguda. */
  readonly notes: readonly Note[];
  readonly tuplet: Tuplet | null;
  readonly voice: number;
  readonly endTick: number;
  readonly isChord: boolean;
}

function lastWhere<T>(items: readonly T[], test: (item: T) => boolean): T | undefined {
  for (let i = items.length - 1; i >= 0; i--) if (test(items[i]!)) return items[i];
  return undefined;
}

/** Compases de la partitura, de 1 (o 0, si hay anacrusa) hasta el que contiene el final. Siempre hay al menos uno. */
export function measures(score: Score): Measure[] {
  const result: Measure[] = [];
  const end = lengthTicks(score);
  let tick = 0;
  let number = score.pickupTicks > 0 ? 0 : 1;
  do {
    const signature =
      lastWhere(score.timeSignatures, (s) => s.startTick <= tick) ?? { startTick: 0, numerator: 4, denominator: 4 };
    const key = lastWhere(score.keySignatures, (k) => k.startTick <= tick)?.fifths ?? 0;
    const isPickup = score.pickupTicks > 0 && tick === 0;
    const full = measureTicks(signature);
    const length = isPickup ? Math.min(score.pickupTicks, full) : full;
    result.push({
      number,
      startTick: tick,
      endTick: tick + length,
      timeSignature: signature,
      keyFifths: key,
      gridOffset: isPickup ? full - length : 0,
    });
    tick += length;
    number++;
  } while (tick < end);
  return result;
}

/** Acordes y notas sueltas, ordenados por inicio (mano derecha antes que izquierda, voz 1 antes que 2). */
export function chords(score: Score, hand: Hand | null = null): Chord[] {
  const groups = new Map<string, Note[]>();
  for (const note of score.notes) {
    if (note.grace || (hand !== null && note.hand !== hand)) continue;
    const key = `${note.hand}|${note.voice}|${note.startTick}|${note.durationTicks}`;
    const group = groups.get(key);
    if (group) group.push(note);
    else groups.set(key, [note]);
  }
  const result: Chord[] = [];
  for (const group of groups.values()) {
    const first = group[0]!;
    const notes = [...group].sort((a, b) => a.pitch.midi - b.pitch.midi);
    result.push({
      hand: first.hand,
      startTick: first.startTick,
      durationTicks: first.durationTicks,
      notes,
      tuplet: first.tuplet,
      voice: first.voice,
      endTick: first.startTick + first.durationTicks,
      isChord: notes.length > 1,
    });
  }
  const handOrder = (h: Hand) => (h === "right" ? 0 : 1);
  return result.sort(
    (a, b) => a.startTick - b.startTick || handOrder(a.hand) - handOrder(b.hand) || a.voice - b.voice,
  );
}

/** Las notas como suenan: un adorno se toca justo antes de su tiempo; el staccato suena la mitad. */
export function playedNotes(score: Score): Note[] {
  return score.notes.map((n) => {
    if (n.grace) {
      return { ...n, startTick: soundStartTick(n), durationTicks: soundEndTick(n) - soundStartTick(n), grace: false };
    }
    if (n.staccato) return { ...n, durationTicks: Math.max(1, Math.trunc(n.durationTicks / 2)) };
    return n;
  });
}

/** Cuánto más fuerte suena una nota con acento que la dinámica vigente. */
export const ACCENT_BOOST = 20;
/** Intensidad con la que suena todo si la partitura no trae dinámicas. */
export const DEFAULT_VELOCITY = 90;
const FALLBACK_VELOCITY = 80;
const HAIRPIN_STEP = 24;

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** Con qué intensidad suena [note]: la dinámica vigente en su inicio y, si lleva acento, un empujón más. */
export function velocityOf(score: Score, note: Note): number {
  return clamp(velocityAt(score, note.startTick) + (note.accent ? ACCENT_BOOST : 0), 1, 127);
}

function hairpinTarget(bar: Hairpin, marks: readonly DynamicMark[], settled: (tick: number) => number): number {
  const from = settled(bar.startTick);
  const nextMark = marks.find((m) => m.startTick >= bar.endTick);
  const next = nextMark ? DYNAMIC_VELOCITY[nextMark.level] : null;
  const up = bar.kind === "crescendo";
  const target = next !== null && (up ? next >= from : next <= from) ? next : from + (up ? HAIRPIN_STEP : -HAIRPIN_STEP);
  return clamp(target, 1, 127);
}

function settledAt(marks: readonly DynamicMark[], bars: readonly Hairpin[], at: number): number {
  const mark = lastWhere(marks, (m) => m.startTick <= at);
  let bar: Hairpin | undefined;
  for (const b of bars) if (b.endTick <= at && (bar === undefined || b.endTick > bar.endTick)) bar = b;
  if (bar !== undefined && (mark === undefined || bar.endTick > mark.startTick)) {
    return hairpinTarget(bar, marks, (t) => settledAt(marks, bars, t));
  }
  if (mark !== undefined) return DYNAMIC_VELOCITY[mark.level];
  const first = marks[0];
  return first ? DYNAMIC_VELOCITY[first.level] : FALLBACK_VELOCITY;
}

function velocityInside(marks: readonly DynamicMark[], bars: readonly Hairpin[], tick: number): number {
  const inside = bars.find((b) => tick >= b.startTick && tick < b.endTick);
  if (!inside) return settledAt(marks, bars, tick);
  const from = settledAt(marks, bars, inside.startTick);
  const to = hairpinTarget(inside, marks, (t) => settledAt(marks, bars, t));
  const fraction = (tick - inside.startTick) / (inside.endTick - inside.startTick);
  return clamp(Math.trunc(from + (to - from) * fraction), 1, 127);
}

/** Con qué intensidad (1..127) suena una nota que empieza en [tick]. */
export function velocityAt(score: Score, tick: number): number {
  if (score.dynamics.length === 0 && score.hairpins.length === 0) return DEFAULT_VELOCITY;
  const marks = [...score.dynamics].sort((a, b) => a.startTick - b.startTick);
  const bars = [...score.hairpins].sort((a, b) => a.startTick - b.startTick);
  return velocityInside(marks, bars, tick);
}
