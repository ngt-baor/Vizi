<script setup lang="ts">
import {
  AlignCenterHorizontal,
  AlignEndHorizontal,
  AlignStartHorizontal,
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Bold,
  ChevronDown,
  Circle,
  Columns3,
  CircleAlert,
  CircleCheck,
  Copy,
  Diamond,
  Download,
  Eye,
  EyeOff,
  GripVertical,
  Grid2x2,
  Grid3x2,
  Grid3x3,
  Hexagon,
  ImageIcon,
  Keyboard,
  LayoutGrid,
  Layers3,
  Link2,
  LockKeyhole,
  Minus,
  Move,
  MousePointer2,
  Octagon,
  Palette,
  Pentagon,
  PanelLeft,
  PanelRight,
  PanelsTopLeft,
  PanelTop,
  Play,
  Plus,
  Redo2,
  RectangleHorizontal,
  RotateCw,
  Ruler,
  Save,
  Search,
  Sparkles,
  Shapes,
  ShoppingCart,
  ShieldCheck,
  Square,
  Star,
  Trash2,
  Triangle,
  Type,
  Undo2,
  Upload,
  X,
} from "@lucide/vue";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { Component, CSSProperties } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import EditorCanvasV2 from "../editor-v2/EditorCanvasV2.vue";
import CanvasPreview from "../components/CanvasPreview.vue";
import {
  apiBaseUrl,
  getDesign,
  preflightDesign,
  removeBackgroundImageAsset,

  searchStock,

  type PreflightIssue,
  type StockApiAsset,
  type PreflightReport,
  updateDesign,
} from "../api";
import {
  createEditorDocumentV2,
  readEditorDocumentV2,
  type EditorDocumentV2,
  type EditorFillMode,
  type EditorGradientStopV2,
  type EditorLayerV2,
  type EditorLayerType,
  type EditorShapeKind,
  type EditorShapeEffect,
  type EditorSide,
} from "../editor-v2/document";
import { createDefaultGradientStops, shapeFillBackground } from "../editor-v2/shapeAppearance";
import {
  createPrintPdf,
  downloadPrintPdf,
  getPrintExportSpec,
} from "../editor-v2/printExport";
import { extractImagePalette } from "../editor-v2/imagePalette";
import { localFonts } from "../generated/localFonts";
import { stockAssets, type StockAsset, type StockKind } from "../editor-v2/stockAssets";
import { localIconAsset, localIconCounts, searchLocalIcons, type LocalIcon, type LocalIconSource } from "../editor-v2/localIcons";

type EditorTool = "select" | "text" | "rect" | "ellipse" | "image";
type EditorLayerAction = "duplicate" | "toggle-lock" | "bring-forward" | "send-backward" | "delete";
type SidebarPanel = "elements" | "text" | "uploads" | "stock" | "icon" | "layers";
type LocalIconFilter = LocalIconSource | "all";
type StockView = "browse" | "favorites" | "recent";
type StockKindFilter = "all" | StockKind;
type TextPreset = "title" | "subtitle" | "body";
type ShapePreset = EditorShapeKind;
type FramePreset = "frame" | "circle-frame" | "rounded-frame" | "2-photos" | "3-photos" | "4-photos" | "6-photos" | "9-photos" | "feature-left" | "feature-right" | "feature-top" | "hero-mosaic";
type FrameCell = Pick<EditorLayerV2, "x" | "y" | "width" | "height"> & { shapeKind: EditorShapeKind };
type GeometryField = "x" | "y" | "width" | "height";
type NumericLayerField = "fontSize" | "rotation" | "strokeWidth" | "cornerRadius";
type GradientNumberField = "gradientAngle" | "gradientCenterX" | "gradientCenterY" | "gradientRadius";
type EffectNumberField = "shadowX" | "shadowY" | "shadowBlur" | "shadowOpacity";

type IconItem = {
  id: string;
  label: string;
  icon: Component;
};

type FrameItem = IconItem & { preset: FramePreset };
type FontOption = {
  label: string;
  value: string;
  kind: string;
  searchValue: string;
};

type UploadedMedia = {
  id: string;
  name: string;
  src: string;
  pixelWidth?: number;
  pixelHeight?: number;
};

type CanvasDropPoint = {
  centerX: number;
  centerY: number;
};

type CanvasOverlayBounds = {
  left: number;
  top: number;
  width: number;
  height: number;
};

type RulerTick = {
  value: number;
  position: number;
  major: boolean;
};

type LibraryDragItem =
  | { kind: "text"; preset: TextPreset }
  | { kind: "shape"; preset: ShapePreset }
  | { kind: "frame"; preset: FramePreset }
  | { kind: "stock"; id: string }
  | { kind: "icon"; id: string }
  | { kind: "media"; id: string };

type ShortcutGroup = {
  label: string;
  items: Array<{ label: string; keys: string[] }>;
};

const route = useRoute();
const router = useRouter();
const documentId = computed(() => String(route.params.designId ?? "new"));
const storageKey = computed(() => "vizi.editor.v2." + documentId.value);
const document = ref(createEditorDocumentV2(documentId.value));
const historyPast = ref<EditorDocumentV2[]>([]);
const historyFuture = ref<EditorDocumentV2[]>([]);
const internalClipboard = ref<EditorLayerV2 | null>(null);
const pasteCount = ref(0);
let historySnapshot = JSON.stringify(document.value);
const activeSide = ref<EditorSide>("front");
const activePanel = ref<SidebarPanel>("elements");
const activeTool = ref<EditorTool>("select");
const selectedLayerId = ref<string | null>("front-title");
const zoom = ref(100);
const panX = ref(0);
const panY = ref(0);
const canvasFrame = ref<HTMLElement | null>(null);
const zoomControl = ref<HTMLElement | null>(null);
const zoomMenuOpen = ref(false);
const showPrintGuides = ref(true);
const showRulers = ref(true);
const showGrid = ref(false);
const canvasOverlayBounds = ref<CanvasOverlayBounds | null>(null);
const preflightReport = ref<PreflightReport | null>(null);
const preflightLoading = ref(false);
const checkoutLoading = ref(false);
const exportState = ref<"idle" | "exporting" | "downloaded" | "error">("idle");
const exportError = ref("");
const preflightError = ref("");
const saveState = ref<"idle" | "saved" | "dirty" | "saving" | "loading" | "error">("idle");
const saveError = ref("");
const imageInput = ref<HTMLInputElement | null>(null);
const imageTargetLayerId = ref<string | null>(null);
const draggedLayerId = ref<string | null>(null);
const imageFileByLayer = new Map<string, File>();
const mediaFileById = new Map<string, File>();
const uploadedMedia = ref<UploadedMedia[]>([]);
const mediaImportError = ref("");
const libraryDragItem = ref<LibraryDragItem | null>(null);
const canvasDropActive = ref(false);
const mediaDropTarget = ref<"library" | "inspector" | null>(null);
const shortcutsOpen = ref(false);
const shortcutQuery = ref("");
const shortcutSearchInput = ref<HTMLInputElement | null>(null);
const previewOpen = ref(false);
const previewSide = ref<EditorSide>("front");
const backgroundRemovingLayerId = ref<string | null>(null);
const backgroundRemovalError = ref("");
const paletteExtractingLayerId = ref<string | null>(null);
const paletteExtractionError = ref("");
const iconQuery = ref("");
const iconSource = ref<LocalIconFilter>("all");
const localIconResults = computed(() => searchLocalIcons(iconQuery.value, iconSource.value, 60));
const stockQuery = ref("");
const stockView = ref<StockView>("browse");
const stockKindFilter = ref<StockKindFilter>("all");
const stockCollectionFilter = ref("All");
const stockFavorites = ref<string[]>(readStoredStockIds("vizi.editor.stock.favorites"));
const stockRecent = ref<string[]>(readStoredStockIds("vizi.editor.stock.recent"));
const remoteStockAssets = ref<StockAsset[]>([]);
const stockRemoteReady = ref(false);
const stockLoading = ref(false);
const stockError = ref("");
const stockPage = ref(1);
const stockHasMore = ref(false);
let stockRequestId = 0;
let stockSearchTimer: number | null = null;
let canvasOverlayFrame: number | null = null;
let canvasOverlayTimer: number | null = null;
let canvasResizeObserver: ResizeObserver | null = null;
const fontSearch = ref("");
const fontResultLimit = ref(40);
const backendDesignId = computed(() => {
  const value = Number(documentId.value);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
});
const statusText = computed(() => {
  if (exportState.value === "exporting") return "Preparing print PDF...";
  if (exportState.value === "downloaded") return "Print PDF downloaded";
  if (exportState.value === "error") return exportError.value || "Print export failed";
  if (saveState.value === "loading") return "Loading draft...";
  if (saveState.value === "saving") return "Saving draft...";
  if (saveState.value === "error") return saveError.value || "Draft sync failed";
  if (saveState.value === "saved") return backendDesignId.value ? "Saved to draft" : "Saved locally";
  if (saveState.value === "dirty") return "Unsaved changes";
  return backendDesignId.value ? "Draft loaded" : "All changes saved";
});

function draftErrorMessage(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message.trim() : "";
  if (/failed to fetch|networkerror|load failed/i.test(message)) {
    return "Backend offline - local changes preserved";
  }
  return message || fallback;
}
const printExportSpec = computed(() => getPrintExportSpec(document.value));
const preflightErrorCount = computed(() => preflightReport.value?.issues.filter((issue) => issue.level === "ERROR").length ?? 0);
const preflightWarningCount = computed(() => preflightReport.value?.issues.filter((issue) => issue.level === "WARNING").length ?? 0);

const sides: EditorSide[] = ["front", "back"];
const VIZI_DRAG_MIME = "application/x-vizi-editor-item";
const IMAGE_FILE_PATTERN = /^image\/(png|jpeg|webp|gif)$/;
const MAX_IMAGE_FILE_BYTES = 5 * 1024 * 1024;
const MIN_ZOOM = 5;
const MAX_ZOOM = 800;
const zoomPresets = [5, 25, 50, 100, 200, 400, 800];
const RULER_STEP_MM = 5;

function createRulerTicks(lengthMm: number): RulerTick[] {
  const safeLength = Math.max(1, lengthMm);
  const ticks: RulerTick[] = [];
  for (let value = 0; value <= safeLength; value += RULER_STEP_MM) {
    ticks.push({ value, position: value / safeLength * 100, major: value % 10 === 0 });
  }
  const lastValue = ticks[ticks.length - 1]?.value ?? 0;
  if (Math.abs(lastValue - safeLength) > 0.01) {
    ticks.push({ value: safeLength, position: 100, major: true });
  }
  return ticks;
}

const horizontalRulerTicks = computed(() => createRulerTicks(document.value.card.widthMm));
const verticalRulerTicks = computed(() => createRulerTicks(document.value.card.heightMm));
const topRulerStyle = computed<CSSProperties>(() => {
  const bounds = canvasOverlayBounds.value;
  return bounds
    ? { left: bounds.left + "px", top: bounds.top + "px", width: bounds.width + "px" }
    : { visibility: "hidden" };
});
const leftRulerStyle = computed<CSSProperties>(() => {
  const bounds = canvasOverlayBounds.value;
  return bounds
    ? { left: bounds.left + "px", top: bounds.top + "px", height: bounds.height + "px" }
    : { visibility: "hidden" };
});
const canvasGridStyle = computed<CSSProperties>(() => {
  const bounds = canvasOverlayBounds.value;
  const widthMm = Math.max(1, document.value.card.widthMm);
  const heightMm = Math.max(1, document.value.card.heightMm);
  const minorX = RULER_STEP_MM / widthMm * 100;
  const minorY = RULER_STEP_MM / heightMm * 100;
  const majorX = RULER_STEP_MM * 2 / widthMm * 100;
  const majorY = RULER_STEP_MM * 2 / heightMm * 100;
  return bounds
    ? {
        left: bounds.left + "px",
        top: bounds.top + "px",
        width: bounds.width + "px",
        height: bounds.height + "px",
        backgroundSize: minorX + "% 100%, 100% " + minorY + "%, " + majorX + "% 100%, 100% " + majorY + "%",
      }
    : { visibility: "hidden" };
});
const palettes = ["#15161b", "#ffffff", "#b4367d", "#f4c46d", "#0d766d", "#5b83d4"];
const systemFontOptions = [
  { label: "Aptos", value: "Aptos, Segoe UI, sans-serif", kind: "Sans-serif" },
  { label: "Arial", value: "Arial, sans-serif", kind: "Sans-serif" },
  { label: "Georgia", value: "Georgia, serif", kind: "Serif" },
];
const fontOptions: FontOption[] = (() => {
  const seen = new Set<string>();
  return [
    ...systemFontOptions,
    ...localFonts.map((font) => ({
      label: font.family,
      value: font.family,
      kind: font.format.toUpperCase(),
    })),
  ].filter((font) => {
    const key = normalizeFontSearch(font.label);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).map((font) => ({ ...font, searchValue: normalizeFontSearch(font.label) }));
})();
const textPresetOptions: Array<{
  id: TextPreset;
  label: string;
  sample: string;
  fontSize: number;
  fontWeight: number;
}> = [
  { id: "title", label: "Add a title", sample: "Your name", fontSize: 28, fontWeight: 700 },
  { id: "subtitle", label: "Add a subtitle", sample: "Job title", fontSize: 16, fontWeight: 600 },
  { id: "body", label: "Add body text", sample: "Contact details", fontSize: 11, fontWeight: 400 },
];

const shortcutGroups: ShortcutGroup[] = [
  {
    label: "Edit",
    items: [
      { label: "Undo", keys: ["Ctrl/Cmd+Z"] },
      { label: "Redo", keys: ["Ctrl/Cmd+Y", "Ctrl/Cmd+Shift+Z"] },
      { label: "Save draft", keys: ["Ctrl/Cmd+S"] },
      { label: "Copy selected layer", keys: ["Ctrl/Cmd+C"] },
      { label: "Paste layer", keys: ["Ctrl/Cmd+V"] },
      { label: "Duplicate selected layer", keys: ["Ctrl/Cmd+D"] },
      { label: "Delete selected layer", keys: ["Delete", "Backspace"] },
      { label: "Clear selection / close dialog", keys: ["Esc"] },
    ],
  },
  {
    label: "Move & arrange",
    items: [
      { label: "Nudge selected layer", keys: ["Arrow keys"] },
      { label: "Nudge faster", keys: ["Shift+Arrow"] },
      { label: "Bring layer forward", keys: ["]"] },
      { label: "Send layer backward", keys: ["["] },
    ],
  },
  {
    label: "Tools",
    items: [
      { label: "Select tool", keys: ["V"] },
      { label: "Text tool", keys: ["T"] },
      { label: "Rectangle tool", keys: ["R"] },
      { label: "Ellipse tool", keys: ["O"] },
      { label: "Open shortcut reference", keys: ["?"] },
    ],
  },
  {
    label: "Zoom",
    items: [
      { label: "Zoom in", keys: ["+", "Ctrl/Cmd++"] },
      { label: "Zoom out", keys: ["-", "Ctrl/Cmd+-"] },
      { label: "Actual size", keys: ["1"] },
      { label: "Zoom to selection", keys: ["2"] },
      { label: "Fit card", keys: ["Ctrl/Cmd+0"] },
    ],
  },
];

const activePage = computed(() => document.value.pages[activeSide.value]);
const selectedLayer = computed<EditorLayerV2 | null>(() => (
  activePage.value.layers.find((layer) => layer.id === selectedLayerId.value) ?? null
));
const mediaItems = computed<UploadedMedia[]>(() => {
  const bySource = new Map<string, UploadedMedia>();
  uploadedMedia.value.forEach((media) => bySource.set(media.src, media));
  sides.forEach((side) => {
    document.value.pages[side].layers.forEach((layer) => {
      const src = layer.type === "image" ? safeImageSource(layer.src) : "";
      if (!src || bySource.has(src)) return;
      bySource.set(src, {
        id: "layer-" + layer.id,
        name: layer.name,
        src,
        pixelWidth: layer.pixelWidth,
        pixelHeight: layer.pixelHeight,
      });
    });
  });
  return [...bySource.values()];
});
const documentPalette = computed(() => [...new Set(sides.flatMap((side) =>
  document.value.pages[side].layers.flatMap((layer) => layer.extractedPalette ?? []),
))]);
const availablePalettes = computed(() => [...new Set([
  ...documentPalette.value,
  ...palettes,
])]);
const filteredFontOptions = computed(() => {
  const query = normalizeFontSearch(fontSearch.value);
  return query ? fontOptions.filter((font) => font.searchValue.includes(query)) : fontOptions;
});
const visibleFontOptions = computed(() => filteredFontOptions.value.slice(0, fontResultLimit.value));
const filteredShortcutGroups = computed<ShortcutGroup[]>(() => {
  const query = normalizeFontSearch(shortcutQuery.value);
  if (!query) return shortcutGroups;
  return shortcutGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        normalizeFontSearch([item.label, ...item.keys].join(" ")).includes(query)),
    }))
    .filter((group) => group.items.length > 0);
});
const hiddenFontCount = computed(() => Math.max(0, filteredFontOptions.value.length - visibleFontOptions.value.length));
const stockBrowseAssets = computed<StockAsset[]>(() => (
  stockRemoteReady.value ? remoteStockAssets.value : stockAssets
));

const stockAllAssets = computed<StockAsset[]>(() => {
  const byId = new Map(stockAssets.map((asset) => [asset.id, asset]));
  remoteStockAssets.value.forEach((asset) => byId.set(asset.id, asset));
  return [...byId.values()];
});

const stockCollections = computed(() => [
  "All",
  ...new Set(stockBrowseAssets.value.map((asset) => asset.collection)),
]);

const stockResults = computed<StockAsset[]>(() => {
  const query = normalizeFontSearch(stockQuery.value);
  const ids = stockView.value === "favorites"
    ? stockFavorites.value
    : stockView.value === "recent"
      ? stockRecent.value
      : null;
  const catalog = ids ? stockAllAssets.value : stockBrowseAssets.value;
  const source = ids
    ? ids.map((id) => catalog.find((asset) => asset.id === id)).filter((asset): asset is StockAsset => Boolean(asset))
    : catalog;

  return source.filter((asset) => {
    const matchesKind = stockKindFilter.value === "all" || asset.kind === stockKindFilter.value;
    const matchesCollection = stockCollectionFilter.value === "All" || asset.collection === stockCollectionFilter.value;
    const haystack = normalizeFontSearch([asset.title, asset.collection, ...asset.tags].join(" "));
    return matchesKind && matchesCollection && (!query || haystack.includes(query));
  });
});
const canUndo = computed(() => historyPast.value.length > 0);
const canRedo = computed(() => historyFuture.value.length > 0);
const selectedShapePreset = computed<ShapePreset | null>(() => {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return null;
  if (layer.shapeKind) return layer.shapeKind;
  if (layer.type === "ellipse") return "ellipse";
  return (layer.cornerRadius ?? 0) > 0 ? "rounded" : "rectangle";
});
const selectedShapeFillMode = computed<EditorFillMode>(() => {
  const layer = selectedLayer.value;
  return layer && (layer.type === "rect" || layer.type === "ellipse")
    && (layer.fillMode === "linear" || layer.fillMode === "radial")
    ? layer.fillMode
    : "solid";
});
const selectedShapeEffect = computed<EditorShapeEffect>(() => {
  const layer = selectedLayer.value;
  return layer && (layer.type === "rect" || layer.type === "ellipse")
    && (layer.shapeEffect === "shadow" || layer.shapeEffect === "hollow" || layer.shapeEffect === "glow")
    ? layer.shapeEffect
    : "none";
});
const selectedGradientStops = computed<EditorGradientStopV2[]>(() => {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return [];
  return layer.gradientStops && layer.gradientStops.length >= 2
    ? layer.gradientStops
    : createDefaultGradientStops(layer.fill);
});
const inspectorTitle = computed(() => {
  if (!selectedLayer.value) return "Page";
  if (selectedLayer.value.type === "text") return "Text";
  if (selectedLayer.value.type === "image") return "Image";
  return "Shape";
});
const panelTitle = computed(() => ({
  elements: "Elements",
  text: "Text",
  uploads: "Uploads",
  stock: "Stock",
  icon: "Icon",
  layers: "Layers",
}[activePanel.value]));

const sidebarItems: Array<{ id: SidebarPanel; label: string; icon: Component }> = [
  { id: "elements", label: "Elements", icon: Shapes },
  { id: "text", label: "Text", icon: Type },
  { id: "uploads", label: "Uploads", icon: Upload },
  { id: "stock", label: "Stock", icon: ImageIcon },
  { id: "icon", label: "Icon", icon: Grid2x2 },
  { id: "layers", label: "Layers", icon: Layers3 },
];

const localIconSourceOptions: Array<{ value: LocalIconFilter; label: string }> = [
  { value: "all", label: "All" },
  { value: "tabler", label: "Tabler" },
  { value: "lucide", label: "Lucide" },
  { value: "emoji", label: "Emoji" },
];

const stockKindOptions: Array<{ value: StockKindFilter; label: string }> = [
  { value: "all", label: "All" },
  { value: "photo", label: "Photos" },
  { value: "illustration", label: "Illustrations" },
  { value: "background", label: "Backgrounds" },
];

const shapeItems: Array<IconItem & { preset: ShapePreset }> = [
  { id: "rectangle", label: "Rectangle", icon: Square, preset: "rectangle" },
  { id: "rounded", label: "Rounded", icon: Square, preset: "rounded" },
  { id: "ellipse", label: "Ellipse", icon: Circle, preset: "ellipse" },
  { id: "triangle", label: "Triangle", icon: Triangle, preset: "triangle" },
  { id: "pentagon", label: "Pentagon", icon: Pentagon, preset: "pentagon" },
  { id: "hexagon", label: "Hexagon", icon: Hexagon, preset: "hexagon" },
  { id: "star", label: "Star", icon: Star, preset: "star" },
  { id: "diamond", label: "Diamond", icon: Diamond, preset: "diamond" },
  { id: "octagon", label: "Octagon", icon: Octagon, preset: "octagon" },
  { id: "burst", label: "Burst", icon: Sparkles, preset: "burst" },
  { id: "pill", label: "Pill", icon: RectangleHorizontal, preset: "pill" },
];

function gridCells(columns: number, rows: number): FrameCell[] {
  const gap = 2;
  const width = (100 - gap * (columns + 1)) / columns;
  const height = (100 - gap * (rows + 1)) / rows;
  return Array.from({ length: columns * rows }, (_, index) => ({
    x: gap + (index % columns) * (width + gap),
    y: gap + Math.floor(index / columns) * (height + gap),
    width,
    height,
    shapeKind: "rectangle",
  }));
}

const frameSpecs: Record<FramePreset, { label: string; cells: FrameCell[] }> = {
  frame: { label: "Frame", cells: [{ x: 12, y: 12, width: 76, height: 76, shapeKind: "rectangle" }] },
  "circle-frame": { label: "Circle frame", cells: [{ x: 12, y: 12, width: 76, height: 76, shapeKind: "ellipse" }] },
  "rounded-frame": { label: "Rounded frame", cells: [{ x: 12, y: 12, width: 76, height: 76, shapeKind: "rounded" }] },
  "2-photos": { label: "2 photos", cells: gridCells(2, 1) },
  "3-photos": { label: "3 photos", cells: gridCells(3, 1) },
  "4-photos": { label: "4 photos", cells: gridCells(2, 2) },
  "6-photos": { label: "6 photos", cells: gridCells(3, 2) },
  "9-photos": { label: "9 photos", cells: gridCells(3, 3) },
  "feature-left": {
    label: "Feature left",
    cells: [
      { x: 2, y: 2, width: 48, height: 96, shapeKind: "rectangle" },
      { x: 52, y: 2, width: 46, height: 47, shapeKind: "rectangle" },
      { x: 52, y: 51, width: 46, height: 47, shapeKind: "rectangle" },
    ],
  },
  "feature-right": {
    label: "Feature right",
    cells: [
      { x: 2, y: 2, width: 46, height: 47, shapeKind: "rectangle" },
      { x: 2, y: 51, width: 46, height: 47, shapeKind: "rectangle" },
      { x: 52, y: 2, width: 46, height: 96, shapeKind: "rectangle" },
    ],
  },
  "feature-top": {
    label: "Feature top",
    cells: [
      { x: 2, y: 2, width: 96, height: 48, shapeKind: "rectangle" },
      { x: 2, y: 52, width: 47, height: 46, shapeKind: "rectangle" },
      { x: 51, y: 52, width: 47, height: 46, shapeKind: "rectangle" },
    ],
  },
  "hero-mosaic": {
    label: "Hero mosaic",
    cells: [
      { x: 2, y: 2, width: 58, height: 96, shapeKind: "rectangle" },
      { x: 62, y: 2, width: 36, height: 31, shapeKind: "rectangle" },
      { x: 62, y: 35, width: 36, height: 31, shapeKind: "rectangle" },
      { x: 62, y: 69, width: 36, height: 29, shapeKind: "rectangle" },
    ],
  },
};

const frameItems: FrameItem[] = [
  { id: "frame", label: "Frame", icon: LayoutGrid, preset: "frame" },
  { id: "circle-frame", label: "Circle frame", icon: Circle, preset: "circle-frame" },
  { id: "rounded-frame", label: "Rounded frame", icon: Square, preset: "rounded-frame" },
  { id: "2-photos", label: "2 photos", icon: Columns3, preset: "2-photos" },
  { id: "3-photos", label: "3 photos", icon: Columns3, preset: "3-photos" },
  { id: "4-photos", label: "4 photos", icon: Grid2x2, preset: "4-photos" },
  { id: "6-photos", label: "6 photos", icon: Grid3x2, preset: "6-photos" },
  { id: "9-photos", label: "9 photos", icon: Grid3x3, preset: "9-photos" },
  { id: "feature-left", label: "Feature left", icon: PanelLeft, preset: "feature-left" },
  { id: "feature-right", label: "Feature right", icon: PanelRight, preset: "feature-right" },
  { id: "feature-top", label: "Feature top", icon: PanelTop, preset: "feature-top" },
  { id: "hero-mosaic", label: "Hero mosaic", icon: PanelsTopLeft, preset: "hero-mosaic" },
];

const canvasTools: Array<{ id: EditorTool; label: string; icon: Component }> = [
  { id: "select", label: "Select", icon: MousePointer2 },
  { id: "text", label: "Text", icon: Type },
  { id: "rect", label: "Rectangle", icon: Square },
  { id: "ellipse", label: "Ellipse", icon: Circle },
  { id: "image", label: "Image", icon: ImageIcon },
];

function cloneDocument(value: EditorDocumentV2): EditorDocumentV2 {
  return JSON.parse(JSON.stringify(value)) as EditorDocumentV2;
}

function markDirty(): void {
  const nextSnapshot = JSON.stringify(document.value);
  if (nextSnapshot !== historySnapshot) {
    try {
      historyPast.value = [
        ...historyPast.value,
        cloneDocument(JSON.parse(historySnapshot) as EditorDocumentV2),
      ].slice(-50);
      historyFuture.value = [];
      historySnapshot = nextSnapshot;
    } catch {
      historyPast.value = [];
      historyFuture.value = [];
      historySnapshot = nextSnapshot;
    }
  }
  saveError.value = "";
  saveState.value = "dirty";
  preflightReport.value = null;
  preflightError.value = "";
  exportState.value = "idle";
  exportError.value = "";
}

function resetHistory(): void {
  historyPast.value = [];
  historyFuture.value = [];
  historySnapshot = JSON.stringify(document.value);
}

function markHistoryRestore(): void {
  historySnapshot = JSON.stringify(document.value);
  const layers = activePage.value.layers;
  selectedLayerId.value = layers.some((layer) => layer.id === selectedLayerId.value)
    ? selectedLayerId.value
    : layers[layers.length - 1]?.id ?? null;
  activeTool.value = "select";
  cacheDocument();
  saveError.value = "";
  saveState.value = "dirty";
  preflightReport.value = null;
  preflightError.value = "";
  exportState.value = "idle";
  exportError.value = "";
}

function undo(): void {
  const snapshot = historyPast.value.pop();
  if (!snapshot) return;
  historyFuture.value.push(cloneDocument(document.value));
  document.value = cloneDocument(snapshot);
  markHistoryRestore();
}

function redo(): void {
  const snapshot = historyFuture.value.pop();
  if (!snapshot) return;
  historyPast.value.push(cloneDocument(document.value));
  document.value = cloneDocument(snapshot);
  markHistoryRestore();
}

function backendAssetUrl(url: string): string {
  return url.startsWith("/") ? apiBaseUrl + url : url;
}

function safeImageSource(value: string | undefined): string {
  if (!value) return "";
  if (/^https?:\/\//i.test(value) || value.startsWith("/")) return value;
  return /^data:image\/(png|jpeg|webp|gif|svg\+xml);base64,/i.test(value) ? value : "";
}

function readImagePixelSize(source: string): Promise<{ pixelWidth?: number; pixelHeight?: number }> {
  return new Promise((resolve) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.addEventListener("load", () => resolve({
      pixelWidth: image.naturalWidth,
      pixelHeight: image.naturalHeight,
    }), { once: true });
    image.addEventListener("error", () => resolve({}), { once: true });
    image.src = source;
  });
}

function selectPage(side: EditorSide): void {
  activeSide.value = side;
  selectedLayerId.value = null;
  activeTool.value = "select";
  panX.value = 0;
  panY.value = 0;
  paletteExtractionError.value = "";
}

function selectLayer(layerId: string | null): void {
  selectedLayerId.value = layerId;
  paletteExtractionError.value = "";
}

function moveLayer(payload: { layerId: string; x: number; y: number }): void {
  const layer = activePage.value.layers.find((item) => item.id === payload.layerId);
  if (!layer || layer.locked) return;
  layer.x = payload.x;
  layer.y = payload.y;
  markDirty();
}

function resizeLayer(payload: { layerId: string; x: number; y: number; width: number; height: number }): void {
  const layer = activePage.value.layers.find((item) => item.id === payload.layerId);
  if (!layer || layer.locked) return;
  layer.x = payload.x;
  layer.y = payload.y;
  layer.width = payload.width;
  layer.height = payload.height;
  markDirty();
}

function rotateLayer(payload: { layerId: string; rotation: number }): void {
  const layer = activePage.value.layers.find((item) => item.id === payload.layerId);
  if (!layer || layer.locked) return;
  layer.rotation = payload.rotation;
  markDirty();
}

function handleLayerAction(payload: { layerId: string; action: EditorLayerAction }): void {
  const layers = activePage.value.layers;
  const index = layers.findIndex((layer) => layer.id === payload.layerId);
  const layer = layers[index];
  if (!layer) return;

  if (payload.action === "toggle-lock") {
    layer.locked = !layer.locked;
    markDirty();
    return;
  }
  if (layer.locked) return;

  if (payload.action === "duplicate") {
    const duplicate = cloneLayer(layer, 2);
    layers.splice(index + 1, 0, duplicate);
    selectedLayerId.value = duplicate.id;
  } else if (payload.action === "bring-forward" && index < layers.length - 1) {
    layers.splice(index + 1, 0, layers.splice(index, 1)[0]);
  } else if (payload.action === "send-backward" && index > 0) {
    layers.splice(index - 1, 0, layers.splice(index, 1)[0]);
  } else if (payload.action === "delete") {
    layers.splice(index, 1);
    selectedLayerId.value = layers[index]?.id ?? layers[index - 1]?.id ?? null;
  } else {
    return;
  }

  markDirty();
}

function clampPercent(value: number, max: number): number {
  return Math.min(Math.max(0, value), Math.max(0, max));
}

function uniqueIdSuffix(): string {
  return globalThis.crypto?.randomUUID?.() ?? String(Date.now()) + "-" + Math.random().toString(36).slice(2, 10);
}

function cloneLayer(layer: EditorLayerV2, offset: number): EditorLayerV2 {
  const duplicate = JSON.parse(JSON.stringify(layer)) as EditorLayerV2;
  duplicate.id = layer.id + "-copy-" + uniqueIdSuffix();
  duplicate.name = layer.name + " copy";
  duplicate.x = Math.min(Math.max(0, 100 - duplicate.width), layer.x + offset);
  duplicate.y = Math.min(Math.max(0, 100 - duplicate.height), layer.y + offset);
  return duplicate;
}

function copySelectedLayer(): void {
  if (!selectedLayer.value) return;
  internalClipboard.value = JSON.parse(JSON.stringify(selectedLayer.value)) as EditorLayerV2;
  pasteCount.value = 0;
}

function pasteCopiedLayer(): void {
  const copiedLayer = internalClipboard.value;
  if (!copiedLayer) return;
  const offset = 2 + (pasteCount.value % 5) * 2;
  const duplicate = cloneLayer(copiedLayer, offset);
  activePage.value.layers.push(duplicate);
  selectedLayerId.value = duplicate.id;
  pasteCount.value += 1;
  activeTool.value = "select";
  markDirty();
}

function nudgeSelectedLayer(deltaX: number, deltaY: number): void {
  const layer = selectedLayer.value;
  if (!layer || layer.locked) return;
  layer.x = clampPercent(layer.x + deltaX, 100 - layer.width);
  layer.y = clampPercent(layer.y + deltaY, 100 - layer.height);
  markDirty();
}

function dropPositionOverrides(point: CanvasDropPoint | undefined, width: number, height: number): Partial<EditorLayerV2> {
  if (!point) return {};
  return {
    x: clampPercent(point.centerX - width / 2, 100 - width),
    y: clampPercent(point.centerY - height / 2, 100 - height),
  };
}

function moveLayerGroupToDrop(layers: EditorLayerV2[], point: CanvasDropPoint | undefined): void {
  if (!point || !layers.length) return;
  const left = Math.min(...layers.map((layer) => layer.x));
  const top = Math.min(...layers.map((layer) => layer.y));
  const right = Math.max(...layers.map((layer) => layer.x + layer.width));
  const bottom = Math.max(...layers.map((layer) => layer.y + layer.height));
  const groupWidth = right - left;
  const groupHeight = bottom - top;
  const targetLeft = clampPercent(point.centerX - groupWidth / 2, 100 - groupWidth);
  const targetTop = clampPercent(point.centerY - groupHeight / 2, 100 - groupHeight);
  layers.forEach((layer) => {
    layer.x += targetLeft - left;
    layer.y += targetTop - top;
  });
}

function addLayer(type: EditorLayerType, source?: string, overrides: Partial<EditorLayerV2> = {}): EditorLayerV2 {
  const sequence = uniqueIdSuffix();
  const offset = (activePage.value.layers.length % 4) * 3;
  const width = overrides.width ?? (type === "text" ? 46 : type === "image" ? 36 : 28);
  const height = overrides.height ?? (type === "text" ? 14 : type === "image" ? 36 : 28);
  const layer: EditorLayerV2 = {
    id: activeSide.value + "-" + type + "-" + sequence,
    name: type === "text" ? "Text" : type === "rect" ? "Rectangle" : type === "ellipse" ? "Ellipse" : "Image",
    type,
    x: clampPercent(50 - width / 2 + offset, 100 - width),
    y: clampPercent(50 - height / 2 + offset, 100 - height),
    width,
    height,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    fill: type === "ellipse" ? "#0d766d" : type === "rect" ? "#d9b979" : "#151817",
    stroke: "#151817",
    strokeWidth: type === "rect" || type === "ellipse" ? 0 : undefined,
    cornerRadius: type === "rect" ? 2 : undefined,
    content: type === "text" ? "New text" : undefined,
    src: type === "image" ? source : undefined,
    originalSrc: type === "image" ? source : undefined,
    fontFamily: type === "text" ? "Aptos, Segoe UI, sans-serif" : undefined,
    fontSize: type === "text" ? 18 : undefined,
    fontWeight: type === "text" ? 600 : undefined,
    textAlign: type === "text" ? "left" : undefined,
    ...overrides,
  };
  activePage.value.layers.push(layer);
  selectedLayerId.value = layer.id;
  activeTool.value = "select";
  markDirty();
  return layer;
}

function addTextPreset(preset: TextPreset, point?: CanvasDropPoint): EditorLayerV2 | null {
  const option = textPresetOptions.find((item) => item.id === preset);
  if (!option) return null;
  const width = option.id === "title" ? 62 : 50;
  const height = option.id === "body" ? 12 : 14;
  const layer = addLayer("text", undefined, {
    name: option.id === "title" ? "Title" : option.id === "subtitle" ? "Subtitle" : "Body text",
    content: option.sample,
    fontSize: option.fontSize,
    fontWeight: option.fontWeight,
    width,
    height,
    ...dropPositionOverrides(point, width, height),
  });
  activePanel.value = "text";
  return layer;
}

function applyTextPreset(preset: TextPreset): void {
  const layer = selectedLayer.value;
  const option = textPresetOptions.find((item) => item.id === preset);
  if (!layer || layer.type !== "text" || !option) return;
  layer.fontSize = option.fontSize;
  layer.fontWeight = option.fontWeight;
  markDirty();
}

function addShapePreset(preset: ShapePreset, point?: CanvasDropPoint): EditorLayerV2 {
  const width = 28;
  const height = 28;
  const layer = addLayer(preset === "ellipse" ? "ellipse" : "rect", undefined, {
    name: preset === "rounded" ? "Rounded rectangle" : preset[0].toUpperCase() + preset.slice(1),
    shapeKind: preset,
    cornerRadius: preset === "rounded" ? 16 : 0,
    width,
    height,
    ...dropPositionOverrides(point, width, height),
  });
  activePanel.value = "elements";
  return layer;
}

function swapSelectedShape(preset: ShapePreset): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  layer.type = preset === "ellipse" ? "ellipse" : "rect";
  layer.shapeKind = preset;
  layer.cornerRadius = preset === "rounded" ? 16 : 0;
  layer.name = preset === "ellipse"
    ? "Ellipse"
    : preset === "rounded"
      ? "Rounded rectangle"
      : preset[0].toUpperCase() + preset.slice(1);
  markDirty();
}

function addFramePreset(preset: FramePreset, point?: CanvasDropPoint): EditorLayerV2[] {
  const spec = frameSpecs[preset];
  if (!spec) return [];
  const frameId = activeSide.value + "-frame-" + uniqueIdSuffix();
  const layers = spec.cells.map((cell, index) => addLayer("image", undefined, {
    id: frameId + "-" + String(index + 1),
    name: spec.label + " " + String(index + 1),
    x: cell.x,
    y: cell.y,
    width: cell.width,
    height: cell.height,
    shapeKind: cell.shapeKind,
    frameId,
    fill: "#e4e7ed",
    stroke: "#c3cad6",
    strokeWidth: 1,
    cornerRadius: cell.shapeKind === "rounded" ? 16 : 0,
  }));
  moveLayerGroupToDrop(layers, point);
  if (point) markDirty();
  activePanel.value = "elements";
  return layers;
}

function openImagePicker(targetLayerId: string | null = null): void {
  imageTargetLayerId.value = targetLayerId
    ?? (selectedLayer.value?.type === "image" ? selectedLayer.value.id : null);
  imageInput.value?.click();
}

function validateImageFile(file: File): string {
  if (!IMAGE_FILE_PATTERN.test(file.type)) return "Only PNG, JPEG, WebP and GIF images are supported.";
  if (file.size > MAX_IMAGE_FILE_BYTES) return "Each image must be 5 MB or smaller.";
  return "";
}

function readImageFileSource(file: File): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.addEventListener("load", () => {
      resolve(typeof reader.result === "string" ? safeImageSource(reader.result) : "");
    }, { once: true });
    reader.addEventListener("error", () => resolve(""), { once: true });
    reader.readAsDataURL(file);
  });
}

async function importMediaFile(file: File): Promise<UploadedMedia | null> {
  const validationError = validateImageFile(file);
  if (validationError) {
    mediaImportError.value = validationError;
    return null;
  }
  const source = await readImageFileSource(file);
  if (!source) {
    mediaImportError.value = "This image could not be read.";
    return null;
  }
  const existing = uploadedMedia.value.find((media) => media.src === source);
  if (existing) {
    mediaFileById.set(existing.id, file);
    return existing;
  }
  const pixelSize = await readImagePixelSize(source);
  const media: UploadedMedia = {
    id: "media-" + uniqueIdSuffix(),
    name: file.name,
    src: source,
    ...pixelSize,
  };
  uploadedMedia.value = [media, ...uploadedMedia.value];
  mediaFileById.set(media.id, file);
  mediaImportError.value = "";
  return media;
}

function addMediaAsset(media: UploadedMedia, point?: CanvasDropPoint): EditorLayerV2 {
  const width = 36;
  const height = 36;
  const layer = addLayer("image", media.src, {
    name: media.name,
    width,
    height,
    pixelWidth: media.pixelWidth,
    pixelHeight: media.pixelHeight,
    ...dropPositionOverrides(point, width, height),
  });
  const file = mediaFileById.get(media.id);
  if (file) imageFileByLayer.set(layer.id, file);
  activePanel.value = "uploads";
  return layer;
}

async function placeImageFile(file: File, point?: CanvasDropPoint, targetLayerId?: string | null): Promise<void> {
  const media = await importMediaFile(file);
  if (!media) return;
  const target = targetLayerId
    ? activePage.value.layers.find((layer) => layer.id === targetLayerId && layer.type === "image")
    : null;
  if (!target) {
    addMediaAsset(media, point);
    return;
  }
  target.src = media.src;
  target.originalSrc = media.src;
  target.processedSrc = undefined;
  target.processedAssetId = undefined;
  target.processedStorageKey = undefined;
  target.extractedPalette = undefined;
  target.pixelWidth = media.pixelWidth;
  target.pixelHeight = media.pixelHeight;
  target.visible = true;
  const fileForMedia = mediaFileById.get(media.id);
  if (fileForMedia) imageFileByLayer.set(target.id, fileForMedia);
  selectedLayerId.value = target.id;
  markDirty();
}

async function handleImageFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  const targetId = imageTargetLayerId.value;
  input.value = "";
  imageTargetLayerId.value = null;
  activeTool.value = "select";
  for (const [index, file] of files.entries()) {
    await placeImageFile(file, undefined, index === 0 ? targetId : null);
  }
}

function hasFileDrag(event: DragEvent): boolean {
  return Array.from(event.dataTransfer?.types ?? []).includes("Files");
}

function droppedFiles(event: DragEvent): File[] {
  return Array.from(event.dataTransfer?.files ?? []);
}

function startLibraryDrag(item: LibraryDragItem, event: DragEvent): void {
  libraryDragItem.value = item;
  if (!event.dataTransfer) return;
  event.dataTransfer.effectAllowed = "copy";
  event.dataTransfer.setData(VIZI_DRAG_MIME, JSON.stringify(item));
  event.dataTransfer.setData("text/plain", "Vizi " + item.kind);
}

function finishLibraryDrag(): void {
  libraryDragItem.value = null;
  canvasDropActive.value = false;
}

function readLibraryDrag(event: DragEvent): LibraryDragItem | null {
  if (libraryDragItem.value) return libraryDragItem.value;
  const raw = event.dataTransfer?.getData(VIZI_DRAG_MIME);
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<LibraryDragItem>;
    if (value.kind === "text" && typeof value.preset === "string") return value as LibraryDragItem;
    if (value.kind === "shape" && typeof value.preset === "string") return value as LibraryDragItem;
    if (value.kind === "frame" && typeof value.preset === "string") return value as LibraryDragItem;
    if ((value.kind === "stock" || value.kind === "icon" || value.kind === "media") && typeof value.id === "string") {
      return value as LibraryDragItem;
    }
  } catch {
    return null;
  }
  return null;
}

function canvasDropPoint(event: DragEvent): CanvasDropPoint | null {
  const canvas = canvasFrame.value?.querySelector<HTMLElement>("[data-editor-v2-canvas]");
  const rect = canvas?.getBoundingClientRect();
  if (!rect || rect.width <= 0 || rect.height <= 0) return null;
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) {
    return null;
  }
  return {
    centerX: Math.min(100, Math.max(0, ((event.clientX - rect.left) / rect.width) * 100)),
    centerY: Math.min(100, Math.max(0, ((event.clientY - rect.top) / rect.height) * 100)),
  };
}

function handleCanvasDragOver(event: DragEvent): void {
  if (!hasFileDrag(event) && !libraryDragItem.value && !event.dataTransfer?.types.includes(VIZI_DRAG_MIME)) return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "copy";
  canvasDropActive.value = Boolean(canvasDropPoint(event));
}

function handleCanvasDragLeave(event: DragEvent): void {
  const nextTarget = event.relatedTarget as Node | null;
  if (!(event.currentTarget as HTMLElement).contains(nextTarget)) canvasDropActive.value = false;
}

async function addLibraryItemAtPoint(item: LibraryDragItem, point: CanvasDropPoint): Promise<void> {
  if (item.kind === "text") {
    addTextPreset(item.preset, point);
  } else if (item.kind === "shape") {
    addShapePreset(item.preset, point);
  } else if (item.kind === "frame") {
    addFramePreset(item.preset, point);
  } else if (item.kind === "stock") {
    const asset = stockAllAssets.value.find((candidate) => candidate.id === item.id);
    if (asset) await addStockAsset(asset, point);
  } else if (item.kind === "icon") {
    const icon = localIconResults.value.find((candidate) => candidate.id === item.id);
    if (icon) await addLocalIcon(icon, point);
  } else {
    const media = mediaItems.value.find((candidate) => candidate.id === item.id);
    if (media) addMediaAsset(media, point);
  }
}

async function handleCanvasDrop(event: DragEvent): Promise<void> {
  event.preventDefault();
  const point = canvasDropPoint(event);
  canvasDropActive.value = false;
  if (!point) return;

  const files = droppedFiles(event);
  if (files.length) {
    for (const [index, file] of files.entries()) {
      await placeImageFile(file, {
        centerX: Math.min(96, point.centerX + index * 2),
        centerY: Math.min(96, point.centerY + index * 2),
      });
    }
    return;
  }

  const item = readLibraryDrag(event);
  libraryDragItem.value = null;
  if (item) await addLibraryItemAtPoint(item, point);
}

function handleMediaDragOver(event: DragEvent, target: "library" | "inspector"): void {
  if (!hasFileDrag(event)) return;
  event.preventDefault();
  event.stopPropagation();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "copy";
  mediaDropTarget.value = target;
}

function handleMediaDragLeave(event: DragEvent, target: "library" | "inspector"): void {
  const nextTarget = event.relatedTarget as Node | null;
  if (!(event.currentTarget as HTMLElement).contains(nextTarget) && mediaDropTarget.value === target) {
    mediaDropTarget.value = null;
  }
}

async function handleMediaDrop(event: DragEvent): Promise<void> {
  if (!hasFileDrag(event)) return;
  event.preventDefault();
  event.stopPropagation();
  mediaDropTarget.value = null;
  const files = droppedFiles(event);
  if (!files.length) return;
  for (const file of files) await importMediaFile(file);
  activePanel.value = "uploads";
}
function readStoredStockIds(key: string): string[] {
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(key) ?? "[]");
    return Array.isArray(value) ? value.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

function persistStoredStockIds(key: string, ids: string[]): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(ids));
  } catch {
    // Local persistence is optional when browser storage is unavailable.
  }
}

function toggleStockFavorite(asset: StockAsset): void {
  stockFavorites.value = stockFavorites.value.includes(asset.id)
    ? stockFavorites.value.filter((id) => id !== asset.id)
    : [asset.id, ...stockFavorites.value];
  persistStoredStockIds("vizi.editor.stock.favorites", stockFavorites.value);
}

function addStockRecent(id: string): void {
  stockRecent.value = [id, ...stockRecent.value.filter((item) => item !== id)].slice(0, 30);
  persistStoredStockIds("vizi.editor.stock.recent", stockRecent.value);
}

function mapStockApiAsset(asset: StockApiAsset): StockAsset {
  const kind: StockKind = asset.kind === "illustration" || asset.kind === "background"
    ? asset.kind
    : "photo";
  return {
    id: asset.id,
    title: asset.title,
    kind,
    collection: asset.collection || "Openverse",
    previewUrl: backendAssetUrl(asset.previewUrl),
    sourceUrl: asset.sourceUrl,
    tags: asset.tags,
    credit: asset.credit,
  };
}

async function loadStockAssets(reset = true): Promise<void> {
  if (!reset && (stockLoading.value || !stockHasMore.value)) return;

  const requestId = ++stockRequestId;
  const page = reset ? 1 : stockPage.value + 1;
  if (reset) stockError.value = "";
  stockLoading.value = true;

  try {
    const response = await searchStock(
      stockQuery.value.trim(),
      stockKindFilter.value,
      page,
      12,
    );
    if (requestId !== stockRequestId) return;

    const incoming = response.assets.map(mapStockApiAsset);
    remoteStockAssets.value = reset
      ? incoming
      : [
          ...remoteStockAssets.value,
          ...incoming.filter((asset) => !remoteStockAssets.value.some((current) => current.id === asset.id)),
        ];
    stockRemoteReady.value = true;
    stockPage.value = response.page;
    stockHasMore.value = response.hasMore;
    stockCollectionFilter.value = "All";
  } catch {
    if (requestId !== stockRequestId) return;
    if (reset) {
      remoteStockAssets.value = [];
      stockRemoteReady.value = false;
    }
    stockHasMore.value = false;
    stockError.value = "Online stock is unavailable. Showing the starter catalog.";
  } finally {
    if (requestId === stockRequestId) stockLoading.value = false;
  }
}

async function addStockAsset(asset: StockAsset, point?: CanvasDropPoint): Promise<EditorLayerV2 | null> {
  const source = safeImageSource(asset.previewUrl);
  if (!source) return null;
  const pixelSize = await readImagePixelSize(source);
  const width = 52;
  const height = 36;
  const layer = addLayer("image", source, {
    name: "Stock - " + asset.title,
    width,
    height,
    ...pixelSize,
    ...dropPositionOverrides(point, width, height),
  });
  addStockRecent(asset.id);
  activePanel.value = "stock";
  return layer;
}

async function addLocalIcon(icon: LocalIcon, point?: CanvasDropPoint): Promise<EditorLayerV2> {
  const source = localIconAsset(icon);
  const pixelSize = await readImagePixelSize(source);
  const width = 28;
  const height = 28;
  const layer = addLayer("image", source, {
    name: icon.label,
    iconSource: icon.source,
    iconId: icon.id,
    width,
    height,
    ...pixelSize,
    ...dropPositionOverrides(point, width, height),
  });
  activePanel.value = "icon";
  return layer;
}

async function removeBackgroundFromSelected(): Promise<void> {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "image" || backgroundRemovingLayerId.value) return;

  const file = imageFileByLayer.get(layer.id);
  backgroundRemovalError.value = "";
  if (!file) {
    backgroundRemovalError.value = "Re-upload this image before removing its background.";
    return;
  }

  backgroundRemovingLayerId.value = layer.id;
  try {
    const processed = await removeBackgroundImageAsset(file);
    const originalSource = layer.originalSrc ?? layer.src;
    const processedSource = backendAssetUrl(processed.url);
    layer.originalSrc = originalSource;
    layer.processedSrc = processedSource;
    layer.processedAssetId = processed.assetId;
    layer.processedStorageKey = processed.storageKey;
    layer.src = processedSource;
    layer.extractedPalette = undefined;
    layer.name = layer.name.replace(/ \(no background\)$/, "") + " (no background)";
    markDirty();
  } catch (unknownError) {
    backgroundRemovalError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot remove image background";
  } finally {
    backgroundRemovingLayerId.value = null;
  }
}

function restoreOriginalImage(): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "image" || !layer.originalSrc) return;
  layer.src = layer.originalSrc;
  markDirty();
}

async function extractPaletteFromSelected(): Promise<void> {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "image" || paletteExtractingLayerId.value) return;
  const source = safeImageSource(layer.src);
  if (!source) {
    paletteExtractionError.value = "This image has no safe source for color extraction.";
    return;
  }

  const side = activeSide.value;
  const layerId = layer.id;
  paletteExtractionError.value = "";
  paletteExtractingLayerId.value = layerId;
  try {
    const palette = await extractImagePalette(backendAssetUrl(source));
    const target = document.value.pages[side].layers.find((item) => item.id === layerId && item.type === "image");
    if (!target) return;
    target.extractedPalette = palette;
    markDirty();
  } catch (unknownError) {
    paletteExtractionError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot extract colors from this image.";
  } finally {
    paletteExtractingLayerId.value = null;
  }
}

function activateTool(tool: EditorTool): void {
  activeTool.value = tool;
  if (tool === "text") {
    addTextPreset("body");
  } else if (tool === "image") {
    activePanel.value = "uploads";
    openImagePicker();
  } else if (tool === "rect") {
    addShapePreset("rectangle");
  } else if (tool === "ellipse") {
    addShapePreset("ellipse");
  }
}

function layerIcon(layer: EditorLayerV2): Component {
  if (layer.type === "text") return Type;
  if (layer.type === "ellipse") return Circle;
  if (layer.type === "image") return ImageIcon;
  return Square;
}

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value)));
}

function setZoom(value: number, resetPan = true): void {
  zoom.value = clampZoom(value);
  if (resetPan) {
    panX.value = 0;
    panY.value = 0;
  }
  zoomMenuOpen.value = false;
}

function updateZoom(delta: number): void {
  zoom.value = clampZoom(zoom.value + delta);
}

function canvasMetrics(): {
  availableWidth: number;
  availableHeight: number;
  baseWidth: number;
  baseHeight: number;
} | null {
  const frame = canvasFrame.value;
  const canvas = frame?.querySelector<HTMLElement>("[data-editor-v2-canvas]");
  const stage = frame?.closest<HTMLElement>(".editor-v2__stage");
  if (!frame || !canvas || !stage || canvas.offsetWidth <= 0 || canvas.offsetHeight <= 0) return null;

  const style = window.getComputedStyle(stage);
  const horizontalPadding = Number.parseFloat(style.paddingLeft) + Number.parseFloat(style.paddingRight);
  const verticalPadding = Number.parseFloat(style.paddingTop) + Number.parseFloat(style.paddingBottom);
  const headingHeight = stage.querySelector<HTMLElement>(".editor-v2__card-heading")?.offsetHeight ?? 0;
  const gap = Number.parseFloat(style.rowGap || style.gap) || 0;
  return {
    availableWidth: Math.max(1, stage.clientWidth - horizontalPadding),
    availableHeight: Math.max(1, stage.clientHeight - verticalPadding - headingHeight - gap),
    baseWidth: canvas.offsetWidth,
    baseHeight: canvas.offsetHeight,
  };
}

function fitCard(): void {
  const metrics = canvasMetrics();
  if (!metrics) return;
  const target = Math.min(
    metrics.availableWidth / metrics.baseWidth,
    metrics.availableHeight / metrics.baseHeight,
  ) * 100;
  setZoom(target);
}

function zoomToSelection(): void {
  const layer = selectedLayer.value;
  const metrics = canvasMetrics();
  if (!layer || !metrics) return;

  const width = metrics.baseWidth * layer.width / 100;
  const height = metrics.baseHeight * layer.height / 100;
  const radians = layer.rotation * Math.PI / 180;
  const rotatedWidth = Math.abs(width * Math.cos(radians)) + Math.abs(height * Math.sin(radians));
  const rotatedHeight = Math.abs(width * Math.sin(radians)) + Math.abs(height * Math.cos(radians));
  const target = Math.min(
    metrics.availableWidth * 0.72 / Math.max(1, rotatedWidth),
    metrics.availableHeight * 0.72 / Math.max(1, rotatedHeight),
  ) * 100;
  zoom.value = clampZoom(target);
  const scale = zoom.value / 100;
  const centerX = metrics.baseWidth * (layer.x + layer.width / 2) / 100;
  const centerY = metrics.baseHeight * (layer.y + layer.height / 2) / 100;
  panX.value = (metrics.baseWidth / 2 - centerX) * scale;
  panY.value = (metrics.baseHeight / 2 - centerY) * scale;
  zoomMenuOpen.value = false;
}

function handleZoomMenuPointerDown(event: PointerEvent): void {
  if (!zoomControl.value?.contains(event.target as Node)) zoomMenuOpen.value = false;
}

function handleZoomMenuKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape") zoomMenuOpen.value = false;
}

function updateCanvasOverlayBounds(): void {
  const frame = canvasFrame.value;
  const canvas = frame?.querySelector<HTMLElement>("[data-editor-v2-canvas]");
  if (!frame || !canvas) {
    canvasOverlayBounds.value = null;
    return;
  }
  const frameRect = frame.getBoundingClientRect();
  const canvasRect = canvas.getBoundingClientRect();
  canvasOverlayBounds.value = {
    left: canvasRect.left - frameRect.left,
    top: canvasRect.top - frameRect.top,
    width: canvasRect.width,
    height: canvasRect.height,
  };
}

function queueCanvasOverlayUpdate(): void {
  if (canvasOverlayFrame !== null) window.cancelAnimationFrame(canvasOverlayFrame);
  if (canvasOverlayTimer !== null) window.clearTimeout(canvasOverlayTimer);
  canvasOverlayFrame = window.requestAnimationFrame(() => {
    canvasOverlayFrame = null;
    updateCanvasOverlayBounds();
  });
  canvasOverlayTimer = window.setTimeout(() => {
    canvasOverlayTimer = null;
    updateCanvasOverlayBounds();
  }, 190);
}

function openPreview(): void {
  previewSide.value = activeSide.value;
  previewOpen.value = true;
}

function closePreview(): void {
  previewOpen.value = false;
}

function openShortcutDialog(): void {
  shortcutsOpen.value = true;
  shortcutQuery.value = "";
  void nextTick(() => shortcutSearchInput.value?.focus());
}

function closeShortcutDialog(): void {
  shortcutsOpen.value = false;
  shortcutQuery.value = "";
}

function isEditingTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement
    && Boolean(target.closest("input, textarea, select, [contenteditable=true]"));
}

function handleEditorKeydown(event: KeyboardEvent): void {
  const key = event.key.toLowerCase();
  const code = event.code.toLowerCase();
  const modifier = event.ctrlKey || event.metaKey;

  if (key === "escape") {
    zoomMenuOpen.value = false;
    if (previewOpen.value) {
      event.preventDefault();
      closePreview();
    } else if (shortcutsOpen.value) {
      event.preventDefault();
      closeShortcutDialog();
    } else if (!isEditingTarget(event.target)) {
      selectedLayerId.value = null;
    }
    return;
  }
  if (isEditingTarget(event.target)) return;

  if (key === "?") {
    event.preventDefault();
    openShortcutDialog();
    return;
  }
  if (modifier && key === "z") {
    event.preventDefault();
    if (event.shiftKey) redo(); else undo();
    return;
  }
  if (modifier && key === "y") {
    event.preventDefault();
    redo();
    return;
  }
  if (modifier && key === "s") {
    event.preventDefault();
    void saveDraft();
    return;
  }
  if (modifier && key === "c") {
    event.preventDefault();
    copySelectedLayer();
    return;
  }
  if (modifier && key === "v") {
    event.preventDefault();
    pasteCopiedLayer();
    return;
  }
  if (modifier && key === "d") {
    event.preventDefault();
    if (selectedLayer.value) handleLayerAction({ layerId: selectedLayer.value.id, action: "duplicate" });
    return;
  }
  if (modifier && (key === "=" || key === "+" || code === "numpadadd")) {
    event.preventDefault();
    updateZoom(10);
    return;
  }
  if (modifier && (key === "-" || key === "_" || code === "numpadsubtract")) {
    event.preventDefault();
    updateZoom(-10);
    return;
  }
  if (modifier && key === "0") {
    event.preventDefault();
    fitCard();
    return;
  }

  if (key === "delete" || key === "backspace") {
    event.preventDefault();
    if (selectedLayer.value) handleLayerAction({ layerId: selectedLayer.value.id, action: "delete" });
    return;
  }
  if (key === "arrowleft" || key === "arrowright" || key === "arrowup" || key === "arrowdown") {
    event.preventDefault();
    const step = event.shiftKey ? 2.5 : 0.5;
    const deltaX = key === "arrowleft" ? -step : key === "arrowright" ? step : 0;
    const deltaY = key === "arrowup" ? -step : key === "arrowdown" ? step : 0;
    nudgeSelectedLayer(deltaX, deltaY);
    return;
  }
  if (key === "[" || key === "]") {
    if (selectedLayer.value) {
      event.preventDefault();
      handleLayerAction({
        layerId: selectedLayer.value.id,
        action: key === "]" ? "bring-forward" : "send-backward",
      });
    }
    return;
  }
  if (key === "+" || key === "=" || code === "numpadadd") {
    event.preventDefault();
    updateZoom(10);
    return;
  }
  if (key === "-" || key === "_" || code === "numpadsubtract") {
    event.preventDefault();
    updateZoom(-10);
    return;
  }
  if (key === "1") {
    event.preventDefault();
    setZoom(100);
    return;
  }
  if (key === "2" && selectedLayer.value) {
    event.preventDefault();
    zoomToSelection();
    return;
  }
  if (key === "v") {
    activeTool.value = "select";
    return;
  }
  if (key === "t") {
    event.preventDefault();
    activateTool("text");
    return;
  }
  if (key === "r") {
    event.preventDefault();
    activateTool("rect");
    return;
  }
  if (key === "o") {
    event.preventDefault();
    activateTool("ellipse");
  }
}

function updateSelectedText(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "text") return;
  layer.content = (event.target as HTMLTextAreaElement).value;
  markDirty();
}

function updateSelectedNumber(field: NumericLayerField, event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLInputElement).value);
  if (!layer || !Number.isFinite(value)) return;

  if (field === "fontSize") layer.fontSize = Math.min(200, Math.max(1, value));
  if (field === "rotation") layer.rotation = ((value % 360) + 360) % 360;
  if (field === "strokeWidth") layer.strokeWidth = Math.min(20, Math.max(0, value));
  if (field === "cornerRadius") layer.cornerRadius = Math.min(50, Math.max(0, value));
  markDirty();
}

function geometryValueMm(field: GeometryField): number {
  const layer = selectedLayer.value;
  if (!layer) return 0;
  const dimension = field === "x" || field === "width" ? document.value.card.widthMm : document.value.card.heightMm;
  return Number(((layer[field] * dimension) / 100).toFixed(1));
}

function updateSelectedGeometry(field: GeometryField, event: Event): void {
  const layer = selectedLayer.value;
  const valueMm = Number((event.target as HTMLInputElement).value);
  if (!layer || !Number.isFinite(valueMm)) return;
  const dimension = field === "x" || field === "width" ? document.value.card.widthMm : document.value.card.heightMm;
  const value = (valueMm / dimension) * 100;

  if (field === "x") layer.x = clampPercent(value, 100 - layer.width);
  if (field === "y") layer.y = clampPercent(value, 100 - layer.height);
  if (field === "width") layer.width = Math.min(100 - layer.x, Math.max(2, value));
  if (field === "height") layer.height = Math.min(100 - layer.y, Math.max(2, value));
  markDirty();
}

function updateSelectedFill(event: Event): void {
  setSelectedFill((event.target as HTMLInputElement).value);
}

function setSelectedFill(color: string): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type === "image") return;
  layer.fill = color;
  if (layer.type === "rect" || layer.type === "ellipse") layer.fillMode = "solid";
  markDirty();
}

function ensureGradientStops(layer: EditorLayerV2): EditorGradientStopV2[] {
  if (!layer.gradientStops || layer.gradientStops.length < 2) {
    layer.gradientStops = createDefaultGradientStops(layer.fill);
  }
  return layer.gradientStops;
}

function setSelectedFillMode(mode: "solid" | "gradient"): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  if (mode === "solid") {
    layer.fillMode = "solid";
  } else {
    layer.fillMode = layer.fillMode === "radial" ? "radial" : "linear";
    ensureGradientStops(layer);
    layer.gradientAngle ??= 90;
    layer.gradientCenterX ??= 50;
    layer.gradientCenterY ??= 50;
    layer.gradientRadius ??= 70;
  }
  markDirty();
}

function setSelectedGradientKind(mode: "linear" | "radial"): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  layer.fillMode = mode;
  ensureGradientStops(layer);
  layer.gradientAngle ??= 90;
  layer.gradientCenterX ??= 50;
  layer.gradientCenterY ??= 50;
  layer.gradientRadius ??= 70;
  markDirty();
}

function updateSelectedGradientNumber(field: GradientNumberField, event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLInputElement).value);
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse") || !Number.isFinite(value)) return;
  if (field === "gradientAngle") layer.gradientAngle = Math.min(360, Math.max(0, value));
  if (field === "gradientCenterX") layer.gradientCenterX = Math.min(100, Math.max(0, value));
  if (field === "gradientCenterY") layer.gradientCenterY = Math.min(100, Math.max(0, value));
  if (field === "gradientRadius") layer.gradientRadius = Math.min(200, Math.max(1, value));
  markDirty();
}

function updateGradientStop(index: number, field: keyof EditorGradientStopV2, event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  const stops = ensureGradientStops(layer);
  const stop = stops[index];
  if (!stop) return;
  if (field === "color") {
    stop.color = (event.target as HTMLInputElement).value;
  } else {
    const value = Number((event.target as HTMLInputElement).value);
    if (!Number.isFinite(value)) return;
    stop[field] = Math.min(1, Math.max(0, value / 100));
  }
  layer.gradientStops = [...stops];
  markDirty();
}

function addGradientStop(): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  const stops = ensureGradientStops(layer);
  if (stops.length >= 4) return;
  layer.gradientStops = [...stops, { color: "#ffffff", offset: 0.5, opacity: 1 }];
  markDirty();
}

function removeGradientStop(index: number): void {
  const layer = selectedLayer.value;
  if (!layer || !layer.gradientStops || layer.gradientStops.length <= 2) return;
  layer.gradientStops = layer.gradientStops.filter((_, stopIndex) => stopIndex !== index);
  markDirty();
}

function setSelectedShapeEffect(effect: EditorShapeEffect): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  const previousEffect = layer.shapeEffect ?? "none";
  layer.shapeEffect = effect;
  if (effect === "hollow") {
    layer.stroke ??= "#111827";
    layer.strokeWidth = Math.max(2, layer.strokeWidth ?? 0);
  } else if (effect === "shadow" && previousEffect !== "shadow") {
    layer.shadowX = 0;
    layer.shadowY = 4;
    layer.shadowBlur = 16;
    layer.shadowSpread = 0;
    layer.shadowColor = "#111827";
    layer.shadowOpacity = 0.35;
  } else if (effect === "glow" && previousEffect !== "glow") {
    layer.shadowX = 0;
    layer.shadowY = 0;
    layer.shadowBlur = 16;
    layer.shadowSpread = 0;
    layer.shadowColor = layer.fill ?? "#3b82f6";
    layer.shadowOpacity = 0.7;
  }
  markDirty();
}

function updateSelectedEffectNumber(field: EffectNumberField, event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLInputElement).value);
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse") || !Number.isFinite(value)) return;
  if (field === "shadowX") layer.shadowX = Math.min(100, Math.max(-100, value));
  if (field === "shadowY") layer.shadowY = Math.min(100, Math.max(-100, value));
  if (field === "shadowBlur") layer.shadowBlur = Math.min(100, Math.max(0, value));
  if (field === "shadowOpacity") layer.shadowOpacity = Math.min(1, Math.max(0, value / 100));
  markDirty();
}

function updateSelectedEffectColor(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  layer.shadowColor = (event.target as HTMLInputElement).value;
  markDirty();
}

function updateSelectedStroke(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || (layer.type !== "rect" && layer.type !== "ellipse")) return;
  layer.stroke = (event.target as HTMLInputElement).value;
  markDirty();
}

function updateSelectedOpacity(event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLInputElement).value);
  if (!layer || !Number.isFinite(value)) return;
  layer.opacity = Math.min(1, Math.max(0, value / 100));
  markDirty();
}

function updateSelectedFont(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "text") return;
  layer.fontFamily = (event.target as HTMLSelectElement).value;
  loadFont(layer.fontFamily);
  markDirty();
}

function setSelectedFont(fontFamily: string): void {
  loadFont(fontFamily);
  const layer = selectedLayer.value;
  if (layer?.type === "text") {
    layer.fontFamily = fontFamily;
    markDirty();
  } else {
    addLayer("text", undefined, { fontFamily });
    activePanel.value = "text";
  }
}

function normalizeFontSearch(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase()
    .trim();
}

function loadFont(fontFamily: string): void {
  const family = fontFamily.includes(",") ? fontFamily : JSON.stringify(fontFamily);
  void window.document.fonts?.load(`16px ${family}`).catch(() => undefined);
}

function showMoreFonts(): void {
  fontResultLimit.value += 40;
}

function updateSelectedWeight(event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLSelectElement).value);
  if (!layer || layer.type !== "text" || !Number.isFinite(value)) return;
  layer.fontWeight = value;
  markDirty();
}

function toggleBold(): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "text") return;
  layer.fontWeight = (layer.fontWeight ?? 400) >= 700 ? 500 : 700;
  markDirty();
}

function setTextAlign(align: "left" | "center" | "right"): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "text") return;
  layer.textAlign = align;
  markDirty();
}

function updatePageBackground(event: Event): void {
  setPageBackground((event.target as HTMLInputElement).value);
}

function setPageBackground(color: string): void {
  activePage.value.background = color;
  markDirty();
}


function toggleLayerVisibility(layer: EditorLayerV2): void {
  layer.visible = !layer.visible;
  markDirty();
}

function toggleLayerLock(layer: EditorLayerV2): void {
  layer.locked = !layer.locked;
  markDirty();
}

function startLayerReorder(layerId: string, event: DragEvent): void {
  draggedLayerId.value = layerId;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", layerId);
  }
}

function dropLayerBefore(targetLayerId: string): void {
  const sourceLayerId = draggedLayerId.value;
  draggedLayerId.value = null;
  if (!sourceLayerId || sourceLayerId === targetLayerId) return;

  const displayLayers = [...activePage.value.layers].reverse();
  const sourceIndex = displayLayers.findIndex((layer) => layer.id === sourceLayerId);
  if (sourceIndex < 0) return;
  const [sourceLayer] = displayLayers.splice(sourceIndex, 1);
  const targetIndex = displayLayers.findIndex((layer) => layer.id === targetLayerId);
  if (targetIndex < 0) return;
  displayLayers.splice(targetIndex, 0, sourceLayer);
  activePage.value.layers.splice(0, activePage.value.layers.length, ...displayLayers.reverse());
  selectedLayerId.value = sourceLayerId;
  markDirty();
}

function cacheDocument(): void {
  localStorage.setItem(storageKey.value, JSON.stringify(document.value));
}

function finishSavedState(): void {
  saveState.value = "saved";
  window.setTimeout(() => {
    if (saveState.value === "saved") saveState.value = "idle";
  }, 1800);
}

async function saveDraft(): Promise<void> {
  if (saveState.value === "saving" || saveState.value === "loading") return;

  document.value.updatedAt = new Date().toISOString();
  cacheDocument();
  const designId = backendDesignId.value;
  if (!designId) {
    resetHistory();
    finishSavedState();
    return;
  }

  saveError.value = "";
  saveState.value = "saving";
  try {
    const saved = await updateDesign(designId, document.value.name, JSON.stringify(document.value));
    document.value.name = saved.name;
    cacheDocument();
    resetHistory();
    finishSavedState();
  } catch (unknownError) {
    saveError.value = draftErrorMessage(unknownError, "Cannot save draft");
    saveState.value = "error";
  }
}

async function openCheckout(): Promise<void> {
  const designId = backendDesignId.value;
  if (!designId) {
    saveError.value = "Save this draft before checkout.";
    saveState.value = "error";
    return;
  }
  if (checkoutLoading.value || saveState.value === "saving" || saveState.value === "loading") return;

  checkoutLoading.value = true;
  try {
    await saveDraft();
    if (saveState.value !== "error") {
      await router.push({ name: "checkout", params: { designId } });
    }
  } finally {
    checkoutLoading.value = false;
  }
}

async function runPreflightCheck(): Promise<void> {
  const designId = backendDesignId.value;
  if (!designId) {
    preflightError.value = "Save this draft before running preflight.";
    return;
  }
  if (preflightLoading.value || saveState.value === "saving" || saveState.value === "loading") return;

  preflightLoading.value = true;
  preflightReport.value = null;
  preflightError.value = "";
  try {
    await saveDraft();
    if (saveState.value === "error") {
      throw new Error(saveError.value || "Cannot save draft before preflight");
    }
    preflightReport.value = await preflightDesign(designId);
  } catch (unknownError) {
    preflightReport.value = null;
    preflightError.value = unknownError instanceof Error ? unknownError.message : "Cannot run preflight";
  } finally {
    preflightLoading.value = false;
  }
}

async function exportPrintPdf(): Promise<void> {
  const designId = backendDesignId.value;
  if (!designId) {
    exportError.value = "Save this draft before exporting a print PDF.";
    exportState.value = "error";
    return;
  }
  if (exportState.value === "exporting" || preflightLoading.value || saveState.value === "saving" || saveState.value === "loading") return;

  exportState.value = "exporting";
  exportError.value = "";
  preflightLoading.value = true;
  preflightReport.value = null;
  preflightError.value = "";
  try {
    await saveDraft();
    if (saveState.value === "error") {
      throw new Error(saveError.value || "Cannot save draft before print export");
    }
    const report = await preflightDesign(designId);
    preflightReport.value = report;
    if (report.issues.some((issue) => issue.level === "ERROR")) {
      throw new Error("Print export is blocked until all preflight errors are fixed.");
    }
    const result = await createPrintPdf(document.value);
    downloadPrintPdf(result);
    exportState.value = "downloaded";
    window.setTimeout(() => {
      if (exportState.value === "downloaded") exportState.value = "idle";
    }, 3000);
  } catch (unknownError) {
    exportError.value = unknownError instanceof Error ? unknownError.message : "Cannot export print PDF";
    exportState.value = "error";
  } finally {
    preflightLoading.value = false;
  }
}

function focusPreflightIssue(issue: PreflightIssue): void {
  if ((issue.side !== "front" && issue.side !== "back") || issue.layerIndex == null) return;
  const layer = document.value.pages[issue.side].layers[issue.layerIndex];
  activeSide.value = issue.side;
  selectedLayerId.value = layer?.id ?? null;
  activeTool.value = "select";
}

async function loadInitialDocument(): Promise<void> {
  const stored = readEditorDocumentV2(localStorage.getItem(storageKey.value));
  const localDocument = stored?.documentId === documentId.value ? stored : null;
  const designId = backendDesignId.value;
  if (!designId) {
    if (localDocument) document.value = localDocument;
    resetHistory();
    selectedLayerId.value = null;
    return;
  }

  saveState.value = "loading";
  saveError.value = "";
  try {
    const design = await getDesign(designId);
    const remoteDocument = readEditorDocumentV2(design.canvasJson);
    const nextDocument = remoteDocument?.documentId === documentId.value
      ? remoteDocument
      : localDocument ?? createEditorDocumentV2(documentId.value);
    nextDocument.name = design.name;
    if (Number.isFinite(design.widthMm) && design.widthMm > 0) {
      nextDocument.card.widthMm = design.widthMm;
    }
    if (Number.isFinite(design.heightMm) && design.heightMm > 0) {
      nextDocument.card.heightMm = design.heightMm;
    }
    document.value = nextDocument;
    resetHistory();
    cacheDocument();
    saveState.value = remoteDocument?.documentId === documentId.value ? "idle" : "dirty";
  } catch (unknownError) {
    if (localDocument) document.value = localDocument;
    resetHistory();
    saveError.value = draftErrorMessage(unknownError, "Cannot load draft");
    saveState.value = "error";
  } finally {
    selectedLayerId.value = null;
  }
}

watch(activeSide, () => {
  const current = activePage.value.layers.find((layer) => layer.id === selectedLayerId.value);
  if (!current) selectedLayerId.value = null;
});

watch(fontSearch, () => {
  fontResultLimit.value = 40;
});

watch([stockQuery, stockKindFilter], () => {
  if (activePanel.value !== "stock") return;
  if (stockSearchTimer !== null) window.clearTimeout(stockSearchTimer);
  stockSearchTimer = window.setTimeout(() => {
    stockSearchTimer = null;
    void loadStockAssets(true);
  }, 350);
});

watch(activePanel, (panel) => {
  if (panel === "stock" && !stockRemoteReady.value && !stockLoading.value) {
    void loadStockAssets(true);
  }
});

watch(
  [zoom, panX, panY, () => document.value.card.widthMm, () => document.value.card.heightMm],
  () => queueCanvasOverlayUpdate(),
);

onMounted(() => {
  window.addEventListener("pointerdown", handleZoomMenuPointerDown);
  window.addEventListener("keydown", handleZoomMenuKeydown);
  window.addEventListener("keydown", handleEditorKeydown);
  window.addEventListener("resize", queueCanvasOverlayUpdate);
  void nextTick(() => {
    const frame = canvasFrame.value;
    const canvas = frame?.querySelector<HTMLElement>("[data-editor-v2-canvas]");
    canvasResizeObserver = new ResizeObserver(queueCanvasOverlayUpdate);
    if (frame) canvasResizeObserver.observe(frame);
    if (canvas) canvasResizeObserver.observe(canvas);
    queueCanvasOverlayUpdate();
  });
  void loadInitialDocument();
});

onBeforeUnmount(() => {
  if (stockSearchTimer !== null) window.clearTimeout(stockSearchTimer);
  if (canvasOverlayFrame !== null) window.cancelAnimationFrame(canvasOverlayFrame);
  if (canvasOverlayTimer !== null) window.clearTimeout(canvasOverlayTimer);
  canvasResizeObserver?.disconnect();
  window.removeEventListener("pointerdown", handleZoomMenuPointerDown);
  window.removeEventListener("keydown", handleZoomMenuKeydown);
  window.removeEventListener("keydown", handleEditorKeydown);
  window.removeEventListener("resize", queueCanvasOverlayUpdate);
});
</script>

<template>
  <section class="editor-v2" data-editor-version="2">
    <input
      ref="imageInput"
      class="editor-v2__file-input"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      aria-label="Upload image"
      multiple
      @change="handleImageFile"
    >
    <header class="editor-v2__header">
      <div class="editor-v2__header-left">
        <RouterLink class="editor-v2__back" to="/designs" aria-label="Back to drafts" title="Back to drafts">
          <ArrowLeft :size="18" :stroke-width="1.8" aria-hidden="true" />
        </RouterLink>
        <span class="editor-v2__mark" aria-hidden="true">V</span>
        <strong class="editor-v2__brand">Vizi</strong>
        <span class="editor-v2__divider" aria-hidden="true" />
        <div class="editor-v2__document-name">
          <strong>{{ document.name }}</strong>
          <span>Business card</span>
        </div>
      </div>

      <div class="editor-v2__header-center" aria-label="History and status">
        <button
          type="button"
          aria-label="Undo"
          title="Undo (Ctrl/Cmd+Z)"
          aria-keyshortcuts="Control+Z Meta+Z"
          :disabled="!canUndo"
          @click="undo"
        >
          <Undo2 :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Redo"
          title="Redo (Ctrl/Cmd+Y or Ctrl/Cmd+Shift+Z)"
          aria-keyshortcuts="Control+Y Control+Shift+Z Meta+Y Meta+Shift+Z"
          :disabled="!canRedo"
          @click="redo"
        >
          <Redo2 :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <span class="editor-v2__header-tool-divider" aria-hidden="true" />
        <button
          type="button"
          aria-label="Toggle rulers"
          title="Show or hide rulers"
          :aria-pressed="showRulers"
          :class="{ active: showRulers }"
          @click="showRulers = !showRulers"
        >
          <Ruler :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Toggle grid"
          title="Show or hide 5 mm grid"
          :aria-pressed="showGrid"
          :class="{ active: showGrid }"
          @click="showGrid = !showGrid"
        >
          <Grid2x2 :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
      </div>

      <div class="editor-v2__header-actions">
        <div class="editor-v2__status-cluster">
          <span
            class="editor-v2__status-dot"
            :class="{ 'editor-v2__status-dot--error': saveState === 'error' || exportState === 'error' }"
            aria-hidden="true"
          />
          <span class="editor-v2__status" role="status" aria-live="polite">{{ statusText }}</span>
        </div>
        <button class="editor-v2__header-icon editor-v2__preview-icon" type="button" aria-label="Preview card" title="Preview Front and Back" @click="openPreview">
          <Play :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button
          class="editor-v2__header-icon editor-v2__export-icon"
          type="button"
          aria-label="Export print PDF"
          title="Export Front and Back print PDF"
          :disabled="exportState === 'exporting' || preflightLoading || saveState === 'saving' || saveState === 'loading'"
          @click="exportPrintPdf"
        >
          <Download :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button
          class="editor-v2__preflight"
          type="button"
          :disabled="preflightLoading || saveState === 'saving' || saveState === 'loading'"
          @click="runPreflightCheck"
        >
          <ShieldCheck :size="15" :stroke-width="1.8" aria-hidden="true" />
          <span>{{ preflightLoading ? "Checking" : "Preflight" }}</span>
        </button>
        <button
          class="editor-v2__checkout"
          type="button"
          aria-label="Checkout"
          title="Checkout"
          :disabled="checkoutLoading || saveState === 'saving' || saveState === 'loading'"
          @click="openCheckout"
        >
          <ShoppingCart :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>

        <button
          class="editor-v2__save"
          type="button"
          :disabled="saveState === 'saving' || saveState === 'loading'"
          @click="saveDraft"
        >
          <Save :size="15" :stroke-width="1.8" aria-hidden="true" />
          <span>{{ saveState === "saving" ? "Saving" : "Save" }}</span>
        </button>
      </div>
    </header>

    <div class="editor-v2__layout">
      <nav class="editor-v2__rail" aria-label="Editor sections">
        <button
          v-for="item in sidebarItems"
          :key="item.id"
          type="button"
          :class="{ active: activePanel === item.id }"
          :aria-pressed="activePanel === item.id"
          :title="item.label"
          @click="activePanel = item.id"
        >
          <component :is="item.icon" :size="20" :stroke-width="1.7" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
        <button
          class="editor-v2__shortcut-launcher"
          type="button"
          title="Keyboard shortcuts (?)"
          aria-label="Keyboard shortcuts"
          aria-haspopup="dialog"
          @click="openShortcutDialog"
        >
          <Keyboard :size="20" :stroke-width="1.7" aria-hidden="true" />
          <span>Shortcuts</span>
        </button>
      </nav>

      <aside
        class="editor-v2__elements-panel"
        :class="{ 'editor-v2__sidebar--drop-active': mediaDropTarget === 'library' }"
        :aria-label="panelTitle + ' panel'"
        data-media-drop-zone="library"
        @dragenter="handleMediaDragOver($event, 'library')"
        @dragover="handleMediaDragOver($event, 'library')"
        @dragleave="handleMediaDragLeave($event, 'library')"
        @drop="handleMediaDrop"
      >
        <div v-if="mediaDropTarget === 'library'" class="editor-v2__sidebar-drop-overlay">
          <Upload :size="28" :stroke-width="1.6" aria-hidden="true" />
          <strong>Drop images into Your Media</strong>
          <span>They will not be placed on the card yet.</span>
        </div>
        <div class="editor-v2__panel-title">
          <h1>{{ panelTitle }}</h1>
          <span>{{ activePage.name }}</span>
        </div>

        <template v-if="activePanel === 'elements'">
          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Recently used</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__recent-row">
              <button type="button" draggable="true" aria-label="Add rectangle" title="Rectangle" @dragstart="startLibraryDrag({ kind: 'shape', preset: 'rectangle' }, $event)" @dragend="finishLibraryDrag" @click="addShapePreset('rectangle')">
                <Square :size="23" :stroke-width="1.5" aria-hidden="true" />
              </button>
              <button type="button" draggable="true" aria-label="Add ellipse" title="Ellipse" @dragstart="startLibraryDrag({ kind: 'shape', preset: 'ellipse' }, $event)" @dragend="finishLibraryDrag" @click="addShapePreset('ellipse')">
                <Circle :size="23" :stroke-width="1.5" aria-hidden="true" />
              </button>
              <button type="button" draggable="true" aria-label="Add text" title="Text" @dragstart="startLibraryDrag({ kind: 'text', preset: 'body' }, $event)" @dragend="finishLibraryDrag" @click="addTextPreset('body')">
                <Type :size="23" :stroke-width="1.5" aria-hidden="true" />
              </button>
            </div>
          </section>

          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Shapes</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__shape-grid">
              <button
                v-for="shape in shapeItems"
                :key="shape.id"
                type="button"
                draggable="true"
                @dragstart="startLibraryDrag({ kind: 'shape', preset: shape.preset }, $event)"
                @dragend="finishLibraryDrag"
                @click="addShapePreset(shape.preset)"
              >
                <component :is="shape.icon" :size="29" :stroke-width="1.5" aria-hidden="true" />
                <span>{{ shape.label }}</span>
              </button>
            </div>
          </section>

          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Frames &amp; grids</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__shape-grid editor-v2__frame-grid">
              <button
                v-for="frame in frameItems"
                :key="frame.id"
                type="button"
                draggable="true"
                :aria-label="'Add ' + frame.label"
                @dragstart="startLibraryDrag({ kind: 'frame', preset: frame.preset }, $event)"
                @dragend="finishLibraryDrag"
                @click="addFramePreset(frame.preset)"
              >
                <component :is="frame.icon" :size="29" :stroke-width="1.5" aria-hidden="true" />
                <span>{{ frame.label }}</span>
              </button>
            </div>
          </section>

          <section class="editor-v2__element-section editor-v2__element-section--compact">
            <div class="editor-v2__section-label">
              <span>Media</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <button class="editor-v2__quick-item" type="button" @click="openImagePicker()">
              <ImageIcon :size="18" :stroke-width="1.7" aria-hidden="true" />
              <span>Upload image</span>
            </button>
          </section>
        </template>

        <template v-else-if="activePanel === 'text'">
          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Add text</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <button class="editor-v2__primary-panel-action" type="button" draggable="true" @dragstart="startLibraryDrag({ kind: 'text', preset: 'body' }, $event)" @dragend="finishLibraryDrag" @click="addTextPreset('body')">
              <Type :size="17" :stroke-width="1.8" aria-hidden="true" />
              <span>Add a text box</span>
            </button>
            <div class="editor-v2__text-presets">
              <button
                v-for="preset in textPresetOptions"
                :key="preset.id"
                type="button"
                draggable="true"
                :class="'preset-' + preset.id"
                @dragstart="startLibraryDrag({ kind: 'text', preset: preset.id }, $event)"
                @dragend="finishLibraryDrag"
                @click="addTextPreset(preset.id)"
              >
                {{ preset.label }}
              </button>
            </div>
          </section>

          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Fonts</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <label class="editor-v2__font-search">
              <Search :size="15" :stroke-width="1.7" aria-hidden="true" />
              <input v-model="fontSearch" type="search" placeholder="Search fonts" aria-label="Search fonts">
              <span>{{ filteredFontOptions.length }}</span>
            </label>
            <div class="editor-v2__font-list">
              <button
                v-for="font in visibleFontOptions"
                :key="font.value"
                type="button"
                :class="{ active: selectedLayer?.type === 'text' && selectedLayer.fontFamily === font.value }"
                :style="{ fontFamily: font.value }"
                @click="setSelectedFont(font.value)"
              >
                <span>{{ font.label }}</span>
                <small>{{ font.kind }}</small>
              </button>
              <div v-if="!visibleFontOptions.length" class="editor-v2__panel-empty">No fonts found.</div>
              <button
                v-if="hiddenFontCount"
                class="editor-v2__font-more"
                type="button"
                @click="showMoreFonts"
              >
                Show more ({{ hiddenFontCount }})
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="activePanel === 'uploads'">
          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Upload</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <button class="editor-v2__upload-dropzone" type="button" @click="openImagePicker()">
              <Upload :size="22" :stroke-width="1.6" aria-hidden="true" />
              <strong>Upload images</strong>
              <span>PNG, JPEG, WebP or GIF</span>
            </button>
          </section>

          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Your media</span>
              <span>{{ mediaItems.length }}</span>
            </div>
            <p v-if="mediaImportError" class="editor-v2__media-error" role="alert">{{ mediaImportError }}</p>
            <div v-if="mediaItems.length" class="editor-v2__media-grid">
              <button
                v-for="media in mediaItems"
                :key="media.id"
                type="button"
                draggable="true"
                :title="'Add ' + media.name + ' or drag it onto the card'"
                :data-media-id="media.id"
                @dragstart="startLibraryDrag({ kind: 'media', id: media.id }, $event)"
                @dragend="finishLibraryDrag"
                @click="addMediaAsset(media)"
              >
                <img :src="media.src" :alt="media.name" draggable="false">
              </button>
            </div>
            <div v-else class="editor-v2__panel-empty">Drop images here or use Upload images.</div>
          </section>
        </template>

        <template v-else-if="activePanel === 'stock'">
  <section class="editor-v2__element-section editor-v2__stock-controls">
    <div class="editor-v2__stock-tabs" role="tablist" aria-label="Stock views">
      <button type="button" :class="{ active: stockView === 'browse' }" @click="stockView = 'browse'">
        <ImageIcon :size="15" :stroke-width="1.7" aria-hidden="true" />
        <span>Browse</span>
      </button>
      <button type="button" :class="{ active: stockView === 'favorites' }" @click="stockView = 'favorites'">
        <Star :size="15" :stroke-width="1.7" aria-hidden="true" />
        <span>Favorites</span>
      </button>
      <button type="button" :class="{ active: stockView === 'recent' }" @click="stockView = 'recent'">
        <RotateCw :size="15" :stroke-width="1.7" aria-hidden="true" />
        <span>Recent</span>
      </button>
    </div>
    <label class="editor-v2__stock-search">
      <Search :size="15" :stroke-width="1.7" aria-hidden="true" />
      <input v-model="stockQuery" type="search" placeholder="Search photos & illustrations" aria-label="Search stock assets" maxlength="80">
    </label>
    <div class="editor-v2__stock-kinds" aria-label="Stock types">
      <button
        v-for="option in stockKindOptions"
        :key="option.value"
        type="button"
        :class="{ active: stockKindFilter === option.value }"
        @click="stockKindFilter = option.value"
      >
        {{ option.label }}
      </button>
    </div>
    <p v-if="stockLoading" class="editor-v2__stock-status" role="status">Loading online stock...</p>
    <p v-else-if="stockError" class="editor-v2__stock-status editor-v2__stock-status--error" role="status">
      {{ stockError }}
    </p>
    <div class="editor-v2__section-label editor-v2__section-label--subtle">
      <span>Collections</span>
      <span>{{ stockCollections.length - 1 }}</span>
    </div>
    <div class="editor-v2__stock-collections">
      <button
        v-for="collection in stockCollections"
        :key="collection"
        type="button"
        :class="{ active: stockCollectionFilter === collection }"
        @click="stockCollectionFilter = collection"
      >
        {{ collection }}
      </button>
    </div>
  </section>

  <section v-if="stockResults.length" class="editor-v2__element-section">
    <div class="editor-v2__section-label">
      <span>{{ stockView === 'favorites' ? 'Favorites' : stockView === 'recent' ? 'Recent' : 'Results' }}</span>
      <span>{{ stockResults.length }}</span>
    </div>
    <div class="editor-v2__stock-grid">
      <article v-for="asset in stockResults" :key="asset.id" class="editor-v2__stock-card">
        <button
          class="editor-v2__stock-preview"
          type="button"
          draggable="true"
          :aria-label="'Add ' + asset.title"
          :title="'Add ' + asset.title"
          @dragstart="startLibraryDrag({ kind: 'stock', id: asset.id }, $event)"
          @dragend="finishLibraryDrag"
          @click="addStockAsset(asset)"
        >
          <img :src="asset.previewUrl" :alt="asset.title" crossorigin="anonymous" loading="lazy" draggable="false">
          <span
            class="editor-v2__stock-favorite"
            :class="{ active: stockFavorites.includes(asset.id) }"
            role="button"
            :aria-label="stockFavorites.includes(asset.id) ? 'Remove from favorites' : 'Add to favorites'"
            @click.stop="toggleStockFavorite(asset)"
          >
            <Star :size="14" :stroke-width="1.8" aria-hidden="true" />
          </span>
        </button>
        <div class="editor-v2__stock-meta">
          <strong>{{ asset.title }}</strong>
          <span :title="asset.credit">{{ asset.credit || asset.collection }}</span>
        </div>
      </article>
    </div>
    <button
      v-if="stockView === 'browse' && stockHasMore"
      class="editor-v2__stock-load-more"
      type="button"
      :disabled="stockLoading"
      @click="loadStockAssets(false)"
    >
      {{ stockLoading ? "Loading..." : "Load more" }}
    </button>
  </section>
  <div v-else class="editor-v2__panel-empty">
    {{ stockView === 'favorites' ? 'No favorites yet.' : stockView === 'recent' ? 'No recent stock assets.' : 'No stock assets match these filters.' }}
  </div>
</template>
<template v-else-if="activePanel === 'icon'">
          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Icon library</span>
              <span>{{ localIconCounts.all }}</span>
            </div>
            <form class="editor-v2__icon-search" @submit.prevent>
              <input
                v-model="iconQuery"
                type="search"
                aria-label="Search local icons"
                placeholder="Search icons"
                maxlength="80"
              >
            </form>
            <div class="editor-v2__icon-sources" role="tablist" aria-label="Icon sources">
              <button
                v-for="option in localIconSourceOptions"
                :key="option.value"
                type="button"
                role="tab"
                :aria-selected="iconSource === option.value"
                :class="{ active: iconSource === option.value }"
                @click="iconSource = option.value"
              >
                {{ option.label }}
              </button>
            </div>
            <span class="editor-v2__icon-status">{{ localIconCounts[iconSource] }} local icons</span>
          </section>

          <section v-if="localIconResults.length" class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Results</span>
              <span>{{ localIconResults.length }}</span>
            </div>
            <div class="editor-v2__icon-grid">
              <article
                v-for="icon in localIconResults"
                :key="icon.id"
                class="editor-v2__icon-card"
                data-local-icon-card
                :data-icon-source="icon.source"
                :data-icon-id="icon.id"
              >
                <button
                  class="editor-v2__icon-preview"
                  type="button"
                  draggable="true"
                  :aria-label="'Add ' + icon.label + ' icon'"
                  :title="'Add ' + icon.label"
                  @dragstart="startLibraryDrag({ kind: 'icon', id: icon.id }, $event)"
                  @dragend="finishLibraryDrag"
                  @click="addLocalIcon(icon)"
                >
                  <img :src="localIconAsset(icon)" :alt="icon.label" loading="lazy" draggable="false">
                </button>
                <div class="editor-v2__icon-meta">
                  <strong>{{ icon.label }}</strong>
                  <span>{{ icon.source }}</span>
                </div>
                <button class="editor-v2__icon-add" type="button" @click="addLocalIcon(icon)">Add</button>
              </article>
            </div>
          </section>
          <div v-else class="editor-v2__panel-empty">No local icons found.</div>
        </template>
        <template v-else-if="activePanel === 'layers'">
          <section class="editor-v2__layers-panel">
            <div class="editor-v2__section-label">
              <span>{{ activePage.name }} layers</span>
              <span>{{ activePage.layers.length }}</span>
            </div>
            <ol class="editor-v2__layers">
              <li
                v-for="layer in [...activePage.layers].reverse()"
                :key="layer.id"
                :class="{ active: layer.id === selectedLayerId, dragging: layer.id === draggedLayerId }"
                :draggable="true"
                :aria-grabbed="layer.id === draggedLayerId"
                @click="selectLayer(layer.id)"
                @dragstart="startLayerReorder(layer.id, $event)"
                @dragover.prevent
                @drop.prevent="dropLayerBefore(layer.id)"
                @dragend="draggedLayerId = null"
              >
                <GripVertical class="editor-v2__layer-grip" :size="14" :stroke-width="1.8" aria-hidden="true" />
                <div class="editor-v2__layer-main">
                  <component :is="layerIcon(layer)" :size="15" :stroke-width="1.7" aria-hidden="true" />
                  <span>{{ layer.name }}</span>
                </div>
                <div class="editor-v2__layer-actions">
                  <button type="button" :aria-label="'Toggle ' + layer.name + ' visibility'" title="Toggle visibility" @click.stop="toggleLayerVisibility(layer)">
                    <Eye v-if="layer.visible" :size="14" :stroke-width="1.7" aria-hidden="true" />
                    <EyeOff v-else :size="14" :stroke-width="1.7" aria-hidden="true" />
                  </button>
                  <button type="button" :aria-label="'Toggle ' + layer.name + ' lock'" title="Toggle lock" @click.stop="toggleLayerLock(layer)">
                    <LockKeyhole :size="14" :stroke-width="1.7" :class="{ muted: !layer.locked }" aria-hidden="true" />
                  </button>
                </div>
              </li>
            </ol>
          </section>
        </template>
      </aside>

      <main class="editor-v2__workspace" aria-label="Card workspace">
        <div class="editor-v2__canvas-tools" role="toolbar" aria-label="Canvas tools">
          <button
            v-for="tool in canvasTools"
            :key="tool.id"
            type="button"
            :class="{ active: activeTool === tool.id }"
            :aria-pressed="activeTool === tool.id"
            :aria-label="tool.label"
            :title="tool.label"
            @click="activateTool(tool.id)"
          >
            <component :is="tool.icon" :size="18" :stroke-width="1.7" aria-hidden="true" />
          </button>
          <span class="editor-v2__tool-divider" aria-hidden="true" />
          <button type="button" aria-label="Move canvas" title="Move canvas" @click="activeTool = 'select'">
            <Move :size="17" :stroke-width="1.7" aria-hidden="true" />
          </button>
          <button type="button" aria-label="Rotate" title="Rotate" @click="activeTool = 'select'">
            <RotateCw :size="17" :stroke-width="1.7" aria-hidden="true" />
          </button>
        </div>

        <div class="editor-v2__stage">
          <div class="editor-v2__card-heading">
            <div>
              <strong>{{ activePage.name }}</strong>
              <span>Business card</span>
            </div>
            <span class="editor-v2__card-size">{{ document.card.widthMm }} x {{ document.card.heightMm }} mm</span>
          </div>
          <div
            ref="canvasFrame"
            class="editor-v2__canvas-frame"
            :class="{ 'editor-v2__canvas-frame--drop-active': canvasDropActive }"
            @dragenter="handleCanvasDragOver"
            @dragover="handleCanvasDragOver"
            @dragleave="handleCanvasDragLeave"
            @drop="handleCanvasDrop"
          >
            <div
              v-if="showGrid"
              class="editor-v2__canvas-grid"
              :style="canvasGridStyle"
              data-canvas-grid
              aria-hidden="true"
            />
            <div
              v-if="showRulers"
              class="editor-v2__card-ruler editor-v2__card-ruler--top"
              :style="topRulerStyle"
              data-editor-ruler="top"
              aria-hidden="true"
            >
              <span
                v-for="tick in horizontalRulerTicks"
                :key="'x-' + tick.value"
                class="editor-v2__ruler-tick"
                :class="{ major: tick.major }"
                :style="{ left: tick.position + '%' }"
              >
                <i />
                <b v-if="tick.major">{{ tick.value }}</b>
              </span>
            </div>
            <div
              v-if="showRulers"
              class="editor-v2__card-ruler editor-v2__card-ruler--left"
              :style="leftRulerStyle"
              data-editor-ruler="left"
              aria-hidden="true"
            >
              <span
                v-for="tick in verticalRulerTicks"
                :key="'y-' + tick.value"
                class="editor-v2__ruler-tick"
                :class="{ major: tick.major }"
                :style="{ top: tick.position + '%' }"
              >
                <i />
                <b v-if="tick.major">{{ tick.value }}</b>
              </span>
            </div>
            <div v-if="canvasDropActive" class="editor-v2__canvas-drop-overlay" data-canvas-drop-overlay>
              <Upload :size="30" :stroke-width="1.6" aria-hidden="true" />
              <strong>Drop to place on the card</strong>
              <span>Images are also added to Your Media.</span>
            </div>
            <EditorCanvasV2
              :document="document"
              :page="activePage"
              :zoom="zoom"
              :pan-x="panX"
              :pan-y="panY"
              :show-guides="showPrintGuides"
              :selected-layer-id="selectedLayerId"
              @select-layer="selectLayer"
              @move-layer="moveLayer"
              @resize-layer="resizeLayer"
              @rotate-layer="rotateLayer"
              @layer-action="handleLayerAction"
            />
          </div>
        </div>

        <div ref="zoomControl" class="editor-v2__zoom" aria-label="Canvas zoom">
          <div v-if="zoomMenuOpen" class="editor-v2__zoom-menu" role="menu" aria-label="Zoom options">
            <button
              v-for="preset in zoomPresets"
              :key="preset"
              type="button"
              role="menuitem"
              :class="{ active: zoom === preset }"
              @click="setZoom(preset)"
            >
              {{ preset }}%
            </button>
            <span class="editor-v2__zoom-menu-divider" aria-hidden="true" />
            <button type="button" role="menuitem" @click="fitCard">Fit card</button>
            <button type="button" role="menuitem" :disabled="!selectedLayer" @click="zoomToSelection">Zoom to selection</button>
          </div>
          <button type="button" aria-label="Zoom out" title="Zoom out" @click="updateZoom(-10)">
            <Minus :size="15" :stroke-width="2" aria-hidden="true" />
          </button>
          <button
            type="button"
            class="editor-v2__zoom-value"
            aria-haspopup="menu"
            :aria-expanded="zoomMenuOpen"
            title="Zoom options"
            @click.stop="zoomMenuOpen = !zoomMenuOpen"
          >
            <span>{{ zoom }}%</span>
            <ChevronDown :size="11" :stroke-width="1.8" aria-hidden="true" />
          </button>
          <button type="button" aria-label="Zoom in" title="Zoom in" @click="updateZoom(10)">
            <Plus :size="15" :stroke-width="2" aria-hidden="true" />
          </button>
        </div>

        <div class="editor-v2__pages-dock" aria-label="Card sides">
          <div class="editor-v2__page-list">
            <button
              v-for="side in sides"
              :key="side"
              type="button"
              :class="{ active: activeSide === side }"
              :aria-pressed="activeSide === side"
              @click="selectPage(side)"
            >
              <span class="editor-v2__page-thumb" :style="{ background: document.pages[side].background }">
                <span>{{ side === "front" ? "F" : "B" }}</span>
              </span>
              <span class="editor-v2__page-label">
                <strong>{{ document.pages[side].name }}</strong>
                <small>{{ document.pages[side].layers.length }} layers</small>
              </span>
            </button>
          </div>
          <div class="editor-v2__page-meta">
            <span>2 sides</span>
            <strong>Fixed card format</strong>
          </div>
        </div>
      </main>

      <aside
        class="editor-v2__inspector"
        :class="{ 'editor-v2__sidebar--drop-active': mediaDropTarget === 'inspector' }"
        :aria-label="inspectorTitle + ' inspector'"
        data-media-drop-zone="inspector"
        @dragenter="handleMediaDragOver($event, 'inspector')"
        @dragover="handleMediaDragOver($event, 'inspector')"
        @dragleave="handleMediaDragLeave($event, 'inspector')"
        @drop="handleMediaDrop"
      >
        <div v-if="mediaDropTarget === 'inspector'" class="editor-v2__sidebar-drop-overlay">
          <Upload :size="28" :stroke-width="1.6" aria-hidden="true" />
          <strong>Drop images into Your Media</strong>
          <span>They will not be placed on the card yet.</span>
        </div>
        <div class="editor-v2__inspector-title">
          <div>
            <span>{{ selectedLayer?.type.toUpperCase() ?? "CARD" }}</span>
            <h1>{{ inspectorTitle }}</h1>
          </div>
          <div v-if="selectedLayer" class="editor-v2__inspector-title-actions">
            <button type="button" aria-label="Toggle selected layer visibility" title="Toggle visibility" @click="toggleLayerVisibility(selectedLayer)">
              <Eye v-if="selectedLayer.visible" :size="17" :stroke-width="1.7" aria-hidden="true" />
              <EyeOff v-else :size="17" :stroke-width="1.7" aria-hidden="true" />
            </button>
            <button type="button" aria-label="Toggle selected layer lock" title="Toggle lock" @click="toggleLayerLock(selectedLayer)">
              <LockKeyhole :size="17" :stroke-width="1.7" aria-hidden="true" />
            </button>
          </div>
        </div>

        <section
          v-if="preflightError || preflightReport || exportError"
          class="editor-v2__preflight-report"
          :class="{ passed: preflightReport?.valid }"
          aria-live="polite"
        >
          <p v-if="preflightError || exportError" class="editor-v2__preflight-error" role="alert">{{ preflightError || exportError }}</p>
          <template v-else-if="preflightReport">
            <div class="editor-v2__preflight-summary">
              <CircleCheck v-if="preflightReport.valid" :size="19" :stroke-width="1.8" aria-hidden="true" />
              <CircleAlert v-else :size="19" :stroke-width="1.8" aria-hidden="true" />
              <span>
                <strong>{{ preflightReport.valid ? "Preflight passed" : "Preflight found issues" }}</strong>
                <small v-if="preflightReport.issues.length">
                  {{ preflightErrorCount }} {{ preflightErrorCount === 1 ? "error" : "errors" }} /
                  {{ preflightWarningCount }} {{ preflightWarningCount === 1 ? "warning" : "warnings" }}
                </small>
                <small v-else>Ready for the next production step</small>
              </span>
            </div>
            <div v-if="preflightReport.issues.length" class="editor-v2__preflight-issues">
              <button
                v-for="(issue, index) in preflightReport.issues"
                :key="(issue.side ?? 'document') + '-' + issue.code + '-' + index"
                class="editor-v2__preflight-issue"
                :class="issue.level.toLowerCase()"
                type="button"
                :disabled="issue.side == null || issue.layerIndex == null"
                @click="focusPreflightIssue(issue)"
              >
                <CircleAlert :size="15" :stroke-width="1.8" aria-hidden="true" />
                <span>
                  <strong>
                    {{ issue.side === "front" ? "Front" : issue.side === "back" ? "Back" : "Document" }} / {{ issue.level }}
                  </strong>
                  <small>{{ issue.message }}</small>
                </span>
              </button>
            </div>
          </template>
        </section>

        <template v-if="selectedLayer">
          <button class="editor-v2__inspector-lock" type="button" @click="toggleLayerLock(selectedLayer)">
            <LockKeyhole :size="14" :stroke-width="1.8" aria-hidden="true" />
            <span>{{ selectedLayer.locked ? "Unlock layer" : "Lock for me" }}</span>
          </button>

          <section v-if="selectedLayer.type === 'text'" class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Text</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <textarea
              class="editor-v2__text-input"
              :value="selectedLayer.content ?? ''"
              aria-label="Selected text"
              @input="updateSelectedText"
            />
            <div class="editor-v2__segmented">
              <button type="button" @click="applyTextPreset('title')">Title</button>
              <button type="button" @click="applyTextPreset('subtitle')">Subtitle</button>
              <button type="button" @click="applyTextPreset('body')">Body</button>
            </div>
            <select class="editor-v2__select" :value="selectedLayer.fontFamily" aria-label="Font family" @change="updateSelectedFont">
              <option v-for="font in fontOptions" :key="font.value" :value="font.value">{{ font.label }}</option>
            </select>
            <div class="editor-v2__control-row">
              <select class="editor-v2__select" :value="selectedLayer.fontWeight" aria-label="Font weight" @change="updateSelectedWeight">
                <option :value="400">Regular</option>
                <option :value="500">Medium</option>
                <option :value="600">Semibold</option>
                <option :value="700">Bold</option>
              </select>
              <label class="editor-v2__compact-field">
                <span>Size</span>
                <input :value="selectedLayer.fontSize ?? 16" type="number" min="1" max="200" aria-label="Font size" @input="updateSelectedNumber('fontSize', $event)">
              </label>
            </div>
            <div class="editor-v2__icon-row" aria-label="Text formatting">
              <button type="button" aria-label="Bold" title="Bold" :class="{ active: (selectedLayer.fontWeight ?? 400) >= 700 }" @click="toggleBold">
                <Bold :size="15" :stroke-width="1.9" aria-hidden="true" />
              </button>
              <span class="editor-v2__icon-divider" aria-hidden="true" />
              <button type="button" aria-label="Align left" title="Align left" :class="{ active: selectedLayer.textAlign === 'left' }" @click="setTextAlign('left')">
                <AlignStartHorizontal :size="15" :stroke-width="1.8" aria-hidden="true" />
              </button>
              <button type="button" aria-label="Align center" title="Align center" :class="{ active: selectedLayer.textAlign === 'center' }" @click="setTextAlign('center')">
                <AlignCenterHorizontal :size="15" :stroke-width="1.8" aria-hidden="true" />
              </button>
              <button type="button" aria-label="Align right" title="Align right" :class="{ active: selectedLayer.textAlign === 'right' }" @click="setTextAlign('right')">
                <AlignEndHorizontal :size="15" :stroke-width="1.8" aria-hidden="true" />
              </button>
            </div>
          </section>

          <section v-else-if="selectedLayer.type === 'image'" class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Image</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <button
              class="editor-v2__secondary-panel-action"
              type="button"
              :disabled="paletteExtractingLayerId === selectedLayer.id"
              @click="extractPaletteFromSelected"
            >
              <Palette :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>
                {{ paletteExtractingLayerId === selectedLayer.id
                  ? "Extracting colors..."
                  : selectedLayer.extractedPalette?.length
                    ? "Re-extract palette"
                    : "Extract palette" }}
              </span>
            </button>
            <div v-if="selectedLayer.extractedPalette?.length" class="editor-v2__image-palette" aria-label="Extracted image palette">
              <span
                v-for="color in selectedLayer.extractedPalette"
                :key="color"
                :style="{ background: color }"
                :title="color.toUpperCase()"
                :data-palette-color="color"
              />
            </div>
            <span v-if="paletteExtractionError" class="editor-v2__image-error">{{ paletteExtractionError }}</span>
            <div class="editor-v2__image-inspector-preview">
              <img :src="safeImageSource(selectedLayer.src)" :alt="selectedLayer.name">
            </div>
            <button class="editor-v2__secondary-panel-action" type="button" @click="openImagePicker(selectedLayer.id)">
              <Upload :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>Replace image</span>
            </button>
            <div class="editor-v2__image-actions">
              <button
                class="editor-v2__secondary-panel-action"
                type="button"
                :disabled="backgroundRemovingLayerId === selectedLayer.id"
                @click="removeBackgroundFromSelected"
              >
                <ImageIcon :size="15" :stroke-width="1.8" aria-hidden="true" />
                <span>{{ backgroundRemovingLayerId === selectedLayer.id ? "Removing background..." : "Remove background" }}</span>
              </button>
              <button
                v-if="selectedLayer.processedSrc && selectedLayer.originalSrc && selectedLayer.src === selectedLayer.processedSrc"
                class="editor-v2__text-action"
                type="button"
                @click="restoreOriginalImage"
              >
                Use original
              </button>
            </div>
            <span v-if="selectedLayer.processedSrc" class="editor-v2__image-status">Background removed</span>
            <span v-if="backgroundRemovalError" class="editor-v2__image-error">{{ backgroundRemovalError }}</span>
          </section>

          <section v-else class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Shape</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__shape-swap" role="group" aria-label="Swap shape">
              <button
                v-for="shape in shapeItems"
                :key="shape.id"
                type="button"
                :class="{ active: selectedShapePreset === shape.preset }"
                :aria-pressed="selectedShapePreset === shape.preset"
                :aria-label="'Swap to ' + (shape.preset === 'rounded' ? 'rounded rectangle' : shape.label.toLowerCase())"
                :title="shape.label"
                @click="swapSelectedShape(shape.preset)"
              >
                <component :is="shape.icon" :class="{ 'editor-v2__rounded-shape-icon': shape.preset === 'rounded' }" :size="17" :stroke-width="1.8" aria-hidden="true" />
              </button>
            </div>
            <div class="editor-v2__selected-name">{{ selectedLayer.name }}</div>
          </section>

          <section v-if="selectedLayer.type !== 'image'" class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Fill</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div
              v-if="selectedLayer.type === 'rect' || selectedLayer.type === 'ellipse'"
              class="editor-v2__fill-mode"
              role="group"
              aria-label="Fill mode"
            >
              <button
                type="button"
                :class="{ active: selectedShapeFillMode === 'solid' }"
                :aria-pressed="selectedShapeFillMode === 'solid'"
                @click="setSelectedFillMode('solid')"
              >
                Solid
              </button>
              <button
                type="button"
                :class="{ active: selectedShapeFillMode !== 'solid' }"
                :aria-pressed="selectedShapeFillMode !== 'solid'"
                @click="setSelectedFillMode('gradient')"
              >
                Gradient
              </button>
            </div>
            <template v-if="selectedLayer.type === 'text' || selectedShapeFillMode === 'solid'">
              <div class="editor-v2__fill-control">
                <input :value="selectedLayer.fill ?? '#ffffff'" type="color" aria-label="Fill color" @input="updateSelectedFill">
                <span>{{ (selectedLayer.fill ?? "#ffffff").toUpperCase() }}</span>
              </div>
              <div class="editor-v2__swatches" aria-label="Color palette">
                <button
                  v-for="color in availablePalettes"
                  :key="color"
                  type="button"
                  :style="{ background: color }"
                  :aria-label="'Use ' + color"
                  :title="color"
                  @click="setSelectedFill(color)"
                />
              </div>
            </template>
            <template v-else>
              <div class="editor-v2__gradient-kind" role="group" aria-label="Gradient type">
                <button
                  type="button"
                  :class="{ active: selectedShapeFillMode === 'linear' }"
                  :aria-pressed="selectedShapeFillMode === 'linear'"
                  @click="setSelectedGradientKind('linear')"
                >
                  Linear
                </button>
                <button
                  type="button"
                  :class="{ active: selectedShapeFillMode === 'radial' }"
                  :aria-pressed="selectedShapeFillMode === 'radial'"
                  @click="setSelectedGradientKind('radial')"
                >
                  Radial
                </button>
              </div>
              <div class="editor-v2__gradient-preview" :style="{ background: shapeFillBackground(selectedLayer) }" aria-hidden="true" />
              <label v-if="selectedShapeFillMode === 'linear'" class="editor-v2__field-row">
                <span>Angle</span>
                <input
                  :value="selectedLayer.gradientAngle ?? 90"
                  type="number"
                  min="0"
                  max="360"
                  step="1"
                  aria-label="Gradient angle"
                  @input="updateSelectedGradientNumber('gradientAngle', $event)"
                >
              </label>
              <div v-else class="editor-v2__gradient-geometry">
                <label>
                  <span>Center X</span>
                  <input :value="selectedLayer.gradientCenterX ?? 50" type="number" min="0" max="100" aria-label="Gradient center X" @input="updateSelectedGradientNumber('gradientCenterX', $event)">
                </label>
                <label>
                  <span>Center Y</span>
                  <input :value="selectedLayer.gradientCenterY ?? 50" type="number" min="0" max="100" aria-label="Gradient center Y" @input="updateSelectedGradientNumber('gradientCenterY', $event)">
                </label>
                <label>
                  <span>Radius</span>
                  <input :value="selectedLayer.gradientRadius ?? 70" type="number" min="1" max="200" aria-label="Gradient radius" @input="updateSelectedGradientNumber('gradientRadius', $event)">
                </label>
              </div>
              <div class="editor-v2__gradient-stops">
                <div v-for="(stop, stopIndex) in selectedGradientStops" :key="stopIndex" class="editor-v2__gradient-stop">
                  <input
                    :value="stop.color"
                    type="color"
                    :aria-label="`Gradient stop ${stopIndex + 1} color`"
                    @input="updateGradientStop(stopIndex, 'color', $event)"
                  >
                  <label>
                    <span>Position</span>
                    <input
                      :value="Math.round(stop.offset * 100)"
                      type="number"
                      min="0"
                      max="100"
                      :aria-label="`Gradient stop ${stopIndex + 1} position`"
                      @input="updateGradientStop(stopIndex, 'offset', $event)"
                    >
                  </label>
                  <label>
                    <span>Opacity</span>
                    <input
                      :value="Math.round(stop.opacity * 100)"
                      type="number"
                      min="0"
                      max="100"
                      :aria-label="`Gradient stop ${stopIndex + 1} opacity`"
                      @input="updateGradientStop(stopIndex, 'opacity', $event)"
                    >
                  </label>
                  <button
                    type="button"
                    :disabled="(selectedLayer.gradientStops?.length ?? 0) <= 2"
                    :aria-label="`Remove gradient stop ${stopIndex + 1}`"
                    title="Remove stop"
                    @click="removeGradientStop(stopIndex)"
                  >
                    <Minus :size="14" :stroke-width="1.8" aria-hidden="true" />
                  </button>
                </div>
              </div>
              <button
                class="editor-v2__gradient-add"
                type="button"
                :disabled="selectedGradientStops.length >= 4"
                aria-label="Add gradient stop"
                @click="addGradientStop"
              >
                <Plus :size="14" :stroke-width="1.8" aria-hidden="true" />
                <span>Add stop</span>
              </button>
            </template>
          </section>

          <section v-if="selectedLayer.type === 'rect' || selectedLayer.type === 'ellipse'" class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Style</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__control-row">
              <label class="editor-v2__color-field">
                <span>Stroke</span>
                <input :value="selectedLayer.stroke ?? '#151817'" type="color" aria-label="Stroke color" @input="updateSelectedStroke">
              </label>
              <label class="editor-v2__compact-field">
                <span>Width</span>
                <input :value="selectedLayer.strokeWidth ?? 0" type="number" min="0" max="20" step="0.5" aria-label="Stroke width" @input="updateSelectedNumber('strokeWidth', $event)">
              </label>
            </div>
            <label v-if="selectedLayer.type === 'rect'" class="editor-v2__field-row">
              <span>Corner radius</span>
              <input :value="selectedLayer.cornerRadius ?? 0" type="number" min="0" max="50" aria-label="Corner radius" @input="updateSelectedNumber('cornerRadius', $event)">
            </label>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Geometry</strong>
              <Link2 :size="15" :stroke-width="1.7" aria-hidden="true" />
            </div>
            <div class="editor-v2__geometry-grid">
              <label><span>X <small>mm</small></span><input :value="geometryValueMm('x')" type="number" step="0.1" @input="updateSelectedGeometry('x', $event)"></label>
              <label><span>Y <small>mm</small></span><input :value="geometryValueMm('y')" type="number" step="0.1" @input="updateSelectedGeometry('y', $event)"></label>
              <label><span>W <small>mm</small></span><input :value="geometryValueMm('width')" type="number" min="0.1" step="0.1" @input="updateSelectedGeometry('width', $event)"></label>
              <label><span>H <small>mm</small></span><input :value="geometryValueMm('height')" type="number" min="0.1" step="0.1" @input="updateSelectedGeometry('height', $event)"></label>
            </div>
            <label class="editor-v2__field-row">
              <span>Rotation</span>
              <input :value="selectedLayer.rotation" type="number" min="0" max="359" step="1" aria-label="Rotation" @input="updateSelectedNumber('rotation', $event)">
            </label>
          </section>

          <section v-if="selectedLayer.type === 'rect' || selectedLayer.type === 'ellipse'" class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Effects</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__effect-modes" role="group" aria-label="Shape effect">
              <button
                v-for="effect in (['none', 'shadow', 'hollow', 'glow'] as const)"
                :key="effect"
                type="button"
                :class="{ active: selectedShapeEffect === effect }"
                :aria-pressed="selectedShapeEffect === effect"
                @click="setSelectedShapeEffect(effect)"
              >
                {{ effect.charAt(0).toUpperCase() + effect.slice(1) }}
              </button>
            </div>
            <template v-if="selectedShapeEffect === 'shadow'">
              <div class="editor-v2__effect-grid">
                <label class="editor-v2__effect-color">
                  <span>Color</span>
                  <input :value="selectedLayer.shadowColor ?? '#111827'" type="color" aria-label="Shadow color" @input="updateSelectedEffectColor">
                </label>
                <label>
                  <span>X</span>
                  <input :value="selectedLayer.shadowX ?? 0" type="number" min="-100" max="100" aria-label="Shadow X" @input="updateSelectedEffectNumber('shadowX', $event)">
                </label>
                <label>
                  <span>Y</span>
                  <input :value="selectedLayer.shadowY ?? 4" type="number" min="-100" max="100" aria-label="Shadow Y" @input="updateSelectedEffectNumber('shadowY', $event)">
                </label>
                <label>
                  <span>Blur</span>
                  <input :value="selectedLayer.shadowBlur ?? 16" type="number" min="0" max="100" aria-label="Shadow blur" @input="updateSelectedEffectNumber('shadowBlur', $event)">
                </label>
                <label>
                  <span>Opacity</span>
                  <input :value="Math.round((selectedLayer.shadowOpacity ?? 0.35) * 100)" type="number" min="0" max="100" aria-label="Shadow opacity" @input="updateSelectedEffectNumber('shadowOpacity', $event)">
                </label>
              </div>
            </template>
            <template v-else-if="selectedShapeEffect === 'glow'">
              <div class="editor-v2__effect-grid editor-v2__effect-grid--glow">
                <label class="editor-v2__effect-color">
                  <span>Color</span>
                  <input :value="selectedLayer.shadowColor ?? selectedLayer.fill ?? '#3b82f6'" type="color" aria-label="Glow color" @input="updateSelectedEffectColor">
                </label>
                <label>
                  <span>Size</span>
                  <input :value="selectedLayer.shadowBlur ?? 16" type="number" min="0" max="100" aria-label="Glow size" @input="updateSelectedEffectNumber('shadowBlur', $event)">
                </label>
                <label>
                  <span>Opacity</span>
                  <input :value="Math.round((selectedLayer.shadowOpacity ?? 0.7) * 100)" type="number" min="0" max="100" aria-label="Glow opacity" @input="updateSelectedEffectNumber('shadowOpacity', $event)">
                </label>
              </div>
            </template>
            <div v-else-if="selectedShapeEffect === 'hollow'" class="editor-v2__hollow-preview" aria-label="Hollow uses the stroke settings">
              <span :style="{ borderColor: selectedLayer.stroke ?? '#111827', borderWidth: `${Math.max(2, selectedLayer.strokeWidth ?? 0)}px` }" />
              <strong>Stroke only</strong>
            </div>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Arrange</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__arrange-grid">
              <button type="button" @click="handleLayerAction({ layerId: selectedLayer.id, action: 'duplicate' })">
                <Copy :size="15" :stroke-width="1.8" aria-hidden="true" />
                <span>Duplicate</span>
              </button>
              <button type="button" @click="handleLayerAction({ layerId: selectedLayer.id, action: 'bring-forward' })">
                <ArrowUp :size="15" :stroke-width="1.8" aria-hidden="true" />
                <span>Forward</span>
              </button>
              <button type="button" @click="handleLayerAction({ layerId: selectedLayer.id, action: 'send-backward' })">
                <ArrowDown :size="15" :stroke-width="1.8" aria-hidden="true" />
                <span>Backward</span>
              </button>
              <button class="danger" type="button" @click="handleLayerAction({ layerId: selectedLayer.id, action: 'delete' })">
                <Trash2 :size="15" :stroke-width="1.8" aria-hidden="true" />
                <span>Delete</span>
              </button>
            </div>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Appearance</strong>
              <span>{{ Math.round((selectedLayer.opacity ?? 1) * 100) }}%</span>
            </div>
            <input
              class="editor-v2__range"
              :value="Math.round((selectedLayer.opacity ?? 1) * 100)"
              type="range"
              min="0"
              max="100"
              aria-label="Layer opacity"
              @input="updateSelectedOpacity"
            >
          </section>
        </template>

        <template v-else>
          <section class="editor-v2__inspector-section editor-v2__page-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Page size</strong>
              <span>Fixed</span>
            </div>
            <div class="editor-v2__page-size">
              <strong>{{ document.card.widthMm }} x {{ document.card.heightMm }} mm</strong>
              <span>Business card</span>
            </div>
            <div class="editor-v2__page-dimensions">
              <label>W <input :value="document.card.widthMm" type="number" disabled></label>
              <label>H <input :value="document.card.heightMm" type="number" disabled></label>
            </div>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Print guides</strong>
              <span>Fixed</span>
            </div>
            <label class="editor-v2__guide-toggle">
              <span>
                <strong>Show guides</strong>
                <small>2 mm bleed, 3 mm safe zone</small>
              </span>
              <input v-model="showPrintGuides" type="checkbox" aria-label="Show print guides">
            </label>
            <div class="editor-v2__guide-legend" aria-hidden="true">
              <span><i class="bleed" />Bleed</span>
              <span><i class="safe" />Safe zone</span>
            </div>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Side</strong>
              <span>2 sides</span>
            </div>
            <div class="editor-v2__side-segmented">
              <button
                v-for="side in sides"
                :key="side"
                type="button"
                :class="{ active: activeSide === side }"
                @click="selectPage(side)"
              >
                {{ side === "front" ? "Front" : "Back" }}
              </button>
            </div>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Print export</strong>
              <span>{{ printExportSpec.dpi }} DPI</span>
            </div>
            <div class="editor-v2__print-export-summary">
              <span>PDF / Front + Back</span>
              <strong>{{ printExportSpec.pageWidthMm }} x {{ printExportSpec.pageHeightMm }} mm</strong>
              <small>{{ printExportSpec.bleedMm }} mm bleed on every edge</small>
            </div>
            <button
              class="editor-v2__print-export-button"
              type="button"
              :disabled="exportState === 'exporting' || preflightLoading || saveState === 'saving' || saveState === 'loading'"
              @click="exportPrintPdf"
            >
              <Download :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>{{ exportState === "exporting" ? "Preparing PDF" : "Export print PDF" }}</span>
            </button>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Background</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__background-control">
              <input
                :value="activePage.background === 'transparent' ? '#ffffff' : activePage.background"
                type="color"
                aria-label="Page background"
                @input="updatePageBackground"
              >
              <span>{{ activePage.background === "transparent" ? "None" : activePage.background.toUpperCase() }}</span>
              <button type="button" @click="setPageBackground('transparent')">None</button>
            </div>
            <div class="editor-v2__swatches" aria-label="Page color palette">
              <button
                v-for="color in availablePalettes"
                :key="color"
                type="button"
                :style="{ background: color }"
                :aria-label="'Use ' + color"
                :title="color"
                @click="setPageBackground(color)"
              />
            </div>
          </section>
        </template>

        <div class="editor-v2__inspector-footer">
          <span>Document</span>
          <strong>{{ document.name }}</strong>
          <span>Side</span>
          <strong>{{ activePage.name }}</strong>
        </div>
      </aside>
    </div>

    <div
      v-if="previewOpen"
      class="editor-v2__preview-backdrop"
      role="presentation"
      @pointerdown.self="closePreview"
    >
      <section
        class="editor-v2__preview-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="editor-preview-title"
        data-preview-dialog
      >
        <header>
          <div>
            <span>Print preview</span>
            <h2 id="editor-preview-title">{{ document.name }}</h2>
          </div>
          <button type="button" aria-label="Close preview" title="Close preview" @click="closePreview">
            <X :size="18" :stroke-width="1.8" aria-hidden="true" />
          </button>
        </header>
        <div class="editor-v2__preview-tabs" role="tablist" aria-label="Preview card sides">
          <button
            v-for="side in sides"
            :key="side"
            type="button"
            role="tab"
            :aria-selected="previewSide === side"
            :class="{ active: previewSide === side }"
            @click="previewSide = side"
          >
            {{ side === "front" ? "Front" : "Back" }}
          </button>
        </div>
        <div class="editor-v2__preview-body">
          <div class="editor-v2__preview-label">
            <strong>{{ previewSide === "front" ? "Front" : "Back" }}</strong>
            <span>{{ document.card.widthMm }} x {{ document.card.heightMm }} mm</span>
          </div>
          <CanvasPreview
            :layers="document.pages[previewSide].layers"
            :width-mm="document.card.widthMm"
            :height-mm="document.card.heightMm"
            :label="document.pages[previewSide].name + ' print preview'"
            empty-label="This side is empty"
            :background="document.pages[previewSide].background"
          />
        </div>
        <footer>
          <span>Preview does not change the design.</span>
          <div>
            <button type="button" class="editor-v2__preview-close" @click="closePreview">Back to editor</button>
            <button
              type="button"
              class="editor-v2__preview-export"
              :disabled="exportState === 'exporting' || preflightLoading || saveState === 'saving' || saveState === 'loading'"
              @click="exportPrintPdf"
            >
              <Download :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>Export PDF</span>
            </button>
          </div>
        </footer>
      </section>
    </div>
    <div
      v-if="shortcutsOpen"
      class="editor-v2__shortcut-backdrop"
      role="presentation"
      @pointerdown.self="closeShortcutDialog"
    >
      <section
        class="editor-v2__shortcut-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="editor-shortcuts-title"
        data-shortcut-dialog
      >
        <header>
          <h2 id="editor-shortcuts-title">Keyboard shortcuts</h2>
          <button type="button" aria-label="Close keyboard shortcuts" title="Close" @click="closeShortcutDialog">
            <X :size="18" :stroke-width="1.8" aria-hidden="true" />
          </button>
        </header>
        <label class="editor-v2__shortcut-search">
          <Search :size="16" :stroke-width="1.7" aria-hidden="true" />
          <input
            ref="shortcutSearchInput"
            v-model="shortcutQuery"
            type="search"
            placeholder="Search shortcuts"
            aria-label="Search keyboard shortcuts"
          >
        </label>
        <div class="editor-v2__shortcut-results">
          <section v-for="group in filteredShortcutGroups" :key="group.label">
            <h3>{{ group.label }}</h3>
            <dl>
              <div v-for="item in group.items" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd>
                  <kbd v-for="keyName in item.keys" :key="keyName">{{ keyName }}</kbd>
                </dd>
              </div>
            </dl>
          </section>
          <p v-if="!filteredShortcutGroups.length" class="editor-v2__shortcut-empty">No shortcuts found.</p>
        </div>
        <footer>Press ? anywhere in the editor to open this reference.</footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.editor-v2__file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.editor-v2 {
  --editor-ink: #25262d;
  --editor-muted: #777984;
  --editor-line: #e0e1e5;
  --editor-workspace: #e8e8eb;
  --editor-soft: #f4f4f6;
  --editor-accent: #bd347f;
  --editor-accent-hover: #a82d70;
  --editor-accent-soft: #f8e8f1;
  --sidebar: #191a20;
  --sidebar-raised: #202128;
  --sidebar-line: #30313a;
  --sidebar-text: #f2f2f5;
  --sidebar-muted: #999aa5;
  width: 100%;
  min-height: 100svh;
  min-height: 100dvh;
  overflow: hidden;
  background: var(--editor-workspace);
  color: var(--editor-ink);
  font-family: Aptos, "Segoe UI", sans-serif;
}

button,
a,
input,
select,
textarea {
  font: inherit;
}

button,
a {
  -webkit-tap-highlight-color: transparent;
}

.editor-v2__header {
  position: relative;
  z-index: 12;
  height: 54px;
  min-height: 54px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid var(--editor-line);
  background: #ffffff;
  padding: 0 14px;
}

.editor-v2__header-left,
.editor-v2__header-center,
.editor-v2__header-actions,
.editor-v2__document-name,
.editor-v2__status,
.editor-v2__fill-control,
.editor-v2__control-row,
.editor-v2__icon-row,
.editor-v2__page-list,
.editor-v2__page-meta {
  display: flex;
  align-items: center;
}

.editor-v2__header-left,
.editor-v2__header-actions {
  min-width: 0;
  gap: 9px;
}

.editor-v2__header-left {
  justify-self: start;
}

.editor-v2__header-center {
  justify-self: center;
  gap: 4px;
  color: var(--editor-muted);
  font-size: 11px;
}

.editor-v2__header-actions {
  justify-self: end;
}

.editor-v2__status-cluster {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.editor-v2__header-center button,
.editor-v2__header-icon,
.editor-v2__back,
.editor-v2__inspector-title button,
.editor-v2__icon-row button,
.editor-v2__layer-actions button,
.editor-v2__zoom button {
  display: inline-grid;
  place-items: center;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.editor-v2__header-center button,
.editor-v2__header-icon {
  width: 30px;
  height: 30px;
  border-radius: 5px;
}

.editor-v2__header-center button:hover:not(:disabled),
.editor-v2__header-center button.active,
.editor-v2__header-icon:hover {
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
}

.editor-v2__header-tool-divider {
  width: 1px;
  height: 20px;
  background: var(--editor-line);
  margin: 0 3px;
}

.editor-v2__header-center button:disabled {
  cursor: not-allowed;
  opacity: 0.36;
}

.editor-v2__back {
  width: 28px;
  height: 30px;
  color: #56565e;
  border-radius: 5px;
}

.editor-v2__back:hover {
  background: var(--editor-soft);
}

.editor-v2__mark {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--editor-accent);
  color: #ffffff;
  font-size: 15px;
  font-weight: 800;
}

.editor-v2__brand {
  font-size: 14px;
  letter-spacing: 0;
}

.editor-v2__divider {
  width: 1px;
  height: 22px;
  background: var(--editor-line);
}

.editor-v2__document-name {
  min-width: 0;
  align-items: flex-start;
  flex-direction: column;
  gap: 1px;
}

.editor-v2__document-name strong {
  max-width: min(28vw, 300px);
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__document-name span {
  color: var(--editor-muted);
  font-size: 10px;
}

.editor-v2__status {
  gap: 6px;
  white-space: nowrap;
}

.editor-v2__status-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4baf86;
}

.editor-v2__status-dot--error {
  background: #df7b32;
}

.editor-v2__preflight,
.editor-v2__checkout,
.editor-v2__save {
  height: 31px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--editor-line);
  border-radius: 5px;
  background: #ffffff;
  color: var(--editor-ink);
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  padding: 0 10px;
}

.editor-v2__checkout {
  width: 31px;
  justify-content: center;
  padding: 0;
}

.editor-v2__save {
  border-color: var(--editor-accent);
  background: var(--editor-accent);
  color: #ffffff;
}

.editor-v2__preflight:hover,
.editor-v2__checkout:hover {
  background: var(--editor-soft);
}

.editor-v2__save:hover {
  background: var(--editor-accent-hover);
}

.editor-v2__preflight:disabled,
.editor-v2__checkout:disabled,
.editor-v2__save:disabled {
  cursor: wait;
  opacity: 0.65;
}

.editor-v2__layout {
  height: calc(100svh - 54px);
  height: calc(100dvh - 54px);
  display: grid;
  grid-template-columns: 68px 288px minmax(0, 1fr) 330px;
  min-height: 0;
}

.editor-v2__rail,
.editor-v2__elements-panel,
.editor-v2__inspector {
  min-width: 0;
  overflow: auto;
  background: var(--sidebar);
  color: var(--sidebar-text);
  scrollbar-color: #51525d transparent;
  scrollbar-width: thin;
}

.editor-v2__rail {
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border-right: 1px solid var(--sidebar-line);
  padding: 10px 5px;
}

.editor-v2__rail button {
  width: 58px;
  min-height: 56px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 5px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #b6b7c1;
  cursor: pointer;
  font-size: 9px;
  padding: 5px 2px;
}

.editor-v2__rail button:hover {
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
}

.editor-v2__rail button.active {
  background: #38172b;
  color: #f36db4;
  font-weight: 700;
}

.editor-v2__rail .editor-v2__shortcut-launcher {
  margin-top: auto;
  border-top: 1px solid var(--sidebar-line);
  border-radius: 0 0 6px 6px;
}

.editor-v2__elements-panel {
  position: relative;
  border-right: 1px solid var(--sidebar-line);
  padding: 20px 16px 90px;
}

.editor-v2__inspector {
  position: relative;
}

.editor-v2__elements-panel [draggable="true"] {
  cursor: grab;
}

.editor-v2__elements-panel [draggable="true"]:active {
  cursor: grabbing;
}

.editor-v2__sidebar--drop-active {
  outline: 2px solid #f05aa8;
  outline-offset: -2px;
}

.editor-v2__sidebar-drop-overlay {
  position: absolute;
  inset: 8px;
  z-index: 30;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  border: 2px dashed #f16bb2;
  border-radius: 6px;
  background: rgb(24 25 31 / 94%);
  color: #fff;
  padding: 24px;
  text-align: center;
  pointer-events: none;
}

.editor-v2__sidebar-drop-overlay span {
  max-width: 230px;
  color: #b8bac5;
  font-size: 11px;
  line-height: 1.5;
}

.editor-v2__media-error {
  margin: 0 0 10px;
  color: #ff9bad;
  font-size: 11px;
  line-height: 1.45;
}

.editor-v2__panel-title,
.editor-v2__section-label,
.editor-v2__inspector-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.editor-v2__panel-title {
  margin-bottom: 24px;
}

.editor-v2__panel-title h1,
.editor-v2__inspector-title h1 {
  margin: 0;
  color: var(--sidebar-text);
  font-size: 15px;
  font-weight: 800;
}

.editor-v2__panel-title > span {
  color: var(--sidebar-muted);
  font-size: 10px;
}

.editor-v2__element-section {
  margin-bottom: 26px;
}

.editor-v2__element-section--compact {
  padding-top: 2px;
}

.editor-v2__section-label {
  margin-bottom: 11px;
  color: var(--sidebar-muted);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.editor-v2__recent-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
}

.editor-v2__recent-row button {
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  border: 1px solid var(--sidebar-line);
  border-radius: 6px;
  background: var(--sidebar-raised);
  color: #c8c9d1;
  cursor: pointer;
}

.editor-v2__recent-row button:hover {
  border-color: #74405e;
  color: #f36db4;
}

.editor-v2__shape-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.editor-v2__shape-grid button {
  min-width: 0;
  aspect-ratio: 1.12;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #15161b;
  color: #c9cad2;
  cursor: pointer;
  padding: 10px 4px;
}

.editor-v2__shape-grid button:hover {
  border-color: #723b5b;
  background: #271823;
  color: #f36db4;
}

.editor-v2__shape-grid button span {
  color: var(--sidebar-text);
  font-size: 11px;
}

.editor-v2__quick-item,
.editor-v2__primary-panel-action,
.editor-v2__secondary-panel-action {
  width: 100%;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--sidebar-line);
  border-radius: 6px;
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  padding: 0 10px;
}

.editor-v2__quick-item:hover,
.editor-v2__secondary-panel-action:hover {
  border-color: #75405f;
  color: #f36db4;
}

.editor-v2__secondary-panel-action:disabled {
  cursor: wait;
  opacity: 0.58;
}

.editor-v2__primary-panel-action {
  border-color: var(--editor-accent);
  background: var(--editor-accent);
  color: #ffffff;
}

.editor-v2__primary-panel-action:hover {
  background: var(--editor-accent-hover);
}

.editor-v2__text-presets {
  display: grid;
  gap: 7px;
  margin-top: 9px;
}

.editor-v2__text-presets button {
  min-height: 48px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #15161b;
  color: var(--sidebar-text);
  cursor: pointer;
  padding: 0 14px;
  text-align: left;
}

.editor-v2__text-presets button:hover {
  border-color: #75405f;
  color: #f36db4;
}

.editor-v2__text-presets .preset-title {
  font-size: 20px;
  font-weight: 700;
}

.editor-v2__text-presets .preset-subtitle {
  font-size: 15px;
  font-weight: 600;
}

.editor-v2__text-presets .preset-body {
  font-size: 12px;
}

.editor-v2__font-list {
  display: grid;
  gap: 4px;
}

.editor-v2__font-search {
  height: 36px;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  padding: 0 9px;
  border: 1px solid #30323a;
  border-radius: 5px;
  background: #17181e;
  color: var(--sidebar-muted);
}

.editor-v2__font-search:focus-within {
  border-color: #b4367d;
  color: #f36db4;
}

.editor-v2__font-search input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--sidebar-text);
  font: inherit;
  font-size: 11px;
}

.editor-v2__font-search input::placeholder {
  color: #777984;
}

.editor-v2__font-search > span {
  color: #777984;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.editor-v2__font-list button {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid transparent;
  border-radius: 5px;
  background: #15161b;
  color: var(--sidebar-text);
  cursor: pointer;
  padding: 0 11px;
  text-align: left;
}

.editor-v2__font-list button:hover {
  border-color: #75405f;
  color: #f36db4;
}

.editor-v2__font-list button.active {
  border-color: #d44792;
  background: #351424;
  color: #ff8fc7;
}

.editor-v2__font-list button.editor-v2__font-more {
  justify-content: center;
  min-height: 34px;
  font-family: Aptos, "Segoe UI", sans-serif;
  font-size: 10px;
  font-weight: 700;
}

.editor-v2__font-list small {
  color: #666873;
  font-family: Aptos, "Segoe UI", sans-serif;
  font-size: 8px;
  text-transform: uppercase;
}

.editor-v2__upload-dropzone {
  width: 100%;
  min-height: 132px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px dashed #555761;
  border-radius: 6px;
  background: #17181e;
  color: #f36db4;
  cursor: pointer;
}

.editor-v2__upload-dropzone:hover {
  border-color: #f36db4;
  background: #241821;
}

.editor-v2__upload-dropzone strong {
  color: var(--sidebar-text);
  font-size: 12px;
}

.editor-v2__upload-dropzone span {
  color: var(--sidebar-muted);
  font-size: 9px;
}

.editor-v2__media-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.editor-v2__media-grid button {
  aspect-ratio: 1.2;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 5px;
  background: #101116;
  cursor: pointer;
  padding: 0;
}

.editor-v2__media-grid button:hover,
.editor-v2__media-grid button.active {
  border-color: #f36db4;
}

.editor-v2__media-grid img,
.editor-v2__image-inspector-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.editor-v2__panel-empty {
  min-height: 96px;
  display: grid;
  place-items: center;
  color: var(--sidebar-muted);
  font-size: 10px;
  text-align: center;
}

.editor-v2__stock-tabs,
.editor-v2__stock-kinds,
.editor-v2__stock-collections {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.editor-v2__stock-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 9px;
}

.editor-v2__stock-tabs button,
.editor-v2__stock-kinds button,
.editor-v2__stock-collections button {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px solid #30323a;
  border-radius: 999px;
  background: #17181e;
  color: var(--sidebar-muted);
  cursor: pointer;
  padding: 0 9px;
  font: inherit;
  font-size: 10px;
  white-space: nowrap;
}

.editor-v2__stock-tabs button:hover,
.editor-v2__stock-tabs button.active,
.editor-v2__stock-kinds button:hover,
.editor-v2__stock-kinds button.active,
.editor-v2__stock-collections button:hover,
.editor-v2__stock-collections button.active {
  border-color: #d44792;
  background: #351424;
  color: #ff8fc7;
}

.editor-v2__stock-search {
  height: 36px;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 7px;
  margin-bottom: 9px;
  padding: 0 9px;
  border: 1px solid #30323a;
  border-radius: 5px;
  background: #17181e;
  color: var(--sidebar-muted);
}

.editor-v2__stock-search:focus-within {
  border-color: #b4367d;
  color: #f36db4;
}

.editor-v2__stock-search input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--sidebar-text);
  font: inherit;
  font-size: 11px;
}

.editor-v2__stock-search input::placeholder {
  color: #777984;
}

.editor-v2__section-label--subtle {
  margin-top: 12px;
  color: #777984;
  font-size: 9px;
  text-transform: uppercase;
}

.editor-v2__stock-collections {
  max-height: 76px;
  overflow: auto;
  padding-right: 2px;
}

.editor-v2__stock-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.editor-v2__stock-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #2c2e36;
  border-radius: 6px;
  background: #15161b;
}

.editor-v2__stock-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 1.18;
  display: block;
  overflow: hidden;
  border: 0;
  background: #22242c;
  cursor: pointer;
  padding: 0;
}

.editor-v2__stock-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transition: transform 160ms ease;
}

.editor-v2__stock-preview:hover img {
  transform: scale(1.04);
}

.editor-v2__stock-favorite {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(21, 22, 27, 0.78);
  color: #e1e3ea;
}

.editor-v2__stock-favorite.active {
  background: #351424;
  color: #ff8fc7;
}

.editor-v2__stock-meta {
  display: grid;
  gap: 3px;
  padding: 7px 8px 8px;
}

.editor-v2__stock-meta strong,
.editor-v2__stock-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__stock-meta strong {
  color: var(--sidebar-text);
  font-size: 10px;
}

.editor-v2__stock-meta span {
  color: var(--sidebar-muted);
  font-size: 9px;
}

.editor-v2__stock-status {
  margin: 9px 0 0;
  color: var(--sidebar-muted);
  font-size: 9px;
  line-height: 1.45;
}

.editor-v2__stock-status--error {
  color: #f2a9c9;
}

.editor-v2__stock-load-more {
  width: 100%;
  min-height: 34px;
  margin-top: 10px;
  border: 1px solid #b4367d;
  border-radius: 5px;
  background: #351424;
  color: #ff8fc7;
  cursor: pointer;
  font: inherit;
  font-size: 10px;
  font-weight: 700;
}

.editor-v2__stock-load-more:hover:not(:disabled) {
  border-color: #f36db4;
  background: #4a1931;
}

.editor-v2__stock-load-more:disabled {
  cursor: wait;
  opacity: 0.55;
}

.editor-v2__icon-search {
  display: grid;
  gap: 8px;
}

.editor-v2__icon-search input,
.editor-v2__icon-search select,
.editor-v2__icon-search button {
  width: 100%;
  min-height: 34px;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: #15161b;
  color: var(--sidebar-text);
  font-size: 11px;
}

.editor-v2__icon-search input,
.editor-v2__icon-search select {
  padding: 0 9px;
}

.editor-v2__icon-search button,
.editor-v2__icon-add {
  background: #351424;
  color: #ff8fc7;
  cursor: pointer;
  font-weight: 700;
}

.editor-v2__icon-search button:hover:not(:disabled),
.editor-v2__icon-add:hover {
  border-color: #f36db4;
  background: #4a1b38;
}

.editor-v2__icon-search button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.editor-v2__icon-sources {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 5px;
  margin-top: 8px;
}

.editor-v2__icon-sources button {
  min-height: 28px;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: #15161b;
  color: var(--sidebar-muted);
  cursor: pointer;
  font-size: 10px;
  font-weight: 700;
}

.editor-v2__icon-sources button.active {
  border-color: #f36db4;
  background: #351424;
  color: #ff8fc7;
}
.editor-v2__icon-credit {
  display: inline-block;
  margin-top: 9px;
  color: #f36db4;
  font-size: 10px;
  text-decoration: none;
}

.editor-v2__icon-credit:hover {
  text-decoration: underline;
}

.editor-v2__icon-status {
  display: block;
  margin-top: 8px;
  color: var(--sidebar-muted);
  font-size: 10px;
  line-height: 1.45;
}

.editor-v2__icon-status--error {
  color: #ff9aa9;
}

.editor-v2__icon-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.editor-v2__icon-card {
  min-width: 0;
  display: grid;
  gap: 7px;
  border: 1px solid var(--sidebar-line);
  border-radius: 6px;
  background: #15161b;
  padding: 7px;
}

.editor-v2__icon-preview {
  width: 100%;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: #ffffff;
  cursor: pointer;
  padding: 8px;
}

.editor-v2__icon-preview:hover {
  outline: 2px solid #f36db4;
  outline-offset: -2px;
}

.editor-v2__icon-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.editor-v2__icon-meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.editor-v2__icon-meta strong,
.editor-v2__icon-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__icon-meta strong {
  color: var(--sidebar-text);
  font-size: 10px;
}

.editor-v2__icon-meta span {
  color: var(--sidebar-muted);
  font-size: 9px;
}

.editor-v2__icon-add {
  min-height: 28px;
  border: 1px solid #673252;
  border-radius: 4px;
  font-size: 10px;
}

.editor-v2__brand-swatches {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 9px;
}

.editor-v2__brand-swatches button {
  aspect-ratio: 1;
  border: 2px solid #3a3b44;
  border-radius: 6px;
  cursor: pointer;
}

.editor-v2__brand-swatches button:hover {
  border-color: #f36db4;
}

.editor-v2__brand-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid var(--sidebar-line);
  border-bottom: 1px solid var(--sidebar-line);
  padding: 14px 2px;
}

.editor-v2__brand-summary > div {
  display: grid;
  gap: 3px;
}

.editor-v2__brand-summary strong {
  font-size: 11px;
}

.editor-v2__brand-summary span {
  color: var(--sidebar-muted);
  font-size: 9px;
}

.editor-v2__layers-panel {
  margin-top: 4px;
}

.editor-v2__layers {
  display: grid;
  gap: 3px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.editor-v2__layers li {
  min-width: 0;
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: 5px;
  color: #c4c5cd;
  cursor: pointer;
  padding: 0 6px 0 3px;
}

.editor-v2__layers li:hover,
.editor-v2__layers li.active {
  border-color: #573147;
  background: #321627;
  color: #f36db4;
}

.editor-v2__layers li.dragging {
  opacity: 0.45;
}

.editor-v2__layer-grip {
  flex: 0 0 auto;
  color: #686a74;
  cursor: grab;
}

.editor-v2__layer-main,
.editor-v2__layer-actions {
  display: flex;
  align-items: center;
}

.editor-v2__layer-main {
  min-width: 0;
  flex: 1;
  gap: 8px;
}

.editor-v2__layer-main span {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__layer-actions {
  flex: 0 0 auto;
  gap: 1px;
}

.editor-v2__layer-actions button {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  color: inherit;
}

.editor-v2__layer-actions button:hover {
  background: rgba(243, 109, 180, 0.12);
}

.editor-v2__layer-actions .muted {
  opacity: 0.35;
}

.editor-v2__workspace {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--editor-workspace);
}

.editor-v2__canvas-tools {
  position: absolute;
  z-index: 5;
  top: 42px;
  left: 11px;
  width: 34px;
  display: grid;
  justify-items: center;
  gap: 2px;
  border: 1px solid #d4d4d9;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(39, 40, 48, 0.14);
  padding: 4px 2px;
}

.editor-v2__canvas-tools button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #595b64;
  cursor: pointer;
}

.editor-v2__canvas-tools button:hover,
.editor-v2__canvas-tools button.active {
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
}

.editor-v2__tool-divider {
  width: 22px;
  height: 1px;
  background: var(--editor-line);
  margin: 2px 0;
}

.editor-v2__stage {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  box-sizing: border-box;
  padding: 58px 24px 120px 54px;
}

.editor-v2__card-heading {
  width: min(720px, calc(100vw - 780px));
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  color: #747680;
}

.editor-v2__card-heading div {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.editor-v2__card-heading strong {
  color: var(--editor-ink);
  font-size: 12px;
}

.editor-v2__card-heading span,
.editor-v2__card-size {
  font-size: 10px;
}

.editor-v2__card-size {
  white-space: nowrap;
}

.editor-v2__canvas-frame {
  position: relative;
  display: grid;
  place-items: center;
}

.editor-v2__canvas-grid {
  position: absolute;
  z-index: 3;
  box-sizing: border-box;
  background-image:
    linear-gradient(to right, rgb(49 117 213 / 14%) 1px, transparent 1px),
    linear-gradient(to bottom, rgb(49 117 213 / 14%) 1px, transparent 1px),
    linear-gradient(to right, rgb(49 117 213 / 25%) 1px, transparent 1px),
    linear-gradient(to bottom, rgb(49 117 213 / 25%) 1px, transparent 1px);
  background-position: -0.5px 0, 0 -0.5px, -0.5px 0, 0 -0.5px;
  pointer-events: none;
}

.editor-v2__card-ruler {
  position: absolute;
  z-index: 8;
  box-sizing: border-box;
  border: 1px solid #c9cad0;
  background: rgb(247 247 249 / 97%);
  color: #737680;
  font-size: 8px;
  line-height: 1;
  pointer-events: none;
}

.editor-v2__card-ruler--top {
  height: 22px;
  border-bottom-color: #afb1b9;
  transform: translateY(-100%);
}

.editor-v2__card-ruler--left {
  width: 22px;
  border-right-color: #afb1b9;
  transform: translateX(-100%);
}

.editor-v2__ruler-tick {
  position: absolute;
  display: block;
}

.editor-v2__ruler-tick i,
.editor-v2__ruler-tick b {
  position: absolute;
  display: block;
}

.editor-v2__ruler-tick b {
  font: inherit;
  font-weight: 600;
}

.editor-v2__card-ruler--top .editor-v2__ruler-tick {
  top: 0;
  bottom: 0;
}

.editor-v2__card-ruler--top .editor-v2__ruler-tick i {
  bottom: 0;
  left: 0;
  width: 1px;
  height: 5px;
  background: currentColor;
}

.editor-v2__card-ruler--top .editor-v2__ruler-tick.major i {
  height: 8px;
}

.editor-v2__card-ruler--top .editor-v2__ruler-tick b {
  top: 3px;
  left: 0;
  transform: translateX(-50%);
}

.editor-v2__card-ruler--left .editor-v2__ruler-tick {
  right: 0;
  left: 0;
}

.editor-v2__card-ruler--left .editor-v2__ruler-tick i {
  top: 0;
  right: 0;
  width: 5px;
  height: 1px;
  background: currentColor;
}

.editor-v2__card-ruler--left .editor-v2__ruler-tick.major i {
  width: 8px;
}

.editor-v2__card-ruler--left .editor-v2__ruler-tick b {
  top: 0;
  right: 10px;
  transform: translateY(-50%) rotate(-90deg);
}

.editor-v2__canvas-frame--drop-active {
  outline: 3px solid #f05aa8;
  outline-offset: 8px;
}

.editor-v2__canvas-drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  border: 2px dashed #f05aa8;
  background: rgb(255 255 255 / 88%);
  color: #272830;
  text-align: center;
  pointer-events: none;
}

.editor-v2__canvas-drop-overlay span {
  color: #686a75;
  font-size: 11px;
}

.editor-v2__guide-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--sidebar-text);
  cursor: pointer;
}

.editor-v2__guide-toggle > span {
  display: grid;
  gap: 2px;
}

.editor-v2__guide-toggle small {
  color: var(--sidebar-muted);
  font-size: 10px;
}

.editor-v2__guide-toggle input {
  width: 34px;
  height: 18px;
  accent-color: var(--editor-accent);
  cursor: pointer;
}

.editor-v2__guide-legend {
  display: flex;
  gap: 14px;
  margin-top: 11px;
  color: var(--sidebar-muted);
  font-size: 10px;
}

.editor-v2__guide-legend span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.editor-v2__guide-legend i {
  width: 15px;
  border-top: 1px dashed currentColor;
}

.editor-v2__guide-legend .bleed {
  color: #d54e3b;
}

.editor-v2__guide-legend .safe {
  color: #00825d;
}

.editor-v2__zoom {
  position: absolute;
  z-index: 6;
  right: 18px;
  bottom: 105px;
  display: flex;
  gap: 2px;
  border: 1px solid #d4d4d9;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 7px 18px rgba(39, 40, 48, 0.13);
  padding: 3px;
  isolation: isolate;
}

.editor-v2__zoom button {
  width: 29px;
  height: 28px;
  border-radius: 4px;
  color: #595b64;
}

.editor-v2__zoom button:hover {
  background: var(--editor-soft);
}

.editor-v2__zoom-value {
  width: 62px !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 10px;
  font-weight: 700;
}

.editor-v2__zoom-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  width: 176px;
  display: grid;
  gap: 1px;
  border: 1px solid #343640;
  border-radius: 7px;
  background: #1b1c22;
  box-shadow: 0 14px 36px rgba(16, 17, 22, 0.28);
  padding: 6px;
}

.editor-v2__zoom-menu button {
  width: 100%;
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  border-radius: 4px;
  color: #d8d9df;
  font-size: 11px;
  padding: 0 9px;
}

.editor-v2__zoom-menu button:hover,
.editor-v2__zoom-menu button.active {
  background: #30313a;
  color: #ffffff;
}

.editor-v2__zoom-menu button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.editor-v2__zoom-menu-divider {
  height: 1px;
  background: #343640;
  margin: 4px 3px;
}

.editor-v2__pages-dock {
  position: absolute;
  z-index: 5;
  right: 0;
  bottom: 0;
  left: 0;
  height: 86px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid var(--editor-line);
  background: #ffffff;
  padding: 8px 18px;
}

.editor-v2__page-list {
  min-width: 0;
  gap: 10px;
}

.editor-v2__page-list > button {
  min-width: 116px;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--editor-ink);
  cursor: pointer;
  padding: 6px;
  text-align: left;
}

.editor-v2__page-list > button:hover,
.editor-v2__page-list > button.active {
  border-color: #e2aaca;
  background: var(--editor-accent-soft);
}

.editor-v2__page-thumb {
  width: 58px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(31, 33, 40, 0.16);
  border-radius: 2px;
  color: rgba(31, 33, 40, 0.48);
  font-size: 9px;
  font-weight: 800;
}

.editor-v2__page-label {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.editor-v2__page-label strong,
.editor-v2__page-label small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__page-label strong {
  font-size: 11px;
}

.editor-v2__page-label small {
  color: var(--editor-muted);
  font-size: 9px;
}

.editor-v2__page-meta {
  flex-direction: column;
  align-items: end;
  gap: 3px;
  color: var(--editor-muted);
  font-size: 10px;
}

.editor-v2__page-meta strong {
  color: var(--editor-ink);
  font-size: 11px;
}

.editor-v2__inspector {
  border-left: 1px solid var(--sidebar-line);
  padding: 20px 16px 90px;
}

.editor-v2__inspector-title {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.editor-v2__inspector-title > div:first-child {
  display: grid;
  gap: 2px;
}

.editor-v2__inspector-title > div:first-child > span {
  color: var(--sidebar-muted);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.editor-v2__inspector-title-actions {
  display: flex;
  gap: 3px;
}

.editor-v2__inspector-title button {
  width: 30px;
  height: 30px;
  border-radius: 5px;
  color: var(--sidebar-muted);
}

.editor-v2__inspector-title button:hover {
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
}

.editor-v2__preflight-report {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #6f354f;
  border-radius: 5px;
  background: #21161d;
}

.editor-v2__preflight-report.passed {
  border-color: #286650;
  background: #13231d;
}

.editor-v2__preflight-summary {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: #f16aa9;
}

.editor-v2__preflight-report.passed .editor-v2__preflight-summary {
  color: #63c39c;
}

.editor-v2__preflight-summary > span,
.editor-v2__preflight-issue > span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.editor-v2__preflight-summary strong,
.editor-v2__preflight-issue strong {
  color: var(--sidebar-text);
  font-size: 11px;
}

.editor-v2__preflight-summary small,
.editor-v2__preflight-issue small {
  color: var(--sidebar-muted);
  font-size: 10px;
  line-height: 1.4;
}

.editor-v2__preflight-error {
  margin: 0;
  color: #ff8f9a;
  font-size: 11px;
  line-height: 1.5;
}

.editor-v2__preflight-issues {
  display: grid;
  gap: 7px;
  margin-top: 11px;
}

.editor-v2__preflight-issue {
  width: 100%;
  min-height: 48px;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: #ff8792;
  text-align: left;
  cursor: pointer;
}

.editor-v2__preflight-issue.warning {
  color: #e5b85c;
}

.editor-v2__preflight-issue:hover:not(:disabled) {
  border-color: #8b4770;
}

.editor-v2__preflight-issue:disabled {
  cursor: default;
  opacity: 1;
}

.editor-v2__inspector-lock {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: transparent;
  color: #c5c6ce;
  cursor: pointer;
  font-size: 10px;
}

.editor-v2__inspector-lock:hover {
  border-color: #75405f;
  color: #f36db4;
}

.editor-v2__inspector-section {
  border-bottom: 1px solid var(--sidebar-line);
  padding: 18px 0;
}

.editor-v2__inspector-section-title {
  margin-bottom: 11px;
  color: var(--sidebar-muted);
  font-size: 10px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.editor-v2__text-input,
.editor-v2__selected-name,
.editor-v2__select,
.editor-v2__control-row input,
.editor-v2__geometry-grid input,
.editor-v2__field-row input,
.editor-v2__page-dimensions input {
  box-sizing: border-box;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
}

.editor-v2__text-input,
.editor-v2__selected-name {
  width: 100%;
  min-height: 56px;
  font-size: 13px;
  line-height: 1.35;
  padding: 10px;
  resize: vertical;
}

.editor-v2__selected-name {
  display: flex;
  align-items: center;
}

.editor-v2__text-input:focus,
.editor-v2__select:focus,
.editor-v2__control-row input:focus,
.editor-v2__geometry-grid input:focus,
.editor-v2__field-row input:focus {
  border-color: #c54b8c;
  outline: 2px solid rgba(189, 52, 127, 0.18);
}

.editor-v2__segmented,
.editor-v2__side-segmented {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  margin: 9px 0;
}

.editor-v2__segmented button,
.editor-v2__side-segmented button {
  min-width: 0;
  min-height: 30px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  cursor: pointer;
  font-size: 9px;
}

.editor-v2__segmented button:hover,
.editor-v2__side-segmented button:hover,
.editor-v2__side-segmented button.active {
  border-color: #75405f;
  background: #351829;
  color: #f36db4;
}

.editor-v2__side-segmented {
  grid-template-columns: repeat(2, 1fr);
  margin: 0;
}

.editor-v2__shape-swap {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
  margin-bottom: 9px;
}

.editor-v2__shape-swap button {
  min-width: 0;
  min-height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  cursor: pointer;
}

.editor-v2__shape-swap button:hover,
.editor-v2__shape-swap button.active {
  border-color: #c54b8c;
  background: #351829;
  color: #f36db4;
}

.editor-v2__rounded-shape-icon {
  border-radius: 4px;
}

.editor-v2__fill-mode,
.editor-v2__gradient-kind {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  margin-bottom: 9px;
}

.editor-v2__fill-mode button,
.editor-v2__gradient-kind button {
  min-width: 0;
  min-height: 31px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  cursor: pointer;
  font-size: 10px;
}

.editor-v2__fill-mode button:hover,
.editor-v2__fill-mode button.active,
.editor-v2__gradient-kind button:hover,
.editor-v2__gradient-kind button.active {
  border-color: #c54b8c;
  background: #351829;
  color: #f36db4;
}

.editor-v2__gradient-preview {
  height: 36px;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  margin-bottom: 8px;
}

.editor-v2__gradient-geometry {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
  margin-top: 8px;
}

.editor-v2__gradient-geometry label,
.editor-v2__gradient-stop label {
  min-width: 0;
  display: grid;
  gap: 3px;
  color: var(--sidebar-muted);
  font-size: 8px;
}

.editor-v2__gradient-geometry input,
.editor-v2__gradient-stop input[type="number"] {
  min-width: 0;
  width: 100%;
  height: 30px;
  box-sizing: border-box;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
  font-size: 10px;
  padding: 0 6px;
}

.editor-v2__gradient-stops {
  display: grid;
  gap: 6px;
  margin-top: 10px;
}

.editor-v2__gradient-stop {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) minmax(0, 1fr) 30px;
  align-items: end;
  gap: 5px;
}

.editor-v2__gradient-stop > input[type="color"] {
  width: 34px;
  height: 30px;
  box-sizing: border-box;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  cursor: pointer;
  padding: 3px;
}

.editor-v2__gradient-stop > button,
.editor-v2__gradient-add {
  min-width: 0;
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  cursor: pointer;
}

.editor-v2__gradient-stop > button:hover:not(:disabled),
.editor-v2__gradient-add:hover:not(:disabled) {
  border-color: #c54b8c;
  color: #f36db4;
}

.editor-v2__gradient-stop > button:disabled,
.editor-v2__gradient-add:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.editor-v2__gradient-add {
  width: 100%;
  gap: 6px;
  margin-top: 8px;
  font-size: 9px;
}

.editor-v2__effect-modes {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
}

.editor-v2__effect-modes button {
  min-width: 0;
  min-height: 31px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  cursor: pointer;
  font-size: 9px;
}

.editor-v2__effect-modes button:hover,
.editor-v2__effect-modes button.active {
  border-color: #c54b8c;
  background: #351829;
  color: #f36db4;
}

.editor-v2__effect-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 9px;
}

.editor-v2__effect-grid--glow {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.editor-v2__effect-grid label {
  min-width: 0;
  display: grid;
  gap: 3px;
  color: var(--sidebar-muted);
  font-size: 8px;
}

.editor-v2__effect-grid input[type="number"] {
  min-width: 0;
  width: 100%;
  height: 31px;
  box-sizing: border-box;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
  font-size: 10px;
  padding: 0 7px;
}

.editor-v2__effect-color input[type="color"] {
  width: 100%;
  height: 31px;
  box-sizing: border-box;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  cursor: pointer;
  padding: 3px;
}

.editor-v2__hollow-preview {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
  border: 1px solid var(--sidebar-line);
  border-radius: 4px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  font-size: 9px;
  padding: 0 9px;
}

.editor-v2__hollow-preview span {
  width: 22px;
  height: 16px;
  box-sizing: border-box;
  border-style: solid;
  border-radius: 3px;
}

.editor-v2__select,
.editor-v2__control-row input,
.editor-v2__geometry-grid input,
.editor-v2__field-row input {
  min-width: 0;
  height: 34px;
  font-size: 11px;
  padding: 0 9px;
}

.editor-v2__select {
  width: 100%;
}

.editor-v2__control-row {
  gap: 7px;
  margin-top: 7px;
}

.editor-v2__control-row .editor-v2__select {
  flex: 1;
}

.editor-v2__compact-field,
.editor-v2__color-field {
  min-width: 78px;
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: var(--sidebar-raised);
  color: var(--sidebar-muted);
  font-size: 9px;
  overflow: hidden;
}

.editor-v2__compact-field > span,
.editor-v2__color-field > span {
  padding-left: 8px;
}

.editor-v2__compact-field input,
.editor-v2__color-field input {
  width: 58px;
  border: 0;
  background: transparent;
}

.editor-v2__color-field {
  flex: 1;
}

.editor-v2__color-field input[type="color"] {
  width: 48px;
  height: 32px;
  justify-self: end;
  cursor: pointer;
  padding: 4px;
}

.editor-v2__icon-row {
  gap: 3px;
  margin-top: 9px;
}

.editor-v2__icon-row button {
  width: 31px;
  height: 29px;
  border-radius: 4px;
  color: var(--sidebar-muted);
}

.editor-v2__icon-row button:hover,
.editor-v2__icon-row button.active {
  background: #351829;
  color: #f36db4;
}

.editor-v2__icon-divider {
  width: 1px;
  height: 20px;
  background: var(--sidebar-line);
  margin: 0 3px;
}

.editor-v2__fill-control,
.editor-v2__background-control {
  height: 36px;
  display: flex;
  align-items: center;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: var(--sidebar-raised);
  color: var(--sidebar-text);
  font-size: 10px;
  overflow: hidden;
}

.editor-v2__fill-control input[type="color"],
.editor-v2__background-control input[type="color"] {
  width: 40px;
  height: 36px;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 4px;
}

.editor-v2__background-control button {
  height: 100%;
  margin-left: auto;
  border: 0;
  border-left: 1px solid var(--sidebar-line);
  background: transparent;
  color: var(--sidebar-muted);
  cursor: pointer;
  font-size: 9px;
  padding: 0 10px;
}

.editor-v2__background-control button:hover {
  color: #f36db4;
}

.editor-v2__swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
}

.editor-v2__swatches button {
  width: 24px;
  height: 24px;
  border: 2px solid #3a3b44;
  border-radius: 5px;
  cursor: pointer;
}

.editor-v2__swatches button:hover {
  border-color: #f36db4;
}

.editor-v2__geometry-grid,
.editor-v2__page-dimensions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.editor-v2__geometry-grid label,
.editor-v2__page-dimensions label {
  display: grid;
  gap: 4px;
  color: var(--sidebar-muted);
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
}

.editor-v2__geometry-grid label > span {
  display: flex;
  justify-content: space-between;
}

.editor-v2__geometry-grid small {
  color: #666873;
  font-size: 8px;
  font-weight: 500;
  text-transform: none;
}

.editor-v2__geometry-grid input,
.editor-v2__page-dimensions input {
  width: 100%;
}

.editor-v2__field-row {
  min-height: 36px;
  display: grid;
  grid-template-columns: 1fr 92px;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--sidebar-muted);
  font-size: 10px;
}

.editor-v2__field-row input {
  width: 100%;
}

.editor-v2__image-inspector-preview {
  width: 100%;
  aspect-ratio: 1.8;
  overflow: hidden;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: #101116;
  margin-bottom: 9px;
}

.editor-v2__image-palette {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 9px 0 11px;
}

.editor-v2__image-palette span {
  width: 26px;
  height: 26px;
  box-sizing: border-box;
  border: 2px solid #3a3b44;
  border-radius: 5px;
}

.editor-v2__image-error {
  display: block;
  margin: 7px 0;
  color: #ff8a8a;
  font-size: 10px;
  line-height: 1.4;
}

.editor-v2__arrange-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.editor-v2__arrange-grid button {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--sidebar-line);
  border-radius: 5px;
  background: var(--sidebar-raised);
  color: #c7c8d0;
  cursor: pointer;
  font-size: 9px;
}

.editor-v2__arrange-grid button:hover {
  border-color: #75405f;
  color: #f36db4;
}

.editor-v2__arrange-grid button.danger:hover {
  border-color: #a84848;
  color: #ff8383;
}

.editor-v2__range {
  width: 100%;
  accent-color: var(--editor-accent);
}

.editor-v2__print-export-summary {
  display: grid;
  gap: 3px;
  margin-bottom: 10px;
  color: var(--sidebar-text);
}

.editor-v2__print-export-summary span,
.editor-v2__print-export-summary small {
  color: var(--sidebar-muted);
  font-size: 10px;
}

.editor-v2__print-export-summary strong {
  font-size: 15px;
  font-weight: 650;
}

.editor-v2__print-export-button {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid #75405f;
  border-radius: 4px;
  background: #351829;
  color: #f36db4;
  cursor: pointer;
  font-size: 11px;
  font-weight: 650;
}

.editor-v2__print-export-button:hover:not(:disabled) {
  border-color: #9d4d7b;
  background: #482039;
  color: #ffffff;
}

.editor-v2__print-export-button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.editor-v2__page-size {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 11px;
  color: var(--sidebar-text);
}

.editor-v2__page-size strong {
  font-size: 15px;
}

.editor-v2__page-size span {
  color: var(--sidebar-muted);
  font-size: 9px;
}

.editor-v2__page-dimensions input {
  height: 34px;
  box-sizing: border-box;
  padding: 0 8px;
}

.editor-v2__page-dimensions input:disabled {
  color: #777984;
  cursor: not-allowed;
  opacity: 1;
}

.editor-v2__inspector-footer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px 12px;
  margin-top: 20px;
  color: var(--sidebar-muted);
  font-size: 9px;
}

.editor-v2__inspector-footer strong {
  max-width: 170px;
  overflow: hidden;
  color: var(--sidebar-text);
  font-size: 9px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__preview-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgb(10 10 12 / 68%);
  padding: 20px;
}

.editor-v2__preview-dialog {
  width: min(760px, 100%);
  max-height: min(820px, calc(100svh - 40px));
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid #4b4c52;
  border-radius: 8px;
  background: #2d2e30;
  color: #f7f7f8;
  box-shadow: 0 24px 70px rgb(0 0 0 / 42%);
}

.editor-v2__preview-dialog > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 12px;
}

.editor-v2__preview-dialog > header > div {
  display: grid;
  gap: 4px;
}

.editor-v2__preview-dialog > header span {
  color: #b7b9c1;
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.editor-v2__preview-dialog h2 {
  margin: 0;
  font-size: 18px;
}

.editor-v2__preview-dialog > header button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #f3f3f4;
  cursor: pointer;
}

.editor-v2__preview-dialog > header button:hover {
  background: #404145;
}

.editor-v2__preview-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #45464b;
  padding: 0 20px;
}

.editor-v2__preview-tabs button {
  min-width: 92px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #b4b6bf;
  cursor: pointer;
  padding: 10px 14px;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}

.editor-v2__preview-tabs button:hover,
.editor-v2__preview-tabs button.active {
  border-bottom-color: #f05aa8;
  color: #fff;
}

.editor-v2__preview-body {
  min-height: 0;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 14px;
  overflow-y: auto;
  padding: 24px;
  scrollbar-color: #73747b transparent;
  scrollbar-width: thin;
}

.editor-v2__preview-label {
  width: min(640px, 100%);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  color: #b7b9c1;
  font-size: 11px;
}

.editor-v2__preview-label strong {
  color: #fff;
  font-size: 13px;
}

.editor-v2__preview-body :deep(.canvas-frame) {
  width: min(640px, 100%);
  box-shadow: 0 12px 30px rgb(0 0 0 / 24%);
}

.editor-v2__preview-dialog > footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid #45464b;
  color: #aeb0b7;
  padding: 12px 20px;
  font-size: 10px;
}

.editor-v2__preview-dialog > footer > div {
  display: flex;
  gap: 8px;
}

.editor-v2__preview-close,
.editor-v2__preview-export {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid #5b5c61;
  border-radius: 5px;
  padding: 7px 12px;
  font: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.editor-v2__preview-close {
  background: transparent;
  color: #f0f0f2;
}

.editor-v2__preview-export {
  border-color: #b8327e;
  background: #bd3783;
  color: #fff;
}

.editor-v2__preview-close:hover {
  background: #404145;
}

.editor-v2__preview-export:hover:not(:disabled) {
  background: #cf438f;
}

.editor-v2__preview-close:disabled,
.editor-v2__preview-export:disabled {
  cursor: wait;
  opacity: 0.55;
}
.editor-v2__shortcut-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgb(10 10 12 / 68%);
  padding: 20px;
}

.editor-v2__shortcut-dialog {
  width: min(560px, 100%);
  max-height: min(720px, calc(100svh - 40px));
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid #4b4c52;
  border-radius: 8px;
  background: #2d2e30;
  color: #f7f7f8;
  box-shadow: 0 24px 70px rgb(0 0 0 / 42%);
}

.editor-v2__shortcut-dialog > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 18px 12px;
}

.editor-v2__shortcut-dialog h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.editor-v2__shortcut-dialog > header button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #f3f3f4;
  cursor: pointer;
}

.editor-v2__shortcut-dialog > header button:hover {
  background: #404145;
}

.editor-v2__shortcut-search {
  margin: 0 18px 14px;
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #5b5c62;
  border-radius: 7px;
  color: #adafb7;
  padding: 0 12px;
}

.editor-v2__shortcut-search:focus-within {
  border-color: #f05aa8;
}

.editor-v2__shortcut-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: #fff;
  font: inherit;
  letter-spacing: 0;
}

.editor-v2__shortcut-search input::placeholder {
  color: #8d8e95;
}

.editor-v2__shortcut-results {
  min-height: 0;
  overflow-y: auto;
  padding: 0 18px 16px;
  scrollbar-color: #73747b transparent;
  scrollbar-width: thin;
}

.editor-v2__shortcut-results > section + section {
  margin-top: 20px;
}

.editor-v2__shortcut-results h3 {
  margin: 0 0 8px;
  color: #aeb0b7;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.editor-v2__shortcut-results dl {
  margin: 0;
  overflow: hidden;
  border: 1px solid #45464b;
  border-radius: 7px;
}

.editor-v2__shortcut-results dl > div {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px;
}

.editor-v2__shortcut-results dl > div + div {
  border-top: 1px solid #45464b;
}

.editor-v2__shortcut-results dt {
  color: #f0f0f2;
  font-size: 12px;
  font-weight: 650;
}

.editor-v2__shortcut-results dd {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
  margin: 0;
}

.editor-v2__shortcut-results kbd {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #5b5c61;
  border-radius: 5px;
  background: #45464a;
  color: #f4f4f5;
  padding: 2px 7px;
  font-family: inherit;
  font-size: 10px;
  white-space: nowrap;
}

.editor-v2__shortcut-empty {
  margin: 28px 0;
  color: #b9bbc2;
  text-align: center;
}

.editor-v2__shortcut-dialog > footer {
  border-top: 1px solid #45464b;
  color: #aeb0b7;
  padding: 12px 18px;
  font-size: 10px;
}
button:focus-visible,
a:focus-visible,
input:focus-visible,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid #e05a9f;
  outline-offset: 2px;
}

@media (max-height: 720px) and (min-width: 961px) {
  .editor-v2__stage {
    padding-top: 42px;
    padding-bottom: 104px;
  }

  .editor-v2__zoom {
    bottom: 92px;
  }

  .editor-v2__pages-dock {
    height: 76px;
  }

}

@media (max-width: 1280px) {
  .editor-v2__status-cluster {
    display: none;
  }

  .editor-v2__layout {
    grid-template-columns: 60px 236px minmax(0, 1fr) 286px;
  }

  .editor-v2__rail button {
    width: 50px;
  }

  .editor-v2__elements-panel,
  .editor-v2__inspector {
    padding-inline: 12px;
  }

  .editor-v2__card-heading {
    width: min(640px, calc(100vw - 670px));
  }
}

@media (max-width: 960px) {
  :global(main.app-shell--editor) {
    padding: 0;
  }
  .editor-v2 {
    overflow: visible;
  }

  .editor-v2__header {
    position: sticky;
    top: 0;
  }

  .editor-v2__header-center {
    display: none;
  }

  .editor-v2__preflight span {
    display: none;
  }

  .editor-v2__layout {
    height: auto;
    grid-template-columns: 1fr;
  }

  .editor-v2__rail {
    height: 64px;
    flex-direction: row;
    justify-content: flex-start;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: 0;
    border-bottom: 1px solid var(--sidebar-line);
    padding: 5px 8px;
  }

  .editor-v2__rail button {
    flex: 0 0 58px;
    min-height: 52px;
  }

  .editor-v2__rail .editor-v2__shortcut-launcher {
    margin-top: 0;
    margin-left: auto;
    border-top: 0;
    border-left: 1px solid var(--sidebar-line);
    border-radius: 0 6px 6px 0;
  }

  .editor-v2__elements-panel,
  .editor-v2__inspector {
    max-height: none;
    overflow: visible;
    border-right: 0;
    border-left: 0;
    border-bottom: 1px solid var(--sidebar-line);
    padding: 16px;
  }

  .editor-v2__element-section {
    margin-bottom: 16px;
  }

  .editor-v2__shape-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .editor-v2__workspace {
    min-height: 650px;
  }

  .editor-v2__stage {
    min-height: 650px;
    padding: 58px 30px 126px 54px;
  }

  .editor-v2__card-heading {
    width: min(78vw, 680px);
  }

}

@media (max-width: 600px) {
  .editor-v2__shortcut-backdrop {
    align-items: end;
    padding: 10px;
  }

  .editor-v2__shortcut-dialog {
    max-height: calc(100svh - 20px);
  }

  .editor-v2__shortcut-results dl > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }

  .editor-v2__shortcut-results dd {
    justify-content: flex-start;
  }
}
@media (max-width: 600px) {
  .editor-v2__preview-backdrop {
    align-items: end;
    padding: 10px;
  }

  .editor-v2__preview-dialog {
    max-height: calc(100svh - 20px);
  }

  .editor-v2__preview-body {
    padding: 18px 14px;
  }

  .editor-v2__preview-dialog > footer {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .editor-v2__preview-dialog > footer > div {
    width: 100%;
  }

  .editor-v2__preview-close,
  .editor-v2__preview-export {
    flex: 1;
  }
}
@media (max-width: 520px) {
  .editor-v2__header {
    gap: 7px;
    padding: 0 9px;
  }

  .editor-v2__brand,
  .editor-v2__divider,
  .editor-v2__document-name span,
  .editor-v2__header-icon {
    display: none;
  }

  .editor-v2__header-icon.editor-v2__preview-icon,
  .editor-v2__header-icon.editor-v2__export-icon {
    display: grid;
  }

  .editor-v2__document-name strong {
    max-width: 38vw;
  }

  .editor-v2__header-actions {
    gap: 5px;
  }

  .editor-v2__preflight,
  .editor-v2__checkout,
  .editor-v2__save {
    width: 32px;
    justify-content: center;
    padding: 0;
  }

  .editor-v2__preflight span,
  .editor-v2__save span {
    display: none;
  }

  .editor-v2__elements-panel,
  .editor-v2__inspector {
    padding-inline: 12px;
  }

  .editor-v2__shape-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .editor-v2__workspace,
  .editor-v2__stage {
    min-height: 590px;
  }

  .editor-v2__stage {
    padding-inline: 36px 16px;
  }

  .editor-v2__card-ruler {
    display: none;
  }

  .editor-v2__canvas-tools {
    left: 4px;
  }

  .editor-v2__pages-dock {
    gap: 6px;
    padding-inline: 7px;
  }

  .editor-v2__page-list {
    gap: 3px;
  }

  .editor-v2__page-list > button {
    min-width: 86px;
    padding-inline: 3px;
  }

  .editor-v2__page-thumb {
    width: 42px;
    height: 30px;
  }

  .editor-v2__page-meta {
    display: none;
  }

  .editor-v2__zoom {
    right: 7px;
    bottom: 96px;
  }
}
</style>
