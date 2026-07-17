import {
  readEditorDocumentV2,
  type EditorLayerV2,
  type EditorSide,
} from "./document";

export type EditorPreviewLayer = Record<string, unknown> & {
  type?: string;
  page?: string;
};

export type EditorPreviewPage = {
  background: string;
  layers: EditorPreviewLayer[];
};

const emptyPages = (): Record<EditorSide, EditorPreviewPage> => ({
  front: { background: "#fffdf8", layers: [] },
  back: { background: "#fffdf8", layers: [] },
});

function adaptEditorLayers(layers: EditorLayerV2[]): EditorPreviewLayer[] {
  return layers.map((layer) => ({
    ...layer,
    text: layer.content,
    radius: layer.cornerRadius,
  }));
}

export function readEditorPreviewPages(
  canvasJson: string,
): Record<EditorSide, EditorPreviewPage> {
  const document = readEditorDocumentV2(canvasJson);
  if (document) {
    return {
      front: {
        background: document.pages.front.background,
        layers: adaptEditorLayers(document.pages.front.layers),
      },
      back: {
        background: document.pages.back.background,
        layers: adaptEditorLayers(document.pages.back.layers),
      },
    };
  }

  try {
    const canvas = JSON.parse(canvasJson) as { layers?: unknown };
    if (!Array.isArray(canvas.layers)) {
      return emptyPages();
    }
    const layers = canvas.layers.filter(
      (layer): layer is EditorPreviewLayer => typeof layer === "object" && layer !== null,
    );
    return {
      front: {
        background: "#fffdf8",
        layers: layers.filter((layer) => layer.page !== "back"),
      },
      back: {
        background: "#fffdf8",
        layers: layers.filter((layer) => layer.page === "back"),
      },
    };
  } catch {
    return emptyPages();
  }
}
