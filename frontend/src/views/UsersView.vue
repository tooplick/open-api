<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageUsers, updateUserStatus } from '@/api/user'
import type { User } from '@/types'
import { useAuthStore } from '@/stores/auth'
import BasePagination from '@/components/BasePagination.vue'
import { toast } from '@/composables/useToast'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const list = ref<User[]>([])
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const size = ref(10)
const search = ref('')

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
              <td>{{ formatDateTime(u.createTime) }}</td>
              <td class="text-right nowrap">
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
  </div>
</template>
