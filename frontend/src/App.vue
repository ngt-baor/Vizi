<template>
  <main class="app-shell">
    <nav class="topbar" aria-label="Primary navigation">
      <RouterLink to="/">Vizi</RouterLink>
      <div class="topbar-links">
        <span>Business card design studio</span>
        <RouterLink to="/account">Account</RouterLink>
      </div>
    </nav>
    <RouterView />
  </main>
</template>
