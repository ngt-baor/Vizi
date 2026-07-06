import { createRouter, createWebHistory } from "vue-router";
import HomeView from "./views/HomeView.vue";
import AccountView from "./views/AccountView.vue";
import CheckoutView from "./views/CheckoutView.vue";
import DesignDetailView from "./views/DesignDetailView.vue";
import DesignsView from "./views/DesignsView.vue";
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
      component: DesignDetailView,
    },
    {
      path: "/checkout/:designId",
      name: "checkout",
      component: CheckoutView,
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
