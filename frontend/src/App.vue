<template>
  <main class="app-shell" :class="{ 'app-shell--editor': isEditorRoute }">
    <nav v-if="!isEditorRoute" class="topbar" aria-label="Primary navigation">
      <RouterLink to="/">Vizi</RouterLink>
      <div class="topbar-links">
        <span>Business card design studio</span>
        <RouterLink to="/templates">Templates</RouterLink>
        <RouterLink to="/designs">My drafts</RouterLink>
        <RouterLink v-if="currentUser?.role === 'ADMIN'" to="/admin">Admin</RouterLink>
        <RouterLink to="/account">Account</RouterLink>
      </div>
    </nav>
    <RouterView />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";
import { getCurrentUser, type AuthUser } from "./api";

const route = useRoute();
const isEditorRoute = computed(() => route.name === "editor");
const currentUser = ref<AuthUser | null>(null);

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

onMounted(refreshCurrentUser);
watch(() => route.fullPath, refreshCurrentUser);
</script>
