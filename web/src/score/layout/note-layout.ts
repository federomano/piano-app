/*
 * Dibujo de las notas, los silencios y las ligaduras (PIA-89). Mismo código que
 * app/.../score/layout/NoteLayout.kt, pasado a TypeScript. Calcula instrucciones de dibujo en
 * espacios de pentagrama.
 *
 * Las reglas son las de la notación tradicional: las plicas van hacia arriba si la nota está por debajo
 * de la línea central y hacia abajo si está en ella o por encima; en un acorde (o en un grupo unido por
 * una barra) manda la nota más alejada de la línea central. Una alteración se escribe solo si cambia lo
 * que ya valía en ese compás (o lo que da la armadura). Las corcheas y semicorcheas seguidas se unen con
 * barras dentro de cada tiempo del compás.
 */
import {
  type Clef,
  type Hand,
  type Note,
  type Pitch,
  type Rest,
  type Score,
  type Step,
  type Tuplet,
  HANDS,
  Ticks,
  clefAt,
  octaveShiftAt,
  writtenTicks,
} from "../model";
import { type Chord, type Measure, chords } from "../tools";
import { type DrawOp, Smufl, glyph, polygon, rect } from "./drawops";
import { Geometry } from "./geometry";
import type { MeasureLayout } from "./measure-layout";

/**
 * Las instrucciones de dibujo [from] hasta [to] (sin incluir) son las de [chord] (cabezas, plica, bandera,
 * alteraciones, puntillos, articulaciones, líneas adicionales); [heads] dice cuál es la instrucción de la cabeza
 * de cada nota. Las barras, ligaduras y grupos irregulares son de varias notas y quedan afuera.
 */
export interface ChordSpan {
  readonly chord: Chord;
  readonly from: number;
  readonly to: number;
  readonly heads: ReadonlyArray<readonly [Note, number]>;
}

export interface Built {
  readonly ops: DrawOp[];
  readonly spans: ChordSpan[];
}

export const STEM_THICKNESS = 0.12;
export const LEDGER_THICKNESS = 0.16;
export const BEAM_THICKNESS = 0.5;
const BEAM_SPACING = 0.25;
const MAX_BEAM_SLOPE = 0.3;
const STUB_LENGTH = 1.1;
const LEDGER_OVERHANG = 0.4;
const STEM_LENGTH = 3.5;
const STEM_ANCHOR = 0.168;
const REST_VOICE_SHIFT = 1;
const REST_CLEARANCE = 0.2;
const REST_STEP = 0.5;
const REST_MAX_MOVE = 5;
const DOT_GAP = 0.35;
const DOT_SPACING = 0.45;
const ACCIDENTAL_MARGIN = 0.2;
const ACCIDENTAL_COLUMN_GAP = 0.15;

// Notas de adorno: chiquitas, con plica hacia arriba, banderita y una rayita cruzada.
const GRACE_SCALE = 0.62;
const GRACE_GAP = 0.9;
const GRACE_STEM_LENGTH = 2.4;
const GRACE_STEM_THICKNESS = 0.1;
const GRACE_SLASH_THICKNESS = 0.12;

const STACCATO_WIDTH = 0.336;
const ACCENT_WIDTH = 1.356;
const ACCENT_HEIGHT = 0.98;

/** Posición diatónica de la línea inferior de cada pentagrama: Mi4 en clave de sol, Sol2 en clave de fa. */
const TREBLE_BOTTOM = 4 * 7 + 2;
const BASS_BOTTOM = 2 * 7 + 4;

const TUPLET_LINE_THICKNESS = 0.1;
const TUPLET_GAP = 1.1;
const TUPLET_HOOK = 0.7;
const TUPLET_NUMBER_PADDING = 0.3;

/** Figura rítmica: la duración base más los puntillos. */
export interface Figure {
  readonly base: number;
  readonly dots: number;
}

const FIGURE_BASES = [Ticks.WHOLE, Ticks.HALF, Ticks.QUARTER, Ticks.EIGHTH, Ticks.SIXTEENTH, Ticks.THIRTY_SECOND];

/** Si la figura es de un grupo irregular, se dibuja la figura escrita (un tresillo de 160 ticks es una corchea). */
export function figureOf(realTicks: number, tuplet: Tuplet | null = null): Figure {
  const ticks = (tuplet ? writtenTicks(realTicks, tuplet) : null) ?? realTicks;
  for (const base of FIGURE_BASES) {
    if (ticks === base) return { base, dots: 0 };
    if (ticks === Math.floor((base * 3) / 2)) return { base, dots: 1 };
    if (ticks === Math.floor((base * 7) / 4)) return { base, dots: 2 };
  }
  // Duración rara: se dibuja con la figura más grande que entre, sin puntillos.
  return { base: FIGURE_BASES.find((b) => b <= ticks) ?? Ticks.THIRTY_SECOND, dots: 0 };
}

/** Duración del grupo de barras: un tiempo del compás (la negra, o la negra con puntillo en los compases compuestos). */
export function beatTicks(numerator: number, denominator: number): number {
  return denominator >= 8 && numerator % 3 === 0 ? 720 : Ticks.QUARTER;
}

const STEP_INDEX: Readonly<Record<Step, number>> = { C: 0, D: 1, E: 2, F: 3, G: 4, A: 5, B: 6 };

/** Posición diatónica absoluta: Do0 = 0, cada línea o espacio sube 1. */
export function diatonic(pitch: Pitch): number {
  return pitch.octave * 7 + STEP_INDEX[pitch.step];
}

/** Posición en el pentagrama: 0 = línea inferior, 1 = primer espacio, 2 = segunda línea, etc. */
export function staffPosition(pitch: Pitch, clefOrHand: Clef | Hand): number {
  const clef: Clef = clefOrHand === "right" ? "treble" : clefOrHand === "left" ? "bass" : clefOrHand;
  return diatonic(pitch) - (clef === "treble" ? TREBLE_BOTTOM : BASS_BOTTOM);
}

const staffTop = (hand: Hand): number => (hand === "right" ? Geometry.TREBLE_TOP : Geometry.BASS_TOP);

/** Altura (y) de una posición del pentagrama. */
export function yOf(position: number, hand: Hand): number {
  return staffTop(hand) + 4 - position * 0.5;
}

const SHARP_ORDER: readonly Step[] = ["F", "C", "G", "D", "A", "E", "B"];
const FLAT_ORDER: readonly Step[] = ["B", "E", "A", "D", "G", "C", "F"];

/** Alteración que la armadura da a cada nota. */
export function keyAlter(fifths: number, step: Step): number {
  if (fifths > 0 && SHARP_ORDER.slice(0, fifths).includes(step)) return 1;
  if (fifths < 0 && FLAT_ORDER.slice(0, -fifths).includes(step)) return -1;
  return 0;
}

class Head {
  displaced = false;
  x = 0;
  constructor(
    readonly note: Note,
    readonly position: number,
  ) {}
}

/** Todo lo que hace falta saber de un acorde (o nota suelta) para dibujarlo. */
class Plan {
  readonly hand: Hand;
  readonly figure: Figure;
  readonly whole: boolean;
  readonly headWidth: number;
  readonly heads: Head[];
  readonly lowest: number;
  readonly highest: number;
  readonly beamable: boolean;
  readonly voice: number;
  forcedStemDown: boolean | null = null;
  stemDown: boolean;
  group: BeamGroup | null = null;
  /** Corrimiento horizontal cuando las cabezas chocan con las de otra voz de la misma mano. */
  shiftX = 0;
  stemX = 0;
  leftEdge = 0;
  rightEdge = 0;

  constructor(
    readonly chord: Chord,
    readonly measureIndex: number,
    readonly measure: Measure,
    readonly noteX: number,
    readonly clef: Clef,
    readonly octaveShift: number,
  ) {
    this.hand = chord.hand;
    this.figure = figureOf(chord.durationTicks, chord.tuplet);
    this.whole = this.figure.base === Ticks.WHOLE;
    this.headWidth = this.whole ? Smufl.NOTEHEAD_WHOLE_WIDTH : Smufl.NOTEHEAD_WIDTH;
    this.heads = chord.notes
      .map((n) => new Head(n, staffPosition(n.pitch, clef) - 7 * octaveShift))
      .sort((a, b) => a.position - b.position);
    this.lowest = this.heads[0]!.position;
    this.highest = this.heads[this.heads.length - 1]!.position;
    this.beamable = this.figure.base <= Ticks.EIGHTH;
    this.voice = chord.voice;
    this.stemDown = this.highest - 4 >= 4 - this.lowest;
  }

  get beams(): number {
    return this.figure.base === Ticks.THIRTY_SECOND ? 3 : this.figure.base === Ticks.SIXTEENTH ? 2 : 1;
  }
  yOf(position: number): number {
    return yOf(position, this.hand);
  }
  get stemAnchorTop(): number {
    return this.yOf(this.highest) + STEM_ANCHOR;
  }
  get stemAnchorBottom(): number {
    return this.yOf(this.lowest) - STEM_ANCHOR;
  }

  /** Donde terminaría la plica si no hubiera barra. */
  idealTip(beamLevels: number = this.beams): number {
    const extra =
      (beamLevels - 1) * (BEAM_THICKNESS + BEAM_SPACING) + (this.group === null && this.beams > 1 ? 0.5 * (this.beams - 1) : 0);
    const middle = staffTop(this.hand) + 2;
    return this.stemDown
      ? Math.max(this.yOf(this.lowest) + STEM_LENGTH + extra, middle)
      : Math.min(this.yOf(this.highest) - STEM_LENGTH - extra, middle);
  }

  arrange(): void {
    // Cabezas de notas contiguas (segundas) van a lados opuestos de la plica.
    const heads = this.heads;
    if (this.stemDown) {
      for (let i = heads.length - 1; i >= 0; i--) {
        const above = heads[i + 1];
        heads[i]!.displaced = above !== undefined && above.position - heads[i]!.position === 1 && !above.displaced;
      }
    } else {
      for (let i = 0; i < heads.length; i++) {
        const below = heads[i - 1];
        heads[i]!.displaced = below !== undefined && heads[i]!.position - below.position === 1 && !below.displaced;
      }
    }
    const x0 = this.noteX + this.shiftX;
    this.stemX = this.stemDown ? x0 : x0 + this.headWidth - STEM_THICKNESS;
    for (const head of heads) {
      if (!head.displaced) head.x = x0;
      else if (this.stemDown) head.x = x0 - this.headWidth + STEM_THICKNESS;
      else head.x = x0 + this.headWidth - STEM_THICKNESS;
    }
    this.leftEdge = Math.min(...heads.map((h) => h.x));
    this.rightEdge = Math.max(...heads.map((h) => h.x + this.headWidth));
  }
}

/** Corcheas y semicorcheas seguidas dentro de un mismo tiempo, unidas por barras. */
class BeamGroup {
  readonly stemDown: boolean;
  y0 = 0;
  slope = 0;
  constructor(readonly plans: Plan[]) {
    this.stemDown =
      plans[0]!.forcedStemDown ??
      Math.max(...plans.map((p) => p.highest)) - 4 >= 4 - Math.min(...plans.map((p) => p.lowest));
  }
  beamY(x: number): number {
    return this.y0 + this.slope * (x - this.plans[0]!.stemX);
  }
}

const HEAD_CODES = new Set<number>([Smufl.NOTEHEAD_WHOLE, Smufl.NOTEHEAD_HALF, Smufl.NOTEHEAD_BLACK]);

/** Dibuja todas las notas, silencios y ligaduras de la partitura. [xOfTick] da la posición horizontal de cada instante. */
export function build(score: Score, measureLayouts: readonly MeasureLayout[], xOfTick: (tick: number) => number): DrawOp[] {
  return buildWithSpans(score, measureLayouts, xOfTick).ops;
}

function clampNumber(v: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, v));
}

function distinctPairs(pairs: ReadonlyArray<readonly [Hand, number]>): Array<readonly [Hand, number]> {
  const seen = new Set<string>();
  const out: Array<readonly [Hand, number]> = [];
  for (const p of pairs) {
    const key = `${p[0]}|${p[1]}`;
    if (!seen.has(key)) {
      seen.add(key);
      out.push(p);
    }
  }
  return out;
}

const sameTuplet = (a: Tuplet | null, b: Tuplet | null): boolean =>
  a === b || (a !== null && b !== null && a.actual === b.actual && a.normal === b.normal);

/** Lo mismo que [build], y además qué instrucciones de dibujo son de cada acorde. */
export function buildWithSpans(score: Score, measureLayouts: readonly MeasureLayout[], xOfTick: (tick: number) => number): Built {
  const ops: DrawOp[] = [];
  const spans: ChordSpan[] = [];
  const measureIndexAt = (tick: number): number => {
    let found = 0;
    for (let i = measureLayouts.length - 1; i >= 0; i--) {
      if (measureLayouts[i]!.measure.startTick <= tick) {
        found = i;
        break;
      }
    }
    return found;
  };

  const plans = chords(score).map((chord) => {
    const index = measureIndexAt(chord.startTick);
    return new Plan(
      chord,
      index,
      measureLayouts[index]!.measure,
      xOfTick(chord.startTick),
      clefAt(score, chord.hand, chord.startTick),
      octaveShiftAt(score, chord.hand, chord.startTick),
    );
  });

  // Plicas: con varias voces en una mano, la voz 1 va arriba y las otras abajo.
  for (const hand of HANDS) {
    if (!score.notes.some((n) => n.hand === hand && n.voice > 1 && !n.grace)) continue;
    for (const p of plans) {
      if (p.hand !== hand) continue;
      p.forcedStemDown = p.voice !== 1;
      p.stemDown = p.voice !== 1;
    }
  }

  // Voces que suenan a la vez con cabezas en el mismo lugar o contiguas: la de número mayor se corre a la derecha.
  for (const a of plans) {
    for (const b of plans) {
      if (a.hand !== b.hand || b.voice <= a.voice) continue;
      if (a.chord.startTick >= b.chord.endTick || b.chord.startTick >= a.chord.endTick) continue;
      const collide = a.heads.some((ha) => b.heads.some((hb) => Math.abs(ha.position - hb.position) <= 1));
      if (collide) b.shiftX = Math.max(b.shiftX, a.headWidth);
    }
  }

  // 1) Grupos de barras, por mano y voz.
  for (const [hand, voice] of distinctPairs(plans.map((p) => [p.hand, p.voice] as const))) {
    const handPlans = plans.filter((p) => p.hand === hand && p.voice === voice).sort((a, b) => a.chord.startTick - b.chord.startTick);
    let current: Plan[] = [];
    const close = () => {
      if (current.length >= 2) {
        const group = new BeamGroup([...current]);
        for (const p of current) {
          p.group = group;
          p.stemDown = group.stemDown;
        }
      }
      current = [];
    };
    for (const plan of handPlans) {
      if (!plan.beamable) {
        close();
        continue;
      }
      const last = current[current.length - 1];
      const m = plan.measure;
      const beat = beatTicks(m.timeSignature.numerator, m.timeSignature.denominator);
      const slot = (p: Plan) => Math.floor((p.chord.startTick - p.measure.startTick + p.measure.gridOffset) / beat);
      const continues =
        last !== undefined &&
        last.measureIndex === plan.measureIndex &&
        last.chord.endTick === plan.chord.startTick &&
        slot(last) === slot(plan);
      if (!continues) close();
      current.push(plan);
    }
    close();
  }

  // 2) Geometría de cada acorde y de cada barra.
  for (const p of plans) p.arrange();
  const groups = distinctGroups(plans);
  for (const group of groups) layoutBeam(group);

  // 3) Dibujo de acordes, en orden, para llevar la cuenta de las alteraciones de cada compás.
  let stateMeasure = -1;
  const state = new Map<Hand, Map<string, number>>();
  // Cada adorno se dibuja a la izquierda de la nota principal de su mano en ese tick (la de la voz más baja).
  const graceOwner = new Map<string, Plan>();
  for (const p of plans) {
    const key = `${p.hand}|${p.chord.startTick}`;
    const owner = graceOwner.get(key);
    if (owner === undefined || p.voice < owner.voice) graceOwner.set(key, p);
  }
  for (const plan of plans) {
    if (plan.measureIndex !== stateMeasure) {
      stateMeasure = plan.measureIndex;
      state.clear();
    }
    let handState = state.get(plan.hand);
    if (!handState) {
      handState = new Map();
      state.set(plan.hand, handState);
    }
    const tiedFromBefore = new Set<Note>(
      plan.chord.notes.filter(
        (note) =>
          note.startTick === plan.measure.startTick &&
          score.notes.some(
            (n) => n.hand === note.hand && n.pitch.midi === note.pitch.midi && n.startTick + n.durationTicks === note.startTick && n.tiedToNext,
          ),
      ),
    );
    const ownGraces =
      graceOwner.get(`${plan.hand}|${plan.chord.startTick}`) === plan
        ? score.notes
            .filter((n) => n.grace && n.hand === plan.hand && n.startTick === plan.chord.startTick)
            .sort((a, b) => a.pitch.midi - b.pitch.midi)
        : [];
    // La alteración de un adorno vale para el resto del compás, igual que la de cualquier nota.
    const graceAccidentals = ownGraces.map((grace) => {
      const key = `${grace.pitch.step}${grace.pitch.octave}`;
      const current = handState.get(key) ?? keyAlter(plan.measure.keyFifths, grace.pitch.step);
      handState.set(key, grace.pitch.alter);
      return [grace, grace.pitch.alter !== current] as const;
    });
    const spanFrom = ops.length;
    const leftMost = drawChord(ops, plan, plan.measure.keyFifths, handState, tiedFromBefore);
    const headOps: number[] = [];
    for (let i = spanFrom; i < ops.length; i++) {
      const op = ops[i]!;
      if (op.kind === "glyph" && HEAD_CODES.has(op.codepoint) && op.scale === 1) headOps.push(i);
    }
    spans.push({
      chord: plan.chord,
      from: spanFrom,
      to: ops.length,
      heads: headOps.length === plan.heads.length ? plan.heads.map((h, i) => [h.note, headOps[i]!] as const) : [],
    });
    if (graceAccidentals.length > 0) drawGrace(ops, plan.hand, plan.clef, plan.octaveShift, graceAccidentals, leftMost);
  }
  for (const group of groups) drawBeam(ops, group);

  // 4) Ligaduras de prolongación.
  drawTies(ops, plans);

  // 5) Silencios. Se anota hasta dónde llega cada uno (arriba y abajo) para que los corchetes de grupo lo esquiven.
  const restSpans = new Map<Rest, readonly [number, number]>();
  for (const rest of score.rests) {
    const index = measureIndexAt(rest.startTick);
    // Con varias voces en la mano durante ese compás, el silencio de la voz 1 sube y el de las demás baja.
    const m = measureLayouts[index]!.measure;
    const manyVoices =
      score.notes.some(
        (n) => n.hand === rest.hand && n.voice > 1 && !n.grace && n.startTick < m.endTick && n.startTick + n.durationTicks > m.startTick,
      ) ||
      score.rests.some((r) => r.hand === rest.hand && r.voice > 1 && r.startTick < m.endTick && r.startTick + r.durationTicks > m.startTick);
    const yShift = !manyVoices ? 0 : rest.voice === 1 ? -REST_VOICE_SHIFT : REST_VOICE_SHIFT;
    // Cabezas de las otras voces de la mano: el silencio se aparta de ellas.
    const others = !manyVoices ? [] : plans.filter((p) => p.hand === rest.hand && p.voice !== rest.voice);
    restSpans.set(
      rest,
      drawRest(ops, rest.hand, rest.startTick, rest.durationTicks, rest.tuplet, measureLayouts[index]!, xOfTick(rest.startTick), yShift, manyVoices, rest.voice, others),
    );
  }

  // 6) Corchetes y números de los grupos irregulares.
  drawTuplets(ops, score, plans, measureIndexAt, xOfTick, restSpans);
  return { ops, spans };
}

function distinctGroups(plans: readonly Plan[]): BeamGroup[] {
  const seen = new Set<BeamGroup>();
  const out: BeamGroup[] = [];
  for (const p of plans) {
    if (p.group && !seen.has(p.group)) {
      seen.add(p.group);
      out.push(p.group);
    }
  }
  return out;
}

// --- Grupos irregulares (tresillos, etc.) ---

/** Una figura (acorde o silencio) de un grupo irregular, con el ancho que ocupa en el dibujo. */
interface TupletItem {
  readonly startTick: number;
  readonly durationTicks: number;
  readonly tuplet: Tuplet;
  readonly plan: Plan | null;
  readonly left: number;
  readonly right: number;
  /** Solo en los silencios: hasta dónde llega el dibujo hacia arriba y hacia abajo (y crece hacia abajo). */
  readonly restTop: number;
  readonly restBottom: number;
}

/**
 * Cada grupo irregular completo lleva su número. Si todas sus figuras están unidas por una misma barra, el número va
 * solo junto a la barra; si no, lleva además un corchete con el número en el medio.
 */
function drawTuplets(
  ops: DrawOp[],
  score: Score,
  plans: readonly Plan[],
  measureIndexAt: (tick: number) => number,
  xOfTick: (tick: number) => number,
  restSpans: ReadonlyMap<Rest, readonly [number, number]>,
): void {
  const lines = distinctPairs([
    ...score.notes.filter((n) => n.tuplet !== null).map((n) => [n.hand, n.voice] as const),
    ...score.rests.filter((r) => r.tuplet !== null).map((r) => [r.hand, r.voice] as const),
  ]);
  for (const [hand, voice] of lines) {
    const items: TupletItem[] = [
      ...plans
        .filter((p) => p.hand === hand && p.voice === voice && p.chord.tuplet !== null)
        .map(
          (p): TupletItem => ({
            startTick: p.chord.startTick,
            durationTicks: p.chord.durationTicks,
            tuplet: p.chord.tuplet!,
            plan: p,
            left: p.leftEdge,
            right: p.rightEdge,
            restTop: Infinity,
            restBottom: -Infinity,
          }),
        ),
      ...score.rests
        .filter((r) => r.hand === hand && r.voice === voice && r.tuplet !== null)
        .map((rest): TupletItem => {
          const x = xOfTick(rest.startTick);
          const base = figureOf(rest.durationTicks, rest.tuplet).base;
          const span = restSpans.get(rest);
          return {
            startTick: rest.startTick,
            durationTicks: rest.durationTicks,
            tuplet: rest.tuplet!,
            plan: null,
            left: x,
            right: x + restWidthOf(base),
            restTop: span ? span[0] : Infinity,
            restBottom: span ? span[1] : -Infinity,
          };
        }),
    ].sort((a, b) => a.startTick - b.startTick);

    // Figuras seguidas del mismo grupo, dentro de un compás.
    const runs: TupletItem[][] = [];
    for (const item of items) {
      const run = runs[runs.length - 1];
      const last = run?.[run.length - 1];
      if (
        last !== undefined &&
        sameTuplet(last.tuplet, item.tuplet) &&
        last.startTick + last.durationTicks === item.startTick &&
        measureIndexAt(last.startTick) === measureIndexAt(item.startTick)
      ) {
        run!.push(item);
      } else {
        runs.push([item]);
      }
    }
    for (const run of runs) {
      const tuplet = run[0]!.tuplet;
      const span = tuplet.normal * Math.min(...run.map((it) => writtenTicks(it.durationTicks, tuplet) ?? it.durationTicks));
      const origin = run[0]!.startTick;
      const byIndex = new Map<number, TupletItem[]>();
      for (const it of run) {
        const k = Math.floor((it.startTick - origin) / span);
        const list = byIndex.get(k);
        if (list) list.push(it);
        else byIndex.set(k, [it]);
      }
      for (const group of byIndex.values()) {
        if (group.reduce((s, it) => s + it.durationTicks, 0) !== span) continue; // grupo incompleto: no se dibuja
        drawTuplet(ops, hand, voice, tuplet, group, plans);
      }
    }
  }
}

function restWidthOf(base: number): number {
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

function tupletLabel(tuplet: Tuplet): number[] {
  if (tuplet.actual === 3 && tuplet.normal === 2) return [Smufl.tupletDigit(3)];
  return [Smufl.tupletDigit(tuplet.actual), Smufl.TUPLET_COLON, Smufl.tupletDigit(tuplet.normal)];
}

const labelAdvance = (code: number): number =>
  code === Smufl.TUPLET_COLON ? Smufl.TUPLET_COLON_ADVANCE : Smufl.tupletDigitAdvance(code - 0xe880);

function drawTuplet(
  ops: DrawOp[],
  hand: Hand,
  voice: number,
  tuplet: Tuplet,
  group: readonly TupletItem[],
  allPlans: readonly Plan[],
): void {
  const plans = group.map((it) => it.plan).filter((p): p is Plan => p !== null);
  // Del lado de las plicas; un grupo de solo silencios va arriba en la voz 1 y abajo en las otras.
  const above = plans.length === 0 ? voice === 1 : plans.filter((p) => !p.stemDown).length >= plans.filter((p) => p.stemDown).length;
  const beamGroup = plans[0]?.group ?? null;
  const fullyBeamed =
    beamGroup !== null && plans.length === group.length && plans.every((p) => p.group === beamGroup) && beamGroup.plans.length === plans.length;
  const top = staffTop(hand);

  // Lo más alejado que llega el grupo hacia ese lado (y crece hacia abajo).
  const planExtreme = (plan: Plan): number => {
    const tip = plan.whole ? null : plan.group ? plan.group.beamY(plan.stemX) : plan.idealTip();
    return above
      ? Math.min(plan.yOf(plan.highest) - 0.5, tip ?? Infinity)
      : Math.max(plan.yOf(plan.lowest) + 0.5, (tip ?? -Infinity) + (plan.group ? BEAM_THICKNESS : 0));
  };
  const left = group[0]!.left;
  const right = group[group.length - 1]!.right;
  // Las cabezas de otros grupos o voces de la misma mano que caen debajo del corchete también cuentan.
  const beside = allPlans.filter((p) => p.hand === hand && !plans.includes(p) && p.leftEdge < right + 0.2 && p.rightEdge > left - 0.2);
  // Los silencios que se corrieron (para no pisar las notas de otra voz) también cuentan.
  const extreme = above
    ? Math.min(
        top - 0.5,
        ...plans.map(planExtreme),
        ...group.map((it) => it.restTop),
        ...beside.map((p) => p.yOf(p.highest) - 0.5),
      )
    : Math.max(
        top + 4.5,
        ...plans.map(planExtreme),
        ...group.map((it) => it.restBottom),
        ...beside.map((p) => p.yOf(p.lowest) + 0.5),
      );

  const label = tupletLabel(tuplet);
  const width = label.reduce((s, c) => s + labelAdvance(c), 0);
  const mid = fullyBeamed ? (beamGroup!.plans[0]!.stemX + beamGroup!.plans[beamGroup!.plans.length - 1]!.stemX) / 2 : (left + right) / 2;

  const drawLabel = (cx: number, baseline: number) => {
    let x = cx - width / 2;
    for (const code of label) {
      ops.push(glyph(code, x, baseline));
      x += labelAdvance(code);
    }
  };

  if (fullyBeamed) {
    // Solo el número, pegado a la barra.
    const baseline = above ? extreme - 0.4 : extreme + 0.4 + Smufl.TUPLET_DIGIT_HEIGHT;
    drawLabel(mid, baseline);
    return;
  }
  // Corchete: línea con un hueco para el número y ganchos hacia las figuras.
  const y = above ? extreme - TUPLET_GAP : extreme + TUPLET_GAP;
  const gapLeft = mid - width / 2 - TUPLET_NUMBER_PADDING;
  const gapRight = mid + width / 2 + TUPLET_NUMBER_PADDING;
  ops.push(rect(left, y - TUPLET_LINE_THICKNESS / 2, gapLeft - left, TUPLET_LINE_THICKNESS));
  ops.push(rect(gapRight, y - TUPLET_LINE_THICKNESS / 2, right - gapRight, TUPLET_LINE_THICKNESS));
  const hookTop = above ? y - TUPLET_LINE_THICKNESS / 2 : y + TUPLET_LINE_THICKNESS / 2 - TUPLET_HOOK;
  ops.push(rect(left, hookTop, TUPLET_LINE_THICKNESS, TUPLET_HOOK));
  ops.push(rect(right - TUPLET_LINE_THICKNESS, hookTop, TUPLET_LINE_THICKNESS, TUPLET_HOOK));
  drawLabel(mid, y + Smufl.TUPLET_DIGIT_HEIGHT / 2);
}

// --- Barras ---

function layoutBeam(group: BeamGroup): void {
  const first = group.plans[0]!;
  const last = group.plans[group.plans.length - 1]!;
  // Todas las plicas del grupo usan el mismo margen para las barras (las del grupo con más barras).
  const levels = Math.max(...group.plans.map((p) => p.beams));
  const tips = group.plans.map((p) => p.idealTip(levels));
  const dx = last.stemX - first.stemX;
  group.slope = dx > 0 ? clampNumber((tips[tips.length - 1]! - tips[0]!) / dx, -MAX_BEAM_SLOPE, MAX_BEAM_SLOPE) : 0;
  group.y0 = tips[0]!;
  // Que ninguna plica quede más corta que lo normal: se desplaza la barra hacia afuera.
  const violations = group.plans.map((plan, i) => tips[i]! - group.beamY(plan.stemX));
  group.y0 += group.stemDown ? Math.max(0, Math.max(...violations)) : Math.min(0, Math.min(...violations));
}

function drawBeam(ops: DrawOp[], group: BeamGroup): void {
  const plans = group.plans;
  // Plicas hasta la barra.
  for (const plan of plans) {
    const beamY = group.beamY(plan.stemX);
    if (group.stemDown) ops.push(rect(plan.stemX, plan.stemAnchorTop, STEM_THICKNESS, beamY - plan.stemAnchorTop));
    else ops.push(rect(plan.stemX, beamY, STEM_THICKNESS, plan.stemAnchorBottom - beamY));
  }
  const segment = (x1: number, x2: number, level: number) => {
    // La barra exterior queda en la punta de la plica; las siguientes se apilan hacia las cabezas.
    const shift = group.stemDown ? -level * (BEAM_THICKNESS + BEAM_SPACING) : level * (BEAM_THICKNESS + BEAM_SPACING);
    const outer1 = group.beamY(x1) + shift;
    const outer2 = group.beamY(x2) + shift;
    const top1 = group.stemDown ? outer1 - BEAM_THICKNESS : outer1;
    const top2 = group.stemDown ? outer2 - BEAM_THICKNESS : outer2;
    ops.push(polygon([x1, top1, x2, top2, x2, top2 + BEAM_THICKNESS, x1, top1 + BEAM_THICKNESS]));
  };
  // Primera barra: de la primera a la última plica.
  segment(plans[0]!.stemX, plans[plans.length - 1]!.stemX + STEM_THICKNESS, 0);
  // Barras siguientes: una sola entre figuras vecinas que las llevan; una figura sola lleva un tramo corto.
  const maxBeams = Math.max(...plans.map((p) => p.beams));
  for (let level = 1; level < maxBeams; level++) {
    let i = 0;
    while (i < plans.length) {
      if (plans[i]!.beams <= level) {
        i++;
        continue;
      }
      let j = i;
      while (j + 1 < plans.length && plans[j + 1]!.beams > level) j++;
      if (j > i) segment(plans[i]!.stemX, plans[j]!.stemX + STEM_THICKNESS, level);
      else if (i === 0) segment(plans[i]!.stemX, plans[i]!.stemX + STUB_LENGTH, level);
      else segment(plans[i]!.stemX + STEM_THICKNESS - STUB_LENGTH, plans[i]!.stemX + STEM_THICKNESS, level);
      i = j + 1;
    }
  }
}

// --- Acordes ---

function ledgerPositions(positions: Iterable<number>): number[] {
  const set = new Set<number>();
  for (const p of positions) {
    if (p <= -2) for (let q = p; q <= -2; q++) if (q % 2 === 0) set.add(q);
    if (p >= 10) for (let q = 10; q <= p; q++) if (q % 2 === 0) set.add(q);
  }
  return [...set].sort((a, b) => a - b);
}

function drawChord(ops: DrawOp[], plan: Plan, fifths: number, handState: Map<string, number>, tiedFromBefore: ReadonlySet<Note>): number {
  const figure = plan.figure;
  const headCode = figure.base === Ticks.WHOLE ? Smufl.NOTEHEAD_WHOLE : figure.base === Ticks.HALF ? Smufl.NOTEHEAD_HALF : Smufl.NOTEHEAD_BLACK;
  const heads = plan.heads;
  const leftEdge = plan.leftEdge;
  const rightEdge = plan.rightEdge;
  // Lo más a la izquierda que llega lo dibujado: ahí termina el lugar del adorno.
  let leftMost = leftEdge;

  // Líneas adicionales (una sola vez por línea, a lo ancho de todo el acorde).
  const ledgers = ledgerPositions(heads.map((h) => h.position));
  if (ledgers.length > 0) leftMost = Math.min(leftMost, leftEdge - LEDGER_OVERHANG);
  for (const q of ledgers) {
    ops.push(rect(leftEdge - LEDGER_OVERHANG, plan.yOf(q) - LEDGER_THICKNESS / 2, rightEdge - leftEdge + 2 * LEDGER_OVERHANG, LEDGER_THICKNESS));
  }

  // Plica y bandera (si el acorde va en un grupo de barras, la plica la dibuja el grupo).
  if (!plan.whole && plan.group === null) {
    const tip = plan.idealTip();
    if (plan.stemDown) {
      const top = plan.stemAnchorTop;
      ops.push(rect(plan.stemX, top, STEM_THICKNESS, tip - top));
      const flag = flagFor(figure.base, true);
      if (flag !== null) ops.push(glyph(flag, plan.stemX, tip));
    } else {
      const bottom = plan.stemAnchorBottom;
      ops.push(rect(plan.stemX, tip, STEM_THICKNESS, bottom - tip));
      const flag = flagFor(figure.base, false);
      if (flag !== null) ops.push(glyph(flag, plan.stemX, tip));
    }
  }

  // Cabezas.
  for (const head of heads) ops.push(glyph(headCode, head.x, plan.yOf(head.position)));

  // Alteraciones, en columnas hacia la izquierda si las notas están cerca.
  const accidentals: Array<readonly [Head, number]> = [];
  for (const head of [...heads].sort((a, b) => b.position - a.position)) {
    const pitch = head.note.pitch;
    const key = `${pitch.step}${pitch.octave}`;
    const current = handState.get(key) ?? keyAlter(fifths, pitch.step);
    if (pitch.alter !== current && !tiedFromBefore.has(head.note)) accidentals.push([head, pitch.alter]);
    handState.set(key, pitch.alter);
  }
  const columnLastPosition: number[] = [];
  const placed = accidentals.map(([head, alter]) => {
    let column = columnLastPosition.findIndex((last) => last - head.position >= 6);
    if (column < 0) {
      column = columnLastPosition.length;
      columnLastPosition.push(head.position);
    } else {
      columnLastPosition[column] = head.position;
    }
    return [head, alter, column] as const;
  });
  const columnWidth = new Array<number>(columnLastPosition.length).fill(0);
  for (const [, alter, column] of placed) columnWidth[column] = Math.max(columnWidth[column]!, accidentalAdvance(alter));
  for (const [head, alter, column] of placed) {
    let offset = 0;
    for (let k = 0; k <= column; k++) offset += columnWidth[k]!;
    offset += column * ACCIDENTAL_COLUMN_GAP;
    const accidentalX = leftEdge - ACCIDENTAL_MARGIN - offset;
    leftMost = Math.min(leftMost, accidentalX);
    ops.push(glyph(accidentalCode(alter), accidentalX, plan.yOf(head.position)));
  }

  // Puntillos: a la derecha de todas las cabezas, en el espacio (si la nota está en una línea, el de arriba).
  if (figure.dots > 0) {
    const dotYs = [...new Set(heads.map((h) => (h.position % 2 === 0 ? plan.yOf(h.position) - 0.5 : plan.yOf(h.position))))];
    for (const y of dotYs) {
      for (let i = 0; i < figure.dots; i++) ops.push(glyph(Smufl.AUGMENTATION_DOT, rightEdge + DOT_GAP + i * DOT_SPACING, y));
    }
  }
  drawArticulations(ops, plan);
  return leftMost;
}

/**
 * Staccato (puntito) y acento («>»): del lado de las cabezas, o sea el opuesto a la plica, alejados de la nota
 * para que no se toquen. Si hay los dos, el puntito queda más cerca y el acento más afuera.
 */
function drawArticulations(ops: DrawOp[], plan: Plan): void {
  const staccato = plan.heads.some((h) => h.note.staccato);
  const accent = plan.heads.some((h) => h.note.accent);
  if (!staccato && !accent) return;
  const above = plan.stemDown;
  const edge = above ? plan.heads[plan.heads.length - 1]! : plan.heads[0]!;
  const sign = above ? 1 : -1;
  // Una nota en una línea necesita un espacio más de distancia que una nota en un espacio.
  let position = edge.position + sign * (edge.position % 2 === 0 ? 3 : 2);
  const centerX = edge.x + plan.headWidth / 2;
  if (staccato) {
    ops.push(glyph(Smufl.STACCATO_ABOVE, centerX - STACCATO_WIDTH / 2, plan.yOf(position) + STACCATO_WIDTH / 2));
    position += sign * 2;
  }
  if (accent) {
    if (staccato) position += sign;
    const y = plan.yOf(position);
    if (above) ops.push(glyph(Smufl.ACCENT_ABOVE, centerX - ACCENT_WIDTH / 2, y + ACCENT_HEIGHT / 2));
    else ops.push(glyph(Smufl.ACCENT_BELOW, centerX - ACCENT_WIDTH / 2, y - ACCENT_HEIGHT / 2));
  }
}

/**
 * Adorno (acciaccatura): una o más notas chiquitas, pegadas a la izquierda de [leftMost], con plica hacia arriba,
 * banderita y una rayita en diagonal. [graces] trae cada nota y si lleva alteración.
 */
function drawGrace(
  ops: DrawOp[],
  hand: Hand,
  clef: Clef,
  octaveShift: number,
  graces: ReadonlyArray<readonly [Note, boolean]>,
  leftMost: number,
): void {
  const headWidth = Smufl.NOTEHEAD_WIDTH * GRACE_SCALE;
  const headX = leftMost - GRACE_GAP - headWidth;
  const positions = graces.map(([n]) => staffPosition(n.pitch, clef) - 7 * octaveShift);
  const yAt = (position: number) => yOf(position, hand);

  for (const [note, needsAccidental] of graces) {
    if (!needsAccidental) continue;
    const alter = note.pitch.alter;
    const x = headX - accidentalAdvance(alter) * GRACE_SCALE - ACCIDENTAL_MARGIN * GRACE_SCALE;
    ops.push(glyph(accidentalCode(alter), x, yAt(staffPosition(note.pitch, clef) - 7 * octaveShift), GRACE_SCALE));
  }
  for (const q of ledgerPositions(positions)) {
    ops.push(rect(headX - 0.25, yAt(q) - LEDGER_THICKNESS / 2, headWidth + 0.5, LEDGER_THICKNESS));
  }
  for (const p of positions) ops.push(glyph(Smufl.NOTEHEAD_BLACK, headX, yAt(p), GRACE_SCALE));

  const stemX = headX + headWidth - GRACE_STEM_THICKNESS;
  const bottom = yAt(Math.min(...positions)) - STEM_ANCHOR * GRACE_SCALE;
  const tip = yAt(Math.max(...positions)) - GRACE_STEM_LENGTH;
  ops.push(rect(stemX, tip, GRACE_STEM_THICKNESS, bottom - tip));
  ops.push(glyph(Smufl.FLAG_8TH_UP, stemX, tip, GRACE_SCALE));
  // Rayita en diagonal que cruza la plica.
  const x1 = stemX - 0.5;
  const y1 = tip + 1.3;
  const x2 = stemX + 0.9;
  const y2 = tip + 0.5;
  ops.push(polygon([x1, y1, x2, y2, x2, y2 + GRACE_SLASH_THICKNESS, x1, y1 + GRACE_SLASH_THICKNESS]));
}

// --- Ligaduras de prolongación ---

function drawTies(ops: DrawOp[], plans: readonly Plan[]): void {
  const byNote: Array<readonly [Note, Plan, Head]> = [];
  for (const plan of plans) for (const head of plan.heads) byNote.push([head.note, plan, head]);
  for (const plan of plans) {
    for (let index = 0; index < plan.heads.length; index++) {
      const head = plan.heads[index]!;
      if (!head.note.tiedToNext) continue;
      const end = head.note.startTick + head.note.durationTicks;
      const target = byNote.find(([n]) => n.hand === head.note.hand && n.pitch.midi === head.note.pitch.midi && n.startTick === end);
      if (!target) continue;
      const [, targetPlan, targetHead] = target;
      // Opuesta a la plica; en un acorde, las notas de arriba van hacia arriba y las de abajo hacia abajo.
      const above = plan.heads.length === 1 ? plan.stemDown : index >= Math.floor(plan.heads.length / 2);
      const y = plan.yOf(head.position);
      const x1 = head.x + plan.headWidth * 0.85;
      const x2 = targetHead.x + targetPlan.headWidth * 0.15;
      if (x2 > x1) ops.push(tie(x1, x2, y, above));
    }
  }
}

/** Ligadura: una media luna entre dos puntos, más gruesa en el medio. */
export function tie(x1: number, x2: number, headY: number, above: boolean): DrawOp & { kind: "polygon" } {
  const dir = above ? -1 : 1;
  const width = x2 - x1;
  const height = clampNumber(0.3 + width * 0.05, 0.5, 1.1);
  const thickness = 0.22;
  const y0 = headY + dir * 0.55;
  const steps = 16;
  const points: number[] = [];
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    points.push(x1 + width * t, y0 + dir * height * 4 * t * (1 - t));
  }
  for (let i = steps; i >= 0; i--) {
    const t = i / steps;
    points.push(x1 + width * t, y0 + dir * (height - thickness) * 4 * t * (1 - t));
  }
  return polygon(points) as DrawOp & { kind: "polygon" };
}

// --- Silencios ---

function drawRest(
  ops: DrawOp[],
  hand: Hand,
  start: number,
  duration: number,
  tuplet: Tuplet | null,
  measureLayout: MeasureLayout,
  x: number,
  startShift = 0,
  manyVoices = false,
  voice = 1,
  others: readonly Plan[] = [],
): readonly [number, number] {
  const top = staffTop(hand);
  const m = measureLayout.measure;
  const wholeMeasure = start === m.startTick && start + duration === m.endTick && m.number !== 0;
  const figure: Figure = wholeMeasure ? { base: Ticks.WHOLE, dots: 0 } : figureOf(duration, tuplet);
  let code: number;
  let width: number;
  let above: number;
  let below: number;
  switch (figure.base) {
    case Ticks.WHOLE:
      [code, width, above, below] = [Smufl.REST_WHOLE, Smufl.REST_WHOLE_WIDTH, 0, 0.5];
      break;
    case Ticks.HALF:
      [code, width, above, below] = [Smufl.REST_HALF, Smufl.REST_HALF_WIDTH, 0.5, 0];
      break;
    case Ticks.QUARTER:
      [code, width, above, below] = [Smufl.REST_QUARTER, Smufl.REST_QUARTER_WIDTH, 1.7, 2.0];
      break;
    case Ticks.EIGHTH:
      [code, width, above, below] = [Smufl.REST_8TH, Smufl.REST_8TH_WIDTH, 1.0, 1.0];
      break;
    case Ticks.SIXTEENTH:
      [code, width, above, below] = [Smufl.REST_16TH, Smufl.REST_16TH_WIDTH, 1.5, 1.5];
      break;
    default:
      [code, width, above, below] = [Smufl.REST_32ND, Smufl.REST_32ND_WIDTH, 1.7, 2.0];
  }
  // El silencio de compás completo va centrado en el compás, sea cual sea la firma.
  const left = wholeMeasure && !manyVoices ? (measureLayout.contentX + measureLayout.x1) / 2 - width / 2 : x;
  // Redonda: cuelga de la 4.ª línea. Blanca: se apoya en la línea central. Los demás, centrados en ella.
  const home = figure.base === Ticks.WHOLE ? top + 1 : top + 2;
  // La voz 1 sube y las demás bajan hasta quedar libres de las cabezas de las otras voces.
  const direction = voice === 1 ? -1 : 1;
  const collides = (shift: number): boolean =>
    others.some((other) =>
      other.heads.some((head) => {
        const y = other.yOf(head.position);
        return (
          head.x < left + width + REST_CLEARANCE &&
          head.x + other.headWidth > left - REST_CLEARANCE &&
          y - 0.5 < home + shift + below + REST_CLEARANCE &&
          y + 0.5 > home + shift - above - REST_CLEARANCE
        );
      }),
    );
  let yShift = startShift;
  if (collides(yShift)) {
    let candidate = yShift;
    while (Math.abs(candidate - startShift) < REST_MAX_MOVE && collides(candidate)) candidate += direction * REST_STEP;
    if (!collides(candidate)) yShift = candidate;
  }
  const baseline = home + yShift;
  // Un silencio de una voz de abajo que tendría que salir del pentagrama (y chocaría con la otra mano) se omite.
  if (voice !== 1 && yShift !== startShift && baseline + below > top + 4.5) return [Infinity, -Infinity];
  ops.push(glyph(code, left, baseline));
  for (let i = 0; i < figure.dots; i++) {
    ops.push(glyph(Smufl.AUGMENTATION_DOT, left + width + DOT_GAP + i * DOT_SPACING, top + 1.5 + yShift));
  }
  return [baseline - above, baseline + below];
}

function flagFor(base: number, down: boolean): number | null {
  switch (base) {
    case Ticks.EIGHTH:
      return down ? Smufl.FLAG_8TH_DOWN : Smufl.FLAG_8TH_UP;
    case Ticks.SIXTEENTH:
      return down ? Smufl.FLAG_16TH_DOWN : Smufl.FLAG_16TH_UP;
    case Ticks.THIRTY_SECOND:
      return down ? Smufl.FLAG_32ND_DOWN : Smufl.FLAG_32ND_UP;
    default:
      return null;
  }
}

function accidentalCode(alter: number): number {
  switch (alter) {
    case 2:
      return Smufl.DOUBLE_SHARP;
    case 1:
      return Smufl.SHARP;
    case 0:
      return Smufl.NATURAL;
    case -1:
      return Smufl.FLAT;
    default:
      return Smufl.DOUBLE_FLAT;
  }
}

function accidentalAdvance(alter: number): number {
  switch (alter) {
    case 2:
      return Smufl.DOUBLE_SHARP_ADVANCE;
    case 1:
      return Smufl.SHARP_ADVANCE;
    case 0:
      return Smufl.NATURAL_ADVANCE;
    case -1:
      return Smufl.FLAT_ADVANCE;
    default:
      return Smufl.DOUBLE_FLAT_ADVANCE;
  }
}
