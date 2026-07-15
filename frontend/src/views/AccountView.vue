<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import {
  changeAccountEmail,
  changeAccountPassword,
  getCurrentUser,
  loginAccount,
  logoutAccount,
  registerAccount,
  updateProfile,
  type AuthUser,
} from "../api";

const route = useRoute();
const router = useRouter();
const mode = ref<"login" | "register">("login");
const email = ref("");
const password = ref("");
const fullName = ref("");
const phone = ref("");
const address = ref("");
const currentPassword = ref("");
const newPassword = ref("");
const newEmail = ref("");
const currentUser = ref<AuthUser | null>(null);
const loading = ref(true);
const submitting = ref(false);
const message = ref("");
const error = ref("");

function localRedirectPath(): string {
  const redirect = route.query.redirect;
  return typeof redirect === "string" && redirect.startsWith("/") && !redirect.startsWith("//")
    ? redirect
    : "";
}

function syncProfileFields(user: AuthUser): void {
  fullName.value = user.fullName;
  phone.value = user.phone ?? "";
  address.value = user.address ?? "";
  newEmail.value = user.email;
}

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser();
    if (currentUser.value) {
      syncProfileFields(currentUser.value);
    }
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
  message.value = "";

  try {
    if (mode.value === "register") {
      await registerAccount(email.value, password.value, fullName.value);
    }
    await loginAccount(email.value, password.value);
    currentUser.value = await getCurrentUser();
    if (currentUser.value) {
      syncProfileFields(currentUser.value);
    }
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

async function saveProfile(): Promise<void> {
  if (!currentUser.value) {
    return;
  }
  submitting.value = true;
  error.value = "";
  message.value = "";
  try {
    currentUser.value = await updateProfile(fullName.value, phone.value, address.value);
    syncProfileFields(currentUser.value);
    message.value = "Profile updated.";
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot update profile";
  } finally {
    submitting.value = false;
  }
}

async function saveEmail(): Promise<void> {
  submitting.value = true;
  error.value = "";
  message.value = "";
  try {
    await changeAccountEmail(newEmail.value, currentPassword.value);
    currentUser.value = null;
    currentPassword.value = "";
    message.value = "Email changed. Please sign in again with the new email.";
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot change email";
  } finally {
    submitting.value = false;
  }
}

async function savePassword(): Promise<void> {
  submitting.value = true;
  error.value = "";
  message.value = "";
  try {
    await changeAccountPassword(currentPassword.value, newPassword.value);
    currentPassword.value = "";
    newPassword.value = "";
    message.value = "Password updated.";
  } catch (unknownError) {
    error.value = unknownError instanceof Error ? unknownError.message : "Cannot change password";
  } finally {
    submitting.value = false;
  }
}

async function logout(): Promise<void> {
  submitting.value = true;
  error.value = "";
  message.value = "";

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
    <p class="summary">Sign in, manage profile, and secure your workspace.</p>

    <p v-if="loading" class="muted">Checking account...</p>

    <div v-else-if="currentUser" class="account-stack">
      <div class="account-panel account-panel--span account-panel--session">
        <div class="account-session-main">
          <p class="eyebrow">Signed in - {{ currentUser.role }}</p>
          <h2>{{ currentUser.fullName }}</h2>
          <p class="muted">{{ currentUser.email }}</p>
        </div>
        <div class="account-session-actions">
          <RouterLink
            v-if="currentUser.role === 'ADMIN'"
            class="secondary-action"
            to="/admin"
          >
            Open admin console
          </RouterLink>
          <button class="secondary-action" type="button" :disabled="submitting" @click="logout">
            {{ submitting ? "Signing out..." : "Sign out" }}
          </button>
        </div>
      </div>

      <form class="account-panel" @submit.prevent="saveProfile">
        <h3>Profile</h3>
        <label>
          Full name
          <input v-model="fullName" maxlength="160" required />
        </label>
        <label>
          Phone
          <input v-model="phone" maxlength="40" placeholder="Optional" />
        </label>
        <label>
          Address
          <textarea v-model="address" maxlength="500" rows="3" placeholder="Optional shipping address" />
        </label>
        <button class="primary-action" type="submit" :disabled="submitting">Save profile</button>
      </form>

      <form class="account-panel" @submit.prevent="saveEmail">
        <h3>Change email</h3>
        <label>
          New email
          <input v-model="newEmail" type="email" maxlength="320" required />
        </label>
        <label>
          Current password
          <input v-model="currentPassword" type="password" minlength="8" maxlength="72" required />
        </label>
        <button class="secondary-action" type="submit" :disabled="submitting">Update email</button>
      </form>

      <form class="account-panel account-panel--span account-panel--password" @submit.prevent="savePassword">
        <h3>Change password</h3>
        <label>
          Current password
          <input v-model="currentPassword" type="password" minlength="8" maxlength="72" required />
        </label>
        <label>
          New password
          <input v-model="newPassword" type="password" minlength="8" maxlength="72" required />
        </label>
        <button class="secondary-action" type="submit" :disabled="submitting">Update password</button>
      </form>

      <p v-if="message" class="save-status" role="status">{{ message }}</p>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
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
