<script setup lang="ts">
import { ArrowDown, ArrowUp, Copy, LockKeyhole, Trash2 } from "@lucide/vue";
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
  x: number;
  y: number;
  width: number;
  height: number;
};

type LayerRotatePayload = {
  layerId: string;
  rotation: number;
};

type LayerActionPayload = {
  layerId: string;
  action: "duplicate" | "toggle-lock" | "bring-forward" | "send-backward" | "delete";
};

type ResizeHandle = "nw" | "n" | "ne" | "e" | "se" | "s" | "sw" | "w";

type DragState = {
  layerId: string;
  pointerId: number;
  startClientX: number;
  startClientY: number;
  originX: number;
  originY: number;
  layerWidth: number;
  layerHeight: number;
  canvasWidth: number;
  canvasHeight: number;
};

type ResizeState = {
  layerId: string;
  pointerId: number;
  startClientX: number;
  startClientY: number;
  handle: ResizeHandle;
  rotation: number;
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
  "layer-action": [payload: LayerActionPayload];
}>();

const dragState = ref<DragState | null>(null);
const resizeState = ref<ResizeState | null>(null);
const rotateState = ref<RotateState | null>(null);
const selectedLayer = computed(() => props.page.layers.find((layer) => layer.id === props.selectedLayerId) ?? null);
const resizeHandles: ResizeHandle[] = ["nw", "n", "ne", "e", "se", "s", "sw", "w"];

const canvasStyle = computed<CSSProperties>(() => ({
  aspectRatio: String(props.document.card.widthMm) + " / " + String(props.document.card.heightMm),
  background: props.page.background,
  transform: "scale(" + String(props.zoom / 100) + ")",
}));

function safeImageSource(src: string | undefined): string {
  return src && (/^https?:\/\//.test(src) || src.startsWith("/") || /^data:image\/(png|jpeg|webp|gif);base64,/.test(src)) ? src : "";
}

function layerStyle(layer: EditorLayerV2): CSSProperties {
  return {
    left: String(layer.x) + "%",
    top: String(layer.y) + "%",
    width: String(layer.width) + "%",
    height: String(layer.height) + "%",
    opacity: layer.opacity,
    transform: "rotate(" + String(layer.rotation) + "deg)",
    color: layer.type === "text" ? layer.fill : undefined,
    background: layer.type === "rect" || layer.type === "ellipse" ? layer.fill : undefined,
    border: layer.strokeWidth
      ? String(layer.strokeWidth) + "px solid " + (layer.stroke ?? "transparent")
      : undefined,
    borderRadius: layer.type === "ellipse" ? "50%" : String(layer.cornerRadius ?? 0) + "px",
    fontFamily: layer.fontFamily,
    fontSize: layer.fontSize ? String(layer.fontSize) + "px" : undefined,
    fontWeight: layer.fontWeight,
    textAlign: layer.textAlign,
  };
}

function selectionStyle(layer: EditorLayerV2): CSSProperties {
  return {
    left: String(layer.x) + "%",
    top: String(layer.y) + "%",
    width: String(layer.width) + "%",
    height: String(layer.height) + "%",
    transform: "rotate(" + String(layer.rotation) + "deg)",
  };
}

function chromePositionStyle(layer: EditorLayerV2, placement: "toolbar" | "badge"): CSSProperties {
  const radians = (layer.rotation * Math.PI) / 180;
  const canvasRatio = props.document.card.widthMm / props.document.card.heightMm;
  const halfHeight = (Math.abs(layer.width * canvasRatio * Math.sin(radians))
    + Math.abs(layer.height * Math.cos(radians))) / 2;
  const centerY = layer.y + layer.height / 2;
  const edgeY = placement === "toolbar" ? centerY - halfHeight : centerY + halfHeight;
  return {
    left: String(layer.x + layer.width / 2) + "%",
    top: placement === "toolbar"
      ? "calc(" + String(edgeY) + "% - 88px)"
      : "calc(" + String(edgeY) + "% + 12px)",
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

function handleResizePointerDown(event: PointerEvent, layer: EditorLayerV2, handle: ResizeHandle): void {
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
    handle,
    rotation: layer.rotation,
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

  const radians = (-state.rotation * Math.PI) / 180;
  const deltaPixelsX = event.clientX - state.startClientX;
  const deltaPixelsY = event.clientY - state.startClientY;
  const localPixelsX = deltaPixelsX * Math.cos(radians) - deltaPixelsY * Math.sin(radians);
  const localPixelsY = deltaPixelsX * Math.sin(radians) + deltaPixelsY * Math.cos(radians);
  const deltaX = (localPixelsX / state.canvasWidth) * 100;
  const deltaY = (localPixelsY / state.canvasHeight) * 100;
  const maxWidth = Math.max(0.1, 100 - state.layerX);
  const maxHeight = Math.max(0.1, 100 - state.layerY);
  const minWidth = Math.min(2, maxWidth);
  const minHeight = Math.min(2, maxHeight);
  let nextX = state.layerX;
  let nextY = state.layerY;
  let nextWidth = state.originWidth;
  let nextHeight = state.originHeight;

  if (state.handle.includes("e")) {
    nextWidth = clamp(state.originWidth + deltaX, minWidth, maxWidth);
  }
  if (state.handle.includes("s")) {
    nextHeight = clamp(state.originHeight + deltaY, minHeight, maxHeight);
  }
  if (state.handle.includes("w")) {
    nextX = clamp(state.layerX + deltaX, 0, state.layerX + state.originWidth - minWidth);
    nextWidth = state.originWidth + state.layerX - nextX;
  }
  if (state.handle.includes("n")) {
    nextY = clamp(state.layerY + deltaY, 0, state.layerY + state.originHeight - minHeight);
    nextHeight = state.originHeight + state.layerY - nextY;
  }

  emit("resize-layer", {
    layerId: state.layerId,
    x: roundPosition(nextX),
    y: roundPosition(nextY),
    width: roundPosition(nextWidth),
    height: roundPosition(nextHeight),
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
  const selectionBox = target.closest(".v2-selection-box");
  const layerRect = selectionBox?.getBoundingClientRect();
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
    :aria-label="page.name + ' card canvas'"
    data-editor-v2-canvas
    @click="emit('select-layer', null)"
  >
    <div
      v-for="layer in page.layers"
      v-show="layer.visible"
      :key="layer.id"
      class="v2-layer"
      :class="[
        'v2-layer--' + layer.type,
        {
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
      <div class="v2-layer__content">
        <span v-if="layer.type === 'text'">{{ layer.content ?? "" }}</span>
        <img
          v-else-if="layer.type === 'image' && safeImageSource(layer.src)"
          :src="safeImageSource(layer.src)"
          :alt="layer.name"
        >
      </div>
    </div>

    <div
      v-if="selectedLayer && selectedLayer.visible"
      class="v2-selection-box"
      data-selection-box
      :style="selectionStyle(selectedLayer)"
    >
      <div class="v2-selection-border" aria-hidden="true" />
      <template v-if="!selectedLayer.locked">
        <button
          v-for="handle in resizeHandles"
          :key="handle"
          class="v2-selection-handle"
          :class="'v2-selection-handle--' + handle"
          type="button"
          :aria-label="'Resize ' + selectedLayer.name + ' ' + handle"
          title="Resize layer"
          @pointerdown.stop="handleResizePointerDown($event, selectedLayer, handle)"
          @click.stop
        />
        <button
          class="v2-rotate-handle"
          type="button"
          :aria-label="'Rotate ' + selectedLayer.name"
          title="Rotate layer"
          @pointerdown.stop="handleRotatePointerDown($event, selectedLayer)"
          @click.stop
        />
      </template>
    </div>

    <div
      v-if="selectedLayer && selectedLayer.visible"
      class="v2-floating-toolbar"
      :style="chromePositionStyle(selectedLayer, 'toolbar')"
      @pointerdown.stop
      @click.stop
    >
      <button type="button" aria-label="Duplicate layer" title="Duplicate layer" @click="emit('layer-action', { layerId: selectedLayer.id, action: 'duplicate' })">
        <Copy :size="15" :stroke-width="1.8" aria-hidden="true" />
      </button>
      <button
        type="button"
        :aria-label="selectedLayer.locked ? 'Unlock layer' : 'Lock layer'"
        :title="selectedLayer.locked ? 'Unlock layer' : 'Lock layer'"
        @click="emit('layer-action', { layerId: selectedLayer.id, action: 'toggle-lock' })"
      >
        <LockKeyhole :size="15" :stroke-width="1.8" aria-hidden="true" />
      </button>
      <span class="v2-toolbar-divider" aria-hidden="true" />
      <button type="button" aria-label="Bring forward" title="Bring forward" @click="emit('layer-action', { layerId: selectedLayer.id, action: 'bring-forward' })">
        <ArrowUp :size="15" :stroke-width="1.8" aria-hidden="true" />
      </button>
      <button type="button" aria-label="Send backward" title="Send backward" @click="emit('layer-action', { layerId: selectedLayer.id, action: 'send-backward' })">
        <ArrowDown :size="15" :stroke-width="1.8" aria-hidden="true" />
      </button>
      <span class="v2-toolbar-divider" aria-hidden="true" />
      <button type="button" aria-label="Delete layer" title="Delete layer" @click="emit('layer-action', { layerId: selectedLayer.id, action: 'delete' })">
        <Trash2 :size="15" :stroke-width="1.8" aria-hidden="true" />
      </button>
    </div>

    <span
      v-if="selectedLayer && selectedLayer.visible && rotateState?.layerId === selectedLayer.id"
      class="v2-rotation-badge"
      :style="chromePositionStyle(selectedLayer, 'badge')"
    >
      {{ Math.round(selectedLayer.width) }} x {{ Math.round(selectedLayer.height) }} - {{ selectedLayer.rotation }}&deg;
    </span>
  </div>
</template>

<style scoped>
.v2-canvas {
  position: relative;
  width: min(720px, calc(100vw - 780px));
  min-width: 360px;
  overflow: visible;
  border: 1px solid rgba(31, 33, 40, 0.18);
  border-radius: 3px;
  box-shadow: 0 20px 50px rgba(39, 40, 48, 0.18);
  transform-origin: center;
  transition: transform 160ms ease;
}

.v2-layer {
  position: absolute;
  box-sizing: border-box;
  overflow: visible;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.v2-layer__content {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  overflow: hidden;
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

.v2-layer--text .v2-layer__content {
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

.v2-selection-box {
  position: absolute;
  z-index: 5;
  transform-origin: center;
  pointer-events: none;
}

.v2-selection-border {
  position: absolute;
  inset: 0;
  border: 1.5px solid #1557d6;
  pointer-events: none;
}

.v2-selection-handle {
  position: absolute;
  width: 12px;
  height: 12px;
  padding: 0;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #1557d6;
  box-shadow: 0 0 0 1px rgba(18, 49, 112, 0.4);
  pointer-events: auto;
  touch-action: none;
}

.v2-selection-handle--nw {
  top: -6px;
  left: -6px;
  cursor: nwse-resize;
}

.v2-selection-handle--n {
  top: -6px;
  left: 50%;
  cursor: ns-resize;
  transform: translateX(-50%);
}

.v2-selection-handle--ne {
  top: -6px;
  right: -6px;
  cursor: nesw-resize;
}

.v2-selection-handle--e {
  top: 50%;
  right: -6px;
  cursor: ew-resize;
  transform: translateY(-50%);
}

.v2-selection-handle--se {
  right: -6px;
  bottom: -6px;
  cursor: nwse-resize;
}

.v2-selection-handle--s {
  bottom: -6px;
  left: 50%;
  cursor: ns-resize;
  transform: translateX(-50%);
}

.v2-selection-handle--sw {
  bottom: -6px;
  left: -6px;
  cursor: nesw-resize;
}

.v2-selection-handle--w {
  top: 50%;
  left: -6px;
  cursor: ew-resize;
  transform: translateY(-50%);
}

.v2-rotate-handle {
  position: absolute;
  top: -38px;
  left: 50%;
  width: 14px;
  height: 14px;
  padding: 0;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #1557d6;
  box-shadow: 0 0 0 1px rgba(18, 49, 112, 0.4);
  cursor: crosshair;
  pointer-events: auto;
  touch-action: none;
  transform: translateX(-50%);
}

.v2-rotate-handle::before {
  position: absolute;
  top: 12px;
  left: 4px;
  width: 2px;
  height: 24px;
  background: #1557d6;
  content: "";
  pointer-events: none;
}

.v2-floating-toolbar {
  position: absolute;
  top: -88px;
  left: 50%;
  display: flex;
  box-sizing: border-box;
  align-items: center;
  gap: 2px;
  height: 42px;
  padding: 5px;
  border-radius: 9px;
  background: #17181e;
  box-shadow: 0 8px 20px rgba(16, 18, 25, 0.22);
  pointer-events: auto;
  transform: translateX(-50%);
  white-space: nowrap;
}

.v2-floating-toolbar button {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #f5f6f8;
  cursor: pointer;
}

.v2-floating-toolbar button:hover,
.v2-floating-toolbar button:focus-visible {
  background: #30323b;
  outline: none;
}

.v2-toolbar-divider {
  width: 1px;
  height: 22px;
  margin: 0 3px;
  background: #4a4c56;
}

.v2-rotation-badge {
  position: absolute;
  top: calc(100% + 12px);
  left: 50%;
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(38, 40, 49, 0.82);
  color: #ffffff;
  font-size: 11px;
  line-height: 1.2;
  pointer-events: none;
  transform: translateX(-50%);
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .v2-canvas {
    width: min(640px, calc(100vw - 670px));
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
