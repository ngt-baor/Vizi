<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { createBlankDesign, getTemplates, type TemplateListItem } from "../api";
import TemplateThumbnail from "../components/TemplateThumbnail.vue";
import { geoRedCanvasJson } from "../geoRedCanvas";

const router = useRouter();
const templates = ref<TemplateListItem[]>([]);
const loading = ref(true);
const error = ref("");
const creating = ref(false);

const geoRedCanvas = geoRedCanvasJson;

onMounted(async () => {
  try {
    templates.value = await getTemplates();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load templates";
  } finally {
    loading.value = false;
  }
});

async function startGeoRedDraft(): Promise<void> {
  creating.value = true;
  error.value = "";
  try {
    const design = await createBlankDesign({
      name: "Geo Red Corporate Card",
      widthMm: 90,
      heightMm: 54,
      canvasJson: geoRedCanvas,
    });
    await router.push({ name: "editor", params: { designId: String(design.id) } });
  } catch (unknownError) {
    error.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot create draft — sign in and ensure backend is restarted";
  } finally {
    creating.value = false;
  }
}
</script>

<template>
  <section class="home-view">
    <div class="section-heading">
      <p class="eyebrow">Templates</p>
      <h1>Saved layouts</h1>
      <p class="summary">Choose a business-card layout and open its canvas.</p>
    </div>

    <p v-if="loading" class="muted">Loading templates...</p>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>

    <div v-if="!loading && templates.length === 0" class="empty-templates">
      <p class="muted">No active templates yet.</p>
      <button
        class="primary-action"
        type="button"
        :disabled="creating"
        @click="startGeoRedDraft"
      >
        {{ creating ? "Creating..." : "Start Geo Red Corporate Card" }}
      </button>
      <p class="muted" style="margin-top: 10px; font-size: 13px">
        Creates a draft with editable rect/text layers (no full-card image paste). Requires sign-in + backend support for blank drafts.
      </p>
    </div>

    <div v-else-if="!loading" class="template-grid">
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
</template>
