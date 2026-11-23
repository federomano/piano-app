/* Un compás ya ubicado en el eje horizontal (PIA-89). Mismo código que MeasureLayout de ScoreLayout.kt. */
import type { Measure } from "../tools";
import type { Anchors } from "./spacing";

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

export class MeasureLayout {
  constructor(
    readonly measure: Measure,
    /** Donde está la barra de compás de la izquierda (en el primero, el comienzo de la pieza). */
    readonly x0: number,
    /** Donde empieza el tiempo del compás (después de un eventual cambio de armadura o de compás). */
    readonly contentX: number,
    /** Barra de compás de la derecha. */
    readonly x1: number,
    /** Puntos de anclaje del tiempo del compás (ver Spacing). */
    readonly anchors: Anchors | null = null,
    readonly perTick = 0,
    /**
     * Posición que corresponde al final del compás en el tiempo: la primera nota del compás siguiente.
     * El tramo entre la última nota y ese punto incluye la barra de compás (y un eventual cambio de armadura).
     */
    readonly timeEndX: number = x1,
  ) {}

  /** Posición horizontal de un instante dentro de este compás. */
  xOfTick(tick: number): number {
    const t = Math.min(tick, this.measure.endTick);
    const a = this.anchors;
    if (a === null) return this.contentX + (t - this.measure.startTick) * this.perTick;
    const last = a.ticks.length - 1;
    if (last >= 1 && t > a.ticks[last - 1]!) {
      // Último tramo: de la última nota a la primera del compás siguiente.
      const fromX = this.contentX + a.offsets[last - 1]!;
      return fromX + ((this.timeEndX - fromX) * (t - a.ticks[last - 1]!)) / (a.ticks[last]! - a.ticks[last - 1]!);
    }
    return this.contentX + a.offsetOf(t, this.perTick);
  }

  /** El instante que corresponde a una posición horizontal dentro de este compás. */
  tickAtX(x: number): number {
    const a = this.anchors;
    if (a === null) return this.measure.startTick + (x - this.contentX) / this.perTick;
    const last = a.ticks.length - 1;
    if (last >= 1 && x > this.contentX + a.offsets[last - 1]!) {
      const fromX = this.contentX + a.offsets[last - 1]!;
      const fraction = clamp((x - fromX) / (this.timeEndX - fromX), 0, 1);
      return a.ticks[last - 1]! + (a.ticks[last]! - a.ticks[last - 1]!) * fraction;
    }
    return clamp(a.tickAt(x - this.contentX, this.perTick), this.measure.startTick, this.measure.endTick);
  }
}
