<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { PackageSearch } from "@lucide/vue";
import { RouterLink } from "vue-router";
import { listOrders, type OrderResponse } from "../api";

const orders = ref<OrderResponse[]>([]);
const loading = ref(true);
const error = ref("");

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});
const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
  timeStyle: "short",
});

const statusLabels: Record<string, string> = {
  DRAFT: "Draft",
  PENDING_PAYMENT: "Pending payment",
  PAID: "Paid",
  PRINTING: "Printing",
  DONE: "Completed",
  CANCELLED: "Cancelled",
};

function statusLabel(status: string): string {
  return statusLabels[status] ?? status.replaceAll("_", " ").toLowerCase();
}

function formatDate(value?: string): string {
  if (!value) {
    return "Date unavailable";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

function totalQuantity(order: OrderResponse): number {
  return order.items.reduce((sum, item) => sum + item.quantity, 0);
}

function designNames(order: OrderResponse): string {
  return order.items.map((item) => item.designName).join(", ");
}

const hasOrders = computed(() => orders.value.length > 0);

onMounted(async () => {
  try {
    orders.value = await listOrders();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load orders";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="home-view order-history-view">
    <div class="section-heading">
      <p class="eyebrow">Your purchases</p>
      <h1>Orders</h1>
      <p class="summary">Follow payment, printing and completion status for every order.</p>
    </div>

    <p v-if="loading" class="muted">Loading orders...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>
    <section v-else-if="!hasOrders" class="account-panel order-empty-state">
      <PackageSearch :size="28" aria-hidden="true" />
      <p class="eyebrow">No orders</p>
      <h2>Your order history is empty</h2>
      <p class="summary">Choose a saved draft and complete checkout to place your first print order.</p>
      <RouterLink class="primary-action" to="/designs">View drafts</RouterLink>
    </section>

    <div v-else class="order-history-list">
      <article v-for="order in orders" :key="order.id" class="order-history-card">
        <div class="order-history-card__heading">
          <div>
            <p class="eyebrow">Order #{{ order.id }}</p>
            <h2>{{ designNames(order) }}</h2>
            <p class="muted">Created {{ formatDate(order.createdAt) }}</p>
          </div>
          <span class="order-status" :data-status="order.status">{{ statusLabel(order.status) }}</span>
        </div>
        <dl class="order-history-card__facts">
          <div>
            <dt>Quantity</dt>
            <dd>{{ totalQuantity(order) }} cards</dd>
          </div>
          <div>
            <dt>Total</dt>
            <dd>{{ currency.format(order.totalAmount) }}</dd>
          </div>
          <div>
            <dt>Updated</dt>
            <dd>{{ formatDate(order.updatedAt) }}</dd>
          </div>
        </dl>
        <RouterLink class="secondary-action" :to="{ name: 'order-detail', params: { orderId: order.id } }">
          View order
        </RouterLink>
      </article>
    </div>
  </section>
</template>
