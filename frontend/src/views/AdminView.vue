<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  adminCreatePaper,
  adminDeletePaper,
  adminGetDesign,
  adminListOrders,
  adminListPapers,
  adminListTemplates,
  adminListUserDesigns,
  adminListUsers,
  adminPublishTemplate,
  adminSetTemplateActive,
  adminUpdateOrderStatus,
  adminUpdatePaper,
  getCurrentUser,
  getDesign,
  type AdminDesignDetail,
  type AdminDesignItem,
  type AdminOrder,
  type AdminTemplate,
  type AdminUser,
  type AuthUser,
  type PaperStock,
  type PaperStockInput,
} from "../api";
import AdminPrintPanel from "../components/AdminPrintPanel.vue";
import TemplateThumbnail from "../components/TemplateThumbnail.vue";

type AdminTab = "orders" | "templates" | "papers" | "albums" | "print";

const tab = ref<AdminTab>("orders");
const me = ref<AuthUser | null>(null);
const loading = ref(true);
const error = ref("");
const message = ref("");
const orders = ref<AdminOrder[]>([]);
const templates = ref<AdminTemplate[]>([]);
const papers = ref<PaperStock[]>([]);
const users = ref<AdminUser[]>([]);
const selectedUserId = ref<number | null>(null);
const selectedUserName = ref("");
const album = ref<AdminDesignDetail[]>([]);
const albumLoading = ref(false);
const publishing = ref(false);
const publishDesignId = ref("");
const paperSaving = ref(false);
const editingPaperId = ref<number | null>(null);
const paperForm = ref<PaperStockInput>(emptyPaperForm());

const orderStatuses = ["PENDING_PAYMENT", "PAID", "PRINTING", "DONE", "CANCELLED"];

function emptyPaperForm(): PaperStockInput {
  return {
    code: "",
    name: "",
    description: null,
    gsm: 350,
    pricePer100: 180000,
    status: "IN_STOCK",
    active: true,
  };
}

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
  const [loadedOrders, loadedTemplates, loadedPapers, loadedUsers] = await Promise.all([
    adminListOrders(),
    adminListTemplates(),
    adminListPapers(),
    adminListUsers(),
  ]);
  orders.value = loadedOrders;
  templates.value = loadedTemplates;
  papers.value = loadedPapers;
  users.value = loadedUsers;
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

function editPaper(paper: PaperStock): void {
  editingPaperId.value = paper.id;
  paperForm.value = {
    code: paper.code,
    name: paper.name,
    description: paper.description,
    gsm: paper.gsm,
    pricePer100: paper.pricePer100,
    status: paper.status,
    active: paper.active,
  };
  message.value = "";
  error.value = "";
}

function resetPaperForm(): void {
  editingPaperId.value = null;
  paperForm.value = emptyPaperForm();
}

async function savePaper(): Promise<void> {
  paperSaving.value = true;
  error.value = "";
  message.value = "";
  try {
    if (editingPaperId.value) {
      const updated = await adminUpdatePaper(editingPaperId.value, paperForm.value);
      papers.value = papers.value.map((item) => item.id === updated.id ? updated : item);
      message.value = `Updated paper #${updated.id}`;
    } else {
      const created = await adminCreatePaper(paperForm.value);
      papers.value = [...papers.value, created];
      message.value = `Created paper #${created.id}`;
    }
    resetPaperForm();
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot save paper";
  } finally {
    paperSaving.value = false;
  }
}

async function removePaper(paper: PaperStock): Promise<void> {
  if (!window.confirm(`Delete paper "${paper.name}"?`)) {
    return;
  }
  error.value = "";
  message.value = "";
  try {
    await adminDeletePaper(paper.id);
    papers.value = papers.value.filter((item) => item.id !== paper.id);
    if (editingPaperId.value === paper.id) {
      resetPaperForm();
    }
    message.value = `Deleted paper #${paper.id}`;
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot delete paper";
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
      <p class="summary">Manage print orders, templates, paper stock and customer designs.</p>
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
        <button type="button" :class="{ active: tab === 'papers' }" @click="tab = 'papers'">Paper stock</button>
        <button type="button" :class="{ active: tab === 'albums' }" @click="tab = 'albums'">User albums</button>
        <button type="button" :class="{ active: tab === 'print' }" @click="tab = 'print'">Print</button>
      </div>

      <p v-if="message" class="save-status" role="status">{{ message }}</p>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>

      <div v-if="tab === 'print'" class="account-panel admin-panel admin-print-root-wrapper">
        <AdminPrintPanel :orders="orders" :templates="templates" />
      </div>

      <div v-else-if="tab === 'orders'" class="account-panel admin-panel">
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
            <p class="muted">Preview templates before publishing or unpublishing them.</p>
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

      <div v-else-if="tab === 'papers'" class="account-panel admin-panel">
        <div class="admin-panel-header">
          <div>
            <h3>Paper stock ({{ papers.length }})</h3>
            <p class="muted">Paper names, prices and availability used by checkout.</p>
          </div>
          <button v-if="editingPaperId" class="secondary-action" type="button" @click="resetPaperForm">
            Add new paper
          </button>
        </div>

        <form class="admin-paper-form" @submit.prevent="savePaper">
          <label>
            Code
            <input v-model.trim="paperForm.code" required maxlength="64" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="couche-350" />
          </label>
          <label>
            Name
            <input v-model.trim="paperForm.name" required maxlength="160" placeholder="Couche 350gsm" />
          </label>
          <label>
            GSM
            <input v-model.number="paperForm.gsm" type="number" min="1" max="2000" placeholder="350" />
          </label>
          <label>
            Price per 100
            <input v-model.number="paperForm.pricePer100" type="number" min="0" step="1000" required />
          </label>
          <label>
            Stock status
            <select v-model="paperForm.status">
              <option value="IN_STOCK">In stock</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
            </select>
          </label>
          <label class="admin-paper-description">
            Description
            <input v-model.trim="paperForm.description" maxlength="500" placeholder="Surface and print characteristics" />
          </label>
          <label class="checkout-toggle admin-paper-active">
            <input v-model="paperForm.active" type="checkbox">
            <span>Visible at checkout</span>
          </label>
          <button class="primary-action" type="submit" :disabled="paperSaving">
            {{ paperSaving ? "Saving..." : editingPaperId ? "Save changes" : "Add paper" }}
          </button>
        </form>

        <div class="admin-paper-list">
          <div v-for="paperItem in papers" :key="paperItem.id" class="admin-row admin-paper-row">
            <div>
              <strong>{{ paperItem.name }}</strong>
              <p class="muted">
                {{ paperItem.code }} - {{ paperItem.gsm ? paperItem.gsm + "gsm" : "No GSM" }} -
                {{ money(paperItem.pricePer100) }} / 100
              </p>
            </div>
            <span class="admin-paper-status" :class="{ 'admin-paper-status--out': paperItem.status === 'OUT_OF_STOCK' }">
              {{ paperItem.status === "IN_STOCK" ? "In stock" : "Out of stock" }}
            </span>
            <span>{{ paperItem.active ? "Visible" : "Hidden" }}</span>
            <div class="admin-paper-actions">
              <button class="secondary-action" type="button" @click="editPaper(paperItem)">Edit</button>
              <button class="secondary-action admin-danger-action" type="button" @click="removePaper(paperItem)">Delete</button>
            </div>
          </div>
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