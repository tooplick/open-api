<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { CalendarIcon } from '@lucide/vue'
import { pageActivityLogs } from '@/api/activityLog'
import type { ActivityLogItem } from '@/types'
import type { DateRange } from 'reka-ui'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { RangeCalendar } from '@/components/ui/range-calendar'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
const list = ref<ActivityLogItem[]>([])
const loading = ref(true)
const total = ref(0)
const current = ref(1)
const size = ref(10)

const filters = reactive({
  action: 'all',
  resourceType: 'all',
  userId: '',
})

const dateRange = ref<DateRange>({ start: undefined, end: undefined })

type CalDate = { year: number, month: number, day: number }

function dvToIso(d: CalDate | undefined, endOfDay: boolean): string | undefined {
  if (!d) return undefined
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.year}-${p(d.month)}-${p(d.day)}T${endOfDay ? '23:59:59' : '00:00:00'}`
}

function fmtDate(d: CalDate | undefined): string {
  if (!d) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.year}-${p(d.month)}-${p(d.day)}`
}

const rangeLabel = computed(() => {
  const start = dateRange.value?.start
  const end = dateRange.value?.end
  if (start && end) return `${fmtDate(start)} ~ ${fmtDate(end)}`
  if (start) return `${fmtDate(start)} ~ …`
  return '选择日期范围'
})

const actionOptions = [
  { value: 'all', label: '全部' },
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGIN_FAILED', label: '登录失败' },
  { value: 'REGISTER', label: '注册' },
  { value: 'REGISTER_EMAIL', label: '邮箱注册' },
  { value: 'REGISTER_GITHUB', label: 'GitHub 注册' },
  { value: 'INITIAL_CREDENTIALS', label: '首次改密' },
  { value: 'PASSWORD_CHANGE', label: '修改密码' },
  { value: 'APIKEY_CREATE', label: '创建 API Key' },
  { value: 'APIKEY_STATUS_CHANGE', label: 'API Key 状态变更' },
  { value: 'APIKEY_DELETE', label: '删除 API Key' },
  { value: 'CHANNEL_CREATE', label: '创建渠道' },
  { value: 'CHANNEL_UPDATE', label: '更新渠道' },
  { value: 'CHANNEL_STATUS_CHANGE', label: '渠道状态变更' },
  { value: 'CHANNEL_DELETE', label: '删除渠道' },
  { value: 'USER_STATUS_CHANGE', label: '用户状态变更' },
  { value: 'SETTING_UPDATE', label: '更新设置' },
]

const resourceTypeOptions = [
  { value: 'all', label: '全部' },
  { value: 'USER', label: '用户' },
  { value: 'API_KEY', label: 'API Key' },
  { value: 'CHANNEL', label: '渠道' },
  { value: 'SETTING', label: '设置' },
]

function actionLabel(action: string): string {
  const found = actionOptions.find(o => o.value === action)
  return found ? found.label : action
}

function resourceTypeLabel(type?: string | null): string {
  if (!type) return '—'
  const found = resourceTypeOptions.find(o => o.value === type)
  return found ? found.label : type
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const p = await pageActivityLogs({
      current: current.value,
      size: size.value,
      userId: auth.isAdmin && filters.userId ? Number(filters.userId) : undefined,
      action: filters.action !== 'all' ? filters.action : undefined,
      resourceType: filters.resourceType !== 'all' ? filters.resourceType : undefined,
      startTime: dvToIso(dateRange.value?.start, false),
      endTime: dvToIso(dateRange.value?.end, true),
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
  void loadPage()
}

function reset(): void {
  filters.action = 'all'
  filters.resourceType = 'all'
  filters.userId = ''
  dateRange.value = { start: undefined, end: undefined }
  doSearch()
}

function goPage(p: number): void {
  current.value = p
  void loadPage()
}

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <div>
    <Card class="mb-6">
      <CardContent>
        <form
          class="grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] items-end gap-5"
          @submit.prevent="doSearch"
        >
          <div class="space-y-1.5">
            <Label>操作类型</Label>
            <Select v-model="filters.action">
              <SelectTrigger class="w-full">
                <SelectValue placeholder="全部" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="opt in actionOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5">
            <Label>资源类型</Label>
            <Select v-model="filters.resourceType">
              <SelectTrigger class="w-full">
                <SelectValue placeholder="全部" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="opt in resourceTypeOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div v-if="auth.isAdmin" class="space-y-1.5">
            <Label>用户 ID</Label>
            <Input v-model="filters.userId" type="number" placeholder="全部" />
          </div>
          <div class="space-y-1.5">
            <Label>时间范围</Label>
            <Popover>
              <PopoverTrigger as-child>
                <Button
                  variant="outline"
                  type="button"
                  class="w-full justify-start gap-2 font-normal"
                  :class="!dateRange.start && 'text-muted-foreground'"
                >
                  <CalendarIcon class="size-4" />
                  {{ rangeLabel }}
                </Button>
              </PopoverTrigger>
              <PopoverContent class="w-auto p-0" align="start">
                <RangeCalendar
                  :model-value="dateRange as any"
                  :number-of-months="2"
                  @update:model-value="(v: any) => (dateRange = v)"
                />
              </PopoverContent>
            </Popover>
          </div>
          <div class="flex gap-2.5">
            <Button type="submit">查询</Button>
            <Button variant="outline" type="button" @click="reset">重置</Button>
          </div>
        </form>
      </CardContent>
    </Card>

    <Card class="gap-0 overflow-hidden p-0">
      <div v-if="loading" class="space-y-3 p-4">
        <Skeleton v-for="i in 8" :key="i" class="h-11 w-full" />
      </div>
      <div v-else-if="list.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        暂无日志
      </div>
      <template v-else>
        <div class="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>时间</TableHead>
                <TableHead v-if="auth.isAdmin">用户</TableHead>
                <TableHead>操作类型</TableHead>
                <TableHead>资源类型</TableHead>
                <TableHead>资源名称</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>IP</TableHead>
                <TableHead>详情</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <template v-for="item in list" :key="item.id">
                <TableRow>
                  <TableCell class="whitespace-nowrap">{{ formatDateTime(item.createTime) }}</TableCell>
                  <TableCell v-if="auth.isAdmin">{{ item.username || item.userId }}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{{ actionLabel(item.action) }}</Badge>
                  </TableCell>
                  <TableCell>{{ resourceTypeLabel(item.resourceType) }}</TableCell>
                  <TableCell class="max-w-[200px] truncate">{{ item.resourceName || '—' }}</TableCell>
                  <TableCell>
                    <Badge :variant="item.status === 1 ? 'success' : 'danger'">
                      {{ item.status === 1 ? '成功' : '失败' }}
                    </Badge>
                  </TableCell>
                  <TableCell class="font-mono">{{ item.ip || '—' }}</TableCell>
                  <TableCell class="text-muted-foreground max-w-[300px] truncate">
                    {{ item.detail || '—' }}
                  </TableCell>
                </TableRow>
              </template>
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
