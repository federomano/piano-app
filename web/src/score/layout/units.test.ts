/* PIA-89: pruebas chicas portadas de Kotlin (ZoomTest, PlaybackTest) para las piezas que los ejemplos no ejercitan del todo. */
import { describe, expect, it } from "vitest";
import { Geometry } from "./geometry";
import { TempoMap } from "./tempo-map";
import * as Zoom from "./zoom";

describe("Zoom", () => {
  const base = { spPx: 40, topPx: 220 };

  it("tamaño normal no cambia nada", () => {
    expect(Zoom.apply(base, 1, 1000, 150)).toEqual(base);
  });

  it("achicar deja el centro de los pentagramas en su lugar", () => {
    const z = Zoom.apply(base, 0.5, 1000, 150);
    expect(z.spPx).toBeCloseTo(20, 4);
    expect(z.topPx + 7 * 20).toBeCloseTo(base.topPx + 7 * 40, 3);
  });

  it("el máximo nunca baja de 1 ni pasa el tope", () => {
    expect(Zoom.max(500, 150, 40)).toBe(1);
    expect(Zoom.max(5000, 150, 10)).toBe(Zoom.MAX);
    const m = Zoom.max(1000, 150, 40);
    expect(m).toBeGreaterThanOrEqual(1);
    expect(m).toBeLessThanOrEqual(Zoom.MAX);
  });

  it("agrandada, queda entre el borde de arriba y los controles", () => {
    const heightPx = 1000;
    const controls = 150;
    const b = { spPx: 30, topPx: 200 };
    const z = Zoom.apply(b, Zoom.max(heightPx, controls, b.spPx), heightPx, controls);
    expect(z.topPx).toBeGreaterThanOrEqual(3.2 * z.spPx - 0.01);
    expect(z.topPx + Geometry.SYSTEM_BOTTOM * z.spPx).toBeLessThanOrEqual(heightPx - controls + 0.01);
  });

  it("clamp deja el valor en el rango", () => {
    expect(Zoom.clamp(0.1, 1.3)).toBe(0.5);
    expect(Zoom.clamp(2, 1.3)).toBe(1.3);
    expect(Zoom.clamp(0.8, 1.3)).toBe(0.8);
  });
});

describe("TempoMap", () => {
  it("una negra a 60 es un segundo", () => {
    expect(new TempoMap([{ startTick: 0, bpm: 60 }]).advance(0, 1)).toBeCloseTo(480, 1);
  });

  it("cruza los cambios de tempo usando cada uno", () => {
    const map = new TempoMap([
      { startTick: 0, bpm: 60 },
      { startTick: 960, bpm: 120 },
    ]);
    expect(map.advance(0, 3)).toBeCloseTo(1920, 1);
    expect(map.advance(480, 2)).toBeCloseTo(1920, 1);
  });

  it("advance y secondsBetween son inversas", () => {
    const map = new TempoMap([
      { startTick: 0, bpm: 90 },
      { startTick: 5760, bpm: 60 },
      { startTick: 10080, bpm: 80 },
    ]);
    expect(map.advance(1000, map.secondsBetween(1000, 12000))).toBeCloseTo(12000, 0);
  });

  it("muchos pasos chicos dan lo mismo que uno grande", () => {
    const map = new TempoMap([
      { startTick: 0, bpm: 90 },
      { startTick: 5760, bpm: 60 },
    ]);
    let tick = 0;
    for (let i = 0; i < 600; i++) tick = map.advance(tick, 1 / 60);
    expect(Math.abs(tick - map.advance(0, 10))).toBeLessThan(1);
  });

  it("sin tempos usa 100 negras por minuto", () => {
    expect(new TempoMap([]).bpmAt(0)).toBe(100);
  });
});
