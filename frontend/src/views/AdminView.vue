<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  adminGetDesign,
  adminListOrders,
  adminListTemplates,
  adminListUserDesigns,
  adminListUsers,
  adminPublishTemplate,
  adminSetTemplateActive,
  adminUpdateOrderStatus,
  getCurrentUser,
  getDesign,
  type AdminDesignDetail,
  type AdminDesignItem,
  type AdminOrder,
  type AdminTemplate,
  type AdminUser,
  type AuthUser,
} from "../api";
import TemplateThumbnail from "../components/TemplateThumbnail.vue";

const tab = ref<"orders" | "templates" | "albums">("orders");
const me = ref<AuthUser | null>(null);
const loading = ref(true);
const error = ref("");
const message = ref("");
const orders = ref<AdminOrder[]>([]);
const templates = ref<AdminTemplate[]>([]);
const users = ref<AdminUser[]>([]);
const selectedUserId = ref<number | null>(null);
const selectedUserName = ref("");
const album = ref<AdminDesignDetail[]>([]);
const albumLoading = ref(false);
const publishing = ref(false);
const publishDesignId = ref("");

const orderStatuses = ["PENDING_PAYMENT", "PAID", "PRINTING", "DONE", "CANCELLED"];

function emptyAdminDesign(item: AdminDesignItem): AdminDesignDetail {
  return {
    ...item,
    userEmail: "",
    canvasJson: JSON.stringify({ layers: [] }),
  };
}

async function refresh(): Promise<void> {
  error.value = "";
  me.value = await getCurrentUser();
  if (!me.value || me.value.role !== "ADMIN") {
    error.value = "Admin access required";
    return;
  }
  orders.value = await adminListOrders();
  templates.value = await adminListTemplates();
  users.value = await adminListUsers();
}

onMounted(async () => {
  try {
    await refresh();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load admin data";
  } finally {
    loading.value = false;
  }
});

async function setStatus(order: AdminOrder, status: string): Promise<void> {
  try {
    const updated = await adminUpdateOrderStatus(order.id, status);
    orders.value = orders.value.map((item) => (item.id === order.id ? updated : item));
    message.value = `Order #${order.id} -> ${status}`;
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot update order";
  }
}

async function toggleTemplate(template: AdminTemplate): Promise<void> {
  try {
    const updated = await adminSetTemplateActive(template.id, !template.active);
    templates.value = templates.value.map((item) => (item.id === template.id ? updated : item));
    message.value = `Template #${template.id} ${updated.active ? "published" : "unpublished"}`;
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot update template";
  }
}

async function loadAlbum(user: AdminUser): Promise<void> {
  selectedUserId.value = user.id;
  selectedUserName.value = user.fullName;
  albumLoading.value = true;
  error.value = "";
  try {
    const list = await adminListUserDesigns(user.id);
    album.value = await Promise.all(list.map(async (item) => {
      try {
        return await adminGetDesign(item.id);
      } catch {
        return emptyAdminDesign(item);
      }
    }));
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load album";
  } finally {
    albumLoading.value = false;
  }
}

async function publishFromDesign(): Promise<void> {
  const id = Number(publishDesignId.value);
  if (!Number.isFinite(id) || id <= 0) {
    error.value = "Enter a valid design id";
    return;
  }
  publishing.value = true;
  error.value = "";
  message.value = "";
  try {
    const design = await getDesign(id);
    const created = await adminPublishTemplate({
      name: design.name,
      category: "user",
      previewUrl: null,
      widthMm: design.widthMm,
      heightMm: design.heightMm,
      canvasJson: design.canvasJson,
      active: true,
    });
    templates.value = await adminListTemplates();
    message.value = `Published template #${created.id} from design #${id}`;
    publishDesignId.value = "";
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot publish template";
  } finally {
    publishing.value = false;
  }
}

function money(value: number): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
}
</script>

<template>
  <section class="home-view admin-view">
    <div class="section-heading admin-heading">
      <p class="eyebrow">Admin</p>
      <h1>Console</h1>
      <p class="summary">Quản lý đơn in, template và album thiết kế của người dùng.</p>
    </div>

    <p v-if="loading" class="muted">Loading admin console...</p>
    <p v-else-if="error && !me" class="error-text" role="alert">
      {{ error }}
      <RouterLink to="/account">Sign in as admin</RouterLink>
    </p>

    <template v-else-if="me?.role === 'ADMIN'">
      <div class="mode-control admin-mode-control" aria-label="Admin sections">
        <button type="button" :class="{ active: tab === 'orders' }" @click="tab = 'orders'">Orders</button>
        <button type="button" :class="{ active: tab === 'templates' }" @click="tab = 'templates'">Templates</button>
        <button type="button" :class="{ active: tab === 'albums' }" @click="tab = 'albums'">User albums</button>
      </div>

      <p v-if="message" class="save-status" role="status">{{ message }}</p>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>

      <div v-if="tab === 'orders'" class="account-panel admin-panel">
        <h3>Incoming orders ({{ orders.length }})</h3>
        <p v-if="orders.length === 0" class="muted">No orders yet.</p>
        <div v-for="order in orders" :key="order.id" class="admin-row admin-order-row">
          <div>
            <strong>#{{ order.id }} - {{ order.status }}</strong>
            <p class="muted">
              {{ order.userFullName }} - {{ order.userEmail }} - {{ money(order.totalAmount) }}
            </p>
          </div>
          <label>
            Status
            <select :value="order.status" @change="setStatus(order, ($event.target as HTMLSelectElement).value)">
              <option v-for="status in orderStatuses" :key="status" :value="status">{{ status }}</option>
            </select>
          </label>
        </div>
      </div>

      <div v-else-if="tab === 'templates'" class="account-panel admin-panel">
        <div class="admin-panel-header">
          <div>
            <h3>Templates ({{ templates.length }})</h3>
            <p class="muted">Xem trước template trước khi publish/unpublish.</p>
          </div>
          <form class="admin-publish" @submit.prevent="publishFromDesign">
            <label>
              Publish design id as template
              <input v-model="publishDesignId" type="number" min="1" placeholder="e.g. 74" />
            </label>
            <button class="primary-action" type="submit" :disabled="publishing">
              {{ publishing ? "Publishing..." : "Publish" }}
            </button>
          </form>
        </div>

        <div class="admin-template-grid">
          <article v-for="template in templates" :key="template.id" class="admin-template-card">
            <TemplateThumbnail
              :name="template.name"
              :preview-url="template.previewUrl"
              :canvas-json="template.canvasJson"
              :width-mm="template.widthMm"
              :height-mm="template.heightMm"
            />
            <div class="admin-card-meta">
              <strong>#{{ template.id }} - {{ template.name }}</strong>
              <p class="muted">
                {{ template.category }} - {{ template.widthMm }}x{{ template.heightMm }} mm -
                {{ template.active ? "ACTIVE" : "INACTIVE" }}
              </p>
              <button class="secondary-action" type="button" @click="toggleTemplate(template)">
                {{ template.active ? "Unpublish" : "Publish" }}
              </button>
            </div>
          </article>
        </div>
      </div>

      <div v-else class="account-panel admin-panel">
        <h3>Users & albums</h3>
        <p v-if="!selectedUserId" class="muted">Select a user to preview their designs.</p>
        <div
          class="admin-split admin-album-layout"
          :class="{ 'admin-album-layout--selected': !!selectedUserId }"
        >
          <div class="admin-user-list">
            <div
              v-for="user in users"
              :key="user.id"
              class="admin-row admin-user-row"
              :class="{ active: selectedUserId === user.id }"
            >
              <div>
                <strong>{{ user.fullName }}</strong>
                <p class="muted">{{ user.email }} - {{ user.role }} - {{ user.designCount }} designs</p>
              </div>
              <button class="secondary-action" type="button" @click="loadAlbum(user)">View album</button>
            </div>
          </div>
          <div v-if="selectedUserId" class="admin-album-panel">
            <h4>{{ selectedUserName }} - {{ album.length }} designs</h4>
            <p v-if="albumLoading" class="muted">Loading album previews...</p>
            <p v-else-if="album.length === 0" class="muted">No designs.</p>

            <div v-else class="admin-template-grid admin-album-grid">
              <article v-for="design in album" :key="design.id" class="admin-template-card">
                <TemplateThumbnail
                  :name="design.name"
                  :preview-url="null"
                  :canvas-json="design.canvasJson"
                  :width-mm="design.widthMm"
                  :height-mm="design.heightMm"
                />
                <div class="admin-card-meta">
                  <strong>#{{ design.id }} - {{ design.name }}</strong>
                  <p class="muted">{{ design.widthMm }}x{{ design.heightMm }} mm</p>
                  <button
                    class="secondary-action"
                    type="button"
                    @click="publishDesignId = String(design.id); tab = 'templates'"
                  >
                    Publish
                  </button>
                </div>
              </article>
            </div>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>