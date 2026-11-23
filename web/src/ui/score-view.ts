/*
 * La vista de la partitura (PIA-90): un canvas que ocupa todo el lugar disponible, nítido en pantallas retina, que se
 * reacomoda al cambiar el tamaño de la ventana y se desliza arrastrando con el mouse o el dedo (con inercia), o con la
 * rueda / el trackpad. Mismo comportamiento que ScoreView.kt en el Pixel, sin reproducción todavía.
 */
import type { ScoreLayout } from "../score/layout";
import { MUSIC_FONT, paintScore } from "./paint";
import {
  FLING_END_SPEED,
  MIN_FLING_SPEED,
  type Viewport,
  clampScroll,
  computeViewport,
  inertiaStep,
  retarget,
} from "./viewport";

/** Carga la fuente de música. Hasta que esté lista no se dibuja (los símbolos saldrían como cuadraditos). */
export async function loadMusicFont(): Promise<void> {
  const url = new URL(`${import.meta.env.BASE_URL}fonts/Bravura.otf`, document.baseURI).href;
  const face = new FontFace(MUSIC_FONT, `url(${url})`);
  await face.load();
  document.fonts.add(face);
}

export class ScoreView {
  readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private layout: ScoreLayout | null = null;
  private view: Viewport | null = null;
  private scroll = 0;
  private dirty = false;
  private frame = 0;
  private resizeObserver: ResizeObserver;

  // Arrastre
  private dragging = false;
  private lastX = 0;
  private lastTime = 0;
  private velocity = 0; // espacios por segundo (positivo = la partitura avanza)
  private flingFrame = 0;

  constructor(private readonly parent: HTMLElement) {
    this.canvas = document.createElement("canvas");
    this.canvas.className = "partitura";
    this.canvas.setAttribute("role", "img");
    this.canvas.setAttribute("aria-label", "Partitura");
    this.canvas.style.touchAction = "none";
    this.canvas.tabIndex = 0;
    parent.appendChild(this.canvas);
    const ctx = this.canvas.getContext("2d");
    if (!ctx) throw new Error("Este navegador no puede dibujar en un canvas.");
    this.ctx = ctx;

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(parent);

    this.canvas.addEventListener("pointerdown", this.onDown);
    this.canvas.addEventListener("pointermove", this.onMove);
    this.canvas.addEventListener("pointerup", this.onUp);
    this.canvas.addEventListener("pointercancel", this.onUp);
    this.canvas.addEventListener("wheel", this.onWheel, { passive: false });
    this.canvas.addEventListener("keydown", this.onKey);
    this.resize();
  }

  /** Muestra otra partitura, desde el comienzo. */
  setLayout(layout: ScoreLayout | null): void {
    this.stopFling();
    this.layout = layout;
    this.view = null;
    this.resize(true);
  }

  /** Desplazamiento actual (espacios de pentagrama), para pruebas y para el resto de la app. */
  get position(): number {
    return this.scroll;
  }

  get viewport(): Viewport | null {
    return this.view;
  }

  /** Mueve la partitura a un desplazamiento (se limita a los extremos). */
  scrollTo(scroll: number): void {
    if (!this.view) return;
    this.scroll = clampScroll(scroll, this.view);
    this.invalidate();
  }

  destroy(): void {
    this.stopFling();
    cancelAnimationFrame(this.frame);
    this.resizeObserver.disconnect();
    this.canvas.remove();
  }

  // --- Tamaño ---

  private resize(reset = false): void {
    const width = Math.max(1, Math.round(this.parent.clientWidth));
    const height = Math.max(1, Math.round(this.parent.clientHeight));
    const dpr = window.devicePixelRatio || 1;
    const pxW = Math.round(width * dpr);
    const pxH = Math.round(height * dpr);
    if (this.canvas.width !== pxW || this.canvas.height !== pxH) {
      this.canvas.width = pxW;
      this.canvas.height = pxH;
    }
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;
    if (this.layout) {
      const next = computeViewport(this.layout, width, height);
      if (reset || !this.view) this.scroll = next.minScroll;
      else this.scroll = retarget(this.layout, this.scroll, this.view.playheadSp, next.playheadSp);
      this.view = next;
      this.scroll = clampScroll(this.scroll, next);
    }
    this.invalidate();
  }

  // --- Dibujo ---

  private invalidate(): void {
    if (this.dirty) return;
    this.dirty = true;
    this.frame = requestAnimationFrame(() => {
      this.dirty = false;
      this.paint();
    });
  }

  /** Dibuja ya (sin esperar al próximo cuadro). */
  paint(): void {
    const dpr = window.devicePixelRatio || 1;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (!this.layout || !this.view) {
      this.ctx.fillStyle = "#F6F3EA";
      this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
      return;
    }
    paintScore(this.ctx, this.layout, this.view, this.scroll);
  }

  // --- Mouse y dedo ---

  private onDown = (e: PointerEvent): void => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    this.stopFling();
    this.dragging = true;
    this.lastX = e.clientX;
    this.lastTime = e.timeStamp;
    this.velocity = 0;
    this.canvas.setPointerCapture(e.pointerId);
    this.canvas.classList.add("arrastrando");
  };

  private onMove = (e: PointerEvent): void => {
    if (!this.dragging || !this.view) return;
    const dx = e.clientX - this.lastX;
    const dt = (e.timeStamp - this.lastTime) / 1000;
    this.lastX = e.clientX;
    this.lastTime = e.timeStamp;
    const delta = -dx / this.view.spPx;
    this.scroll = clampScroll(this.scroll + delta, this.view);
    if (dt > 0) {
      // Velocidad suavizada: así un movimiento brusco al final no dispara la inercia.
      const instant = delta / dt;
      this.velocity = this.velocity * 0.6 + instant * 0.4;
    }
    this.invalidate();
  };

  private onUp = (e: PointerEvent): void => {
    if (!this.dragging) return;
    this.dragging = false;
    this.canvas.classList.remove("arrastrando");
    if (this.canvas.hasPointerCapture(e.pointerId)) this.canvas.releasePointerCapture(e.pointerId);
    // Si se quedó quieto antes de soltar, no hay inercia.
    const stale = e.timeStamp - this.lastTime > 80;
    if (!stale && Math.abs(this.velocity) >= MIN_FLING_SPEED) this.startFling();
  };

  private startFling(): void {
    let last = performance.now();
    const step = (now: number) => {
      const view = this.view;
      if (!view) return;
      const seconds = Math.min((now - last) / 1000, 0.05);
      last = now;
      this.velocity = inertiaStep(this.velocity, seconds);
      const before = this.scroll;
      this.scroll = clampScroll(this.scroll + this.velocity * seconds, view);
      this.paint();
      if (Math.abs(this.velocity) < FLING_END_SPEED || this.scroll === before) {
        this.flingFrame = 0;
        return;
      }
      this.flingFrame = requestAnimationFrame(step);
    };
    this.flingFrame = requestAnimationFrame(step);
  }

  private stopFling(): void {
    if (this.flingFrame) cancelAnimationFrame(this.flingFrame);
    this.flingFrame = 0;
  }

  // --- Rueda, trackpad y teclado ---

  private onWheel = (e: WheelEvent): void => {
    if (!this.view) return;
    e.preventDefault();
    this.stopFling();
    // El trackpad manda desplazamiento horizontal; la rueda del mouse, vertical: las dos mueven la partitura.
    const pixels = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
    const lineHeight = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? this.view.heightPx : 1;
    this.scrollTo(this.scroll + (pixels * lineHeight) / this.view.spPx);
  };

  private onKey = (e: KeyboardEvent): void => {
    if (!this.view) return;
    const page = this.view.widthPx / this.view.spPx / 2;
    const moves: Record<string, number> = { ArrowRight: 4, ArrowLeft: -4, PageDown: page, PageUp: -page };
    if (e.key === "Home") this.scrollTo(this.view.minScroll);
    else if (e.key === "End") this.scrollTo(this.view.maxScroll);
    else if (e.key in moves) this.scrollTo(this.scroll + moves[e.key]!);
    else return;
    e.preventDefault();
  };
}
