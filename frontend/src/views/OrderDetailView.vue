<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { getOrder, type OrderResponse } from "../api";

const route = useRoute();
const order = ref<OrderResponse | null>(null);
const loading = ref(true);
const error = ref("");

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

const totalQuantity = computed(() =>
  order.value?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0,
);

onMounted(async () => {
  const rawId = route.params.orderId;
  const orderId = Number(Array.isArray(rawId) ? rawId[0] : rawId);
  try {
    order.value = await getOrder(orderId);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load order";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="checkout-view">
    <RouterLink class="back-link" to="/designs">Back to drafts</RouterLink>

    <p v-if="loading" class="muted">Loading order...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>

    <template v-else-if="order">
      <header class="checkout-header">
        <div>
          <p class="eyebrow">Order created</p>
          <h1>Order #{{ order.id }}</h1>
          <p class="summary">Your print order is saved and waiting for payment confirmation.</p>
        </div>
        <RouterLink class="secondary-action" to="/designs">
          View drafts
        </RouterLink>
      </header>

      <section class="order-success-panel" aria-label="Order summary">
        <dl class="checkout-summary">
          <div>
            <dt>Status</dt>
            <dd>{{ order.status }}</dd>
          </div>
          <div>
            <dt>Total quantity</dt>
            <dd>{{ totalQuantity }} cards</dd>
          </div>
          <div>
            <dt>Total amount</dt>
            <dd>{{ currency.format(order.totalAmount) }}</dd>
          </div>
        </dl>

        <div class="order-items">
          <article v-for="item in order.items" :key="item.id" class="order-item">
            <span>Item #{{ item.id }}</span>
            <strong>{{ item.quantity }} cards</strong>
            <strong>{{ currency.format(item.subtotal) }}</strong>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>
