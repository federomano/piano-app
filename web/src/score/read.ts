/*
 * Lectura de una partitura desde un archivo (PIA-88): bytes o texto → `Score`, o la lista de avisos en español.
 *
 * Es estricto, como la app de Android: si el archivo no cumple el contrato (clave de más, compás incompleto, nota fuera
 * del piano...), no se abre y se devuelve qué corregir. Los avisos son los mismos que los de `tools/validar_partitura.py`.
 */
import { validateContract } from "./contract";
import { Float, parseJson } from "./json";
import {
  FORMAT_VERSION,
  parsePitch,
  parseTuplet,
  type Clef,
  type DynamicLevel,
  type Hairpin,
  type Hand,
  type Note,
  type OttavaKind,
  type Rest,
  type Score,
} from "./model";

type Obj = Map<string, unknown>;

export type ReadResult =
  | { readonly ok: true; readonly score: Score }
  | { readonly ok: false; readonly errors: readonly string[] };

/**
 * Revisa el texto de un archivo contra el contrato. Devuelve los problemas (vacía = cumple), igual que
 * `validate_file` del validador de Python.
 */
export function validateText(text: string): string[] {
  const parsed = parseJson(text);
  if (!parsed.ok) {
    return [
      `No es un JSON válido (línea ${parsed.line}, columna ${parsed.column}): ${parsed.message}. ` +
        "Sin comentarios ni comas de más al final de una lista.",
    ];
  }
  return validateContract(parsed.value);
}

/** Igual que `validateText`, pero desde los bytes del archivo (UTF-8, con o sin marca de orden al principio). */
export function validateBytes(bytes: Uint8Array): string[] {
  let text: string;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    return ["El archivo no está en UTF-8."];
  }
  return validateText(text);
}

/** Lee una partitura desde el texto de un archivo `.json`. */
export function readScoreText(text: string): ReadResult {
  const parsed = parseJson(text);
  if (!parsed.ok) return { ok: false, errors: validateText(text) };
  let errors: string[];
  try {
    errors = validateContract(parsed.value);
  } catch {
    return { ok: false, errors: ["El archivo tiene una estructura que no se pudo revisar. Probá con otro archivo."] };
  }
  const root = parsed.value as Obj;
  if (errors.length === 0 && root.has("composer") && root.get("composer") !== null && typeof root.get("composer") !== "string") {
    errors = ['"composer" tiene que ser texto.'];
  }
  if (errors.length > 0) return { ok: false, errors };
  return { ok: true, score: toScore(root) };
}

/** Lee una partitura desde los bytes de un archivo `.json`. */
export function readScoreBytes(bytes: Uint8Array): ReadResult {
  let text: string;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    return { ok: false, errors: ["El archivo no está en UTF-8."] };
  }
  return readScoreText(text);
}

// --- Conversión (los datos ya cumplen el contrato) --------------------------------------------------------------------

function num(v: unknown): number {
  return v instanceof Float ? v.value : (v as number);
}

function list(root: Obj, key: string): Obj[] {
  const value = root.get(key);
  return Array.isArray(value) ? (value as Obj[]) : [];
}

function text(v: unknown): string | null {
  return typeof v === "string" ? v : null;
}

function toScore(root: Obj): Score {
  const timeSignatures = list(root, "timeSignatures").map((s) => ({
    startTick: num(s.get("startTick")),
    numerator: num(s.get("numerator")),
    denominator: num(s.get("denominator")),
  }));
  const keySignatures = list(root, "keySignatures").map((k) => ({
    startTick: num(k.get("startTick")),
    fifths: num(k.get("fifths")),
  }));
  const tempos = list(root, "tempos").map((t) => ({ startTick: num(t.get("startTick")), bpm: num(t.get("bpm")) }));

  const notes: Note[] = list(root, "notes").map((n) => ({
    hand: n.get("hand") as Hand,
    pitch: parsePitch(n.get("pitch"))!,
    startTick: num(n.get("startTick")),
    durationTicks: num(n.get("durationTicks")),
    tiedToNext: n.get("tiedToNext") === true,
    finger: n.get("finger") == null ? null : num(n.get("finger")),
    tuplet: n.get("tuplet") == null ? null : parseTuplet(n.get("tuplet")),
    voice: n.has("voice") ? num(n.get("voice")) : 1,
    grace: n.get("grace") === true,
    staccato: n.get("staccato") === true,
    accent: n.get("accent") === true,
  }));
  const rests: Rest[] = list(root, "rests").map((r) => ({
    hand: r.get("hand") as Hand,
    startTick: num(r.get("startTick")),
    durationTicks: num(r.get("durationTicks")),
    tuplet: r.get("tuplet") == null ? null : parseTuplet(r.get("tuplet")),
    voice: r.has("voice") ? num(r.get("voice")) : 1,
  }));

  return {
    formatVersion: FORMAT_VERSION,
    title: root.get("title") as string,
    composer: text(root.get("composer")),
    level: text(root.get("level")),
    goal: text(root.get("goal")),
    timeSignatures: timeSignatures.length > 0 ? timeSignatures : [{ startTick: 0, numerator: 4, denominator: 4 }],
    keySignatures: keySignatures.length > 0 ? keySignatures : [{ startTick: 0, fifths: 0 }],
    tempos: tempos.length > 0 ? tempos : [{ startTick: 0, bpm: 100 }],
    pickupTicks: root.has("pickupTicks") ? num(root.get("pickupTicks")) : 0,
    dynamics: list(root, "dynamics").map((d) => ({
      startTick: num(d.get("startTick")),
      level: d.get("level") as DynamicLevel,
    })),
    hairpins: list(root, "hairpins").map(
      (h): Hairpin => ({
        startTick: num(h.get("startTick")),
        endTick: num(h.get("endTick")),
        kind: h.get("kind") as Hairpin["kind"],
      }),
    ),
    ottavas: list(root, "ottavas").map((o) => ({
      hand: o.get("hand") as Hand,
      startTick: num(o.get("startTick")),
      endTick: num(o.get("endTick")),
      kind: o.get("kind") as OttavaKind,
    })),
    pedals: list(root, "pedals").map((p) => ({ startTick: num(p.get("startTick")), endTick: num(p.get("endTick")) })),
    clefs: list(root, "clefs").map((c) => ({
      hand: c.get("hand") as Hand,
      startTick: num(c.get("startTick")),
      clef: c.get("clef") as Clef,
    })),
    notes,
    rests,
  };
}
