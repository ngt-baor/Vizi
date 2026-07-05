<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { getDesigns, type DesignListItem } from "../api";

const designs = ref<DesignListItem[]>([]);
const loading = ref(true);
const error = ref("");

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
  timeStyle: "short",
});

function formatUpdatedAt(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

onMounted(async () => {
  try {
    designs.value = await getDesigns();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load drafts";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="home-view">
    <div class="section-heading">
      <p class="eyebrow">Private workspace</p>
      <h1>My drafts</h1>
      <p class="summary">Open a saved draft and continue editing.</p>
    </div>

    <p v-if="loading" class="muted">Loading drafts...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>
    <div v-else-if="designs.length === 0" class="account-panel">
      <p class="eyebrow">No drafts</p>
      <h2>Your workspace is empty</h2>
      <RouterLink class="secondary-action" to="/">Start from templates</RouterLink>
    </div>

    <div v-else class="template-grid">
      <RouterLink
        v-for="design in designs"
        :key="design.id"
        :to="{ name: 'design-detail', params: { id: design.id } }"
        class="template-card"
      >
        <div class="template-preview" aria-hidden="true">
          <span>Draft #{{ design.id }}</span>
        </div>
        <div class="template-meta">
          <h3>{{ design.name }}</h3>
          <p>{{ design.widthMm }} x {{ design.heightMm }} mm</p>
          <p>Updated {{ formatUpdatedAt(design.updatedAt) }}</p>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
