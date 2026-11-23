/*
 * Los mismos casos que tools/test_validar_partitura.py, pasados a TypeScript. Cada caso se revisa dos veces:
 * contra lo que se espera, y contra los avisos exactos del validador real de Python (si hay python3).
 */
import { beforeAll, describe, expect, it } from "vitest";
import { validateBytes, validateText } from "./read";
import { pythonAvailable, validateWithPython } from "./test-support";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Doc = Record<string, any>;

const BASE: Doc = {
  formatVersion: 1,
  title: "Prueba",
  level: "principiante",
  goal: "Probar",
  timeSignatures: [{ startTick: 0, numerator: 4, denominator: 4 }],
  keySignatures: [{ startTick: 0, fifths: 0 }],
  tempos: [{ startTick: 0, bpm: 80 }],
  notes: [
    { hand: "right", pitch: "C4", startTick: 0, durationTicks: 960 },
    { hand: "right", pitch: "D4", startTick: 960, durationTicks: 960 },
    { hand: "left", pitch: "C3", startTick: 0, durationTicks: 1920 },
  ],
  rests: [],
};

const con = (cambio: (d: Doc) => void): Doc => {
  const d = structuredClone(BASE);
  cambio(d);
  return d;
};

type Verifica = (errores: string[]) => void;
interface Caso {
  nombre: string;
  texto: string;
  verifica: Verifica;
}

const casos: Caso[] = [];
const agregar = (nombre: string, datos: Doc, verifica: Verifica): void => {
  casos.push({ nombre, texto: JSON.stringify(datos), verifica });
};
const contiene =
  (texto: string): Verifica =>
  (errores) => {
    expect(errores.some((e) => e.includes(texto)), `se esperaba «${texto}» en ${JSON.stringify(errores)}`).toBe(true);
  };
const vacio: Verifica = (errores) => expect(errores).toEqual([]);

// --- Casos básicos ---
agregar("la base está bien", BASE, vacio);
agregar("mano sin silencio", con((d) => d.notes.pop()), (e) =>
  expect(e.some((x) => x.includes("mano izquierda") && x.includes("falta un silencio"))).toBe(true),
);
agregar(
  "un silencio completa el compás",
  con((d) => {
    d.notes.pop();
    d.rests.push({ hand: "left", startTick: 0, durationTicks: 1920 });
  }),
  vacio,
);
agregar("compás que no suma", con((d) => Object.assign(d.notes[1], { durationTicks: 480 })), contiene("falta un silencio"));
agregar("duración que no es figura (tresillo sin tuplet)", con((d) => Object.assign(d.notes[0], { durationTicks: 320 })), contiene("no es una figura que la app sepa dibujar"));
agregar("fuera de rango", con((d) => Object.assign(d.notes[0], { pitch: "C9" })), contiene("fuera del rango del piano"));
agregar("altura inválida", con((d) => Object.assign(d.notes[0], { pitch: "H4" })), contiene("altura inválida"));
agregar(
  "cruza la barra de compás",
  con((d) => {
    Object.assign(d.notes[1], { startTick: 1440, durationTicks: 960 });
    Object.assign(d.notes[0], { durationTicks: 1440 });
  }),
  contiene("cruza la barra de compás"),
);
agregar("ligadura sin segunda nota", con((d) => Object.assign(d.notes[0], { tiedToNext: true })), contiene("está ligada"));
agregar(
  "voces superpuestas",
  con((d) => d.notes.push({ hand: "right", pitch: "E4", startTick: 0, durationTicks: 1920 })),
  contiene("superpuestas"),
);
agregar("clave desconocida", con((d) => Object.assign(d.notes[0], { dedo: 1 })), contiene("no existe en el modelo"));
agregar(
  "falta título, level y goal",
  con((d) => {
    delete d.title;
    delete d.level;
    delete d.goal;
  }),
  (e) => {
    expect(e.some((x) => x.includes("título"))).toBe(true);
    expect(e.some((x) => x.includes("level"))).toBe(true);
    expect(e.some((x) => x.includes("goal"))).toBe(true);
  },
);
agregar("digitación a medias", con((d) => Object.assign(d.notes[0], { finger: 1 })), contiene("Digitación a medias"));
agregar(
  "último compás incompleto",
  con((d) => {
    Object.assign(d.notes[1], { durationTicks: 480 });
    Object.assign(d.notes[2], { durationTicks: 1440 });
  }),
  contiene("incompleto"),
);
agregar("bpm en otra figura", con((d) => Object.assign(d.tempos[0], { bpm: 500 })), contiene("negras por minuto"));
agregar(
  "cambio de compás a mitad de un compás",
  con((d) => d.timeSignatures.push({ startTick: 960, numerator: 3, denominator: 4 })),
  contiene("no cae al inicio de un compás"),
);

// --- Grupos irregulares ---
function tresillos(cantidad = 3, tuplet: string | null = "3:2", dur = 160): Doc {
  return con((d) => {
    d.notes = [{ hand: "left", pitch: "C3", startTick: 0, durationTicks: 1920 }];
    for (let i = 0; i < cantidad; i++) {
      const n: Doc = { hand: "right", pitch: "C4", startTick: i * dur, durationTicks: dur };
      if (tuplet) n.tuplet = tuplet;
      d.notes.push(n);
    }
    const resto = 1920 - cantidad * dur;
    if (resto) d.rests = [{ hand: "right", startTick: cantidad * dur, durationTicks: resto }];
  });
}
agregar(
  "tresillo con tuplet está bien",
  (() => {
    const d = tresillos(3);
    d.rests = [
      { hand: "right", startTick: 480, durationTicks: 960 },
      { hand: "right", startTick: 1440, durationTicks: 480 },
    ];
    return d;
  })(),
  vacio,
);
agregar("tresillo sin tuplet", tresillos(3, null), contiene('agregá "tuplet"'));
agregar("tuplet mal escrito", tresillos(3, "tres"), contiene('tiene que escribirse como "3:2"'));
agregar("tuplet con duración que no da figura", tresillos(3, "3:2", 200), contiene("no puede durar 200 ticks"));
agregar(
  "grupo incompleto",
  (() => {
    const d = tresillos(2);
    d.rests = [{ hand: "right", startTick: 320, durationTicks: 1600 }];
    return d;
  })(),
  contiene("incompleto"),
);
agregar(
  "cuatro en el tiempo de tres",
  {
    ...BASE,
    timeSignatures: [{ startTick: 0, numerator: 3, denominator: 4 }],
    notes: [
      ...[0, 1, 2, 3].map((i) => ({ hand: "right", pitch: "C4", startTick: i * 360, durationTicks: 360, tuplet: "4:3" })),
      { hand: "left", pitch: "C3", startTick: 0, durationTicks: 1440 },
    ],
    rests: [],
  },
  vacio,
);

// --- Voces ---
const dosVoces = (): Doc => {
  const d = structuredClone(BASE);
  d.notes = [
    { hand: "right", pitch: "E5", startTick: 0, durationTicks: 1920 },
    { hand: "right", pitch: "C4", startTick: 0, durationTicks: 960, voice: 2 },
    { hand: "right", pitch: "E4", startTick: 960, durationTicks: 960, voice: 2 },
    { hand: "left", pitch: "C3", startTick: 0, durationTicks: 1920 },
  ];
  return d;
};
agregar("dos voces en una mano están bien", dosVoces(), vacio);
agregar(
  "voces sin voice se marcan como superpuestas",
  (() => {
    const d = dosVoces();
    for (const n of d.notes) delete n.voice;
    return d;
  })(),
  contiene("superpuestas"),
);
agregar(
  "una voz secundaria tiene que llenar sus compases",
  (() => {
    const d = dosVoces();
    d.notes[2].durationTicks = 480;
    return d;
  })(),
  contiene("voz 2"),
);
agregar(
  "la voz secundaria no hace falta en todos los compases",
  (() => {
    const d = dosVoces();
    d.notes.push(
      { hand: "right", pitch: "G4", startTick: 1920, durationTicks: 1920 },
      { hand: "left", pitch: "C3", startTick: 1920, durationTicks: 1920 },
    );
    return d;
  })(),
  vacio,
);
agregar(
  "voz fuera de rango",
  (() => {
    const d = dosVoces();
    d.notes[1].voice = 5;
    return d;
  })(),
  contiene('"voice" tiene que ser un entero de 1 a 4'),
);
agregar(
  "acorde con notas de distinta duración en la misma voz",
  (() => {
    const d = dosVoces();
    delete d.notes[1].voice;
    return d;
  })(),
  contiene("superpuestas"),
);
agregar(
  "la misma tecla dos veces a la vez",
  (() => {
    const d = dosVoces();
    d.notes.push({ hand: "right", pitch: "E5", startTick: 960, durationTicks: 960, voice: 3 });
    return d;
  })(),
  contiene("se pisan"),
);

// --- Anacrusa ---
const anacrusa = (notes: Doc[], rests?: Doc[]): Doc =>
  con((d) => {
    d.pickupTicks = 480;
    d.notes = notes;
    if (rests) d.rests = rests;
  });
agregar(
  "anacrusa bien",
  anacrusa(
    [
      { hand: "right", pitch: "G4", startTick: 0, durationTicks: 480 },
      { hand: "right", pitch: "C5", startTick: 480, durationTicks: 1920 },
      { hand: "left", pitch: "C3", startTick: 480, durationTicks: 1920 },
    ],
    [{ hand: "left", startTick: 0, durationTicks: 480 }],
  ),
  vacio,
);
agregar(
  "anacrusa sin llenar la mano izquierda",
  anacrusa([
    { hand: "right", pitch: "G4", startTick: 0, durationTicks: 480 },
    { hand: "right", pitch: "C5", startTick: 480, durationTicks: 1920 },
    { hand: "left", pitch: "C3", startTick: 480, durationTicks: 1920 },
  ]),
  contiene("compás 0"),
);
agregar(
  "anacrusa que cruza su barra",
  anacrusa([
    { hand: "right", pitch: "G4", startTick: 0, durationTicks: 960 },
    { hand: "left", pitch: "C3", startTick: 0, durationTicks: 960 },
  ]),
  contiene("cruza la barra"),
);
agregar("anacrusa demasiado larga", con((d) => (d.pickupTicks = 1920)), contiene("pickupTicks"));
agregar("anacrusa negativa", con((d) => (d.pickupTicks = -5)), contiene("pickupTicks"));

// --- Fusas ---
const fusas = (d: Doc): void => {
  const ns: Doc[] = [];
  for (let i = 0; i < 8; i++) ns.push({ hand: "right", pitch: "C5", startTick: i * 60, durationTicks: 60 });
  ns.push(
    { hand: "right", pitch: "D5", startTick: 480, durationTicks: 960 },
    { hand: "right", pitch: "E5", startTick: 1440, durationTicks: 90 },
    { hand: "right", pitch: "F5", startTick: 1530, durationTicks: 30 },
  );
  d.notes = [...ns, ...d.notes.filter((n: Doc) => n.hand === "left")];
};
agregar("fusas: 30 ticks no es una figura", con(fusas), contiene("30"));
agregar(
  "fusas bien",
  con((d) => {
    fusas(d);
    d.notes = d.notes.filter((n: Doc) => n.durationTicks !== 30 && n.durationTicks !== 90);
    d.notes.push({ hand: "right", pitch: "E5", startTick: 1440, durationTicks: 480 });
  }),
  (e) => expect(e.filter((x) => x.includes("duración") || x.includes("figura"))).toEqual([]),
);

// --- Adornos ---
const adorno = (extra: Doc): Doc =>
  con((d) => d.notes.push({ hand: "right", pitch: "B3", startTick: 960, durationTicks: 60, grace: true, ...extra }));
agregar("adorno bien", adorno({}), vacio);
agregar("adorno sin nota principal", adorno({ startTick: 480 }), contiene("no tiene una nota principal"));
agregar("adorno demasiado largo", adorno({ durationTicks: 480 }), contiene("como mucho"));
agregar("adorno ligado", adorno({ tiedToNext: true }), contiene("ligado"));
agregar("adorno con tuplet", adorno({ tuplet: "3:2" }), contiene("tuplet"));
agregar("grace que no es true o false", adorno({ grace: "si" }), contiene("true o false"));

// --- Dinámicas y reguladores ---
agregar(
  "dinámicas bien",
  con((d) => {
    d.dynamics = [
      { startTick: 0, level: "p" },
      { startTick: 960, level: "f" },
    ];
    d.hairpins = [{ startTick: 0, endTick: 960, kind: "crescendo" }];
  }),
  vacio,
);
agregar("dinámica con nivel inventado", con((d) => (d.dynamics = [{ startTick: 0, level: "forte" }])), contiene('"level" tiene que ser uno de'));
agregar(
  "dinámicas desordenadas",
  con((d) => (d.dynamics = [{ startTick: 960, level: "p" }, { startTick: 0, level: "f" }])),
  contiene("ordenadas"),
);
agregar("dinámica después del final", con((d) => (d.dynamics = [{ startTick: 9600, level: "f" }])), contiene("después del final"));
agregar("regulador que termina donde empieza", con((d) => (d.hairpins = [{ startTick: 960, endTick: 960, kind: "crescendo" }])), contiene("mayor que"));
agregar("regulador con kind inventado", con((d) => (d.hairpins = [{ startTick: 0, endTick: 960, kind: "sube" }])), contiene("crescendo"));
agregar(
  "reguladores que se pisan",
  con(
    (d) =>
      (d.hairpins = [
        { startTick: 0, endTick: 1440, kind: "crescendo" },
        { startTick: 960, endTick: 1920, kind: "diminuendo" },
      ]),
  ),
  contiene("se pisa"),
);
agregar(
  "dinámica en el medio de un regulador",
  con((d) => {
    d.hairpins = [{ startTick: 0, endTick: 1920, kind: "crescendo" }];
    d.dynamics = [{ startTick: 960, level: "f" }];
  }),
  contiene("en el medio de un regulador"),
);
agregar(
  "clave desconocida en una dinámica",
  con((d) => (d.dynamics = [{ startTick: 0, level: "p", color: "rojo" }])),
  contiene("no existe en el modelo"),
);

// --- Articulaciones ---
agregar(
  "articulaciones bien",
  con((d) => {
    d.notes[0].staccato = true;
    d.notes[1].accent = true;
    d.notes[1].staccato = true;
  }),
  vacio,
);
agregar("articulación no booleana", con((d) => (d.notes[0].accent = "si")), contiene("true o false"));
agregar(
  "staccato en nota ligada",
  con((d) => {
    Object.assign(d.notes[0], { durationTicks: 960, tiedToNext: true, staccato: true });
    Object.assign(d.notes[1], { pitch: "C4" });
  }),
  contiene('no puede llevar "staccato"'),
);
agregar("acento en un adorno", adorno({ accent: true }), contiene('no puede llevar "accent"'));
agregar(
  "acorde con articulaciones distintas",
  con((d) => d.notes.push({ hand: "left", pitch: "G3", startTick: 0, durationTicks: 1920, staccato: true })),
  contiene('el mismo "staccato"'),
);

// --- Claves, octavas y pedal ---
agregar("clave de la izquierda en sol está bien", con((d) => (d.clefs = [{ hand: "left", startTick: 0, clef: "treble" }])), vacio);
agregar("clave con mano inventada", con((d) => (d.clefs = [{ hand: "izq", startTick: 0, clef: "bass" }])), contiene('"hand" tiene que ser'));
agregar("clave inventada", con((d) => (d.clefs = [{ hand: "left", startTick: 0, clef: "alto" }])), contiene('"clef" tiene que ser'));
agregar("cambio de clave a mitad de un compás", con((d) => (d.clefs = [{ hand: "left", startTick: 480, clef: "treble" }])), contiene("no cae al inicio de un compás"));
agregar(
  "claves desordenadas",
  con((d) => (d.clefs = [{ hand: "left", startTick: 0, clef: "treble" }, { hand: "left", startTick: 0, clef: "bass" }])),
  contiene("ordenadas"),
);
agregar("clave con una clave de más", con((d) => (d.clefs = [{ hand: "left", startTick: 0, clef: "bass", x: 1 }])), contiene('"x"'));

agregar("octava bien", con((d) => (d.ottavas = [{ hand: "right", startTick: 0, endTick: 1920, kind: "8va" }])), vacio);
agregar("octava 15ma", con((d) => (d.ottavas = [{ hand: "right", startTick: 0, endTick: 1920, kind: "15ma" }])), contiene('"kind" tiene que ser'));
agregar("octava que termina donde empieza", con((d) => (d.ottavas = [{ hand: "right", startTick: 960, endTick: 960, kind: "8va" }])), contiene("endTick mayor que startTick"));
agregar(
  "octavas que se pisan",
  con(
    (d) =>
      (d.ottavas = [
        { hand: "right", startTick: 0, endTick: 1000, kind: "8va" },
        { hand: "right", startTick: 900, endTick: 1920, kind: "8va" },
      ]),
  ),
  contiene("no pueden superponerse"),
);
agregar("octava sin ninguna nota adentro", con((d) => (d.ottavas = [{ hand: "right", startTick: 5000, endTick: 6000, kind: "8vb" }])), contiene("ninguna nota"));
agregar(
  "octava que parte una ligadura",
  con((d) => {
    d.notes = [
      { hand: "right", pitch: "C4", startTick: 0, durationTicks: 1920, tiedToNext: true },
      { hand: "right", pitch: "C4", startTick: 1920, durationTicks: 1920 },
      { hand: "left", pitch: "C3", startTick: 0, durationTicks: 3840 },
    ];
    d.ottavas = [{ hand: "right", startTick: 0, endTick: 1920, kind: "8va" }];
  }),
  contiene("fuera de su signo de octava"),
);

agregar(
  "pedal con cambio de pedal está bien",
  con((d) => (d.pedals = [{ startTick: 0, endTick: 960 }, { startTick: 960, endTick: 1920 }])),
  vacio,
);
agregar("pedal que se suelta donde se aprieta", con((d) => (d.pedals = [{ startTick: 960, endTick: 960 }])), contiene("endTick mayor que startTick"));
agregar("pedales que se pisan", con((d) => (d.pedals = [{ startTick: 0, endTick: 1000 }, { startTick: 900, endTick: 1920 }])), contiene("sin superponerse"));
agregar("pedal después del final", con((d) => (d.pedals = [{ startTick: 0, endTick: 99999 }])), contiene("termina después del final"));
agregar("pedal con una clave de más", con((d) => (d.pedals = [{ startTick: 0, endTick: 960, x: 1 }])), contiene('"x"'));

// --- JSON roto (se escribe a mano porque no es un objeto válido) ---
casos.push({ nombre: "json roto: coma de más", texto: '{"title": "x",}', verifica: contiene("No es un JSON válido") });

describe("contrato: casos del validador de Python", () => {
  let python: Array<string[] | null> = [];
  beforeAll(() => {
    if (pythonAvailable) python = validateWithPython(casos.map((c) => c.texto));
  });

  it.each(casos.map((c, i) => [c.nombre, i] as const))("%s", (_nombre, i) => {
    const caso = casos[i]!;
    const errores = validateText(caso.texto);
    caso.verifica(errores);
  });

  it.skipIf(!pythonAvailable)("da exactamente los mismos avisos que el validador de Python", () => {
    casos.forEach((caso, i) => {
      const esperado = python[i];
      if (esperado === null || esperado === undefined) return;
      expect(validateText(caso.texto), caso.nombre).toEqual(esperado);
    });
  });
});

describe("archivos que no son un JSON de partitura", () => {
  it("un archivo que no está en UTF-8", () => {
    expect(validateBytes(new Uint8Array([0x7b, 0xff, 0xfe, 0x7d]))).toEqual(["El archivo no está en UTF-8."]);
  });

  it("acepta la marca de orden de bytes (BOM) al principio", () => {
    const bytes = new TextEncoder().encode("﻿" + JSON.stringify(BASE));
    expect(validateBytes(bytes)).toEqual([]);
  });

  it("un JSON que no es un objeto", () => {
    expect(validateText("[1, 2]")).toEqual(["El archivo tiene que ser un objeto JSON con llaves { }."]);
  });

  it("un número escrito con punto decimal no es un entero", () => {
    const texto = JSON.stringify(BASE).replace('"durationTicks":960', '"durationTicks":960.0');
    expect(validateText(texto).some((e) => e.includes('"durationTicks" tiene que ser un entero'))).toBe(true);
  });

  it("un bpm con punto decimal está bien (así lo escribe la app)", () => {
    const texto = JSON.stringify(BASE).replace('"bpm":80', '"bpm":80.0');
    expect(validateText(texto)).toEqual([]);
  });
});
