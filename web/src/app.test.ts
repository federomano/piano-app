import { describe, expect, it } from "vitest";
import { EJEMPLOS, EJEMPLO_INICIAL } from "./ejemplos";
import { NOMBRE_APP, prepararPartitura } from "./app";

describe("pantalla inicial", () => {
  it("se llama Piano", () => {
    expect(NOMBRE_APP).toBe("Piano");
  });

  it("incluye los 14 ejemplos de docs/ejemplos y el inicial está entre ellos", () => {
    expect(EJEMPLOS.length).toBe(14);
    expect(EJEMPLOS.map((e) => e.nombre)).toContain(EJEMPLO_INICIAL);
  });

  it("todos los ejemplos se preparan sin avisos", () => {
    for (const e of EJEMPLOS) {
      const r = prepararPartitura(e.texto);
      expect(r.ok, e.nombre).toBe(true);
    }
  });

  it("un archivo roto devuelve avisos en vez de fallar", () => {
    const r = prepararPartitura("{ esto no es json");
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.avisos.length).toBeGreaterThan(0);
  });
});
