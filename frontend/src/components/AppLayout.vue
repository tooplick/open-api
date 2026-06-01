<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SidebarIcon from './SidebarIcon.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

interface NavItem {
  name: string
  label: string
  admin?: boolean
}

const nav: NavItem[] = [
  { name: 'dashboard', label: '概览' },
  { name: 'keys', label: 'API Key' },
  { name: 'models', label: '模型' },
  { name: 'channels', label: '渠道管理', admin: true },
  { name: 'users', label: '用户管理', admin: true },
  { name: 'logs', label: '调用日志' },
  { name: 'profile', label: '个人资料' },
]

const visibleNav = computed(() => nav.filter((n) => !n.admin || auth.isAdmin))
const pageTitle = computed(() => route.meta.title ?? '')

function logout(): void {
  auth.clear()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">AI</span>
        <span class="brand-text">Open Platform</span>
      </div>
      <nav class="nav">
        <RouterLink
          v-for="item in visibleNav"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-item"
          :class="{ active: route.name === item.name }"
        >
          <SidebarIcon :name="item.name" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-foot">AI 模型聚合开放平台</div>
    </aside>

    <div class="main">
      <header class="header">
        <h1 class="header-title">{{ pageTitle }}</h1>
        <div class="header-user">
          <div class="user-meta">
            <span class="user-name">{{ auth.user?.username }}</span>
            <span :class="['badge', auth.isAdmin ? 'badge-indigo' : 'badge-gray']">
              {{ auth.isAdmin ? '管理员' : '普通用户' }}
            </span>
          </div>
          <button class="btn btn-sm btn-ghost" type="button" @click="logout">
            <SidebarIcon name="logout" />
            退出
          </button>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--sidebar-bg);
  color: var(--sidebar-text);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  border-right: 1px solid var(--color-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--header-height);
  padding: 0 20px;
  font-weight: 600;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.brand-text {
  font-size: 15px;
}

.nav {
  flex: 1;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  color: var(--sidebar-text);
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--color-text);
}

.nav-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-text-active);
  font-weight: 500;
}

.sidebar-foot {
  padding: 14px 20px;
  font-size: 12px;
  color: var(--color-text-faint);
  border-top: 1px solid var(--color-border);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.header {
  height: var(--header-height);
  flex-shrink: 0;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-weight: 500;
}

.content {
  flex: 1;
  padding: 32px;
  overflow-x: hidden;
}

@media (max-width: 720px) {
  .sidebar {
    width: 64px;
  }
  .brand-text,
  .nav-item span,
  .sidebar-foot {
    display: none;
  }
  .nav-item {
    justify-content: center;
  }
  .content {
    padding: 20px;
  }
}
</style>
