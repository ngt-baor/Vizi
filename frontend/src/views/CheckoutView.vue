<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import {
  createOrder,
  getDesign,
  listPapers,
  preflightDesign,
  type DesignDetail,
  type PaperStock,
  type PreflightReport,
} from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";
import { type EditorSide } from "../editor-v2/document";
import {
  readEditorPreviewPages,
  type EditorPreviewPage,
} from "../editor-v2/preview";

const route = useRoute();
const router = useRouter();
const design = ref<DesignDetail | null>(null);
const activeSide = ref<EditorSide>("front");
const checkoutPages = ref<Record<EditorSide, EditorPreviewPage>>({
  front: { background: "#fffdf8", layers: [] },
  back: { background: "#fffdf8", layers: [] },
});
const layers = computed(() => checkoutPages.value[activeSide.value].layers);
const previewBackground = computed(() => checkoutPages.value[activeSide.value].background);
const loading = ref(true);
const error = ref("");
const orderError = ref("");
const submitting = ref(false);
const paper = ref("");
const paperOptions = ref<PaperStock[]>([]);
const quantity = ref(100);
const roundedCorners = ref(false);
const preflightReport = ref<PreflightReport | null>(null);
const preflightLoading = ref(false);
const preflightError = ref("");
const preflightBlocksOrder = computed(() =>
  preflightReport.value?.issues.some((issue) => issue.level === "ERROR") ?? false,
);

const checkoutSides: EditorSide[] = ["front", "back"];
const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

const selectedPaper = computed(() =>
  paperOptions.value.find((option) => option.code === paper.value) ?? null,
);
const validQuantity = computed(() =>
  Number.isInteger(quantity.value) && quantity.value >= 1 && quantity.value <= 100000,
);
const roundedCornerPrice = computed(() =>
  roundedCorners.value && validQuantity.value ? Math.round(quantity.value / 100 * 30000) : 0,
);
const subtotal = computed(() =>
  selectedPaper.value && validQuantity.value
    ? Math.round(quantity.value / 100 * selectedPaper.value.pricePer100)
    : 0,
);
const estimatedTotal = computed(() => subtotal.value + roundedCornerPrice.value);
const canSubmit = computed(() =>
  !!selectedPaper.value
  && selectedPaper.value.status === "IN_STOCK"
  && validQuantity.value
  && !submitting.value
  && !preflightLoading.value
  && !preflightBlocksOrder.value,
);

onMounted(async () => {
  const rawId = route.params.designId;
  const designId = Number(Array.isArray(rawId) ? rawId[0] : rawId);
  try {
    const [loadedDesign, loadedPapers] = await Promise.all([
      getDesign(designId),
      listPapers(),
    ]);
    design.value = loadedDesign;
    checkoutPages.value = readEditorPreviewPages(loadedDesign.canvasJson);
    paperOptions.value = loadedPapers;
    paper.value = loadedPapers.find((option) => option.status === "IN_STOCK")?.code ?? "";
    if (!paper.value) {
      orderError.value = "No paper type is currently in stock.";
    }
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load checkout";
  } finally {
    loading.value = false;
  }
});

async function runPreflight(): Promise<PreflightReport | null> {
  if (!design.value || preflightLoading.value) {
    return preflightReport.value;
  }
  preflightLoading.value = true;
  preflightError.value = "";
  try {
    preflightReport.value = await preflightDesign(design.value.id);
    return preflightReport.value;
  } catch (unknownError) {
    preflightError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot run preflight";
    preflightReport.value = null;
    return null;
  } finally {
    preflightLoading.value = false;
  }
}

async function submitOrder(): Promise<void> {
  if (!design.value || submitting.value) {
    return;
  }
  if (!validQuantity.value) {
    orderError.value = "Quantity must be a whole number between 1 and 100,000.";
    return;
  }
  if (!selectedPaper.value || selectedPaper.value.status !== "IN_STOCK") {
    orderError.value = "Select a paper type that is in stock.";
    return;
  }

  submitting.value = true;
  orderError.value = "";
  try {
    const report = preflightReport.value ?? await runPreflight();
    if (!report) {
      orderError.value = preflightError.value || "Preflight must complete before creating an order.";
      return;
    }
    if (!report.valid) {
      orderError.value = "Preflight found errors. Fix the design before creating an order.";
      return;
    }
    const order = await createOrder(design.value.id, paper.value, quantity.value, roundedCorners.value);
    await router.push({ name: "order-detail", params: { orderId: order.id } });
  } catch (unknownError) {
    orderError.value = unknownError instanceof Error ? unknownError.message : "Cannot create order";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="checkout-view">
    <RouterLink class="back-link" to="/designs">Back to drafts</RouterLink>

    <p v-if="loading" class="muted">Loading checkout...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>

    <template v-else-if="design">
      <header class="checkout-header">
        <div>
          <p class="eyebrow">Checkout</p>
          <h1>{{ design.name }}</h1>
          <p class="summary">{{ design.widthMm }} x {{ design.heightMm }} mm business card.</p>
        </div>
        <RouterLink class="secondary-action" :to="{ name: 'editor', params: { designId: design.id } }">
          Edit design
        </RouterLink>
      </header>

      <div class="checkout-shell">
        <section class="checkout-preview" aria-label="Checkout design preview">
          <div class="checkout-preview__toolbar">
            <strong>{{ activeSide === "front" ? "Front side" : "Back side" }}</strong>
            <div class="checkout-side-switch" aria-label="Card side">
              <button
                v-for="side in checkoutSides"
                :key="side"
                type="button"
                :class="{ active: activeSide === side }"
                :aria-pressed="activeSide === side"
                @click="activeSide = side"
              >
                {{ side === "front" ? "Front" : "Back" }}
              </button>
            </div>
          </div>
          <CanvasPreview
            :layers="layers"
            :width-mm="design.widthMm"
            :height-mm="design.heightMm"
            :background="previewBackground"
            :label="`Checkout ${activeSide} card preview`"
            :empty-label="activeSide === 'front' ? 'Front' : 'Back'"
          />
        </section>

        <form class="checkout-panel" aria-label="Print checkout options" @submit.prevent="submitOrder">
          <label>
            <span>Paper</span>
            <select v-model="paper" aria-label="Paper type" required>
              <option
                v-for="option in paperOptions"
                :key="option.id"
                :value="option.code"
                :disabled="option.status === 'OUT_OF_STOCK'"
              >
                {{ option.name }}{{ option.status === "OUT_OF_STOCK" ? " - Out of stock" : "" }}
              </option>
            </select>
          </label>

          <label>
            <span>Quantity</span>
            <input
              v-model.number="quantity"
              type="number"
              min="1"
              max="100000"
              step="1"
              inputmode="numeric"
              aria-label="Quantity"
              required
            >
            <small>Enter the exact number of cards to print.</small>
          </label>

          <label class="checkout-toggle">
            <input v-model="roundedCorners" type="checkbox" aria-label="Rounded corners">
            <span>Rounded corners</span>
          </label>

          <dl class="checkout-summary">
            <div>
              <dt>Paper</dt>
              <dd>{{ selectedPaper?.name ?? "Unavailable" }}</dd>
            </div>
            <div>
              <dt>Quantity</dt>
              <dd>{{ validQuantity ? quantity : 0 }} cards</dd>
            </div>
            <div>
              <dt>Rounded corners</dt>
              <dd>{{ roundedCorners ? "Yes" : "No" }}</dd>
            </div>
            <div>
              <dt>Estimated total</dt>
              <dd>{{ currency.format(estimatedTotal) }}</dd>
            </div>
          </dl>

          <div class="checkout-preflight" aria-label="Preflight checks">
            <button
              class="secondary-action"
              type="button"
              :disabled="preflightLoading || submitting"
              @click="runPreflight"
            >
              {{ preflightLoading ? "Running preflight..." : "Run preflight" }}
            </button>
            <p v-if="preflightError" class="error-text" role="alert">{{ preflightError }}</p>
            <div v-if="preflightReport" role="status">
              <strong>
                {{ preflightReport.valid ? "Preflight passed" : "Preflight failed" }}
              </strong>
              <ul v-if="preflightReport.issues.length">
                <li v-for="(issue, index) in preflightReport.issues" :key="`${issue.code}-${index}`">
                  {{ issue.side === "front" ? "Front" : issue.side === "back" ? "Back" : "Document" }}
                  [{{ issue.level }}] {{ issue.message }}
                </li>
              </ul>
            </div>
          </div>

          <p v-if="orderError" class="error-text" role="alert">
            {{ orderError }}
            <RouterLink v-if="orderError.includes('Sign in')" to="/account">Open account</RouterLink>
          </p>

          <button class="primary-action" type="submit" :disabled="!canSubmit">
            {{ submitting ? "Creating order..." : "Create order" }}
          </button>
        </form>
      </div>
    </template>
  </section>
</template>