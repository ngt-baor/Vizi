export const EDITOR_V2_SCHEMA_VERSION = 2 as const;

export type EditorSide = "front" | "back";
export type EditorLayerType = "text" | "rect" | "ellipse" | "image";
export type EditorShapeKind = "rectangle" | "rounded" | "ellipse" | "triangle" | "pentagon" | "hexagon" | "star" | "diamond" | "octagon" | "burst" | "pill";
export type EditorFillMode = "solid" | "linear" | "radial";
export type EditorShapeEffect = "none" | "shadow" | "hollow" | "glow";

export type EditorGradientStopV2 = {
  color: string;
  offset: number;
  opacity: number;
};

export type EditorLayerV2 = {
  id: string;
  name: string;
  type: EditorLayerType;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  opacity: number;
  visible: boolean;
  locked: boolean;
  content?: string;
  src?: string;
  iconSource?: "tabler" | "lucide" | "emoji";
  iconId?: string;
  assetId?: number;
  storageKey?: string;
  originalSrc?: string;
  processedSrc?: string;
  processedAssetId?: number;
  processedStorageKey?: string;
  pixelWidth?: number;
  pixelHeight?: number;
  extractedPalette?: string[];
  fill?: string;
  fillMode?: EditorFillMode;
  gradientAngle?: number;
  gradientCenterX?: number;
  gradientCenterY?: number;
  gradientRadius?: number;
  gradientStops?: EditorGradientStopV2[];
  stroke?: string;
  strokeWidth?: number;
  cornerRadius?: number;
  shapeKind?: EditorShapeKind;
  frameId?: string;
  shapeEffect?: EditorShapeEffect;
  shadowX?: number;
  shadowY?: number;
  shadowBlur?: number;
  shadowSpread?: number;
  shadowColor?: string;
  shadowOpacity?: number;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: number;
  textAlign?: "left" | "center" | "right";
};

export type EditorPageV2 = {
  id: EditorSide;
  name: string;
  background: string;
  layers: EditorLayerV2[];
};

export type EditorDocumentV2 = {
  schemaVersion: typeof EDITOR_V2_SCHEMA_VERSION;
  documentId: string;
  name: string;
  card: {
    widthMm: number;
    heightMm: number;
  };
  pages: Record<EditorSide, EditorPageV2>;
  updatedAt: string;
};

const starterFrontLayers: EditorLayerV2[] = [
  {
    id: "front-accent",
    name: "Accent",
    type: "rect",
    x: 8,
    y: 12,
    width: 4,
    height: 76,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    fill: "#0d766d",
    stroke: "#0d766d",
    strokeWidth: 0,
    cornerRadius: 2,
  },
  {
    id: "front-title",
    name: "Brand name",
    type: "text",
    x: 18,
    y: 24,
    width: 66,
    height: 20,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    content: "Vizi Studio",
    fill: "#151817",
    fontFamily: "Aptos, Segoe UI, sans-serif",
    fontSize: 28,
    fontWeight: 700,
    textAlign: "left",
  },
  {
    id: "front-subtitle",
    name: "Tagline",
    type: "text",
    x: 18,
    y: 51,
    width: 66,
    height: 12,
    rotation: 0,
    opacity: 0.72,
    visible: true,
    locked: false,
    content: "Business card editor V2",
    fill: "#333936",
    fontFamily: "Aptos, Segoe UI, sans-serif",
    fontSize: 13,
    fontWeight: 500,
    textAlign: "left",
  },
];

const starterBackLayers: EditorLayerV2[] = [
  {
    id: "back-title",
    name: "Back label",
    type: "text",
    x: 20,
    y: 40,
    width: 60,
    height: 20,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    content: "Vizi",
    fill: "#f7f4ec",
    fontFamily: "Aptos, Segoe UI, sans-serif",
    fontSize: 28,
    fontWeight: 700,
    textAlign: "center",
  },
];

export function createEditorDocumentV2(documentId: string): EditorDocumentV2 {
  return {
    schemaVersion: EDITOR_V2_SCHEMA_VERSION,
    documentId,
    name: `Business card ${documentId}`,
    card: { widthMm: 90, heightMm: 54 },
    pages: {
      front: {
        id: "front",
        name: "Front",
        background: "#f7f4ec",
        layers: structuredClone(starterFrontLayers),
      },
      back: {
        id: "back",
        name: "Back",
        background: "#123b36",
        layers: structuredClone(starterBackLayers),
      },
    },
    updatedAt: new Date().toISOString(),
  };
}

export function isUnmodifiedStarterDocumentV2(candidate: EditorDocumentV2): boolean {
  const starter = createEditorDocumentV2(candidate.documentId);
  return JSON.stringify(candidate.pages) === JSON.stringify(starter.pages);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isOptionalNumberInRange(value: unknown, min: number, max: number): boolean {
  return value === undefined
    || (typeof value === "number" && Number.isFinite(value) && value >= min && value <= max);
}

function isGradientStop(value: unknown): value is EditorGradientStopV2 {
  return isRecord(value)
    && typeof value.color === "string"
    && /^#[\da-f]{6}$/i.test(value.color)
    && typeof value.offset === "number"
    && Number.isFinite(value.offset)
    && value.offset >= 0
    && value.offset <= 1
    && typeof value.opacity === "number"
    && Number.isFinite(value.opacity)
    && value.opacity >= 0
    && value.opacity <= 1;
}

const shapeKinds: EditorShapeKind[] = [
  "rectangle", "rounded", "ellipse", "triangle", "pentagon", "hexagon", "star", "diamond", "octagon", "burst", "pill",
];

function isLayer(value: unknown): value is EditorLayerV2 {
  if (!isRecord(value)) {
    return false;
  }
  const type = value.type;
  const palette = value.extractedPalette;
  const hasValidPalette = palette === undefined || (Array.isArray(palette)
    && palette.length <= 8
    && palette.every((color) => typeof color === "string" && /^#[\da-f]{6}$/i.test(color)));
  const stops = value.gradientStops;
  const hasValidGradientStops = stops === undefined || (Array.isArray(stops)
    && stops.length >= 2
    && stops.length <= 4
    && stops.every(isGradientStop));
  return typeof value.id === "string"
    && typeof value.name === "string"
    && (type === "text" || type === "rect" || type === "ellipse" || type === "image")
    && ["x", "y", "width", "height", "rotation", "opacity"].every(
      (field) => typeof value[field] === "number" && Number.isFinite(value[field]),
    )
    && typeof value.visible === "boolean"
    && typeof value.locked === "boolean"
    && hasValidPalette
    && (value.fillMode === undefined || value.fillMode === "solid" || value.fillMode === "linear" || value.fillMode === "radial")
    && isOptionalNumberInRange(value.gradientAngle, 0, 360)
    && isOptionalNumberInRange(value.gradientCenterX, 0, 100)
    && isOptionalNumberInRange(value.gradientCenterY, 0, 100)
    && isOptionalNumberInRange(value.gradientRadius, 1, 200)
    && hasValidGradientStops
    && (value.shapeKind === undefined || (typeof value.shapeKind === "string" && shapeKinds.includes(value.shapeKind as EditorShapeKind)))
    && (value.frameId === undefined || typeof value.frameId === "string")
    && (value.shapeEffect === undefined || value.shapeEffect === "none" || value.shapeEffect === "shadow" || value.shapeEffect === "hollow" || value.shapeEffect === "glow")
    && isOptionalNumberInRange(value.shadowX, -100, 100)
    && isOptionalNumberInRange(value.shadowY, -100, 100)
    && isOptionalNumberInRange(value.shadowBlur, 0, 100)
    && isOptionalNumberInRange(value.shadowSpread, 0, 50)
    && (value.shadowColor === undefined || (typeof value.shadowColor === "string" && /^#[\da-f]{6}$/i.test(value.shadowColor)))
    && isOptionalNumberInRange(value.shadowOpacity, 0, 1);
}

function isPage(value: unknown, side: EditorSide): value is EditorPageV2 {
  return isRecord(value)
    && value.id === side
    && typeof value.name === "string"
    && typeof value.background === "string"
    && Array.isArray(value.layers)
    && value.layers.every(isLayer);
}

export function readEditorDocumentV2(value: string | unknown): EditorDocumentV2 | null {
  try {
    const document = typeof value === "string" ? JSON.parse(value) as unknown : value;
    if (!isRecord(document)
      || document.schemaVersion !== EDITOR_V2_SCHEMA_VERSION
      || typeof document.documentId !== "string"
      || typeof document.name !== "string"
      || typeof document.updatedAt !== "string"
      || !isRecord(document.card)
      || typeof document.card.widthMm !== "number"
      || typeof document.card.heightMm !== "number"
      || !isRecord(document.pages)
      || !isPage(document.pages.front, "front")
      || !isPage(document.pages.back, "back")) {
      return null;
    }
    return document as EditorDocumentV2;
  } catch {
    return null;
  }
}

export type LegacyDocumentMigrationOptions = {
  documentId: string;
  name: string;
  widthMm: number;
  heightMm: number;
  updatedAt?: string;
};

function legacyString(value: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const candidate = value[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate;
  }
  return undefined;
}

function legacyNumber(value: Record<string, unknown>, keys: string[], fallback: number): number {
  for (const key of keys) {
    const candidate = value[key];
    const parsed = typeof candidate === "number" ? candidate : typeof candidate === "string" ? Number(candidate) : NaN;
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function legacyColor(value: Record<string, unknown>, keys: string[], fallback: string): string {
  return legacyString(value, keys) ?? fallback;
}

function clampLegacy(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function legacySide(value: Record<string, unknown>): EditorSide {
  const side = legacyString(value, ["page", "side"])?.toLowerCase();
  return side === "back" ? "back" : "front";
}

type LegacyShapeKind = "rectangle" | "rounded" | "ellipse" | "triangle" | "pentagon" | "hexagon" | "star" | "diamond" | "octagon" | "burst" | "pill";

function legacyShapeKind(value: Record<string, unknown>): LegacyShapeKind | undefined {
  const raw = legacyString(value, ["shapeKind", "shape", "variant"])?.toLowerCase();
  if (!raw) return undefined;
  const aliases: Record<string, LegacyShapeKind> = {
    rectangle: "rectangle",
    rect: "rectangle",
    rounded: "rounded",
    "rounded-rectangle": "rounded",
    ellipse: "ellipse",
    circle: "ellipse",
    oval: "ellipse",
    triangle: "triangle",
    pentagon: "pentagon",
    hexagon: "hexagon",
    star: "star",
    diamond: "diamond",
    octagon: "octagon",
    burst: "burst",
    pill: "pill",
  };
  return aliases[raw];
}

function legacyLayerType(value: Record<string, unknown>): EditorLayerType {
  const declared = legacyString(value, ["type"])?.toLowerCase();
  if (declared === "image" || declared === "photo" || legacyString(value, ["src", "url", "imageUrl"])) return "image";
  if (declared === "ellipse" || declared === "circle") return "ellipse";
  if (declared === "text" || declared === "icon") return "text";
  if (declared === "rect" || declared === "rectangle" || declared === "shape" || declared === "frame") {
    return legacyShapeKind(value) === "ellipse" ? "ellipse" : "rect";
  }
  return legacyString(value, ["text", "content", "value", "label"]) ? "text" : "rect";
}

function legacyBackground(value: Record<string, unknown>, side: EditorSide): string {
  const directKeys = side === "front"
    ? ["background", "backgroundColor", "fill"]
    : ["backBackground", "backBackgroundColor", "backColor"];
  const direct = legacyString(value, directKeys);
  if (direct) return direct;

  const sides = value.sides;
  if (Array.isArray(sides)) {
    const sideEntry = sides.find((entry) => isRecord(entry) && legacySide(entry) === side);
    if (isRecord(sideEntry)) return legacyColor(sideEntry, ["background", "backgroundColor", "fill"], "#fffdf8");
  } else if (isRecord(sides) && isRecord(sides[side])) {
    return legacyColor(sides[side], ["background", "backgroundColor", "fill"], "#fffdf8");
  }
  return "#fffdf8";
}

function uniqueLegacyId(value: string, used: Set<string>): string {
  const base = value || "layer";
  let candidate = base;
  let suffix = 2;
  while (used.has(candidate)) candidate = `${base}-${suffix++}`;
  used.add(candidate);
  return candidate;
}

function migrateLegacyLayer(value: Record<string, unknown>, index: number, usedIds: Set<string>): EditorLayerV2 {
  const type = legacyLayerType(value);
  const shapeKind = legacyShapeKind(value);
  const id = uniqueLegacyId(legacyString(value, ["id", "key"]) ?? `legacy-layer-${index + 1}`, usedIds);
  const isText = type === "text";
  const isImage = type === "image";
  const defaultWidth = isText ? 45 : isImage ? 32 : 30;
  const defaultHeight = isText ? 16 : isImage ? 26 : 24;
  const layer: EditorLayerV2 = {
    id,
    name: legacyString(value, ["name", "label"]) ?? `${isText ? "Text" : isImage ? "Image" : "Shape"} ${index + 1}`,
    type,
    x: legacyNumber(value, ["x", "left"], 8),
    y: legacyNumber(value, ["y", "top"], 8),
    width: Math.max(0.1, legacyNumber(value, ["width", "w"], defaultWidth)),
    height: Math.max(0.1, legacyNumber(value, ["height", "h"], defaultHeight)),
    rotation: legacyNumber(value, ["rotation", "angle"], 0),
    opacity: clampLegacy(legacyNumber(value, ["opacity", "alpha"], 1), 0, 1),
    visible: value.visible !== false && value.hidden !== true,
    locked: value.locked === true,
    fill: legacyColor(value, ["fill", "color", "background"], isText ? "#151817" : "#ffffff"),
    stroke: legacyColor(value, ["stroke", "borderColor"], "transparent"),
    strokeWidth: Math.max(0, legacyNumber(value, ["strokeWidth", "borderWidth"], 0)),
    cornerRadius: Math.max(0, legacyNumber(value, ["cornerRadius", "radius"], 0)),
  };

  if (shapeKind) Object.assign(layer, { shapeKind });
  if (isText) {
    layer.content = legacyString(value, ["content", "text", "value", "label"]) ?? "";
    layer.fontFamily = legacyString(value, ["fontFamily", "font"]) ?? "system-ui, Segoe UI, Arial, sans-serif";
    layer.fontSize = Math.max(1, legacyNumber(value, ["fontSize", "size"], 14));
    layer.fontWeight = Math.max(100, legacyNumber(value, ["fontWeight", "weight"], 600));
    const align = legacyString(value, ["textAlign", "align", "alignment"])?.toLowerCase();
    layer.textAlign = align === "center" || align === "right" ? align : "left";
  }
  const src = legacyString(value, ["src", "url", "imageUrl"]);
  if (src) layer.src = src;
  const iconSource = legacyString(value, ["iconSource"]);
  if (iconSource === "tabler" || iconSource === "lucide" || iconSource === "emoji") layer.iconSource = iconSource;
  const iconId = legacyString(value, ["iconId"]);
  if (iconId) layer.iconId = iconId;
  for (const field of ["assetId", "processedAssetId", "pixelWidth", "pixelHeight"] as const) {
    const number = legacyNumber(value, [field], NaN);
    if (Number.isFinite(number)) layer[field] = number;
  }
  for (const field of ["storageKey", "originalSrc", "processedSrc", "processedStorageKey"] as const) {
    const string = legacyString(value, [field]);
    if (string) layer[field] = string;
  }
  return layer;
}

export function migrateLegacyCanvasToV2(
  value: string | unknown,
  options: LegacyDocumentMigrationOptions,
): EditorDocumentV2 | null {
  try {
    const canvas = typeof value === "string" ? JSON.parse(value) as unknown : value;
    if (!isRecord(canvas) || !Array.isArray(canvas.layers)) return null;
    const usedIds = new Set<string>();
    const pages: Record<EditorSide, EditorLayerV2[]> = { front: [], back: [] };
    canvas.layers.forEach((candidate, index) => {
      if (!isRecord(candidate)) return;
      const side = legacySide(candidate);
      pages[side].push(migrateLegacyLayer(candidate, index, usedIds));
    });
    const widthMm = Number.isFinite(options.widthMm) && options.widthMm > 0 ? options.widthMm : 90;
    const heightMm = Number.isFinite(options.heightMm) && options.heightMm > 0 ? options.heightMm : 54;
    return {
      schemaVersion: EDITOR_V2_SCHEMA_VERSION,
      documentId: options.documentId,
      name: options.name || `Business card ${options.documentId}`,
      card: { widthMm, heightMm },
      pages: {
        front: { id: "front", name: "Front", background: legacyBackground(canvas, "front"), layers: pages.front },
        back: { id: "back", name: "Back", background: legacyBackground(canvas, "back"), layers: pages.back },
      },
      updatedAt: options.updatedAt ?? new Date().toISOString(),
    };
  } catch {
    return null;
  }
}
