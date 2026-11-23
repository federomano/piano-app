import { EJEMPLOS, EJEMPLO_INICIAL } from "./ejemplos";
import { readScoreText } from "./score";
import { ScoreLayout } from "./score/layout";
import { ScoreView, loadMusicFont } from "./ui/score-view";

export const NOMBRE_APP = "Piano";

/** Calcula el layout de un texto JSON, o devuelve los avisos en español. */
export function prepararPartitura(texto: string): { ok: true; layout: ScoreLayout } | { ok: false; avisos: readonly string[] } {
  const leida = readScoreText(texto);
  if (!leida.ok) return { ok: false, avisos: leida.errors };
  return { ok: true, layout: ScoreLayout.build(leida.score) };
}

/** Arma la pantalla: una barra con el nombre y la lista de ejemplos, y debajo la partitura. */
export function montar(raiz: HTMLElement): { vista: ScoreView; mostrar: (nombre: string) => void } {
  raiz.textContent = "";
  raiz.setAttribute("role", "main");

  const barra = document.createElement("header");
  barra.className = "barra";
  const titulo = document.createElement("h1");
  titulo.textContent = NOMBRE_APP;
  const lista = document.createElement("select");
  lista.setAttribute("aria-label", "Partitura de ejemplo");
  for (const e of EJEMPLOS) lista.add(new Option(e.nombre, e.nombre));
  barra.append(titulo, lista);

  const zona = document.createElement("div");
  zona.className = "zona";
  const aviso = document.createElement("p");
  aviso.className = "aviso";
  aviso.hidden = true;
  zona.appendChild(aviso);
  raiz.append(barra, zona);

  const vista = new ScoreView(zona);

  const mostrar = (nombre: string): void => {
    const ejemplo = EJEMPLOS.find((e) => e.nombre === nombre) ?? EJEMPLOS[0];
    if (!ejemplo) return;
    lista.value = ejemplo.nombre;
    history.replaceState(null, "", `#${ejemplo.nombre}`);
    const r = prepararPartitura(ejemplo.texto);
    if (r.ok) {
      aviso.hidden = true;
      vista.setLayout(r.layout);
    } else {
      aviso.hidden = false;
      aviso.textContent = `No se pudo abrir ${ejemplo.nombre}:\n${r.avisos.join("\n")}`;
      vista.setLayout(null);
    }
  };
  lista.addEventListener("change", () => mostrar(lista.value));

  // La fuente de música tiene que estar cargada antes de dibujar.
  loadMusicFont()
    .catch(() => {
      aviso.hidden = false;
      aviso.textContent = "No se pudo cargar la fuente de música; los símbolos no se van a ver bien.";
    })
    .finally(() => {
      const pedido = decodeURIComponent(location.hash.replace(/^#/, ""));
      mostrar(EJEMPLOS.some((e) => e.nombre === pedido) ? pedido : EJEMPLO_INICIAL);
    });

  return { vista, mostrar };
}
