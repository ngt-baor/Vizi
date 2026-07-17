export const EDITOR_V2_SCHEMA_VERSION = 2 as const;

export type EditorSide = "front" | "back";
export type EditorLayerType = "text" | "rect" | "ellipse" | "image";

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
  assetId?: number;
  storageKey?: string;
  originalSrc?: string;
  processedSrc?: string;
  processedAssetId?: number;
  processedStorageKey?: string;
  pixelWidth?: number;
  pixelHeight?: number;
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  cornerRadius?: number;
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isLayer(value: unknown): value is EditorLayerV2 {
  if (!isRecord(value)) {
    return false;
  }
  const type = value.type;
  return typeof value.id === "string"
    && typeof value.name === "string"
    && (type === "text" || type === "rect" || type === "ellipse" || type === "image")
    && ["x", "y", "width", "height", "rotation", "opacity"].every(
      (field) => typeof value[field] === "number" && Number.isFinite(value[field]),
    )
    && typeof value.visible === "boolean"
    && typeof value.locked === "boolean";
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
