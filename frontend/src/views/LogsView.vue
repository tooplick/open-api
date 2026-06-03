<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CalendarIcon, ChevronRightIcon } from '@lucide/vue'
import { logStatistics, pageLogs } from '@/api/log'
import type { LogItem, LogStat } from '@/types'
import type { DateRange } from 'reka-ui'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatNumber } from '@/utils/format'
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
const list = ref<LogItem[]>([])
const stat = ref<LogStat | null>(null)
const loading = ref(true)
const total = ref(0)
const current = ref(1)
const size = ref(10)

const expanded = ref<Set<number>>(new Set())
const colspan = computed(() => (auth.isAdmin ? 9 : 8))

function toggle(id: number): void {
  if (expanded.value.has(id)) {
    expanded.value.delete(id)
  }
  else {
    expanded.value.add(id)
  }
}

const filters = reactive({
  modelName: '',
  type: 'all' as 'all' | '1' | '2',
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

function buildCommonParams() {
  return {
    userId: auth.isAdmin && filters.userId ? Number(filters.userId) : undefined,
    startTime: dvToIso(dateRange.value?.start, false),
    endTime: dvToIso(dateRange.value?.end, true),
  }
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const p = await pageLogs({
      current: current.value,
      size: size.value,
      modelName: filters.modelName.trim() || undefined,
      type: filters.type !== 'all' ? Number(filters.type) : undefined,
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
  filters.type = 'all'
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
  void loadStat()
})
</script>

<template>
  <div>
    <div class="mb-6 grid grid-cols-2 gap-6 max-md:grid-cols-1">
      <Card>
        <CardContent>
          <div class="text-[13px] text-muted-foreground">请求数</div>
          <div class="mt-2 text-2xl font-semibold">{{ formatNumber(stat?.requests) }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardContent>
          <div class="text-[13px] text-muted-foreground">总 Tokens</div>
          <div class="mt-2 text-2xl font-semibold">{{ formatNumber(stat?.totalTokens) }}</div>
        </CardContent>
      </Card>
    </div>

    <Card class="mb-6">
      <CardContent>
        <form
          class="grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] items-end gap-5"
          @submit.prevent="doSearch"
        >
          <div class="space-y-1.5">
            <Label>模型</Label>
            <Input v-model="filters.modelName" placeholder="模型标识" />
          </div>
          <div class="space-y-1.5">
            <Label>状态</Label>
            <Select v-model="filters.type">
              <SelectTrigger class="w-full">
                <SelectValue placeholder="全部" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部</SelectItem>
                <SelectItem value="1">成功</SelectItem>
                <SelectItem value="2">失败</SelectItem>
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
                <TableHead class="w-10" />
                <TableHead>时间</TableHead>
                <TableHead v-if="auth.isAdmin">用户</TableHead>
                <TableHead>模型</TableHead>
                <TableHead>渠道</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>输入 / 输出 / 总</TableHead>
                <TableHead>耗时</TableHead>
                <TableHead>内容 / 错误</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <template v-for="l in list" :key="l.id">
                <TableRow class="cursor-pointer" @click="toggle(l.id)">
                  <TableCell class="align-middle">
                    <ChevronRightIcon
                      class="text-muted-foreground size-4 transition-transform"
                      :class="expanded.has(l.id) && 'rotate-90'"
                    />
                  </TableCell>
                  <TableCell class="whitespace-nowrap">{{ formatDateTime(l.createTime) }}</TableCell>
                  <TableCell v-if="auth.isAdmin">{{ l.username || l.userId }}</TableCell>
                  <TableCell class="font-mono">{{ l.modelName }}</TableCell>
                  <TableCell>{{ l.channelName || '—' }}</TableCell>
                  <TableCell>
                    <Badge :variant="l.type === 1 ? 'success' : 'danger'">
                      {{ l.type === 1 ? '成功' : '失败' }}
                    </Badge>
                  </TableCell>
                  <TableCell class="whitespace-nowrap font-mono">
                    {{ formatNumber(l.promptTokens) }} / {{ formatNumber(l.completionTokens) }} /
                    {{ formatNumber(l.totalTokens) }}
                  </TableCell>
                  <TableCell class="whitespace-nowrap">{{ formatNumber(l.durationMs) }} ms</TableCell>
                  <TableCell class="text-muted-foreground max-w-[360px] truncate">
                    {{ l.content || '—' }}
                  </TableCell>
                </TableRow>
                <TableRow v-if="expanded.has(l.id)" class="bg-muted/40 hover:bg-muted/40">
                  <TableCell :colspan="colspan" class="p-0">
                    <dl class="grid grid-cols-2 gap-x-10 gap-y-3.5 px-6 py-5 text-sm max-lg:grid-cols-1">
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">上游模型</dt>
                        <dd class="break-all font-mono">{{ l.upstreamModel || '—' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">端点</dt>
                        <dd class="break-all font-mono">{{ l.endpoint || '—' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">HTTP 状态</dt>
                        <dd class="font-mono">{{ l.httpStatus ?? '—' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">流式</dt>
                        <dd>{{ l.stream === 1 ? '是' : '否' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">首字延迟</dt>
                        <dd class="font-mono">{{ l.ttfbMs != null ? `${formatNumber(l.ttfbMs)} ms` : '—' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">上游耗时</dt>
                        <dd class="font-mono">{{ l.upstreamMs != null ? `${formatNumber(l.upstreamMs)} ms` : '—' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">请求 ID</dt>
                        <dd class="break-all font-mono">{{ l.requestId || '—' }}</dd>
                      </div>
                      <div class="flex gap-3">
                        <dt class="text-muted-foreground w-24 shrink-0">客户端 IP</dt>
                        <dd class="font-mono">{{ l.ip || '—' }}</dd>
                      </div>
                      <div class="col-span-2 flex gap-3 max-lg:col-span-1">
                        <dt class="text-muted-foreground w-24 shrink-0">User-Agent</dt>
                        <dd class="break-all">{{ l.userAgent || '—' }}</dd>
                      </div>
                      <div class="col-span-2 flex gap-3 max-lg:col-span-1">
                        <dt class="text-muted-foreground w-24 shrink-0">内容 / 错误</dt>
                        <dd class="break-all whitespace-pre-wrap">{{ l.content || '—' }}</dd>
                      </div>
                    </dl>
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
