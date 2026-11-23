/*
 * Dibujo de la partitura en un canvas (PIA-90). Es el dibujo de ScoreView.kt (app del Pixel) pasado a canvas 2D:
 * las instrucciones del layout (rectángulos, símbolos de la fuente Bravura, textos y polígonos) en espacios de pentagrama
 * se multiplican por el tamaño de un espacio en píxeles. La línea de reproducción queda fija; la partitura se desliza.
 */
import type { DrawOp } from "../score/layout";
import { Geometry, type ScoreLayout, polygonMinX } from "../score/layout";
import type { Viewport } from "./viewport";

export const PAPER_COLOR = "#F6F3EA";
export const INK_COLOR = "#1A1A1A";
export const PLAYHEAD_COLOR = "rgba(179, 38, 30, 0.65)";

export const MUSIC_FONT = "Bravura";
const TEXT_FONT = '"Helvetica Neue", Arial, sans-serif';

/** El subconjunto del canvas 2D que se usa (así se puede probar también con otro canvas). */
export type Ctx = Pick<
  CanvasRenderingContext2D,
  | "fillRect"
  | "fillText"
  | "beginPath"
  | "moveTo"
  | "lineTo"
  | "closePath"
  | "fill"
  | "save"
  | "restore"
  | "rect"
  | "clip"
  | "stroke"
  | "fillStyle"
  | "strokeStyle"
  | "lineWidth"
  | "lineCap"
  | "globalAlpha"
  | "font"
  | "textBaseline"
  | "textAlign"
>;

function paintOp(ctx: Ctx, op: DrawOp, dx: number, top: number, sp: number, alpha: number): void {
  if (alpha <= 0) return;
  ctx.globalAlpha = alpha;
  switch (op.kind) {
    case "rect":
      ctx.fillRect(dx + op.x * sp, top + op.y * sp, op.width * sp, op.height * sp);
      break;
    case "glyph":
      ctx.font = `${4 * sp * op.scale}px ${MUSIC_FONT}`;
      ctx.fillText(String.fromCodePoint(op.codepoint), dx + op.x * sp, top + op.y * sp);
      break;
    case "polygon": {
      ctx.beginPath();
      for (let i = 0; i < op.points.length; i += 2) {
        const px = dx + op.points[i]! * sp;
        const py = top + op.points[i + 1]! * sp;
        if (i === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.closePath();
      ctx.fill();
      break;
    }
    case "text":
      ctx.font = `${op.size * sp}px ${TEXT_FONT}`;
      ctx.fillText(op.text, dx + op.x * sp, top + op.y * sp);
      break;
  }
}

function leftOf(op: DrawOp): number {
  switch (op.kind) {
    case "polygon":
      return polygonMinX(op);
    default:
      return op.x;
  }
}

/** Pinta un cuadro: fondo, líneas, área fija a la izquierda, lo que viaja con la partitura y la línea de reproducción. */
export function paintScore(ctx: Ctx, layout: ScoreLayout, view: Viewport, scroll: number): void {
  const { widthPx, heightPx, spPx: sp, topPx: top } = view;
  const headerPx = layout.headerWidth * sp;
  ctx.fillStyle = PAPER_COLOR;
  ctx.fillRect(0, 0, widthPx, heightPx);
  ctx.fillStyle = INK_COLOR;
  ctx.textBaseline = "alphabetic";
  ctx.textAlign = "left";

  const paint = (ops: readonly DrawOp[], dx: number, alpha = 1) => {
    for (const op of ops) paintOp(ctx, op, dx, top, sp, alpha);
  };

  // 1) Líneas de los pentagramas, a la derecha del área fija.
  ctx.save();
  ctx.beginPath();
  ctx.rect(headerPx, 0, widthPx - headerPx, heightPx);
  ctx.clip();
  paint(layout.staff, headerPx - scroll * sp);
  ctx.restore();

  // 2) Área fija: líneas, llave y claves (no cambian nunca), y encima la armadura y el compás.
  paint(layout.fixed, 0);
  for (const draw of layout.clefDraws(scroll)) paint(draw.ops, draw.dx * sp, draw.alpha);
  for (const draw of layout.headerDraws(scroll, widthPx / sp)) paint(draw.ops, draw.dx * sp, draw.alpha);

  // 3) Barras de compás, notas y números: pasan por encima del área fija y se desvanecen al acercarse a la clave.
  const fadeEnd = layout.signatureLeft;
  const shift = layout.headerWidth - scroll;
  const dx = shift * sp;
  const visibleRight = widthPx / sp;
  for (const op of layout.marks) {
    const x = leftOf(op) + shift;
    if (x < 0 || x > visibleRight) continue;
    const alpha = Math.min(1, Math.max(0, (x - fadeEnd) / (layout.headerWidth - fadeEnd)));
    paintOp(ctx, op, dx, top, sp, alpha);
  }
  ctx.globalAlpha = 1;

  // 4) Línea de reproducción, siempre en el mismo lugar de la pantalla.
  const lineX = view.playheadSp * sp;
  ctx.strokeStyle = PLAYHEAD_COLOR;
  ctx.lineWidth = 0.14 * sp;
  ctx.lineCap = "butt";
  ctx.beginPath();
  ctx.moveTo(lineX, top - 0.6 * sp);
  ctx.lineTo(lineX, top + (Geometry.SYSTEM_BOTTOM + 0.6) * sp);
  ctx.stroke();
}
