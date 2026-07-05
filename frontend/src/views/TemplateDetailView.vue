<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { getTemplate, type TemplateDetail } from "../api";

const route = useRoute();
const template = ref<TemplateDetail | null>(null);
const loading = ref(true);
const error = ref("");

const templateId = computed(() => Number(route.params.id));
const canvasLayerCount = computed(() => {
  if (!template.value) {
    return 0;
  }

  try {
    const canvas = JSON.parse(template.value.canvasJson) as { layers?: unknown[] };
    return Array.isArray(canvas.layers) ? canvas.layers.length : 0;
  } catch {
    return 0;
  }
});

onMounted(async () => {
  if (!Number.isFinite(templateId.value)) {
    error.value = "Template id is invalid";
    loading.value = false;
    return;
  }

  try {
    template.value = await getTemplate(templateId.value);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load template";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="detail-view">
    <RouterLink class="back-link" to="/">Back to templates</RouterLink>

    <p v-if="loading" class="muted">Loading template...</p>
    <p v-else-if="error" class="error-text">{{ error }}</p>

    <article v-else-if="template" class="detail-shell">
      <div class="detail-preview" aria-hidden="true">
        <img
          v-if="template.previewUrl"
          :src="template.previewUrl"
          :alt="template.name"
        />
        <span v-else>{{ template.category }}</span>
      </div>

      <div class="detail-content">
        <p class="eyebrow">{{ template.category }}</p>
        <h1>{{ template.name }}</h1>
        <p class="summary">
          {{ template.widthMm }} x {{ template.heightMm }} mm canvas with
          {{ canvasLayerCount }} layer<span v-if="canvasLayerCount !== 1">s</span>.
        </p>

        <div class="detail-actions">
          <button class="primary-action" type="button" disabled>
            Open editor
          </button>
          <span class="muted">Editor starts in step 32.</span>
        </div>
      </div>
    </article>
  </section>
</template>
