import assert from "node:assert/strict";
import test from "node:test";

import {
  applyTextRewritePreview,
  canvasLayerId,
  readLayerText,
} from "../src/aiRewritePreview.ts";

test("AI rewrite preview applies immutably to the intended text layer", () => {
  const layers = [
    { type: "rect", fill: "#ffffff" },
    { id: "company-name", type: "text", text: "Vizi Atelier" },
  ];

  assert.equal(canvasLayerId(layers[1], 1), "company-name");
  assert.equal(readLayerText(layers[1]), "Vizi Atelier");

  const result = applyTextRewritePreview(
    layers,
    "company-name",
    "Vizi Atelier",
    "Vizi Maison",
  );

  assert.notEqual(result.layers, layers);
  assert.equal(result.selectedIndex, 1);
  assert.equal(readLayerText(layers[1]), "Vizi Atelier");
  assert.equal(readLayerText(result.layers[1]), "Vizi Maison");
});

test("AI rewrite preview rejects a stale layer instead of overwriting manual edits", () => {
  const layers = [{ type: "text", value: "Edited manually" }];

  assert.throws(
    () => applyTextRewritePreview(layers, "layer-1", "Original", "AI proposal"),
    /changed since the preview was created/,
  );
  assert.equal(readLayerText(layers[0]), "Edited manually");
});
