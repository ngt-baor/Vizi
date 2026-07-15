<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  apiBaseUrl,
  getHealth,
  getTemplates,
  type HealthResponse,
  type TemplateListItem,
} from "../api";
import TemplateThumbnail from "../components/TemplateThumbnail.vue";

const health = ref<HealthResponse | null>(null);
const templates = ref<TemplateListItem[]>([]);
const healthError = ref("");
const templateError = ref("");
const healthLoading = ref(true);
const templatesLoading = ref(true);

onMounted(async () => {
  try {
    health.value = await getHealth();
  } catch (unknownError) {
    healthError.value = unknownError instanceof Error ? unknownError.message : "Cannot reach backend";
  } finally {
    healthLoading.value = false;
  }

  try {
    templates.value = await getTemplates();
  } catch (unknownError) {
    templateError.value = unknownError instanceof Error ? unknownError.message : "Cannot load templates";
  } finally {
    templatesLoading.value = false;
  }
});
</script>

<template>
  <section class="home-view">
    <div class="brand-panel">
      <p class="eyebrow">Vizi</p>
      <h1>Luxury card studio</h1>
      <p class="summary">
        Choose a refined business-card layout, inspect its canvas, then move into editing.
        API target:
        <code>{{ apiBaseUrl }}</code>
      </p>
    </div>

    <div class="status-panel" aria-label="Backend status">
      <span
        class="status-dot"
        :class="{ 'status-dot--error': healthError }"
        aria-hidden="true"
      ></span>
      <span v-if="healthLoading">Checking backend...</span>
      <span v-else-if="health">Backend {{ health.service }}: {{ health.status }}</span>
      <span v-else>{{ healthError }}</span>
    </div>

    <section class="template-section" aria-labelledby="template-heading">
      <div class="section-heading">
        <p class="eyebrow">Templates</p>
        <h2 id="template-heading">Start from a saved layout</h2>
      </div>

      <p v-if="templatesLoading" class="muted">Loading templates...</p>
      <p v-else-if="templateError" class="error-text">{{ templateError }}</p>
      <p v-else-if="templates.length === 0" class="muted">No active templates yet.</p>

      <div v-else class="template-grid">
        <RouterLink
          v-for="template in templates"
          :key="template.id"
          :to="{ name: 'template-detail', params: { id: template.id } }"
          class="template-card"
        >
          <TemplateThumbnail
            :name="template.name"
            :preview-url="template.previewUrl"
            :canvas-json="template.canvasJson"
            :width-mm="template.widthMm"
            :height-mm="template.heightMm"
          />
          <div class="template-meta">
            <h3>{{ template.name }}</h3>
            <p>{{ template.category }} - {{ template.widthMm }} x {{ template.heightMm }} mm</p>
          </div>
        </RouterLink>
      </div>
    </section>
  </section>
</template>
