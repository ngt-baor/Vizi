<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { X } from "@lucide/vue";
import { useRouter } from "vue-router";
import { createBlankDesign } from "../api";
import {
  customDesignSizeId,
  defaultDesignName,
  designSizePresets,
} from "../designPresets";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const router = useRouter();
const selectedId = ref(designSizePresets[0].id);
const customWidthMm = ref(90);
const customHeightMm = ref(54);
const designName = ref("");
const creating = ref(false);
const error = ref("");
const titleInput = ref<HTMLInputElement | null>(null);

const selectedPreset = computed(() =>
  designSizePresets.find((preset) => preset.id === selectedId.value) ?? null,
);
const widthMm = computed(() => selectedPreset.value?.widthMm ?? customWidthMm.value);
const heightMm = computed(() => selectedPreset.value?.heightMm ?? customHeightMm.value);
const validCustomSize = computed(() =>
  widthMm.value >= 10 && widthMm.value <= 200 && heightMm.value >= 10 && heightMm.value <= 200,
);

function close(): void {
  if (!creating.value) {
    emit("close");
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && props.open) {
    close();
  }
}

watch(
  () => props.open,
  async (open) => {
    document.removeEventListener("keydown", onKeydown);
    if (!open) {
      return;
    }
    error.value = "";
    document.addEventListener("keydown", onKeydown);
    await nextTick();
    titleInput.value?.focus();
  },
  { immediate: true },
);

onBeforeUnmount(() => document.removeEventListener("keydown", onKeydown));

async function createDesign(): Promise<void> {
  if (!validCustomSize.value || creating.value) {
    return;
  }
  creating.value = true;
  error.value = "";
  try {
    const design = await createBlankDesign({
      name: designName.value.trim() || defaultDesignName(widthMm.value, heightMm.value),
      widthMm: widthMm.value,
      heightMm: heightMm.value,
    });
    emit("close");
    await router.push({ name: "editor", params: { designId: String(design.id) } });
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot create design";
  } finally {
    creating.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="start-design-backdrop"
      role="presentation"
      data-start-design-dialog
      @mousedown.self="close"
    >
      <section class="start-design-dialog" role="dialog" aria-modal="true" aria-labelledby="start-design-title">
        <header>
          <div>
            <p class="eyebrow">New design</p>
            <h2 id="start-design-title">Start a design</h2>
          </div>
          <button type="button" aria-label="Close" title="Close" @click="close">
            <X :size="18" aria-hidden="true" />
          </button>
        </header>

        <label class="start-design-name">
          <span>Design name</span>
          <input ref="titleInput" v-model="designName" maxlength="160" placeholder="Business card" />
        </label>

        <div class="start-design-presets" role="radiogroup" aria-label="Card size">
          <button
            v-for="preset in designSizePresets"
            :key="preset.id"
            type="button"
            class="start-design-preset"
            :class="{ 'start-design-preset--selected': selectedId === preset.id }"
            role="radio"
            :aria-checked="selectedId === preset.id"
            @click="selectedId = preset.id"
          >
            <span class="start-design-preset__shape" :style="{ aspectRatio: `${preset.widthMm} / ${preset.heightMm}` }" />
            <span>
              <strong>{{ preset.name }}</strong>
              <small>{{ preset.widthMm }} x {{ preset.heightMm }} mm - {{ preset.note }}</small>
            </span>
          </button>
          <button
            type="button"
            class="start-design-preset"
            :class="{ 'start-design-preset--selected': selectedId === customDesignSizeId }"
            role="radio"
            :aria-checked="selectedId === customDesignSizeId"
            @click="selectedId = customDesignSizeId"
          >
            <span class="start-design-preset__shape start-design-preset__shape--custom">+</span>
            <span>
              <strong>Custom size</strong>
              <small>10-200 mm per side</small>
            </span>
          </button>
        </div>

        <div v-if="selectedId === customDesignSizeId" class="start-design-custom">
          <label>
            <span>Width (mm)</span>
            <input v-model.number="customWidthMm" type="number" min="10" max="200" step="0.1" />
          </label>
          <span aria-hidden="true">x</span>
          <label>
            <span>Height (mm)</span>
            <input v-model.number="customHeightMm" type="number" min="10" max="200" step="0.1" />
          </label>
        </div>

        <p v-if="!validCustomSize" class="error-text" role="alert">Each side must be between 10 and 200 mm.</p>
        <p v-if="error" class="error-text" role="alert">{{ error }}</p>

        <footer>
          <p>{{ widthMm }} x {{ heightMm }} mm - Front and back</p>
          <div>
            <button class="secondary-action" type="button" :disabled="creating" @click="close">Cancel</button>
            <button class="primary-action" type="button" :disabled="creating || !validCustomSize" @click="createDesign">
              {{ creating ? "Creating..." : "Create design" }}
            </button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
