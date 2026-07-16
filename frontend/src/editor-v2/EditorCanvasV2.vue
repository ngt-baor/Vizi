<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import type { CSSProperties } from "vue";
import type { EditorDocumentV2, EditorLayerV2, EditorPageV2 } from "./document";

type LayerMovePayload = {
  layerId: string;
  x: number;
  y: number;
};

type LayerResizePayload = {
  layerId: string;
  width: number;
  height: number;
};

type LayerRotatePayload = {
  layerId: string;
  rotation: number;
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

type ResizeState = {
  layerId: string;
  pointerId: number;
  startClientX: number;
  startClientY: number;
  originWidth: number;
  originHeight: number;
  layerX: number;
  layerY: number;
  canvasWidth: number;
  canvasHeight: number;
};

type RotateState = {
  layerId: string;
  pointerId: number;
  originRotation: number;
  startAngle: number;
  centerX: number;
  centerY: number;
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
  "resize-layer": [payload: LayerResizePayload];
  "rotate-layer": [payload: LayerRotatePayload];
}>();

const dragState = ref<DragState | null>(null);
const resizeState = ref<ResizeState | null>(null);
const rotateState = ref<RotateState | null>(null);

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

function handleResizePointerDown(event: PointerEvent, layer: EditorLayerV2): void {
  if (layer.locked) return;

  const target = event.currentTarget as HTMLElement;
  const canvas = target.closest("[data-editor-v2-canvas]");
  const canvasRect = canvas?.getBoundingClientRect();
  if (!canvasRect || canvasRect.width <= 0 || canvasRect.height <= 0) return;

  resizeState.value = {
    layerId: layer.id,
    pointerId: event.pointerId,
    startClientX: event.clientX,
    startClientY: event.clientY,
    originWidth: layer.width,
    originHeight: layer.height,
    layerX: layer.x,
    layerY: layer.y,
    canvasWidth: canvasRect.width,
    canvasHeight: canvasRect.height,
  };

  target.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", handleResizePointerMove);
  window.addEventListener("pointerup", handleResizePointerUp);
  window.addEventListener("pointercancel", handleResizePointerUp);
  event.preventDefault();
}

function handleResizePointerMove(event: PointerEvent): void {
  const state = resizeState.value;
  if (!state || event.pointerId !== state.pointerId) return;

  const maxWidth = Math.max(0.1, 100 - state.layerX);
  const maxHeight = Math.max(0.1, 100 - state.layerY);
  const minWidth = Math.min(2, maxWidth);
  const minHeight = Math.min(2, maxHeight);
  const deltaWidth = ((event.clientX - state.startClientX) / state.canvasWidth) * 100;
  const deltaHeight = ((event.clientY - state.startClientY) / state.canvasHeight) * 100;
  emit("resize-layer", {
    layerId: state.layerId,
    width: roundPosition(clamp(state.originWidth + deltaWidth, minWidth, maxWidth)),
    height: roundPosition(clamp(state.originHeight + deltaHeight, minHeight, maxHeight)),
  });
}

function handleResizePointerUp(event: PointerEvent): void {
  if (!resizeState.value || event.pointerId !== resizeState.value.pointerId) return;
  stopResizing();
}

function stopResizing(): void {
  window.removeEventListener("pointermove", handleResizePointerMove);
  window.removeEventListener("pointerup", handleResizePointerUp);
  window.removeEventListener("pointercancel", handleResizePointerUp);
  resizeState.value = null;
}

function handleRotatePointerDown(event: PointerEvent, layer: EditorLayerV2): void {
  if (layer.locked) return;

  const target = event.currentTarget as HTMLElement;
  const layerElement = target.closest("[data-layer-id]");
  const layerRect = layerElement?.getBoundingClientRect();
  if (!layerRect || layerRect.width <= 0 || layerRect.height <= 0) return;

  const centerX = layerRect.left + layerRect.width / 2;
  const centerY = layerRect.top + layerRect.height / 2;
  rotateState.value = {
    layerId: layer.id,
    pointerId: event.pointerId,
    originRotation: layer.rotation,
    startAngle: pointerAngle(event.clientX, event.clientY, centerX, centerY),
    centerX,
    centerY,
  };

  target.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", handleRotatePointerMove);
  window.addEventListener("pointerup", handleRotatePointerUp);
  window.addEventListener("pointercancel", handleRotatePointerUp);
  event.preventDefault();
}

function pointerAngle(clientX: number, clientY: number, centerX: number, centerY: number): number {
  return Math.atan2(clientY - centerY, clientX - centerX) * (180 / Math.PI);
}

function roundRotation(value: number): number {
  const normalized = ((value % 360) + 360) % 360;
  return Math.round(normalized * 10) / 10;
}

function handleRotatePointerMove(event: PointerEvent): void {
  const state = rotateState.value;
  if (!state || event.pointerId !== state.pointerId) return;

  const currentAngle = pointerAngle(event.clientX, event.clientY, state.centerX, state.centerY);
  let delta = currentAngle - state.startAngle;
  if (delta > 180) delta -= 360;
  if (delta < -180) delta += 360;
  emit("rotate-layer", {
    layerId: state.layerId,
    rotation: roundRotation(state.originRotation + delta),
  });
}

function handleRotatePointerUp(event: PointerEvent): void {
  if (!rotateState.value || event.pointerId !== rotateState.value.pointerId) return;
  stopRotating();
}

function stopRotating(): void {
  window.removeEventListener("pointermove", handleRotatePointerMove);
  window.removeEventListener("pointerup", handleRotatePointerUp);
  window.removeEventListener("pointercancel", handleRotatePointerUp);
  rotateState.value = null;
}
onBeforeUnmount(() => {
  stopDragging();
  stopResizing();
  stopRotating();
});
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
          'v2-layer--resizing': layer.id === resizeState?.layerId,
          'v2-layer--rotating': layer.id === rotateState?.layerId,
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
      <button
        v-if="layer.id === props.selectedLayerId && !layer.locked"
        class="v2-resize-handle"
        type="button"
        :aria-label="`Resize ${layer.name}`"
        title="Resize layer"
        @pointerdown.stop="handleResizePointerDown($event, layer)"
        @click.stop
      />
      <button
        v-if="layer.id === props.selectedLayerId && !layer.locked"
        class="v2-rotate-handle"
        type="button"
        :aria-label="`Rotate ${layer.name}`"
        title="Rotate layer"
        @pointerdown.stop="handleRotatePointerDown($event, layer)"
        @click.stop
      />
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

.v2-layer--resizing {
  cursor: nwse-resize;
}

.v2-layer--rotating {
  cursor: crosshair;
}

.v2-layer--locked {
  cursor: not-allowed;
}

.v2-layer--selected {
  outline: 2px solid #b4367d;
  outline-offset: 2px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.92);
}

.v2-resize-handle {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 14px;
  height: 14px;
  padding: 0;
  border: 2px solid #ffffff;
  border-radius: 2px;
  background: #b4367d;
  box-shadow: 0 0 0 1px rgba(38, 38, 45, 0.32);
  cursor: nwse-resize;
  touch-action: none;
}

.v2-rotate-handle {
  position: absolute;
  top: 2px;
  left: calc(50% - 7px);
  width: 14px;
  height: 14px;
  padding: 0;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #0d766d;
  box-shadow: 0 0 0 1px rgba(38, 38, 45, 0.32);
  cursor: crosshair;
  touch-action: none;
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
