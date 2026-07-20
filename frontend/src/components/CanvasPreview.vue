<script setup lang="ts">
import { computed } from "vue";
import type { EditorLayerV2 } from "../editor-v2/document";
import { shapeBorderRadius, shapeBoxShadow, shapeClipPath, shapeFillBackground } from "../editor-v2/shapeAppearance";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};

const props = withDefaults(
  defineProps<{
    layers: CanvasLayer[];
    widthMm: number;
    heightMm: number;
    label: string;
    emptyLabel: string;
    selectedLayerIndex?: number | null;
    selectedLayerIndexes?: number[];
    resizableLayerIndex?: number | null;
    rotatableLayerIndex?: number | null;
    interactive?: boolean;
    showSafeZoneGuides?: boolean;
    safeZoneMm?: number;
    bleedMm?: number;
    background?: string;
  }>(),
  {
    showSafeZoneGuides: false,
    safeZoneMm: 3,
    bleedMm: 2,
    background: "#fffdf8",
  },
);

const guideStyle = computed(() => {
  const safeX = props.widthMm > 0 ? (props.safeZoneMm / props.widthMm) * 100 : 0;
  const safeY = props.heightMm > 0 ? (props.safeZoneMm / props.heightMm) * 100 : 0;
  const bleedX = props.widthMm > 0 ? (props.bleedMm / props.widthMm) * 100 : 0;
  const bleedY = props.heightMm > 0 ? (props.bleedMm / props.heightMm) * 100 : 0;
  return {
    "--safe-inset-x": `${safeX}%`,
    "--safe-inset-y": `${safeY}%`,
    "--bleed-inset-x": `${Math.max(0, bleedX)}%`,
    "--bleed-inset-y": `${Math.max(0, bleedY)}%`,
  };
});

const emit = defineEmits<{
  canvasPointerdown: [event: PointerEvent];
  layerPointerdown: [index: number, event: PointerEvent];
  layerResizePointerdown: [index: number, event: PointerEvent];
  layerRotatePointerdown: [index: number, event: PointerEvent];
  layerSelect: [index: number, event: KeyboardEvent];
}>();

const frameStyle = computed(() => ({
  aspectRatio: `${props.widthMm} / ${props.heightMm}`,
  "--canvas-width-mm": props.widthMm,
  "--canvas-height-mm": props.heightMm,
  background: props.background,
}));

function numberValue(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function stringValue(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() ? value : fallback;
}

function colorWithOpacity(color: string, opacity: number): string {
  const normalized = color.trim();
  const hex = normalized.match(/^#([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i);
  if (!hex) {
    return normalized;
  }
  return `rgba(${Number.parseInt(hex[1], 16)}, ${Number.parseInt(hex[2], 16)}, ${Number.parseInt(hex[3], 16)}, ${opacity})`;
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
  return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("/") || /^data:image\/svg\+xml;base64,/i.test(source)
    ? source
    : "";
}

function layerClass(layer: CanvasLayer): string {
  const type = stringValue(layer.type, "unknown");
  return ["text", "icon", "rect", "ellipse", "shape", "image"].includes(type)
    ? `canvas-layer--${type}`
    : "canvas-layer--unknown";
}

function layerIsTextLike(layer: CanvasLayer): boolean {
  return layer.type === "text" || layer.type === "icon";
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
  const isIcon = layer.type === "icon";
  const isText = layer.type === "text";
  const isImage = layer.type === "image";
  const isFrame = isImage && !layerImageSource(layer);
  const textLike = isText || isIcon;
  const width = numberValue(layer.width, isText ? 45 : isIcon ? 10 : 32);
  const height = numberValue(layer.height, isText ? 16 : isIcon ? 10 : 26);
  const fontSize = numberValue(layer.fontSize, isIcon ? 28 : 14);
  const stroke = stringValue(layer.stroke, "rgba(122, 93, 46, 0.18)");
  const strokeWidth = numberValue(layer.strokeWidth, 1);
  const blur = numberValue(layer.blur, 0);
  const shadowBlur = numberValue(layer.shadowBlur, 0);
  const shadowOpacity = Math.min(Math.max(numberValue(layer.shadowOpacity, 0.5), 0), 1);
  const shadowColor = colorWithOpacity(
    stringValue(layer.shadowColor, "#2f281c"),
    shadowOpacity,
  );
  const shadow = shadowBlur > 0
    ? `${numberValue(layer.shadowX, 0)}px ${numberValue(layer.shadowY, 4)}px ${shadowBlur}px ${numberValue(layer.shadowSpread, 0)}px ${shadowColor}`
    : "none";
  const shapeShadow = layer.shapeEffect ? shapeBoxShadow(layer as EditorLayerV2) : shadow;
  const textShadow = shadowBlur > 0
    ? `${numberValue(layer.shadowX, 0)}px ${numberValue(layer.shadowY, 4)}px ${shadowBlur}px ${shadowColor}`
    : "none";

  // Canvas font sizes are authored against the canonical 520 px preview.
  // Keep them proportional in thumbnails instead of applying a fixed minimum.
  const textFontSize = `min(${fontSize}px, ${(fontSize / 5.2).toFixed(3)}cqw)`;
  const iconSize = fontSize * 1.2;
  const iconFontSize = `min(${iconSize}px, ${(iconSize / 5.2).toFixed(3)}cqw)`;
  const usesV2ShapeFill = layer.fillMode === "linear"
    || layer.fillMode === "radial"
    || layer.shapeEffect === "hollow"
    || isFrame;
  const shapeLike = layer.type === "rect" || layer.type === "ellipse" || isFrame;

  return {
    left: `${x}%`,
    top: `${y}%`,
    width: `${width}%`,
    height: `${height}%`,
    color: stringValue(layer.color ?? layer.fill, "#1f2937"),
    background: textLike || (isImage && !isFrame)
      ? "transparent"
      : usesV2ShapeFill
        ? shapeFillBackground(layer as EditorLayerV2)
        : stringValue(layer.fill ?? layer.background, "rgba(255,255,255,0.72)"),
    border: textLike || (isImage && !isFrame) ? "0" : `${strokeWidth}px solid ${stroke}`,
    borderRadius: shapeLike
      ? shapeBorderRadius(layer as EditorLayerV2)
      : `${numberValue(layer.radius, 10)}px`,
    clipPath: shapeLike ? shapeClipPath(layer as EditorLayerV2) ?? "none" : "none",
    fontFamily: isIcon
      ? "\"Segoe UI Emoji\", \"Apple Color Emoji\", \"Noto Color Emoji\", \"Segoe UI Symbol\", \"Noto Sans Symbols 2\", system-ui, sans-serif"
      : stringValue(
        layer.fontFamily,
        "system-ui, \"Segoe UI\", \"Be Vietnam Pro\", \"Helvetica Neue\", Arial, sans-serif",
      ),
    fontSize: isIcon ? iconFontSize : isText ? textFontSize : `${fontSize}px`,
    fontWeight: numberValue(layer.fontWeight, isText ? 600 : 700),
    opacity: numberValue(layer.opacity, 1),
    boxShadow: textLike ? "none" : shapeShadow,
    textShadow: isText ? textShadow : "none",
    filter: blur > 0 ? `blur(${blur}px)` : "none",
    transform: `rotate(${numberValue(layer.rotation, 0)}deg)`,
    transformOrigin: "center center",
    lineHeight: isIcon ? 1 : 1.25,
    letterSpacing: isText ? "0.01em" : "normal",
    whiteSpace: isText ? "pre-wrap" : "nowrap",
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
    <div
      v-if="showSafeZoneGuides"
      class="canvas-guides"
      :style="guideStyle"
      aria-hidden="true"
    >
      <span class="canvas-guide canvas-guide--bleed" />
      <span class="canvas-guide canvas-guide--safe" />
    </div>
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
          crossorigin="anonymous"
          loading="lazy"
          referrerpolicy="no-referrer"
          draggable="false"
        />
        <template v-else>
          {{ layerText(layer) }}
        </template>
        <button
          v-if="interactive && index === rotatableLayerIndex"
          type="button"
          class="canvas-rotate-handle"
          :aria-label="`Rotate layer ${index + 1}`"
          title="Rotate layer"
          @pointerdown.stop.prevent="emit('layerRotatePointerdown', index, $event)"
        />
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
