<script setup lang="ts">
import {
  AlignCenterHorizontal,
  AlignEndHorizontal,
  AlignStartHorizontal,
  ArrowLeft,
  Bell,
  Bold,
  Check,
  Circle,
  Copy,
  Diamond,
  Download,
  Eye,
  FileImage,
  Hexagon,
  ImageIcon,
  Italic,
  Layers3,
  LayoutGrid,
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
  Pentagon,
  Play,
  Plus,
  Redo2,
  RotateCw,
  Save,
  Shapes,
  Share2,
  Sparkles,
  Square,
  Star,
  Trash2,
  Triangle,
  Type,
  Underline,
  Undo2,
  Upload,
} from "@lucide/vue";
import { computed, onMounted, ref, watch } from "vue";
import type { Component } from "vue";
import { RouterLink, useRoute } from "vue-router";
import EditorCanvasV2 from "../editor-v2/EditorCanvasV2.vue";
import {
  createEditorDocumentV2,
  readEditorDocumentV2,
  type EditorLayerV2,
  type EditorLayerType,
  type EditorSide,
} from "../editor-v2/document";

type EditorTool = "select" | "text" | "rect" | "ellipse" | "image";
type SidebarPanel = "elements" | "text" | "ai" | "uploads" | "stock" | "apps" | "brand" | "layers";

type IconItem = {
  id: string;
  label: string;
  icon: Component;
};

const route = useRoute();
const documentId = computed(() => String(route.params.designId ?? "new"));
const storageKey = computed(() => `vizi.editor.v2.${documentId.value}`);
const document = ref(createEditorDocumentV2(documentId.value));
const activeSide = ref<EditorSide>("front");
const activePanel = ref<SidebarPanel>("elements");
const activeTool = ref<EditorTool>("select");
const selectedLayerId = ref<string | null>("front-title");
const zoom = ref(100);
const saveState = ref<"idle" | "saved" | "dirty">("idle");

const sides: EditorSide[] = ["front", "back"];

const activePage = computed(() => document.value.pages[activeSide.value]);
const selectedLayer = computed<EditorLayerV2 | null>(() => (
  activePage.value.layers.find((layer) => layer.id === selectedLayerId.value) ?? null
));
const panelTitle = computed(() => ({
  elements: "Elements",
  text: "Text",
  ai: "AI",
  uploads: "Uploads",
  stock: "Stock",
  apps: "Apps",
  brand: "Brand",
  layers: "Layers",
}[activePanel.value]));

const sidebarItems: Array<{ id: SidebarPanel; label: string; icon: Component }> = [
  { id: "elements", label: "Elements", icon: Shapes },
  { id: "text", label: "Text", icon: Type },
  { id: "ai", label: "AI", icon: Sparkles },
  { id: "uploads", label: "Uploads", icon: Upload },
  { id: "stock", label: "Stock", icon: FileImage },
  { id: "apps", label: "Apps", icon: LayoutGrid },
  { id: "brand", label: "Brand", icon: Palette },
  { id: "layers", label: "Layers", icon: Layers3 },
];

const shapeItems: Array<IconItem & { tool: EditorTool }> = [
  { id: "rectangle", label: "Rectangle", icon: Square, tool: "rect" },
  { id: "rounded", label: "Rounded", icon: Square, tool: "rect" },
  { id: "ellipse", label: "Ellipse", icon: Circle, tool: "ellipse" },
  { id: "triangle", label: "Triangle", icon: Triangle, tool: "rect" },
  { id: "pentagon", label: "Pentagon", icon: Pentagon, tool: "rect" },
  { id: "hexagon", label: "Hexagon", icon: Hexagon, tool: "rect" },
  { id: "star", label: "Star", icon: Star, tool: "rect" },
  { id: "diamond", label: "Diamond", icon: Diamond, tool: "rect" },
];

const canvasTools: Array<{ id: EditorTool; label: string; icon: Component }> = [
  { id: "select", label: "Select", icon: MousePointer2 },
  { id: "text", label: "Text", icon: Type },
  { id: "rect", label: "Rectangle", icon: Square },
  { id: "ellipse", label: "Ellipse", icon: Circle },
  { id: "image", label: "Image", icon: ImageIcon },
];

const palettes = ["#15161b", "#ffffff", "#b4367d", "#f4c46d", "#c6d6cb", "#5b83d4"];

function selectPage(side: EditorSide): void {
  activeSide.value = side;
  const nextLayer = document.value.pages[side].layers.find((layer) => layer.type === "text") ?? document.value.pages[side].layers.find((layer) => layer.visible);
  selectedLayerId.value = nextLayer?.id ?? null;
}

function selectLayer(layerId: string | null): void {
  selectedLayerId.value = layerId;
  if (layerId) {
    activePanel.value = "layers";
  }
}

function moveLayer(payload: { layerId: string; x: number; y: number }): void {
  const layer = activePage.value.layers.find((item) => item.id === payload.layerId);
  if (!layer || layer.locked) return;
  layer.x = payload.x;
  layer.y = payload.y;
  saveState.value = "dirty";
}

function resizeLayer(payload: { layerId: string; width: number; height: number }): void {
  const layer = activePage.value.layers.find((item) => item.id === payload.layerId);
  if (!layer || layer.locked) return;
  layer.width = payload.width;
  layer.height = payload.height;
  saveState.value = "dirty";
}

function rotateLayer(payload: { layerId: string; rotation: number }): void {
  const layer = activePage.value.layers.find((item) => item.id === payload.layerId);
  if (!layer || layer.locked) return;
  layer.rotation = payload.rotation;
  saveState.value = "dirty";
}

function activateTool(tool: EditorTool): void {
  activeTool.value = tool;
  if (tool === "text") {
    activePanel.value = "text";
  } else if (tool === "image") {
    activePanel.value = "uploads";
  } else if (tool !== "select") {
    activePanel.value = "elements";
  }
}

function activateShape(tool: EditorTool): void {
  activeTool.value = tool;
  activePanel.value = "elements";
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
}

function updateSelectedNumber(field: "fontSize" | "x" | "y" | "width" | "height", event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLInputElement).value);
  if (!layer || !Number.isFinite(value)) return;
  layer[field] = value;
}

function updateSelectedFill(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer) return;
  layer.fill = (event.target as HTMLInputElement).value;
}

function updateSelectedFont(event: Event): void {
  const layer = selectedLayer.value;
  if (!layer || layer.type !== "text") return;
  layer.fontFamily = (event.target as HTMLSelectElement).value;
}



function updateSelectedWeight(event: Event): void {
  const layer = selectedLayer.value;
  const value = Number((event.target as HTMLSelectElement).value);
  if (!layer || layer.type !== "text" || !Number.isFinite(value)) return;
  layer.fontWeight = value;
}
function setTextAlign(align: "left" | "center" | "right"): void {
  const layer = selectedLayer.value;
  if (layer?.type === "text") layer.textAlign = align;
}

function toggleLayerVisibility(layer: EditorLayerV2): void {
  layer.visible = !layer.visible;
}

function toggleLayerLock(layer: EditorLayerV2): void {
  layer.locked = !layer.locked;
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
  if (!current) {
    selectedLayerId.value = activePage.value.layers.find((layer) => layer.type === "text")?.id ?? activePage.value.layers.find((layer) => layer.visible)?.id ?? null;
  }
});

onMounted(() => {
  const stored = readEditorDocumentV2(localStorage.getItem(storageKey.value));
  if (stored?.documentId === documentId.value) {
    document.value = stored;
    selectedLayerId.value = stored.pages.front.layers.find((layer) => layer.type === "text")?.id ?? stored.pages.front.layers.find((layer) => layer.visible)?.id ?? null;
  }
});
</script>

<template>
  <section class="editor-v2" data-editor-version="2">
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
          <component :is="item.icon" :size="19" :stroke-width="1.7" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <aside class="editor-v2__elements-panel" aria-label="Elements panel">
        <div class="editor-v2__panel-title">
          <h1>{{ panelTitle }}</h1>
          <button type="button" aria-label="Close panel" title="Close panel">
            <PanelLeft :size="16" :stroke-width="1.8" aria-hidden="true" />
          </button>
        </div>

        <template v-if="activePanel === 'elements'">
          <section class="editor-v2__element-section">
            <div class="editor-v2__section-label">
              <span>Shapes</span>
              <span>⌄</span>
            </div>
            <div class="editor-v2__shape-grid">
              <button
                v-for="shape in shapeItems"
                :key="shape.id"
                type="button"
                :class="{ active: activeTool === shape.tool && shape.id === 'rectangle' }"
                @click="activateShape(shape.tool)"
              >
                <component :is="shape.icon" :size="28" :stroke-width="1.5" aria-hidden="true" />
                <span>{{ shape.label }}</span>
              </button>
            </div>
          </section>
          <section class="editor-v2__element-section editor-v2__element-section--compact">
            <div class="editor-v2__section-label"><span>Quick add</span><span>⌄</span></div>
            <button class="editor-v2__quick-item" type="button" @click="activateTool('text')">
              <Type :size="18" :stroke-width="1.7" aria-hidden="true" />
              <span>Text</span>
              <small>T</small>
            </button>
            <button class="editor-v2__quick-item" type="button" @click="activateTool('image')">
              <ImageIcon :size="18" :stroke-width="1.7" aria-hidden="true" />
              <span>Image</span>
              <small>I</small>
            </button>
          </section>
        </template>

        <template v-else-if="activePanel === 'text'">
          <section class="editor-v2__empty-panel">
            <Type :size="28" :stroke-width="1.5" aria-hidden="true" />
            <strong>Add text</strong>
            <button type="button" @click="activateTool('text')">Heading</button>
            <button type="button" @click="activateTool('text')">Body text</button>
          </section>
        </template>

        <template v-else-if="activePanel === 'uploads'">
          <section class="editor-v2__empty-panel">
            <Upload :size="28" :stroke-width="1.5" aria-hidden="true" />
            <strong>Upload assets</strong>
            <button type="button" @click="activateTool('image')">Choose image</button>
          </section>
        </template>

        <template v-else-if="activePanel === 'layers'">
          <section class="editor-v2__layers-panel">
            <div class="editor-v2__section-label"><span>{{ activePage.name }} layers</span><span>{{ activePage.layers.length }}</span></div>
            <ol class="editor-v2__layers">
              <li
                v-for="layer in [...activePage.layers].reverse()"
                :key="layer.id"
                :class="{ active: layer.id === selectedLayerId }"
                @click="selectLayer(layer.id)"
              >
                <div class="editor-v2__layer-main">
                  <component :is="layerIcon(layer)" :size="15" :stroke-width="1.7" aria-hidden="true" />
                  <span>{{ layer.name }}</span>
                </div>
                <div class="editor-v2__layer-actions">
                  <button type="button" :aria-label="`Toggle ${layer.name} visibility`" title="Toggle visibility" @click.stop="toggleLayerVisibility(layer)">
                    <Eye v-if="layer.visible" :size="14" :stroke-width="1.7" aria-hidden="true" />
                    <Eye v-else :size="14" :stroke-width="1.7" class="muted" aria-hidden="true" />
                  </button>
                  <button type="button" :aria-label="`Toggle ${layer.name} lock`" title="Toggle lock" @click.stop="toggleLayerLock(layer)">
                    <LockKeyhole :size="14" :stroke-width="1.7" :class="{ muted: !layer.locked }" aria-hidden="true" />
                  </button>
                </div>
              </li>
            </ol>
          </section>
        </template>

        <template v-else>
          <section class="editor-v2__empty-panel">
            <Sparkles v-if="activePanel === 'ai'" :size="28" :stroke-width="1.5" aria-hidden="true" />
            <Palette v-else-if="activePanel === 'brand'" :size="28" :stroke-width="1.5" aria-hidden="true" />
            <LayoutGrid v-else :size="28" :stroke-width="1.5" aria-hidden="true" />
            <strong>{{ panelTitle }}</strong>
            <span>Panel ready for the next editor phase</span>
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

      <aside class="editor-v2__inspector" aria-label="Inspector">
        <div class="editor-v2__inspector-title">
          <h1>Inspector</h1>
          <div>
            <button type="button" aria-label="Toggle panel visibility" title="Toggle panel visibility"><PanelRight :size="16" :stroke-width="1.8" aria-hidden="true" /></button>
            <button type="button" aria-label="Lock inspector" title="Lock inspector"><LockKeyhole :size="16" :stroke-width="1.8" aria-hidden="true" /></button>
          </div>
        </div>

        <template v-if="selectedLayer">
          <div class="editor-v2__inspector-lock">
            <LockKeyhole :size="14" :stroke-width="1.8" aria-hidden="true" />
            <span>{{ selectedLayer.locked ? "Layer locked" : "Lock for me" }}</span>
            <button type="button" @click="toggleLayerLock(selectedLayer)">{{ selectedLayer.locked ? "Unlock" : "Lock" }}</button>
          </div>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>{{ selectedLayer.type.toUpperCase() }}</strong>
              <div>
                <button type="button" aria-label="Toggle selected layer visibility" title="Toggle visibility" @click="toggleLayerVisibility(selectedLayer)"><Eye :size="15" :stroke-width="1.7" aria-hidden="true" /></button>
                <button type="button" aria-label="Lock selected layer" title="Toggle lock" @click="toggleLayerLock(selectedLayer)"><LockKeyhole :size="15" :stroke-width="1.7" aria-hidden="true" /></button>
              </div>
            </div>

            <textarea
              v-if="selectedLayer.type === 'text'"
              class="editor-v2__text-input"
              :value="selectedLayer.content ?? ''"
              aria-label="Selected text"
              @input="updateSelectedText"
            />
            <div v-else class="editor-v2__selected-name">{{ selectedLayer.name }}</div>

            <template v-if="selectedLayer.type === 'text'">
              <div class="editor-v2__segmented">
                <button type="button" class="active">Heading</button>
                <button type="button">Subheading</button>
                <button type="button">Body</button>
              </div>
              <select class="editor-v2__select" :value="selectedLayer.fontFamily" aria-label="Font family" @change="updateSelectedFont">
                <option>Aptos, Segoe UI, sans-serif</option>
                <option>Arial, sans-serif</option>
                <option>Georgia, serif</option>
              </select>
              <div class="editor-v2__control-row">
                <select class="editor-v2__select" :value="selectedLayer.fontWeight" aria-label="Font weight" @change="updateSelectedWeight">
                  <option :value="400">Regular</option>
                  <option :value="500">Medium</option>
                  <option :value="700">Bold</option>
                </select>
                <input :value="selectedLayer.fontSize ?? 16" type="number" min="1" max="200" aria-label="Font size" @input="updateSelectedNumber('fontSize', $event)">
              </div>
              <div class="editor-v2__icon-row">
                <button type="button" aria-label="Bold" title="Bold" :class="{ active: selectedLayer.fontWeight && selectedLayer.fontWeight >= 700 }" @click="selectedLayer.fontWeight = selectedLayer.fontWeight && selectedLayer.fontWeight >= 700 ? 500 : 700"><Bold :size="15" :stroke-width="1.9" aria-hidden="true" /></button>
                <button type="button" aria-label="Italic" title="Italic"><Italic :size="15" :stroke-width="1.9" aria-hidden="true" /></button>
                <button type="button" aria-label="Underline" title="Underline"><Underline :size="15" :stroke-width="1.9" aria-hidden="true" /></button>
                <span class="editor-v2__icon-divider" aria-hidden="true" />
                <button type="button" aria-label="Align left" title="Align left" :class="{ active: selectedLayer.textAlign === 'left' }" @click="setTextAlign('left')"><AlignStartHorizontal :size="15" :stroke-width="1.8" aria-hidden="true" /></button>
                <button type="button" aria-label="Align center" title="Align center" :class="{ active: selectedLayer.textAlign === 'center' }" @click="setTextAlign('center')"><AlignCenterHorizontal :size="15" :stroke-width="1.8" aria-hidden="true" /></button>
                <button type="button" aria-label="Align right" title="Align right" :class="{ active: selectedLayer.textAlign === 'right' }" @click="setTextAlign('right')"><AlignEndHorizontal :size="15" :stroke-width="1.8" aria-hidden="true" /></button>
              </div>
            </template>

            <div class="editor-v2__inspector-subtitle">Fill</div>
            <div class="editor-v2__fill-control">
              <input :value="selectedLayer.fill ?? '#ffffff'" type="color" aria-label="Fill color" @input="updateSelectedFill">
              <span>{{ selectedLayer.fill ?? "#ffffff" }}</span>
              <input :value="Math.round((selectedLayer.opacity ?? 1) * 100)" type="number" min="0" max="100" aria-label="Opacity">
              <span>%</span>
            </div>
            <div class="editor-v2__swatches" aria-label="Color palette">
              <button v-for="color in palettes" :key="color" type="button" :style="{ background: color }" :aria-label="`Use ${color}`" :title="color" @click="selectedLayer.fill = color" />
            </div>
          </section>

          <section class="editor-v2__inspector-section">
            <div class="editor-v2__inspector-section-title">
              <strong>GEOMETRY</strong>
              <Link2 :size="15" :stroke-width="1.7" aria-hidden="true" />
            </div>
            <div class="editor-v2__geometry-grid">
              <label>X<input :value="selectedLayer.x" type="number" step="0.1" @input="updateSelectedNumber('x', $event)"></label>
              <label>Y<input :value="selectedLayer.y" type="number" step="0.1" @input="updateSelectedNumber('y', $event)"></label>
              <label>W<input :value="selectedLayer.width" type="number" step="0.1" @input="updateSelectedNumber('width', $event)"></label>
              <label>H<input :value="selectedLayer.height" type="number" step="0.1" @input="updateSelectedNumber('height', $event)"></label>
            </div>
          </section>
        </template>

        <section v-else class="editor-v2__nothing-selected">
          <MousePointer2 :size="22" :stroke-width="1.6" aria-hidden="true" />
          <strong>Nothing selected</strong>
          <span>Select a layer from the card or Layers panel.</span>
        </section>

        <div class="editor-v2__inspector-footer">
          <span>Card format</span>
          <strong>{{ document.card.widthMm }} x {{ document.card.heightMm }} mm</strong>
          <span>Side</span>
          <strong>{{ activePage.name }}</strong>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.editor-v2 {
  --editor-ink: #26262d;
  --editor-muted: #777881;
  --editor-line: #e2e2e6;
  --editor-panel: #ffffff;
  --editor-workspace: #e9e9ec;
  --editor-soft: #f6f6f8;
  --editor-accent: #b4367d;
  --editor-accent-soft: #f9eaf3;
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
.editor-v2__inspector-title,
.editor-v2__inspector-section-title,
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
.editor-v2__inspector-section-title button,
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
  background: #9f2d6e;
}

.editor-v2__layout {
  height: calc(100dvh - 54px);
  display: grid;
  grid-template-columns: 62px 260px minmax(0, 1fr) 294px;
  min-height: 0;
}

.editor-v2__rail,
.editor-v2__elements-panel,
.editor-v2__inspector {
  min-width: 0;
  overflow: auto;
  background: var(--editor-panel);
}

.editor-v2__rail {
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  border-right: 1px solid var(--editor-line);
  padding: 10px 5px;
}

.editor-v2__rail button {
  width: 52px;
  min-height: 52px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #686972;
  cursor: pointer;
  font-size: 9px;
  padding: 5px 2px;
}

.editor-v2__rail button:hover {
  background: var(--editor-soft);
  color: var(--editor-ink);
}

.editor-v2__rail button.active {
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
  font-weight: 700;
}

.editor-v2__elements-panel {
  border-right: 1px solid var(--editor-line);
  padding: 20px 14px 90px;
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
  font-size: 16px;
  font-weight: 800;
}

.editor-v2__panel-title button,
.editor-v2__inspector-title button {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--editor-muted);
  cursor: pointer;
}

.editor-v2__panel-title button:hover,
.editor-v2__inspector-title button:hover {
  background: var(--editor-soft);
}

.editor-v2__element-section {
  margin-bottom: 24px;
}

.editor-v2__element-section--compact {
  padding-top: 4px;
}

.editor-v2__section-label {
  margin-bottom: 10px;
  color: #8a8b93;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.editor-v2__shape-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.editor-v2__shape-grid button {
  min-width: 0;
  aspect-ratio: 1.1;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 7px;
  border: 1px solid #f0f0f2;
  border-radius: 6px;
  background: #fbfbfc;
  color: #4c4d55;
  cursor: pointer;
  padding: 10px 4px;
}

.editor-v2__shape-grid button:hover,
.editor-v2__shape-grid button.active {
  border-color: #e6b4cf;
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
}

.editor-v2__shape-grid button span {
  font-size: 10px;
}

.editor-v2__quick-item {
  width: 100%;
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #505159;
  cursor: pointer;
  padding: 0 8px;
  text-align: left;
}

.editor-v2__quick-item:hover {
  background: var(--editor-soft);
  color: var(--editor-accent);
}

.editor-v2__quick-item small {
  margin-left: auto;
  color: #a4a5ac;
  font-size: 10px;
}

.editor-v2__empty-panel {
  min-height: 280px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #a1a2aa;
  text-align: center;
}

.editor-v2__empty-panel strong {
  color: var(--editor-ink);
  font-size: 13px;
}

.editor-v2__empty-panel span {
  max-width: 180px;
  font-size: 11px;
  line-height: 1.5;
}

.editor-v2__empty-panel button {
  min-height: 30px;
  border: 1px solid var(--editor-line);
  border-radius: 5px;
  background: #ffffff;
  color: var(--editor-accent);
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  padding: 0 12px;
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
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  border-radius: 5px;
  color: #55565e;
  cursor: pointer;
  padding: 0 7px 0 9px;
}

.editor-v2__layers li:hover,
.editor-v2__layers li.active {
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
}

.editor-v2__layer-main,
.editor-v2__layer-actions {
  display: flex;
  align-items: center;
}

.editor-v2__layer-main {
  min-width: 0;
  gap: 9px;
}

.editor-v2__layer-main span {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__layer-actions {
  gap: 2px;
}

.editor-v2__layer-actions button {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  color: inherit;
}

.editor-v2__layer-actions button:hover {
  background: rgba(180, 54, 125, 0.12);
}

.editor-v2__layer-actions .muted {
  opacity: 0.34;
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
  color: #9b9ca4;
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
  border-bottom: 1px solid #dcdce0;
  background: rgba(233, 233, 236, 0.94);
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
  border-right: 1px solid #dcdce0;
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
  border: 1px solid #d8d8dc;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(56, 57, 65, 0.12);
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
  color: #5b5c64;
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
  padding: 58px 48px 120px 62px;
}

.editor-v2__card-heading {
  width: min(720px, calc(100vw - 690px));
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  color: #777881;
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
  border: 1px solid #d8d8dc;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 7px 18px rgba(56, 57, 65, 0.12);
  padding: 3px;
}

.editor-v2__zoom button {
  width: 29px;
  height: 28px;
  border-radius: 4px;
  color: #5b5c64;
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
  border-color: #e6b4cf;
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
  border-left: 1px solid var(--editor-line);
  padding: 20px 16px 90px;
}

.editor-v2__inspector-title {
  justify-content: space-between;
  margin-bottom: 20px;
}

.editor-v2__inspector-title > div,
.editor-v2__inspector-section-title > div {
  display: flex;
  gap: 2px;
}

.editor-v2__inspector-title button,
.editor-v2__inspector-section-title button {
  color: var(--editor-muted);
}

.editor-v2__inspector-lock {
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--editor-line);
  border-radius: 5px;
  color: var(--editor-muted);
  font-size: 10px;
  padding: 0 9px;
}

.editor-v2__inspector-lock button {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--editor-accent);
  cursor: pointer;
  font-size: 10px;
  font-weight: 700;
}

.editor-v2__inspector-section {
  border-bottom: 1px solid var(--editor-line);
  padding: 20px 0;
}

.editor-v2__inspector-section-title {
  margin-bottom: 12px;
  color: var(--editor-muted);
  font-size: 10px;
  letter-spacing: 0.07em;
}

.editor-v2__text-input,
.editor-v2__selected-name {
  width: 100%;
  min-height: 54px;
  box-sizing: border-box;
  border: 1px solid #dedee2;
  border-radius: 6px;
  background: #ffffff;
  color: var(--editor-ink);
  font-size: 13px;
  line-height: 1.35;
  padding: 10px;
  resize: vertical;
}

.editor-v2__text-input:focus,
.editor-v2__select:focus,
.editor-v2__control-row input:focus,
.editor-v2__geometry-grid input:focus {
  border-color: #d884b1;
  outline: 2px solid rgba(180, 54, 125, 0.12);
}

.editor-v2__segmented {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  margin: 10px 0;
}

.editor-v2__segmented button {
  min-width: 0;
  min-height: 28px;
  border: 0;
  border-radius: 4px;
  background: var(--editor-soft);
  color: var(--editor-muted);
  cursor: pointer;
  font-size: 9px;
}

.editor-v2__segmented button.active,
.editor-v2__segmented button:hover {
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
}

.editor-v2__select,
.editor-v2__control-row input,
.editor-v2__geometry-grid input {
  min-width: 0;
  height: 32px;
  box-sizing: border-box;
  border: 1px solid var(--editor-line);
  border-radius: 5px;
  background: #ffffff;
  color: var(--editor-ink);
  font-size: 11px;
  padding: 0 8px;
}

.editor-v2__select {
  width: 100%;
}

.editor-v2__control-row {
  gap: 6px;
  margin-top: 7px;
}

.editor-v2__control-row .editor-v2__select {
  flex: 1;
}

.editor-v2__control-row input {
  width: 66px;
}

.editor-v2__icon-row {
  gap: 3px;
  margin-top: 9px;
}

.editor-v2__icon-row button {
  width: 29px;
  height: 28px;
  border-radius: 4px;
  color: var(--editor-muted);
}

.editor-v2__icon-row button:hover,
.editor-v2__icon-row button.active {
  background: var(--editor-accent-soft);
  color: var(--editor-accent);
}

.editor-v2__icon-divider {
  width: 1px;
  height: 20px;
  background: var(--editor-line);
  margin: 0 3px;
}

.editor-v2__inspector-subtitle {
  margin: 18px 0 7px;
  color: var(--editor-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.editor-v2__fill-control {
  height: 34px;
  border: 1px solid var(--editor-line);
  border-radius: 5px;
  color: var(--editor-ink);
  font-size: 10px;
  overflow: hidden;
}

.editor-v2__fill-control input[type="color"] {
  width: 35px;
  height: 34px;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 4px;
}

.editor-v2__fill-control input[type="number"] {
  width: 42px;
  height: 100%;
  margin-left: auto;
  border: 0;
  border-left: 1px solid var(--editor-line);
  outline: 0;
  font-size: 10px;
  text-align: right;
  padding: 0 4px;
}

.editor-v2__fill-control > span:last-child {
  padding-right: 7px;
}

.editor-v2__swatches {
  display: flex;
  gap: 6px;
  margin-top: 9px;
}

.editor-v2__swatches button {
  width: 20px;
  height: 20px;
  border: 1px solid rgba(31, 33, 40, 0.16);
  border-radius: 50%;
  cursor: pointer;
}

.editor-v2__geometry-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.editor-v2__geometry-grid label {
  display: grid;
  gap: 4px;
  color: var(--editor-muted);
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
}

.editor-v2__geometry-grid input {
  width: 100%;
}

.editor-v2__nothing-selected {
  min-height: 220px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  color: #a1a2aa;
  text-align: center;
}

.editor-v2__nothing-selected strong {
  color: var(--editor-ink);
  font-size: 12px;
}

.editor-v2__nothing-selected span {
  max-width: 180px;
  font-size: 10px;
  line-height: 1.5;
}

.editor-v2__inspector-footer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px 12px;
  margin-top: 22px;
  color: var(--editor-muted);
  font-size: 10px;
}

.editor-v2__inspector-footer strong {
  color: var(--editor-ink);
  font-size: 10px;
  text-align: right;
}

button:focus-visible,
a:focus-visible,
input:focus-visible,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid #cf6fa3;
  outline-offset: 2px;
}

@media (max-width: 1180px) {
  .editor-v2__layout {
    grid-template-columns: 56px 220px minmax(0, 1fr) 258px;
  }

  .editor-v2__stage {
    padding-inline: 40px 28px;
  }

  .editor-v2__card-heading {
    width: min(640px, calc(100vw - 590px));
  }
}

@media (max-width: 900px) {
  .editor-v2 {
    overflow: visible;
  }

  .editor-v2__header {
    position: sticky;
    z-index: 20;
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
    border-bottom: 1px solid var(--editor-line);
    padding: 5px 8px;
  }

  .editor-v2__rail button {
    flex: 0 0 58px;
    min-height: 52px;
  }

  .editor-v2__elements-panel,
  .editor-v2__inspector {
    overflow: visible;
    border-right: 0;
    border-left: 0;
    border-bottom: 1px solid var(--editor-line);
    padding: 16px;
  }

  .editor-v2__elements-panel {
    max-height: none;
  }

  .editor-v2__element-section {
    margin-bottom: 12px;
  }

  .editor-v2__shape-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .editor-v2__workspace {
    min-height: 650px;
  }

  .editor-v2__stage {
    min-height: 650px;
    padding: 58px 30px 126px 54px;
  }

  .editor-v2__card-heading {
    width: min(88vw, 680px);
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
