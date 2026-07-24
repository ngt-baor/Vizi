<template>
  <main class="app-shell" :class="{ 'app-shell--editor': isEditorRoute }">
    <nav v-if="!isEditorRoute" class="topbar" aria-label="Primary navigation">
      <RouterLink to="/">Vizi</RouterLink>
      <div class="topbar-links">
        <span>Business card design studio</span>
        <RouterLink to="/templates">Templates</RouterLink>
        <RouterLink to="/designs">My drafts</RouterLink>
        <RouterLink
          v-if="currentUser"
          class="topbar-cart-link"
          to="/cart"
          :aria-label="`Cart with ${cartCount} designs`"
        >
          Cart
          <span v-if="cartCount > 0" class="topbar-cart-count">{{ cartCount }}</span>
        </RouterLink>
        <RouterLink v-if="currentUser" to="/orders">Orders</RouterLink>
        <RouterLink v-if="currentUser?.role === 'ADMIN'" to="/admin">Admin</RouterLink>
        <RouterLink to="/account">Account</RouterLink>
      </div>
    </nav>
    <RouterView />
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";
import { getCurrentUser, type AuthUser } from "./api";
import { CART_UPDATED_EVENT, getCartDesignIds } from "./cart";

const route = useRoute();
const isEditorRoute = computed(() => route.name === "editor");
const currentUser = ref<AuthUser | null>(null);
const cartCount = ref(0);

async function refreshCurrentUser(): Promise<void> {
  if (isEditorRoute.value || window.location.pathname.startsWith("/editor/")) {
    return;
  }
  try {
    currentUser.value = await getCurrentUser();
  } catch {
    currentUser.value = null;
  }
}

function refreshCartCount(): void {
  cartCount.value = getCartDesignIds().length;
}

onMounted(() => {
  void refreshCurrentUser();
  refreshCartCount();
  window.addEventListener(CART_UPDATED_EVENT, refreshCartCount);
});
onBeforeUnmount(() => window.removeEventListener(CART_UPDATED_EVENT, refreshCartCount));
watch(() => route.fullPath, refreshCurrentUser);
</script>
