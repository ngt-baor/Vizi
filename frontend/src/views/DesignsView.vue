<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ShoppingCart, Trash2 } from "@lucide/vue";
import { RouterLink } from "vue-router";
import {
  deleteDesign,
  getDesign,
  getDesigns,
  updateDesign,
  type DesignDetail,
} from "../api";
import { addCartDesign, getCartDesignIds, removeCartDesign } from "../cart";
import CanvasPreview from "../components/CanvasPreview.vue";
import ConfirmDeleteDialog from "../components/ConfirmDeleteDialog.vue";
import StartDesignDialog from "../components/StartDesignDialog.vue";
import {
  readEditorPreviewPages,
  type EditorPreviewLayer,
} from "../editor-v2/preview";

type DraftCard = DesignDetail & {
  frontBackground: string;
  frontLayers: EditorPreviewLayer[];
};

const designs = ref<DraftCard[]>([]);
const loading = ref(true);
const error = ref("");
const renamingId = ref<number | null>(null);
const renameError = ref("");
const deleteError = ref("");
const deletingId = ref<number | null>(null);
const deleteTarget = ref<DraftCard | null>(null);
const cartDesignIds = ref(new Set(getCartDesignIds()));
const startDialogOpen = ref(false);

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
  timeStyle: "short",
});

function formatUpdatedAt(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

function toDraftCard(design: DesignDetail): DraftCard {
  const front = readEditorPreviewPages(design.canvasJson).front;
  return {
    ...design,
    frontBackground: front.background,
    frontLayers: front.layers,
  };
}

async function loadDesigns(): Promise<void> {
  const list = await getDesigns();
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

function addDraftToCart(designId: number, event: Event): void {
  event.preventDefault();
  event.stopPropagation();
  addCartDesign(designId);
  cartDesignIds.value = new Set(getCartDesignIds());
}

function openDeleteDialog(design: DraftCard, event: Event): void {
  event.preventDefault();
  event.stopPropagation();
  deleteError.value = "";
  deleteTarget.value = design;
}

function closeDeleteDialog(): void {
  if (deletingId.value === null) {
    deleteTarget.value = null;
  }
}

function deleteDialogTitle(): string {
  return `Delete "${deleteTarget.value?.name ?? "draft"}"?`;
}

async function confirmDeleteDraft(): Promise<void> {
  const target = deleteTarget.value;
  if (!target || deletingId.value !== null) {
    return;
  }

  deletingId.value = target.id;
  deleteError.value = "";
  try {
    await deleteDesign(target.id);
    removeCartDesign(target.id);
    cartDesignIds.value = new Set(getCartDesignIds());
    designs.value = designs.value.filter((design) => design.id !== target.id);
    deleteTarget.value = null;
  } catch (unknownError) {
    deleteError.value = unknownError instanceof Error
      ? unknownError.message
      : "Cannot delete draft";
  } finally {
    deletingId.value = null;
  }
}
</script>

<template>
  <section class="home-view">
    <div class="section-heading section-heading--with-action">
      <div>
        <p class="eyebrow">Private workspace</p>
        <h1>My drafts</h1>
        <p class="summary">Open a saved draft or start with a new card size.</p>
      </div>
      <button class="primary-action" type="button" @click="startDialogOpen = true">Start a design</button>
    </div>

    <p v-if="loading" class="muted">Loading drafts...</p>
    <p v-else-if="error" class="error-text" role="alert">
      {{ error }}
      <RouterLink v-if="error.includes('Sign in')" to="/account">Open account</RouterLink>
    </p>
    <p v-else-if="renameError || deleteError" class="error-text" role="alert">
      {{ renameError || deleteError }}
    </p>
    <div v-else-if="designs.length === 0" class="account-panel">
      <p class="eyebrow">No drafts</p>
      <h2>Your workspace is empty</h2>
      <div class="draft-card-actions">
        <button class="primary-action" type="button" @click="startDialogOpen = true">Start a design</button>
        <RouterLink class="secondary-action" to="/templates">Start from templates</RouterLink>
      </div>
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
                :background="design.frontBackground"
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
            <button
              type="button"
              class="secondary-action button-with-icon"
              :disabled="cartDesignIds.has(design.id)"
              @click="addDraftToCart(design.id, $event)"
            >
              <ShoppingCart :size="15" aria-hidden="true" />
              {{ cartDesignIds.has(design.id) ? "In cart" : "Add to cart" }}
            </button>
            <button
              type="button"
              class="danger-action button-with-icon"
              :disabled="deletingId === design.id"
              @click="openDeleteDialog(design, $event)"
            >
              <Trash2 :size="15" aria-hidden="true" />
              Delete
            </button>
          </div>
        </div>
      </article>
    </div>

    <StartDesignDialog :open="startDialogOpen" @close="startDialogOpen = false" />
    <ConfirmDeleteDialog
      :open="deleteTarget !== null"
      :title="deleteDialogTitle()"
      description="This draft and its saved history will be permanently deleted. This action cannot be undone."
      :busy="deletingId !== null"
      @cancel="closeDeleteDialog"
      @confirm="confirmDeleteDraft"
    />
  </section>
</template>