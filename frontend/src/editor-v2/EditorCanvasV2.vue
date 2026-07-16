<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import type { CSSProperties } from "vue";
import type { EditorDocumentV2, EditorLayerV2, EditorPageV2 } from "./document";

type LayerMovePayload = {
  layerId: string;
  x: number;
  y: number;
};

type DragState = {
  layerId: string;
  pointerId: number;
  startClientX: number;
  startClientY: number;
  originX: number;
  originY: number;
  layerWidth: number;
  layerHeight: number;
  canvasLeft: number;
  canvasTop: number;
  canvasWidth: number;
  canvasHeight: number;
};

const props = defineProps<{
  document: EditorDocumentV2;
  page: EditorPageV2;
  zoom: number;
  selectedLayerId?: string | null;
}>();

const emit = defineEmits<{
  "select-layer": [layerId: string | null];
  "move-layer": [payload: LayerMovePayload];
}>();

const dragState = ref<DragState | null>(null);

const canvasStyle = computed<CSSProperties>(() => ({
  aspectRatio: `${props.document.card.widthMm} / ${props.document.card.heightMm}`,
  background: props.page.background,
  transform: `scale(${props.zoom / 100})`,
}));

function safeImageSource(src: string | undefined): string {
  return src && (/^https?:\/\//.test(src) || src.startsWith("/")) ? src : "";
}

function layerStyle(layer: EditorLayerV2): CSSProperties {
  return {
    left: `${layer.x}%`,
    top: `${layer.y}%`,
    width: `${layer.width}%`,
    height: `${layer.height}%`,
    opacity: layer.opacity,
    transform: `rotate(${layer.rotation}deg)`,
    color: layer.type === "text" ? layer.fill : undefined,
    background: layer.type === "rect" || layer.type === "ellipse" ? layer.fill : undefined,
    border: layer.strokeWidth
      ? `${layer.strokeWidth}px solid ${layer.stroke ?? "transparent"}`
      : undefined,
    borderRadius: layer.type === "ellipse" ? "50%" : `${layer.cornerRadius ?? 0}px`,
    fontFamily: layer.fontFamily,
    fontSize: layer.fontSize ? `${layer.fontSize}px` : undefined,
    fontWeight: layer.fontWeight,
    textAlign: layer.textAlign,
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function roundPosition(value: number): number {
  return Math.round(value * 10) / 10;
}

function handlePointerDown(event: PointerEvent, layer: EditorLayerV2): void {
  emit("select-layer", layer.id);
  const target = event.currentTarget as HTMLElement;
  if (layer.locked) {
    target.setPointerCapture?.(event.pointerId);
    return;
  }

  const canvas = target.closest("[data-editor-v2-canvas]");
  const canvasRect = canvas?.getBoundingClientRect();
  if (!canvasRect || canvasRect.width <= 0 || canvasRect.height <= 0) return;

  dragState.value = {
    layerId: layer.id,
    pointerId: event.pointerId,
    startClientX: event.clientX,
    startClientY: event.clientY,
    originX: layer.x,
    originY: layer.y,
    layerWidth: layer.width,
    layerHeight: layer.height,
    canvasLeft: canvasRect.left,
    canvasTop: canvasRect.top,
    canvasWidth: canvasRect.width,
    canvasHeight: canvasRect.height,
  };

  target.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", handlePointerMove);
  window.addEventListener("pointerup", handlePointerUp);
  window.addEventListener("pointercancel", handlePointerUp);
  event.preventDefault();
}

function handlePointerMove(event: PointerEvent): void {
  const state = dragState.value;
  if (!state || event.pointerId !== state.pointerId) return;

  const deltaX = ((event.clientX - state.startClientX) / state.canvasWidth) * 100;
  const deltaY = ((event.clientY - state.startClientY) / state.canvasHeight) * 100;
  emit("move-layer", {
    layerId: state.layerId,
    x: roundPosition(clamp(state.originX + deltaX, 0, 100 - state.layerWidth)),
    y: roundPosition(clamp(state.originY + deltaY, 0, 100 - state.layerHeight)),
  });
}

function handlePointerUp(event: PointerEvent): void {
  if (!dragState.value || event.pointerId !== dragState.value.pointerId) return;
  stopDragging();
}

function stopDragging(): void {
  window.removeEventListener("pointermove", handlePointerMove);
  window.removeEventListener("pointerup", handlePointerUp);
  window.removeEventListener("pointercancel", handlePointerUp);
  dragState.value = null;
}

onBeforeUnmount(stopDragging);
</script>

<template>
  <div
    class="v2-canvas"
    :style="canvasStyle"
    :aria-label="`${page.name} card canvas`"
    data-editor-v2-canvas
    @click="emit('select-layer', null)"
  >
    <div
      v-for="layer in page.layers"
      v-show="layer.visible"
      :key="layer.id"
      class="v2-layer"
      :class="[
        `v2-layer--${layer.type}`,
        {
          'v2-layer--selected': layer.id === props.selectedLayerId,
          'v2-layer--dragging': layer.id === dragState?.layerId,
          'v2-layer--locked': layer.locked,
        },
      ]"
      :style="layerStyle(layer)"
      :data-layer-id="layer.id"
      @pointerdown.stop="handlePointerDown($event, layer)"
      @click.stop="emit('select-layer', layer.id)"
    >
      <span v-if="layer.type === 'text'">{{ layer.content ?? "" }}</span>
      <img
        v-else-if="layer.type === 'image' && safeImageSource(layer.src)"
        :src="safeImageSource(layer.src)"
        :alt="layer.name"
      >
    </div>
  </div>
</template>

<style scoped>
.v2-canvas {
  position: relative;
  width: min(720px, calc(100vw - 690px));
  min-width: 360px;
  overflow: hidden;
  border: 1px solid rgba(31, 33, 40, 0.18);
  border-radius: 3px;
  box-shadow: 0 20px 50px rgba(39, 40, 48, 0.18);
  transform-origin: center;
  transition: transform 160ms ease;
}

.v2-layer {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.v2-layer--dragging {
  cursor: grabbing;
  transition: none;
}

.v2-layer--locked {
  cursor: not-allowed;
}

.v2-layer--selected {
  outline: 2px solid #b4367d;
  outline-offset: 2px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.92);
}

.v2-layer--text {
  display: flex;
  align-items: center;
  line-height: 1.12;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.v2-layer--text span {
  width: 100%;
}

.v2-layer--image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 1180px) {
  .v2-canvas {
    width: min(640px, calc(100vw - 620px));
    min-width: 320px;
  }
}

@media (max-width: 900px) {
  .v2-canvas {
    width: min(78vw, 680px);
    min-width: 0;
  }
}
</style>
