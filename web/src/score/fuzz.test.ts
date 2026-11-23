/*
 * Comparación a lo bruto contra el validador real de Python: se toman los ejemplos, se les rompen cosas al azar
 * (siempre las mismas, con una semilla fija) y se exige que los avisos de TypeScript sean idénticos, uno por uno y en
 * el mismo orden. Si alguna vez falla, el mensaje muestra el texto exacto que hay que mirar.
 */
import { describe, expect, it } from "vitest";
import { validateText } from "./read";
import { exampleFiles, pythonAvailable, readExample, validateWithPython } from "./test-support";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Any = any;

/** Generador de números al azar con semilla (mulberry32): da siempre la misma secuencia. */
function semilla(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const MALOS_VALORES: Any[] = [null, "x", "", true, false, 1.5, -1, 0, [], {}, [1], { a: 1 }, 99999, "C4", "3:2"];
const DURACIONES = [1, 30, 60, 90, 100, 120, 160, 180, 240, 320, 360, 480, 500, 720, 960, 1440, 1920, 3000];
const ALTURAS = ["C4", "H4", "C9", "F#4", "Bb3", "", "c4", "A0", "C8", "B#3", "Cbb2", "C##5", "A-1"];
const TUPLETS = ["3:2", "4:3", "2:2", "9:1", "x", "3:", "5:4", "1:2"];

function mutar(doc: Any, azar: () => number): void {
  const elegir = <T>(xs: readonly T[]): T => xs[Math.floor(azar() * xs.length)]!;
  const notas: Any[] = doc.notes ?? [];
  const silencios: Any[] = doc.rests ?? [];
  const cualquiera = (): Any => {
    const todos = [...notas, ...silencios];
    return todos.length ? elegir(todos) : {};
  };
  switch (Math.floor(azar() * 22)) {
    case 0:
      if (notas.length) notas.splice(Math.floor(azar() * notas.length), 1);
      break;
    case 1:
      cualquiera().durationTicks = elegir(DURACIONES);
      break;
    case 2:
      cualquiera().startTick = Math.max(-5, (cualquiera().startTick ?? 0) + elegir([-480, -1, 1, 60, 240, 480, 1920]));
      break;
    case 3: {
      const x = cualquiera();
      x.hand = x.hand === "right" ? "left" : "right";
      break;
    }
    case 4:
      if (notas.length) elegir(notas).pitch = elegir(ALTURAS);
      break;
    case 5:
      cualquiera()[elegir(["dedo", "x", "voices", "color"])] = elegir(MALOS_VALORES);
      break;
    case 6:
      delete doc[elegir(["title", "level", "goal", "notes", "rests", "tempos", "timeSignatures", "keySignatures", "formatVersion"])];
      break;
    case 7: {
      const x = cualquiera();
      const claves = Object.keys(x);
      if (claves.length) x[elegir(claves)] = elegir(MALOS_VALORES);
      break;
    }
    case 8:
      if (silencios.length) silencios.splice(Math.floor(azar() * silencios.length), 1);
      break;
    case 9:
      if (notas.length) notas.push({ ...elegir(notas) });
      break;
    case 10:
      cualquiera().voice = elegir([0, 1, 2, 3, 4, 5, "a", null, 1.5]);
      break;
    case 11: {
      const x = elegir(notas.length ? notas : [{}]);
      x.tiedToNext = !x.tiedToNext;
      break;
    }
    case 12:
      cualquiera().tuplet = elegir(TUPLETS);
      break;
    case 13: {
      const sig = (doc.timeSignatures ??= [{ startTick: 0, numerator: 4, denominator: 4 }]);
      if (sig[0]) {
        sig[0].numerator = elegir([1, 2, 3, 4, 6, 7, 0, "x", 4.5]);
        sig[0].denominator = elegir([1, 2, 4, 8, 16, 32, 3, 0, "x"]);
      }
      break;
    }
    case 14:
      doc.pickupTicks = elegir([-1, 0, 240, 480, 960, 1920, 2.5, "x", null]);
      break;
    case 15:
      (doc.dynamics ??= []).push({ startTick: elegir([0, 480, 960, 1920, 99999, -1]), level: elegir(["p", "mf", "ff", "forte", 3]) });
      break;
    case 16:
      (doc.hairpins ??= []).push({
        startTick: elegir([0, 480, 960]),
        endTick: elegir([480, 960, 1920, 0]),
        kind: elegir(["crescendo", "diminuendo", "x"]),
      });
      break;
    case 17:
      (doc.pedals ??= []).push({ startTick: elegir([0, 480, 960]), endTick: elegir([960, 1920, 99999, 0]) });
      break;
    case 18:
      (doc.ottavas ??= []).push({
        hand: elegir(["right", "left", "x"]),
        startTick: elegir([0, 480, 1920]),
        endTick: elegir([960, 1920, 3840, 0]),
        kind: elegir(["8va", "8vb", "15ma"]),
      });
      break;
    case 19:
      (doc.clefs ??= []).push({
        hand: elegir(["right", "left", "x"]),
        startTick: elegir([0, 480, 1920, 3840]),
        clef: elegir(["treble", "bass", "alto"]),
      });
      break;
    case 20:
      if (doc.tempos?.[0]) doc.tempos[0].bpm = elegir([0, -3, 10, 40, 120, 300, 301, "x", null, 90.5]);
      break;
    default:
      cualquiera()[elegir(["grace", "staccato", "accent"])] = elegir([true, false, "si", null]);
      cualquiera().finger = elegir([1, 3, 5, 0, 6, "x", null]);
  }
}

/** Rompe el texto mismo (no el contenido): lo corta o le mete un carácter de más. */
function romperTexto(texto: string, azar: () => number): string {
  const pos = Math.floor(azar() * texto.length);
  switch (Math.floor(azar() * 5)) {
    case 0:
      return texto.slice(0, pos);
    case 1:
      return texto.slice(0, pos) + texto.slice(pos + 1);
    case 2:
      return texto.slice(0, pos) + ["{", "}", "[", "]", ",", ":", '"', "\\", "x", "\n", "\t"][Math.floor(azar() * 11)] + texto.slice(pos);
    case 3:
      return texto.slice(0, pos) + "\n\n  " + texto.slice(pos);
    default:
      return texto + " basura";
  }
}

const SEMILLA = 20261123;
const MUTANTES_POR_EJEMPLO = 30;

function generarCasos(): Array<{ nombre: string; texto: string }> {
  const azar = semilla(SEMILLA);
  const casos: Array<{ nombre: string; texto: string }> = [];
  for (const archivo of exampleFiles()) {
    const original = JSON.parse(readExample(archivo)) as Any;
    for (let i = 0; i < MUTANTES_POR_EJEMPLO; i++) {
      const doc = structuredClone(original);
      const cambios = 1 + Math.floor(azar() * 3);
      for (let c = 0; c < cambios; c++) mutar(doc, azar);
      let texto = JSON.stringify(doc, null, azar() < 0.5 ? 2 : undefined);
      if (i % 10 === 9) texto = romperTexto(texto, azar);
      casos.push({ nombre: `${archivo} #${i}`, texto });
    }
  }
  return casos;
}

const BANCO_DE_JSON_ROTO = [
  "",
  "   ",
  "{",
  "[",
  '{"a":1,}',
  "[1,2,]",
  '{"a" 1}',
  '{"a":1 "b":2}',
  "[1 2]",
  '{"a":"x',
  '"a\\qb"',
  '{"a":"\\u12G4"}',
  "{'a':1}",
  "[1,2] x",
  '{"a":nul}',
  '"a\nb"',
  '{"a": [1,, 2]}',
  "// comentario\n{}",
  '{"a":01}',
  '{"a":1.}',
  '{"a":-}',
  "x",
  '{"title":"x"',
  '{"title":"x"}}',
  '{\n  "title": "x",\n  "notes": [\n    {"hand": "right",}\n  ]\n}',
  '{"a":"\\ud83d\\ude00 ñ"}',
  '{"a":1e5}',
  '{"startTick":480.0}',
  "true",
  "null",
  '"texto"',
  '{"a":1,"a":2}',
  "\t\n  [ ] ",
  '{"é": "ü", "bad": tru}',
];

describe.skipIf(!pythonAvailable)("igual que el validador de Python, con archivos rotos al azar", () => {
  const casos = generarCasos();

  it(`${casos.length} archivos mutados dan los mismos avisos`, () => {
    const python = validateWithPython(casos.map((c) => c.texto));
    let comparados = 0;
    let caidasDePython = 0;
    casos.forEach((caso, i) => {
      const esperado = python[i];
      if (esperado === null || esperado === undefined) {
        caidasDePython++;
        return;
      }
      comparados++;
      expect(validateText(caso.texto), `${caso.nombre}\n${caso.texto}`).toEqual(esperado);
    });
    // La mayoría tiene que haberse podido comparar: si Python se cae en casi todo, la prueba no sirve.
    expect(comparados).toBeGreaterThan(casos.length * 0.9);
    expect(caidasDePython).toBeLessThan(casos.length * 0.1);
  });

  it("los avisos de un JSON roto (frase, línea y columna) son los de Python", () => {
    const python = validateWithPython(BANCO_DE_JSON_ROTO);
    BANCO_DE_JSON_ROTO.forEach((texto, i) => {
      const esperado = python[i];
      if (esperado === null || esperado === undefined) return;
      expect(validateText(texto), JSON.stringify(texto)).toEqual(esperado);
    });
  });
});
