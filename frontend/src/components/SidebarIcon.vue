<script setup lang="ts">
defineProps<{ name: string }>()
</script>

<template>
  <svg
    class="nav-ico"
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'dashboard'">
      <rect x="3" y="3" width="8" height="8" rx="1" />
      <rect x="13" y="3" width="8" height="5" rx="1" />
      <rect x="13" y="12" width="8" height="9" rx="1" />
      <rect x="3" y="13" width="8" height="8" rx="1" />
    </template>
    <template v-else-if="name === 'keys'">
      <circle cx="7.5" cy="15.5" r="3.5" />
      <path d="M10 13l7-7 3 3-2 2-2-2" />
    </template>
    <template v-else-if="name === 'models'">
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <rect x="9" y="9" width="6" height="6" rx="1" />
      <path d="M9 2v2M15 2v2M9 20v2M15 20v2M2 9h2M2 15h2M20 9h2M20 15h2" />
    </template>
    <template v-else-if="name === 'channels'">
      <circle cx="6" cy="12" r="2.2" />
      <circle cx="18" cy="6" r="2.2" />
      <circle cx="18" cy="18" r="2.2" />
      <path d="M8 11l8-4M8 13l8 4" />
    </template>
    <template v-else-if="name === 'users'">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
    </template>
    <template v-else-if="name === 'logs'">
      <path d="M4 6h16M4 12h16M4 18h10" />
    </template>
    <template v-else-if="name === 'profile'">
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <circle cx="8" cy="11" r="2" />
      <path d="M13 10h5M13 14h5M5 16c0.5-1.6 5-1.6 5.5 0" />
    </template>
    <template v-else-if="name === 'logout'">
      <path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4" />
      <path d="M10 17l-5-5 5-5M4 12h11" />
    </template>
  </svg>
</template>

<style scoped>
.nav-ico {
  flex-shrink: 0;
}
</style>
