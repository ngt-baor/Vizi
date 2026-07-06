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
  selectedLayerIndex?: number | null;
  selectedLayerIndexes?: number[];
  resizableLayerIndex?: number | null;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  canvasPointerdown: [event: PointerEvent];
  layerPointerdown: [index: number, event: PointerEvent];
  layerResizePointerdown: [index: number, event: PointerEvent];
  layerSelect: [index: number, event: KeyboardEvent];
}>();

const frameStyle = computed(() => ({
  aspectRatio: `${props.widthMm} / ${props.heightMm}`,
  "--canvas-width-mm": props.widthMm,
  "--canvas-height-mm": props.heightMm,
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

function layerIsVisible(layer: CanvasLayer): boolean {
  return layer.visible !== false && layer.hidden !== true;
}

function layerIsSelected(index: number): boolean {
  return props.selectedLayerIndexes
    ? props.selectedLayerIndexes.includes(index)
    : index === props.selectedLayerIndex;
}

function layerStyle(layer: CanvasLayer): Record<string, string | number> {
  const x = numberValue(layer.x, 8);
  const y = numberValue(layer.y, 8);
  const width = numberValue(layer.width, layer.type === "text" ? 45 : 32);
  const height = numberValue(layer.height, layer.type === "text" ? 16 : 26);
  const fontSize = numberValue(layer.fontSize, 14);
  const stroke = stringValue(layer.stroke, "rgba(122, 93, 46, 0.18)");
  const strokeWidth = numberValue(layer.strokeWidth, 1);

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
    border: layer.type === "text" ? "0" : `${strokeWidth}px solid ${stroke}`,
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
  <div
    class="canvas-frame"
    :class="{ 'canvas-frame--interactive': interactive }"
    :style="frameStyle"
    :aria-label="label"
    @pointerdown.self="emit('canvasPointerdown', $event)"
  >
    <template v-for="(layer, index) in layers" :key="index">
      <div
        v-if="layerIsVisible(layer)"
        class="canvas-layer"
        :class="[layerClass(layer), { 'canvas-layer--selected': layerIsSelected(index) }]"
        :style="layerStyle(layer)"
        :role="interactive ? 'button' : undefined"
        :tabindex="interactive ? 0 : undefined"
        :aria-label="interactive ? `Select layer ${index + 1} ${layer.type || 'Layer'}` : undefined"
        @pointerdown.stop="interactive && emit('layerPointerdown', index, $event)"
        @keydown.enter.prevent="interactive && emit('layerSelect', index, $event)"
        @keydown.space.prevent="interactive && emit('layerSelect', index, $event)"
      >
        <img
          v-if="layer.type === 'image' && layerImageSource(layer)"
          :src="layerImageSource(layer)"
          :alt="layerText(layer)"
        />
        <template v-else>
          {{ layerText(layer) }}
        </template>
        <button
          v-if="interactive && index === resizableLayerIndex"
          type="button"
          class="canvas-resize-handle"
          :aria-label="`Resize layer ${index + 1}`"
          title="Resize layer"
          @pointerdown.stop.prevent="emit('layerResizePointerdown', index, $event)"
        />
      </div>
    </template>
    <span v-if="layers.length === 0" class="canvas-empty">{{ emptyLabel }}</span>
  </div>
</template>
