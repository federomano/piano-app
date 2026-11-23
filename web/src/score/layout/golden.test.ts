/*
 * PIA-89: el layout calculado en TypeScript tiene que dar lo mismo que el de Kotlin para cada archivo de docs/ejemplos.
 * Los valores de referencia están en fixtures/ (los genera LayoutGoldenDump.kt). Kotlin usa números de 32 bits, así que se
 * compara con una tolerancia chica.
 */
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { readScoreText } from "../read";
import { EXAMPLES_DIR, exampleFiles } from "../test-support";
import type { DrawOp } from "./drawops";
import { box } from "./extents";
import { ScoreLayout } from "./score-layout";
import * as NoteStates from "./note-state";
import { playedNotes, velocityAt, velocityOf } from "../tools";

const FIXTURES = join(__dirname, "fixtures");
const TOL = 1e-4;

type Json = any; // eslint-disable-line @typescript-eslint/no-explicit-any

function encode(op: DrawOp): Json[] {
  switch (op.kind) {
    case "rect":
      return ["r", op.x, op.y, op.width, op.height];
    case "glyph":
      return ["g", op.codepoint, op.x, op.y, op.scale];
    case "text":
      return ["t", op.text, op.x, op.y, op.size];
    case "polygon":
      return ["p", [...op.points]];
  }
}

function close(actual: unknown, expected: unknown, path: string): string | null {
  if (typeof expected === "number") {
    if (typeof actual !== "number") return `${path}: se esperaba un número y vino ${JSON.stringify(actual)}`;
    const tol = TOL * Math.max(1, Math.abs(expected));
    return Math.abs(actual - expected) <= tol ? null : `${path}: ${actual} (TS) vs ${expected} (Kotlin)`;
  }
  if (Array.isArray(expected)) {
    if (!Array.isArray(actual)) return `${path}: se esperaba una lista`;
    if (actual.length !== expected.length) return `${path}: largo ${actual.length} (TS) vs ${expected.length} (Kotlin)`;
    for (let i = 0; i < expected.length; i++) {
      const e = close(actual[i], expected[i], `${path}[${i}]`);
      if (e) return e;
    }
    return null;
  }
  if (expected !== null && typeof expected === "object") {
    if (actual === null || typeof actual !== "object") return `${path}: se esperaba un objeto`;
    const keys = Object.keys(expected);
    for (const k of keys) {
      const e = close((actual as Json)[k], (expected as Json)[k], `${path}.${k}`);
      if (e) return e;
    }
    return null;
  }
  return actual === expected ? null : `${path}: ${JSON.stringify(actual)} (TS) vs ${JSON.stringify(expected)} (Kotlin)`;
}

function same(actual: unknown, expected: unknown, path: string): void {
  const problem = close(actual, expected, path);
  if (problem) throw new Error(problem);
}

const opsOf = (list: readonly DrawOp[]) => list.map(encode);

for (const file of exampleFiles()) {
  describe(`layout de ${file}`, () => {
    const text = readFileSync(join(EXAMPLES_DIR, file), "utf8");
    const read = readScoreText(text);
    if (!read.ok) throw new Error(`${file} no abre: ${read.errors.join("; ")}`);
    const layout = ScoreLayout.build(read.score);
    const gold: Json = JSON.parse(readFileSync(join(FIXTURES, file), "utf8"));

    it("medidas del sistema", () => {
      same(layout.headerWidth, gold.headerWidth, "headerWidth");
      same(layout.signatureLeft, gold.signatureLeft, "signatureLeft");
      same(layout.totalWidth, gold.totalWidth, "totalWidth");
      same(layout.marginTop, gold.marginTop, "marginTop");
      same(layout.marginBottom, gold.marginBottom, "marginBottom");
      same(layout.endTick, gold.endTick, "endTick");
    });

    it("compases y puntos de anclaje", () => {
      expect(layout.measures.length).toBe(gold.measures.length);
      layout.measures.forEach((m, i) => {
        const g = gold.measures[i];
        const mine = {
          number: m.measure.number,
          startTick: m.measure.startTick,
          endTick: m.measure.endTick,
          keyFifths: m.measure.keyFifths,
          gridOffset: m.measure.gridOffset,
          x0: m.x0,
          contentX: m.contentX,
          x1: m.x1,
          timeEndX: m.timeEndX,
          anchorTicks: m.anchors ? [...m.anchors.ticks] : [],
          anchorOffsets: m.anchors ? [...m.anchors.offsets] : [],
          leadExtra: m.anchors?.leadExtra ?? 0,
        };
        same(mine, g, `compás[${i}]`);
      });
    });

    it("líneas, marcas y cambios dibujados", () => {
      same(opsOf(layout.staff), gold.staff, "staff");
      same(opsOf(layout.fixed), gold.fixed, "fixed");
      same(opsOf(layout.inlineChanges), gold.inlineChanges, "inlineChanges");
      same(
        layout.changes.map((c) => ({
          measureIndex: c.measureIndex,
          ops: opsOf(c.ops),
          cancel: opsOf(c.cancel),
          anchor: c.anchor,
          shift: c.shift,
          pinScroll: c.pinScroll,
        })),
        gold.changes,
        "changes",
      );
      same(layout.clefChanges, gold.clefChanges, "clefChanges");
    });

    it("notas, silencios, barras, ligaduras, adornos y demás (marks)", () => {
      same(opsOf(layout.marks), gold.marks, "marks");
    });

    it("qué instrucciones son de cada acorde", () => {
      same(
        layout.chordSpans.map((s) => ({
          hand: s.chord.hand,
          voice: s.chord.voice,
          startTick: s.chord.startTick,
          durationTicks: s.chord.durationTicks,
          notes: s.chord.notes.length,
          from: s.from,
          to: s.to,
          heads: s.heads.map(([n, i]) => [n.pitch.midi, i]),
        })),
        gold.chordSpans,
        "chordSpans",
      );
    });

    it("nombres de nota y dedos", () => {
      for (const [key, names, fingers] of [["names", true, false], ["fingers", false, true], ["both", true, true]] as const) {
        same(
          layout.labels(names, fingers).map((l) => [l.hand, encode(l.op)]),
          gold.labels[key],
          `labels.${key}`,
        );
      }
    });

    it("acomodo en pantalla (fit)", () => {
      const both = layout.labels(true, true);
      const fits = [layout.fit(800, 40, 120), layout.fit(300, 40, 150), layout.fit(800, 40, 120, both)];
      same(fits.map((f) => [f.spPx, f.topPx]), gold.fit, "fit");
    });

    it("posición de cada instante y recorrido de la vista", () => {
      const s = gold.samples;
      const ticks: number[] = s.ticks;
      same(ticks.map((t) => layout.xOfTick(t)), s.xOfTick, "xOfTick");
      same(ticks.map((t) => layout.tickAtX(layout.xOfTick(t) + 0.3)), s.tickAtX, "tickAtX");
      const ph: number = s.playheadX;
      same(ticks.map((t) => layout.scrollForTick(t, ph)), s.scrollForTick, "scrollForTick");
      same(ticks.map((t) => layout.tickAtScroll(layout.scrollForTick(t, ph) + 0.7, ph)), s.tickAtScroll, "tickAtScroll");
    });

    it("área fija a la izquierda en distintos desplazamientos", () => {
      const s = gold.samples;
      const scrolls: number[] = s.scrolls;
      const draws = (list: ReturnType<ScoreLayout["headerDraws"]>) => list.map((d) => ({ ops: opsOf(d.ops), dx: d.dx, alpha: d.alpha }));
      same(scrolls.map((x) => draws(layout.headerDraws(x, 60))), s.headerDraws, "headerDraws");
      same(scrolls.map((x) => draws(layout.clefDraws(x))), s.clefDraws, "clefDraws");
    });

    it("qué acordes suenan en cada instante", () => {
      const s = gold.samples;
      const ticks: number[] = s.ticks;
      same(
        ticks.map((t) => [...NoteStates.current(layout.chordSpans, t)].sort((a, b) => a - b)),
        s.current,
        "current",
      );
    });

    it("intensidad y notas como suenan", () => {
      const s = gold.samples;
      const ticks: number[] = s.ticks;
      const score = read.score;
      same(ticks.map((t) => velocityAt(score, t)), s.velocityAt, "velocityAt");
      same(score.notes.map((n) => velocityOf(score, n)), s.velocityOfNotes, "velocityOf");
      same(playedNotes(score).map((n) => [n.pitch.midi, n.startTick, n.durationTicks]), s.playedNotes, "playedNotes");
    });

    it("las cajas de lo dibujado son finitas", () => {
      for (const op of layout.marks) {
        const b = box(op);
        expect(Number.isFinite(b.left + b.top + b.right + b.bottom)).toBe(true);
      }
    });
  });
}
