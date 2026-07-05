<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiBaseUrl, getHealth, type HealthResponse } from "../api";

const health = ref<HealthResponse | null>(null);
const error = ref("");
const loading = ref(true);

onMounted(async () => {
  try {
    health.value = await getHealth();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot reach backend";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="home-view">
    <div class="brand-panel">
      <p class="eyebrow">Vizi</p>
      <h1>Card visit editor</h1>
      <p class="summary">
        Frontend Vue workspace is connected to backend API target:
        <code>{{ apiBaseUrl }}</code>
      </p>
    </div>

    <div class="status-panel" aria-label="Backend status">
      <span
        class="status-dot"
        :class="{ 'status-dot--error': error }"
        aria-hidden="true"
      ></span>
      <span v-if="loading">Checking backend...</span>
      <span v-else-if="health">Backend {{ health.service }}: {{ health.status }}</span>
      <span v-else>{{ error }}</span>
    </div>
  </section>
</template>
