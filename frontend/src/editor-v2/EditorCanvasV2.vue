<script setup lang="ts">
import { computed } from "vue";
import type { CSSProperties } from "vue";
import type { EditorDocumentV2, EditorLayerV2, EditorPageV2 } from "./document";

const props = defineProps<{
  document: EditorDocumentV2;
  page: EditorPageV2;
  zoom: number;
}>();

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
</script>

<template>
  <div
    class="v2-canvas"
    :style="canvasStyle"
    :aria-label="`${page.name} card canvas`"
    data-editor-v2-canvas
  >
    <div
      v-for="layer in page.layers"
      v-show="layer.visible"
      :key="layer.id"
      class="v2-layer"
      :class="`v2-layer--${layer.type}`"
      :style="layerStyle(layer)"
      :data-layer-id="layer.id"
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
  width: min(720px, calc(100vw - 650px));
  min-width: 360px;
  overflow: hidden;
  border: 1px solid rgba(24, 29, 27, 0.16);
  border-radius: 2px;
  box-shadow: 0 28px 80px rgba(32, 38, 35, 0.2);
  transform-origin: center;
  transition: transform 160ms ease;
}

.v2-layer {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
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

@media (max-width: 1100px) {
  .v2-canvas {
    width: min(640px, calc(100vw - 520px));
    min-width: 320px;
  }
}

@media (max-width: 820px) {
  .v2-canvas {
    width: min(88vw, 680px);
    min-width: 0;
  }
}
</style>
