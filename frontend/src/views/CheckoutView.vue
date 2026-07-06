<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { createOrder, getDesign, type DesignDetail } from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
};

const route = useRoute();
const router = useRouter();
const design = ref<DesignDetail | null>(null);
const layers = ref<CanvasLayer[]>([]);
const loading = ref(true);
const error = ref("");
const orderError = ref("");
const submitting = ref(false);
const paper = ref("matte-350");
const quantity = ref(100);
const roundedCorners = ref(false);

const paperOptions = [
  { id: "matte-350", label: "Matte 350gsm", pricePer100: 180000 },
  { id: "silk-400", label: "Silk 400gsm", pricePer100: 240000 },
  { id: "linen-300", label: "Linen 300gsm", pricePer100: 280000 },
];
const quantityOptions = [100, 200, 500, 1000];
const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

const selectedPaper = computed(() =>
  paperOptions.find((option) => option.id === paper.value) ?? paperOptions[0],
);
const roundedCornerPrice = computed(() => roundedCorners.value ? Math.ceil(quantity.value / 100) * 30000 : 0);
const subtotal = computed(() => Math.ceil(quantity.value / 100) * selectedPaper.value.pricePer100);
const estimatedTotal = computed(() => subtotal.value + roundedCornerPrice.value);

function parseCanvasLayers(canvasJson: string): CanvasLayer[] {
  try {
    const canvas = JSON.parse(canvasJson) as { layers?: unknown };
    return Array.isArray(canvas.layers)
      ? canvas.layers.filter((layer): layer is CanvasLayer => typeof layer === "object" && layer !== null)
      : [];
  } catch {
    return [];
  }
}

onMounted(async () => {
  const rawId = route.params.designId;
  const designId = Number(Array.isArray(rawId) ? rawId[0] : rawId);
  try {
    design.value = await getDesign(designId);
    layers.value = parseCanvasLayers(design.value.canvasJson);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load checkout";
  } finally {
    loading.value = false;
  }
});

async function submitOrder() {
  if (!design.value || submitting.value) {
    return;
  }
  submitting.value = true;
  orderError.value = "";
  try {
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
          <CanvasPreview
            :layers="layers"
            :width-mm="design.widthMm"
            :height-mm="design.heightMm"
            label="Checkout card preview"
            empty-label="Draft"
          />
        </section>

        <form class="checkout-panel" aria-label="Print checkout options" @submit.prevent="submitOrder">
          <label>
            <span>Paper</span>
            <select v-model="paper" aria-label="Paper type">
              <option v-for="option in paperOptions" :key="option.id" :value="option.id">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label>
            <span>Quantity</span>
            <select v-model.number="quantity" aria-label="Quantity">
              <option v-for="option in quantityOptions" :key="option" :value="option">
                {{ option }} cards
              </option>
            </select>
          </label>

          <label class="checkout-toggle">
            <input v-model="roundedCorners" type="checkbox" aria-label="Rounded corners">
            <span>Rounded corners</span>
          </label>

          <dl class="checkout-summary">
            <div>
              <dt>Paper</dt>
              <dd>{{ selectedPaper.label }}</dd>
            </div>
            <div>
              <dt>Quantity</dt>
              <dd>{{ quantity }} cards</dd>
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

          <p v-if="orderError" class="error-text" role="alert">
            {{ orderError }}
            <RouterLink v-if="orderError.includes('Sign in')" to="/account">Open account</RouterLink>
          </p>

          <button class="primary-action" type="submit" :disabled="submitting">
            {{ submitting ? "Creating order..." : "Create order" }}
          </button>
        </form>
      </div>
    </template>
  </section>
</template>
