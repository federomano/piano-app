/*
 * El recorrido de la vista durante la reproducción (PIA-89). Mismo código que SmoothPath.kt: la posición
 * "exacta" de cada instante, promediada en una pequeña ventana de tiempo, así la velocidad cambia de forma
 * gradual. La ventana es simétrica; en el comienzo y el final coincide con la posición exacta.
 */
import type { TempoMap } from "./tempo-map";

const f = Math.fround;
const STEP_SECONDS = f(1 / 120);

function firstIndexAbove(values: Float32Array, v: number): number {
  let lo = 0;
  let hi = values.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (values[mid]! <= v) lo = mid;
    else hi = mid;
  }
  return hi;
}

export class SmoothPath {
  private constructor(
    private readonly ticks: Float32Array,
    private readonly positions: Float32Array,
  ) {}

  /** Posición en el instante [tick]. */
  positionAt(tick: number): number {
    const { ticks, positions } = this;
    if (tick <= ticks[0]!) return positions[0]!;
    if (tick >= ticks[ticks.length - 1]!) return positions[positions.length - 1]!;
    const i = firstIndexAbove(ticks, tick);
    const span = f(ticks[i]! - ticks[i - 1]!);
    return f(positions[i - 1]! + f(f(f(positions[i]! - positions[i - 1]!) * f(tick - ticks[i - 1]!)) / span));
  }

  /** Lo inverso: el instante que corresponde a una posición. */
  tickAt(position: number): number {
    const { ticks, positions } = this;
    if (position <= positions[0]!) return ticks[0]!;
    if (position >= positions[positions.length - 1]!) return ticks[ticks.length - 1]!;
    const i = firstIndexAbove(positions, position);
    const span = f(positions[i]! - positions[i - 1]!);
    if (span <= 0) return ticks[i]!;
    return f(ticks[i - 1]! + f(f(f(ticks[i]! - ticks[i - 1]!) * f(position - positions[i - 1]!)) / span));
  }

  /** [exactX] da la posición exacta de un instante; [tempo] pasa de segundos a instantes; [windowSeconds] es el ancho de la ventana. */
  static build(endTick: number, exactX: (tick: number) => number, tempo: TempoMap, windowSeconds: number): SmoothPath {
    const total = tempo.secondsBetween(0, endTick);
    const steps = Math.max(2, Math.ceil(f(total / STEP_SECONDS)));
    const half = Math.max(1, Math.trunc(f(f(f(windowSeconds) / STEP_SECONDS) / 2)));

    // Instante y posición exacta en cada paso; el último paso cae justo en el final.
    const tickAt = new Float32Array(steps + 1);
    for (let i = 1; i <= steps; i++) tickAt[i] = i === steps ? endTick : Math.min(tempo.advance(tickAt[i - 1]!, STEP_SECONDS), endTick);
    const exact = new Float32Array(steps + 1);
    for (let i = 0; i <= steps; i++) exact[i] = exactX(tickAt[i]!);

    // Fuera de la pieza se sigue con el reflejo respecto del extremo (así el promedio en las puntas da la posición exacta).
    const valueAt = (i: number): number => {
      if (i < 0) return f(2 * exact[0]! - exact[Math.min(-i, steps)]!);
      if (i > steps) return f(2 * exact[steps]! - exact[Math.max(2 * steps - i, 0)]!);
      return exact[i]!;
    };
    const prefix = new Float64Array(steps + 2 * half + 2);
    for (let k = 0; k < prefix.length - 1; k++) prefix[k + 1] = prefix[k]! + valueAt(k - half);
    const smooth = new Float32Array(steps + 1);
    for (let i = 0; i <= steps; i++) smooth[i] = (prefix[i + 2 * half + 1]! - prefix[i]!) / (2 * half + 1);
    // Se asegura que siga siempre creciendo.
    for (let i = 1; i <= steps; i++) if (smooth[i]! < smooth[i - 1]!) smooth[i] = smooth[i - 1]!;
    return new SmoothPath(tickAt, smooth);
  }
}
