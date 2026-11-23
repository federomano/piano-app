/* PIA-90: cálculo del acomodo en la ventana y dibujo (con un canvas de mentira que anota lo que se le pide). */
import { describe, expect, it } from "vitest";
import { EJEMPLOS, EJEMPLO_INICIAL } from "../ejemplos";
import { prepararPartitura } from "../app";
import { Geometry } from "../score/layout";
import { type Ctx, paintScore } from "./paint";
import { clampScroll, computeViewport, inertiaStep, retarget } from "./viewport";

function layoutDe(nombre: string) {
  const r = prepararPartitura(EJEMPLOS.find((e) => e.nombre === nombre)!.texto);
  if (!r.ok) throw new Error(r.avisos.join("; "));
  return r.layout;
}

describe("acomodo en la ventana", () => {
  const layout = layoutDe(EJEMPLO_INICIAL);

  it("la línea de reproducción queda al 30% del ancho", () => {
    const v = computeViewport(layout, 1000, 600);
    expect(v.playheadSp * v.spPx).toBeCloseTo(1000 * Geometry.PLAYHEAD_FRACTION, 6);
  });

  it("la partitura entra en el alto y el espacio no pasa del tope", () => {
    for (const h of [300, 600, 1200]) {
      const v = computeViewport(layout, 1000, h);
      expect(v.spPx).toBeLessThanOrEqual(17 + 1e-9);
      expect(v.topPx).toBeGreaterThanOrEqual(0);
      expect(v.topPx + Geometry.SYSTEM_BOTTOM * v.spPx).toBeLessThanOrEqual(h + 1e-6);
    }
  });

  it("el recorrido va del comienzo al final y clampScroll lo respeta", () => {
    const v = computeViewport(layout, 1000, 600);
    expect(v.maxScroll).toBeGreaterThan(v.minScroll);
    expect(clampScroll(-1e6, v)).toBe(v.minScroll);
    expect(clampScroll(1e6, v)).toBe(v.maxScroll);
  });

  it("al cambiar el ancho, sigue sobre la misma nota", () => {
    const a = computeViewport(layout, 1000, 600);
    const b = computeViewport(layout, 500, 600);
    const scroll = (a.minScroll + a.maxScroll) / 2;
    const tick = layout.tickAtScroll(scroll, a.playheadSp);
    const moved = retarget(layout, scroll, a.playheadSp, b.playheadSp);
    expect(layout.tickAtScroll(moved, b.playheadSp)).toBeCloseTo(tick, 1);
  });

  it("la inercia frena", () => {
    expect(inertiaStep(10, 0)).toBe(10);
    expect(Math.abs(inertiaStep(10, 1))).toBeLessThan(1);
  });
});

function falso() {
  const llamadas: string[] = [];
  const ctx = new Proxy(
    {},
    {
      get: (_t, name: string) => (...args: unknown[]) => {
        llamadas.push(`${name}:${args.length}`);
      },
      set: () => true,
    },
  ) as unknown as Ctx;
  return { ctx, llamadas };
}

describe("dibujo", () => {
  it("dibuja fondo, símbolos, polígonos y la línea de reproducción en todos los ejemplos, en varios desplazamientos", () => {
    for (const e of EJEMPLOS) {
      const layout = layoutDe(e.nombre);
      const v = computeViewport(layout, 900, 500);
      for (const f of [0, 0.5, 1]) {
        const { ctx, llamadas } = falso();
        paintScore(ctx, layout, v, v.minScroll + (v.maxScroll - v.minScroll) * f);
        expect(llamadas.filter((c) => c.startsWith("fillRect")).length, e.nombre).toBeGreaterThan(10);
        expect(llamadas.some((c) => c.startsWith("fillText")), e.nombre).toBe(true);
        expect(llamadas.filter((c) => c.startsWith("stroke")).length, e.nombre).toBe(1);
      }
    }
  });
});
