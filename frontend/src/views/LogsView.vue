<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { logStatistics, pageLogs } from '@/api/log'
import type { LogItem, LogStat } from '@/types'
import { useAuthStore } from '@/stores/auth'
import BasePagination from '@/components/BasePagination.vue'
import { formatDateTime, formatNumber, localInputToIso } from '@/utils/format'

const auth = useAuthStore()
const list = ref<LogItem[]>([])
const stat = ref<LogStat | null>(null)
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const size = ref(10)

const filters = reactive({
  modelName: '',
  type: '' as '' | '1' | '2',
  userId: '',
  startTime: '',
  endTime: '',
})

function buildCommonParams() {
  return {
    userId: auth.isAdmin && filters.userId ? Number(filters.userId) : undefined,
    startTime: localInputToIso(filters.startTime),
    endTime: localInputToIso(filters.endTime),
  }
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const p = await pageLogs({
      current: current.value,
      size: size.value,
      modelName: filters.modelName.trim() || undefined,
      type: filters.type ? Number(filters.type) : undefined,
      ...buildCommonParams(),
    })
    list.value = p.records
    total.value = p.total
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadStat(): Promise<void> {
  try {
    stat.value = await logStatistics(buildCommonParams())
  } catch {
    stat.value = null
  }
}

function doSearch(): void {
  current.value = 1
  void loadPage()
  void loadStat()
}

function reset(): void {
  filters.modelName = ''
  filters.type = ''
  filters.userId = ''
  filters.startTime = ''
  filters.endTime = ''
  doSearch()
}

function goPage(p: number): void {
  current.value = p
  void loadPage()
}

onMounted(() => {
  void loadPage()
  void loadStat()
})
</script>

<template>
  <div>
    <div class="stat-row">
      <div class="card card-pad stat">
        <div class="stat-label">请求数</div>
        <div class="stat-value">{{ formatNumber(stat?.requests) }}</div>
      </div>
      <div class="card card-pad stat">
        <div class="stat-label">总 Tokens</div>
        <div class="stat-value">{{ formatNumber(stat?.totalTokens) }}</div>
      </div>
      <div class="card card-pad stat">
        <div class="stat-label">消耗额度</div>
        <div class="stat-value">{{ formatNumber(stat?.quota) }}</div>
      </div>
    </div>

    <div class="card card-pad filters">
      <form class="filter-grid" @submit.prevent="doSearch">
        <div class="field">
          <label class="field-label">模型</label>
          <input v-model="filters.modelName" class="input" placeholder="模型标识" />
        </div>
        <div class="field">
          <label class="field-label">状态</label>
          <select v-model="filters.type" class="select">
            <option value="">全部</option>
            <option value="1">成功</option>
            <option value="2">失败</option>
          </select>
        </div>
        <div v-if="auth.isAdmin" class="field">
          <label class="field-label">用户 ID</label>
          <input v-model="filters.userId" class="input" type="number" placeholder="全部" />
        </div>
        <div class="field">
          <label class="field-label">开始时间</label>
          <input v-model="filters.startTime" class="input" type="datetime-local" />
        </div>
        <div class="field">
          <label class="field-label">结束时间</label>
          <input v-model="filters.endTime" class="input" type="datetime-local" />
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" type="submit">查询</button>
          <button class="btn" type="button" @click="reset">重置</button>
        </div>
      </form>
    </div>

    <div class="card">
      <div v-if="loading" class="state-box"><span class="spinner" /> 加载中…</div>
      <div v-else-if="list.length === 0" class="state-box">暂无日志</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>时间</th>
              <th v-if="auth.isAdmin">用户</th>
              <th>模型</th>
              <th>渠道</th>
              <th>状态</th>
              <th>输入 / 输出 / 总</th>
              <th>额度</th>
              <th>耗时</th>
              <th>内容 / 错误</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in list" :key="l.id">
              <td class="nowrap">{{ formatDateTime(l.createTime) }}</td>
              <td v-if="auth.isAdmin">{{ l.username || l.userId }}</td>
              <td class="mono">{{ l.modelName }}</td>
              <td>{{ l.channelName || '—' }}</td>
              <td>
                <span :class="['badge', l.type === 1 ? 'badge-green' : 'badge-red']">
                  {{ l.type === 1 ? '成功' : '失败' }}
                </span>
              </td>
              <td class="mono nowrap">
                {{ formatNumber(l.promptTokens) }} / {{ formatNumber(l.completionTokens) }} /
                {{ formatNumber(l.totalTokens) }}
              </td>
              <td class="mono">{{ formatNumber(l.quota) }}</td>
              <td class="nowrap">{{ formatNumber(l.durationMs) }} ms</td>
              <td class="wrap faint">{{ l.content || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card-pad" style="padding-top: 0">
        <BasePagination :current="current" :size="size" :total="total" @update:current="goPage" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-row {
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
  font-size: 24px;
  font-weight: 600;
}
.filters {
  margin-bottom: 24px;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 20px;
  align-items: end;
}
.filter-grid .field {
  margin-bottom: 0;
}
.filter-actions {
  display: flex;
  gap: 10px;
}
@media (max-width: 720px) {
  .stat-row {
    grid-template-columns: 1fr;
  }
}
</style>
