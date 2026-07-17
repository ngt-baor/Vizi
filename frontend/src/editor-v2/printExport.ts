import type { EditorDocumentV2, EditorLayerV2, EditorPageV2, EditorSide } from "./document";

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
    if (!source) throw new Error(`Image "${layer.name}" has no safe source for print export.`);
    return source;
  }))];
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
  context.beginPath();
  context.rect(-width / 2, -height / 2, width, height);
  context.clip();

  if (layer.type === "text") {
    drawText(context, layer, width, height, scale);
  } else if (layer.type === "image") {
    const source = safeImageSource(layer.src);
    const image = source ? images.get(source) : undefined;
    if (image) drawImageCover(context, image, width, height);
  } else if (layer.type === "ellipse") {
    drawEllipse(context, layer, width, height, scale);
  } else {
    drawRectangle(context, layer, width, height, scale);
  }
  context.restore();
}

function drawRectangle(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
  scale: number,
): void {
  const radius = Math.min(Math.max(0, (layer.cornerRadius ?? 0) * scale), width / 2, height / 2);
  context.beginPath();
  context.roundRect(-width / 2, -height / 2, width, height, radius);
  fillAndStroke(context, layer, scale);
}

function drawEllipse(
  context: CanvasRenderingContext2D,
  layer: EditorLayerV2,
  width: number,
  height: number,
  scale: number,
): void {
  context.beginPath();
  context.ellipse(0, 0, width / 2, height / 2, 0, 0, Math.PI * 2);
  fillAndStroke(context, layer, scale);
}

function fillAndStroke(context: CanvasRenderingContext2D, layer: EditorLayerV2, scale: number): void {
  if (layer.fill && layer.fill !== "transparent") {
    context.fillStyle = layer.fill;
    context.fill();
  }
  const strokeWidth = Math.max(0, layer.strokeWidth ?? 0) * scale;
  if (strokeWidth > 0 && layer.stroke && layer.stroke !== "transparent") {
    context.lineWidth = strokeWidth;
    context.strokeStyle = layer.stroke;
    context.stroke();
  }
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
  return /^data:image\/(png|jpeg|webp|gif);base64,/i.test(value) ? value : "";
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
