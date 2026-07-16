<script setup lang="ts">
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Check,
  Circle,
  Copy,
  Eye,
  EyeOff,
  ImageIcon,
  Lock,
  Move,
  MousePointer2,
  Square,
  RotateCcw,
  RotateCw,
  Sparkles,
  Trash2,
  Type,
  Unlock,
  X,
  ZoomIn,
  ZoomOut,
} from "@lucide/vue";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import {
  apiBaseUrl,
  deleteDesign,
  getDesign,
  preflightDesign,
  removeBackgroundImageAsset,
  rewriteDesignText,
  searchIcons8,
  updateDesign,
  uploadImageAsset,
  type AiEditStrength,
  type AiTextRewriteResponse,
  type DesignDetail,
  type Icons8Icon,
  type ImageUploadResponse,
  type PreflightReport,
} from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";
import { applyTextRewritePreview, canvasLayerId, readLayerText } from "../aiRewritePreview";
import { appleEmojiIcons } from "../generated/appleEmojiIcons";
import { localFonts } from "../generated/localFonts";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};
type EditorPage = "front" | "back";
type EditorTool = "select" | "text" | "rect" | "ellipse" | "shape" | "image" | "icon";
type ColorTarget = "fill" | "stroke";
type IconAsset = {
  id: string;
  label: string;
  glyph: string;
  tags: string[];
  description: string;
  keywords: string[];
};
type LayerPanelItem = {
  index: number;
  number: number;
  layer: CanvasLayer;
};
type LayerContextMenuState = {
  index: number;
  x: number;
  y: number;
};
const layerContextMenuWidth = 176;
const layerContextMenuHeight = 260;
const layerContextMenuMargin = 8;
const iconAssets: IconAsset[] = appleEmojiIcons;
type AiRewritePreviewState = {
  response: AiTextRewriteResponse;
  layerId: string;
  originalText: string;
  replacementText: string;
};
type AssetLibraryItem = {
  id: string;
  file: File;
  url: string;
  name: string;
  sizeLabel: string;
  pixelWidth: number;
  pixelHeight: number;
  uploaded?: ImageUploadResponse;
};


const route = useRoute();
const router = useRouter();
const design = ref<DesignDetail | null>(null);
const loading = ref(true);
const error = ref("");
const editableLayers = ref<CanvasLayer[]>([]);
const saving = ref(false);
const deleting = ref(false);
const confirmingDelete = ref(false);
const saveMessage = ref("");
const saveError = ref("");
const selectedPage = ref<EditorPage>("front");
const activeTool = ref<EditorTool>("select");
const selectedLayerIndex = ref(0);
const selectedLayerIndexes = ref<number[]>([0]);
const draggingLayerIndex = ref<number | null>(null);
const layerContextMenu = ref<LayerContextMenuState | null>(null);
const layerContextMenuElement = ref<HTMLElement | null>(null);
const activeColorTarget = ref<ColorTarget>("fill");
const undoLayerStack = ref<CanvasLayer[][]>([]);
const redoLayerStack = ref<CanvasLayer[][]>([]);
const editorZoom = ref(100);
const editorPanX = ref(0);
const editorPanY = ref(0);
const assetFileInput = ref<HTMLInputElement | null>(null);
const assetLibrary = ref<AssetLibraryItem[]>([]);
const selectedAssetId = ref<string | null>(null);
const assetPreviewError = ref("");
const assetUploading = ref(false);
const backgroundRemovingAssetId = ref<string | null>(null);
const assetDropActive = ref(false);
const layerClipboard = ref<CanvasLayer[]>([]);
const iconSearchQuery = ref("");
const selectedIconId = ref(iconAssets[0]?.id ?? "diamond");
const draggedIconId = ref<string | null>(null);
const icons8SearchQuery = ref("");
const icons8SearchLanguage = ref("en");
const icons8Results = ref<Icons8Icon[]>([]);
const icons8Loading = ref(false);
const icons8Error = ref("");
const icons8Message = ref("");
const draggedIcons8Id = ref<string | null>(null);
const randomPhotoLoading = ref(false);
const randomPhotoError = ref("");
const aiRewritePrompt = ref("");
const aiRewriteStrength = ref<AiEditStrength>("light");
const aiRewriteLoading = ref(false);
const aiRewriteError = ref("");
const aiRewritePreview = ref<AiRewritePreviewState | null>(null);
const showSafeZoneGuides = ref(true);
const preflightReport = ref<PreflightReport | null>(null);
const preflightLoading = ref(false);
const preflightError = ref("");
const LEFT_SIDEBAR_MIN = 180;
const LEFT_SIDEBAR_MAX = 420;
const LEFT_SIDEBAR_DEFAULT = 240;
const RIGHT_SIDEBAR_MIN = 220;
const RIGHT_SIDEBAR_MAX = 480;
const RIGHT_SIDEBAR_DEFAULT = 300;
const SIDEBAR_STORAGE_KEY = "vizi.editor.sidebarWidths";
const leftSidebarWidth = ref(LEFT_SIDEBAR_DEFAULT);
const rightSidebarWidth = ref(RIGHT_SIDEBAR_DEFAULT);
const resizingSidebar = ref<"left" | "right" | null>(null);
const aiRewriteStrengths: Array<{ id: AiEditStrength; label: string }> = [
  { id: "light", label: "Light" },
  { id: "balanced", label: "Balanced" },
  { id: "creative", label: "Creative" },
  { id: "direct_command", label: "Direct" },
];

const editorPages: { id: EditorPage; label: string }[] = [
  { id: "front", label: "Front" },
  { id: "back", label: "Back" },
];
const editorTools: { id: EditorTool; label: string; icon: typeof MousePointer2 }[] = [
  { id: "select", label: "Select", icon: MousePointer2 },
  { id: "text", label: "Text", icon: Type },
  { id: "rect", label: "Rect", icon: Square },
  { id: "ellipse", label: "Ellipse", icon: Circle },
  { id: "image", label: "Image", icon: ImageIcon },
];
const colorTargets: { id: ColorTarget; label: string }[] = [
  { id: "fill", label: "Fill" },
  { id: "stroke", label: "Stroke" },
];
const recentColors = ["#B1B2B5", "#2F281C", "#A87F33", "#5F7344", "#A5382F", "#FFFFFF"];
const icons8LanguageOptions = [
  { id: "en", label: "English" },
  { id: "ru", label: "Russian" },
  { id: "vi", label: "Vietnamese" },
  { id: "es", label: "Spanish" },
  { id: "fr", label: "French" },
  { id: "de", label: "German" },
  { id: "ja", label: "Japanese" },
  { id: "zh", label: "Chinese" },
];
const icons8DefaultPlatform = "ios7";
const allowedPreviewImageTypes = ["image/png", "image/jpeg", "image/webp"];
const maximumPreviewImageBytes = 5 * 1024 * 1024;
const systemFontFamilyOptions = [
  "inherit",
  "Arial",
  "Georgia",
  "Times New Roman",
  "Trebuchet MS",
];
const fontFamilyOptions = [
  ...systemFontFamilyOptions,
  ...localFonts.map((font) => font.family),
];
const fontWeightOptions = [300, 400, 500, 600, 700, 800, 900];

const designId = computed(() => {
  const value = route.params.designId ?? route.params.id;
  return Number(Array.isArray(value) ? value[0] : value);
});
const isEditorRoute = computed(() => route.name === "editor");
const canvasLayers = computed<CanvasLayer[]>(() => editableLayers.value);
const selectedPageLabel = computed(() =>
  editorPages.find((page) => page.id === selectedPage.value)?.label ?? "Front",
);
const activeToolLabel = computed(() =>
  activeTool.value === "icon"
    ? "Icon"
    : editorTools.find((tool) => tool.id === activeTool.value)?.label ?? "Select",
);
const activeColorTargetLabel = computed(() =>
  colorTargets.find((target) => target.id === activeColorTarget.value)?.label ?? "Fill",
);
const selectedIconAsset = computed(() =>
  iconAssets.find((icon) => icon.id === selectedIconId.value) ?? iconAssets[0],
);
const selectedAsset = computed(() =>
  assetLibrary.value.find((asset) => asset.id === selectedAssetId.value) ?? null,
);
function normalizeIconSearchText(value: string): string {
  return value
    .trim()
    .toLocaleLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\u0111/g, "d");
}

function iconSearchTerms(value: string): string[] {
  return normalizeIconSearchText(value).split(/\s+/).filter(Boolean);
}

function iconSearchHaystack(icon: IconAsset): string {
  return normalizeIconSearchText([
    icon.label,
    icon.description,
    ...icon.tags,
    ...icon.keywords,
  ].join(" "));
}

const filteredIconAssets = computed(() => {
  const terms = iconSearchTerms(iconSearchQuery.value);
  if (terms.length === 0) {
    return iconAssets;
  }
  return iconAssets
    .map((icon) => {
      const haystack = iconSearchHaystack(icon);
      const matchCount = terms.filter((term) => haystack.includes(term)).length;
      return { icon, matchCount };
    })
    .filter((entry) => entry.matchCount > 0)
    .sort((left, right) => right.matchCount - left.matchCount || left.icon.label.localeCompare(right.icon.label))
    .map((entry) => entry.icon);
});
const pageLayerEntries = computed(() =>
  editableLayers.value
    .map((layer, index) => ({ layer, index }))
    .filter((entry) => layerPage(entry.layer) === selectedPage.value),
);
const editorCanvasLayers = computed<CanvasLayer[]>(() =>
  pageLayerEntries.value.map((entry) => entry.layer),
);
const displayedCanvasLayers = computed<CanvasLayer[]>(() =>
  isEditorRoute.value ? editorCanvasLayers.value : canvasLayers.value,
);
const layerPanelItems = computed<LayerPanelItem[]>(() => {
  const entries = isEditorRoute.value
    ? pageLayerEntries.value
    : editableLayers.value.map((layer, index) => ({ layer, index }));
  return entries
    .map((entry, order) => ({
      layer: entry.layer,
      index: entry.index,
      number: order + 1,
    }))
    .reverse();
});
const canvasLayerCount = computed(() => displayedCanvasLayers.value.length);
const selectedLayer = computed<CanvasLayer | null>(
  () => selectedLayerIndexes.value.length > 0
    ? editableLayers.value[selectedLayerIndex.value] ?? null
    : null,
);
const canvasSelectedLayerIndexes = computed(() => {
  if (!isEditorRoute.value) {
    return selectedLayerIndexes.value;
  }
  const local: number[] = [];
  pageLayerEntries.value.forEach((entry, localIndex) => {
    if (selectedLayerIndexes.value.includes(entry.index)) {
      local.push(localIndex);
    }
  });
  return local;
});
const canvasSelectedLayerIndex = computed(() => {
  if (!isEditorRoute.value) {
    return selectedLayerIndex.value;
  }
  const local = pageLayerEntries.value.findIndex((entry) => entry.index === selectedLayerIndex.value);
  return local >= 0 ? local : null;
});
const canvasResizableLayerIndex = computed<number | null>(() => {
  const layer = selectedLayer.value;
  if (
    !isEditorRoute.value
    || selectedLayerIndexes.value.length !== 1
    || !layer
    || !layerIsVisible(layer)
    || layerIsLocked(layer)
  ) {
    return null;
  }
  return canvasSelectedLayerIndex.value;
});
const canvasRotatableLayerIndex = computed<number | null>(() => canvasResizableLayerIndex.value);
const selectedLayerCanEditGeometry = computed(() => {
  const layer = selectedLayer.value;
  if (!layer || selectedLayerIndexes.value.length !== 1) {
    return false;
  }
  return !layerIsLocked(layer) && layerPage(layer) === selectedPage.value;
});
const selectedLayerCanEditText = computed(() => {
  const layer = selectedLayer.value;
  return selectedLayerCanEditGeometry.value && layer?.type === "text";
});
const selectedLayerIsImage = computed(() =>
  selectedLayerIndexes.value.length === 1 && selectedLayer.value?.type === "image",
);
const canRequestAiRewrite = computed(() =>
  Boolean(design.value && selectedLayerCanEditText.value && aiRewritePrompt.value.trim()),
);

const selectedLayerCanEditAppearance = computed(() => selectedLayerCanEditGeometry.value);
const canUndoLayerChange = computed(() => undoLayerStack.value.length > 0);
const canRedoLayerChange = computed(() => redoLayerStack.value.length > 0);
const canDuplicateSelectedLayers = computed(() => selectedLayerIndexes.value.length > 0);
const canDeleteSelectedLayers = computed(() => selectedLayerIndexes.value.length > 0);
const editorCanvasTransform = computed(() => ({
  transform: `translate(${editorPanX.value}px, ${editorPanY.value}px) scale(${editorZoom.value / 100})`,
}));
const editorShellStyle = computed(() => ({
  gridTemplateColumns: `${leftSidebarWidth.value}px 6px minmax(0, 1fr) 6px ${rightSidebarWidth.value}px`,
}));
const layerContextMenuStyle = computed(() => ({
  left: `${layerContextMenu.value?.x ?? 0}px`,
  top: `${layerContextMenu.value?.y ?? 0}px`,
}));
const selectedLayerLabel = computed(() => {
  if (selectedLayerIndexes.value.length > 1) {
    return `${selectedLayerIndexes.value.length} layers selected`;
  }
  const layer = selectedLayer.value;
  return layer ? `${layerPanelNumber(selectedLayerIndex.value)}. ${layerDisplayName(layer)}` : "No layer";
});
const activeColorValue = computed(() => {
  const layer = selectedLayer.value;
  if (!layer) {
    return "";
  }
  if (activeColorTarget.value === "stroke") {
    return optionalString(layer.stroke) ?? "";
  }
  return optionalString(layer.type === "text" ? layer.color : layer.fill ?? layer.background) ?? "";
});
const firstTextLayerIndex = computed(() =>
  editableLayers.value.findIndex((layer) => layer.type === "text"),
);
const editableTextLayerIndex = computed(() => {
  if (isEditorRoute.value) {
    const selected = selectedLayer.value;
    if (selected?.type === "text" && selectedLayerIndexes.value.length === 1) {
      return selectedLayerIndex.value;
    }
    return -1;
  }
  return firstTextLayerIndex.value;
});
const editableTextLayerLocked = computed(() => {
  const layer = editableLayers.value[editableTextLayerIndex.value];
  return layer ? layerIsLocked(layer) : false;
});
const editableTextLayerText = computed({
  get: () => {
    const layer = editableLayers.value[editableTextLayerIndex.value];
    return layer ? optionalString(layer.text ?? layer.value) ?? "" : "";
  },
  set: (value: string) => {
    const index = editableTextLayerIndex.value;
    const layer = editableLayers.value[index];
    if (!layer || layer.type !== "text") {
      return;
    }

    const textField = typeof layer.text === "string" || typeof layer.value !== "string"
      ? "text"
      : "value";
    const layers = [...editableLayers.value];
    layers[index] = { ...layer, [textField]: value };
    commitLayers(layers, [index]);
  },
});
function isCanvasLayer(layer: unknown): layer is CanvasLayer {
  return typeof layer === "object" && layer !== null;
}

function layerPage(layer: CanvasLayer): EditorPage {
  return layer.page === "back" ? "back" : "front";
}

function localCanvasIndexToGlobal(localIndex: number): number {
  return pageLayerEntries.value[localIndex]?.index ?? localIndex;
}

function setSelectedPage(page: EditorPage): void {
  if (selectedPage.value === page) {
    return;
  }
  selectedPage.value = page;
  clearLayerSelection();
  closeLayerContextMenu();
}

function clampSidebarWidth(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)));
}

function loadSidebarWidths(): void {
  try {
    const raw = window.localStorage.getItem(SIDEBAR_STORAGE_KEY);
    if (!raw) {
      return;
    }
    const parsed = JSON.parse(raw) as { left?: unknown; right?: unknown };
    if (typeof parsed.left === "number" && Number.isFinite(parsed.left)) {
      leftSidebarWidth.value = clampSidebarWidth(parsed.left, LEFT_SIDEBAR_MIN, LEFT_SIDEBAR_MAX);
    }
    if (typeof parsed.right === "number" && Number.isFinite(parsed.right)) {
      rightSidebarWidth.value = clampSidebarWidth(parsed.right, RIGHT_SIDEBAR_MIN, RIGHT_SIDEBAR_MAX);
    }
  } catch {
    // Ignore corrupt localStorage payloads.
  }
}

function persistSidebarWidths(): void {
  try {
    window.localStorage.setItem(
      SIDEBAR_STORAGE_KEY,
      JSON.stringify({
        left: leftSidebarWidth.value,
        right: rightSidebarWidth.value,
      }),
    );
  } catch {
    // Ignore quota / private-mode failures.
  }
}

function isCompactEditorLayout(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(max-width: 900px)").matches;
}

function startSidebarResize(side: "left" | "right", event: PointerEvent): void {
  if (isCompactEditorLayout()) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  resizingSidebar.value = side;
  const startX = event.clientX;
  const startLeft = leftSidebarWidth.value;
  const startRight = rightSidebarWidth.value;

  const move = (moveEvent: PointerEvent) => {
    const delta = moveEvent.clientX - startX;
    if (side === "left") {
      leftSidebarWidth.value = clampSidebarWidth(
        startLeft + delta,
        LEFT_SIDEBAR_MIN,
        LEFT_SIDEBAR_MAX,
      );
    } else {
      rightSidebarWidth.value = clampSidebarWidth(
        startRight - delta,
        RIGHT_SIDEBAR_MIN,
        RIGHT_SIDEBAR_MAX,
      );
    }
  };
  const stop = () => {
    resizingSidebar.value = null;
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", stop);
    window.removeEventListener("pointercancel", stop);
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
    persistSidebarWidths();
  };

  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", stop);
  window.addEventListener("pointercancel", stop);
}

function handleSidebarResizeKey(side: "left" | "right", event: KeyboardEvent): void {
  if (isCompactEditorLayout()) {
    return;
  }
  const step = event.shiftKey ? 24 : 12;
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
    return;
  }
  event.preventDefault();
  const direction = event.key === "ArrowRight" ? 1 : -1;
  if (side === "left") {
    leftSidebarWidth.value = clampSidebarWidth(
      leftSidebarWidth.value + direction * step,
      LEFT_SIDEBAR_MIN,
      LEFT_SIDEBAR_MAX,
    );
  } else {
    // ArrowRight widens the right panel (handle moves left visually).
    rightSidebarWidth.value = clampSidebarWidth(
      rightSidebarWidth.value - direction * step,
      RIGHT_SIDEBAR_MIN,
      RIGHT_SIDEBAR_MAX,
    );
  }
  persistSidebarWidths();
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function layerDisplayName(layer: CanvasLayer): string {
  return optionalString(layer.name)
    ?? optionalString(layer.type)
    ?? "Layer";
}

function layerPanelNumber(index: number): number {
  const item = layerPanelItems.value.find((entry) => entry.index === index);
  return item?.number ?? index + 1;
}

function layerIsVisible(layer: CanvasLayer): boolean {
  return layer.visible !== false && layer.hidden !== true;
}

function layerIsLocked(layer: CanvasLayer): boolean {
  return layer.locked === true;
}

function parseCanvasLayers(canvasJson: string): CanvasLayer[] {
  try {
    const canvas = JSON.parse(canvasJson) as { layers?: unknown };
    return Array.isArray(canvas.layers)
      ? canvas.layers
        .filter(isCanvasLayer)
        .map((layer) => ({
          ...layer,
          page: layer.page === "back" ? "back" : "front",
        }))
      : [];
  } catch {
    return [];
  }
}

function serializeCanvas(): string {
  try {
    const canvas = JSON.parse(design.value?.canvasJson ?? "{}");
    if (typeof canvas === "object" && canvas !== null && !Array.isArray(canvas)) {
      return JSON.stringify({ ...canvas, layers: editableLayers.value });
    }
  } catch {
    // Invalid saved JSON falls back to the validated canvas shape below.
  }
  return JSON.stringify({ layers: editableLayers.value });
}

function cloneLayers(layers: CanvasLayer[]): CanvasLayer[] {
  return JSON.parse(JSON.stringify(layers)) as CanvasLayer[];
}

function clampSelectionToLayers(): void {
  const lastIndex = editableLayers.value.length - 1;
  if (lastIndex < 0) {
    selectedLayerIndex.value = 0;
    selectedLayerIndexes.value = [];
    return;
  }

  selectedLayerIndexes.value = selectedLayerIndexes.value
    .filter((index) => index >= 0 && index <= lastIndex)
    .sort((left, right) => left - right);
  if (selectedLayerIndexes.value.length === 0) {
    selectedLayerIndex.value = Math.min(Math.max(selectedLayerIndex.value, 0), lastIndex);
    selectedLayerIndexes.value = [selectedLayerIndex.value];
    return;
  }

  selectedLayerIndex.value = selectedLayerIndexes.value.at(-1)!;
}

function rememberLayerState(): void {
  undoLayerStack.value = [...undoLayerStack.value.slice(-49), cloneLayers(editableLayers.value)];
  redoLayerStack.value = [];
}

function commitLayers(nextLayers: CanvasLayer[], nextSelectedIndexes = selectedLayerIndexes.value): void {
  rememberLayerState();
  editableLayers.value = nextLayers;
  selectedLayerIndexes.value = nextSelectedIndexes;
  selectedLayerIndex.value = nextSelectedIndexes.at(-1) ?? 0;
  clampSelectionToLayers();
  saveMessage.value = "";
}

function restoreLayerState(layers: CanvasLayer[]): void {
  editableLayers.value = cloneLayers(layers);
  clampSelectionToLayers();
  saveMessage.value = "";
}

function undoLayerChange(): void {
  const previous = undoLayerStack.value.at(-1);
  if (!previous) {
    return;
  }
  undoLayerStack.value = undoLayerStack.value.slice(0, -1);
  redoLayerStack.value = [...redoLayerStack.value.slice(-49), cloneLayers(editableLayers.value)];
  restoreLayerState(previous);
}

function redoLayerChange(): void {
  const next = redoLayerStack.value.at(-1);
  if (!next) {
    return;
  }
  redoLayerStack.value = redoLayerStack.value.slice(0, -1);
  undoLayerStack.value = [...undoLayerStack.value.slice(-49), cloneLayers(editableLayers.value)];
  restoreLayerState(next);
}

function updateEditorZoom(delta: number): void {
  editorZoom.value = clamp(Math.round(editorZoom.value + delta), 50, 200);
}

function setEditorZoom(nextZoom: number): void {
  editorZoom.value = clamp(Math.round(nextZoom), 50, 200);
}

function updateEditorPan(deltaX: number, deltaY: number): void {
  const panLimit = Math.round(360 * (editorZoom.value / 100));
  editorPanX.value = clamp(editorPanX.value + deltaX, -panLimit, panLimit);
  editorPanY.value = clamp(editorPanY.value + deltaY, -panLimit, panLimit);
}

function resetEditorView(): void {
  editorZoom.value = 100;
  editorPanX.value = 0;
  editorPanY.value = 0;
}

/**
 * Touchpad gestures on the canvas workspace:
 * - Pinch zoom (browser exposes as wheel + ctrlKey)
 * - Ctrl/Cmd + two-finger scroll = zoom
 * - Two-finger scroll = pan
 */
function handleEditorWheel(event: WheelEvent): void {
  if (!isEditorRoute.value) {
    return;
  }
  // Don't steal scroll from nested scrollable panels if the wheel started there.
  const target = event.target;
  if (target instanceof HTMLElement && target.closest(".editor-sidebar, .editor-toolbar, .editor-zoom-controls")) {
    return;
  }

  event.preventDefault();
  const zoomGesture = event.ctrlKey || event.metaKey;
  if (zoomGesture) {
    // Pinch / Ctrl+scroll: smooth scale. Clamp per-event so large trackpad
    // deltas do not jump from 100% to 200% in one tick.
    const rawFactor = Math.exp(-event.deltaY * 0.004);
    const zoomFactor = clamp(rawFactor, 0.92, 1.08);
    setEditorZoom(editorZoom.value * zoomFactor);
    return;
  }

  // Two-finger scroll pans the canvas (natural direction).
  updateEditorPan(-event.deltaX, -event.deltaY);
}

function formatBytes(bytes: number): string {
  return bytes >= 1024 * 1024
    ? `${Math.round((bytes / 1024 / 1024) * 10) / 10} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function createAssetId(): string {
  return `asset-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function revokeAllAssetLibrary(): void {
  for (const asset of assetLibrary.value) {
    if (asset.url.startsWith("blob:")) {
      URL.revokeObjectURL(asset.url);
    }
  }
  assetLibrary.value = [];
  selectedAssetId.value = null;
}

function removeAssetFromLibrary(assetId: string): void {
  const asset = assetLibrary.value.find((item) => item.id === assetId);
  if (!asset) {
    return;
  }
  if (asset.url.startsWith("blob:")) {
    URL.revokeObjectURL(asset.url);
  }
  assetLibrary.value = assetLibrary.value.filter((item) => item.id !== assetId);
  if (selectedAssetId.value === assetId) {
    selectedAssetId.value = assetLibrary.value[0]?.id ?? null;
  }
}

function readImageDimensions(url: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight });
    image.onerror = () => reject(new Error("Cannot read image dimensions"));
    image.src = url;
  });
}

async function ingestImageFiles(fileList: FileList | File[]): Promise<AssetLibraryItem[]> {
  const files = Array.from(fileList);
  if (files.length === 0) {
    return [];
  }

  assetPreviewError.value = "";
  const accepted: AssetLibraryItem[] = [];
  for (const file of files) {
    if (!allowedPreviewImageTypes.includes(file.type)) {
      assetPreviewError.value = "Only PNG, JPG, or WebP images are supported.";
      continue;
    }
    if (file.size > maximumPreviewImageBytes) {
      assetPreviewError.value = "Each image must be 5 MB or smaller.";
      continue;
    }
    const url = URL.createObjectURL(file);
    try {
      const dimensions = await readImageDimensions(url);
      accepted.push({
        id: createAssetId(),
        file,
        url,
        name: file.name,
        sizeLabel: formatBytes(file.size),
        pixelWidth: dimensions.width,
        pixelHeight: dimensions.height,
      });
    } catch {
      URL.revokeObjectURL(url);
      assetPreviewError.value = "Cannot read one of the selected images.";
    }
  }

  if (accepted.length === 0) {
    return [];
  }

  assetLibrary.value = [...assetLibrary.value, ...accepted];
  selectedAssetId.value = accepted[accepted.length - 1]!.id;
  return accepted;
}

async function previewAssetImage(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  if (!input.files?.length) {
    return;
  }
  await ingestImageFiles(input.files);
  input.value = "";
}

function backendAssetUrl(url: string): string {
  return url.startsWith("/") ? `${apiBaseUrl}${url}` : url;
}

function returnToSelectTool(): void {
  activeTool.value = "select";
}

/** Public portrait pool for demo cards (no API key). */
function randomPortraitUrl(): string {
  const gender = Math.random() > 0.45 ? "men" : "women";
  const index = 1 + Math.floor(Math.random() * 99);
  // Cache-bust so the browser reloads when re-rolling the same index.
  return `https://randomuser.me/api/portraits/${gender}/${index}.jpg?v=${Date.now()}`;
}

function applyRandomPhotoToSelectedImage(): void {
  if (!selectedLayerIsImage.value || !selectedLayer.value) {
    return;
  }
  randomPhotoError.value = "";
  randomPhotoLoading.value = true;
  try {
    const url = randomPortraitUrl();
    const layers = [...editableLayers.value];
    const index = selectedLayerIndex.value;
    const layer = layers[index];
    if (!layer || layer.type !== "image") {
      return;
    }
    layers[index] = {
      ...layer,
      src: url,
      name: layer.name === "Portrait" || !optionalString(layer.name)
        ? "Portrait (random)"
        : layer.name,
      // Clear uploaded-asset binding so external URL is used.
      assetId: undefined,
      storageKey: undefined,
    };
    commitLayers(layers, [index]);
  } catch (unknownError) {
    randomPhotoError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot load random photo";
  } finally {
    randomPhotoLoading.value = false;
  }
}

async function addAssetToCanvas(asset: AssetLibraryItem, x = 12, y = 12): Promise<void> {
  if (assetUploading.value || backgroundRemovingAssetId.value) {
    return;
  }

  assetUploading.value = true;
  assetPreviewError.value = "";
  try {
    const uploaded = asset.uploaded ?? await uploadImageAsset(asset.file);
    const layer: CanvasLayer = {
      type: "image",
      name: asset.name || uploaded.fileName,
      src: backendAssetUrl(uploaded.url),
      assetId: uploaded.assetId,
      storageKey: uploaded.storageKey,
      page: selectedPage.value,
      x,
      y,
      width: 32,
      height: 32,
      opacity: 1,
      pixelWidth: asset.pixelWidth,
      pixelHeight: asset.pixelHeight,
    };
    const layers = [...editableLayers.value, layer];
    commitLayers(layers, [layers.length - 1]);
    selectedAssetId.value = asset.id;
    returnToSelectTool();
  } catch (unknownError) {
    assetPreviewError.value = unknownError instanceof Error ? unknownError.message : "Cannot upload image";
  } finally {
    assetUploading.value = false;
  }
}

async function removeBackgroundFromAsset(asset: AssetLibraryItem): Promise<void> {
  if (asset.uploaded || assetUploading.value || backgroundRemovingAssetId.value) {
    return;
  }

  backgroundRemovingAssetId.value = asset.id;
  assetPreviewError.value = "";
  try {
    const uploaded = await removeBackgroundImageAsset(asset.file);
    const processedAsset: AssetLibraryItem = {
      ...asset,
      id: createAssetId(),
      name: asset.name + " (no background)",
      url: backendAssetUrl(uploaded.url),
      uploaded,
    };
    assetLibrary.value = [...assetLibrary.value, processedAsset];
    selectedAssetId.value = processedAsset.id;
    activeTool.value = "image";
  } catch (unknownError) {
    assetPreviewError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot remove image background";
  } finally {
    backgroundRemovingAssetId.value = null;
  }
}

async function addPreviewAssetToCanvas(x = 12, y = 12): Promise<void> {
  const asset = selectedAsset.value;
  if (!asset) {
    assetPreviewError.value = "Choose or drop an image in Assets first.";
    return;
  }
  await addAssetToCanvas(asset, x, y);
}

const iconDragDataType = "application/x-vizi-icon-id";

function dragEventHasType(event: DragEvent, type: string): boolean {
  return Array.from(event.dataTransfer?.types ?? []).includes(type);
}

function iconAssetById(iconId: string | null): IconAsset | null {
  return iconAssets.find((icon) => icon.id === iconId) ?? null;
}

function draggedIconAsset(event?: DragEvent): IconAsset | null {
  const iconId = event?.dataTransfer?.getData(iconDragDataType) || draggedIconId.value;
  return iconAssetById(iconId);
}

const icons8DragDataType = "application/x-vizi-icons8-id";

function icons8IconById(iconId: string | null): Icons8Icon | null {
  return icons8Results.value.find((icon) => icon.id === iconId) ?? null;
}

function draggedIcons8Icon(event?: DragEvent): Icons8Icon | null {
  const iconId = event?.dataTransfer?.getData(icons8DragDataType) || draggedIcons8Id.value;
  return icons8IconById(iconId);
}

function onAssetDragOver(event: DragEvent): void {
  const hasFiles = dragEventHasType(event, "Files");
  const hasIcon = dragEventHasType(event, iconDragDataType) || dragEventHasType(event, icons8DragDataType) || draggedIconId.value !== null || draggedIcons8Id.value !== null;
  if (!hasFiles && !hasIcon) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = "copy";
  }
  assetDropActive.value = true;
}

function onAssetDragLeave(event: DragEvent): void {
  const related = event.relatedTarget as Node | null;
  if (related && (event.currentTarget as HTMLElement).contains(related)) {
    return;
  }
  assetDropActive.value = false;
}

async function onAssetDrop(event: DragEvent): Promise<void> {
  event.preventDefault();
  assetDropActive.value = false;
  if (draggedIconAsset(event) || draggedIcons8Icon(event)) {
    draggedIconId.value = null;
    draggedIcons8Id.value = null;
    return;
  }
  const files = event.dataTransfer?.files;
  if (!files?.length) {
    return;
  }
  await ingestImageFiles(files);
}

async function onWorkspaceDrop(event: DragEvent): Promise<void> {
  event.preventDefault();
  assetDropActive.value = false;
  const droppedIcon = draggedIconAsset(event);
  if (droppedIcon) {
    const position = layerPlacementFromClientPoint(event.target, event.clientX, event.clientY, 10, 10);
    const layer = createIconLayer(droppedIcon, position.x, position.y);
    const layers = [...editableLayers.value, layer];
    commitLayers(layers, [layers.length - 1]);
    draggedIconId.value = null;
    returnToSelectTool();
    return;
  }
  const droppedIcons8 = draggedIcons8Icon(event);
  if (droppedIcons8) {
    const position = layerPlacementFromClientPoint(event.target, event.clientX, event.clientY, 10, 10);
    addIcons8IconToCanvas(droppedIcons8, position.x, position.y);
    draggedIcons8Id.value = null;
    returnToSelectTool();
    return;
  }
  const files = event.dataTransfer?.files;
  if (!files?.length) {
    return;
  }
  const accepted = await ingestImageFiles(files);
  if (accepted.length === 0) {
    return;
  }
  const position = layerPlacementFromClientPoint(event.target, event.clientX, event.clientY, 32, 32);
  // Place each dropped image with a small offset cascade.
  for (let index = 0; index < accepted.length; index += 1) {
    const asset = accepted[index]!;
    await addAssetToCanvas(
      asset,
      clamp(position.x + index * 4, 0, 68),
      clamp(position.y + index * 4, 0, 68),
    );
  }
}

function layerPlacementFromClientPoint(
  target: EventTarget | null,
  clientX: number,
  clientY: number,
  width: number,
  height: number,
): { x: number; y: number } {
  const frame = (target as HTMLElement | null)?.closest<HTMLElement>(".canvas-frame");
  if (!frame) {
    return { x: 50 - width / 2, y: 50 - height / 2 };
  }
  const rect = frame.getBoundingClientRect();
  const x = ((clientX - rect.left) / rect.width) * 100 - width / 2;
  const y = ((clientY - rect.top) / rect.height) * 100 - height / 2;
  return {
    x: roundGeometry(clamp(x, 0, 100 - width)),
    y: roundGeometry(clamp(y, 0, 100 - height)),
  };
}

function layerPlacementFromPointer(event: PointerEvent, width: number, height: number): { x: number; y: number } {
  return layerPlacementFromClientPoint(event.target, event.clientX, event.clientY, width, height);
}

async function handleCanvasPointerDown(event: PointerEvent): Promise<void> {
  if (activeTool.value === "select") {
    clearLayerSelection();
    return;
  }

  event.preventDefault();
  event.stopPropagation();

  if (activeTool.value === "image") {
    const { x, y } = layerPlacementFromPointer(event, 28, 28);
    if (!selectedAsset.value) {
      assetPreviewError.value = "Choose or drop an image in Assets before placing it.";
      assetFileInput.value?.click();
      return;
    }
    await addPreviewAssetToCanvas(x, y);
    return;
  }

  const layer = createToolLayer(activeTool.value, event);
  if (!layer) {
    return;
  }
  const layers = [...editableLayers.value, layer];
  commitLayers(layers, [layers.length - 1]);
  returnToSelectTool();
}

function createIconLayer(icon: IconAsset, x: number, y: number): CanvasLayer {
  return {
    type: "icon",
    name: icon.label,
    text: icon.glyph,
    iconId: icon.id,
    page: selectedPage.value,
    x,
    y,
    width: 10,
    height: 10,
    fill: "#0B4F9C",
    color: "#0B4F9C",
    fontFamily: "\"Apple Color Emoji\", \"Segoe UI Emoji\", \"Noto Color Emoji\", \"Segoe UI Symbol\", sans-serif",
    fontSize: 28,
    fontWeight: 600,
    opacity: 1,
  };
}

function createIcons8IconLayer(icon: Icons8Icon, x: number, y: number): CanvasLayer {
  return {
    type: "image",
    name: icon.name,
    src: icon.previewUrl,
    page: selectedPage.value,
    x,
    y,
    width: 10,
    height: 10,
    fill: "transparent",
    stroke: "transparent",
    strokeWidth: 0,
    radius: 0,
    opacity: 1,
    assetProvider: "icons8",
    sourceUrl: icon.sourceUrl,
    creditText: "Icons by Icons8",
    creditUrl: "https://icons8.com",
  };
}

function addIcons8IconToCanvas(icon: Icons8Icon, x = 12, y = 12): void {
  const layer = createIcons8IconLayer(icon, x, y);
  const layers = [...editableLayers.value, layer];
  commitLayers(layers, [layers.length - 1]);
  saveMessage.value = "Icons8 icon added. Keep the Icons8 credit visible for free use.";
}

async function runIcons8Search(): Promise<void> {
  const term = icons8SearchQuery.value.trim();
  icons8Error.value = "";
  icons8Message.value = "";
  if (!term) {
    icons8Error.value = "Enter an icon keyword first.";
    return;
  }
  icons8Loading.value = true;
  try {
    const response = await searchIcons8(term, icons8SearchLanguage.value, icons8DefaultPlatform, 24);
    icons8Results.value = response.icons;
    icons8Message.value = response.message || (response.icons.length ? "" : "No Icons8 icons found.");
  } catch (unknownError) {
    icons8Results.value = [];
    icons8Error.value = unknownError instanceof Error ? unknownError.message : "Icons8 search failed";
  } finally {
    icons8Loading.value = false;
  }
}
function createToolLayer(tool: EditorTool, event: PointerEvent): CanvasLayer | null {
  const page = selectedPage.value;
  if (tool === "text") {
    const size = { width: 32, height: 12 };
    const position = layerPlacementFromPointer(event, size.width, size.height);
    return {
      type: "text",
      name: "Text",
      text: "Text",
      page,
      ...position,
      ...size,
      color: "#1f2937",
      fontFamily: "system-ui, \"Segoe UI\", \"Be Vietnam Pro\", Arial, sans-serif",
      fontSize: 18,
      fontWeight: 600,
      opacity: 1,
    };
  }
  if (tool === "rect" || tool === "shape") {
    const size = { width: 24, height: 16 };
    const position = layerPlacementFromPointer(event, size.width, size.height);
    return {
      type: "rect",
      name: "Board",
      page,
      ...position,
      ...size,
      fill: "#ffffff",
      stroke: "#b1b2b5",
      strokeWidth: 1,
      radius: 4,
      opacity: 1,
    };
  }
  if (tool === "ellipse") {
    const size = { width: 18, height: 18 };
    const position = layerPlacementFromPointer(event, size.width, size.height);
    return {
      type: "ellipse",
      name: "Ellipse",
      page,
      ...position,
      ...size,
      fill: "#b1b2b5",
      stroke: "#b1b2b5",
      strokeWidth: 1,
      radius: 999,
      opacity: 1,
    };
  }
  if (tool === "icon") {
    const size = { width: 10, height: 10 };
    const position = layerPlacementFromPointer(event, size.width, size.height);
    return createIconLayer(selectedIconAsset.value, position.x, position.y);
  }
  return null;
}

function startCanvasPan(event: PointerEvent): void {
  if ((event.target as HTMLElement | null)?.closest(".canvas-frame, .editor-toolbar, .editor-color-bar, .editor-zoom-controls")) {
    return;
  }

  event.preventDefault();
  event.stopPropagation();
  clearLayerSelection();
  const startX = event.clientX;
  const startY = event.clientY;
  const startPanX = editorPanX.value;
  const startPanY = editorPanY.value;

  const move = (moveEvent: PointerEvent) => {
    editorPanX.value = startPanX + moveEvent.clientX - startX;
    editorPanY.value = startPanY + moveEvent.clientY - startY;
  };
  const stop = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", stop);
    window.removeEventListener("pointercancel", stop);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", stop);
  window.addEventListener("pointercancel", stop);
}

function targetAcceptsTextInput(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return target.isContentEditable || ["INPUT", "SELECT", "TEXTAREA"].includes(target.tagName);
}

function copySelectedLayersToClipboard(cut = false): void {
  if (selectedLayerIndexes.value.length === 0) {
    return;
  }
  const selected = selectedLayerIndexes.value
    .slice()
    .sort((left, right) => left - right)
    .map((index) => editableLayers.value[index])
    .filter(isCanvasLayer);
  if (selected.length === 0) {
    return;
  }
  layerClipboard.value = cloneLayers(selected);
  if (cut) {
    deleteSelectedLayers();
  }
}

function pasteLayersFromClipboard(): void {
  if (layerClipboard.value.length === 0) {
    return;
  }
  const duplicates = layerClipboard.value.map((layer) => {
    const width = numberValue(layer.width, layerDefaultSize(layer, "width"));
    const height = numberValue(layer.height, layerDefaultSize(layer, "height"));
    return {
      ...cloneLayers([layer])[0],
      page: selectedPage.value,
      x: roundGeometry(clamp(numberValue(layer.x, 8) + 4, 0, 100 - width)),
      y: roundGeometry(clamp(numberValue(layer.y, 8) + 4, 0, 100 - height)),
    };
  });
  const insertAt = editableLayers.value.length;
  const layers = [...editableLayers.value, ...duplicates];
  // Nudge clipboard so repeated paste cascades.
  layerClipboard.value = cloneLayers(duplicates);
  commitLayers(layers, duplicates.map((_, offset) => insertAt + offset));
  returnToSelectTool();
}

function handleEditorShortcut(event: KeyboardEvent): void {
  if (!isEditorRoute.value) {
    return;
  }
  if (event.key === "Escape") {
    event.preventDefault();
    event.stopPropagation();
    if (layerContextMenu.value) {
      closeLayerContextMenu();
      return;
    }
    clearLayerSelection();
    returnToSelectTool();
    return;
  }
  if (targetAcceptsTextInput(event.target)) {
    return;
  }

  const key = event.key.toLowerCase();
  const modifier = event.ctrlKey || event.metaKey;

  if (modifier && key === "z") {
    event.preventDefault();
    event.shiftKey ? redoLayerChange() : undoLayerChange();
    return;
  }
  if (modifier && key === "y") {
    event.preventDefault();
    redoLayerChange();
    return;
  }
  if (modifier && key === "c") {
    event.preventDefault();
    copySelectedLayersToClipboard(false);
    return;
  }
  if (modifier && key === "x") {
    event.preventDefault();
    copySelectedLayersToClipboard(true);
    return;
  }
  if (modifier && key === "v") {
    event.preventDefault();
    pasteLayersFromClipboard();
    return;
  }
  if (modifier && key === "d") {
    event.preventDefault();
    duplicateSelectedLayers();
    return;
  }
  if (modifier && (key === "=" || key === "+" || event.key === "+")) {
    event.preventDefault();
    updateEditorZoom(10);
    return;
  }
  if (modifier && key === "-") {
    event.preventDefault();
    updateEditorZoom(-10);
    return;
  }
  if (modifier && key === "0") {
    event.preventDefault();
    resetEditorView();
    return;
  }
  if (modifier && key === "a") {
    event.preventDefault();
    const indexes = pageLayerEntries.value.map((entry) => entry.index);
    selectedLayerIndexes.value = indexes;
    selectedLayerIndex.value = indexes.at(-1) ?? 0;
    return;
  }
  if (!modifier && (event.key === "Delete" || event.key === "Backspace")) {
    event.preventDefault();
    deleteSelectedLayers();
    return;
  }
  if (!modifier && !event.altKey) {
    if (key === "v") {
      event.preventDefault();
      selectTool("select");
    } else if (key === "t") {
      event.preventDefault();
      selectTool("text");
    } else if (key === "r") {
      event.preventDefault();
      selectTool("rect");
    } else if (key === "e") {
      event.preventDefault();
      selectTool("ellipse");
    } else if (key === "i") {
      event.preventDefault();
      selectTool("image");
    }
  }
}


function handleLayerContextMenuKeyup(event: KeyboardEvent): void {
  if (!isEditorRoute.value || event.key !== "Escape" || !layerContextMenu.value) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  closeLayerContextMenu();
}
function selectTool(tool: EditorTool): void {
  activeTool.value = tool;
  saveMessage.value = "";
  if (tool === "image" && !selectedAsset.value) {
    assetFileInput.value?.click();
  }
}
function selectIconForPlacement(icon: IconAsset): void {
  selectedIconId.value = icon.id;
  activeTool.value = "icon";
  saveMessage.value = "";
}

function startIconDrag(icon: IconAsset, event: DragEvent): void {
  selectIconForPlacement(icon);
  draggedIconId.value = icon.id;
  event.dataTransfer?.setData(iconDragDataType, icon.id);
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "copy";
  }
}

function endIconDrag(): void {
  draggedIconId.value = null;
  assetDropActive.value = false;
}

function startIcons8Drag(icon: Icons8Icon, event: DragEvent): void {
  draggedIcons8Id.value = icon.id;
  event.dataTransfer?.setData(icons8DragDataType, icon.id);
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "copy";
  }
}

function endIcons8Drag(): void {
  draggedIcons8Id.value = null;
  assetDropActive.value = false;
}

function selectLayer(index: number, event?: MouseEvent | KeyboardEvent): void {
  const additive = event?.shiftKey || event?.ctrlKey || event?.metaKey;
  if (!additive) {
    selectedLayerIndexes.value = [index];
    selectedLayerIndex.value = index;
  } else if (selectedLayerIndexes.value.includes(index)) {
    selectedLayerIndexes.value = selectedLayerIndexes.value.filter((selected) => selected !== index);
    selectedLayerIndex.value = selectedLayerIndexes.value[0] ?? index;
  } else {
    selectedLayerIndexes.value = [...selectedLayerIndexes.value, index].sort((left, right) => left - right);
    selectedLayerIndex.value = index;
  }
  saveMessage.value = "";
}

function clearLayerSelection(): void {
  selectedLayerIndexes.value = [];
  selectedLayerIndex.value = 0;
  saveMessage.value = "";
}

function handleCanvasLayerPointerDown(localIndex: number, event: PointerEvent): void {
  startLayerDrag(localCanvasIndexToGlobal(localIndex), event);
}

function handleCanvasLayerResizePointerDown(localIndex: number, event: PointerEvent): void {
  startLayerResize(localCanvasIndexToGlobal(localIndex), event);
}

function handleCanvasLayerRotatePointerDown(localIndex: number, event: PointerEvent): void {
  startLayerRotate(localCanvasIndexToGlobal(localIndex), event);
}

function handleCanvasLayerSelect(localIndex: number, event: KeyboardEvent): void {
  selectLayer(localCanvasIndexToGlobal(localIndex), event);
}

function startLayerDrag(index: number, event: PointerEvent): void {
  if (activeTool.value !== "select") {
    void handleCanvasPointerDown(event);
    return;
  }

  const wasSelected = selectedLayerIndexes.value.includes(index);
  if (!wasSelected || event.shiftKey || event.ctrlKey || event.metaKey) {
    selectLayer(index, event);
  }
  if (!selectedLayerIndexes.value.includes(index)) {
    return;
  }

  const frame = (event.currentTarget as HTMLElement).closest<HTMLElement>(".canvas-frame");
  if (!frame) {
    return;
  }
  const movableIndexes = selectedLayerIndexes.value.filter((selected) => {
    const layer = editableLayers.value[selected];
    return layer && !layerIsLocked(layer);
  });
  if (movableIndexes.length === 0) {
    return;
  }

  event.preventDefault();
  rememberLayerState();
  const frameRect = frame.getBoundingClientRect();
  const startX = event.clientX;
  const startY = event.clientY;
  const startPositions = new Map(movableIndexes.map((selected) => {
    const layer = editableLayers.value[selected];
    return [selected, {
      x: numberValue(layer.x, 8),
      y: numberValue(layer.y, 8),
      width: numberValue(layer.width, layer.type === "text" ? 45 : 32),
      height: numberValue(layer.height, layer.type === "text" ? 16 : 26),
    }];
  }));
  const positions = [...startPositions.values()];
  const minimumDeltaX = Math.max(...positions.map((position) => -position.x));
  const maximumDeltaX = Math.min(...positions.map((position) => 100 - position.width - position.x));
  const minimumDeltaY = Math.max(...positions.map((position) => -position.y));
  const maximumDeltaY = Math.min(...positions.map((position) => 100 - position.height - position.y));

  const move = (moveEvent: PointerEvent) => {
    const deltaX = clamp(
      ((moveEvent.clientX - startX) / frameRect.width) * 100,
      minimumDeltaX,
      maximumDeltaX,
    );
    const deltaY = clamp(
      ((moveEvent.clientY - startY) / frameRect.height) * 100,
      minimumDeltaY,
      maximumDeltaY,
    );
    const layers = [...editableLayers.value];
    for (const selected of movableIndexes) {
      const layer = layers[selected];
      const start = startPositions.get(selected);
      if (!layer || !start) {
        continue;
      }
      layers[selected] = {
        ...layer,
        x: roundGeometry(start.x + deltaX),
        y: roundGeometry(start.y + deltaY),
      };
    }
    editableLayers.value = layers;
    saveMessage.value = "";
  };
  const stop = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", stop);
    window.removeEventListener("pointercancel", stop);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", stop);
  window.addEventListener("pointercancel", stop);
}

function startLayerResize(index: number, event: PointerEvent): void {
  const layer = editableLayers.value[index];
  const frame = (event.currentTarget as HTMLElement).closest<HTMLElement>(".canvas-frame");
  if (!layer || !frame || layerIsLocked(layer) || selectedLayerIndexes.value.length !== 1) {
    return;
  }

  event.preventDefault();
  rememberLayerState();
  const frameRect = frame.getBoundingClientRect();
  const startX = event.clientX;
  const startY = event.clientY;
  const x = numberValue(layer.x, 8);
  const y = numberValue(layer.y, 8);
  const startWidth = numberValue(layer.width, layer.type === "text" ? 45 : 32);
  const startHeight = numberValue(layer.height, layer.type === "text" ? 16 : 26);

  const move = (moveEvent: PointerEvent) => {
    const width = clamp(
      roundGeometry(startWidth + ((moveEvent.clientX - startX) / frameRect.width) * 100),
      3,
      100 - x,
    );
    const height = clamp(
      roundGeometry(startHeight + ((moveEvent.clientY - startY) / frameRect.height) * 100),
      3,
      100 - y,
    );
    editableLayers.value[index] = { ...editableLayers.value[index], width, height };
    saveMessage.value = "";
  };
  const stop = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", stop);
    window.removeEventListener("pointercancel", stop);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", stop);
  window.addEventListener("pointercancel", stop);
}

function startLayerRotate(index: number, event: PointerEvent): void {
  const layer = editableLayers.value[index];
  const layerElement = (event.currentTarget as HTMLElement).closest<HTMLElement>(".canvas-layer");
  if (!layer || !layerElement || layerIsLocked(layer) || selectedLayerIndexes.value.length !== 1) {
    return;
  }

  event.preventDefault();
  rememberLayerState();
  const layerRect = layerElement.getBoundingClientRect();
  const centerX = layerRect.left + layerRect.width / 2;
  const centerY = layerRect.top + layerRect.height / 2;
  const startAngle = angleFromCenter(event.clientX, event.clientY, centerX, centerY);
  const startRotation = numberValue(layer.rotation, 0);

  const move = (moveEvent: PointerEvent) => {
    const currentAngle = angleFromCenter(moveEvent.clientX, moveEvent.clientY, centerX, centerY);
    editableLayers.value[index] = {
      ...editableLayers.value[index],
      rotation: normalizeDegrees(startRotation + currentAngle - startAngle),
    };
    saveMessage.value = "";
  };
  const stop = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", stop);
    window.removeEventListener("pointercancel", stop);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", stop);
  window.addEventListener("pointercancel", stop);
}

function numberValue(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function angleFromCenter(clientX: number, clientY: number, centerX: number, centerY: number): number {
  return Math.atan2(clientY - centerY, clientX - centerX) * 180 / Math.PI;
}

function normalizeDegrees(value: number): number {
  const rounded = roundGeometry(value);
  return roundGeometry(((rounded % 360) + 360) % 360);
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(Math.max(value, minimum), maximum);
}

function roundGeometry(value: number): number {
  return Math.round(value * 10) / 10;
}

function selectedLayerNumber(field: string, fallback: number): number {
  const layer = selectedLayer.value;
  return layer ? numberValue(layer[field], fallback) : fallback;
}

/** X/Y UI: card center is 0; unit = 1% of card; values are rounded to one decimal. */
function selectedLayerCenterCoord(axis: "x" | "y"): number {
  const layer = selectedLayer.value;
  if (!layer) {
    return 0;
  }
  const size = axis === "x"
    ? numberValue(layer.width, layerDefaultSize(layer, "width"))
    : numberValue(layer.height, layerDefaultSize(layer, "height"));
  const origin = numberValue(layer[axis], 8);
  return roundGeometry(origin + size / 2 - 50);
}

function selectedLayerSizeInt(field: "width" | "height"): number {
  const layer = selectedLayer.value;
  if (!layer) {
    return layerDefaultSize({ type: "text" }, field);
  }
  return roundGeometry(numberValue(layer[field], layerDefaultSize(layer, field)));
}

function selectedLayerRotationInt(): number {
  const layer = selectedLayer.value;
  return layer ? normalizeDegrees(numberValue(layer.rotation, 0)) : 0;
}

function selectedLayerPercent(field: string, fallback: number): number {
  const layer = selectedLayer.value;
  const value = layer ? numberValue(layer[field], fallback / 100) : fallback / 100;
  return Math.round(clamp(value, 0, 1) * 100);
}

function layerDefaultSize(layer: CanvasLayer, field: "width" | "height"): number {
  if (field === "width") {
    return layer.type === "text" ? 45 : 32;
  }
  return layer.type === "text" ? 16 : 26;
}

function updateSelectedLayerNumber(field: "x" | "y" | "width" | "height" | "rotation", event: Event): void {
  const layer = selectedLayer.value;
  const value = (event.target as HTMLInputElement).valueAsNumber;
  if (!layer || !Number.isFinite(value) || !selectedLayerCanEditGeometry.value) {
    return;
  }

  const width = numberValue(layer.width, layerDefaultSize(layer, "width"));
  const height = numberValue(layer.height, layerDefaultSize(layer, "height"));
  const x = numberValue(layer.x, 8);
  const y = numberValue(layer.y, 8);
  const patch: CanvasLayer = { ...layer };

  if (field === "rotation") {
    patch.rotation = normalizeDegrees(value);
  } else if (field === "x") {
    // Input is center-origin percent; storage stays top-left percent rounded to one decimal.
    const minCenter = width / 2;
    const maxCenter = 100 - width / 2;
    const center = clamp(50 + roundGeometry(value), minCenter, maxCenter);
    patch.x = roundGeometry(center - width / 2);
  } else if (field === "y") {
    const minCenter = height / 2;
    const maxCenter = 100 - height / 2;
    const center = clamp(50 + roundGeometry(value), minCenter, maxCenter);
    patch.y = roundGeometry(center - height / 2);
  } else if (field === "width") {
    const nextWidth = clamp(roundGeometry(value), 3, 100);
    const center = x + width / 2;
    const nextX = clamp(center - nextWidth / 2, 0, 100 - nextWidth);
    patch.width = nextWidth;
    patch.x = roundGeometry(nextX);
  } else {
    const nextHeight = clamp(roundGeometry(value), 3, 100);
    const center = y + height / 2;
    const nextY = clamp(center - nextHeight / 2, 0, 100 - nextHeight);
    patch.height = nextHeight;
    patch.y = roundGeometry(nextY);
  }

  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = patch;
  commitLayers(layers);
}

function selectedLayerString(field: string, fallback: string): string {
  const layer = selectedLayer.value;
  return layer ? optionalString(layer[field]) ?? fallback : fallback;
}

function selectedLayerHexColor(field: string, fallback: string): string {
  const value = selectedLayerString(field, fallback);
  return /^#[\da-f]{6}$/i.test(value) ? value : fallback;
}

function selectedLayerColor(target: ColorTarget): string {
  const layer = selectedLayer.value;
  if (!layer) {
    return target === "stroke" ? "#000000" : "#ffffff";
  }
  const value = target === "stroke"
    ? optionalString(layer.stroke)
    : optionalString(layer.type === "text" ? layer.color : layer.fill ?? layer.background);
  return value && /^#[\da-f]{6}$/i.test(value)
    ? value
    : target === "stroke" ? "#000000" : "#ffffff";
}

function updateSelectedTextField(field: "fontFamily" | "fontSize" | "fontWeight", event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || !selectedLayerCanEditText.value) {
    return;
  }

  const target = event.target as HTMLInputElement | HTMLSelectElement;
  const value = field === "fontFamily"
    ? target.value
    : Number.parseFloat(target.value);
  if (typeof value === "number" && !Number.isFinite(value)) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = {
    ...layer,
    [field]: field === "fontSize"
      ? clamp(value as number, 6, 120)
      : field === "fontWeight" ? clamp(value as number, 100, 900) : value,
  };
  commitLayers(layers);
}

function updateSelectedAppearanceColor(target: ColorTarget, event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || !selectedLayerCanEditAppearance.value) {
    return;
  }

  const value = (event.target as HTMLInputElement).value;
  const field = target === "stroke"
    ? "stroke"
    : layer.type === "text" ? "color" : "fill";
  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = { ...layer, [field]: value };
  commitLayers(layers);
}

function updateSelectedAppearanceNumber(field: "opacity" | "strokeWidth", event: Event): void {
  const layer = selectedLayer.value;
  const value = (event.target as HTMLInputElement).valueAsNumber;
  if (!layer || !Number.isFinite(value) || !selectedLayerCanEditAppearance.value) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = {
    ...layer,
    [field]: field === "opacity"
      ? Math.round(clamp(value, 0, 1) * 100) / 100
      : Math.round(clamp(value, 0, 20) * 100) / 100,
  };
  commitLayers(layers);
}

function updateSelectedEffectColor(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || !selectedLayerCanEditAppearance.value) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = {
    ...layer,
    shadowColor: (event.target as HTMLInputElement).value,
  };
  commitLayers(layers);
}

function updateSelectedEffectNumber(
  field: "shadowX" | "shadowY" | "shadowBlur" | "shadowSpread" | "blur",
  event: Event,
): void {
  const layer = selectedLayer.value;
  const value = (event.target as HTMLInputElement).valueAsNumber;
  if (!layer || !Number.isFinite(value) || !selectedLayerCanEditAppearance.value) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = {
    ...layer,
    [field]: field === "shadowX" || field === "shadowY"
      ? Math.round(clamp(value, -50, 50) * 100) / 100
      : Math.round(clamp(value, 0, 50) * 100) / 100,
  };
  commitLayers(layers);
}

function updateSelectedShadowOpacity(event: Event): void {
  const layer = selectedLayer.value;
  const value = (event.target as HTMLInputElement).valueAsNumber;
  if (!layer || !Number.isFinite(value) || !selectedLayerCanEditAppearance.value) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = {
    ...layer,
    shadowOpacity: Math.round(clamp(value, 0, 100)) / 100,
  };
  commitLayers(layers);
}

function toggleLayerVisibility(index: number): void {
  const layer = editableLayers.value[index];
  if (!layer) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[index] = { ...layer, visible: !layerIsVisible(layer) };
  commitLayers(layers, [index]);
}

function toggleLayerLock(index: number): void {
  const layer = editableLayers.value[index];
  if (!layer) {
    return;
  }

  const layers = [...editableLayers.value];
  layers[index] = { ...layer, locked: !layerIsLocked(layer) };
  commitLayers(layers, [index]);
}

function moveLayer(index: number, direction: -1 | 1): void {
  const nextIndex = index + direction;
  if (nextIndex < 0 || nextIndex >= editableLayers.value.length) {
    return;
  }

  const layers = [...editableLayers.value];
  [layers[index], layers[nextIndex]] = [layers[nextIndex], layers[index]];
  const nextSelectedIndexes = selectedLayerIndexes.value
    .map((selected) => selected === index ? nextIndex : selected === nextIndex ? index : selected)
    .sort((left, right) => left - right);
  commitLayers(layers, nextSelectedIndexes);
}

function layerPanelIndex(index: number): number {
  return layerPanelItems.value.findIndex((item) => item.index === index);
}

function canMoveLayerInPanel(index: number, direction: -1 | 1): boolean {
  const panelIndex = layerPanelIndex(index);
  const nextPanelIndex = panelIndex + direction;
  return panelIndex >= 0 && nextPanelIndex >= 0 && nextPanelIndex < layerPanelItems.value.length;
}

function moveLayerInPanel(index: number, direction: -1 | 1): void {
  const panelIndex = layerPanelIndex(index);
  if (panelIndex < 0) {
    return;
  }
  reorderLayerByPanelIndex(index, panelIndex + direction);
}

function reorderLayerByPanelIndex(sourceIndex: number, targetPanelIndex: number): void {
  const panelOrder = layerPanelItems.value.map((item) => item.index);
  const currentPanelIndex = panelOrder.indexOf(sourceIndex);
  if (currentPanelIndex < 0 || targetPanelIndex < 0 || targetPanelIndex >= panelOrder.length) {
    return;
  }

  const [moved] = panelOrder.splice(currentPanelIndex, 1);
  panelOrder.splice(targetPanelIndex, 0, moved);
  const newPageOrder = [...panelOrder].reverse();
  const pagePositions = editableLayers.value
    .map((layer, index) => (layerPage(layer) === selectedPage.value ? index : -1))
    .filter((index) => index >= 0);
  if (pagePositions.length !== newPageOrder.length) {
    return;
  }

  const nextLayers = [...editableLayers.value];
  const oldToNew = new Map<number, number>();
  pagePositions.forEach((position, order) => {
    const oldIndex = newPageOrder[order]!;
    nextLayers[position] = editableLayers.value[oldIndex]!;
    oldToNew.set(oldIndex, position);
  });

  const nextSelectedIndexes = selectedLayerIndexes.value
    .map((selected) => oldToNew.get(selected) ?? selected)
    .filter((index, _i, all) => all.indexOf(index) === index)
    .sort((left, right) => left - right);
  commitLayers(
    nextLayers,
    nextSelectedIndexes.length ? nextSelectedIndexes : [oldToNew.get(sourceIndex) ?? sourceIndex],
  );
}

function startLayerPanelDrag(index: number, event: DragEvent): void {
  draggingLayerIndex.value = index;
  layerContextMenu.value = null;
  event.dataTransfer?.setData("text/plain", String(index));
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
  }
}

function dropLayerOnPanel(index: number, event: DragEvent): void {
  event.preventDefault();
  const source = Number(event.dataTransfer?.getData("text/plain") ?? draggingLayerIndex.value);
  draggingLayerIndex.value = null;
  if (!Number.isInteger(source) || source === index) {
    return;
  }
  const targetPanelIndex = layerPanelIndex(index);
  reorderLayerByPanelIndex(source, targetPanelIndex);
}

function layerContextMenuPosition(x: number, y: number): { x: number; y: number } {
  if (typeof window === "undefined") {
    return { x, y };
  }
  return {
    x: clamp(x, layerContextMenuMargin, Math.max(layerContextMenuMargin, window.innerWidth - layerContextMenuWidth - layerContextMenuMargin)),
    y: clamp(y, layerContextMenuMargin, Math.max(layerContextMenuMargin, window.innerHeight - layerContextMenuHeight - layerContextMenuMargin)),
  };
}

function openLayerContextMenu(index: number, event: MouseEvent): void {
  event.preventDefault();
  event.stopPropagation();
  selectLayer(index, event);
  layerContextMenu.value = { index, ...layerContextMenuPosition(event.clientX, event.clientY) };
  window.requestAnimationFrame(() => layerContextMenuElement.value?.focus());
}

function closeLayerContextMenu(): void {
  layerContextMenu.value = null;
}

function handleGlobalPointerDown(event: PointerEvent): void {
  if (!layerContextMenu.value) {
    return;
  }
  const target = event.target;
  if (target instanceof HTMLElement && target.closest(".editor-layer-context-menu")) {
    return;
  }
  closeLayerContextMenu();
}

function renameLayer(index: number): void {
  const layer = editableLayers.value[index];
  if (!layer) {
    return;
  }
  const nextName = window.prompt("Layer name", layerDisplayName(layer));
  if (nextName === null) {
    return;
  }
  const trimmed = nextName.trim().slice(0, 80);
  if (!trimmed) {
    return;
  }
  const layers = [...editableLayers.value];
  layers[index] = { ...layer, name: trimmed };
  commitLayers(layers, [index]);
  closeLayerContextMenu();
}

function duplicateSelectedLayers(): void {
  if (!canDuplicateSelectedLayers.value) {
    return;
  }

  const selectedIndexes = selectedLayerIndexes.value
    .filter((index) => editableLayers.value[index])
    .sort((left, right) => left - right);
  if (selectedIndexes.length === 0) {
    return;
  }

  const duplicates = selectedIndexes.map((index) => {
    const layer = cloneLayers([editableLayers.value[index]])[0];
    const width = numberValue(layer.width, layerDefaultSize(layer, "width"));
    const height = numberValue(layer.height, layerDefaultSize(layer, "height"));
    return {
      ...layer,
      page: layerPage(layer),
      x: Math.round(clamp(numberValue(layer.x, 8) + 4, 0, 100 - width) * 100) / 100,
      y: Math.round(clamp(numberValue(layer.y, 8) + 4, 0, 100 - height) * 100) / 100,
    };
  });
  const insertAt = selectedIndexes.at(-1)! + 1;
  const layers = [...editableLayers.value];
  layers.splice(insertAt, 0, ...duplicates);
  commitLayers(layers, duplicates.map((_, offset) => insertAt + offset));
}

async function runPreflightCheck(): Promise<void> {
  if (!design.value || preflightLoading.value) {
    return;
  }
  preflightLoading.value = true;
  preflightError.value = "";
  try {
    // Persist latest canvas so server preflight matches the current editor state.
    design.value = await updateDesign(design.value.id, design.value.name, serializeCanvas());
    saveMessage.value = `Draft #${design.value.id} saved`;
    preflightReport.value = await preflightDesign(design.value.id);
  } catch (unknownError) {
    preflightError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot run preflight";
    preflightReport.value = null;
  } finally {
    preflightLoading.value = false;
  }
}

function deleteSelectedLayers(): void {
  if (!canDeleteSelectedLayers.value) {
    return;
  }

  const selected = new Set(selectedLayerIndexes.value);
  const firstSelected = Math.min(...selectedLayerIndexes.value);
  const layers = editableLayers.value.filter((_, index) => !selected.has(index));
  const nextIndex = layers.length > 0 ? Math.min(firstSelected, layers.length - 1) : -1;
  commitLayers(layers, nextIndex >= 0 ? [nextIndex] : []);
}

function applyQuickColor(color: string): void {
  const layer = editableLayers.value[selectedLayerIndex.value];
  if (!layer || layerIsLocked(layer) || !selectedLayerCanEditAppearance.value) {
    return;
  }

  const field = activeColorTarget.value === "stroke"
    ? "stroke"
    : layer.type === "text" ? "color" : "fill";
  const layers = [...editableLayers.value];
  layers[selectedLayerIndex.value] = { ...layer, [field]: color };
  commitLayers(layers);
}

async function requestAiTextRewrite(): Promise<void> {
  const currentDesign = design.value;
  const layer = selectedLayer.value;
  if (!currentDesign || !layer || !canRequestAiRewrite.value || aiRewriteLoading.value) {
    return;
  }

  const layerId = canvasLayerId(layer, selectedLayerIndex.value);
  const originalText = readLayerText(layer);
  aiRewriteLoading.value = true;
  aiRewriteError.value = "";
  aiRewritePreview.value = null;

  try {
    const response = await rewriteDesignText(
      currentDesign.id,
      layerId,
      aiRewritePrompt.value.trim(),
      aiRewriteStrength.value,
      selectedPage.value,
    );
    const action = response.actions.length === 1 ? response.actions[0] : null;
    if (
      response.schemaVersion !== 1
      || !action
      || action.op !== "update_text"
      || action.layerId !== layerId
      || typeof action.text !== "string"
    ) {
      throw new Error("AI rewrite returned an invalid preview");
    }

    aiRewritePreview.value = {
      response,
      layerId,
      originalText,
      replacementText: action.text,
    };
  } catch (unknownError) {
    aiRewriteError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot create AI rewrite preview";
  } finally {
    aiRewriteLoading.value = false;
  }
}

function applyAiTextRewrite(): void {
  const preview = aiRewritePreview.value;
  if (!preview) {
    return;
  }

  try {
    const result = applyTextRewritePreview(
      editableLayers.value,
      preview.layerId,
      preview.originalText,
      preview.replacementText,
    );
    commitLayers(result.layers, [result.selectedIndex]);
    aiRewritePreview.value = null;
    aiRewriteError.value = "";
    aiRewritePrompt.value = "";
  } catch (unknownError) {
    aiRewriteError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot apply AI rewrite preview";
  }
}

function rejectAiTextRewrite(): void {
  aiRewritePreview.value = null;
  aiRewriteError.value = "";
}

async function saveDraft(): Promise<void> {
  if (!design.value || saving.value) {
    return;
  }

  saving.value = true;
  saveMessage.value = "";
  saveError.value = "";

  try {
    const saved = await updateDesign(design.value.id, design.value.name, serializeCanvas());
    design.value = saved;
    undoLayerStack.value = [];
    redoLayerStack.value = [];
    saveMessage.value = `Draft #${saved.id} saved`;
  } catch (unknownError) {
    saveError.value = unknownError instanceof Error ? unknownError.message : "Cannot save draft";
  } finally {
    saving.value = false;
  }
}

async function renameDesign(): Promise<void> {
  if (!design.value || saving.value) {
    return;
  }
  const next = window.prompt("Draft name", design.value.name);
  if (next === null) {
    return;
  }
  const trimmed = next.trim().slice(0, 160);
  if (!trimmed || trimmed === design.value.name) {
    return;
  }

  saving.value = true;
  saveError.value = "";
  try {
    const saved = await updateDesign(design.value.id, trimmed, serializeCanvas());
    design.value = saved;
    saveMessage.value = `Renamed - Draft #${saved.id} saved`;
  } catch (unknownError) {
    saveError.value = unknownError instanceof Error ? unknownError.message : "Cannot rename draft";
  } finally {
    saving.value = false;
  }
}

async function deleteDraft(): Promise<void> {
  if (!design.value || deleting.value) {
    return;
  }
  if (!confirmingDelete.value) {
    confirmingDelete.value = true;
    saveMessage.value = "";
    saveError.value = "";
    return;
  }

  deleting.value = true;
  saveError.value = "";

  try {
    await deleteDesign(design.value.id);
    await router.push({ name: "designs" });
  } catch (unknownError) {
    saveError.value = unknownError instanceof Error ? unknownError.message : "Cannot delete draft";
    confirmingDelete.value = false;
  } finally {
    deleting.value = false;
  }
}

onMounted(async () => {
  document.addEventListener("keydown", handleEditorShortcut, true);
  document.addEventListener("keyup", handleLayerContextMenuKeyup, true);
  document.addEventListener("pointerdown", handleGlobalPointerDown, true);
  loadSidebarWidths();
  if (!Number.isFinite(designId.value)) {
    error.value = "Draft id is invalid";
    loading.value = false;
    return;
  }

  try {
    design.value = await getDesign(designId.value);
    editableLayers.value = parseCanvasLayers(design.value.canvasJson);
    undoLayerStack.value = [];
    redoLayerStack.value = [];
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load draft";
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleEditorShortcut, true);
  document.removeEventListener("keyup", handleLayerContextMenuKeyup, true);
  document.removeEventListener("pointerdown", handleGlobalPointerDown, true);
  revokeAllAssetLibrary();
});
</script>

<template>
  <section :class="isEditorRoute ? 'editor-view' : 'detail-view'">
    <RouterLink class="back-link" to="/designs">Back to drafts</RouterLink>

    <p v-if="loading" class="muted">Loading draft...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>

    <template v-else-if="design && isEditorRoute">
      <header class="editor-header">
        <div>
          <p class="eyebrow">Draft #{{ design.id }}</p>
          <h1>{{ design.name }}</h1>
          <button
            class="secondary-action editor-rename-button"
            type="button"
            :disabled="saving"
            @click="renameDesign"
          >
            Rename
          </button>
        </div>
        <div class="editor-header-actions">
          <span v-if="saveMessage" class="save-status" role="status">{{ saveMessage }}</span>
          <span v-else-if="!saveError" class="muted">Saved draft</span>
          <button
            class="secondary-action"
            type="button"
            :disabled="preflightLoading || saving"
            @click="runPreflightCheck"
          >
            {{ preflightLoading ? "Checking..." : "Preflight" }}
          </button>
          <RouterLink
            class="secondary-action"
            :to="{ name: 'checkout', params: { designId: design.id } }"
          >
            Checkout
          </RouterLink>
          <button class="primary-action" type="button" :disabled="saving" @click="saveDraft">
            {{ saving ? "Saving..." : "Save draft" }}
          </button>
        </div>
      </header>
      <p v-if="preflightError" class="error-text" role="alert">{{ preflightError }}</p>
      <div v-if="preflightReport" class="editor-preflight" role="status">
        <strong>{{ preflightReport.valid ? "Preflight passed" : "Preflight found issues" }}</strong>
        <ul v-if="preflightReport.issues.length">
          <li v-for="(issue, index) in preflightReport.issues" :key="`${issue.code}-${index}`">
            [{{ issue.level }}] {{ issue.message }}
            <span v-if="issue.layerIndex != null"> (layer {{ issue.layerIndex + 1 }})</span>
          </li>
        </ul>
        <p v-else class="muted">No issues reported.</p>
      </div>

      <div
        class="editor-shell"
        :class="{ 'editor-shell--resizing': resizingSidebar !== null }"
        :style="editorShellStyle"
      >
        <aside class="editor-sidebar editor-sidebar--left" aria-label="Editor sidebar">
          <section class="editor-section">
            <h2>Pages</h2>
            <div class="editor-page-switch" aria-label="Card sides">
              <button
                v-for="page in editorPages"
                :key="page.id"
                type="button"
                :class="{ active: selectedPage === page.id }"
                :aria-pressed="selectedPage === page.id"
                @click="setSelectedPage(page.id)"
              >
                {{ page.label }}
              </button>
            </div>
          </section>
          <section class="editor-section">
            <h2>Layers</h2>
            <div class="editor-layer-tools" aria-label="Layer edit actions">
              <button
                type="button"
                :disabled="!canUndoLayerChange"
                aria-label="Undo layer change"
                title="Undo"
                @click="undoLayerChange"
              >
                <RotateCcw :size="16" :stroke-width="1.8" aria-hidden="true" />
                <span>Undo</span>
              </button>
              <button
                type="button"
                :disabled="!canRedoLayerChange"
                aria-label="Redo layer change"
                title="Redo"
                @click="redoLayerChange"
              >
                <RotateCw :size="16" :stroke-width="1.8" aria-hidden="true" />
                <span>Redo</span>
              </button>
              <button
                type="button"
                :disabled="!canDuplicateSelectedLayers"
                aria-label="Duplicate selected layer"
                title="Duplicate selected layer"
                @click="duplicateSelectedLayers"
              >
                <Copy :size="16" :stroke-width="1.8" aria-hidden="true" />
                <span>Copy</span>
              </button>
              <button
                type="button"
                :disabled="!canDeleteSelectedLayers"
                aria-label="Delete selected layer"
                title="Delete selected layer"
                @click="deleteSelectedLayers"
              >
                <Trash2 :size="16" :stroke-width="1.8" aria-hidden="true" />
                <span>Delete</span>
              </button>
            </div>
            <ol class="editor-layer-list">
              <li
                v-for="item in layerPanelItems"
                :key="item.index"
                draggable="true"
                @dragstart="startLayerPanelDrag(item.index, $event)"
                @dragend="draggingLayerIndex = null"
                @dragover.prevent
                @drop="dropLayerOnPanel(item.index, $event)"
              >
                <div
                  class="editor-layer-row"
                  :class="{ active: selectedLayerIndexes.includes(item.index) }"
                  @contextmenu="openLayerContextMenu(item.index, $event)"
                >
                  <button
                    type="button"
                    class="editor-layer-button"
                    :aria-pressed="selectedLayerIndexes.includes(item.index)"
                    :aria-label="`Select layer ${item.number} ${layerDisplayName(item.layer)}`"
                    @click="selectLayer(item.index, $event)"
                  >
                    <span class="editor-layer-number">{{ item.number }}</span>
                    <strong>{{ layerDisplayName(item.layer) }}</strong>
                  </button>
                  <div class="editor-layer-actions" :aria-label="`Layer ${item.number} actions`">
                    <button
                      type="button"
                      class="editor-layer-toggle"
                      :aria-pressed="!layerIsVisible(item.layer)"
                      :aria-label="`${layerIsVisible(item.layer) ? 'Hide' : 'Show'} layer ${item.number}`"
                      :title="layerIsVisible(item.layer) ? 'Hide layer' : 'Show layer'"
                      @click="toggleLayerVisibility(item.index)"
                    >
                      <component
                        :is="layerIsVisible(item.layer) ? Eye : EyeOff"
                        :size="16"
                        :stroke-width="1.8"
                        aria-hidden="true"
                      />
                    </button>
                    <button
                      type="button"
                      class="editor-layer-toggle editor-layer-toggle--lock"
                      :aria-pressed="layerIsLocked(item.layer)"
                      :aria-label="`${layerIsLocked(item.layer) ? 'Unlock' : 'Lock'} layer ${item.number}`"
                      :title="layerIsLocked(item.layer) ? 'Unlock layer' : 'Lock layer'"
                      @click="toggleLayerLock(item.index)"
                    >
                      <component
                        :is="layerIsLocked(item.layer) ? Lock : Unlock"
                        :size="16"
                        :stroke-width="1.8"
                        aria-hidden="true"
                      />
                    </button>
                  </div>
                </div>
              </li>
            </ol>
            <div
              v-if="layerContextMenu"
              ref="layerContextMenuElement"
              class="editor-layer-context-menu"
              role="menu"
              tabindex="-1"
              :style="layerContextMenuStyle"
              @click.stop
              @keydown.esc.prevent.stop="closeLayerContextMenu"
              @keyup.esc.prevent.stop="closeLayerContextMenu"
              @contextmenu.prevent
            >
              <button type="button" role="menuitem" @click="duplicateSelectedLayers(); closeLayerContextMenu()">
                Copy
              </button>
              <button type="button" role="menuitem" @click="deleteSelectedLayers(); closeLayerContextMenu()">
                Delete
              </button>
              <button type="button" role="menuitem" @click="renameLayer(layerContextMenu.index)">
                Rename
              </button>
              <button
                type="button"
                role="menuitem"
                :disabled="!canMoveLayerInPanel(layerContextMenu.index, -1)"
                @click="moveLayerInPanel(layerContextMenu.index, -1); closeLayerContextMenu()"
              >
                Move up
              </button>
              <button
                type="button"
                role="menuitem"
                :disabled="!canMoveLayerInPanel(layerContextMenu.index, 1)"
                @click="moveLayerInPanel(layerContextMenu.index, 1); closeLayerContextMenu()"
              >
                Move down
              </button>
              <button type="button" role="menuitem" @click="toggleLayerVisibility(layerContextMenu.index); closeLayerContextMenu()">
                {{ layerIsVisible(editableLayers[layerContextMenu.index]) ? "Hide" : "Show" }}
              </button>
              <button type="button" role="menuitem" @click="toggleLayerLock(layerContextMenu.index); closeLayerContextMenu()">
                {{ layerIsLocked(editableLayers[layerContextMenu.index]) ? "Unlock" : "Lock" }}
              </button>
            </div>
            <p v-if="displayedCanvasLayers.length === 0" class="muted">No layers</p>
          </section>
          <section
            class="editor-section"
            :class="{ 'editor-section--drop-active': assetDropActive }"
            aria-label="Assets panel"
            @dragover="onAssetDragOver"
            @dragleave="onAssetDragLeave"
            @drop="onAssetDrop"
          >
            <h2>Assets</h2>
            <label class="editor-upload-control">
              <span>Images (multi)</span>
              <input
                ref="assetFileInput"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                multiple
                aria-label="Add image assets"
                @change="previewAssetImage"
              >
            </label>
            <p class="muted editor-drop-hint">
              Drop images here or on canvas. Zoom: pinch / Ctrl+scroll - Pan: two-finger scroll. Shortcuts: Ctrl+C/V/X/Z, V/T/R/E/I.
            </p>
            <p v-if="assetPreviewError" class="error-text editor-upload-error" role="alert">
              {{ assetPreviewError }}
            </p>
            <div v-if="assetLibrary.length" class="editor-asset-library" aria-label="Image library">
              <article
                v-for="asset in assetLibrary"
                :key="asset.id"
                class="editor-asset-card"
                :class="{ active: selectedAssetId === asset.id }"
              >
                <button
                  type="button"
                  class="editor-asset-card-main"
                  :aria-pressed="selectedAssetId === asset.id"
                  :aria-label="`Select image ${asset.name}`"
                  @click="selectedAssetId = asset.id; activeTool = 'image'"
                >
                  <img :src="asset.url" :alt="asset.name">
                  <span>
                    <strong>{{ asset.name }}</strong>
                    <small>{{ asset.sizeLabel }} - {{ asset.pixelWidth }}x{{ asset.pixelHeight }}</small>
                  </span>
                </button>
                <div class="editor-asset-card-actions">
                  <button
                    type="button"
                    :disabled="assetUploading || backgroundRemovingAssetId !== null || asset.pixelWidth <= 0"
                    @click="() => addAssetToCanvas(asset)"
                  >
                    Add
                  </button>
                  <button
                    v-if="!asset.uploaded"
                    type="button"
                    :disabled="assetUploading || backgroundRemovingAssetId !== null"
                    @click="() => removeBackgroundFromAsset(asset)"
                  >
                    {{ backgroundRemovingAssetId === asset.id ? "Removing..." : "Remove background" }}
                  </button>
                  <button type="button" @click="removeAssetFromLibrary(asset.id)">
                    Remove
                  </button>
                </div>
              </article>
            </div>
            <p v-else-if="!assetPreviewError" class="muted">No images yet - upload or drop several files.</p>
            <div class="editor-icon-library" aria-label="Emoji library">
              <label>
                <span>Emoji search ({{ filteredIconAssets.length }})</span>
                <input
                  v-model="iconSearchQuery"
                  type="search"
                  placeholder="Search emoji"
                  aria-label="Search emoji"
                >
              </label>
              <p class="muted editor-drop-hint">
                Select an emoji, then click the card. Or drag it directly onto the card.
              </p>
              <div v-if="filteredIconAssets.length" class="editor-icon-grid" aria-label="Emoji results">
                <button
                  v-for="icon in filteredIconAssets"
                  :key="icon.id"
                  type="button"
                  draggable="true"
                  :class="{ active: selectedIconId === icon.id }"
                  :aria-pressed="selectedIconId === icon.id"
                  :aria-label="`Select ${icon.label} emoji`"
                  :title="`${icon.label} - drag to card or click to place`"
                  @click="selectIconForPlacement(icon)"
                  @dragstart="startIconDrag(icon, $event)"
                  @dragend="endIconDrag"
                >
                  <span class="editor-icon-glyph" aria-hidden="true">{{ icon.glyph }}</span>
                  <span>{{ icon.label }}</span>
                </button>
              </div>
              <p v-else class="editor-empty-state" role="status">
                No emoji found. Try shop, star, phone, or clear search.
              </p>
            </div>

            <div class="editor-icon-library editor-icons8-library" aria-label="Icons8 library">
              <div class="editor-icons8-search-row">
                <label>
                  <span>Icons8 search</span>
                  <input
                    v-model="icons8SearchQuery"
                    type="search"
                    placeholder="Search Icons8 icons"
                    aria-label="Search Icons8 icons"
                    @keydown.enter.prevent="runIcons8Search"
                  >
                </label>
                <label>
                  <span>Language</span>
                  <select v-model="icons8SearchLanguage" aria-label="Icons8 search language">
                    <option
                      v-for="language in icons8LanguageOptions"
                      :key="language.id"
                      :value="language.id"
                    >
                      {{ language.label }}
                    </option>
                  </select>
                </label>
              </div>
              <button
                type="button"
                class="editor-icons8-search-button"
                :disabled="icons8Loading"
                @click="runIcons8Search"
              >
                {{ icons8Loading ? "Searching..." : "Search Icons8" }}
              </button>
              <p class="muted editor-drop-hint">
                Add or drag Icons8 results into the card. Free use requires visible attribution.
                <a href="https://icons8.com" target="_blank" rel="noreferrer">Icons by Icons8</a>
              </p>
              <p v-if="icons8Error" class="error-text editor-upload-error" role="alert">
                {{ icons8Error }}
              </p>
              <p v-else-if="icons8Message" class="muted editor-drop-hint" role="status">
                {{ icons8Message }}
              </p>
              <div v-if="icons8Results.length" class="editor-icons8-grid" aria-label="Icons8 results">
                <article
                  v-for="icon in icons8Results"
                  :key="icon.id"
                  class="editor-icons8-card"
                  draggable="true"
                  @dragstart="startIcons8Drag(icon, $event)"
                  @dragend="endIcons8Drag"
                >
                  <img :src="icon.previewUrl" :alt="icon.name" loading="lazy" referrerpolicy="no-referrer">
                  <span>{{ icon.name }}</span>
                  <button type="button" @click="addIcons8IconToCanvas(icon)">
                    Add
                  </button>
                </article>
              </div>
              <p v-else-if="!icons8Loading && !icons8Error && !icons8Message" class="editor-empty-state" role="status">
                Search Icons8 by keyword in English, Russian, Vietnamese, or another language.
              </p>
            </div>
          </section>
        </aside>

        <div
          class="editor-resizer editor-resizer--left"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize left sidebar"
          :aria-valuenow="leftSidebarWidth"
          :aria-valuemin="LEFT_SIDEBAR_MIN"
          :aria-valuemax="LEFT_SIDEBAR_MAX"
          tabindex="0"
          title="Drag to resize left panel"
          @pointerdown="startSidebarResize('left', $event)"
          @keydown="handleSidebarResizeKey('left', $event)"
        />

        <section
          class="editor-workspace"
          aria-label="Card canvas workspace"
          @pointerdown="startCanvasPan"
          @wheel.prevent="handleEditorWheel"
          @dragover.prevent="onAssetDragOver"
          @dragleave="onAssetDragLeave"
          @drop="onWorkspaceDrop"
        >
          <div class="editor-toolbar" role="toolbar" aria-label="Canvas tools">
            <button
              v-for="tool in editorTools"
              :key="tool.id"
              type="button"
              :class="{ active: activeTool === tool.id }"
              :aria-pressed="activeTool === tool.id"
              :aria-label="`${tool.label} tool`"
              :title="tool.label"
              @click="selectTool(tool.id)"
            >
              <component :is="tool.icon" :size="18" :stroke-width="1.8" aria-hidden="true" />
              <span>{{ tool.label }}</span>
            </button>
          </div>
          <div class="editor-zoom-controls" aria-label="Canvas zoom and pan">
            <button type="button" aria-label="Zoom out canvas" title="Zoom out" @click="updateEditorZoom(-10)">
              <ZoomOut :size="16" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <button
              type="button"
              aria-label="Reset canvas view"
              title="Reset view - Pinch or Ctrl+scroll to zoom - Two-finger scroll to pan"
              @click="resetEditorView"
            >
              {{ editorZoom }}%
            </button>
            <button type="button" aria-label="Zoom in canvas" title="Zoom in" @click="updateEditorZoom(10)">
              <ZoomIn :size="16" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <button type="button" aria-label="Pan canvas left" title="Pan left" @click="updateEditorPan(-40, 0)">
              <ArrowLeft :size="16" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <button type="button" aria-label="Pan canvas right" title="Pan right" @click="updateEditorPan(40, 0)">
              <ArrowRight :size="16" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <button type="button" aria-label="Pan canvas up" title="Pan up" @click="updateEditorPan(0, -40)">
              <ArrowUp :size="16" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <button type="button" aria-label="Pan canvas down" title="Pan down" @click="updateEditorPan(0, 40)">
              <ArrowDown :size="16" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <span title="Drag empty workspace / two-finger pan - Pinch or Ctrl+scroll zoom">
              <Move :size="16" :stroke-width="1.8" aria-hidden="true" />
            </span>
          </div>
          <div class="editor-guide-toggle">
            <label>
              <input v-model="showSafeZoneGuides" type="checkbox" />
              <span>Safe zone / bleed guides</span>
            </label>
          </div>
          <div
            class="editor-canvas-viewport"
            :style="editorCanvasTransform"
            aria-label="Pan canvas viewport"
            @pointerdown="startCanvasPan"
          >
            <CanvasPreview
              :layers="displayedCanvasLayers"
              :width-mm="design.widthMm"
              :height-mm="design.heightMm"
              label="Draft canvas preview"
              :empty-label="selectedPageLabel"
              :selected-layer-index="canvasSelectedLayerIndex"
              :selected-layer-indexes="canvasSelectedLayerIndexes"
              :resizable-layer-index="canvasResizableLayerIndex"
              :rotatable-layer-index="canvasRotatableLayerIndex"
              :show-safe-zone-guides="showSafeZoneGuides"
              interactive
              @canvas-pointerdown="handleCanvasPointerDown"
              @layer-pointerdown="handleCanvasLayerPointerDown"
              @layer-resize-pointerdown="handleCanvasLayerResizePointerDown"
              @layer-rotate-pointerdown="handleCanvasLayerRotatePointerDown"
              @layer-select="handleCanvasLayerSelect"
            />
          </div>
          <div class="editor-color-bar" aria-label="Quick colors">
            <div class="editor-color-targets" aria-label="Color target">
              <button
                v-for="target in colorTargets"
                :key="target.id"
                type="button"
                :class="{ active: activeColorTarget === target.id }"
                :aria-pressed="activeColorTarget === target.id"
                @click="activeColorTarget = target.id"
              >
                {{ target.label }}
              </button>
            </div>
            <div class="editor-color-swatches" aria-label="Recent colors">
              <button
                v-for="color in recentColors"
                :key="color"
                type="button"
                class="editor-color-swatch"
                :class="{ active: activeColorValue.toUpperCase() === color }"
                :style="{ '--swatch-color': color }"
                :aria-label="`Apply ${color} to ${activeColorTargetLabel}`"
                :title="`${activeColorTargetLabel}: ${color}`"
                :disabled="!selectedLayer || layerIsLocked(selectedLayer)"
                @click="applyQuickColor(color)"
              />
            </div>
            <span class="editor-color-value">{{ selectedLayerLabel }}</span>
          </div>
        </section>

        <div
          class="editor-resizer editor-resizer--right"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize right sidebar"
          :aria-valuenow="rightSidebarWidth"
          :aria-valuemin="RIGHT_SIDEBAR_MIN"
          :aria-valuemax="RIGHT_SIDEBAR_MAX"
          tabindex="0"
          title="Drag to resize right panel"
          @pointerdown="startSidebarResize('right', $event)"
          @keydown="handleSidebarResizeKey('right', $event)"
        />

        <aside class="editor-sidebar editor-sidebar--right" aria-label="Properties panel">
          <section class="editor-section">
            <h2>Properties</h2>
            <dl class="editor-properties">
              <div>
                <dt>Size</dt>
                <dd>{{ design.widthMm }} x {{ design.heightMm }} mm</dd>
              </div>
              <div>
                <dt>Side</dt>
                <dd>{{ selectedPageLabel }}</dd>
              </div>
              <div>
                <dt>Tool</dt>
                <dd>{{ activeToolLabel }}</dd>
              </div>
              <div>
                <dt>Color</dt>
                <dd>{{ activeColorTargetLabel }}</dd>
              </div>
              <div>
                <dt>Layers</dt>
                <dd>{{ canvasLayerCount }}</dd>
              </div>
              <div>
                <dt>Selected</dt>
                <dd>{{ selectedLayerIndexes.length }}</dd>
              </div>
              <div>
                <dt>Locked</dt>
                <dd>{{ selectedLayer && layerIsLocked(selectedLayer) ? "Yes" : "No" }}</dd>
              </div>
            </dl>
            <div
              v-if="selectedLayerIndexes.length === 1 && selectedLayer"
              class="editor-geometry"
              aria-label="Selected layer geometry"
            >
              <label title="Horizontal position from card center (0 = middle). Unit = 1% of card width.">
                <span>X</span>
                <input
                  type="number"
                  min="-50"
                  max="50"
                  step="0.1"
                  :value="selectedLayerCenterCoord('x')"
                  :disabled="!selectedLayerCanEditGeometry"
                  aria-label="Layer X from center"
                  @input="updateSelectedLayerNumber('x', $event)"
                />
              </label>
              <label title="Vertical position from card center (0 = middle). Unit = 1% of card height.">
                <span>Y</span>
                <input
                  type="number"
                  min="-50"
                  max="50"
                  step="0.1"
                  :value="selectedLayerCenterCoord('y')"
                  :disabled="!selectedLayerCanEditGeometry"
                  aria-label="Layer Y from center"
                  @input="updateSelectedLayerNumber('y', $event)"
                />
              </label>
              <label title="Width as one-decimal percent of card width">
                <span>W %</span>
                <input
                  type="number"
                  min="3"
                  max="100"
                  step="0.1"
                  :value="selectedLayerSizeInt('width')"
                  :disabled="!selectedLayerCanEditGeometry"
                  aria-label="Layer width percent"
                  @input="updateSelectedLayerNumber('width', $event)"
                />
              </label>
              <label title="Height as one-decimal percent of card height">
                <span>H %</span>
                <input
                  type="number"
                  min="3"
                  max="100"
                  step="0.1"
                  :value="selectedLayerSizeInt('height')"
                  :disabled="!selectedLayerCanEditGeometry"
                  aria-label="Layer height percent"
                  @input="updateSelectedLayerNumber('height', $event)"
                />
              </label>
              <label title="Rotation in one-decimal degrees">
                <span>R deg</span>
                <input
                  type="number"
                  min="0"
                  max="359"
                  step="0.1"
                  :value="selectedLayerRotationInt()"
                  :disabled="!selectedLayerCanEditGeometry"
                  aria-label="Layer rotation degrees"
                  @input="updateSelectedLayerNumber('rotation', $event)"
                />
              </label>
            </div>
            <p v-else class="muted">Select one layer to edit geometry.</p>
            <div
              v-if="selectedLayerIsImage"
              class="editor-image-tools"
              aria-label="Selected image tools"
            >
              <p class="muted">Portrait / image layer</p>
              <button
                type="button"
                class="secondary-action"
                :disabled="!selectedLayerCanEditGeometry || randomPhotoLoading"
                @click="applyRandomPhotoToSelectedImage"
              >
                {{ randomPhotoLoading ? "Loading..." : "Random portrait" }}
              </button>
              <p v-if="randomPhotoError" class="error-text" role="alert">{{ randomPhotoError }}</p>
              <p class="muted editor-drop-hint">
                Uses free demo portraits (randomuser.me). Re-click for another face.
              </p>
            </div>
            <div
              v-if="selectedLayerIndexes.length === 1 && selectedLayer && selectedLayer.type === 'text'"
              class="editor-text-style"
              aria-label="Selected text style"
            >
              <label>
                <span>Font</span>
                <input
                  list="editor-font-options"
                  :value="selectedLayerString('fontFamily', 'inherit')"
                  :disabled="!selectedLayerCanEditText"
                  aria-label="Layer font family"
                  @input="updateSelectedTextField('fontFamily', $event)"
                />
                <datalist id="editor-font-options">
                  <option v-for="fontFamily in fontFamilyOptions" :key="fontFamily" :value="fontFamily" />
                </datalist>
              </label>
              <label>
                <span>Size</span>
                <input
                  type="number"
                  min="6"
                  max="120"
                  step="1"
                  :value="selectedLayerNumber('fontSize', 18)"
                  :disabled="!selectedLayerCanEditText"
                  aria-label="Layer font size"
                  @input="updateSelectedTextField('fontSize', $event)"
                />
              </label>
              <label>
                <span>Weight</span>
                <select
                  :value="selectedLayerNumber('fontWeight', 700)"
                  :disabled="!selectedLayerCanEditText"
                  aria-label="Layer font weight"
                  @change="updateSelectedTextField('fontWeight', $event)"
                >
                  <option
                    v-for="fontWeight in fontWeightOptions"
                    :key="fontWeight"
                    :value="fontWeight"
                  >
                    {{ fontWeight }}
                  </option>
                </select>
              </label>
            </div>
            <p v-else class="muted">Select a text layer to edit type.</p>
            <div
              v-if="selectedLayerIndexes.length === 1 && selectedLayer"
              class="editor-appearance"
              aria-label="Selected layer appearance"
            >
              <label>
                <span>Fill</span>
                <input
                  type="color"
                  :value="selectedLayerColor('fill')"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer fill color"
                  @input="updateSelectedAppearanceColor('fill', $event)"
                />
              </label>
              <label>
                <span>Stroke</span>
                <input
                  type="color"
                  :value="selectedLayerColor('stroke')"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer stroke color"
                  @input="updateSelectedAppearanceColor('stroke', $event)"
                />
              </label>
              <label>
                <span>Opacity</span>
                <input
                  type="number"
                  min="0"
                  max="1"
                  step="0.01"
                  :value="selectedLayerNumber('opacity', 1)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer opacity"
                  @input="updateSelectedAppearanceNumber('opacity', $event)"
                />
              </label>
              <label>
                <span>Stroke W</span>
                <input
                  type="number"
                  min="0"
                  max="20"
                  step="0.5"
                  :value="selectedLayerNumber('strokeWidth', 1)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer stroke width"
                  @input="updateSelectedAppearanceNumber('strokeWidth', $event)"
                />
              </label>
            </div>
            <p v-else class="muted">Select one layer to edit appearance.</p>
            <div
              v-if="selectedLayerIndexes.length === 1 && selectedLayer"
              class="editor-effects"
              aria-label="Selected layer effects"
            >
              <label>
                <span>Shadow X</span>
                <input
                  type="number"
                  min="-50"
                  max="50"
                  step="1"
                  :value="selectedLayerNumber('shadowX', 0)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer shadow X"
                  @input="updateSelectedEffectNumber('shadowX', $event)"
                />
              </label>
              <label>
                <span>Shadow Y</span>
                <input
                  type="number"
                  min="-50"
                  max="50"
                  step="1"
                  :value="selectedLayerNumber('shadowY', 4)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer shadow Y"
                  @input="updateSelectedEffectNumber('shadowY', $event)"
                />
              </label>
              <label>
                <span>Shadow Blur</span>
                <input
                  type="number"
                  min="0"
                  max="50"
                  step="1"
                  :value="selectedLayerNumber('shadowBlur', 0)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer shadow blur"
                  @input="updateSelectedEffectNumber('shadowBlur', $event)"
                />
              </label>
              <label>
                <span>Spread</span>
                <input
                  type="number"
                  min="0"
                  max="50"
                  step="1"
                  :value="selectedLayerNumber('shadowSpread', 0)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer shadow spread"
                  @input="updateSelectedEffectNumber('shadowSpread', $event)"
                />
              </label>
              <label>
                <span>Shadow Color</span>
                <input
                  type="color"
                  :value="selectedLayerHexColor('shadowColor', '#000000')"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer shadow color"
                  @input="updateSelectedEffectColor"
                />
              </label>
              <label>
                <span>Shadow %</span>
                <input
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  :value="selectedLayerPercent('shadowOpacity', 50)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer shadow opacity"
                  @input="updateSelectedShadowOpacity"
                />
              </label>
              <label>
                <span>Blur</span>
                <input
                  type="number"
                  min="0"
                  max="50"
                  step="0.5"
                  :value="selectedLayerNumber('blur', 0)"
                  :disabled="!selectedLayerCanEditAppearance"
                  aria-label="Layer blur"
                  @input="updateSelectedEffectNumber('blur', $event)"
                />
              </label>
            </div>
            <p v-else class="muted">Select one layer to edit effects.</p>
          </section>

          <section class="editor-ai-rewrite" aria-label="AI text rewrite">
            <div class="editor-ai-heading">
              <Sparkles :size="16" :stroke-width="1.8" aria-hidden="true" />
              <h2>AI rewrite</h2>
            </div>
            <textarea
              v-model="aiRewritePrompt"
              rows="3"
              maxlength="1000"
              placeholder="Describe the text change"
              :disabled="!selectedLayerCanEditText || aiRewriteLoading"
              aria-label="AI rewrite instruction"
            />
            <div class="editor-ai-strengths" role="group" aria-label="AI edit strength">
              <button
                v-for="strength in aiRewriteStrengths"
                :key="strength.id"
                type="button"
                :class="{ active: aiRewriteStrength === strength.id }"
                :aria-pressed="aiRewriteStrength === strength.id"
                :disabled="aiRewriteLoading"
                @click="aiRewriteStrength = strength.id"
              >
                {{ strength.label }}
              </button>
            </div>
            <button
              class="editor-ai-preview-button"
              type="button"
              :disabled="!canRequestAiRewrite || aiRewriteLoading"
              @click="requestAiTextRewrite"
            >
              <Sparkles :size="15" :stroke-width="1.8" aria-hidden="true" />
              {{ aiRewriteLoading ? "Generating..." : "Preview rewrite" }}
            </button>
            <p v-if="aiRewriteError" class="error-text" role="alert">{{ aiRewriteError }}</p>

            <div v-if="aiRewritePreview" class="editor-ai-preview" role="status">
              <p class="editor-ai-summary">{{ aiRewritePreview.response.summary }}</p>
              <div class="editor-ai-diff">
                <div>
                  <span>Current</span>
                  <p>{{ aiRewritePreview.originalText }}</p>
                </div>
                <div>
                  <span>Proposed</span>
                  <p>{{ aiRewritePreview.replacementText }}</p>
                </div>
              </div>
              <div class="editor-ai-actions">
                <button type="button" @click="rejectAiTextRewrite">
                  <X :size="15" :stroke-width="1.8" aria-hidden="true" />
                  Reject
                </button>
                <button
                  class="editor-ai-apply"
                  type="button"
                  @click="applyAiTextRewrite"
                >
                  <Check :size="15" :stroke-width="1.8" aria-hidden="true" />
                  Apply
                </button>
              </div>
            </div>
          </section>
          <div v-if="editableTextLayerIndex >= 0" class="editor-panel">
            <label for="editor-text-layer">Text content (selected)</label>
            <textarea
              id="editor-text-layer"
              v-model="editableTextLayerText"
              maxlength="120"
              rows="5"
              :disabled="editableTextLayerLocked"
            />
          </div>
          <p v-else class="muted">Select a text layer to edit content.</p>

          <p v-if="saveError" class="error-text" role="alert">{{ saveError }}</p>
          <button
            class="danger-action"
            type="button"
            :disabled="saving || deleting"
            @click="deleteDraft"
          >
            {{ deleting ? "Deleting..." : confirmingDelete ? "Confirm delete" : "Delete draft" }}
          </button>
        </aside>
      </div>
    </template>

    <article v-else-if="design" class="detail-shell">
      <div class="detail-preview">
        <CanvasPreview
          :layers="canvasLayers"
          :width-mm="design.widthMm"
          :height-mm="design.heightMm"
          label="Draft canvas preview"
          empty-label="Draft"
        />
      </div>

      <div class="detail-content">
        <p class="eyebrow">Draft #{{ design.id }}</p>
        <h1>{{ design.name }}</h1>
        <p class="summary">
          {{ design.widthMm }} x {{ design.heightMm }} mm canvas with
          {{ canvasLayerCount }} {{ canvasLayerCount === 1 ? "layer" : "layers" }}.
        </p>

        <div class="detail-actions">
          <RouterLink
            class="secondary-action"
            :to="{ name: 'checkout', params: { designId: design.id } }"
          >
            Checkout
          </RouterLink>
          <button class="primary-action" type="button" :disabled="saving" @click="saveDraft">
            {{ saving ? "Saving..." : "Save draft" }}
          </button>
          <button
            class="danger-action"
            type="button"
            :disabled="saving || deleting"
            @click="deleteDraft"
          >
            {{ deleting ? "Deleting..." : confirmingDelete ? "Confirm delete" : "Delete draft" }}
          </button>
          <span v-if="saveMessage" class="save-status" role="status">{{ saveMessage }}</span>
          <span v-else-if="!saveError" class="muted">Saved draft</span>
        </div>
        <p v-if="saveError" class="error-text" role="alert">{{ saveError }}</p>

        <div v-if="editableTextLayerIndex >= 0" class="editor-panel">
          <label for="draft-text-layer">Text layer</label>
          <textarea
            id="draft-text-layer"
            v-model="editableTextLayerText"
            maxlength="120"
            rows="4"
          />
        </div>
        <p v-else class="muted">No text layer.</p>
      </div>
    </article>
  </section>
</template>
