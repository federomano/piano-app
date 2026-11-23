/*
 * Cuenta de tiempo de la reproducción (PIA-89): pasa de segundos a posición en la partitura (ticks)
 * respetando los cambios de tempo. Mismo código que TempoMap de app/.../score/Playback.kt.
 * Kotlin usa Float de 32 bits: Math.fround imita esa precisión donde decide cuántos pasos hay.
 */
import type { TempoChange } from "../model";
import { Ticks } from "../model";

const f = Math.fround;

export class TempoMap {
  private readonly changes: readonly TempoChange[];

  constructor(tempos: readonly TempoChange[]) {
    const sorted = [...tempos].sort((a, b) => a.startTick - b.startTick);
    this.changes = sorted.length > 0 ? sorted : [{ startTick: 0, bpm: 100 }];
  }

  private segmentAt(tick: number): number {
    for (let i = this.changes.length - 1; i >= 0; i--) if (this.changes[i]!.startTick <= tick) return i;
    return 0;
  }

  /** Tempo (negras por minuto) en ese instante. */
  bpmAt(tick: number): number {
    return f(this.changes[this.segmentAt(tick)]!.bpm);
  }

  /** Cuántos ticks pasan por segundo en ese instante. */
  ticksPerSecond(tick: number): number {
    return f((this.changes[this.segmentAt(tick)]!.bpm * Ticks.QUARTER) / 60);
  }

  /** Posición a la que se llega [seconds] segundos después de [tick], cruzando los cambios de tempo que haya. */
  advance(tick: number, seconds: number): number {
    let position = f(tick);
    let left = f(seconds);
    while (left > 0) {
      const i = this.segmentAt(position);
      const perSecond = this.ticksPerSecond(position);
      const next = this.changes[i + 1];
      if (next === undefined) return f(position + f(left * perSecond));
      const segmentEnd = f(next.startTick);
      const secondsToEnd = f(f(segmentEnd - position) / perSecond);
      if (left < secondsToEnd) return f(position + f(left * perSecond));
      left = f(left - secondsToEnd);
      position = segmentEnd;
    }
    return position;
  }

  /** Segundos que pasan entre dos posiciones. */
  secondsBetween(from: number, to: number): number {
    let position = f(from);
    let total = 0;
    while (position < to) {
      const next = this.changes[this.segmentAt(position) + 1];
      const end = f(Math.min(to, next !== undefined ? next.startTick : to));
      total = f(total + f(f(end - position) / this.ticksPerSecond(position)));
      position = end;
    }
    return total;
  }
}
