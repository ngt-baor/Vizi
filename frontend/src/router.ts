import { createRouter, createWebHistory } from "vue-router";
import HomeView from "./views/HomeView.vue";
import AccountView from "./views/AccountView.vue";
import AdminView from "./views/AdminView.vue";
import CheckoutView from "./views/CheckoutView.vue";
import DesignDetailView from "./views/DesignDetailView.vue";
import DesignsView from "./views/DesignsView.vue";
import EditorV2View from "./views/EditorV2View.vue";
import OrderDetailView from "./views/OrderDetailView.vue";
import TemplateDetailView from "./views/TemplateDetailView.vue";
import TemplatesView from "./views/TemplatesView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/account",
      name: "account",
      component: AccountView,
    },
    {
      path: "/admin",
      name: "admin",
      component: AdminView,
    },
    {
      path: "/designs",
      name: "designs",
      component: DesignsView,
    },
    {
      path: "/templates",
      name: "templates",
      component: TemplatesView,
    },
    {
      path: "/editor/:designId",
      name: "editor",
      component: EditorV2View,
    },
    {
      path: "/checkout/:designId",
      name: "checkout",
      component: CheckoutView,
    },
    {
      path: "/orders/:orderId",
      name: "order-detail",
      component: OrderDetailView,
    },
    {
      path: "/designs/:id",
      name: "design-detail",
      component: DesignDetailView,
    },
    {
      path: "/templates/:id",
      name: "template-detail",
      component: TemplateDetailView,
    },
  ],
});
