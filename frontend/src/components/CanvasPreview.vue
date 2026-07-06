<script setup lang="ts">
import { computed } from "vue";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};

const props = defineProps<{
  layers: CanvasLayer[];
  widthMm: number;
  heightMm: number;
  label: string;
  emptyLabel: string;
}>();

const frameStyle = computed(() => ({
  aspectRatio: `${props.widthMm} / ${props.heightMm}`,
}));

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
  const fontSize = numberValue(layer.fontSize, 14);

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
    fontSize: layer.type === "text"
      ? `min(${fontSize}px, ${(fontSize / 5.2).toFixed(4)}cqw)`
      : `${fontSize}px`,
    fontWeight: numberValue(layer.fontWeight, 700),
    opacity: numberValue(layer.opacity, 1),
  };
}
</script>

<template>
  <div class="canvas-frame" :style="frameStyle" :aria-label="label">
    <div
      v-for="(layer, index) in layers"
      :key="index"
      class="canvas-layer"
      :class="layerClass(layer)"
      :style="layerStyle(layer)"
    >
      <img
        v-if="layer.type === 'image' && layerImageSource(layer)"
        :src="layerImageSource(layer)"
        :alt="layerText(layer)"
      />
      <template v-else>
        {{ layerText(layer) }}
      </template>
    </div>
    <span v-if="layers.length === 0" class="canvas-empty">{{ emptyLabel }}</span>
  </div>
</template>
