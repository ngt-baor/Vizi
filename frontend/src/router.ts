import { createRouter, createWebHistory } from "vue-router";
import HomeView from "./views/HomeView.vue";
import TemplateDetailView from "./views/TemplateDetailView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/templates/:id",
      name: "template-detail",
      component: TemplateDetailView,
    },
  ],
});
