import { describe, expect, it } from "vitest";
import { NOMBRE_APP, montar } from "./app";

describe("pantalla inicial", () => {
  it("muestra el nombre de la app", () => {
    const raiz = { textContent: "", setAttribute: () => {} } as unknown as HTMLElement;
    montar(raiz);
    expect(raiz.textContent).toBe(NOMBRE_APP);
  });
});
