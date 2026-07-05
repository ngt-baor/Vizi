<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  getCurrentUser,
  loginAccount,
  logoutAccount,
  registerAccount,
  type AuthUser,
} from "../api";

const route = useRoute();
const router = useRouter();
const mode = ref<"login" | "register">("login");
const email = ref("");
const password = ref("");
const fullName = ref("");
const currentUser = ref<AuthUser | null>(null);
const loading = ref(true);
const submitting = ref(false);
const error = ref("");

function localRedirectPath(): string {
  const redirect = route.query.redirect;
  return typeof redirect === "string" && redirect.startsWith("/") && !redirect.startsWith("//")
    ? redirect
    : "";
}

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser();
    const redirectPath = localRedirectPath();
    if (currentUser.value && redirectPath) {
      await router.push(redirectPath);
    }
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot load account";
  } finally {
    loading.value = false;
  }
});

async function submit(): Promise<void> {
  submitting.value = true;
  error.value = "";

  try {
    if (mode.value === "register") {
      await registerAccount(email.value, password.value, fullName.value);
    }
    await loginAccount(email.value, password.value);
    currentUser.value = await getCurrentUser();
    password.value = "";
    const redirectPath = localRedirectPath();
    if (redirectPath) {
      await router.push(redirectPath);
    }
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Authentication failed";
  } finally {
    submitting.value = false;
  }
}

async function logout(): Promise<void> {
  submitting.value = true;
  error.value = "";

  try {
    await logoutAccount();
    currentUser.value = null;
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Logout failed";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="account-view">
    <p class="eyebrow">Private workspace</p>
    <h1>Account</h1>
    <p class="summary">Sign in before saving designs to your private workspace.</p>

    <p v-if="loading" class="muted">Checking account...</p>

    <div v-else-if="currentUser" class="account-panel">
      <p class="eyebrow">Signed in</p>
      <h2>{{ currentUser.fullName }}</h2>
      <p class="muted">{{ currentUser.email }}</p>
      <button class="secondary-action" type="button" :disabled="submitting" @click="logout">
        {{ submitting ? "Signing out..." : "Sign out" }}
      </button>
    </div>

    <form v-else class="account-panel" @submit.prevent="submit">
      <div class="mode-control" aria-label="Account mode">
        <button
          type="button"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >
          Sign in
        </button>
        <button
          type="button"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          Create account
        </button>
      </div>

      <label v-if="mode === 'register'">
        Full name
        <input
          v-model="fullName"
          name="fullName"
          autocomplete="name"
          maxlength="160"
          required
        />
      </label>
      <label>
        Email
        <input
          v-model="email"
          name="email"
          type="email"
          autocomplete="email"
          maxlength="320"
          required
        />
      </label>
      <label>
        Password
        <input
          v-model="password"
          name="password"
          type="password"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          minlength="8"
          maxlength="72"
          required
        />
      </label>

      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <button class="primary-action" type="submit" :disabled="submitting">
        {{ submitting ? "Please wait..." : mode === "login" ? "Sign in" : "Create account" }}
      </button>
    </form>
  </section>
</template>
