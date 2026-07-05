<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { getTemplate, type TemplateDetail } from "../api";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};

const route = useRoute();
const template = ref<TemplateDetail | null>(null);
const loading = ref(true);
const error = ref("");

const templateId = computed(() => Number(route.params.id));
const canvasLayers = computed<CanvasLayer[]>(() => {
  if (!template.value) {
    return [];
  }

  try {
    const canvas = JSON.parse(template.value.canvasJson) as { layers?: unknown };
    return Array.isArray(canvas.layers) ? canvas.layers.filter(isCanvasLayer) : [];
  } catch {
    return [];
  }
});
const canvasLayerCount = computed(() => canvasLayers.value.length);
const canvasFrameStyle = computed(() => {
  if (!template.value) {
    return {};
  }

  return {
    aspectRatio: `${template.value.widthMm} / ${template.value.heightMm}`,
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

function layerText(layer: CanvasLayer): string {
  return stringValue(layer.text ?? layer.value ?? layer.name, stringValue(layer.type, "Layer"));
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

onMounted(async () => {
  if (!Number.isFinite(templateId.value)) {
    error.value = "Template id is invalid";
    loading.value = false;
    return;
  }

  try {
    template.value = await getTemplate(templateId.value);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load template";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="detail-view">
    <RouterLink class="back-link" to="/">Back to templates</RouterLink>

    <p v-if="loading" class="muted">Loading template...</p>
    <p v-else-if="error" class="error-text">{{ error }}</p>

    <article v-else-if="template" class="detail-shell">
      <div class="detail-preview">
        <div
          class="canvas-frame"
          :style="canvasFrameStyle"
          aria-label="Template canvas preview"
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
          <span v-if="canvasLayers.length === 0" class="canvas-empty">{{ template.category }}</span>
        </div>
      </div>

      <div class="detail-content">
        <p class="eyebrow">{{ template.category }}</p>
        <h1>{{ template.name }}</h1>
        <p class="summary">
          {{ template.widthMm }} x {{ template.heightMm }} mm canvas with
          {{ canvasLayerCount }} layer<span v-if="canvasLayerCount !== 1">s</span>.
        </p>

        <div class="detail-actions">
          <button class="primary-action" type="button" disabled>
            Open editor
          </button>
          <span class="muted">Editing starts in step 33.</span>
        </div>
      </div>
    </article>
  </section>
</template>
