<script setup lang="ts">
import {
  AlignCenterHorizontal,
  AlignEndHorizontal,
  AlignStartHorizontal,
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Bell,
  Bold,
  ChevronDown,
  Circle,
  Copy,
  Download,
  Eye,
  EyeOff,
  GripVertical,
  ImageIcon,
  Layers3,
  Link2,
  LockKeyhole,
  Menu,
  Minus,
  Monitor,
  Move,
  MousePointer2,
  Palette,
  PanelLeft,
  PanelRight,
  Play,
  Plus,
  Redo2,
  RotateCw,
  Save,
  Shapes,
  Share2,
  Square,
  Trash2,
  Type,
  Undo2,
  Upload,
} from "@lucide/vue";
import { computed, onMounted, ref, watch } from "vue";
import type { Component } from "vue";
import { RouterLink, useRoute } from "vue-router";
import EditorCanvasV2 from "../editor-v2/EditorCanvasV2.vue";
import { apiBaseUrl, removeBackgroundImageAsset } from "../api";
import {
  createEditorDocumentV2,
  readEditorDocumentV2,
  type EditorLayerV2,
  type EditorLayerType,
  type EditorSide,
} from "../editor-v2/document";

type EditorTool = "select" | "text" | "rect" | "ellipse" | "image";
type EditorLayerAction = "duplicate" | "toggle-lock" | "bring-forward" | "send-backward" | "delete";
type SidebarPanel = "elements" | "text" | "uploads" | "brand" | "layers";
type TextPreset = "title" | "subtitle" | "body";
type ShapePreset = "rectangle" | "rounded" | "ellipse";
type GeometryField = "x" | "y" | "width" | "height";
type NumericLayerField = "fontSize" | "rotation" | "strokeWidth" | "cornerRadius";

type IconItem = {
  id: string;
  label: string;
  icon: Component;
};

const route = useRoute();
const documentId = computed(() => String(route.params.designId ?? "new"));
const storageKey = computed(() => "vizi.editor.v2." + documentId.value);
const document = ref(createEditorDocumentV2(documentId.value));
const activeSide = ref<EditorSide>("front");
const activePanel = ref<SidebarPanel>("elements");
const activeTool = ref<EditorTool>("select");
const selectedLayerId = ref<string | null>("front-title");
const zoom = ref(100);
const saveState = ref<"idle" | "saved" | "dirty">("idle");
const imageInput = ref<HTMLInputElement | null>(null);
const imageTargetLayerId = ref<string | null>(null);
const draggedLayerId = ref<string | null>(null);
const imageFileByLayer = new Map<string, File>();
const backgroundRemovingLayerId = ref<string | null>(null);
const backgroundRemovalError = ref("");

const sides: EditorSide[] = ["front", "back"];
const palettes = ["#15161b", "#ffffff", "#b4367d", "#f4c46d", "#0d766d", "#5b83d4"];
const fontOptions = [
  { label: "Aptos", value: "Aptos, Segoe UI, sans-serif" },
  { label: "Arial", value: "Arial, sans-serif" },
  { label: "Georgia", value: "Georgia, serif" },
];
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

const activePage = computed(() => document.value.pages[activeSide.value]);
const selectedLayer = computed<EditorLayerV2 | null>(() => (
  activePage.value.layers.find((layer) => layer.id === selectedLayerId.value) ?? null
));
const imageLayers = computed(() => activePage.value.layers.filter((layer) => layer.type === "image" && safeImageSource(layer.src)));
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
  brand: "Brand",
  layers: "Layers",
}[activePanel.value]));

const sidebarItems: Array<{ id: SidebarPanel; label: string; icon: Component }> = [
  { id: "elements", label: "Elements", icon: Shapes },
  { id: "text", label: "Text", icon: Type },
  { id: "uploads", label: "Uploads", icon: Upload },
  { id: "brand", label: "Brand", icon: Palette },
  { id: "layers", label: "Layers", icon: Layers3 },
];

const shapeItems: Array<IconItem & { preset: ShapePreset }> = [
  { id: "rectangle", label: "Rectangle", icon: Square, preset: "rectangle" },
  { id: "rounded", label: "Rounded", icon: Square, preset: "rounded" },
  { id: "ellipse", label: "Ellipse", icon: Circle, preset: "ellipse" },
];

const canvasTools: Array<{ id: EditorTool; label: string; icon: Component }> = [
  { id: "select", label: "Select", icon: MousePointer2 },
  { id: "text", label: "Text", icon: Type },
  { id: "rect", label: "Rectangle", icon: Square },
  { id: "ellipse", label: "Ellipse", icon: Circle },
  { id: "image", label: "Image", icon: ImageIcon },
];

function markDirty(): void {
  saveState.value = "dirty";
}

function backendAssetUrl(url: string): string {
  return url.startsWith("/") ? apiBaseUrl + url : url;
}

function safeImageSource(value: string | undefined): string {
  if (!value) return "";
  if (/^https?:\/\//i.test(value) || value.startsWith("/")) return value;
  return /^data:image\/(png|jpeg|webp|gif);base64,/i.test(value) ? value : "";
}

function selectPage(side: EditorSide): void {
  activeSide.value = side;
  selectedLayerId.value = null;
  activeTool.value = "select";
}

function selectLayer(layerId: string | null): void {
  selectedLayerId.value = layerId;
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
    const duplicate: EditorLayerV2 = { ...layer };
    duplicate.id = layer.id + "-copy-" + String(Date.now());
    duplicate.name = layer.name + " copy";
    duplicate.x = Math.min(Math.max(0, 100 - duplicate.width), layer.x + 2);
    duplicate.y = Math.min(Math.max(0, 100 - duplicate.height), layer.y + 2);
    layers.splice(index + 1, 0, duplicate);
    selectedLayerId.value = duplicate.id;
  } else if (payload.action === "bring-forward" && index < layers.length - 1) {
    layers.splice(index + 1, 0, layers.splice(index, 1)[0]);
  } else if (payload.action === "send-backward" && index > 0) {
    layers.splice(index - 1, 0, layers.splice(index, 1)[0]);
  } else if (payload.action === "delete") {
    layers.splice(index, 1);
    selectedLayerId.value = null;
  } else {
    return;
  }

  markDirty();
}

function clampPercent(value: number, max: number): number {
  return Math.min(Math.max(0, value), Math.max(0, max));
}

function addLayer(type: EditorLayerType, source?: string, overrides: Partial<EditorLayerV2> = {}): EditorLayerV2 {
  const sequence = String(Date.now());
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

function addTextPreset(preset: TextPreset): void {
  const option = textPresetOptions.find((item) => item.id === preset);
  if (!option) return;
  addLayer("text", undefined, {
    name: option.id === "title" ? "Title" : option.id === "subtitle" ? "Subtitle" : "Body text",
    content: option.sample,
    fontSize: option.fontSize,
    fontWeight: option.fontWeight,
    width: option.id === "title" ? 62 : 50,
    height: option.id === "body" ? 12 : 14,
  });
  activePanel.value = "text";
}

function applyTextPreset(preset: TextPreset): void {
  const layer = selectedLayer.value;
  const option = textPresetOptions.find((item) => item.id === preset);
  if (!layer || layer.type !== "text" || !option) return;
  layer.fontSize = option.fontSize;
  layer.fontWeight = option.fontWeight;
  markDirty();
}

function addShapePreset(preset: ShapePreset): void {
  if (preset === "ellipse") {
    addLayer("ellipse");
  } else {
    addLayer("rect", undefined, {
      name: preset === "rounded" ? "Rounded rectangle" : "Rectangle",
      cornerRadius: preset === "rounded" ? 16 : 0,
    });
  }
  activePanel.value = "elements";
}

function openImagePicker(targetLayerId: string | null = null): void {
  imageTargetLayerId.value = targetLayerId;
  imageInput.value?.click();
}

function handleImageFile(event: Event): void {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  activeTool.value = "select";
  if (!file || !/^image\/(png|jpeg|webp|gif)$/.test(file.type) || file.size > 5 * 1024 * 1024) {
    imageTargetLayerId.value = null;
    return;
  }

  const reader = new FileReader();
  reader.addEventListener("load", () => {
    const source = typeof reader.result === "string" ? safeImageSource(reader.result) : "";
    const targetId = imageTargetLayerId.value;
    imageTargetLayerId.value = null;
    if (!source) return;
    const target = targetId ? activePage.value.layers.find((layer) => layer.id === targetId && layer.type === "image") : null;
    if (target) {
      target.src = source;
      target.originalSrc = source;
      target.processedSrc = undefined;
      target.processedAssetId = undefined;
      target.processedStorageKey = undefined;
      target.visible = true;
      imageFileByLayer.set(target.id, file);
      selectedLayerId.value = target.id;
      markDirty();
    } else {
      const layer = addLayer("image", source, { name: file.name });
      imageFileByLayer.set(layer.id, file);
      activePanel.value = "uploads";
    }
  });
  reader.readAsDataURL(file);
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

function updateZoom(delta: number): void {
  zoom.value = Math.min(160, Math.max(50, zoom.value + delta));
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
  markDirty();
}

function setSelectedFont(fontFamily: string): void {
  const layer = selectedLayer.value;
  if (layer?.type === "text") {
    layer.fontFamily = fontFamily;
    markDirty();
  } else {
    addLayer("text", undefined, { fontFamily });
    activePanel.value = "text";
  }
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

function applyBrandColor(color: string): void {
  if (selectedLayer.value && selectedLayer.value.type !== "image") {
    setSelectedFill(color);
  } else {
    setPageBackground(color);
  }
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

function saveLocally(): void {
  document.value.updatedAt = new Date().toISOString();
  localStorage.setItem(storageKey.value, JSON.stringify(document.value));
  saveState.value = "saved";
  window.setTimeout(() => {
    saveState.value = "idle";
  }, 1800);
}

watch(activeSide, () => {
  const current = activePage.value.layers.find((layer) => layer.id === selectedLayerId.value);
  if (!current) selectedLayerId.value = null;
});

onMounted(() => {
  const stored = readEditorDocumentV2(localStorage.getItem(storageKey.value));
  if (stored?.documentId === documentId.value) {
    document.value = stored;
    selectedLayerId.value = null;
  }
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
        <button type="button" aria-label="Undo" title="Undo" disabled>
          <Undo2 :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button type="button" aria-label="Redo" title="Redo" disabled>
          <Redo2 :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <span class="editor-v2__status-dot" aria-hidden="true" />
        <span class="editor-v2__status">{{ saveState === "saved" ? "Saved locally" : saveState === "dirty" ? "Unsaved changes" : "All changes saved" }}</span>
      </div>

      <div class="editor-v2__header-actions">
        <button class="editor-v2__header-icon" type="button" aria-label="Preview" title="Preview">
          <Play :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button class="editor-v2__header-icon" type="button" aria-label="Presentation view" title="Presentation view">
          <Monitor :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button class="editor-v2__header-icon" type="button" aria-label="Comments" title="Comments">
          <PanelRight :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button class="editor-v2__header-icon" type="button" aria-label="Notifications" title="Notifications">
          <Bell :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button class="editor-v2__header-icon" type="button" aria-label="More actions" title="More actions">
          <Menu :size="17" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button class="editor-v2__header-icon" type="button" aria-label="Download" title="Download">
          <Download :size="16" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button class="editor-v2__share" type="button">
          <Share2 :size="15" :stroke-width="1.8" aria-hidden="true" />
          <span>Share</span>
        </button>
        <button class="editor-v2__save" type="button" @click="saveLocally">
          <Save :size="15" :stroke-width="1.8" aria-hidden="true" />
          <span>Save</span>
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
      </nav>

      <aside class="editor-v2__elements-panel" :aria-label="panelTitle + ' panel'">
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
              <button type="button" aria-label="Add rectangle" title="Rectangle" @click="addShapePreset('rectangle')">
                <Square :size="23" :stroke-width="1.5" aria-hidden="true" />
              </button>
              <button type="button" aria-label="Add ellipse" title="Ellipse" @click="addShapePreset('ellipse')">
                <Circle :size="23" :stroke-width="1.5" aria-hidden="true" />
              </button>
              <button type="button" aria-label="Add text" title="Text" @click="addTextPreset('body')">
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
                @click="addShapePreset(shape.preset)"
              >
                <component :is="shape.icon" :size="29" :stroke-width="1.5" aria-hidden="true" />
                <span>{{ shape.label }}</span>
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
            <button class="editor-v2__primary-panel-action" type="button" @click="addTextPreset('body')">
              <Type :size="17" :stroke-width="1.8" aria-hidden="true" />
              <span>Add a text box</span>
            </button>
            <div class="editor-v2__text-presets">
              <button
                v-for="preset in textPresetOptions"
                :key="preset.id"
                type="button"
                :class="'preset-' + preset.id"
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
            <div class="editor-v2__font-list">
              <button
                v-for="font in fontOptions"
                :key="font.value"
                type="button"
                :style="{ fontFamily: font.value }"
                @click="setSelectedFont(font.value)"
              >
                <span>{{ font.label }}</span>
                <small>{{ font.label === 'Georgia' ? 'Serif' : 'Sans-serif' }}</small>
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
              <span>{{ imageLayers.length }}</span>
            </div>
            <div v-if="imageLayers.length" class="editor-v2__media-grid">
              <button
                v-for="layer in imageLayers"
                :key="layer.id"
                type="button"
                :class="{ active: selectedLayerId === layer.id }"
                :title="layer.name"
                @click="selectLayer(layer.id)"
              >
                <img :src="safeImageSource(layer.src)" :alt="layer.name">
              </button>
            </div>
            <div v-else class="editor-v2__panel-empty">No uploaded images on this side.</div>
          </section>
        </template>

        <template v-else-if="activePanel === 'brand'">
          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Brand colors</span>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__brand-swatches">
              <button
                v-for="color in palettes"
                :key="color"
                type="button"
                :style="{ background: color }"
                :aria-label="'Apply ' + color"
                :title="color"
                @click="applyBrandColor(color)"
              />
            </div>
          </section>
          <section class="editor-v2__brand-summary">
            <Palette :size="21" :stroke-width="1.6" aria-hidden="true" />
            <div>
              <strong>Vizi palette</strong>
              <span>Apply to the selected element or page.</span>
            </div>
          </section>
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
        <div class="editor-v2__ruler editor-v2__ruler--top" aria-hidden="true">
          <span>0</span><span>100</span><span>200</span><span>300</span><span>400</span><span>500</span><span>600</span><span>700</span>
        </div>
        <div class="editor-v2__ruler editor-v2__ruler--left" aria-hidden="true">
          <span>0</span><span>100</span><span>200</span><span>300</span><span>400</span>
        </div>

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
          <div class="editor-v2__canvas-frame">
            <EditorCanvasV2
              :document="document"
              :page="activePage"
              :zoom="zoom"
              :selected-layer-id="selectedLayerId"
              @select-layer="selectLayer"
              @move-layer="moveLayer"
              @resize-layer="resizeLayer"
              @rotate-layer="rotateLayer"
              @layer-action="handleLayerAction"
            />
          </div>
        </div>

        <div class="editor-v2__zoom" aria-label="Canvas zoom">
          <button type="button" aria-label="Zoom out" title="Zoom out" @click="updateZoom(-10)">
            <Minus :size="15" :stroke-width="2" aria-hidden="true" />
          </button>
          <button type="button" class="editor-v2__zoom-value" title="Reset zoom" @click="zoom = 100">
            {{ zoom }}%
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

      <aside class="editor-v2__inspector" :aria-label="inspectorTitle + ' inspector'">
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
            <div class="editor-v2__selected-name">{{ selectedLayer.name }}</div>
          </section>

          <section v-if="selectedLayer.type !== 'image'" class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>Fill</strong>
              <ChevronDown :size="14" :stroke-width="1.8" aria-hidden="true" />
            </div>
            <div class="editor-v2__fill-control">
              <input :value="selectedLayer.fill ?? '#ffffff'" type="color" aria-label="Fill color" @input="updateSelectedFill">
              <span>{{ (selectedLayer.fill ?? "#ffffff").toUpperCase() }}</span>
            </div>
            <div class="editor-v2__swatches" aria-label="Color palette">
              <button
                v-for="color in palettes"
                :key="color"
                type="button"
                :style="{ background: color }"
                :aria-label="'Use ' + color"
                :title="color"
                @click="setSelectedFill(color)"
              />
            </div>
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
                v-for="color in palettes"
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
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.editor-v2__header-center {
  gap: 4px;
  color: var(--editor-muted);
  font-size: 11px;
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
.editor-v2__header-icon:hover {
  background: var(--editor-soft);
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
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4baf86;
}

.editor-v2__share,
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

.editor-v2__save {
  border-color: var(--editor-accent);
  background: var(--editor-accent);
  color: #ffffff;
}

.editor-v2__share:hover {
  background: var(--editor-soft);
}

.editor-v2__save:hover {
  background: var(--editor-accent-hover);
}

.editor-v2__layout {
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

.editor-v2__elements-panel {
  border-right: 1px solid var(--sidebar-line);
  padding: 20px 16px 90px;
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

.editor-v2__ruler {
  position: absolute;
  z-index: 1;
  color: #92939b;
  font-size: 9px;
  pointer-events: none;
}

.editor-v2__ruler--top {
  top: 0;
  left: 52px;
  right: 0;
  height: 28px;
  display: flex;
  justify-content: space-around;
  align-items: end;
  border-bottom: 1px solid #d5d5d9;
  background: rgba(232, 232, 235, 0.95);
  padding: 0 30px 5px;
}

.editor-v2__ruler--left {
  top: 28px;
  bottom: 86px;
  left: 0;
  width: 46px;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  align-items: end;
  border-right: 1px solid #d5d5d9;
  padding: 14px 8px 14px 0;
}

.editor-v2__canvas-tools {
  position: absolute;
  z-index: 5;
  top: 42px;
  left: 11px;
  width: 34px;
  display: grid;
  justify-items: center;
  gap: 3px;
  border: 1px solid #d4d4d9;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(39, 40, 48, 0.14);
  padding: 4px;
}

.editor-v2__canvas-tools button {
  width: 28px;
  height: 30px;
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
  display: grid;
  place-items: center;
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
  width: 48px !important;
  font-size: 10px;
  font-weight: 700;
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

button:focus-visible,
a:focus-visible,
input:focus-visible,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid #e05a9f;
  outline-offset: 2px;
}

@media (max-width: 1280px) {
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

  .editor-v2__header-icon:nth-child(-n + 4),
  .editor-v2__share span {
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

  .editor-v2__ruler--left {
    bottom: 86px;
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

  .editor-v2__document-name strong {
    max-width: 38vw;
  }

  .editor-v2__header-actions {
    gap: 5px;
  }

  .editor-v2__share,
  .editor-v2__save {
    width: 32px;
    justify-content: center;
    padding: 0;
  }

  .editor-v2__share span,
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

  .editor-v2__ruler--top {
    left: 38px;
  }

  .editor-v2__ruler--left {
    width: 32px;
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
