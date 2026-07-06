<script setup lang="ts">
import {
  ArrowDown,
  ArrowUp,
  Eye,
  EyeOff,
  ImageIcon,
  Lock,
  MousePointer2,
  QrCode,
  Shapes,
  Sticker,
  Type,
  Unlock,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { deleteDesign, getDesign, updateDesign, type DesignDetail } from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};
type EditorPage = "front" | "back";
type EditorTool = "select" | "text" | "shape" | "image" | "qr" | "icon";
type ColorTarget = "fill" | "stroke";

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
const activeColorTarget = ref<ColorTarget>("fill");
const editorPages: { id: EditorPage; label: string }[] = [
  { id: "front", label: "Front" },
  { id: "back", label: "Back" },
];
const editorTools: { id: EditorTool; label: string; icon: typeof MousePointer2 }[] = [
  { id: "select", label: "Select", icon: MousePointer2 },
  { id: "text", label: "Text", icon: Type },
  { id: "shape", label: "Shape", icon: Shapes },
  { id: "image", label: "Image", icon: ImageIcon },
  { id: "qr", label: "QR", icon: QrCode },
  { id: "icon", label: "Icon", icon: Sticker },
];
const colorTargets: { id: ColorTarget; label: string }[] = [
  { id: "fill", label: "Fill" },
  { id: "stroke", label: "Stroke" },
];
const recentColors = ["#B1B2B5", "#2F281C", "#A87F33", "#5F7344", "#A5382F", "#FFFFFF"];

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
  editorTools.find((tool) => tool.id === activeTool.value)?.label ?? "Select",
);
const activeColorTargetLabel = computed(() =>
  colorTargets.find((target) => target.id === activeColorTarget.value)?.label ?? "Fill",
);
const editorCanvasLayers = computed<CanvasLayer[]>(() =>
  selectedPage.value === "front" ? canvasLayers.value : [],
);
const displayedCanvasLayers = computed<CanvasLayer[]>(() =>
  isEditorRoute.value ? editorCanvasLayers.value : canvasLayers.value,
);
const canvasLayerCount = computed(() => displayedCanvasLayers.value.length);
const selectedLayer = computed<CanvasLayer | null>(
  () => selectedLayerIndexes.value.length > 0
    ? displayedCanvasLayers.value[selectedLayerIndex.value] ?? null
    : null,
);
const resizableLayerIndex = computed<number | null>(() => {
  const layer = selectedLayer.value;
  return selectedPage.value === "front"
    && selectedLayerIndexes.value.length === 1
    && layer
    && layerIsVisible(layer)
    && !layerIsLocked(layer)
    ? selectedLayerIndex.value
    : null;
});
const selectedLayerLabel = computed(() => {
  if (selectedLayerIndexes.value.length > 1) {
    return `${selectedLayerIndexes.value.length} layers selected`;
  }
  const layer = selectedLayer.value;
  return layer ? `${selectedLayerIndex.value + 1}. ${optionalString(layer.type) ?? "Layer"}` : "No layer";
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
  displayedCanvasLayers.value.findIndex((layer) => layer.type === "text"),
);
const firstTextLayerLocked = computed(() => {
  const layer = editableLayers.value[firstTextLayerIndex.value];
  return layer ? layerIsLocked(layer) : false;
});
const firstTextLayerText = computed({
  get: () => {
    const layer = editableLayers.value[firstTextLayerIndex.value];
    return layer ? optionalString(layer.text ?? layer.value) ?? "" : "";
  },
  set: (value: string) => {
    const index = firstTextLayerIndex.value;
    const layer = editableLayers.value[index];
    if (!layer) {
      return;
    }

    const textField = typeof layer.text === "string" || typeof layer.value !== "string"
      ? "text"
      : "value";
    editableLayers.value[index] = { ...layer, [textField]: value };
    saveMessage.value = "";
  },
});
function isCanvasLayer(layer: unknown): layer is CanvasLayer {
  return typeof layer === "object" && layer !== null;
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
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
    return Array.isArray(canvas.layers) ? canvas.layers.filter(isCanvasLayer) : [];
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

function selectTool(tool: EditorTool): void {
  activeTool.value = tool;
  saveMessage.value = "";
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
  saveMessage.value = "";
}

function startLayerDrag(index: number, event: PointerEvent): void {
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
        x: start.x + deltaX,
        y: start.y + deltaY,
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
  const frameRect = frame.getBoundingClientRect();
  const startX = event.clientX;
  const startY = event.clientY;
  const x = numberValue(layer.x, 8);
  const y = numberValue(layer.y, 8);
  const startWidth = numberValue(layer.width, layer.type === "text" ? 45 : 32);
  const startHeight = numberValue(layer.height, layer.type === "text" ? 16 : 26);

  const move = (moveEvent: PointerEvent) => {
    const width = clamp(
      startWidth + ((moveEvent.clientX - startX) / frameRect.width) * 100,
      3,
      100 - x,
    );
    const height = clamp(
      startHeight + ((moveEvent.clientY - startY) / frameRect.height) * 100,
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

function numberValue(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(Math.max(value, minimum), maximum);
}

function toggleLayerVisibility(index: number): void {
  const layer = editableLayers.value[index];
  if (!layer) {
    return;
  }

  editableLayers.value[index] = { ...layer, visible: !layerIsVisible(layer) };
  selectedLayerIndex.value = index;
  selectedLayerIndexes.value = [index];
  saveMessage.value = "";
}

function toggleLayerLock(index: number): void {
  const layer = editableLayers.value[index];
  if (!layer) {
    return;
  }

  editableLayers.value[index] = { ...layer, locked: !layerIsLocked(layer) };
  selectedLayerIndex.value = index;
  selectedLayerIndexes.value = [index];
  saveMessage.value = "";
}

function moveLayer(index: number, direction: -1 | 1): void {
  const nextIndex = index + direction;
  if (nextIndex < 0 || nextIndex >= editableLayers.value.length) {
    return;
  }

  const layers = [...editableLayers.value];
  [layers[index], layers[nextIndex]] = [layers[nextIndex], layers[index]];
  editableLayers.value = layers;
  selectedLayerIndex.value = nextIndex;
  selectedLayerIndexes.value = selectedLayerIndexes.value
    .map((selected) => selected === index ? nextIndex : selected === nextIndex ? index : selected)
    .sort((left, right) => left - right);
  saveMessage.value = "";
}

function applyQuickColor(color: string): void {
  if (selectedPage.value !== "front") {
    return;
  }
  const layer = editableLayers.value[selectedLayerIndex.value];
  if (!layer || layerIsLocked(layer)) {
    return;
  }

  const field = activeColorTarget.value === "stroke"
    ? "stroke"
    : layer.type === "text" ? "color" : "fill";
  editableLayers.value[selectedLayerIndex.value] = { ...layer, [field]: color };
  saveMessage.value = "";
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
    saveMessage.value = `Draft #${saved.id} saved`;
  } catch (unknownError) {
    saveError.value = unknownError instanceof Error ? unknownError.message : "Cannot save draft";
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
  if (!Number.isFinite(designId.value)) {
    error.value = "Draft id is invalid";
    loading.value = false;
    return;
  }

  try {
    design.value = await getDesign(designId.value);
    editableLayers.value = parseCanvasLayers(design.value.canvasJson);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load draft";
  } finally {
    loading.value = false;
  }
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
        </div>
        <div class="editor-header-actions">
          <span v-if="saveMessage" class="save-status" role="status">{{ saveMessage }}</span>
          <span v-else-if="!saveError" class="muted">Saved draft</span>
          <button class="primary-action" type="button" :disabled="saving" @click="saveDraft">
            {{ saving ? "Saving..." : "Save draft" }}
          </button>
        </div>
      </header>

      <div class="editor-shell">
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
                @click="selectedPage = page.id"
              >
                {{ page.label }}
              </button>
            </div>
          </section>
          <section class="editor-section">
            <h2>Layers</h2>
            <ol class="editor-layer-list">
              <li v-for="(layer, index) in displayedCanvasLayers" :key="index">
                <div
                  class="editor-layer-row"
                  :class="{ active: selectedLayerIndexes.includes(index) }"
                >
                  <button
                    type="button"
                    class="editor-layer-button"
                    :aria-pressed="selectedLayerIndexes.includes(index)"
                    :aria-label="`Select layer ${index + 1} ${layer.type || 'Layer'}`"
                    @click="selectLayer(index, $event)"
                  >
                    <span>{{ index + 1 }}</span>
                    <strong>{{ layer.type || "Layer" }}</strong>
                  </button>
                  <div class="editor-layer-actions" :aria-label="`Layer ${index + 1} actions`">
                    <button
                      type="button"
                      class="editor-layer-toggle"
                      :disabled="index === 0"
                      :aria-label="`Move layer ${index + 1} up`"
                      title="Move layer up"
                      @click="moveLayer(index, -1)"
                    >
                      <ArrowUp :size="16" :stroke-width="1.8" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      class="editor-layer-toggle"
                      :disabled="index === displayedCanvasLayers.length - 1"
                      :aria-label="`Move layer ${index + 1} down`"
                      title="Move layer down"
                      @click="moveLayer(index, 1)"
                    >
                      <ArrowDown :size="16" :stroke-width="1.8" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      class="editor-layer-toggle"
                      :aria-pressed="!layerIsVisible(layer)"
                      :aria-label="`${layerIsVisible(layer) ? 'Hide' : 'Show'} layer ${index + 1}`"
                      :title="layerIsVisible(layer) ? 'Hide layer' : 'Show layer'"
                      @click="toggleLayerVisibility(index)"
                    >
                      <component
                        :is="layerIsVisible(layer) ? Eye : EyeOff"
                        :size="16"
                        :stroke-width="1.8"
                        aria-hidden="true"
                      />
                    </button>
                    <button
                      type="button"
                      class="editor-layer-toggle editor-layer-toggle--lock"
                      :aria-pressed="layerIsLocked(layer)"
                      :aria-label="`${layerIsLocked(layer) ? 'Unlock' : 'Lock'} layer ${index + 1}`"
                      :title="layerIsLocked(layer) ? 'Unlock layer' : 'Lock layer'"
                      @click="toggleLayerLock(index)"
                    >
                      <component
                        :is="layerIsLocked(layer) ? Lock : Unlock"
                        :size="16"
                        :stroke-width="1.8"
                        aria-hidden="true"
                      />
                    </button>
                  </div>
                </div>
              </li>
            </ol>
            <p v-if="displayedCanvasLayers.length === 0" class="muted">No layers</p>
          </section>
          <section class="editor-section">
            <h2>Assets</h2>
            <p class="muted">No uploaded assets</p>
          </section>
        </aside>

        <section
          class="editor-workspace"
          aria-label="Card canvas workspace"
          @pointerdown.self="clearLayerSelection"
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
          <CanvasPreview
            :layers="displayedCanvasLayers"
            :width-mm="design.widthMm"
            :height-mm="design.heightMm"
            label="Draft canvas preview"
            :empty-label="selectedPageLabel"
            :selected-layer-index="selectedLayerIndex"
            :selected-layer-indexes="selectedLayerIndexes"
            :resizable-layer-index="resizableLayerIndex"
            interactive
            @canvas-pointerdown="clearLayerSelection"
            @layer-pointerdown="startLayerDrag"
            @layer-resize-pointerdown="startLayerResize"
            @layer-select="selectLayer"
          />
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
          </section>

          <div v-if="firstTextLayerIndex >= 0" class="editor-panel">
            <label for="editor-text-layer">Text layer</label>
            <textarea
              id="editor-text-layer"
              v-model="firstTextLayerText"
              maxlength="120"
              rows="5"
              :disabled="firstTextLayerLocked"
            />
          </div>
          <p v-else class="muted">No text layer.</p>

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
          {{ canvasLayerCount }} layer<span v-if="canvasLayerCount !== 1">s</span>.
        </p>

        <div class="detail-actions">
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

        <div v-if="firstTextLayerIndex >= 0" class="editor-panel">
          <label for="draft-text-layer">Text layer</label>
          <textarea
            id="draft-text-layer"
            v-model="firstTextLayerText"
            maxlength="120"
            rows="4"
          />
        </div>
        <p v-else class="muted">No text layer.</p>
      </div>
    </article>
  </section>
</template>
