<template>
  <main class="app-shell" :class="{ 'app-shell--editor': isEditorRoute }">
    <nav v-if="!isEditorRoute" class="topbar" aria-label="Primary navigation">
      <RouterLink to="/">Vizi</RouterLink>
      <div class="topbar-links">
        <span>Business card design studio</span>
        <RouterLink to="/templates">Templates</RouterLink>
        <RouterLink to="/designs">My drafts</RouterLink>
        <RouterLink to="/account">Account</RouterLink>
      </div>
    </nav>
    <RouterView />
  </main>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

const route = useRoute();
const isEditorRoute = computed(() => route.name === "editor");
</script>
