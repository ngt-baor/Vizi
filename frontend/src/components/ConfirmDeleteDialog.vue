<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Trash2, X } from "@lucide/vue";

const props = defineProps<{
  open: boolean;
  title: string;
  description: string;
  busy?: boolean;
  confirmLabel?: string;
}>();
const emit = defineEmits<{
  cancel: [];
  confirm: [];
}>();

const cancelButton = ref<HTMLButtonElement | null>(null);

function cancel(): void {
  if (!props.busy) {
    emit("cancel");
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && props.open) {
    cancel();
  }
}

watch(
  () => props.open,
  async (open) => {
    document.removeEventListener("keydown", onKeydown);
    if (!open) {
      return;
    }
    document.addEventListener("keydown", onKeydown);
    await nextTick();
    cancelButton.value?.focus();
  },
  { immediate: true },
);

onBeforeUnmount(() => document.removeEventListener("keydown", onKeydown));
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="start-design-backdrop"
      role="presentation"
      data-confirm-delete-dialog
      @mousedown.self="cancel"
    >
      <section class="confirm-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-delete-title">
        <button
          class="confirm-delete-dialog__close"
          type="button"
          aria-label="Close"
          title="Close"
          :disabled="busy"
          @click="cancel"
        >
          <X :size="18" aria-hidden="true" />
        </button>
        <span class="confirm-delete-dialog__icon" aria-hidden="true">
          <Trash2 :size="22" />
        </span>
        <p class="eyebrow">Delete draft</p>
        <h2 id="confirm-delete-title">{{ title }}</h2>
        <p>{{ description }}</p>
        <div class="confirm-delete-dialog__actions">
          <button ref="cancelButton" class="secondary-action" type="button" :disabled="busy" @click="cancel">
            Cancel
          </button>
          <button class="danger-action button-with-icon" type="button" :disabled="busy" @click="emit('confirm')">
            <Trash2 :size="16" aria-hidden="true" />
            {{ busy ? "Deleting..." : confirmLabel ?? "Delete draft" }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
