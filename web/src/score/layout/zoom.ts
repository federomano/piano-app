/*
 * Tamaño de la partitura a gusto (PIA-89): un factor que multiplica el tamaño "justo" que calcula ScoreLayout.fit.
 * Mismo código que Zoom.kt. Achicar (hasta la mitad) deja ver más compases; agrandar tiene un tope para que los dos
 * pentagramas sigan entrando en pantalla sin quedar tapados por los controles de abajo.
 */
import { Geometry } from "./geometry";
import type { Fit } from "./score-layout";

export const MIN = 0.5;
export const MAX = 1.6;
export const DEFAULT = 1;

/** Espacios que hace falta ver arriba del pentagrama de la derecha (números de compás) y abajo del de la izquierda. */
const ABOVE = 3.2;
const BELOW = 0.5;

const coerce = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** El mayor factor que deja entrar la partitura entre el borde de arriba y los controles. Nunca menos que 1 ni más que MAX. */
export function max(heightPx: number, controlsPx: number, baseSpPx: number): number {
  return coerce((heightPx - controlsPx) / ((ABOVE + Geometry.SYSTEM_BOTTOM + BELOW) * baseSpPx), DEFAULT, MAX);
}

export function clamp(zoom: number, maxZoom: number): number {
  return coerce(zoom, MIN, maxZoom);
}

/** El mismo acomodo pero con el tamaño multiplicado por [zoom] (ya limitado con clamp), alrededor del centro de los pentagramas. */
export function apply(base: Fit, zoom: number, heightPx: number, controlsPx: number): Fit {
  if (zoom === DEFAULT) return base;
  const sp = base.spPx * zoom;
  const middle = Geometry.SYSTEM_BOTTOM / 2;
  const centered = base.topPx + middle * base.spPx - middle * sp;
  const top =
    zoom < DEFAULT
      ? centered
      : Math.min(Math.max(centered, ABOVE * sp), heightPx - controlsPx - (Geometry.SYSTEM_BOTTOM + BELOW) * sp);
  return { spPx: sp, topPx: top };
}
