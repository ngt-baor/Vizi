<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import {
  createDesignFromTemplate,
  getCurrentUser,
  getTemplate,
  updateDesign,
  type TemplateDetail,
} from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};

const route = useRoute();
const router = useRouter();
const template = ref<TemplateDetail | null>(null);
const loading = ref(true);
const error = ref("");
const editableLayers = ref<CanvasLayer[]>([]);
const savedDesignId = ref<number | null>(null);
const saving = ref(false);
const saveMessage = ref("");
const saveError = ref("");

const templateId = computed(() => Number(route.params.id));
const canvasLayers = computed<CanvasLayer[]>(() => editableLayers.value.filter(isFrontLayer));
const canvasLayerCount = computed(() => canvasLayers.value.length);
const firstTextLayerIndex = computed(() =>
  editableLayers.value.findIndex((layer) => isFrontLayer(layer) && layer.type === "text"),
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
function isCanvasLayer(layer: unknown): layer is CanvasLayer {
  return typeof layer === "object" && layer !== null;
}

function isFrontLayer(layer: CanvasLayer): boolean {
  return layer.page !== "back";
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
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
    const canvas = JSON.parse(template.value?.canvasJson ?? "{}");
    if (typeof canvas === "object" && canvas !== null && !Array.isArray(canvas)) {
      return JSON.stringify({ ...canvas, layers: editableLayers.value });
    }
  } catch {
    // Invalid template JSON falls back to the validated canvas shape below.
  }
  return JSON.stringify({ layers: editableLayers.value });
}

async function saveDraft(): Promise<void> {
  if (!template.value || saving.value) {
    return;
  }

  saveMessage.value = "";
  saveError.value = "";

  try {
    if (savedDesignId.value === null) {
      const currentUser = await getCurrentUser();
      if (!currentUser) {
        await router.push({ name: "account", query: { redirect: route.fullPath } });
        return;
      }
    }

    saving.value = true;

    if (savedDesignId.value === null) {
      const created = await createDesignFromTemplate(template.value.id);
      savedDesignId.value = created.id;
    }
    const saved = await updateDesign(
      savedDesignId.value,
      template.value.name,
      serializeCanvas(),
    );
    saveMessage.value = `Draft #${saved.id} saved`;
  } catch (unknownError) {
    saveError.value = unknownError instanceof Error ? unknownError.message : "Cannot save draft";
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  if (!Number.isFinite(templateId.value)) {
    error.value = "Template id is invalid";
    loading.value = false;
    return;
  }

  try {
    template.value = await getTemplate(templateId.value);
    editableLayers.value = parseCanvasLayers(template.value.canvasJson);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load template";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="detail-view">
    <RouterLink class="back-link" to="/templates">Back to templates</RouterLink>

    <p v-if="loading" class="muted">Loading template...</p>
    <p v-else-if="error" class="error-text">{{ error }}</p>

    <article v-else-if="template" class="detail-shell">
      <div class="detail-preview">
        <CanvasPreview
          :layers="canvasLayers"
          :width-mm="template.widthMm"
          :height-mm="template.heightMm"
          label="Template canvas preview"
          :empty-label="template.category"
        />
      </div>

      <div class="detail-content">
        <p class="eyebrow">{{ template.category }}</p>
        <h1>{{ template.name }}</h1>
        <p class="summary">
          {{ template.widthMm }} x {{ template.heightMm }} mm canvas with
          {{ canvasLayerCount }} {{ canvasLayerCount === 1 ? "layer" : "layers" }}.
        </p>

        <div class="detail-actions">
          <button class="primary-action" type="button" :disabled="saving" @click="saveDraft">
            {{ saving ? "Saving..." : "Save draft" }}
          </button>
          <RouterLink
            v-if="savedDesignId !== null"
            class="secondary-action"
            :to="{ name: 'editor', params: { designId: savedDesignId } }"
          >
            Open editor
          </RouterLink>
          <span v-if="saveMessage" class="save-status" role="status">{{ saveMessage }}</span>
          <span v-else-if="!saveError" class="muted">
            {{ savedDesignId === null ? "Local draft" : "Unsaved changes" }}
          </span>
        </div>
        <p v-if="saveError" class="error-text" role="alert">
          {{ saveError }}
          <RouterLink v-if="saveError.includes('Sign in')" to="/account">Open account</RouterLink>
        </p>

        <div v-if="firstTextLayerIndex >= 0" class="editor-panel">
          <label for="first-text-layer">Text layer</label>
          <textarea
            id="first-text-layer"
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
