<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ShoppingCart, Trash2 } from "@lucide/vue";
import { RouterLink } from "vue-router";
import { getDesign, type DesignDetail } from "../api";
import { getCartDesignIds, removeCartDesign } from "../cart";
import CanvasPreview from "../components/CanvasPreview.vue";
import {
  readEditorPreviewPages,
  type EditorPreviewLayer,
} from "../editor-v2/preview";

type CartCard = DesignDetail & {
  frontBackground: string;
  frontLayers: EditorPreviewLayer[];
};

const items = ref<CartCard[]>([]);
const loading = ref(true);
const error = ref("");

function toCartCard(design: DesignDetail): CartCard {
  const front = readEditorPreviewPages(design.canvasJson).front;
  return {
    ...design,
    frontBackground: front.background,
    frontLayers: front.layers,
  };
}

function removeItem(designId: number): void {
  removeCartDesign(designId);
  items.value = items.value.filter((item) => item.id !== designId);
}

onMounted(async () => {
  const designIds = getCartDesignIds();
  if (designIds.length === 0) {
    loading.value = false;
    return;
  }

  try {
    const results = await Promise.allSettled(designIds.map((id) => getDesign(id)));
    const rejected = results.find(
      (result): result is PromiseRejectedResult => result.status === "rejected",
    );
    if (rejected?.reason instanceof Error && rejected.reason.message.includes("Sign in")) {
      throw rejected.reason;
    }

    const loaded: CartCard[] = [];
    results.forEach((result, index) => {
      if (result.status === "fulfilled") {
        loaded.push(toCartCard(result.value));
      } else {
        const staleDesignId = designIds[index];
        if (staleDesignId !== undefined) {
          removeCartDesign(staleDesignId);
        }
      }
    });
    items.value = loaded;
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load cart";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="home-view cart-view">
    <div class="section-heading">
      <p class="eyebrow">Ready to print</p>
      <h1>Cart</h1>
      <p class="summary">Review saved designs before choosing paper, quantity and print options.</p>
    </div>

    <p v-if="loading" class="muted">Loading cart...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>
    <section v-else-if="items.length === 0" class="account-panel cart-empty-state">
      <ShoppingCart :size="28" aria-hidden="true" />
      <p class="eyebrow">Empty cart</p>
      <h2>No designs are waiting</h2>
      <p class="summary">Add a draft to the cart, then choose its paper and quantity at checkout.</p>
      <RouterLink class="primary-action" to="/designs">View drafts</RouterLink>
    </section>

    <div v-else class="template-grid cart-grid">
      <article v-for="item in items" :key="item.id" class="template-card draft-card cart-card">
        <div class="template-preview draft-card-preview" aria-hidden="true">
          <div class="draft-card-stage" :style="{ aspectRatio: `${item.widthMm} / ${item.heightMm}` }">
            <CanvasPreview
              v-if="item.frontLayers.length > 0"
              :layers="item.frontLayers"
              :width-mm="item.widthMm"
              :height-mm="item.heightMm"
              :background="item.frontBackground"
              :label="`${item.name} preview`"
              empty-label="Empty"
            />
            <span v-else class="draft-card-fallback">Empty card</span>
          </div>
        </div>
        <div class="template-meta draft-card-meta">
          <h3>{{ item.name }}</h3>
          <p>{{ item.widthMm }} x {{ item.heightMm }} mm</p>
          <div class="draft-card-actions">
            <RouterLink
              class="primary-action"
              :to="{ name: 'checkout', params: { designId: item.id } }"
            >
              Checkout
            </RouterLink>
            <RouterLink
              class="secondary-action"
              :to="{ name: 'editor', params: { designId: item.id } }"
            >
              Edit
            </RouterLink>
            <button class="danger-action button-with-icon" type="button" @click="removeItem(item.id)">
              <Trash2 :size="15" aria-hidden="true" />
              Remove
            </button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
