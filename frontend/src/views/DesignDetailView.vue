<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { deleteDesign, getDesign, updateDesign, type DesignDetail } from "../api";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
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

const designId = computed(() => Number(route.params.id));
const canvasLayers = computed<CanvasLayer[]>(() => editableLayers.value);
const canvasLayerCount = computed(() => canvasLayers.value.length);
const firstTextLayerIndex = computed(() =>
  editableLayers.value.findIndex((layer) => layer.type === "text"),
);
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
const canvasFrameStyle = computed(() => {
  if (!design.value) {
    return {};
  }

  return {
    aspectRatio: `${design.value.widthMm} / ${design.value.heightMm}`,
  };
});

function isCanvasLayer(layer: unknown): layer is CanvasLayer {
  return typeof layer === "object" && layer !== null;
}

function numberValue(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function stringValue(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() ? value : fallback;
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function layerText(layer: CanvasLayer): string {
  return optionalString(layer.text ?? layer.value)
    ?? stringValue(layer.name, stringValue(layer.type, "Layer"));
}

function layerImageSource(layer: CanvasLayer): string {
  const source = stringValue(layer.src ?? layer.url ?? layer.imageUrl, "");
  return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("/")
    ? source
    : "";
}

function layerClass(layer: CanvasLayer): string {
  const type = stringValue(layer.type, "unknown");
  return ["text", "rect", "shape", "image"].includes(type)
    ? `canvas-layer--${type}`
    : "canvas-layer--unknown";
}

function layerStyle(layer: CanvasLayer): Record<string, string | number> {
  const x = numberValue(layer.x, 8);
  const y = numberValue(layer.y, 8);
  const width = numberValue(layer.width, layer.type === "text" ? 45 : 32);
  const height = numberValue(layer.height, layer.type === "text" ? 16 : 26);

  return {
    left: `${x}%`,
    top: `${y}%`,
    width: `${width}%`,
    height: `${height}%`,
    color: stringValue(layer.color, "#2f281c"),
    background: stringValue(
      layer.fill ?? layer.background,
      layer.type === "text" ? "transparent" : "rgba(255,255,255,0.72)",
    ),
    borderRadius: `${numberValue(layer.radius, 10)}px`,
    fontFamily: stringValue(layer.fontFamily, "inherit"),
    fontSize: `${numberValue(layer.fontSize, 14)}px`,
    fontWeight: numberValue(layer.fontWeight, 700),
    opacity: numberValue(layer.opacity, 1),
  };
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
  <section class="detail-view">
    <RouterLink class="back-link" to="/designs">Back to drafts</RouterLink>

    <p v-if="loading" class="muted">Loading draft...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>

    <article v-else-if="design" class="detail-shell">
      <div class="detail-preview">
        <div
          class="canvas-frame"
          :style="canvasFrameStyle"
          aria-label="Draft canvas preview"
        >
          <div
            v-for="(layer, index) in canvasLayers"
            :key="index"
            class="canvas-layer"
            :class="layerClass(layer)"
            :style="layerStyle(layer)"
          >
            <template v-if="layer.type === 'image' && layerImageSource(layer)">
              <img :src="layerImageSource(layer)" :alt="layerText(layer)" />
            </template>
            <template v-else-if="layer.type === 'text'">
              {{ layerText(layer) }}
            </template>
            <template v-else>
              {{ layerText(layer) }}
            </template>
          </div>
          <span v-if="canvasLayers.length === 0" class="canvas-empty">Draft</span>
        </div>
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
