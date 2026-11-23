/*
 * Ayudas solo para los tests: correr el validador real de Python (tools/validar_partitura.py) sobre los mismos textos
 * y comparar sus avisos con los de la versión TypeScript.
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

/** Carpeta raíz del repo (web/src/score → tres niveles arriba). */
export const REPO_ROOT = fileURLToPath(new URL("../../../", import.meta.url));
export const EXAMPLES_DIR = join(REPO_ROOT, "docs", "ejemplos");

export function exampleFiles(): string[] {
  return readdirSync(EXAMPLES_DIR)
    .filter((name) => name.endsWith(".json"))
    .sort();
}

export function readExample(name: string): string {
  return readFileSync(join(EXAMPLES_DIR, name), "utf8");
}

export const pythonAvailable: boolean = spawnSync("python3", ["--version"]).status === 0;

const SCRIPT = `
import sys, os, json
sys.path.insert(0, sys.argv[2])
import validar_partitura as v
d = sys.argv[1]
out = {}
for n in sorted(os.listdir(d)):
    try:
        out[n] = v.validate_file(os.path.join(d, n))
    except Exception:
        out[n] = None
sys.stdout.buffer.write(json.dumps(out).encode("ascii"))
`;

/**
 * Corre `validate_file` del validador de Python sobre cada texto. Devuelve sus avisos, o null si Python falló con
 * una excepción en ese texto (el validador original se rompe con algunas entradas muy raras).
 */
export function validateWithPython(texts: readonly string[]): Array<string[] | null> {
  const dir = mkdtempSync(join(tmpdir(), "piano-validar-"));
  try {
    texts.forEach((text, i) => writeFileSync(join(dir, `c${String(i).padStart(6, "0")}.json`), text, "utf8"));
    const result = spawnSync("python3", ["-I", "-c", SCRIPT, dir, join(REPO_ROOT, "tools")], {
      maxBuffer: 256 * 1024 * 1024,
    });
    if (result.status !== 0) throw new Error(`Python falló: ${result.stderr?.toString()}`);
    const parsed = JSON.parse(result.stdout.toString("ascii")) as Record<string, string[] | null>;
    return texts.map((_, i) => parsed[`c${String(i).padStart(6, "0")}.json`] ?? null);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
