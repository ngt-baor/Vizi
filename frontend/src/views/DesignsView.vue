<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { getDesign, getDesigns, updateDesign, type DesignDetail } from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";

type CanvasLayer = Record<string, unknown> & {
  type?: string;
  page?: string;
};

type DraftCard = DesignDetail & {
  frontLayers: CanvasLayer[];
};

const designs = ref<DraftCard[]>([]);
const loading = ref(true);
const error = ref("");
const renamingId = ref<number | null>(null);
const renameError = ref("");

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
  timeStyle: "short",
});

function formatUpdatedAt(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

function parseFrontLayers(canvasJson: string): CanvasLayer[] {
  try {
    const canvas = JSON.parse(canvasJson) as { layers?: unknown };
    if (!Array.isArray(canvas.layers)) {
      return [];
    }
    // Same rule as editor Front page: missing page => front.
    return canvas.layers.filter((layer): layer is CanvasLayer => {
      if (typeof layer !== "object" || layer === null) {
        return false;
      }
      const page = (layer as CanvasLayer).page;
      return page !== "back";
    });
  } catch {
    return [];
  }
}

function toDraftCard(design: DesignDetail): DraftCard {
  return {
    ...design,
    frontLayers: parseFrontLayers(design.canvasJson),
  };
}

async function loadDesigns(): Promise<void> {
  const list = await getDesigns();
  // List API is metadata-only; load canvas so thumbnail matches editor front.
  const details = await Promise.all(list.map((item) => getDesign(item.id)));
  designs.value = details.map(toDraftCard);
}

onMounted(async () => {
  try {
    await loadDesigns();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load drafts";
  } finally {
    loading.value = false;
  }
});

async function renameDraft(design: DraftCard, event: Event): Promise<void> {
  event.preventDefault();
  event.stopPropagation();
  renameError.value = "";
  const next = window.prompt("Draft name", design.name);
  if (next === null) {
    return;
  }
  const trimmed = next.trim().slice(0, 160);
  if (!trimmed || trimmed === design.name) {
    return;
  }

  renamingId.value = design.id;
  try {
    const saved = await updateDesign(design.id, trimmed, design.canvasJson);
    designs.value = designs.value.map((item) =>
      item.id === design.id ? toDraftCard(saved) : item,
    );
  } catch (unknownError) {
    renameError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot rename draft";
  } finally {
    renamingId.value = null;
  }
}
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
    <p v-else-if="renameError" class="error-text" role="alert">{{ renameError }}</p>
    <div v-else-if="designs.length === 0" class="account-panel">
      <p class="eyebrow">No drafts</p>
      <h2>Your workspace is empty</h2>
      <RouterLink class="secondary-action" to="/">Start from templates</RouterLink>
    </div>

    <div v-else class="template-grid">
      <article
        v-for="design in designs"
        :key="design.id"
        class="template-card draft-card"
      >
        <RouterLink
          :to="{ name: 'editor', params: { designId: design.id } }"
          class="draft-card-link"
        >
          <div class="template-preview draft-card-preview" aria-hidden="true">
            <div
              class="draft-card-stage"
              :style="{
                aspectRatio: `${design.widthMm} / ${design.heightMm}`,
              }"
            >
              <CanvasPreview
                v-if="design.frontLayers.length > 0"
                :layers="design.frontLayers"
                :width-mm="design.widthMm"
                :height-mm="design.heightMm"
                :label="`${design.name} preview`"
                empty-label="Empty"
              />
              <span v-else class="draft-card-fallback">Empty card</span>
            </div>
          </div>
        </RouterLink>
        <div class="template-meta draft-card-meta">
          <h3>{{ design.name }}</h3>
          <p>{{ design.widthMm }} x {{ design.heightMm }} mm - #{{ design.id }}</p>
          <p>Updated {{ formatUpdatedAt(design.updatedAt) }}</p>
          <div class="draft-card-actions">
            <RouterLink
              class="secondary-action"
              :to="{ name: 'editor', params: { designId: design.id } }"
            >
              Open editor
            </RouterLink>
            <button
              type="button"
              class="secondary-action"
              :disabled="renamingId === design.id"
              @click="renameDraft(design, $event)"
            >
              {{ renamingId === design.id ? "Saving..." : "Rename" }}
            </button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
