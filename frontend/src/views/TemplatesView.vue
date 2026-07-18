<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { getTemplates, type TemplateListItem } from "../api";
import StartDesignDialog from "../components/StartDesignDialog.vue";
import TemplateThumbnail from "../components/TemplateThumbnail.vue";

const templates = ref<TemplateListItem[]>([]);
const loading = ref(true);
const error = ref("");
const startDialogOpen = ref(false);

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
    <div class="section-heading section-heading--with-action">
      <div>
        <p class="eyebrow">Templates</p>
        <h1>Saved layouts</h1>
        <p class="summary">Choose a business-card layout or start with a blank card.</p>
      </div>
      <button class="primary-action" type="button" @click="startDialogOpen = true">Start a design</button>
    </div>

    <p v-if="loading" class="muted">Loading templates...</p>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>

    <div v-if="!loading && templates.length === 0" class="empty-templates">
      <p class="muted">No active templates yet.</p>
      <button class="primary-action" type="button" @click="startDialogOpen = true">Start a design</button>
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

    <StartDesignDialog :open="startDialogOpen" @close="startDialogOpen = false" />
  </section>
</template>
