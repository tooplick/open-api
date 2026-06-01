<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageUsers, updateUserQuota, updateUserStatus } from '@/api/user'
import type { User } from '@/types'
import { useAuthStore } from '@/stores/auth'
import BaseModal from '@/components/BaseModal.vue'
import BasePagination from '@/components/BasePagination.vue'
import { toast } from '@/composables/useToast'
import { formatDateTime, formatQuota } from '@/utils/format'

const auth = useAuthStore()
const list = ref<User[]>([])
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const size = ref(10)
const search = ref('')

const showQuota = ref(false)
const saving = ref(false)
const quotaTarget = ref<User | null>(null)
const quotaValue = ref(0)

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await pageUsers({
      current: current.value,
      size: size.value,
      username: search.value.trim() || undefined,
    })
    list.value = p.records
    total.value = p.total
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function doSearch(): void {
  current.value = 1
  void load()
}

function goPage(p: number): void {
  current.value = p
  void load()
}

async function toggle(u: User): Promise<void> {
  if (u.id === auth.user?.id) {
    toast.error('不能禁用当前登录的账户')
    return
  }
  const next = u.status === 1 ? 0 : 1
  try {
    await updateUserStatus(u.id, next)
    u.status = next
    toast.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    // 拦截器已提示
  }
}

function openQuota(u: User): void {
  quotaTarget.value = u
  quotaValue.value = u.quota ?? 0
  showQuota.value = true
}

async function saveQuota(): Promise<void> {
  if (!quotaTarget.value) return
  saving.value = true
  try {
    const q = Math.max(0, Number(quotaValue.value) || 0)
    await updateUserQuota(quotaTarget.value.id, q)
    quotaTarget.value.quota = q
    toast.success('额度已更新')
    showQuota.value = false
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="row spread" style="margin-bottom: 24px; flex-wrap: wrap; gap: 12px">
      <form class="row gap-8" @submit.prevent="doSearch">
        <input v-model="search" class="input" style="width: 220px" placeholder="搜索用户名" />
        <button class="btn" type="submit">搜索</button>
      </form>
    </div>

    <div class="card">
      <div v-if="loading" class="state-box"><span class="spinner" /> 加载中…</div>
      <div v-else-if="list.length === 0" class="state-box">暂无用户</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户名</th>
              <th>邮箱</th>
              <th>角色</th>
              <th>状态</th>
              <th>额度(已用 / 总)</th>
              <th>注册时间</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in list" :key="u.id">
              <td>{{ u.id }}</td>
              <td>{{ u.username }}</td>
              <td>{{ u.email || '—' }}</td>
              <td>
                <span :class="['badge', u.role === 'admin' ? 'badge-indigo' : 'badge-gray']">
                  {{ u.role === 'admin' ? '管理员' : '普通用户' }}
                </span>
              </td>
              <td>
                <span :class="['badge', u.status === 1 ? 'badge-green' : 'badge-red']">
                  {{ u.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td class="mono">{{ formatQuota(u.usedQuota, u.quota) }}</td>
              <td>{{ formatDateTime(u.createTime) }}</td>
              <td class="text-right nowrap">
                <button class="btn btn-sm" type="button" @click="openQuota(u)">设额度</button>
                <button
                  class="btn btn-sm"
                  type="button"
                  :disabled="u.id === auth.user?.id"
                  @click="toggle(u)"
                >
                  {{ u.status === 1 ? '禁用' : '启用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card-pad" style="padding-top: 0">
        <BasePagination :current="current" :size="size" :total="total" @update:current="goPage" />
      </div>
    </div>

    <BaseModal :open="showQuota" title="设置用户额度" width="420px" @close="showQuota = false">
      <p class="muted" style="margin-bottom: 14px">
        用户:<strong>{{ quotaTarget?.username }}</strong>
      </p>
      <div class="field">
        <label class="field-label">总额度(点数)</label>
        <input v-model.number="quotaValue" class="input" type="number" min="0" />
        <span class="field-hint">0 表示不限额;已用 {{ formatQuota(quotaTarget?.usedQuota, 0).split(' / ')[0] }} 点</span>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="showQuota = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="saveQuota">
          <span v-if="saving" class="spinner" />
          <span v-else>保存</span>
        </button>
      </template>
    </BaseModal>
  </div>
</template>
