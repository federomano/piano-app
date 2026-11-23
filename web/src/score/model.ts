/*
 * Modelo de datos de la partitura (PIA-88). Es el mismo modelo que app/.../score/Score.kt, pasado a TypeScript.
 * Ver docs/MODELO-PARTITURA.md y docs/CONTRATO-ARCHIVOS.md.
 *
 * El tiempo se mide en "ticks": una negra son 480 ticks, siempre.
 */

/** Cantidad de ticks que dura una negra. Es fijo en todo el modelo. */
export const TICKS_PER_QUARTER = 480;

/** Versión del formato del archivo JSON. */
export const FORMAT_VERSION = 1;

/** Cantidad máxima de voces por mano. */
export const MAX_VOICES = 4;

/** Duraciones comunes, en ticks. */
export const Ticks = {
  WHOLE: 4 * TICKS_PER_QUARTER,
  HALF: 2 * TICKS_PER_QUARTER,
  QUARTER: TICKS_PER_QUARTER,
  EIGHTH: TICKS_PER_QUARTER / 2,
  SIXTEENTH: TICKS_PER_QUARTER / 4,
  /** Fusa: 60 ticks. */
  THIRTY_SECOND: TICKS_PER_QUARTER / 8,
  /** Cuánto antes del tiempo suena una nota de adorno: una fusa. */
  GRACE: TICKS_PER_QUARTER / 8,
  /** Lo más largo que puede ser una nota de adorno: una semicorchea. */
  GRACE_MAX: TICKS_PER_QUARTER / 4,
} as const;

/** Figuras que la app sabe dibujar, con su nombre, de la más larga a la más corta. */
export const FIGURE_BASES: ReadonlyArray<readonly [string, number]> = [
  ["redonda", 1920],
  ["blanca", 960],
  ["negra", 480],
  ["corchea", 240],
  ["semicorchea", 120],
  ["fusa", 60],
];

/** Duraciones permitidas sin grupo irregular (redonda a fusa, con hasta dos puntillos) y cómo se llama cada una. */
export const STANDARD_DURATIONS: ReadonlyMap<number, string> = (() => {
  const result = new Map<number, string>();
  for (const [name, base] of FIGURE_BASES) {
    result.set(base, name);
    if (name !== "redonda") {
      result.set(Math.floor((base * 3) / 2), `${name} con puntillo`);
      result.set(Math.floor((base * 7) / 4), `${name} con dos puntillos`);
    }
  }
  return result;
})();

/** Rango del piano: de La0 (MIDI 21) a Do8 (MIDI 108). */
export const PIANO_MIN_MIDI = 21;
export const PIANO_MAX_MIDI = 108;

export type Hand = "right" | "left";
export const HANDS: readonly Hand[] = ["right", "left"];
export const HAND_LABEL: Readonly<Record<Hand, string>> = { right: "mano derecha", left: "mano izquierda" };

export type Clef = "treble" | "bass";
export type OttavaKind = "8va" | "8vb";
export type DynamicLevel = "ppp" | "pp" | "p" | "mp" | "mf" | "f" | "ff" | "fff";
export const DYNAMIC_LEVELS: readonly DynamicLevel[] = ["ppp", "pp", "p", "mp", "mf", "f", "ff", "fff"];
export type HairpinKind = "crescendo" | "diminuendo";
export const HAIRPIN_KINDS: readonly HairpinKind[] = ["crescendo", "diminuendo"];

/** Con qué intensidad (1..127) suena cada dinámica. */
export const DYNAMIC_VELOCITY: Readonly<Record<DynamicLevel, number>> = {
  ppp: 20,
  pp: 33,
  p: 49,
  mp: 64,
  mf: 80,
  f: 96,
  ff: 112,
  fff: 124,
};

export type Step = "C" | "D" | "E" | "F" | "G" | "A" | "B";
const STEP_SEMITONE: Readonly<Record<Step, number>> = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };

/**
 * Altura de una nota escrita, con su ortografía: Fa# y Solb suenan igual pero se escriben distinto.
 * En texto: `C4` (Do central), `F#4`, `Bb3`, `C##5`. `alter`: +1 sostenido, -1 bemol, +2 doble sostenido, -2 doble bemol.
 */
export interface Pitch {
  readonly step: Step;
  readonly alter: number;
  readonly octave: number;
  /** Número de nota MIDI (Do central = 60, La 440 Hz = 69). */
  readonly midi: number;
}

const PITCH_PATTERN = /^([A-G])(#{1,2}|b{1,2})?(-?[0-9])$/;

/** Lee una altura escrita como `C4`, `F#4` o `Bb3`. Devuelve null si el texto no es una altura. */
export function parsePitch(text: unknown): Pitch | null {
  if (typeof text !== "string") return null;
  const match = PITCH_PATTERN.exec(text.trim());
  if (!match) return null;
  const step = match[1] as Step;
  const accidental = match[2] ?? "";
  const alter = accidental === "" ? 0 : accidental[0] === "#" ? accidental.length : -accidental.length;
  const octave = Number(match[3]);
  return { step, alter, octave, midi: (octave + 1) * 12 + STEP_SEMITONE[step] + alter };
}

/** La altura como se escribe en el archivo: `C4`, `F#4`, `Bb3`. */
export function pitchToString(pitch: Pitch): string {
  const accidental = pitch.alter > 0 ? "#".repeat(pitch.alter) : "b".repeat(-pitch.alter);
  return `${pitch.step}${accidental}${pitch.octave}`;
}

/**
 * Grupo irregular: `actual` figuras en el tiempo de `normal` (tresillo = 3:2, cuatro en el tiempo de tres = 4:3).
 * La duración de la nota (`durationTicks`) es siempre la real, la que suena.
 */
export interface Tuplet {
  readonly actual: number;
  readonly normal: number;
}

const TUPLET_PATTERN = /^(\d):(\d)$/;

/** Lee `"3:2"`. Devuelve null si no es un grupo válido. */
export function parseTuplet(text: unknown): Tuplet | null {
  if (typeof text !== "string") return null;
  const match = TUPLET_PATTERN.exec(text.trim());
  if (!match) return null;
  const actual = Number(match[1]);
  const normal = Number(match[2]);
  if (!(actual >= 2 && actual <= 9 && normal >= 1 && normal <= 8 && actual !== normal)) return null;
  return { actual, normal };
}

/** Duración de la figura tal como se escribe, dada la duración real; null si el grupo no da un número entero. */
export function writtenTicks(realTicks: number, tuplet: Tuplet | null): number | null {
  if (tuplet === null) return realTicks;
  return (realTicks * tuplet.actual) % tuplet.normal === 0 ? Math.floor((realTicks * tuplet.actual) / tuplet.normal) : null;
}

export interface Note {
  readonly hand: Hand;
  readonly pitch: Pitch;
  readonly startTick: number;
  readonly durationTicks: number;
  /** Ligadura de prolongación: sigue sonando con la nota igual que empieza justo después. */
  readonly tiedToNext: boolean;
  /** Dedo sugerido, de 1 (pulgar) a 5 (meñique). */
  readonly finger: number | null;
  /** Grupo irregular al que pertenece. Null = figura común. */
  readonly tuplet: Tuplet | null;
  /** Voz dentro de la mano, de 1 a MAX_VOICES. La voz 1 es la principal. */
  readonly voice: number;
  /** Nota de adorno: se toca justo antes de la nota principal y no cuenta para llenar el compás. */
  readonly grace: boolean;
  readonly staccato: boolean;
  readonly accent: boolean;
}

export interface Rest {
  readonly hand: Hand;
  readonly startTick: number;
  readonly durationTicks: number;
  readonly tuplet: Tuplet | null;
  readonly voice: number;
}

export interface TimeSignatureChange {
  readonly startTick: number;
  readonly numerator: number;
  readonly denominator: number;
}

export interface KeySignatureChange {
  readonly startTick: number;
  /** Positivo = sostenidos, negativo = bemoles (0 = Do mayor / La menor). */
  readonly fifths: number;
}

export interface TempoChange {
  readonly startTick: number;
  /** Negras por minuto. */
  readonly bpm: number;
}

export interface DynamicMark {
  readonly startTick: number;
  readonly level: DynamicLevel;
}

export interface Hairpin {
  readonly startTick: number;
  readonly endTick: number;
  readonly kind: HairpinKind;
}

export interface ClefChange {
  readonly hand: Hand;
  readonly startTick: number;
  readonly clef: Clef;
}

export interface OttavaMark {
  readonly hand: Hand;
  readonly startTick: number;
  readonly endTick: number;
  readonly kind: OttavaKind;
}

export interface PedalMark {
  readonly startTick: number;
  readonly endTick: number;
}

export interface Score {
  readonly formatVersion: number;
  readonly title: string;
  readonly composer: string | null;
  readonly level: string | null;
  readonly goal: string | null;
  readonly timeSignatures: readonly TimeSignatureChange[];
  readonly keySignatures: readonly KeySignatureChange[];
  readonly tempos: readonly TempoChange[];
  /** Anacrusa: duración (en ticks) del primer compás cuando es más corto que los demás. 0 = no hay. */
  readonly pickupTicks: number;
  readonly dynamics: readonly DynamicMark[];
  readonly hairpins: readonly Hairpin[];
  readonly ottavas: readonly OttavaMark[];
  readonly pedals: readonly PedalMark[];
  readonly clefs: readonly ClefChange[];
  readonly notes: readonly Note[];
  readonly rests: readonly Rest[];
}

/** Cuándo termina una nota (sin contar adornos). */
export function noteEndTick(note: Note): number {
  return note.startTick + note.durationTicks;
}

/** Cuándo empieza a sonar: una nota común, en su inicio; un adorno, su duración antes (si hay lugar). */
export function soundStartTick(note: Note): number {
  return note.grace && note.startTick >= note.durationTicks ? note.startTick - note.durationTicks : note.startTick;
}

/** Cuándo deja de sonar: un adorno termina donde empieza la nota principal. */
export function soundEndTick(note: Note): number {
  if (!note.grace) return noteEndTick(note);
  return note.startTick >= note.durationTicks ? note.startTick : note.startTick + note.durationTicks;
}

/** Fin de la partitura: donde termina la última nota o silencio (sin contar adornos). */
export function lengthTicks(score: Score): number {
  let end = 0;
  for (const note of score.notes) if (!note.grace) end = Math.max(end, noteEndTick(note));
  for (const rest of score.rests) end = Math.max(end, rest.startTick + rest.durationTicks);
  return end;
}

/** Clave vigente de una mano en un tick. La derecha empieza en sol y la izquierda en fa. */
export function clefAt(score: Score, hand: Hand, tick: number): Clef {
  let found: Clef | null = null;
  for (const change of score.clefs) if (change.hand === hand && change.startTick <= tick) found = change.clef;
  return found ?? (hand === "right" ? "treble" : "bass");
}

/** Cuántas octavas se escribe más abajo (8va: 1) o más arriba (8vb: -1) lo que empieza en un tick de una mano. */
export function octaveShiftAt(score: Score, hand: Hand, tick: number): number {
  const mark = score.ottavas.find((o) => o.hand === hand && tick >= o.startTick && tick < o.endTick);
  if (!mark) return 0;
  return mark.kind === "8va" ? 1 : -1;
}
