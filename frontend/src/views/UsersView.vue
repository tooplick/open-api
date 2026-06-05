<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { pageUsers, updateUserStatus } from '@/api/user'
import type { User } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'

const auth = useAuthStore()
const list = ref<User[]>([])
const loading = ref(true)
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
    <div class="mb-6 flex flex-wrap items-center gap-3">
      <form class="flex items-center gap-2" @submit.prevent="doSearch">
        <Input v-model="search" class="w-[220px]" placeholder="搜索用户名" />
        <Button variant="outline" type="submit">搜索</Button>
      </form>
    </div>

    <Card class="gap-0 overflow-hidden p-0" data-tour="user-table">
      <div v-if="loading" class="space-y-3 p-4">
        <Skeleton v-for="i in 6" :key="i" class="h-12 w-full" />
      </div>
      <div v-else-if="list.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        暂无用户
      </div>
      <template v-else>
        <div class="overflow-x-auto">
          <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>用户名</TableHead>
              <TableHead>邮箱</TableHead>
              <TableHead>角色</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>注册时间</TableHead>
              <TableHead class="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="u in list" :key="u.id">
              <TableCell>{{ u.id }}</TableCell>
              <TableCell>{{ u.username }}</TableCell>
              <TableCell>{{ u.email || '—' }}</TableCell>
              <TableCell>
                <Badge :variant="u.role === 'admin' ? 'info' : 'muted'">
                  {{ u.role === 'admin' ? '管理员' : '普通用户' }}
                </Badge>
              </TableCell>
              <TableCell>
                <Badge :variant="u.status === 1 ? 'success' : 'danger'">
                  {{ u.status === 1 ? '启用' : '禁用' }}
                </Badge>
              </TableCell>
              <TableCell>{{ formatDateTime(u.createTime) }}</TableCell>
              <TableCell class="text-right">
                <Button
                  variant="outline"
                  size="sm"
                  :disabled="u.id === auth.user?.id"
                  @click="toggle(u)"
                >
                  {{ u.status === 1 ? '禁用' : '启用' }}
                </Button>
              </TableCell>
            </TableRow>
          </TableBody>
          </Table>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5">
          <span class="text-sm text-muted-foreground">共 {{ total }} 条</span>
          <Pagination
            v-slot="{ page }"
            :page="current"
            :items-per-page="size"
            :total="total"
            :sibling-count="1"
            show-edges
            class="mx-0 w-auto"
            @update:page="goPage"
          >
            <PaginationContent v-slot="{ items }">
              <PaginationPrevious>上一页</PaginationPrevious>
              <template v-for="(item, idx) in items" :key="idx">
                <PaginationItem
                  v-if="item.type === 'page'"
                  :value="item.value"
                  :is-active="item.value === page"
                >
                  {{ item.value }}
                </PaginationItem>
                <PaginationEllipsis v-else :index="idx" />
              </template>
              <PaginationNext>下一页</PaginationNext>
            </PaginationContent>
          </Pagination>
        </div>
      </template>
    </Card>
  </div>
</template>
