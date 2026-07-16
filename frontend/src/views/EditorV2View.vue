<script setup lang="ts">
import {
  ArrowLeft,
  Check,
  Circle,
  ImageIcon,
  Minus,
  MousePointer2,
  Plus,
  Redo2,
  Save,
  Square,
  Type,
  Undo2,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import EditorCanvasV2 from "../editor-v2/EditorCanvasV2.vue";
import {
  createEditorDocumentV2,
  readEditorDocumentV2,
  type EditorLayerV2,
  type EditorSide,
} from "../editor-v2/document";

type EditorTool = "select" | "text" | "rect" | "ellipse" | "image";

const route = useRoute();
const documentId = computed(() => String(route.params.designId ?? "new"));
const storageKey = computed(() => `vizi.editor.v2.${documentId.value}`);
const document = ref(createEditorDocumentV2(documentId.value));
const activeSide = ref<EditorSide>("front");
const activeTool = ref<EditorTool>("select");
const zoom = ref(100);
const saveState = ref<"idle" | "saved">("idle");

const sides: EditorSide[] = ["front", "back"];
const activePage = computed(() => document.value.pages[activeSide.value]);
const visibleLayers = computed(() => activePage.value.layers.filter((layer) => layer.visible));

const tools: Array<{
  id: EditorTool;
  label: string;
  icon: typeof MousePointer2;
  available: boolean;
}> = [
  { id: "select", label: "Select", icon: MousePointer2, available: true },
  { id: "text", label: "Text", icon: Type, available: false },
  { id: "rect", label: "Rectangle", icon: Square, available: false },
  { id: "ellipse", label: "Ellipse", icon: Circle, available: false },
  { id: "image", label: "Image", icon: ImageIcon, available: false },
];

function selectPage(side: EditorSide): void {
  activeSide.value = side;
}

function layerIcon(layer: EditorLayerV2): typeof Type {
  if (layer.type === "text") return Type;
  if (layer.type === "ellipse") return Circle;
  if (layer.type === "image") return ImageIcon;
  return Square;
}

function updateZoom(delta: number): void {
  zoom.value = Math.min(160, Math.max(50, zoom.value + delta));
}

function saveLocally(): void {
  document.value.updatedAt = new Date().toISOString();
  localStorage.setItem(storageKey.value, JSON.stringify(document.value));
  saveState.value = "saved";
  window.setTimeout(() => {
    saveState.value = "idle";
  }, 1800);
}

onMounted(() => {
  const stored = readEditorDocumentV2(localStorage.getItem(storageKey.value));
  if (stored?.documentId === documentId.value) {
    document.value = stored;
  }
});
</script>

<template>
  <section class="editor-v2" data-editor-version="2">
    <header class="editor-v2__header">
      <div class="editor-v2__identity">
        <RouterLink class="editor-v2__icon-button" to="/designs" aria-label="Back to drafts" title="Back to drafts">
          <ArrowLeft :size="18" :stroke-width="1.8" aria-hidden="true" />
        </RouterLink>
        <strong class="editor-v2__brand">Vizi</strong>
        <span class="editor-v2__divider" aria-hidden="true" />
        <div class="editor-v2__document-name">
          <strong>{{ document.name }}</strong>
          <span>Editor V2</span>
        </div>
      </div>

      <div class="editor-v2__header-actions">
        <span v-if="saveState === 'saved'" class="editor-v2__save-status" role="status">
          <Check :size="15" :stroke-width="2" aria-hidden="true" />
          Saved locally
        </span>
        <button class="editor-v2__save" type="button" @click="saveLocally">
          <Save :size="17" :stroke-width="1.8" aria-hidden="true" />
          <span>Save</span>
        </button>
      </div>
    </header>

    <div class="editor-v2__shell">
      <aside class="editor-v2__sidebar editor-v2__sidebar--left" aria-label="Pages and layers">
        <section class="editor-v2__panel">
          <div class="editor-v2__panel-heading">
            <h2>Pages</h2>
            <span>2 sides</span>
          </div>
          <div class="editor-v2__pages" aria-label="Card sides">
            <button
              v-for="side in sides"
              :key="side"
              type="button"
              :class="{ active: activeSide === side }"
              :aria-pressed="activeSide === side"
              @click="selectPage(side)"
            >
              <span class="editor-v2__page-preview" :style="{ background: document.pages[side].background }">
                <span>{{ side === 'front' ? 'F' : 'B' }}</span>
              </span>
              <span>
                <strong>{{ document.pages[side].name }}</strong>
                <small>{{ document.pages[side].layers.length }} layers</small>
              </span>
            </button>
          </div>
        </section>

        <section class="editor-v2__panel editor-v2__panel--layers">
          <div class="editor-v2__panel-heading">
            <h2>Layers</h2>
            <div class="editor-v2__history" aria-label="History">
              <button type="button" disabled aria-label="Undo" title="Undo">
                <Undo2 :size="15" :stroke-width="1.8" aria-hidden="true" />
              </button>
              <button type="button" disabled aria-label="Redo" title="Redo">
                <Redo2 :size="15" :stroke-width="1.8" aria-hidden="true" />
              </button>
            </div>
          </div>
          <ol class="editor-v2__layers">
            <li v-for="layer in [...activePage.layers].reverse()" :key="layer.id">
              <component :is="layerIcon(layer)" :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>{{ layer.name }}</span>
            </li>
          </ol>
          <p v-if="activePage.layers.length === 0" class="editor-v2__empty">No layers</p>
        </section>
      </aside>

      <main class="editor-v2__workspace" aria-label="Card workspace">
        <div class="editor-v2__toolbar" role="toolbar" aria-label="Canvas tools">
          <button
            v-for="tool in tools"
            :key="tool.id"
            type="button"
            :class="{ active: activeTool === tool.id }"
            :disabled="!tool.available"
            :aria-pressed="activeTool === tool.id"
            :aria-label="tool.label"
            :title="tool.label"
            @click="activeTool = tool.id"
          >
            <component :is="tool.icon" :size="18" :stroke-width="1.8" aria-hidden="true" />
            <span>{{ tool.label }}</span>
          </button>
        </div>

        <div class="editor-v2__stage">
          <EditorCanvasV2 :document="document" :page="activePage" :zoom="zoom" />
        </div>

        <div class="editor-v2__zoom" aria-label="Canvas zoom">
          <button type="button" aria-label="Zoom out" title="Zoom out" @click="updateZoom(-10)">
            <Minus :size="16" :stroke-width="2" aria-hidden="true" />
          </button>
          <button type="button" class="editor-v2__zoom-value" title="Reset zoom" @click="zoom = 100">
            {{ zoom }}%
          </button>
          <button type="button" aria-label="Zoom in" title="Zoom in" @click="updateZoom(10)">
            <Plus :size="16" :stroke-width="2" aria-hidden="true" />
          </button>
        </div>
      </main>

      <aside class="editor-v2__sidebar editor-v2__sidebar--right" aria-label="Properties">
        <section class="editor-v2__panel">
          <div class="editor-v2__panel-heading">
            <h2>Properties</h2>
            <span>Document</span>
          </div>
          <dl class="editor-v2__properties">
            <div><dt>Size</dt><dd>{{ document.card.widthMm }} x {{ document.card.heightMm }} mm</dd></div>
            <div><dt>Side</dt><dd>{{ activePage.name }}</dd></div>
            <div><dt>Layers</dt><dd>{{ visibleLayers.length }}</dd></div>
            <div><dt>Schema</dt><dd>V{{ document.schemaVersion }}</dd></div>
          </dl>
        </section>
        <section class="editor-v2__selection-empty">
          <MousePointer2 :size="22" :stroke-width="1.6" aria-hidden="true" />
          <strong>Nothing selected</strong>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.editor-v2 {
  --v2-ink: #181b1a;
  --v2-muted: #69716d;
  --v2-line: #d9ddda;
  --v2-panel: #f8f9f7;
  --v2-canvas: #dfe3df;
  height: 100vh;
  overflow: hidden;
  background: var(--v2-canvas);
  color: var(--v2-ink);
  font-family: Aptos, "Segoe UI", sans-serif;
}

button,
a {
  font: inherit;
}

.editor-v2__header {
  position: relative;
  z-index: 10;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #282d2a;
  background: #171a19;
  color: #f6f7f4;
  padding: 0 14px;
}

.editor-v2__identity,
.editor-v2__header-actions,
.editor-v2__document-name,
.editor-v2__panel-heading,
.editor-v2__history,
.editor-v2__zoom {
  display: flex;
  align-items: center;
}

.editor-v2__identity,
.editor-v2__header-actions {
  min-width: 0;
  gap: 10px;
}

.editor-v2__icon-button,
.editor-v2__history button,
.editor-v2__zoom button {
  display: inline-grid;
  place-items: center;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.editor-v2__icon-button {
  width: 34px;
  height: 34px;
  border-radius: 5px;
}

.editor-v2__icon-button:hover {
  background: #292e2b;
}

.editor-v2__brand {
  font-size: 18px;
}

.editor-v2__divider {
  width: 1px;
  height: 24px;
  background: #3a403d;
}

.editor-v2__document-name {
  min-width: 0;
  align-items: flex-start;
  flex-direction: column;
  gap: 1px;
}

.editor-v2__document-name strong,
.editor-v2__document-name span {
  max-width: min(38vw, 420px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__document-name strong {
  font-size: 13px;
}

.editor-v2__document-name span {
  color: #9fa7a2;
  font-size: 10px;
}

.editor-v2__save-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #a8ddd6;
  font-size: 12px;
}

.editor-v2__save {
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 0;
  border-radius: 5px;
  background: #f4f5f2;
  color: #181b1a;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  padding: 0 13px;
}

.editor-v2__save:hover {
  background: #dfe6e1;
}

.editor-v2__shell {
  height: calc(100vh - 58px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 300px;
  min-height: 0;
}

.editor-v2__sidebar {
  min-width: 0;
  overflow: auto;
  background: var(--v2-panel);
  scrollbar-color: #bfc5c1 transparent;
}

.editor-v2__sidebar--left {
  border-right: 1px solid var(--v2-line);
}

.editor-v2__sidebar--right {
  border-left: 1px solid var(--v2-line);
}

.editor-v2__panel {
  border-bottom: 1px solid var(--v2-line);
  padding: 16px 14px;
}

.editor-v2__panel--layers {
  min-height: 240px;
}

.editor-v2__panel-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.editor-v2__panel-heading h2 {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.editor-v2__panel-heading > span {
  color: var(--v2-muted);
  font-size: 11px;
}

.editor-v2__pages {
  display: grid;
  gap: 7px;
}

.editor-v2__pages > button {
  min-width: 0;
  display: grid;
  grid-template-columns: 50px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--v2-ink);
  cursor: pointer;
  padding: 7px;
  text-align: left;
}

.editor-v2__pages > button:hover {
  background: #ecefeb;
}

.editor-v2__pages > button.active {
  border-color: #a6d2cc;
  background: #e2f0ed;
}

.editor-v2__page-preview {
  aspect-ratio: 5 / 3;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(24, 27, 26, 0.18);
  border-radius: 2px;
  color: #727a75;
  font-size: 9px;
  font-weight: 800;
}

.editor-v2__pages > button > span:last-child {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.editor-v2__pages strong,
.editor-v2__pages small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__pages strong {
  font-size: 12px;
}

.editor-v2__pages small {
  color: var(--v2-muted);
  font-size: 10px;
}

.editor-v2__history {
  gap: 2px;
}

.editor-v2__history button {
  width: 27px;
  height: 27px;
  border-radius: 4px;
  color: var(--v2-muted);
}

.editor-v2__history button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.editor-v2__layers {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.editor-v2__layers li {
  min-width: 0;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 9px;
  border-radius: 5px;
  color: #3c4540;
  font-size: 12px;
  padding: 0 9px;
}

.editor-v2__layers li:first-child {
  background: #ecefeb;
  color: var(--v2-ink);
}

.editor-v2__layers li span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__empty {
  color: var(--v2-muted);
  font-size: 12px;
}

.editor-v2__workspace {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background-color: var(--v2-canvas);
  background-image:
    linear-gradient(rgba(23, 28, 25, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(23, 28, 25, 0.035) 1px, transparent 1px);
  background-size: 24px 24px;
}

.editor-v2__toolbar {
  position: absolute;
  z-index: 4;
  top: 14px;
  left: 50%;
  display: flex;
  align-items: stretch;
  gap: 2px;
  border: 1px solid #ccd1cd;
  border-radius: 7px;
  background: #fafbf9;
  box-shadow: 0 10px 28px rgba(38, 45, 41, 0.16);
  padding: 4px;
  transform: translateX(-50%);
}

.editor-v2__toolbar button {
  width: 58px;
  height: 48px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 3px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #4e5852;
  cursor: pointer;
}

.editor-v2__toolbar button span {
  font-size: 9px;
}

.editor-v2__toolbar button:hover:not(:disabled) {
  background: #e9eeea;
}

.editor-v2__toolbar button.active {
  background: #1e2421;
  color: #f8faf7;
}

.editor-v2__toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.editor-v2__stage {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  padding: 90px 34px 72px;
}

.editor-v2__zoom {
  position: absolute;
  z-index: 4;
  right: 14px;
  bottom: 14px;
  gap: 2px;
  border: 1px solid #ccd1cd;
  border-radius: 6px;
  background: #fafbf9;
  box-shadow: 0 8px 24px rgba(38, 45, 41, 0.12);
  padding: 3px;
}

.editor-v2__zoom button {
  width: 31px;
  height: 31px;
  border-radius: 4px;
  color: #424b46;
}

.editor-v2__zoom button:hover {
  background: #e9eeea;
}

.editor-v2__zoom .editor-v2__zoom-value {
  width: 52px;
  font-size: 11px;
  font-weight: 700;
}

.editor-v2__properties {
  display: grid;
  gap: 0;
  margin: 0;
}

.editor-v2__properties div {
  min-width: 0;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e4e7e4;
  padding: 10px 0;
}

.editor-v2__properties dt,
.editor-v2__properties dd {
  margin: 0;
  font-size: 11px;
}

.editor-v2__properties dt {
  color: var(--v2-muted);
}

.editor-v2__properties dd {
  min-width: 0;
  overflow: hidden;
  color: var(--v2-ink);
  font-weight: 700;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-v2__selection-empty {
  min-height: 200px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: #929a95;
  padding: 20px;
  text-align: center;
}

.editor-v2__selection-empty strong {
  color: #69716d;
  font-size: 12px;
}

button:focus-visible,
a:focus-visible {
  outline: 2px solid #43a99f;
  outline-offset: 2px;
}

@media (max-width: 1100px) {
  .editor-v2__shell {
    grid-template-columns: 210px minmax(0, 1fr) 250px;
  }
}

@media (max-width: 820px) {
  .editor-v2 {
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }

  .editor-v2__header {
    position: sticky;
    top: 0;
  }

  .editor-v2__document-name span,
  .editor-v2__save-status {
    display: none;
  }

  .editor-v2__document-name strong {
    max-width: 30vw;
  }

  .editor-v2__shell {
    height: auto;
    grid-template-columns: 1fr;
  }

  .editor-v2__sidebar {
    overflow: visible;
  }

  .editor-v2__sidebar--left,
  .editor-v2__sidebar--right {
    border: 0;
    border-bottom: 1px solid var(--v2-line);
  }

  .editor-v2__sidebar--left {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .editor-v2__panel--layers {
    min-height: 0;
    border-left: 1px solid var(--v2-line);
  }

  .editor-v2__workspace {
    min-height: 560px;
  }

  .editor-v2__toolbar {
    max-width: calc(100vw - 20px);
    overflow-x: auto;
  }

  .editor-v2__stage {
    padding-inline: 18px;
  }
}

@media (max-width: 520px) {
  .editor-v2__brand,
  .editor-v2__divider {
    display: none;
  }

  .editor-v2__save {
    width: 34px;
    justify-content: center;
    padding: 0;
  }

  .editor-v2__save span {
    display: none;
  }

  .editor-v2__sidebar--left {
    grid-template-columns: 1fr;
  }

  .editor-v2__panel--layers {
    border-left: 0;
  }

  .editor-v2__workspace {
    min-height: 470px;
  }

  .editor-v2__toolbar button {
    width: 52px;
  }
}
</style>
