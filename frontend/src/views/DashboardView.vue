<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { logStatistics } from '@/api/log'
import type { LogStat } from '@/types'
import { formatNumber } from '@/utils/format'

const auth = useAuthStore()
const stat = ref<LogStat | null>(null)
const loading = ref(false)

const ranges = [
  { key: 'all', label: '全部' },
  { key: '7d', label: '近 7 天' },
  { key: '30d', label: '近 30 天' },
] as const
const range = ref<'all' | '7d' | '30d'>('all')

function startTimeFor(key: 'all' | '7d' | '30d'): string | undefined {
  if (key === 'all') return undefined
  const d = new Date()
  d.setDate(d.getDate() - (key === '7d' ? 7 : 30))
  d.setHours(0, 0, 0, 0)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T00:00:00`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    stat.value = await logStatistics({ startTime: startTimeFor(range.value) })
  } catch {
    stat.value = null
  } finally {
    loading.value = false
  }
}

function pick(key: 'all' | '7d' | '30d'): void {
  range.value = key
  void load()
}

onMounted(() => {
  void load()
  void auth.refreshMe().catch(() => undefined)
})
</script>

<template>
  <div class="dash">
    <div class="row spread" style="margin-bottom: 32px; flex-wrap: wrap; gap: 12px">
      <div>
        <div class="page-title">你好,{{ auth.user?.username }}</div>
        <div class="muted" style="margin-top: 6px">
          {{ auth.isAdmin ? '平台用量总览(全部用户)' : '我的调用用量总览' }}
        </div>
      </div>
      <div class="seg">
        <button
          v-for="r in ranges"
          :key="r.key"
          :class="['seg-btn', { active: range === r.key }]"
          type="button"
          @click="pick(r.key)"
        >
          {{ r.label }}
        </button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="card card-pad stat">
        <div class="stat-label">请求数</div>
        <div class="stat-value">{{ formatNumber(stat?.requests) }}</div>
      </div>
      <div class="card card-pad stat">
        <div class="stat-label">总 Tokens</div>
        <div class="stat-value">{{ formatNumber(stat?.totalTokens) }}</div>
      </div>
      <div class="card card-pad stat">
        <div class="stat-label">输入 / 输出 Tokens</div>
        <div class="stat-value sm">
          {{ formatNumber(stat?.promptTokens) }}
          <span class="faint">/</span>
          {{ formatNumber(stat?.completionTokens) }}
        </div>
      </div>
    </div>

    <div class="dash-cols">
      <div class="card card-pad">
        <h3 class="block-title">我的账户</h3>
        <div class="kv">
          <span class="muted">角色</span>
          <span :class="['badge', auth.isAdmin ? 'badge-indigo' : 'badge-gray']">
            {{ auth.isAdmin ? '管理员' : '普通用户' }}
          </span>
        </div>
        <div class="kv">
          <span class="muted">邮箱</span>
          <span>{{ auth.user?.email || '—' }}</span>
        </div>
      </div>

      <div class="card card-pad">
        <h3 class="block-title">快捷入口</h3>
        <div class="quick">
          <RouterLink class="quick-item" :to="{ name: 'keys' }">创建 / 管理 API Key →</RouterLink>
          <RouterLink class="quick-item" :to="{ name: 'models' }">查看可用模型 →</RouterLink>
          <RouterLink class="quick-item" :to="{ name: 'logs' }">查看调用日志 →</RouterLink>
          <RouterLink v-if="auth.isAdmin" class="quick-item" :to="{ name: 'channels' }">
            配置上游渠道 →
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.seg {
  display: inline-flex;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.seg-btn {
  padding: 7px 14px;
  border: none;
  background: var(--color-surface);
  color: var(--color-text-soft);
  cursor: pointer;
  font-size: 13px;
  border-left: 1px solid var(--color-border);
}
.seg-btn:first-child {
  border-left: none;
}
.seg-btn.active {
  background: var(--color-primary);
  color: #fff;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}
.stat-label {
  font-size: 13px;
  color: var(--color-text-soft);
  margin-bottom: 8px;
}
.stat-value {
  font-size: 26px;
  font-weight: 600;
}
.stat-value.sm {
  font-size: 20px;
}

.dash-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.block-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 18px;
}
.kv {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}
.kv:last-child {
  border-bottom: none;
}
.quick {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.quick-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-primary);
}
.quick-item:last-child {
  border-bottom: none;
}

@media (max-width: 980px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dash-cols {
    grid-template-columns: 1fr;
  }
}
</style>
