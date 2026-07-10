const stableLayerIdPattern = /^[A-Za-z][A-Za-z0-9_-]{0,79}$/;

export function canvasLayerId(layer: Record<string, unknown>, index: number): string {
  return typeof layer.id === "string" && stableLayerIdPattern.test(layer.id)
    ? layer.id
    : `layer-${index + 1}`;
}

export function readLayerText(layer: Record<string, unknown>): string {
  if (typeof layer.text === "string") {
    return layer.text;
  }
  return typeof layer.value === "string" ? layer.value : "";
}

export function applyTextRewritePreview<T extends Record<string, unknown>>(
  layers: T[],
  targetLayerId: string,
  originalText: string,
  replacementText: string,
): { layers: T[]; selectedIndex: number } {
  const selectedIndex = layers.findIndex((layer, index) => canvasLayerId(layer, index) === targetLayerId);
  const layer = layers[selectedIndex];
  if (!layer || layer.type !== "text") {
    throw new Error("The AI preview target no longer exists");
  }
  if (readLayerText(layer) !== originalText) {
    throw new Error("The text changed since the preview was created");
  }

  const textField = typeof layer.text === "string" || typeof layer.value !== "string"
    ? "text"
    : "value";
  const nextLayers = [...layers];
  nextLayers[selectedIndex] = { ...layer, [textField]: replacementText };
  return { layers: nextLayers, selectedIndex };
}
