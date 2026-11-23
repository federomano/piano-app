/*
 * Cálculo del dibujo de la partitura (PIA-89): dos pentagramas unidos con llave, en una sola línea continua.
 * Mismo código que app/.../score/layout/ScoreLayout.kt, pasado a TypeScript. No dibuja: calcula dónde va cada
 * cosa, en espacios de pentagrama (sp).
 *
 * - staff: líneas de los pentagramas, a todo lo largo.
 * - marks: barras de compás, notas, números y demás. Pasan por encima del área fija y se desvanecen.
 * - fixed: lo que nunca cambia a la izquierda (líneas, llave).
 * - changes: cambios de armadura y de compás; headerDraws dice cómo dibujarlos en cada momento.
 */
import { type Clef, type Hand, type Score, HANDS, TICKS_PER_QUARTER, clefAt } from "../model";
import { measures as scoreMeasures } from "../tools";
import { type DrawOp, Smufl, glyph, rect, text as textOp } from "./drawops";
import { type Box, boxes as boxesOf, highest as highestIn, lowest as lowestIn } from "./extents";
import { Geometry } from "./geometry";
import { MeasureLayout } from "./measure-layout";
import * as DynamicsLayout from "./dynamics-layout";
import * as NoteLabelLayout from "./note-label-layout";
import * as NoteLayout from "./note-layout";
import * as OttavaLayout from "./ottava-layout";
import * as PedalLayout from "./pedal-layout";
import { SmoothPath } from "./smooth-path";
import * as Spacing from "./spacing";
import { TempoMap } from "./tempo-map";
import type { ChordSpan } from "./note-layout";
import type { NoteLabel } from "./note-label-layout";

/** Lo que queda fijo a la izquierda en un instante: líneas, llave, claves, armadura y compás vigentes. */
export interface Header {
  readonly ops: readonly DrawOp[];
  readonly width: number;
}

/**
 * Un cambio de armadura o de firma de compás. Existe una sola vez: viaja con la partitura (dentro del compás donde
 * ocurre) y al llegar al área fija se queda ahí, reemplazando a la anterior.
 */
export interface SignatureChange {
  readonly measureIndex: number;
  /** La armadura y la firma de compás nuevas, pegadas (así quedan en el área fija). */
  readonly ops: readonly DrawOp[];
  /** Becuadros que cancelan la armadura anterior. Solo se ven mientras viaja. */
  readonly cancel: readonly DrawOp[];
  /** Donde empieza [ops] en el área fija. */
  readonly anchor: number;
  /** Posición en la partitura menos posición en el área fija. */
  readonly shift: number;
  /** Desplazamiento (scroll) en el que llega a su lugar fijo. */
  readonly pinScroll: number;
}

/** Cambio de la clave grande que queda fija a la izquierda: la de [hand] pasa a [clef]. */
export interface ClefHeaderChange {
  readonly hand: Hand;
  readonly clef: Clef;
  readonly startScroll: number;
  readonly endScroll: number;
}

/** Un grupo de dibujos del área fija, corrido [dx] y con opacidad [alpha] (de 0 a 1). */
export interface HeaderDraw {
  readonly ops: readonly DrawOp[];
  readonly dx: number;
  readonly alpha: number;
}

/** Tamaño de un espacio de pentagrama y dónde cae la línea superior del pentagrama de la derecha, en píxeles. */
export interface Fit {
  readonly spPx: number;
  readonly topPx: number;
}

/** Las líneas siguen hacia la izquierda del comienzo, para cuando la línea de reproducción está en la primera nota. */
export const STAFF_LEAD = 60;
/** Ancho (en segundos) de la ventana con que se suaviza el avance de la vista. */
export const PLAYHEAD_SMOOTHING_SECONDS = 0.3;
const LEFT_PAD = 0.8;
const MEASURE_LEAD = 2.2;
const SIGNATURE_LEAD = 0.8;
const ACCIDENTAL_GAP = 0.15;
const SIGNATURE_GAP = 0.9;
const CLEF_CHANGE_SCALE = 0.8;
const CLEF_CHANGE_LEAD = 0.6;
const CLEF_CHANGE_GAP = 0.3;
const BAR_LABEL_SIZE = 1.8;
const BAR_LABEL_BASELINE = -3.0;
const EDGE_PAD = 0.5;

const BRACE_SCALE = Geometry.SYSTEM_BOTTOM / Smufl.BRACE_HEIGHT;
const SYSTEM_LEFT = LEFT_PAD + Smufl.BRACE_WIDTH * BRACE_SCALE + 0.1;
const CLEF_X = SYSTEM_LEFT + Geometry.THIN_BARLINE + 0.7;
/** Donde empieza la armadura en el área fija. */
export const SIGNATURE_LEFT = CLEF_X + Smufl.CLEF_ADVANCE + SIGNATURE_GAP;

// Altura de cada alteración de la armadura, en sp desde la línea superior de su pentagrama.
const SHARPS_TREBLE = [0, 1.5, -0.5, 1, 2.5, 0.5, 2];
const SHARPS_BASS = [1, 2.5, 0.5, 2, 3.5, 1.5, 3];
const FLATS_TREBLE = [2, 0.5, 2.5, 1, 3, 1.5, 3.5];
const FLATS_BASS = [3, 1.5, 3.5, 2, 4, 2.5, 4.5];

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

export interface ScoreLayoutInit {
  measures: MeasureLayout[];
  staff: DrawOp[];
  marks: DrawOp[];
  inlineChanges: DrawOp[];
  fixed: DrawOp[];
  changes: SignatureChange[];
  initial: DrawOp[];
  headerWidth: number;
  signatureLeft: number;
  totalWidth: number;
  quarterWidth: number;
  smooth: SmoothPath;
  marginTop: number;
  marginBottom: number;
  labelBuilder: (names: boolean, fingers: boolean) => NoteLabel[];
  chordSpans: ChordSpan[];
  initialClefs: Record<Hand, Clef>;
  clefChanges: ClefHeaderChange[];
}

export class ScoreLayout {
  readonly measures: MeasureLayout[];
  readonly staff: DrawOp[];
  readonly marks: DrawOp[];
  /** Cambios de armadura y compás en su lugar dentro del compás (solo para pruebas). */
  readonly inlineChanges: DrawOp[];
  readonly fixed: DrawOp[];
  readonly changes: SignatureChange[];
  readonly headerWidth: number;
  readonly signatureLeft: number;
  readonly totalWidth: number;
  readonly quarterWidth: number;
  readonly marginTop: number;
  readonly marginBottom: number;
  /** Qué marcas son de cada acorde (ordenados por tiempo). */
  readonly chordSpans: ChordSpan[];
  readonly clefChanges: ClefHeaderChange[];
  /** Todo lo que viaja con la partitura: líneas, marcas y cambios en su lugar dentro del compás. */
  readonly body: DrawOp[];
  private readonly initial: DrawOp[];
  private readonly smooth: SmoothPath;
  private readonly labelBuilder: (names: boolean, fingers: boolean) => NoteLabel[];
  private readonly initialClefs: Record<Hand, Clef>;

  private constructor(init: ScoreLayoutInit) {
    this.measures = init.measures;
    this.staff = init.staff;
    this.marks = init.marks;
    this.inlineChanges = init.inlineChanges;
    this.fixed = init.fixed;
    this.changes = init.changes;
    this.initial = init.initial;
    this.headerWidth = init.headerWidth;
    this.signatureLeft = init.signatureLeft;
    this.totalWidth = init.totalWidth;
    this.quarterWidth = init.quarterWidth;
    this.smooth = init.smooth;
    this.marginTop = init.marginTop;
    this.marginBottom = init.marginBottom;
    this.labelBuilder = init.labelBuilder;
    this.chordSpans = init.chordSpans;
    this.initialClefs = init.initialClefs;
    this.clefChanges = init.clefChanges;
    this.body = [...init.staff, ...init.marks, ...init.inlineChanges];
  }

  /** Nombres de nota y dedos según lo que se quiera ver; viajan con la partitura pero no forman parte de [marks]. */
  labels(names: boolean, fingers: boolean): NoteLabel[] {
    return this.labelBuilder(names, fingers);
  }

  /** Alto total que hay que mostrar, en espacios de pentagrama. */
  get totalHeight(): number {
    return this.marginTop + Geometry.SYSTEM_BOTTOM + this.marginBottom;
  }

  /**
   * Cómo acomodar la partitura en una pantalla de [heightPx] de alto, con espacios de a lo sumo [maxSpPx]. Abajo,
   * [controlsPx] los tapan los controles de reproducción: si lo dibujado llega hasta ahí, se achica un poco.
   */
  fit(heightPx: number, maxSpPx: number, controlsPx: number, labels: readonly NoteLabel[] = []): Fit {
    // Los nombres de nota y dedos, si se ven, pueden pedir más lugar arriba o abajo.
    const boxes = boxesOf(labels.map((l) => l.op));
    const mt = boxes.length === 0 ? this.marginTop : Math.max(this.marginTop, -Math.min(...boxes.map((b) => b.top)) + EDGE_PAD);
    const mb =
      boxes.length === 0
        ? this.marginBottom
        : Math.max(this.marginBottom, Math.max(...boxes.map((b) => b.bottom)) - Geometry.SYSTEM_BOTTOM + EDGE_PAD);
    const total = mt + Geometry.SYSTEM_BOTTOM + mb;
    const topFor = (sp: number) => (heightPx - total * sp) / 2 + mt * sp;
    const belowSystem = Geometry.SYSTEM_BOTTOM + mb - EDGE_PAD;
    const plain = Math.min(maxSpPx, heightPx / total);
    if (mb <= Geometry.MARGIN_BOTTOM || topFor(plain) + belowSystem * plain <= heightPx - controlsPx) {
      return { spPx: plain, topPx: topFor(plain) };
    }
    const sp = Math.min(plain, (heightPx - controlsPx) / (mt + belowSystem));
    return { spPx: sp, topPx: mt * sp };
  }

  private measureAt(tick: number): MeasureLayout {
    let found = 0;
    for (let i = this.measures.length - 1; i >= 0; i--) {
      if (this.measures[i]!.measure.startTick <= tick) {
        found = i;
        break;
      }
    }
    return this.measures[found]!;
  }

  /** Posición horizontal de un instante de la pieza. Es proporcional al tiempo, salvo donde hace falta más lugar. */
  xOfTick(tick: number): number {
    return this.measureAt(tick).xOfTick(tick);
  }

  /** El instante que corresponde a una posición horizontal de la partitura (al revés de [xOfTick]). */
  tickAtX(x: number): number {
    let m: MeasureLayout | undefined;
    for (let i = this.measures.length - 1; i >= 0; i--) {
      if (this.measures[i]!.contentX <= x) {
        m = this.measures[i];
        break;
      }
    }
    if (m === undefined) return this.measures[0]!.measure.startTick;
    return m.tickAtX(x);
  }

  /** Fin de la pieza, en ticks. */
  get endTick(): number {
    return this.measures[this.measures.length - 1]!.measure.endTick;
  }

  /** Desplazamiento (scroll) con el que el instante [tick] queda justo en la línea de reproducción ([playheadX] sp desde el borde). */
  scrollForTick(tick: number, playheadX: number): number {
    return this.smooth.positionAt(tick) + this.headerWidth - playheadX;
  }

  /** Al revés: qué instante está sobre la línea de reproducción con ese desplazamiento. */
  tickAtScroll(scroll: number, playheadX: number): number {
    return this.smooth.tickAt(scroll + playheadX - this.headerWidth);
  }

  private currentOps(scrollX: number): readonly DrawOp[] {
    let found: SignatureChange | undefined;
    for (const c of this.changes) if (c.pinScroll <= scrollX) found = c;
    return found?.ops ?? this.initial;
  }

  private static clefGlyph(hand: Hand, clef: Clef): DrawOp[] {
    const top = hand === "right" ? Geometry.TREBLE_TOP : Geometry.BASS_TOP;
    return [clef === "treble" ? glyph(Smufl.G_CLEF, CLEF_X, top + 3) : glyph(Smufl.F_CLEF, CLEF_X, top + 1)];
  }

  /** La clave vigente de cada mano con ese desplazamiento, sin contar transiciones en curso. */
  private currentClefs(scrollX: number): DrawOp[] {
    return HANDS.flatMap((hand) => {
      let done: ClefHeaderChange | undefined;
      for (const c of this.clefChanges) if (c.hand === hand && c.endScroll <= scrollX) done = c;
      return ScoreLayout.clefGlyph(hand, done?.clef ?? this.initialClefs[hand]);
    });
  }

  /**
   * Las claves fijas a dibujar con ese desplazamiento. Cuando la clave chica de un cambio llega al área fija, la clave
   * de esa mano se va desvaneciendo y aparece la nueva en su lugar.
   */
  clefDraws(scrollX: number): HeaderDraw[] {
    const out: HeaderDraw[] = [];
    for (const hand of HANDS) {
      const mine = this.clefChanges.filter((c) => c.hand === hand);
      let done: ClefHeaderChange | undefined;
      for (const c of mine) if (c.endScroll <= scrollX) done = c;
      const current = done?.clef ?? this.initialClefs[hand];
      const next = mine.find((c) => c.endScroll > scrollX);
      const p = next === undefined ? 0 : clamp((scrollX - next.startScroll) / (next.endScroll - next.startScroll), 0, 1);
      out.push({ ops: ScoreLayout.clefGlyph(hand, current), dx: 0, alpha: 1 - p });
      if (next !== undefined && p > 0) out.push({ ops: ScoreLayout.clefGlyph(hand, next.clef), dx: 0, alpha: p });
    }
    return out;
  }

  /** Lo que está fijo a la izquierda con ese desplazamiento, sin contar transiciones en curso. */
  headerAt(scrollX: number): Header {
    return { ops: [...this.fixed, ...this.currentClefs(scrollX), ...this.currentOps(scrollX)], width: this.headerWidth };
  }

  /** Avance de un cambio: 0 cuando toca el borde del área fija, 1 cuando coincide con su lugar fijo. */
  progress(change: SignatureChange, scrollX: number): number {
    const dx = change.pinScroll - scrollX;
    return clamp(1 - dx / (this.headerWidth - change.anchor), 0, 1);
  }

  /**
   * Armadura y compás a dibujar sobre [fixed]. Los vigentes se desvanecen a medida que el próximo cambio, que viaja
   * con la partitura, se les acerca y los reemplaza. [viewWidth] es el ancho visible (sp).
   */
  headerDraws(scrollX: number, viewWidth: number): HeaderDraw[] {
    const upcoming = this.changes.filter((c) => c.pinScroll > scrollX);
    const fade = upcoming.length > 0 ? this.progress(upcoming[0]!, scrollX) : 0;
    const out: HeaderDraw[] = [{ ops: this.currentOps(scrollX), dx: 0, alpha: 1 - fade }];
    for (const change of upcoming) {
      const dx = change.pinScroll - scrollX;
      if (dx > viewWidth) break;
      out.push({ ops: change.ops, dx, alpha: 1 });
      if (change.cancel.length > 0) out.push({ ops: change.cancel, dx, alpha: 1 - this.progress(change, scrollX) });
    }
    return out;
  }

  static build(score: Score, quarterWidth: number = Geometry.QUARTER_WIDTH, smoothingSeconds: number = PLAYHEAD_SMOOTHING_SECONDS): ScoreLayout {
    const measures = scoreMeasures(score);
    const marks: DrawOp[] = [];
    // Números de compás: (número, x). Se ubican al final, esquivando lo que tengan arriba.
    const labelAt: Array<readonly [number, number]> = [];
    const inline: DrawOp[] = [];
    const changes: SignatureChange[] = [];
    const clefHeader: ClefHeaderChange[] = [];
    const laidOut: MeasureLayout[] = [];
    const perTick = quarterWidth / TICKS_PER_QUARTER;

    // El área fija tiene lugar para la armadura y el compás más anchos de la pieza, así no cambia de tamaño
    // (y la partitura no pega saltos) cuando hay cambios.
    const piece = (fifths: number, numerator: number, denominator: number): { ops: DrawOp[]; end: number } => {
      const ops: DrawOp[] = [];
      let end = addAccidentals(ops, SIGNATURE_LEFT, fifths);
      if (fifths !== 0) end += SIGNATURE_GAP;
      end = addTimeSignature(ops, end, numerator, denominator);
      return { ops, end };
    };
    const headerWidth = Math.max(...measures.map((m) => piece(m.keyFifths, m.timeSignature.numerator, m.timeSignature.denominator).end)) + 1.0;
    const pin = (shift: number) => shift + headerWidth;

    let x = 0;
    measures.forEach((m, index) => {
      const x0 = x;
      let cursor = x0;
      const previous = measures[index - 1];
      // Cambio de clave: una clave chica al principio del compás, en el pentagrama de cada mano que cambia.
      const clefChanges =
        index === 0 ? [] : score.clefs.filter((c) => c.startTick === m.startTick && clefAt(score, c.hand, m.startTick - 1) !== c.clef);
      if (clefChanges.length > 0) {
        for (const c of clefChanges) {
          const clefX = cursor + CLEF_CHANGE_LEAD;
          clefHeader.push({ hand: c.hand, clef: c.clef, startScroll: clefX, endScroll: clefX + headerWidth - SIGNATURE_LEFT });
          const top = c.hand === "right" ? Geometry.TREBLE_TOP : Geometry.BASS_TOP;
          marks.push(
            c.clef === "treble"
              ? glyph(Smufl.G_CLEF, cursor + CLEF_CHANGE_LEAD, top + 3, CLEF_CHANGE_SCALE)
              : glyph(Smufl.F_CLEF, cursor + CLEF_CHANGE_LEAD, top + 1, CLEF_CHANGE_SCALE),
          );
        }
        cursor += CLEF_CHANGE_LEAD + Smufl.CLEF_ADVANCE * CLEF_CHANGE_SCALE + CLEF_CHANGE_GAP;
      }
      const keyChanged = previous !== undefined && previous.keyFifths !== m.keyFifths;
      const timeChanged =
        previous !== undefined &&
        (previous.timeSignature.numerator !== m.timeSignature.numerator || previous.timeSignature.denominator !== m.timeSignature.denominator);
      if (keyChanged || timeChanged) {
        // Becuadros + armadura + compás, pegados: es un solo bloque que después se queda fijo a la izquierda.
        cursor += SIGNATURE_LEAD;
        const n = m.timeSignature.numerator;
        const d = m.timeSignature.denominator;
        const anchorInline = addNaturals(inline, cursor, previous!.keyFifths, m.keyFifths);
        let end = addAccidentals(inline, anchorInline, m.keyFifths);
        if (m.keyFifths !== 0) end += SIGNATURE_GAP;
        end = addTimeSignature(inline, end, n, d);
        const cancel: DrawOp[] = [];
        addNaturals(cancel, SIGNATURE_LEFT - (anchorInline - cursor), previous!.keyFifths, m.keyFifths);
        const shift = anchorInline - SIGNATURE_LEFT;
        changes.push({ measureIndex: index, ops: piece(m.keyFifths, n, d).ops, cancel, anchor: SIGNATURE_LEFT, shift, pinScroll: pin(shift) });
        cursor = end + SIGNATURE_GAP;
        cursor += MEASURE_LEAD + 0.4 - SIGNATURE_GAP;
      } else {
        cursor += MEASURE_LEAD;
      }
      const anchors = Spacing.anchors(score, m, perTick, index === measures.length - 1);
      const contentX = cursor + anchors.leadExtra;
      const x1 = contentX + anchors.offsets[anchors.offsets.length - 1]!;

      labelAt.push([m.number, x0 + 0.35]);

      // Barra de compás que une los dos pentagramas. La última es doble: fina y gruesa.
      if (index === measures.length - 1) {
        const thickLeft = x1 - Geometry.THICK_BARLINE;
        marks.push(rect(thickLeft - 0.4 - Geometry.THIN_BARLINE, 0, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM));
        marks.push(rect(thickLeft, 0, Geometry.THICK_BARLINE, Geometry.SYSTEM_BOTTOM));
      } else {
        marks.push(rect(x1 - Geometry.THIN_BARLINE / 2, 0, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM));
      }

      laidOut.push(new MeasureLayout(m, x0, contentX, x1, anchors, perTick));
      x = x1;
    });

    const totalWidth = x;
    // El final de cada compás, en el tiempo, es la primera nota del siguiente (ver MeasureLayout.timeEndX).
    const timed = laidOut.map((m, i) => new MeasureLayout(m.measure, m.x0, m.contentX, m.x1, m.anchors, m.perTick, laidOut[i + 1]?.contentX ?? m.x1));
    const xOf = (tick: number): number => {
      let idx = 0;
      for (let i = timed.length - 1; i >= 0; i--) {
        if (timed[i]!.measure.startTick <= tick) {
          idx = i;
          break;
        }
      }
      return timed[idx]!.xOfTick(tick);
    };
    const notesFrom = marks.length;
    const builtNotes = NoteLayout.buildWithSpans(score, timed, xOf);
    marks.push(...builtNotes.ops);
    // Qué marcas son de cada acorde, con los números ya referidos a [marks].
    const chordSpans: ChordSpan[] = builtNotes.spans
      .map((s) => ({ chord: s.chord, from: s.from + notesFrom, to: s.to + notesFrom, heads: s.heads.map(([n, i]) => [n, i + notesFrom] as const) }))
      .sort((a, b) => a.chord.startTick - b.chord.startTick);
    // Signos de octava: arriba o abajo de lo que hayan dibujado las notas (sin contar las barras de compás, que cruzan todo).
    const beforeOttavas = boxesOf(marks.filter((op) => !(op.kind === "rect" && op.height >= Geometry.SYSTEM_BOTTOM - 0.01)));
    marks.push(...OttavaLayout.build(score, xOf, beforeOttavas));
    // Las letras de dinámica esquivan lo que haya abajo (notas graves, acentos); los números de compás, lo que haya arriba.
    const noteBoxes = boxesOf(marks);
    marks.push(...DynamicsLayout.build(score, xOf, (from, to) => lowestIn(noteBoxes, from, to, Geometry.SYSTEM_BOTTOM)));
    // Pedal: debajo del pentagrama de la izquierda y de lo que haya ahí.
    marks.push(...PedalLayout.build(score, xOf, boxesOf(marks)));
    const aboveBoxes = boxesOf(marks);
    for (const [number, labelX] of labelAt) {
      const label = String(number);
      const width = BAR_LABEL_SIZE * 0.6 * label.length;
      const clearTop = highestIn(aboveBoxes, labelX - 0.3, labelX + width + 0.3, 0);
      marks.push(textOp(label, labelX, Math.min(BAR_LABEL_BASELINE, clearTop - 0.4), BAR_LABEL_SIZE));
    }
    // Nombres de nota y dedos: se calculan al mostrarse; si hace falta más margen, lo pide fit().
    const labelObstacles: Box[] = boxesOf(marks);
    const labelBuilder = (names: boolean, fingers: boolean) => NoteLabelLayout.build(score, xOf, labelObstacles, names, fingers);
    // Margen arriba y abajo según lo que de verdad se dibujó: nada queda cortado ni tapado por los controles.
    const all = boxesOf(marks);
    let minTop = Infinity;
    let maxBottom = -Infinity;
    for (const b of all) {
      minTop = Math.min(minTop, b.top);
      maxBottom = Math.max(maxBottom, b.bottom);
    }
    const marginTop = Math.max(Geometry.MARGIN_TOP, -minTop + EDGE_PAD);
    const marginBottom = Math.max(Geometry.MARGIN_BOTTOM, maxBottom - Geometry.SYSTEM_BOTTOM + EDGE_PAD);
    const fixed: DrawOp[] = [];
    fixed.push(...staffLines(SYSTEM_LEFT, headerWidth));
    fixed.push(glyph(Smufl.BRACE, LEFT_PAD, Geometry.SYSTEM_BOTTOM, BRACE_SCALE));
    fixed.push(rect(SYSTEM_LEFT, 0, Geometry.THIN_BARLINE, Geometry.SYSTEM_BOTTOM));
    // Las claves no van acá: cambian con la pieza (ver clefDraws).

    const smooth = SmoothPath.build(timed[timed.length - 1]!.measure.endTick, xOf, new TempoMap(score.tempos), smoothingSeconds);

    const first = measures[0]!;
    const initial = piece(first.keyFifths, first.timeSignature.numerator, first.timeSignature.denominator).ops;
    return new ScoreLayout({
      measures: timed,
      staff: staffLines(-STAFF_LEAD, totalWidth),
      marks,
      inlineChanges: inline,
      fixed,
      changes,
      initial,
      headerWidth,
      signatureLeft: SIGNATURE_LEFT,
      totalWidth,
      quarterWidth,
      smooth,
      marginTop,
      marginBottom,
      labelBuilder,
      chordSpans,
      initialClefs: { right: clefAt(score, "right", first.startTick), left: clefAt(score, "left", first.startTick) },
      clefChanges: clefHeader,
    });
  }
}

/** Las diez líneas de los dos pentagramas, entre [fromX] y [toX]. */
function staffLines(fromX: number, toX: number): DrawOp[] {
  const t = Geometry.STAFF_LINE_THICKNESS;
  const out: DrawOp[] = [];
  for (const top of [Geometry.TREBLE_TOP, Geometry.BASS_TOP]) {
    for (let line = 0; line <= 4; line++) out.push(rect(fromX, top + line - t / 2, toX - fromX, t));
  }
  return out;
}

function place(ops: DrawOp[], x: number, codepoint: number, trebleY: number, bassY: number): void {
  ops.push(glyph(codepoint, x, Geometry.TREBLE_TOP + trebleY));
  ops.push(glyph(codepoint, x, Geometry.BASS_TOP + bassY));
}

/** Dibuja las alteraciones de la armadura [fifths] en los dos pentagramas. Devuelve donde terminan. */
function addAccidentals(ops: DrawOp[], startX: number, fifths: number): number {
  let x = startX;
  for (let i = 0; i < Math.abs(fifths); i++) {
    if (fifths > 0) {
      place(ops, x, Smufl.SHARP, SHARPS_TREBLE[i]!, SHARPS_BASS[i]!);
      x += Smufl.SHARP_ADVANCE + ACCIDENTAL_GAP;
    } else {
      place(ops, x, Smufl.FLAT, FLATS_TREBLE[i]!, FLATS_BASS[i]!);
      x += Smufl.FLAT_ADVANCE + ACCIDENTAL_GAP;
    }
  }
  return fifths !== 0 ? x - ACCIDENTAL_GAP : x;
}

/**
 * Becuadros que cancelan las alteraciones de la armadura anterior [oldFifths] que ya no valen con la nueva
 * [newFifths]. Devuelve donde tiene que empezar lo que sigue (si hay becuadros, con su separación).
 */
function addNaturals(ops: DrawOp[], startX: number, oldFifths: number, newFifths: number): number {
  let x = startX;
  const sameKind = oldFifths !== 0 && newFifths !== 0 && oldFifths > 0 === newFifths > 0;
  const from = sameKind ? Math.abs(newFifths) : 0;
  for (let i = from; i < Math.abs(oldFifths); i++) {
    const trebleY = (oldFifths > 0 ? SHARPS_TREBLE : FLATS_TREBLE)[i]!;
    const bassY = (oldFifths > 0 ? SHARPS_BASS : FLATS_BASS)[i]!;
    place(ops, x, Smufl.NATURAL, trebleY, bassY);
    x += Smufl.NATURAL_ADVANCE + ACCIDENTAL_GAP;
  }
  // Si no sigue ninguna alteración, lo que viene (el compás) va con la separación normal.
  return newFifths === 0 && x > startX ? x - ACCIDENTAL_GAP + SIGNATURE_GAP : x;
}

function digitsWidth(label: string): number {
  let sum = 0;
  for (const c of label) sum += Smufl.timeDigitAdvance(c.charCodeAt(0) - 48);
  return sum;
}

/** Firma de compás con el numerador arriba y el denominador abajo, en los dos pentagramas. Devuelve donde termina. */
function addTimeSignature(ops: DrawOp[], startX: number, numerator: number, denominator: number): number {
  const top = String(numerator);
  const bottom = String(denominator);
  const topWidth = digitsWidth(top);
  const bottomWidth = digitsWidth(bottom);
  const width = Math.max(topWidth, bottomWidth);
  for (const staffTop of [Geometry.TREBLE_TOP, Geometry.BASS_TOP]) {
    placeDigits(ops, top, startX + (width - topWidth) / 2, staffTop + 1);
    placeDigits(ops, bottom, startX + (width - bottomWidth) / 2, staffTop + 3);
  }
  return startX + width;
}

function placeDigits(ops: DrawOp[], label: string, startX: number, baseline: number): void {
  let x = startX;
  for (const c of label) {
    const digit = c.charCodeAt(0) - 48;
    ops.push(glyph(Smufl.timeDigit(digit), x, baseline));
    x += Smufl.timeDigitAdvance(digit);
  }
}
