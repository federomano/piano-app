import { describe, expect, it } from "vitest";
import { pitchToString, lengthTicks } from "./model";
import { readScoreText, validateText } from "./read";
import { exampleFiles, pythonAvailable, readExample, validateWithPython } from "./test-support";

const archivos = exampleFiles();

describe("ejemplos de referencia (docs/ejemplos)", () => {
  it("están todos los ejemplos del contrato", () => {
    expect(archivos.length).toBeGreaterThanOrEqual(14);
  });

  it.each(archivos)("%s cumple el contrato y se lee entero", (nombre) => {
    const texto = readExample(nombre);
    expect(validateText(texto)).toEqual([]);

    const resultado = readScoreText(texto);
    if (!resultado.ok) throw new Error(resultado.errors.join("\n"));
    const original = JSON.parse(texto) as { title: string; notes: Array<{ pitch: string; startTick: number }>; rests?: unknown[] };
    const { score } = resultado;
    expect(score.title).toBe(original.title);
    expect(score.notes).toHaveLength(original.notes.length);
    expect(score.rests).toHaveLength(original.rests?.length ?? 0);
    // Cada altura vuelve a escribirse igual que en el archivo, y cada nota queda en su lugar.
    score.notes.forEach((nota, i) => {
      expect(pitchToString(nota.pitch)).toBe(original.notes[i]!.pitch);
      expect(nota.startTick).toBe(original.notes[i]!.startTick);
    });
    expect(lengthTicks(score)).toBeGreaterThan(0);
  });

  it("los ejemplos con piezas especiales traen lo que dicen traer", () => {
    const leer = (nombre: string) => {
      const r = readScoreText(readExample(nombre));
      if (!r.ok) throw new Error(r.errors.join("\n"));
      return r.score;
    };
    expect(leer("ejercicio-tresillos.json").notes.some((n) => n.tuplet?.actual === 3 && n.tuplet.normal === 2)).toBe(true);
    expect(leer("ejercicio-dos-voces.json").notes.some((n) => n.voice === 2)).toBe(true);
    expect(leer("ejercicio-anacrusa.json").pickupTicks).toBeGreaterThan(0);
    expect(leer("ejercicio-adornos.json").notes.some((n) => n.grace)).toBe(true);
    expect(leer("ejercicio-dinamicas.json").dynamics.length).toBeGreaterThan(0);
    expect(leer("ejercicio-dinamicas.json").hairpins.length).toBeGreaterThan(0);
    expect(leer("ejercicio-articulaciones.json").notes.some((n) => n.staccato)).toBe(true);
    expect(leer("ejercicio-articulaciones.json").notes.some((n) => n.accent)).toBe(true);
    expect(leer("ejercicio-octavas.json").ottavas.length).toBeGreaterThan(0);
    expect(leer("ejercicio-pedal.json").pedals.length).toBeGreaterThan(0);
    expect(leer("ejercicio-claves.json").clefs.length).toBeGreaterThan(0);
    expect(leer("partitura-ejemplo.json").notes.every((n) => n.finger !== null)).toBe(true);
  });

  it.skipIf(!pythonAvailable)("el validador de Python dice lo mismo (nada que corregir)", () => {
    const resultados = validateWithPython(archivos.map(readExample));
    resultados.forEach((r, i) => expect(r, archivos[i]).toEqual([]));
  });
});
