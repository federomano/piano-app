/*
 * Ayudas para aprender a leer (PIA-89): el nombre de cada nota en Do-Re-Mi y el número de dedo.
 * Mismo código que NoteLabelLayout.kt. Van en una fila arriba del pentagrama de la derecha y abajo del de la
 * izquierda, alineadas con la nota, siempre por fuera de lo ya dibujado. Si dos textos chocan, el segundo se aleja.
 */
import { type Hand, type Note, type Pitch, type Score, type Step, pitchToString } from "../model";
import { chords } from "../tools";
import { type TextOp, text as textOp } from "./drawops";
import { type Box, box, overlapsX } from "./extents";
import { Geometry } from "./geometry";

/** Un texto de ayuda (nombre de nota y/o dedo) y la mano a la que pertenece (para atenuarlo junto con ella). */
export interface NoteLabel {
  readonly op: TextOp;
  readonly hand: Hand;
}

export const SIZE = 1.1;
const GAP = 0.35;
const CHAR_WIDTH = 0.6;
const HEAD_CENTER = 0.6;
const PAD = 0.1;
/** Más ancho que esto: se revisa siempre (reguladores, ligaduras largas); lo demás se busca por cercanía. */
const WIDE = 8;

const NAMES: Readonly<Record<Step, string>> = { C: "Do", D: "Re", E: "Mi", F: "Fa", G: "Sol", A: "La", B: "Si" };

/** Nombre en Do-Re-Mi, con alteración: Do#, Sib, Fa## (la octava no se escribe). */
export function name(pitch: Pitch): string {
  return NAMES[pitch.step] + (pitch.alter > 0 ? "#".repeat(pitch.alter) : "b".repeat(-pitch.alter));
}

export function text(note: Note, names: boolean, fingers: boolean): string {
  const parts: string[] = [];
  if (names) parts.push(name(note.pitch));
  if (fingers && note.finger !== null) parts.push(String(note.finger));
  return parts.join(" ");
}

/** Lo que ya hay dibujado, buscado por cercanía horizontal. */
class Obstacles {
  private readonly wide: Box[];
  private readonly narrow: Box[];
  private readonly lefts: number[];
  private placed: Box[] = [];

  constructor(boxes: readonly Box[]) {
    this.wide = boxes.filter((b) => b.right - b.left > WIDE);
    this.narrow = boxes.filter((b) => b.right - b.left <= WIDE).sort((a, b) => a.left - b.left);
    this.lefts = this.narrow.map((b) => b.left);
  }

  private firstIndexFrom(x: number): number {
    let lo = 0;
    let hi = this.lefts.length;
    while (lo < hi) {
      const mid = (lo + hi) >>> 1;
      if (this.lefts[mid]! < x) lo = mid + 1;
      else hi = mid;
    }
    return lo;
  }

  private forEachNear(from: number, to: number, action: (b: Box) => void): void {
    for (const b of this.wide) if (overlapsX(b, from, to)) action(b);
    let i = this.firstIndexFrom(from - WIDE);
    while (i < this.narrow.length && this.narrow[i]!.left < to) {
      if (overlapsX(this.narrow[i]!, from, to)) action(this.narrow[i]!);
      i++;
    }
    // Los textos se colocan de izquierda a derecha: los que quedaron muy atrás ya no estorban.
    this.placed = this.placed.filter((b) => !(b.right < from - WIDE));
    for (const b of this.placed) if (overlapsX(b, from, to)) action(b);
  }

  highest(from: number, to: number, atMost: number): number {
    let result = atMost;
    this.forEachNear(from, to, (b) => (result = Math.min(result, b.top)));
    return result;
  }

  lowest(from: number, to: number, atLeast: number): number {
    let result = atLeast;
    this.forEachNear(from, to, (b) => (result = Math.max(result, b.bottom)));
    return result;
  }

  add(b: Box): void {
    this.placed.push(b);
  }
}

/**
 * [boxes]: lo ya dibujado. [names] y [fingers] dicen qué mostrar; las notas ligadas a la anterior no se repiten
 * y las que no traen dedo solo muestran el nombre.
 */
export function build(score: Score, xOfTick: (tick: number) => number, boxes: readonly Box[], names: boolean, fingers: boolean): NoteLabel[] {
  if (!names && !fingers) return [];
  const key = (hand: Hand, pitch: Pitch, tick: number) => `${hand}|${pitchToString(pitch)}|${tick}`;
  const continuations = new Set<string>();
  for (const n of score.notes) if (n.tiedToNext) continuations.add(key(n.hand, n.pitch, n.startTick + n.durationTicks));
  const obstacles = new Obstacles(boxes);
  const out: NoteLabel[] = [];
  for (const chord of chords(score)) {
    const above = chord.hand === "right";
    // Arriba: de la nota más grave a la más aguda (la más grave queda pegada al pentagrama). Abajo, al revés.
    const ordered = above ? chord.notes : [...chord.notes].reverse();
    const cx = xOfTick(chord.startTick) + HEAD_CENTER;
    for (const note of ordered) {
      if (continuations.has(key(note.hand, note.pitch, note.startTick))) continue;
      const label = text(note, names, fingers);
      if (label.length === 0) continue;
      const width = SIZE * CHAR_WIDTH * label.length;
      const left = cx - width / 2;
      const from = left - PAD;
      const to = left + width + PAD;
      const baseline = above
        ? obstacles.highest(from, to, Geometry.TREBLE_TOP) - GAP
        : obstacles.lowest(from, to, Geometry.SYSTEM_BOTTOM) + GAP + SIZE;
      const op = textOp(label, left, baseline, SIZE);
      obstacles.add(box(op));
      out.push({ op, hand: chord.hand });
    }
  }
  return out;
}
