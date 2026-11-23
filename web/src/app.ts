export const NOMBRE_APP = "Piano";

/** Dibuja la pantalla inicial: fondo negro con el nombre de la app. */
export function montar(raiz: HTMLElement): void {
  raiz.textContent = NOMBRE_APP;
  raiz.setAttribute("role", "main");
}
