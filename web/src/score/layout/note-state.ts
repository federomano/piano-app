/*
 * Estado de una nota para pintarla (PIA-89). Mismo código que NoteState.kt: el reproductor usa PENDING,
 * CURRENT y PASSED; el modo escucha va a usar HIT y MISS.
 */
import { HANDS } from "../model";
import type { ChordSpan } from "./note-layout";

export type NoteState = "pending" | "current" | "passed" | "hit" | "miss";

/** El acorde más largo (para saber hasta dónde mirar hacia atrás); conviene calcularlo una sola vez. */
export function longestOf(spans: readonly ChordSpan[]): number {
  let max = 0;
  for (const s of spans) max = Math.max(max, s.chord.durationTicks);
  return max;
}

/** Primer acorde que empieza después de [tick] (o el tamaño de la lista si no hay). */
function firstStartingAfter(spans: readonly ChordSpan[], tick: number): number {
  let lo = 0;
  let hi = spans.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (spans[mid]!.chord.startTick <= tick) lo = mid + 1;
    else hi = mid;
  }
  return lo;
}

/**
 * Qué acordes son "los de ahora" en [tick]: en cada mano, el que está sonando (con todas sus voces) o, si la mano
 * tiene un silencio, el próximo que viene. Devuelve los números de [spans] (ordenados por tiempo de inicio).
 */
export function current(spans: readonly ChordSpan[], tick: number, longest: number = longestOf(spans)): Set<number> {
  const result = new Set<number>();
  if (spans.length === 0) return result;
  const firstAfter = firstStartingAfter(spans, tick);
  for (const hand of HANDS) {
    let covering = false;
    // Hacia atrás, mientras un acorde todavía pueda estar sonando.
    let i = firstAfter - 1;
    while (i >= 0 && spans[i]!.chord.startTick > tick - longest) {
      const c = spans[i]!.chord;
      if (c.hand === hand && c.startTick <= tick && tick < c.endTick) {
        result.add(i);
        covering = true;
      }
      i--;
    }
    if (covering) continue;
    // Silencio en esa mano: el próximo acorde (y los que empiezan con él, de otras voces).
    let next = firstAfter;
    while (next < spans.length && spans[next]!.chord.hand !== hand) next++;
    if (next < spans.length) {
      const start = spans[next]!.chord.startTick;
      let j = next;
      while (j < spans.length && spans[j]!.chord.startTick === start) {
        if (spans[j]!.chord.hand === hand) result.add(j);
        j++;
      }
    }
  }
  return result;
}

/** Estado de reproducción del acorde número [index]: el de ahora, ya tocado (atenuado) o pendiente. */
export function playback(spans: readonly ChordSpan[], index: number, tick: number, now: ReadonlySet<number>): NoteState {
  if (now.has(index)) return "current";
  if (spans[index]!.chord.endTick <= tick) return "passed";
  return "pending";
}
