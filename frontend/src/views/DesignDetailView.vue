<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { deleteDesign, getDesign, type DesignDetail } from "../api";
import CanvasPreview from "../components/CanvasPreview.vue";
import { type EditorSide } from "../editor-v2/document";
import {
  readEditorPreviewPages,
  type EditorPreviewPage,
} from "../editor-v2/preview";

const route = useRoute();
const router = useRouter();
const design = ref<DesignDetail | null>(null);
const activeSide = ref<EditorSide>("front");
const previewPages = ref<Record<EditorSide, EditorPreviewPage>>({
  front: { background: "#fffdf8", layers: [] },
  back: { background: "#fffdf8", layers: [] },
});
const loading = ref(true);
const error = ref("");
const deleting = ref(false);
const confirmingDelete = ref(false);
const sides: EditorSide[] = ["front", "back"];

const activePage = computed(() => previewPages.value[activeSide.value]);
const totalLayerCount = computed(
  () => previewPages.value.front.layers.length + previewPages.value.back.layers.length,
);
const updatedAt = computed(() => {
  if (!design.value) {
    return "";
  }
  const date = new Date(design.value.updatedAt);
  return Number.isNaN(date.getTime())
    ? design.value.updatedAt
    : new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
});

function routeDesignId(): number | null {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id;
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

async function deleteDraft(): Promise<void> {
  if (!design.value || deleting.value) {
    return;
  }
  if (!confirmingDelete.value) {
    confirmingDelete.value = true;
    error.value = "";
    return;
  }

  deleting.value = true;
  error.value = "";
  try {
    await deleteDesign(design.value.id);
    await router.push({ name: "designs" });
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot delete draft";
    confirmingDelete.value = false;
  } finally {
    deleting.value = false;
  }
}

onMounted(async () => {
  const id = routeDesignId();
  if (id === null) {
    error.value = "Draft id is invalid";
    loading.value = false;
    return;
  }

  try {
    design.value = await getDesign(id);
    previewPages.value = readEditorPreviewPages(design.value.canvasJson);
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load draft";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="detail-view">
    <RouterLink class="back-link" to="/designs">Back to drafts</RouterLink>

    <p v-if="loading" class="muted">Loading draft...</p>
    <p v-else-if="error && !design" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>

    <article v-else-if="design" class="detail-shell">
      <section class="detail-preview" aria-label="Draft preview">
        <div class="detail-preview__toolbar">
          <strong>{{ activeSide === "front" ? "Front side" : "Back side" }}</strong>
          <div class="checkout-side-switch" aria-label="Card side">
            <button
              v-for="side in sides"
              :key="side"
              type="button"
              :class="{ active: activeSide === side }"
              :aria-pressed="activeSide === side"
              @click="activeSide = side"
            >
              {{ side === "front" ? "Front" : "Back" }}
            </button>
          </div>
        </div>
        <CanvasPreview
          :layers="activePage.layers"
          :width-mm="design.widthMm"
          :height-mm="design.heightMm"
          :background="activePage.background"
          :label="`${activeSide} side of ${design.name}`"
          :empty-label="activeSide === 'front' ? 'Front' : 'Back'"
        />
      </section>

      <div class="detail-content">
        <p class="eyebrow">Draft #{{ design.id }}</p>
        <h1>{{ design.name }}</h1>
        <p class="summary">
          {{ design.widthMm }} x {{ design.heightMm }} mm business card with
          {{ totalLayerCount }} {{ totalLayerCount === 1 ? "layer" : "layers" }} across Front and Back.
        </p>
        <p class="muted">Updated {{ updatedAt }}</p>

        <div class="detail-actions">
          <RouterLink
            class="primary-action"
            :to="{ name: 'editor', params: { designId: design.id } }"
          >
            Open editor
          </RouterLink>
          <RouterLink
            class="secondary-action"
            :to="{ name: 'checkout', params: { designId: design.id } }"
          >
            Checkout
          </RouterLink>
          <button
            class="danger-action"
            type="button"
            :disabled="deleting"
            @click="deleteDraft"
          >
            {{ deleting ? "Deleting..." : confirmingDelete ? "Confirm delete" : "Delete draft" }}
          </button>
        </div>
        <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      </div>
    </article>
  </section>
</template>
