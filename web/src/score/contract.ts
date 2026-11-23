/*
 * Revisión de un archivo de partitura contra el contrato (docs/CONTRATO-ARCHIVOS.md) (PIA-88).
 *
 * Es el validador de Python (`tools/validar_partitura.py`) pasado a TypeScript, regla por regla y con los mismos
 * mensajes, en el mismo orden. Los tests lo comparan contra el validador real, así que si se cambia uno hay que
 * cambiar el otro. Devuelve la lista de problemas; vacía = el archivo cumple el contrato.
 */
import { Float, pyRepr } from "./json";
import {
  DYNAMIC_LEVELS,
  FORMAT_VERSION,
  HAIRPIN_KINDS,
  HANDS,
  HAND_LABEL,
  MAX_VOICES,
  PIANO_MAX_MIDI,
  PIANO_MIN_MIDI,
  STANDARD_DURATIONS,
  Ticks,
  TICKS_PER_QUARTER,
  parsePitch,
  parseTuplet,
  writtenTicks,
  type Hand,
  type Tuplet,
} from "./model";

type Obj = Map<string, unknown>;

const LEVELS = ["principiante", "intermedio", "avanzado"];

const SCORE_KEYS = [
  "formatVersion", "title", "composer", "level", "goal", "timeSignatures", "keySignatures", "tempos", "pickupTicks",
  "dynamics", "hairpins", "clefs", "ottavas", "pedals", "notes", "rests",
];
const NOTE_KEYS = ["hand", "pitch", "startTick", "durationTicks", "tiedToNext", "finger", "tuplet", "voice", "grace", "staccato", "accent"];
const REST_KEYS = ["hand", "startTick", "durationTicks", "tuplet", "voice"];
const TS_KEYS = ["startTick", "numerator", "denominator"];
const KS_KEYS = ["startTick", "fifths"];
const TEMPO_KEYS = ["startTick", "bpm"];
const DYNAMIC_KEYS = ["startTick", "level"];
const HAIRPIN_KEYS = ["startTick", "endTick", "kind"];
const CLEF_KEYS = ["hand", "startTick", "clef"];
const OTTAVA_KEYS = ["hand", "startTick", "endTick", "kind"];
const PEDAL_KEYS = ["startTick", "endTick"];

// --- Ayudas que reproducen cómo Python mira los valores ---------------------------------------------------------------

/** Entero de verdad (no booleano, no escrito con punto decimal). */
function isInt(v: unknown): v is number {
  return typeof v === "number" && Number.isInteger(v);
}

/** El valor como número, tal como lo compara Python: `True` vale 1 y `4.0` vale 4. Null si no es un número. */
function numeric(v: unknown): number | null {
  if (typeof v === "boolean") return v ? 1 : 0;
  if (typeof v === "number") return v;
  if (v instanceof Float) return v.value;
  return null;
}

/** `dict.get(clave)`: lo que no está, o es null, vale null. */
function get(o: Obj, key: string): unknown {
  return o.has(key) ? o.get(key) : null;
}

/** `dict.get(clave, valorPorDefecto)`: el valor por defecto solo si la clave no está (un null escrito en el archivo cuenta). */
function getOr(o: Obj, key: string, fallback: unknown): unknown {
  return o.has(key) ? o.get(key) : fallback;
}

function isHand(v: unknown): v is Hand {
  return v === "right" || v === "left";
}

function handLabel(v: unknown, fallback = "mano"): string {
  return isHand(v) ? HAND_LABEL[v] : fallback;
}

/** Claves desconocidas = error (la app rechaza el archivo). */
function checkFields(obj: Obj, allowed: readonly string[], where: string, errors: string[]): void {
  for (const key of obj.keys()) {
    if (!allowed.includes(key)) {
      errors.push(
        `${where}: la clave "${key}" no existe en el modelo (la app rechaza el archivo). ` +
          `Claves válidas: ${[...allowed].sort().join(", ")}.`,
      );
    }
  }
}

function loadList(data: Obj, key: string, keys: readonly string[], errors: string[]): Obj[] {
  const value = get(data, key);
  if (value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`"${key}" tiene que ser una lista.`);
    return [];
  }
  const items: Obj[] = [];
  value.forEach((item, index) => {
    const i = index + 1;
    if (!(item instanceof Map)) {
      errors.push(`${key} #${i}: tiene que ser un objeto con llaves { }.`);
      return;
    }
    checkFields(item, keys, `${key} #${i}`, errors);
    items.push(new Map(item)); // copia: no se toca lo que viene de afuera
  });
  return items;
}

function allowedList(): string {
  return [...STANDARD_DURATIONS.keys()].sort((a, b) => a - b).join(", ");
}

// --- Dinámicas, claves, octavas y pedal -----------------------------------------------------------------------------

function checkDynamics(dynamics: Obj[], hairpins: Obj[], end: number, errors: string[]): void {
  const marks: number[] = [];
  dynamics.forEach((d, index) => {
    const where = `dynamics #${index + 1}`;
    let ok = true;
    const start = get(d, "startTick");
    if (!isInt(start) || start < 0) {
      errors.push(`${where}: "startTick" tiene que ser un entero de 0 o más.`);
      ok = false;
    }
    const level = get(d, "level");
    if (!DYNAMIC_LEVELS.includes(level as never)) {
      errors.push(`${where}: "level" tiene que ser uno de ${DYNAMIC_LEVELS.join(", ")} (es ${pyRepr(level)}).`);
      ok = false;
    }
    if (ok) {
      marks.push(start as number);
      if ((start as number) >= end) errors.push(`${where}: cae después del final de la pieza (tick ${start}).`);
    }
  });
  if (marks.some((b, i) => i > 0 && b <= marks[i - 1]!)) {
    errors.push('Dinámicas: tienen que estar ordenadas por "startTick" y sin repetir posición.');
  }
  const bars: Array<[number, number]> = [];
  hairpins.forEach((h, index) => {
    const where = `hairpins #${index + 1}`;
    let ok = true;
    for (const key of ["startTick", "endTick"]) {
      const v = get(h, key);
      if (!isInt(v) || v < 0) {
        errors.push(`${where}: "${key}" tiene que ser un entero de 0 o más.`);
        ok = false;
      }
    }
    const kind = get(h, "kind");
    if (!HAIRPIN_KINDS.includes(kind as never)) {
      errors.push(`${where}: "kind" tiene que ser "crescendo" o "diminuendo" (es ${pyRepr(kind)}).`);
      ok = false;
    }
    const start = get(h, "startTick") as number;
    const stop = get(h, "endTick") as number;
    if (ok && stop <= start) {
      errors.push(`${where}: "endTick" tiene que ser mayor que "startTick".`);
      ok = false;
    }
    if (ok) {
      bars.push([start, stop]);
      if (start >= end) errors.push(`${where}: empieza después del final de la pieza (tick ${start}).`);
    }
  });
  bars.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  for (let i = 1; i < bars.length; i++) {
    const [a0, a1] = bars[i - 1]!;
    const [b0] = bars[i]!;
    if (b0 < a1) errors.push(`El regulador que empieza en el tick ${b0} se pisa con el anterior (${a0}-${a1}).`);
  }
  for (const [start, stop] of bars) {
    for (const m of marks) {
      if (start < m && m < stop) {
        errors.push(
          `La dinámica del tick ${m} cae en el medio de un regulador (${start}-${stop}): ` +
            "una dinámica va al principio o al final del regulador.",
        );
      }
    }
  }
}

function checkClefs(clefs: Obj[], starts: Set<number>, end: number, errors: string[]): void {
  const last = new Map<string, number>();
  clefs.forEach((c, index) => {
    const where = `clefs #${index + 1}`;
    const hand = get(c, "hand");
    const clef = get(c, "clef");
    const t = get(c, "startTick");
    let ok = true;
    if (!isHand(hand)) {
      errors.push(`${where}: "hand" tiene que ser "right" o "left" (es ${pyRepr(hand)}).`);
      ok = false;
    }
    if (clef !== "treble" && clef !== "bass") {
      errors.push(`${where}: "clef" tiene que ser "treble" (sol) o "bass" (fa) (es ${pyRepr(clef)}).`);
    }
    if (!isInt(t) || t < 0) {
      errors.push(`${where}: "startTick" tiene que ser un entero de 0 o más.`);
      return;
    }
    if (t < end && !starts.has(t)) {
      errors.push(`El cambio de clave de la ${handLabel(hand)} del tick ${t} no cae al inicio de un compás.`);
    }
    if (ok) {
      const h = hand as Hand;
      const previous = last.get(h);
      if (previous !== undefined && t <= previous) {
        errors.push(`Claves de la ${HAND_LABEL[h]}: tienen que estar ordenadas y sin repetir posición.`);
      }
      last.set(h, t);
    }
  });
}

interface NoteRecord {
  hand: Hand;
  pitch: string;
  midi: number;
  startTick: number;
  durationTicks: number;
  tied: boolean;
  voice: number;
  tuplet: Tuplet | null;
  staccato: boolean;
  accent: boolean;
  finger: unknown;
}

interface RestRecord {
  hand: Hand;
  startTick: number;
  durationTicks: number;
  voice: number;
  tuplet: Tuplet | null;
}

function checkOttavas(ottavas: Obj[], notes: NoteRecord[], errors: string[]): void {
  const good: Array<{ hand: Hand; startTick: number; endTick: number; kind: string }> = [];
  ottavas.forEach((o, index) => {
    const where = `ottavas #${index + 1}`;
    let ok = true;
    const hand = get(o, "hand");
    const kind = get(o, "kind");
    if (!isHand(hand)) {
      errors.push(`${where}: "hand" tiene que ser "right" o "left" (es ${pyRepr(hand)}).`);
      ok = false;
    }
    if (kind !== "8va" && kind !== "8vb") {
      errors.push(`${where}: "kind" tiene que ser "8va" o "8vb" (es ${pyRepr(kind)}).`);
      ok = false;
    }
    const a = get(o, "startTick");
    const b = get(o, "endTick");
    if (!isInt(a) || !isInt(b) || a < 0 || b <= a) {
      errors.push(`${where}: "startTick" y "endTick" tienen que ser enteros, con endTick mayor que startTick.`);
      ok = false;
    }
    if (ok) good.push({ hand: hand as Hand, startTick: a as number, endTick: b as number, kind: kind as string });
  });
  for (const hand of HANDS) {
    const own = good.filter((o) => o.hand === hand).sort((a, b) => a.startTick - b.startTick);
    if (own.some((b, i) => i > 0 && b.startTick < own[i - 1]!.endTick)) {
      errors.push(`Signos de octava de la ${HAND_LABEL[hand]}: no pueden superponerse.`);
    }
    for (const o of own) {
      if (!notes.some((n) => n.hand === hand && o.startTick <= n.startTick && n.startTick < o.endTick)) {
        errors.push(
          `El signo de octava de la ${HAND_LABEL[hand]} (ticks ${o.startTick} a ${o.endTick}) no tiene ninguna nota de esa mano adentro.`,
        );
      }
    }
  }
  const shift = (hand: Hand, tick: number): string | null => {
    for (const o of good) if (o.hand === hand && o.startTick <= tick && tick < o.endTick) return o.kind;
    return null;
  };
  for (const n of notes) {
    if (!n.tied) continue;
    const next = notes.find((m) => m.hand === n.hand && m.midi === n.midi && m.startTick === n.startTick + n.durationTicks);
    if (next !== undefined && shift(n.hand, n.startTick) !== shift(n.hand, next.startTick)) {
      errors.push(
        `La nota ${n.pitch} (${HAND_LABEL[n.hand]}, tick ${n.startTick}) está ligada a otra que cae fuera de su signo de octava: ` +
          "el signo tiene que cubrir las dos.",
      );
    }
  }
}

function checkPedals(pedals: Obj[], end: number, errors: string[]): void {
  const good: Array<{ startTick: number; endTick: number }> = [];
  pedals.forEach((p, index) => {
    const a = get(p, "startTick");
    const b = get(p, "endTick");
    if (!isInt(a) || !isInt(b) || a < 0 || b <= a) {
      errors.push(`pedals #${index + 1}: "startTick" y "endTick" tienen que ser enteros, con endTick mayor que startTick.`);
      return;
    }
    good.push({ startTick: a, endTick: b });
  });
  if (good.some((b, i) => i > 0 && b.startTick < good[i - 1]!.endTick)) {
    errors.push(
      "Pedal: tiene que estar ordenado y sin superponerse (uno puede empezar justo donde termina el anterior, que es un cambio de pedal).",
    );
  }
  for (const p of good) {
    if (p.endTick > end) {
      errors.push(`El pedal que se suelta en el tick ${p.endTick} termina después del final de la partitura (tick ${end}).`);
    }
  }
}

// --- Revisión completa -----------------------------------------------------------------------------------------------

/** Compás calculado: [número, inicio, fin]. */
type Measure = readonly [number, number, number];

/** Revisa los datos ya leídos (ver `parseJson`). Devuelve la lista de problemas; vacía = cumple el contrato. */
export function validateContract(data: unknown): string[] {
  const errors: string[] = [];
  if (!(data instanceof Map)) return ["El archivo tiene que ser un objeto JSON con llaves { }."];
  checkFields(data, SCORE_KEYS, "Partitura", errors);

  // --- Datos generales ---
  if (data.has("formatVersion") && numeric(data.get("formatVersion")) !== FORMAT_VERSION) {
    errors.push(`formatVersion tiene que ser ${FORMAT_VERSION} (es ${pyRepr(data.get("formatVersion"))}).`);
  }
  const title = get(data, "title");
  if (typeof title !== "string" || title.trim() === "") {
    errors.push('Falta el título ("title"): obligatorio, corto y en español.');
  }
  const level = get(data, "level");
  if (level === null) {
    errors.push('Falta "level": "principiante", "intermedio" o "avanzado".');
  } else if (!LEVELS.includes(level as string)) {
    errors.push(`"level" tiene que ser "principiante", "intermedio" o "avanzado" (es ${pyRepr(level)}).`);
  }
  const goal = get(data, "goal");
  if (typeof goal !== "string" || goal.trim() === "") {
    errors.push('Falta "goal": una frase de qué se practica con la pieza.');
  }

  // --- Firmas de compás, armaduras y tempos ---
  let sigs = loadList(data, "timeSignatures", TS_KEYS, errors);
  let keys = loadList(data, "keySignatures", KS_KEYS, errors);
  let tempos = loadList(data, "tempos", TEMPO_KEYS, errors);
  const dynamics = loadList(data, "dynamics", DYNAMIC_KEYS, errors);
  const hairpins = loadList(data, "hairpins", HAIRPIN_KEYS, errors);
  const clefs = loadList(data, "clefs", CLEF_KEYS, errors);
  const ottavas = loadList(data, "ottavas", OTTAVA_KEYS, errors);
  const pedals = loadList(data, "pedals", PEDAL_KEYS, errors);
  if (sigs.length === 0) sigs = [new Map<string, unknown>([["startTick", 0], ["numerator", 4], ["denominator", 4]])];
  if (keys.length === 0) keys = [new Map<string, unknown>([["startTick", 0], ["fifths", 0]])];
  if (tempos.length === 0) tempos = [new Map<string, unknown>([["startTick", 0], ["bpm", 100]])];

  const checkStarts = (name: string, items: Obj[]): boolean => {
    const starts = items.map((it) => get(it, "startTick"));
    if (starts.some((s) => !isInt(s) || s < 0)) {
      errors.push(`${name}: cada "startTick" tiene que ser un entero de 0 o más.`);
      return false;
    }
    const ticks = starts as number[];
    if (ticks[0] !== 0) errors.push(`${name}: el primero tiene que estar en el tick 0.`);
    if (ticks.some((b, i) => i > 0 && b <= ticks[i - 1]!)) {
      errors.push(`${name}: tienen que estar ordenados y sin repetir posición.`);
    }
    return true;
  };

  let sigsOk = checkStarts("Firmas de compás", sigs);
  let keysOk = checkStarts("Armaduras", keys);
  let temposOk = checkStarts("Tempos", tempos);

  for (const s of sigs) {
    const n = get(s, "numerator");
    const d = get(s, "denominator");
    if (!isInt(n) || n < 1) {
      errors.push(`Firma de compás: "numerator" tiene que ser un entero de 1 o más (es ${pyRepr(n)}).`);
      sigsOk = false;
    }
    if (![1, 2, 4, 8, 16, 32].includes(numeric(d) as number)) {
      errors.push(`Firma de compás: "denominator" tiene que ser 1, 2, 4, 8, 16 o 32 (es ${pyRepr(d)}).`);
      sigsOk = false;
    }
  }
  for (const k of keys) {
    const f = get(k, "fifths");
    if (!isInt(f) || f < -7 || f > 7) {
      errors.push(`Armadura: "fifths" tiene que ser un entero de -7 a 7 (es ${pyRepr(f)}).`);
      keysOk = false;
    }
  }
  for (const t of tempos) {
    const b = get(t, "bpm");
    const value = typeof b === "boolean" ? null : numeric(b);
    if (value === null || value <= 0) {
      errors.push(
        `Tempo: "bpm" tiene que ser un número mayor que 0 (es ${pyRepr(b)}). ` +
          'Va en negras por minuto (un 6/8 "negra con puntillo = 60" se escribe 90).',
      );
      temposOk = false;
    } else if (value < 20 || value > 300) {
      errors.push(`Tempo: ${pyRepr(b)} bpm es muy raro. ¿Está en negras por minuto? (regla 9)`);
    }
  }

  // --- Notas y silencios ---
  const rawNotes = loadList(data, "notes", NOTE_KEYS, errors);
  const rawRests = loadList(data, "rests", REST_KEYS, errors);
  const notes: NoteRecord[] = [];
  const rests: RestRecord[] = [];

  /** Revisa lo que tienen en común notas y silencios. Devuelve la voz y el grupo irregular, o null si hay un error. */
  const basic = (item: Obj, where: string, grace: boolean): { voice: number; tuplet: Tuplet | null } | null => {
    let ok = true;
    if (!isHand(get(item, "hand"))) {
      errors.push(`${where}: "hand" tiene que ser "right" o "left" (es ${pyRepr(get(item, "hand"))}).`);
      ok = false;
    }
    for (const key of ["startTick", "durationTicks"]) {
      if (!isInt(get(item, key))) {
        errors.push(`${where}: "${key}" tiene que ser un entero.`);
        ok = false;
      }
    }
    if (!ok) return null;
    const start = get(item, "startTick") as number;
    const d = get(item, "durationTicks") as number;
    if (start < 0) {
      errors.push(`${where}: el inicio no puede ser negativo.`);
      return null;
    }
    if (d <= 0) {
      errors.push(`${where}: la duración tiene que ser mayor que 0.`);
      return null;
    }
    const voice = getOr(item, "voice", 1);
    if (!isInt(voice) || voice < 1 || voice > MAX_VOICES) {
      errors.push(`${where}: "voice" tiene que ser un entero de 1 a ${MAX_VOICES} (es ${pyRepr(voice)}).`);
      return null;
    }
    if (grace) {
      // Un adorno dura "un instante" (hasta una semicorchea) y suena antes de su tick; no es una figura del compás.
      if (get(item, "tuplet") !== null) {
        errors.push(`${where}: un adorno ("grace": true) no puede llevar "tuplet".`);
        return null;
      }
      if (d > Ticks.GRACE_MAX) {
        errors.push(`${where}: un adorno dura como mucho ${Ticks.GRACE_MAX} ticks (es ${d}); lo normal es 60.`);
        return null;
      }
      return { voice, tuplet: null };
    }
    let tuplet: Tuplet | null = null;
    if (get(item, "tuplet") !== null) {
      tuplet = parseTuplet(get(item, "tuplet"));
      if (tuplet === null) {
        errors.push(
          `${where}: "tuplet" tiene que escribirse como "3:2" (3 figuras en el tiempo de 2), no ${pyRepr(get(item, "tuplet"))}.`,
        );
        return null;
      }
    }
    if (tuplet === null) {
      if (!STANDARD_DURATIONS.has(d)) {
        errors.push(
          `${where}: la duración ${d} no es una figura que la app sepa dibujar. Si es un tresillo o ` +
            `similar, agregá "tuplet": "3:2" (3 en el tiempo de 2) y dejá la duración real en ticks. ` +
            `Duraciones comunes válidas: ${allowedList()}.`,
        );
      }
    } else {
      const w = writtenTicks(d, tuplet);
      if (w === null || !STANDARD_DURATIONS.has(w)) {
        errors.push(
          `${where}: con el grupo ${get(item, "tuplet")} no puede durar ${d} ticks: la figura escrita ` +
            "tendría que ser redonda, blanca, negra, corchea, semicorchea o fusa (con hasta dos puntillos).",
        );
      }
    }
    return { voice, tuplet };
  };

  const graces: Array<{ hand: Hand; pitch: string; midi: number; startTick: number }> = [];
  rawNotes.forEach((n, index) => {
    const i = index + 1;
    let where = `Nota #${i}`;
    let grace = false;
    const rawGrace = getOr(n, "grace", false);
    if (typeof rawGrace !== "boolean") {
      errors.push(`${where}: "grace" tiene que ser true o false.`);
    } else {
      grace = rawGrace;
    }
    const pitch = parsePitch(get(n, "pitch"));
    if (pitch === null) {
      errors.push(
        `${where}: altura inválida ${pyRepr(get(n, "pitch"))}. Se escribe como C4, F#4, Bb3 ` +
          "(letra A-G, alteración opcional y octava).",
      );
      basic(n, where, grace);
      return;
    }
    const pitchText = get(n, "pitch") as string;
    where = `Nota ${pitchText} #${i}`;
    const common = basic(n, where, grace);
    if (common === null) return;
    if (pitch.midi < PIANO_MIN_MIDI || pitch.midi > PIANO_MAX_MIDI) {
      errors.push(`${where}: fuera del rango del piano (de A0 a C8). Revisá la octava: el Do central es C4.`);
    }
    let tied = getOr(n, "tiedToNext", false);
    if (typeof tied !== "boolean") {
      errors.push(`${where}: "tiedToNext" tiene que ser true o false.`);
      tied = false;
    }
    const finger = get(n, "finger");
    if (finger !== null && (!isInt(finger) || finger < 1 || finger > 5)) {
      errors.push(`${where}: "finger" tiene que ser un entero de 1 (pulgar) a 5 (meñique).`);
    }
    const flags: Record<string, boolean> = {};
    for (const flag of ["staccato", "accent"]) {
      let value = getOr(n, flag, false);
      if (typeof value !== "boolean") {
        errors.push(`${where}: "${flag}" tiene que ser true o false.`);
        value = false;
      }
      flags[flag] = value as boolean;
      if (value && grace) errors.push(`${where}: un adorno ("grace": true) no puede llevar "${flag}".`);
    }
    if (flags.staccato && tied) {
      errors.push(
        `${where}: una nota ligada a la siguiente ("tiedToNext") no puede llevar "staccato": tiene que sonar hasta la otra.`,
      );
    }
    const hand = get(n, "hand") as Hand;
    const startTick = get(n, "startTick") as number;
    if (grace) {
      if (tied) errors.push(`${where}: un adorno ("grace": true) no puede estar ligado.`);
      graces.push({ hand, pitch: pitchText, midi: pitch.midi, startTick });
      return;
    }
    notes.push({
      hand,
      pitch: pitchText,
      midi: pitch.midi,
      startTick,
      durationTicks: get(n, "durationTicks") as number,
      tied: tied as boolean,
      voice: common.voice,
      tuplet: common.tuplet,
      staccato: flags.staccato!,
      accent: flags.accent!,
      finger,
    });
  });
  rawRests.forEach((r, index) => {
    const common = basic(r, `Silencio #${index + 1}`, false);
    if (common !== null) {
      rests.push({
        hand: get(r, "hand") as Hand,
        startTick: get(r, "startTick") as number,
        durationTicks: get(r, "durationTicks") as number,
        voice: common.voice,
        tuplet: common.tuplet,
      });
    }
  });

  if (notes.length === 0) errors.push("La partitura no tiene notas.");

  // Adornos: cada uno lleva a una nota de la misma mano que empieza en su mismo tick; no llenan el compás.
  const seenGraces = new Set<string>();
  for (const g of graces) {
    if (!notes.some((n) => n.hand === g.hand && n.startTick === g.startTick)) {
      errors.push(
        `El adorno ${g.pitch} (${HAND_LABEL[g.hand]}, tick ${g.startTick}) no tiene una nota principal de la ` +
          "misma mano que empiece en ese tick: el adorno va en el mismo tick que la nota a la que lleva.",
      );
    }
    const key = `${g.hand}|${g.midi}|${g.startTick}`;
    if (seenGraces.has(key)) {
      errors.push(`Hay dos adornos ${g.pitch} iguales de la ${HAND_LABEL[g.hand]} en el tick ${g.startTick}.`);
    }
    seenGraces.add(key);
  }

  const fingered = notes.filter((n) => n.finger !== null);
  if (fingered.length > 0 && fingered.length !== notes.length) {
    errors.push(
      'Digitación a medias: "finger" está en algunas notas pero no en todas. Si se escribe, que sea para todas las notas.',
    );
  }

  if (!(sigsOk && keysOk && temposOk) || notes.length === 0) {
    return errors; // sin esto no se pueden calcular los compases
  }

  // --- Compases ---
  let end = 0;
  for (const n of notes) end = Math.max(end, n.startTick + n.durationTicks);
  for (const r of rests) end = Math.max(end, r.startTick + r.durationTicks);
  // Anacrusa: el primer compás es más corto y se numera 0; el primer compás entero es el 1.
  let pickup: unknown = getOr(data, "pickupTicks", 0);
  if (!isInt(pickup) || pickup < 0) {
    errors.push(`"pickupTicks" tiene que ser un entero de 0 o más (es ${pyRepr(pickup)}).`);
    pickup = 0;
  }
  let pickupTicks = pickup as number;
  const measureLength = (sig: Obj): number =>
    Math.floor(((get(sig, "numerator") as number) * 4 * TICKS_PER_QUARTER) / (numeric(get(sig, "denominator")) as number));
  const firstFull = measureLength(sigs[0]!);
  if (pickupTicks >= firstFull && firstFull > 0) {
    errors.push(`"pickupTicks" (${pickupTicks}) tiene que ser menor que un compás entero (${firstFull} ticks).`);
    pickupTicks = 0;
  }
  const measures: Measure[] = [];
  let tick = 0;
  let number = pickupTicks > 0 ? 0 : 1;
  while (tick < end) {
    const sig = sigs.filter((s) => (get(s, "startTick") as number) <= tick).at(-1) ?? sigs[0]!;
    let length = measureLength(sig);
    if (pickupTicks > 0 && tick === 0) length = pickupTicks;
    measures.push([number, tick, tick + length]);
    tick += length;
    number += 1;
  }
  const starts = new Set(measures.map((m) => m[1]));
  for (const [items, what] of [
    [sigs, "cambio de compás"],
    [keys, "cambio de armadura"],
  ] as const) {
    for (const it of items) {
      const t = get(it, "startTick") as number;
      if (t < end && !starts.has(t)) errors.push(`El ${what} del tick ${t} no cae al inicio de un compás.`);
    }
  }

  checkDynamics(dynamics, hairpins, end, errors);
  checkClefs(clefs, starts, end, errors);
  checkOttavas(ottavas, notes, errors);
  checkPedals(pedals, end, errors);

  const measureOf = (t: number): Measure => measures.find((m) => m[1] <= t && t < m[2]) ?? measures[measures.length - 1]!;
  const whereNote = (n: NoteRecord): string => `La nota ${n.pitch} (${HAND_LABEL[n.hand]}, compás ${measureOf(n.startTick)[0]})`;

  // --- Barras de compás ---
  for (const n of notes) {
    const m = measureOf(n.startTick);
    if (n.startTick + n.durationTicks > m[2]) {
      errors.push(`${whereNote(n)} cruza la barra de compás: partila en dos y unilas con "tiedToNext": true (regla 4).`);
    }
  }
  for (const r of rests) {
    const m = measureOf(r.startTick);
    if (r.startTick + r.durationTicks > m[2]) {
      errors.push(`Un silencio de la ${HAND_LABEL[r.hand]} en el compás ${m[0]} cruza la barra de compás: partilo en dos.`);
    }
  }

  // --- Ligaduras ---
  for (const n of notes) {
    if (n.tied) {
      const endTick = n.startTick + n.durationTicks;
      if (!notes.some((o) => o.hand === n.hand && o.midi === n.midi && o.startTick === endTick)) {
        errors.push(
          `${whereNote(n)} está ligada, pero no hay una nota igual (misma mano y altura) que empiece justo donde termina.`,
        );
      }
    }
  }

  // --- La misma tecla dos veces a la vez ---
  const byKey = new Map<string, NoteRecord[]>();
  for (const n of notes) {
    const key = `${n.hand}|${n.midi}`;
    const group = byKey.get(key);
    if (group) group.push(n);
    else byKey.set(key, [n]);
  }
  for (const group of byKey.values()) {
    group.sort((a, b) => a.startTick - b.startTick);
    for (let i = 1; i < group.length; i++) {
      const a = group[i - 1]!;
      const b = group[i]!;
      if (b.startTick < a.startTick + a.durationTicks) {
        errors.push(
          `Dos notas ${a.pitch} de la ${HAND_LABEL[a.hand]} se pisan en el compás ` +
            `${measureOf(b.startTick)[0]}: la misma tecla no puede sonar dos veces a la vez.`,
        );
      }
    }
  }

  // --- Grupos irregulares: acordes coherentes y grupos completos ---
  const tupletKey = (t: Tuplet | null): string => (t === null ? "-" : `${t.actual}:${t.normal}`);
  const chordTuplets = new Map<string, { hand: Hand; start: number; group: Set<string> }>();
  for (const n of notes) {
    const key = `${n.hand}|${n.voice}|${n.startTick}|${n.durationTicks}`;
    const entry = chordTuplets.get(key);
    if (entry) entry.group.add(tupletKey(n.tuplet));
    else chordTuplets.set(key, { hand: n.hand, start: n.startTick, group: new Set([tupletKey(n.tuplet)]) });
  }
  for (const { hand, start, group } of chordTuplets.values()) {
    if (group.size > 1) {
      errors.push(
        `Las notas del acorde de la ${HAND_LABEL[hand]} del compás ${measureOf(start)[0]} ` +
          'tienen que tener el mismo grupo irregular ("tuplet").',
      );
    }
  }
  const chordMarks = new Map<string, { hand: Hand; start: number; group: Set<string> }>();
  for (const n of notes) {
    const key = `${n.hand}|${n.voice}|${n.startTick}|${n.durationTicks}`;
    const mark = `${n.staccato}|${n.accent}`;
    const entry = chordMarks.get(key);
    if (entry) entry.group.add(mark);
    else chordMarks.set(key, { hand: n.hand, start: n.startTick, group: new Set([mark]) });
  }
  for (const { hand, start, group } of chordMarks.values()) {
    if (group.size > 1) {
      errors.push(
        `Las notas del acorde de la ${HAND_LABEL[hand]} del compás ${measureOf(start)[0]} ` +
          'tienen que llevar todas el mismo "staccato" y el mismo "accent".',
      );
    }
  }

  const lineMap = new Map<string, [Hand, number]>();
  for (const n of notes) lineMap.set(`${n.hand}|${n.voice}`, [n.hand, n.voice]);
  for (const r of rests) lineMap.set(`${r.hand}|${r.voice}`, [r.hand, r.voice]);
  for (const h of HANDS) lineMap.set(`${h}|1`, [h, 1]);
  const lines = [...lineMap.values()].sort((a, b) => HANDS.indexOf(a[0]) - HANDS.indexOf(b[0]) || a[1] - b[1]);

  const lineName = (hand: Hand, voice: number): string => HAND_LABEL[hand] + (voice > 1 ? ` (voz ${voice})` : "");

  for (const [hand, voice] of lines) {
    const figs = new Map<string, { start: number; dur: number; tuplet: Tuplet | null }>();
    for (const n of notes) {
      if (n.hand === hand && n.voice === voice) {
        figs.set(`${n.startTick},${n.durationTicks}`, { start: n.startTick, dur: n.durationTicks, tuplet: n.tuplet });
      }
    }
    for (const r of rests) {
      if (r.hand === hand && r.voice === voice) {
        const key = `${r.startTick},${r.durationTicks}`;
        if (!figs.has(key)) figs.set(key, { start: r.startTick, dur: r.durationTicks, tuplet: r.tuplet });
      }
    }
    let run: Array<{ start: number; dur: number; tuplet: Tuplet | null }> = [];
    const closeRun = (): void => {
      const tuplet = run[0]?.tuplet ?? null;
      if (run.length > 0 && tuplet !== null) {
        const smallest = Math.min(...run.map((f) => writtenTicks(f.dur, tuplet) || f.dur));
        const total = run.reduce((sum, f) => sum + f.dur, 0);
        if (total % (tuplet.normal * smallest) !== 0) {
          errors.push(
            `Un grupo irregular ${tuplet.actual}:${tuplet.normal} de la ${lineName(hand, voice)} en el compás ` +
              `${measureOf(run[0]!.start)[0]} está incompleto: las figuras suman ${total} ticks y tendrían ` +
              `que formar grupos enteros (de ${tuplet.normal * smallest} ticks).`,
          );
        }
      }
      run = [];
    };
    const sorted = [...figs.values()].sort((a, b) => a.start - b.start || a.dur - b.dur);
    for (const fig of sorted) {
      const last = run[run.length - 1];
      const continues =
        last !== undefined &&
        last.tuplet !== null &&
        tupletKey(last.tuplet) === tupletKey(fig.tuplet) &&
        last.start + last.dur === fig.start &&
        !starts.has(fig.start);
      if (!continues) closeRun();
      run.push(fig);
    }
    closeRun();
  }

  // --- Cada voz: una figura por vez, llenando cada compás ---
  // La voz 1 llena todos los compases. Las otras voces llenan los compases en los que aparecen.
  for (const [hand, voice] of lines) {
    const who = lineName(hand, voice);
    const figures = new Map<string, { start: number; dur: number; kind: string }>();
    const badKeys = new Set<string>();
    for (const n of notes) {
      if (n.hand === hand && n.voice === voice) {
        figures.set(`${n.startTick},${n.durationTicks}`, { start: n.startTick, dur: n.durationTicks, kind: "nota" });
      }
    }
    for (const r of rests) {
      if (!(r.hand === hand && r.voice === voice)) continue;
      const key = `${r.startTick},${r.durationTicks}`;
      const m = measureOf(r.startTick)[0];
      const existing = figures.get(key);
      if (existing?.kind === "nota") {
        errors.push(`Hay un silencio de la ${who} en el compás ${m} que coincide con una nota: quitá el silencio.`);
        badKeys.add(key);
      } else if (existing !== undefined) {
        errors.push(`Silencio repetido en la ${who}, compás ${m}.`);
        badKeys.add(key);
      }
      if (existing === undefined) figures.set(key, { start: r.startTick, dur: r.durationTicks, kind: "silencio" });
    }
    const ordered = [...figures.values()].sort((a, b) => a.start - b.start || a.dur - b.dur);
    for (const [num, ms, me] of measures) {
      const inside = ordered.filter((f) => ms <= f.start && f.start < me);
      if (voice > 1 && inside.length === 0) continue;
      if (inside.some((f) => badKeys.has(`${f.start},${f.dur}`))) continue;
      let pos: number | null = ms;
      for (const { start, dur } of inside) {
        if (start < pos) {
          errors.push(
            `En la ${who} hay figuras superpuestas en el compás ${num}. Dentro de una voz, cada nota o ` +
              "silencio tiene que empezar justo cuando termina el anterior, y las notas de un acorde " +
              "tienen que durar lo mismo. Si tienen que sonar a la vez, van en voces distintas " +
              `("voice": ${voice + 1}) (regla 5).`,
          );
          pos = null;
          break;
        }
        if (start > pos) {
          errors.push(
            `La ${who} no tiene nada entre el tick ${pos} y el ${start} (compás ${num}): ` +
              `falta un silencio de ${start - pos} ticks (regla 3).`,
          );
          pos = null;
          break;
        }
        pos = start + dur;
      }
      if (pos !== null && pos < me) {
        if (pos === ms) {
          errors.push(`La ${who} no tiene nada en el compás ${num}: falta un silencio de ${me - ms} ticks (regla 3).`);
        } else {
          errors.push(
            `En el compás ${num} la ${who} termina en el tick ${pos} y tendría que llegar al ${me}: ` +
              `falta un silencio de ${me - pos} ticks (regla 3).`,
          );
        }
      }
    }
  }
  const last = measures[measures.length - 1]!;
  if (end !== last[2]) {
    errors.push(
      `El último compás (el ${last[0]}) está incompleto: termina en el tick ${end} y tendría ` +
        `que terminar en el ${last[2]}. Completalo con silencios en las dos manos (regla 3).`,
    );
  }
  return errors;
}
