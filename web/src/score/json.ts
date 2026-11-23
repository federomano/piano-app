/*
 * Lector de JSON estricto (PIA-88).
 *
 * Hace lo mismo que `json.loads` de Python (3.13) para que los avisos de un archivo roto sean los mismos que los del
 * validador `tools/validar_partitura.py`: misma frase, misma línea y misma columna. Dos diferencias a propósito con
 * `JSON.parse` de JavaScript:
 *  - Los números escritos con punto o exponente (`480.0`, `1e3`) se distinguen de los enteros (`480`): son `Float`.
 *    Así `"startTick": 480.0` se rechaza, como hace la app de Android.
 *  - Los objetos son `Map`, que conservan el orden de las claves tal como están en el archivo.
 *
 * No acepta `NaN` ni `Infinity` (no son JSON).
 */

/** Un número escrito con punto decimal o exponente en el archivo. */
export class Float {
  constructor(
    readonly value: number,
    readonly text: string,
  ) {}
}

export type JsonObject = Map<string, unknown>;

export type JsonResult =
  | { readonly ok: true; readonly value: unknown }
  | { readonly ok: false; readonly message: string; readonly line: number; readonly column: number };

class JsonError extends Error {
  constructor(
    readonly msg: string,
    readonly pos: number,
  ) {
    super(msg);
  }
}

const WHITESPACE = new Set([" ", "\t", "\n", "\r"]);
const NUMBER = /(-?(?:0|[1-9]\d*))(\.\d+)?([eE][-+]?\d+)?/y;
const ESCAPES: Readonly<Record<string, string>> = { '"': '"', "\\": "\\", "/": "/", b: "\b", f: "\f", n: "\n", r: "\r", t: "\t" };

function skipWhitespace(s: string, i: number): number {
  while (i < s.length && WHITESPACE.has(s[i]!)) i++;
  return i;
}

function parseString(s: string, begin: number): [string, number] {
  let out = "";
  let i = begin;
  for (;;) {
    if (i >= s.length) throw new JsonError("Unterminated string starting at", begin - 1);
    const c = s[i]!;
    if (c === '"') return [out, i + 1];
    if (c === "\\") {
      i++;
      if (i >= s.length) throw new JsonError("Unterminated string starting at", begin - 1);
      const e = s[i]!;
      if (e === "u") {
        const hex = s.slice(i + 1, i + 5);
        if (!/^[0-9a-fA-F]{4}$/.test(hex)) throw new JsonError("Invalid \\uXXXX escape", i);
        out += String.fromCharCode(parseInt(hex, 16));
        i += 5;
      } else if (e in ESCAPES) {
        out += ESCAPES[e];
        i++;
      } else {
        throw new JsonError("Invalid \\escape", i - 1);
      }
    } else if (c < " ") {
      throw new JsonError("Invalid control character at", i);
    } else {
      out += c;
      i++;
    }
  }
}

function parseObject(s: string, start: number): [JsonObject, number] {
  const obj: JsonObject = new Map();
  let end = start;
  let next = s[end] ?? "";
  if (next !== '"') {
    if (WHITESPACE.has(next)) {
      end = skipWhitespace(s, end);
      next = s[end] ?? "";
    }
    if (next === "}") return [obj, end + 1];
    if (next !== '"') throw new JsonError("Expecting property name enclosed in double quotes", end);
  }
  end += 1;
  for (;;) {
    const [key, afterKey] = parseString(s, end);
    end = afterKey;
    if (s[end] !== ":") {
      end = skipWhitespace(s, end);
      if (s[end] !== ":") throw new JsonError("Expecting ':' delimiter", end);
    }
    end += 1;
    end = skipWhitespace(s, end);
    const [value, afterValue] = scanOnce(s, end);
    obj.set(key, value);
    end = afterValue;
    next = s[end] ?? "";
    if (WHITESPACE.has(next)) {
      end = skipWhitespace(s, end + 1);
      next = s[end] ?? "";
    }
    end += 1;
    if (next === "}") break;
    if (next !== ",") throw new JsonError("Expecting ',' delimiter", end - 1);
    const commaIndex = end - 1;
    end = skipWhitespace(s, end);
    next = s[end] ?? "";
    end += 1;
    if (next !== '"') {
      if (next === "}") throw new JsonError("Illegal trailing comma before end of object", commaIndex);
      throw new JsonError("Expecting property name enclosed in double quotes", end - 1);
    }
  }
  return [obj, end];
}

function parseArray(s: string, start: number): [unknown[], number] {
  const values: unknown[] = [];
  let end = start;
  let next = s[end] ?? "";
  if (WHITESPACE.has(next)) {
    end = skipWhitespace(s, end + 1);
    next = s[end] ?? "";
  }
  if (next === "]") return [values, end + 1];
  for (;;) {
    const [value, afterValue] = scanOnce(s, end);
    values.push(value);
    end = afterValue;
    next = s[end] ?? "";
    if (WHITESPACE.has(next)) {
      end = skipWhitespace(s, end + 1);
      next = s[end] ?? "";
    }
    end += 1;
    if (next === "]") break;
    if (next !== ",") throw new JsonError("Expecting ',' delimiter", end - 1);
    const commaIndex = end - 1;
    end = skipWhitespace(s, end);
    if ((s[end] ?? "") === "]") throw new JsonError("Illegal trailing comma before end of array", commaIndex);
  }
  return [values, end];
}

function scanOnce(s: string, idx: number): [unknown, number] {
  if (idx >= s.length) throw new JsonError("Expecting value", idx);
  const c = s[idx]!;
  if (c === '"') return parseString(s, idx + 1);
  if (c === "{") return parseObject(s, idx + 1);
  if (c === "[") return parseArray(s, idx + 1);
  if (s.startsWith("null", idx)) return [null, idx + 4];
  if (s.startsWith("true", idx)) return [true, idx + 4];
  if (s.startsWith("false", idx)) return [false, idx + 5];
  NUMBER.lastIndex = idx;
  const match = NUMBER.exec(s);
  if (match) {
    const isFloat = match[2] !== undefined || match[3] !== undefined;
    const value = Number(match[0]);
    return [isFloat ? new Float(value, match[0]) : value + 0, idx + match[0].length];
  }
  throw new JsonError("Expecting value", idx);
}

/** Línea y columna (desde 1) de una posición del texto, contando letras como lo hace Python. */
function lineAndColumn(text: string, pos: number): { line: number; column: number } {
  const before = text.slice(0, pos);
  const lastNewline = before.lastIndexOf("\n");
  return {
    line: before.split("\n").length,
    column: Array.from(before.slice(lastNewline + 1)).length + 1,
  };
}

/** Lee un texto JSON. Si está roto, devuelve el motivo (en inglés, como Python) con su línea y columna. */
export function parseJson(text: string): JsonResult {
  try {
    const start = skipWhitespace(text, 0);
    const [value, afterValue] = scanOnce(text, start);
    const end = skipWhitespace(text, afterValue);
    if (end !== text.length) throw new JsonError("Extra data", end);
    return { ok: true, value };
  } catch (error) {
    if (error instanceof JsonError) {
      return { ok: false, message: error.msg, ...lineAndColumn(text, error.pos) };
    }
    if (error instanceof RangeError) {
      return { ok: false, message: "Demasiados niveles anidados", line: 1, column: 1 };
    }
    throw error;
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// Cómo escribe Python los valores en los avisos (`{valor!r}`): se usa para que los mensajes salgan idénticos.
// ---------------------------------------------------------------------------------------------------------------------

function pyFloat(x: number): string {
  const abs = Math.abs(x);
  if (Object.is(x, -0)) return "-0.0";
  if (abs >= 1e16 || (abs !== 0 && abs < 1e-4)) {
    return x.toExponential().replace(/e([+-])(\d)$/, "e$10$2");
  }
  return Number.isInteger(x) ? `${x}.0` : String(x);
}

function pyString(s: string): string {
  const quote = s.includes("'") && !s.includes('"') ? '"' : "'";
  let out = quote;
  for (const ch of s) {
    const code = ch.codePointAt(0)!;
    if (ch === "\\") out += "\\\\";
    else if (ch === quote) out += `\\${quote}`;
    else if (ch === "\n") out += "\\n";
    else if (ch === "\r") out += "\\r";
    else if (ch === "\t") out += "\\t";
    else if (code < 0x20 || (code >= 0x7f && code <= 0xa0)) out += `\\x${code.toString(16).padStart(2, "0")}`;
    else out += ch;
  }
  return out + quote;
}

/** El valor tal como lo mostraría `repr()` de Python: `None`, `True`, `'texto'`, `80.0`, `[1, 2]`, `{'a': 1}`. */
export function pyRepr(value: unknown): string {
  if (value === null || value === undefined) return "None";
  if (typeof value === "boolean") return value ? "True" : "False";
  if (typeof value === "number") return String(value);
  if (value instanceof Float) return pyFloat(value.value);
  if (typeof value === "string") return pyString(value);
  if (Array.isArray(value)) return `[${value.map(pyRepr).join(", ")}]`;
  if (value instanceof Map) {
    return `{${[...value].map(([k, v]) => `${pyString(k)}: ${pyRepr(v)}`).join(", ")}}`;
  }
  return String(value);
}
