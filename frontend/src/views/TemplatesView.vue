<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { getTemplates, type TemplateListItem } from "../api";

const templates = ref<TemplateListItem[]>([]);
const loading = ref(true);
const error = ref("");

onMounted(async () => {
  try {
    templates.value = await getTemplates();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load templates";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="home-view">
    <div class="section-heading">
      <p class="eyebrow">Templates</p>
      <h1>Saved layouts</h1>
      <p class="summary">Choose a business-card layout and open its canvas.</p>
    </div>

    <p v-if="loading" class="muted">Loading templates...</p>
    <p v-else-if="error" class="error-text" role="alert">{{ error }}</p>
    <p v-else-if="templates.length === 0" class="muted">No active templates yet.</p>

    <div v-else class="template-grid">
      <RouterLink
        v-for="template in templates"
        :key="template.id"
        :to="{ name: 'template-detail', params: { id: template.id } }"
        class="template-card"
      >
        <div class="template-preview" aria-hidden="true">
          <img
            v-if="template.previewUrl"
            :src="template.previewUrl"
            :alt="template.name"
          />
          <span v-else>{{ template.category }}</span>
        </div>
        <div class="template-meta">
          <h3>{{ template.name }}</h3>
          <p>{{ template.category }} - {{ template.widthMm }} x {{ template.heightMm }} mm</p>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
