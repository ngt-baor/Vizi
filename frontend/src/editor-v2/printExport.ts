import type { EditorDocumentV2, EditorLayerV2, EditorPageV2, EditorSide } from "./document";
import { normalizedGradientStops } from "./shapeAppearance";

export const PRINT_BLEED_MM = 2;
export const PRINT_DPI = 300;

const SCREEN_CANVAS_WIDTH_PX = 720;
const MM_PER_INCH = 25.4;
const EXPORT_SIDES: EditorSide[] = ["front", "back"];

export type PrintExportSpec = {
  bleedMm: number;
  dpi: number;
  trimWidthMm: number;
  trimHeightMm: number;
  pageWidthMm: number;
  pageHeightMm: number;
  pixelWidth: number;
  pixelHeight: number;
};

export type PrintPdfResult = {
  blob: Blob;
  fileName: string;
  spec: PrintExportSpec;
};

export function getPrintExportSpec(document: EditorDocumentV2): PrintExportSpec {
  const { widthMm, heightMm } = document.card;
  const pageWidthMm = widthMm + PRINT_BLEED_MM * 2;
  const pageHeightMm = heightMm + PRINT_BLEED_MM * 2;
  return {
    bleedMm: PRINT_BLEED_MM,
    dpi: PRINT_DPI,
    trimWidthMm: widthMm,
    trimHeightMm: heightMm,
    pageWidthMm,
    pageHeightMm,
    pixelWidth: mmToPixels(pageWidthMm),
    pixelHeight: mmToPixels(pageHeightMm),
  };
}

export async function createPrintPdf(document: EditorDocumentV2): Promise<PrintPdfResult> {
  const { jsPDF } = await import("jspdf");
  const spec = getPrintExportSpec(document);
  const orientation = spec.pageWidthMm >= spec.pageHeightMm ? "landscape" : "portrait";
  const pdf = new jsPDF({
    orientation,
    unit: "mm",
    format: [spec.pageWidthMm, spec.pageHeightMm],
    compress: true,
  });

  pdf.setProperties({
    title: document.name,
    subject: `${spec.trimWidthMm} x ${spec.trimHeightMm} mm business card with ${spec.bleedMm} mm bleed`,
    creator: "Vizi",
  });

  await window.document.fonts?.ready;
  for (const [index, side] of EXPORT_SIDES.entries()) {
    if (index > 0) {
      pdf.addPage([spec.pageWidthMm, spec.pageHeightMm], orientation);
    }
    const image = await renderPage(document.pages[side], spec);
    pdf.addImage(image, "PNG", 0, 0, spec.pageWidthMm, spec.pageHeightMm, side, "FAST");
  }

  return {
    blob: pdf.output("blob"),
    fileName: `${safeFilePart(document.name) || "business-card"}-${safeFilePart(document.documentId) || "draft"}-print.pdf`,
    spec,
  };
}

export function downloadPrintPdf(result: PrintPdfResult): void {
  const url = URL.createObjectURL(result.blob);
  const link = window.document.createElement("a");
  link.href = url;
  link.download = result.fileName;
  link.style.display = "none";
  window.document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

async function renderPage(page: EditorPageV2, spec: PrintExportSpec): Promise<string> {
  const canvas = window.document.createElement("canvas");
  canvas.width = spec.pixelWidth;
  canvas.height = spec.pixelHeight;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("This browser cannot create the print canvas.");

  const bleedXPx = (spec.bleedMm / spec.pageWidthMm) * canvas.width;
  const bleedYPx = (spec.bleedMm / spec.pageHeightMm) * canvas.height;
  const trimWidthPx = (spec.trimWidthMm / spec.pageWidthMm) * canvas.width;
  const trimHeightPx = (spec.trimHeightMm / spec.pageHeightMm) * canvas.height;
  if (page.background !== "transparent") {
    context.fillStyle = page.background || "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
  }

  const images = await loadPageImages(page);
  for (const layer of page.layers) {
    if (layer.visible) drawLayer(context, layer, images, bleedXPx, bleedYPx, trimWidthPx, trimHeightPx);
  }
  return canvas.toDataURL("image/png");
}

async function loadPageImages(page: EditorPageV2): Promise<Map<string, HTMLImageElement>> {
  const imageLayers = page.layers.filter((layer) => layer.visible && layer.type === "image");
  const sources = [...new Set(imageLayers.map((layer) => {
    const source = safeImageSource(layer.src);
    if (!source && !layer.frameId) throw new Error(`Image "${layer.name}" has no safe source for print export.`);
    return source;
  }).filter(Boolean))];
  const entries = await Promise.all(sources.map(async (source) => [source, await loadImage(source)] as const));
  return new Map(entries);
}

async function loadImage(source: string): Promise<HTMLImageElement> {
  if (source.startsWith("data:")) return decodeImage(source);

  const response = await fetch(source, { credentials: "include" });
  if (!response.ok) throw new Error(`Cannot load an image for print export (${response.status}).`);
  const objectUrl = URL.createObjectURL(await response.blob());
  try {
    return await decodeImage(objectUrl);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

function decodeImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.decoding = "async";
    image.addEventListener("load", () => resolve(image), { once: true });
    image.addEventListener("error", () => reject(new Error("An image could not be decoded for print export.")), { once: true });
    image.src = source;
  });
}

function drawLayer(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  images: Map<string, HTMLImageElement>,
  bleedXPx: number,
  bleedYPx: number,
  trimWidthPx: number,
  trimHeightPx: number,
): void {
  const x = bleedXPx + (layer.x / 100) * trimWidthPx;
  const y = bleedYPx + (layer.y / 100) * trimHeightPx;
  const width = (layer.width / 100) * trimWidthPx;
  const height = (layer.height / 100) * trimHeightPx;
  const scale = trimWidthPx / SCREEN_CANVAS_WIDTH_PX;

  context.save();
  context.globalAlpha = clamp(layer.opacity, 0, 1);
  context.translate(x + width / 2, y + height / 2);
  context.rotate((layer.rotation * Math.PI) / 180);
  if (layer.type === "text" || layer.type === "image") {
    context.beginPath();
    if (layer.type === "image" && layer.shapeKind && layer.shapeKind !== "rectangle") {
      drawShapePath(context, layer, width, height, scale);
    } else {
      context.rect(-width / 2, -height / 2, width, height);
    }
    context.clip();
  }

  if (layer.type === "text") {
    drawText(context, layer, width, height, scale);
  } else if (layer.type === "image") {
    const source = safeImageSource(layer.src);
    const image = source ? images.get(source) : undefined;
    if (image) drawImageCover(context, image, width, height);
  } else {
    drawShape(context, layer, width, height, scale);
  }
  context.restore();
}

function drawShape(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
  scale: number,
): void {
  drawShapePath(context, layer, width, height, scale);
  fillAndStroke(context, layer, width, height, scale);
}

function drawShapePath(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
  scale: number,
): void {
  context.beginPath();
  if (layer.type === "ellipse" || layer.shapeKind === "ellipse") {
    context.ellipse(0, 0, width / 2, height / 2, 0, 0, Math.PI * 2);
    return;
  }

  const pointsByKind: Record<string, Array<[number, number]>> = {
    triangle: [[0.5, 0.03], [0.97, 0.97], [0.03, 0.97]],
    pentagon: [[0.5, 0.02], [0.98, 0.38], [0.8, 0.98], [0.2, 0.98], [0.02, 0.38]],
    hexagon: [[0.25, 0.03], [0.75, 0.03], [0.98, 0.5], [0.75, 0.97], [0.25, 0.97], [0.02, 0.5]],
    diamond: [[0.5, 0.02], [0.98, 0.5], [0.5, 0.98], [0.02, 0.5]],
    octagon: [[0.28, 0.02], [0.72, 0.02], [0.98, 0.28], [0.98, 0.72], [0.72, 0.98], [0.28, 0.98], [0.02, 0.72], [0.02, 0.28]],
    star: [[0.5, 0.02], [0.61, 0.36], [0.98, 0.36], [0.68, 0.58], [0.8, 0.97], [0.5, 0.74], [0.2, 0.97], [0.32, 0.58], [0.02, 0.36], [0.39, 0.36]],
    burst: [[0.5, 0], [0.58, 0.2], [0.71, 0.07], [0.72, 0.27], [0.93, 0.2], [0.8, 0.37], [1, 0.5], [0.8, 0.58], [0.93, 0.8], [0.71, 0.72], [0.72, 0.93], [0.58, 0.8], [0.5, 1], [0.42, 0.8], [0.29, 0.93], [0.28, 0.72], [0.07, 0.8], [0.2, 0.58], [0, 0.5], [0.2, 0.37], [0.07, 0.2], [0.29, 0.27], [0.28, 0.07], [0.42, 0.2]],
  };
  const points = pointsByKind[layer.shapeKind ?? ""];
  if (points) {
    context.moveTo((points[0][0] - 0.5) * width, (points[0][1] - 0.5) * height);
    for (const [pointX, pointY] of points.slice(1)) {
      context.lineTo((pointX - 0.5) * width, (pointY - 0.5) * height);
    }
    context.closePath();
    return;
  }

  const radius = Math.min(
    layer.shapeKind === "pill" ? Math.max(width, height) / 2 : Math.max(0, (layer.cornerRadius ?? 0) * scale),
    width / 2,
    height / 2,
  );
  context.roundRect(-width / 2, -height / 2, width, height, radius);
}

function fillAndStroke(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
  scale: number,
): void {
  const shouldFill = layer.shapeEffect !== "hollow";
  if (shouldFill) applyCanvasShadow(context, layer, scale);
  if (shouldFill && (layer.fillMode === "linear" || layer.fillMode === "radial")) {
    context.fillStyle = createCanvasGradient(context, layer, width, height);
    context.fill();
  } else if (shouldFill && layer.fill && layer.fill !== "transparent") {
    context.fillStyle = layer.fill;
    context.fill();
  }
  clearCanvasShadow(context);
  const strokeWidth = Math.max(0, layer.strokeWidth ?? 0) * scale;
  if (strokeWidth > 0 && layer.stroke && layer.stroke !== "transparent") {
    context.lineWidth = strokeWidth;
    context.strokeStyle = layer.stroke;
    context.stroke();
  }
}

function applyCanvasShadow(context: CanvasRenderingContext2D, layer: EditorLayerV2, scale: number): void {
  if (layer.shapeEffect !== "shadow" && layer.shapeEffect !== "glow") return;
  const opacity = clamp(layer.shadowOpacity ?? (layer.shapeEffect === "glow" ? 0.7 : 0.35), 0, 1);
  const color = layer.shadowColor ?? (layer.shapeEffect === "glow" ? layer.fill : "#111827") ?? "#111827";
  context.shadowColor = gradientStopColor(color, opacity);
  context.shadowBlur = Math.max(0, layer.shadowBlur ?? 16) * scale;
  context.shadowOffsetX = (layer.shapeEffect === "glow" ? 0 : clamp(layer.shadowX ?? 0, -100, 100)) * scale;
  context.shadowOffsetY = (layer.shapeEffect === "glow" ? 0 : clamp(layer.shadowY ?? 4, -100, 100)) * scale;
}

function clearCanvasShadow(context: CanvasRenderingContext2D): void {
  context.shadowColor = "rgba(0, 0, 0, 0)";
  context.shadowBlur = 0;
  context.shadowOffsetX = 0;
  context.shadowOffsetY = 0;
}

function createCanvasGradient(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
): CanvasGradient {
  let gradient: CanvasGradient;
  if (layer.fillMode === "radial") {
    const centerX = -width / 2 + width * clamp((layer.gradientCenterX ?? 50) / 100, 0, 1);
    const centerY = -height / 2 + height * clamp((layer.gradientCenterY ?? 50) / 100, 0, 1);
    const radius = clamp((layer.gradientRadius ?? 70) / 100, 0.01, 2);
    context.save();
    context.translate(centerX, centerY);
    context.scale(width, height);
    gradient = context.createRadialGradient(0, 0, 0, 0, 0, radius);
    context.restore();
  } else {
    const radians = (clamp(layer.gradientAngle ?? 90, 0, 360) * Math.PI) / 180;
    const dx = Math.sin(radians);
    const dy = -Math.cos(radians);
    const extent = Math.abs(width * dx) + Math.abs(height * dy);
    gradient = context.createLinearGradient(
      -(dx * extent) / 2,
      -(dy * extent) / 2,
      (dx * extent) / 2,
      (dy * extent) / 2,
    );
  }
  for (const stop of normalizedGradientStops(layer)) {
    gradient.addColorStop(stop.offset, gradientStopColor(stop.color, stop.opacity));
  }
  return gradient;
}

function gradientStopColor(color: string, opacity: number): string {
  if (opacity >= 1) return color;
  const red = Number.parseInt(color.slice(1, 3), 16);
  const green = Number.parseInt(color.slice(3, 5), 16);
  const blue = Number.parseInt(color.slice(5, 7), 16);
  return `rgba(${red}, ${green}, ${blue}, ${clamp(opacity, 0, 1)})`;
}

function drawImageCover(
  context: CanvasRenderingContext2D,
  image: HTMLImageElement,
  width: number,
  height: number,
): void {
  const sourceRatio = image.naturalWidth / image.naturalHeight;
  const targetRatio = width / height;
  let sourceWidth = image.naturalWidth;
  let sourceHeight = image.naturalHeight;
  let sourceX = 0;
  let sourceY = 0;
  if (sourceRatio > targetRatio) {
    sourceWidth = image.naturalHeight * targetRatio;
    sourceX = (image.naturalWidth - sourceWidth) / 2;
  } else {
    sourceHeight = image.naturalWidth / targetRatio;
    sourceY = (image.naturalHeight - sourceHeight) / 2;
  }
  context.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, -width / 2, -height / 2, width, height);
}

function drawText(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
  scale: number,
): void {
  const fontSize = Math.max(1, (layer.fontSize ?? 16) * scale);
  const lineHeight = fontSize * 1.12;
  context.fillStyle = layer.fill || "#000000";
  context.font = `${layer.fontWeight ?? 400} ${fontSize}px ${layer.fontFamily || "Arial, sans-serif"}`;
  context.textBaseline = "middle";
  context.textAlign = layer.textAlign ?? "left";
  const lines = wrapText(context, layer.content ?? "", width);
  const maxLines = Math.max(1, Math.floor(height / lineHeight));
  const visibleLines = lines.slice(0, maxLines);
  const textX = context.textAlign === "center" ? 0 : context.textAlign === "right" ? width / 2 : -width / 2;
  const firstY = -((visibleLines.length - 1) * lineHeight) / 2;
  visibleLines.forEach((line, index) => context.fillText(line, textX, firstY + index * lineHeight, width));
}

function wrapText(context: CanvasRenderingContext2D, value: string, maxWidth: number): string[] {
  const result: string[] = [];
  for (const paragraph of value.split("\n")) {
    const tokens = paragraph.split(/\s+/).filter(Boolean);
    if (tokens.length === 0) {
      result.push("");
      continue;
    }
    let line = "";
    for (const token of tokens) {
      const candidate = line ? `${line} ${token}` : token;
      if (!line || context.measureText(candidate).width <= maxWidth) {
        line = candidate;
      } else {
        result.push(line);
        line = token;
      }
    }
    result.push(line);
  }
  return result;
}

function safeImageSource(value: string | undefined): string {
  if (!value) return "";
  if (/^https?:\/\//i.test(value) || value.startsWith("/")) return value;
  return /^data:image\/(png|jpeg|webp|gif|svg\+xml);base64,/i.test(value) ? value : "";
}

function safeFilePart(value: string): string {
  return value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

function mmToPixels(value: number): number {
  return Math.round((value / MM_PER_INCH) * PRINT_DPI);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
