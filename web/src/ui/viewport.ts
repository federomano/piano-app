/*
 * Cómo se acomoda la partitura en la ventana (PIA-90). Es el cálculo que hace ScoreView.kt en la app del Pixel:
 * tamaño de un espacio de pentagrama, línea de reproducción al 30% del ancho, y hasta dónde se puede deslizar.
 * Va aparte del dibujo para poder probarlo sin pantalla.
 */
import { Geometry, type ScoreLayout, Zoom } from "../score/layout";

/** Tamaño máximo de un espacio de pentagrama, en píxeles de pantalla (17 dp en la app del Pixel). */
export const MAX_STAFF_SPACE_PX = 17;

/** Cuánto se achica la velocidad de la inercia por segundo (igual que FLING_RATE de Android). */
export const FLING_RATE = 3.8;
/** La inercia termina cuando la velocidad baja de esto (espacios por segundo). */
export const FLING_END_SPEED = 0.4;
/** Al soltar, velocidad mínima (espacios por segundo) con la que la partitura sigue deslizándose. */
export const MIN_FLING_SPEED = 1.5;

export interface Viewport {
  readonly widthPx: number;
  readonly heightPx: number;
  /** Un espacio de pentagrama, en píxeles. */
  readonly spPx: number;
  /** Dónde cae la línea superior del pentagrama de la derecha, en píxeles. */
  readonly topPx: number;
  /** Línea de reproducción, en espacios de pentagrama desde el borde izquierdo. */
  readonly playheadSp: number;
  /** Desplazamiento con el comienzo de la pieza sobre la línea de reproducción. */
  readonly minScroll: number;
  /** Desplazamiento con el final de la pieza sobre la línea de reproducción. */
  readonly maxScroll: number;
}

export function computeViewport(
  layout: ScoreLayout,
  widthPx: number,
  heightPx: number,
  controlsPx = 0,
  zoom: number = Zoom.DEFAULT,
): Viewport {
  const base = layout.fit(heightPx, MAX_STAFF_SPACE_PX, controlsPx);
  const zoomNow = Zoom.clamp(zoom, Zoom.max(heightPx, controlsPx, base.spPx));
  const fit = Zoom.apply(base, zoomNow, heightPx, controlsPx);
  const playheadSp = (widthPx / fit.spPx) * Geometry.PLAYHEAD_FRACTION;
  return {
    widthPx,
    heightPx,
    spPx: fit.spPx,
    topPx: fit.topPx,
    playheadSp,
    minScroll: layout.scrollForTick(0, playheadSp),
    maxScroll: layout.scrollForTick(layout.endTick, playheadSp),
  };
}

/** Al cambiar el tamaño la línea de reproducción cambia de lugar en espacios: se reacomoda para seguir sobre la misma nota. */
export function retarget(layout: ScoreLayout, scroll: number, oldPlayheadSp: number, newPlayheadSp: number): number {
  if (oldPlayheadSp === newPlayheadSp) return scroll;
  return layout.scrollForTick(layout.tickAtScroll(scroll, oldPlayheadSp), newPlayheadSp);
}

export const clampScroll = (scroll: number, view: Viewport): number => Math.min(view.maxScroll, Math.max(view.minScroll, scroll));

/** Velocidad después de [seconds], frenando hacia cero (la inercia de Android, con la reproducción en pausa). */
export function inertiaStep(velocity: number, seconds: number): number {
  return velocity * Math.exp(-FLING_RATE * seconds);
}
