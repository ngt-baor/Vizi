<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { AdminOrder, AdminTemplate, OrderItemResponse } from "../api";
import CanvasPreview from "./CanvasPreview.vue";
import {
  calculateImposition,
  PRINT_SHEETS,
  type ImpositionPlacement,
  type PrintSheetKey,
} from "../printImposition";
import {
  readEditorPreviewPages,
  type EditorPreviewPage,
} from "../editor-v2/preview";
import type { EditorSide } from "../editor-v2/document";

const props = defineProps<{
  orders: AdminOrder[];
  templates: AdminTemplate[];
}>();

type SourceMode = "order" | "template";

type PrintSource = {
  kind: SourceMode;
  key: string;
  label: string;
  designName: string;
  canvasJson: string;
  widthMm: number;
  heightMm: number;
  quantity: number;
};

type OrderPrintSource = {
  key: string;
  order: AdminOrder;
  item: OrderItemResponse;
};

const sourceMode = ref<SourceMode>("order");
const selectedOrderItemKey = ref("");
const selectedTemplateId = ref("");
const selectedSide = ref<EditorSide>("front");
const selectedSheet = ref<PrintSheetKey>("A4");
const templateQuantity = ref("1");
const printError = ref("");

const orderSources = computed<OrderPrintSource[]>(() =>
  props.orders.flatMap((order) =>
    order.items.map((item, index) => ({
      key: String(order.id) + ":" + String(item.id ?? index),
      order,
      item,
    })),
  ),
);

const selectedOrderSource = computed(() =>
  orderSources.value.find((source) => source.key === selectedOrderItemKey.value)
  ?? orderSources.value[0]
  ?? null,
);

const selectedTemplate = computed(() => {
  const selectedId = Number(selectedTemplateId.value);
  return props.templates.find((template) => template.id === selectedId)
    ?? props.templates[0]
    ?? null;
});

const source = computed<PrintSource | null>(() => {
  if (sourceMode.value === "order") {
    const selected = selectedOrderSource.value;
    if (!selected) {
      return null;
    }
    return {
      kind: "order",
      key: selected.key,
      label: "Order #" + selected.order.id + " - " + (selected.item.designName || selected.order.userFullName || "Customer card"),
      designName: selected.item.designName || "Order #" + selected.order.id,
      canvasJson: selected.item.designSnapshotJson || "{}",
      widthMm: selected.item.widthMm,
      heightMm: selected.item.heightMm,
      quantity: selected.item.quantity,
    };
  }

  const template = selectedTemplate.value;
  if (!template) {
    return null;
  }
  return {
    kind: "template",
    key: "template:" + template.id,
    label: "Template #" + template.id + " - " + template.name,
    designName: template.name,
    canvasJson: template.canvasJson,
    widthMm: template.widthMm,
    heightMm: template.heightMm,
    quantity: Math.max(1, Math.floor(Number(templateQuantity.value) || 1)),
  };
});

const selectedQuantity = computed(() => source.value?.quantity ?? 0);

const previewPages = computed(() => {
  const pages = readEditorPreviewPages(source.value?.canvasJson ?? "{}");
  return pages as Record<EditorSide, EditorPreviewPage>;
});

const activePage = computed(() => previewPages.value[selectedSide.value]);

const imposition = computed(() => {
  if (!source.value) {
    return null;
  }
  try {
    return calculateImposition({
      cardWidthMm: source.value.widthMm,
      cardHeightMm: source.value.heightMm,
      quantity: selectedQuantity.value,
      sheet: selectedSheet.value,
    });
  } catch (error) {
    printError.value = error instanceof Error ? error.message : "Cannot calculate print layout";
    return null;
  }
});

watch(
  () => orderSources.value.map((item) => item.key).join("|"),
  () => {
    if (!orderSources.value.some((item) => item.key === selectedOrderItemKey.value)) {
      selectedOrderItemKey.value = orderSources.value[0]?.key ?? "";
    }
  },
  { immediate: true },
);

watch(
  () => props.templates.map((template) => template.id).join("|"),
  () => {
    if (!props.templates.some((template) => String(template.id) === selectedTemplateId.value)) {
      selectedTemplateId.value = props.templates[0] ? String(props.templates[0].id) : "";
    }
  },
  { immediate: true },
);

watch([source, selectedSheet], () => {
  printError.value = "";
});

function sourceOptions(): string {
  return source.value?.kind === "order" ? "Order snapshot" : "Saved template";
}

function placementStyle(placement: ImpositionPlacement, sheetWidthMm: number, sheetHeightMm: number): Record<string, string> {
  return {
    left: (placement.x / sheetWidthMm) * 100 + "%",
    top: (placement.y / sheetHeightMm) * 100 + "%",
    width: (placement.width / sheetWidthMm) * 100 + "%",
    height: (placement.height / sheetHeightMm) * 100 + "%",
  };
}

function canvasStyle(placement: ImpositionPlacement): Record<string, string> {
  if (placement.rotation !== 90) {
    return { width: "100%", height: "100%" };
  }
  return {
    width: (placement.height / placement.width) * 100 + "%",
    height: (placement.width / placement.height) * 100 + "%",
    transform: "rotate(90deg)",
  };
}

function printCards(): void {
  if (!imposition.value) {
    printError.value = "Choose a source that fits on the selected sheet";
    return;
  }

  const style = document.createElement("style");
  style.dataset.viziPrint = "true";
  style.textContent = "@page { size: "
    + imposition.value.sheet.widthMm + "mm "
    + imposition.value.sheet.heightMm + "mm; margin: 0; }";
  document.head.appendChild(style);
  document.body.classList.add("admin-printing");

  const cleanup = () => {
    document.body.classList.remove("admin-printing");
    style.remove();
    window.removeEventListener("afterprint", cleanup);
  };
  window.addEventListener("afterprint", cleanup);
  window.print();
}
</script>

<template>
  <div
    class="admin-print-root"
    :data-print-card-count="imposition?.cardsPerSheet ?? 0"
    :data-print-sheet-count="imposition?.sheetCount ?? 0"
    :data-print-total-count="imposition?.quantity ?? 0"
  >
    <div class="admin-print-panel__chrome">
      <div class="admin-panel-header">
        <div>
          <p class="eyebrow">Print production</p>
          <h3>Impose business cards</h3>
          <p class="muted">
            Use the frozen order snapshot or a published template, then place the exact quantity on an A-series sheet.
          </p>
        </div>
        <button
          class="primary-action admin-print-action"
          type="button"
          :disabled="!imposition"
          @click="printCards"
        >
          Print {{ imposition?.quantity ?? 0 }} cards
        </button>
      </div>

      <div class="admin-print-controls">
        <div class="admin-print-source">
          <span class="admin-print-control-label">Source</span>
          <div class="mode-control">
            <button
              type="button"
              :class="{ active: sourceMode === 'order' }"
              :disabled="orderSources.length === 0"
              @click="sourceMode = 'order'"
            >
              Order
            </button>
            <button
              type="button"
              :class="{ active: sourceMode === 'template' }"
              :disabled="templates.length === 0"
              @click="sourceMode = 'template'"
            >
              Template
            </button>
          </div>
        </div>

        <label class="admin-print-field" v-if="sourceMode === 'order'">
          Order item
          <select v-model="selectedOrderItemKey" :disabled="orderSources.length === 0">
            <option v-if="orderSources.length === 0" value="">No order snapshots</option>
            <option v-for="item in orderSources" :key="item.key" :value="item.key">
              {{ item.order.id }} - {{ item.item.designName || "Customer card" }} - {{ item.item.quantity }} cards
            </option>
          </select>
        </label>

        <label class="admin-print-field" v-else>
          Saved template
          <select v-model="selectedTemplateId" :disabled="templates.length === 0">
            <option v-if="templates.length === 0" value="">No templates</option>
            <option v-for="template in templates" :key="template.id" :value="String(template.id)">
              {{ template.name }} - {{ template.widthMm }} x {{ template.heightMm }} mm
            </option>
          </select>
        </label>

        <label v-if="sourceMode === 'template'" class="admin-print-field">
          Quantity
          <input v-model.number="templateQuantity" type="number" min="1" max="100000" step="1" />
        </label>
        <div v-else class="admin-print-field admin-print-fixed-quantity">
          Quantity
          <strong>{{ selectedQuantity }} cards</strong>
          <small>Fixed from the order</small>
        </div>

        <label class="admin-print-field">
          Side
          <select v-model="selectedSide">
            <option value="front">Front</option>
            <option value="back">Back</option>
          </select>
        </label>

        <label class="admin-print-field">
          Paper size
          <select v-model="selectedSheet">
            <option v-for="sheet in Object.values(PRINT_SHEETS)" :key="sheet.key" :value="sheet.key">
              {{ sheet.key }} - {{ sheet.widthMm }} x {{ sheet.heightMm }} mm
            </option>
          </select>
        </label>
      </div>

      <p class="admin-print-source-note">
        {{ source?.label || "Choose an order or template" }} · {{ sourceOptions() }}
      </p>
      <p v-if="printError" class="error-text" role="alert">{{ printError }}</p>

      <div v-if="imposition" class="admin-print-metrics" aria-label="Print layout summary">
        <div><strong>{{ imposition.cardsPerSheet }}</strong><span>cards / sheet</span></div>
        <div><strong>{{ imposition.sheetCount }}</strong><span>sheets</span></div>
        <div><strong>{{ imposition.columns }} x {{ imposition.rows }}</strong><span>grid</span></div>
        <div><strong>{{ imposition.bleedMm }} mm</strong><span>bleed</span></div>
        <div><strong>{{ imposition.gutterMm }} mm</strong><span>gutter</span></div>
        <div><strong>{{ imposition.marginMm }} mm</strong><span>margin</span></div>
      </div>
    </div>

    <p v-if="!imposition" class="admin-print-empty">
      Add an order with a saved snapshot or publish a template before opening the print preview.
    </p>

    <div v-else class="admin-print-sheets" aria-label="Imposed print sheets">
      <section
        v-for="(placements, sheetIndex) in imposition.placements"
        :key="sheetIndex"
        class="admin-print-sheet"
        :style="{ width: imposition.sheet.widthMm + 'mm', height: imposition.sheet.heightMm + 'mm' }"
        :aria-label="'Sheet ' + (sheetIndex + 1) + ' of ' + imposition.sheetCount"
      >
        <div
          v-for="(placement, cardIndex) in placements"
          :key="String(sheetIndex) + '-' + String(cardIndex)"
          class="admin-print-card"
          :style="placementStyle(placement, imposition.sheet.widthMm, imposition.sheet.heightMm)"
          :data-print-card-index="sheetIndex * imposition.cardsPerSheet + cardIndex + 1"
        >
          <div
            class="admin-print-card__canvas"
            :style="canvasStyle(placement)"
          >
            <CanvasPreview
              :layers="activePage.layers"
              :width-mm="placement.width"
              :height-mm="placement.height"
              :background="activePage.background"
              :label="(source?.designName || 'Card') + ' ' + selectedSide"
              empty-label=""
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>