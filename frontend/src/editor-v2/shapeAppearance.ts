import type { EditorGradientStopV2, EditorLayerV2 } from "./document";

const HEX_COLOR = /^#[\da-f]{6}$/i;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function finiteOr(value: number | undefined, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function cssColorWithOpacity(colorValue: string | undefined, opacityValue: number | undefined): string {
  const opacity = clamp(finiteOr(opacityValue, 1), 0, 1);
  const color = colorValue && HEX_COLOR.test(colorValue) ? colorValue : "#000000";
  if (opacity >= 1) return color;
  const red = Number.parseInt(color.slice(1, 3), 16);
  const green = Number.parseInt(color.slice(3, 5), 16);
  const blue = Number.parseInt(color.slice(5, 7), 16);
  return `rgba(${red}, ${green}, ${blue}, ${opacity})`;
}

function cssStopColor(stop: EditorGradientStopV2): string {
  return cssColorWithOpacity(stop.color, stop.opacity);
}

export function createDefaultGradientStops(fill = "#6138db"): EditorGradientStopV2[] {
  const start = HEX_COLOR.test(fill) ? fill : "#6138db";
  return [
    { color: start, offset: 0, opacity: 1 },
    { color: "#f4c46d", offset: 1, opacity: 1 },
  ];
}

export function normalizedGradientStops(layer: Pick<EditorLayerV2, "fill" | "gradientStops">): EditorGradientStopV2[] {
  const source = Array.isArray(layer.gradientStops) && layer.gradientStops.length >= 2
    ? layer.gradientStops.slice(0, 4)
    : createDefaultGradientStops(layer.fill);
  return source
    .map((stop) => ({
      color: HEX_COLOR.test(stop.color) ? stop.color : "#000000",
      offset: clamp(finiteOr(stop.offset, 0), 0, 1),
      opacity: clamp(finiteOr(stop.opacity, 1), 0, 1),
    }))
    .sort((left, right) => left.offset - right.offset);
}

export function shapeFillBackground(layer: EditorLayerV2): string {
  if (layer.shapeEffect === "hollow") return "transparent";
  if (layer.fillMode !== "linear" && layer.fillMode !== "radial") {
    return layer.fill ?? "transparent";
  }

  const stops = normalizedGradientStops(layer)
    .map((stop) => `${cssStopColor(stop)} ${Math.round(stop.offset * 1000) / 10}%`)
    .join(", ");
  if (layer.fillMode === "radial") {
    const centerX = clamp(finiteOr(layer.gradientCenterX, 50), 0, 100);
    const centerY = clamp(finiteOr(layer.gradientCenterY, 50), 0, 100);
    const radius = clamp(finiteOr(layer.gradientRadius, 70), 1, 200);
    return `radial-gradient(${radius}% ${radius}% at ${centerX}% ${centerY}%, ${stops})`;
  }

  const angle = clamp(finiteOr(layer.gradientAngle, 90), 0, 360);
  return `linear-gradient(${angle}deg, ${stops})`;
}

export function shapeBoxShadow(layer: EditorLayerV2): string {
  if (layer.shapeEffect !== "shadow" && layer.shapeEffect !== "glow") return "none";
  const blur = clamp(finiteOr(layer.shadowBlur, 16), 0, 100);
  const spread = clamp(finiteOr(layer.shadowSpread, 0), 0, 50);
  const x = layer.shapeEffect === "glow" ? 0 : clamp(finiteOr(layer.shadowX, 0), -100, 100);
  const y = layer.shapeEffect === "glow" ? 0 : clamp(finiteOr(layer.shadowY, 4), -100, 100);
  const opacity = finiteOr(layer.shadowOpacity, layer.shapeEffect === "glow" ? 0.7 : 0.35);
  const color = cssColorWithOpacity(
    layer.shadowColor ?? (layer.shapeEffect === "glow" ? layer.fill : "#111827"),
    opacity,
  );
  return `${x}px ${y}px ${blur}px ${spread}px ${color}`;
}

export function shapeClipPath(layer: Pick<EditorLayerV2, "shapeKind">): string | undefined {
  switch (layer.shapeKind) {
    case "triangle": return "polygon(50% 3%, 97% 97%, 3% 97%)";
    case "pentagon": return "polygon(50% 2%, 98% 38%, 80% 98%, 20% 98%, 2% 38%)";
    case "hexagon": return "polygon(25% 3%, 75% 3%, 98% 50%, 75% 97%, 25% 97%, 2% 50%)";
    case "star": return "polygon(50% 2%, 61% 36%, 98% 36%, 68% 58%, 80% 97%, 50% 74%, 20% 97%, 32% 58%, 2% 36%, 39% 36%)";
    case "diamond": return "polygon(50% 2%, 98% 50%, 50% 98%, 2% 50%)";
    case "octagon": return "polygon(28% 2%, 72% 2%, 98% 28%, 98% 72%, 72% 98%, 28% 98%, 2% 72%, 2% 28%)";
    case "burst": return "polygon(50% 0%, 58% 20%, 71% 7%, 72% 27%, 93% 20%, 80% 37%, 100% 50%, 80% 58%, 93% 80%, 71% 72%, 72% 93%, 58% 80%, 50% 100%, 42% 80%, 29% 93%, 28% 72%, 7% 80%, 20% 58%, 0% 50%, 20% 37%, 7% 20%, 29% 27%, 28% 7%, 42% 20%)";
    default: return undefined;
  }
}

export function shapeBorderRadius(layer: Pick<EditorLayerV2, "shapeKind" | "cornerRadius"> & { type?: EditorLayerV2["type"] }): string {
  if (layer.shapeKind === "ellipse" || layer.type === "ellipse") return "50%";
  if (layer.shapeKind === "pill") return "9999px";
  if (layer.shapeKind === "rounded") return `${Math.max(0, finiteOr(layer.cornerRadius, 16))}px`;
  return `${Math.max(0, finiteOr(layer.cornerRadius, 0))}px`;
}
