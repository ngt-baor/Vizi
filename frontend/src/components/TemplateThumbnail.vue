<script setup lang="ts">
import { computed } from "vue";
import CanvasPreview from "./CanvasPreview.vue";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
  page?: string;
};

const props = defineProps<{
  name: string;
  previewUrl: string | null;
  canvasJson: string;
  widthMm: number;
  heightMm: number;
}>();

function parseFrontLayers(canvasJson: string): CanvasLayer[] {
  try {
    const canvas = JSON.parse(canvasJson) as { layers?: unknown };
    if (!Array.isArray(canvas.layers)) {
      return [];
    }
    return canvas.layers.filter((layer): layer is CanvasLayer => {
      if (typeof layer !== "object" || layer === null) {
        return false;
      }
      return (layer as CanvasLayer).page !== "back";
    });
  } catch {
    return [];
  }
}

/** Prefer live canvas when it has real design layers (text/shape), not only a flat image paste. */
function hasEditablePreviewLayers(layers: CanvasLayer[]): boolean {
  if (layers.length === 0) {
    return false;
  }
  return layers.some((layer) => {
    const type = typeof layer.type === "string" ? layer.type : "";
    return type === "text" || type === "rect" || type === "ellipse" || type === "icon" || type === "shape";
  });
}

const frontLayers = computed(() => parseFrontLayers(props.canvasJson));
const useCanvasPreview = computed(() => hasEditablePreviewLayers(frontLayers.value));
const usePreviewImage = computed(() => Boolean(props.previewUrl) && !useCanvasPreview.value);
</script>

<template>
  <div class="template-preview template-card-preview" aria-hidden="true">
    <img
      v-if="usePreviewImage"
      :src="previewUrl!"
      :alt="name"
      loading="lazy"
      referrerpolicy="no-referrer"
    />
    <div
      v-else
      class="template-card-stage"
      :style="{ aspectRatio: `${widthMm} / ${heightMm}` }"
    >
      <CanvasPreview
        :layers="frontLayers"
        :width-mm="widthMm"
        :height-mm="heightMm"
        :label="`${name} preview`"
        empty-label=""
      />
    </div>
  </div>
</template>
