<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ActivityIcon,
  ArrowDownToLineIcon,
  ArrowUpFromLineIcon,
  BoxesIcon,
  KeyRoundIcon,
  LayersIcon,
  NetworkIcon,
  ScrollTextIcon,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { dailyLogStatistics, logStatistics } from '@/api/log'
import type { DailyStat, LogStat } from '@/types'
import { formatNumber } from '@/utils/format'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { ChartConfig } from '@/components/ui/chart'
import {
  ChartContainer,
  ChartCrosshair,
  ChartTooltipContent,
  componentToString,
} from '@/components/ui/chart'
import { VisArea, VisAxis, VisLine, VisXYContainer } from '@unovis/vue'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

const auth = useAuthStore()
const stat = ref<LogStat | null>(null)
const loading = ref(true)

const trendDaily = ref<DailyStat[]>([])
const trendLoading = ref(true)
const metric = ref<'requests' | 'tokens'>('requests')

type RangeKey = 'all' | '7d' | '30d'
const range = ref<RangeKey>('7d')

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function dateKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function startTimeFor(key: RangeKey): string | undefined {
  if (key === 'all') return undefined
  const d = new Date()
  d.setDate(d.getDate() - (key === '7d' ? 6 : 29))
  d.setHours(0, 0, 0, 0)
  return `${dateKey(d)}T00:00:00`
}

function lastNDates(n: number): string[] {
  const out: string[] = []
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  for (let i = n - 1; i >= 0; i--) {
    const x = new Date(d)
    x.setDate(d.getDate() - i)
    out.push(dateKey(x))
  }
  return out
}

const chartData = computed(() => {
  const map = new Map<string, number>()
  for (const d of trendDaily.value) {
    if (!d.date) continue
    map.set(d.date, (metric.value === 'requests' ? d.requests : d.totalTokens) || 0)
  }
  let keys: string[]
  if (range.value === 'all') {
    keys = [...map.keys()].sort().slice(-30)
    if (keys.length === 0) keys = [dateKey(new Date())]
  }
  else {
    keys = lastNDates(range.value === '7d' ? 7 : 30)
  }
  return keys.map(k => ({ label: k.slice(5), value: map.get(k) ?? 0 }))
})

interface ChartPoint {
  x: number
  label: string
  value: number
}

const series = computed<ChartPoint[]>(() =>
  chartData.value.map((d, i) => ({ x: i, label: d.label, value: d.value })),
)

const yMax = computed(() => {
  const max = Math.max(0, ...series.value.map(p => p.value))
  return max <= 0 ? 10 : Math.ceil(max * 1.15)
})

const xTicks = computed<number[]>(() => {
  const n = series.value.length
  if (n <= 1) return [0]
  const count = Math.min(6, n)
  const step = (n - 1) / (count - 1)
  const out = new Set<number>()
  for (let i = 0; i < count; i++) out.add(Math.round(i * step))
  return [...out].sort((a, b) => a - b)
})

const chartConfig = computed<ChartConfig>(() => ({
  value: {
    label: metric.value === 'requests' ? '请求数' : 'Tokens',
    color: 'var(--primary)',
  },
}))

const areaDefs = `
  <linearGradient id="aiopen-area-fill" x1="0" y1="0" x2="0" y2="1">
    <stop offset="5%" stop-color="var(--primary)" stop-opacity="0.45" />
    <stop offset="95%" stop-color="var(--primary)" stop-opacity="0.04" />
  </linearGradient>
`

const cards = computed(() => [
  { key: 'requests', label: '请求数', value: stat.value?.requests, icon: ActivityIcon },
  { key: 'total', label: '总 Tokens', value: stat.value?.totalTokens, icon: LayersIcon },
  { key: 'prompt', label: '输入 Tokens', value: stat.value?.promptTokens, icon: ArrowUpFromLineIcon },
  { key: 'completion', label: '输出 Tokens', value: stat.value?.completionTokens, icon: ArrowDownToLineIcon },
])

const quickLinks = computed(() => [
  { name: 'keys', label: '创建 / 管理 API Key', icon: KeyRoundIcon },
  { name: 'models', label: '查看可用模型', icon: BoxesIcon },
  { name: 'logs', label: '查看调用日志', icon: ScrollTextIcon },
  ...(auth.isAdmin ? [{ name: 'channels', label: '配置上游渠道', icon: NetworkIcon }] : []),
])

async function loadStat(): Promise<void> {
  loading.value = true
  try {
    stat.value = await logStatistics({ startTime: startTimeFor(range.value) })
  }
  catch {
    stat.value = null
  }
  finally {
    loading.value = false
  }
}

async function loadTrend(): Promise<void> {
  trendLoading.value = true
  try {
    trendDaily.value = await dailyLogStatistics({ startTime: startTimeFor(range.value) })
  }
  catch {
    trendDaily.value = []
  }
  finally {
    trendLoading.value = false
  }
}

function reload(): void {
  void loadStat()
  void loadTrend()
}

function setRange(v: unknown): void {
  range.value = v as RangeKey
  reload()
}

function setMetric(v: unknown): void {
  metric.value = v as 'requests' | 'tokens'
}

onMounted(() => {
  reload()
  void auth.refreshMe().catch(() => undefined)
})
</script>

<template>
  <div class="space-y-8">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 class="text-2xl font-semibold tracking-tight">
          你好,{{ auth.user?.username }}
        </h2>
        <p class="text-muted-foreground mt-1.5">
          {{ auth.isAdmin ? '平台用量总览(全部用户)' : '我的调用用量总览' }}
        </p>
      </div>
      <Tabs :model-value="range" @update:model-value="setRange">
        <TabsList>
          <TabsTrigger value="all">
            全部
          </TabsTrigger>
          <TabsTrigger value="7d">
            近 7 天
          </TabsTrigger>
          <TabsTrigger value="30d">
            近 30 天
          </TabsTrigger>
        </TabsList>
      </Tabs>
    </div>

    <div class="grid grid-cols-4 gap-6 max-xl:grid-cols-2 max-sm:grid-cols-1" data-tour="dashboard-stats">
      <Card v-for="c in cards" :key="c.key">
        <CardContent>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground text-sm">{{ c.label }}</span>
            <span class="bg-muted text-muted-foreground flex size-9 items-center justify-center rounded-lg">
              <component :is="c.icon" class="size-4.5" />
            </span>
          </div>
          <Skeleton v-if="loading" class="mt-3 h-8 w-24" />
          <div v-else class="mt-2 text-3xl font-semibold tracking-tight">
            {{ formatNumber(c.value) }}
          </div>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader class="flex-row items-center justify-between gap-3 space-y-0">
        <div>
          <CardTitle>调用趋势</CardTitle>
          <CardDescription>
            {{ range === 'all' ? '按出现的日期聚合' : `近 ${range === '7d' ? 7 : 30} 天每日` }}{{ metric === 'requests' ? '请求数' : ' Token 用量' }}
          </CardDescription>
        </div>
        <Tabs :model-value="metric" @update:model-value="setMetric">
          <TabsList>
            <TabsTrigger value="requests">
              请求数
            </TabsTrigger>
            <TabsTrigger value="tokens">
              Tokens
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </CardHeader>
      <CardContent>
        <Skeleton v-if="trendLoading" class="h-[240px] w-full" />
        <ChartContainer
          v-else
          :config="chartConfig"
          class="aspect-auto h-[240px] w-full"
          :cursor="true"
        >
          <VisXYContainer :data="series" :svg-defs="areaDefs" :y-domain="[0, yMax]">
            <VisArea
              :x="(d: ChartPoint) => d.x"
              :y="(d: ChartPoint) => d.value"
              color="url(#aiopen-area-fill)"
            />
            <VisLine
              :x="(d: ChartPoint) => d.x"
              :y="(d: ChartPoint) => d.value"
              color="var(--primary)"
              :line-width="2"
            />
            <VisAxis
              type="x"
              :x="(d: ChartPoint) => d.x"
              :tick-values="xTicks"
              :tick-line="false"
              :domain-line="false"
              :grid-line="false"
              :tick-format="(v: number) => series[Math.round(v)]?.label ?? ''"
            />
            <VisAxis
              type="y"
              :num-ticks="3"
              :tick-line="false"
              :domain-line="false"
              :tick-format="(v: number) => formatNumber(v)"
            />
            <ChartCrosshair
              color="var(--primary)"
              :template="componentToString(chartConfig, ChartTooltipContent, {
                labelFormatter: (d: number | Date) => series[Math.round(Number(d))]?.label ?? '',
              })"
            />
          </VisXYContainer>
        </ChartContainer>
      </CardContent>
    </Card>

    <div class="grid grid-cols-2 gap-6 max-lg:grid-cols-1">
      <Card>
        <CardHeader>
          <CardTitle>我的账户</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="divide-border divide-y">
            <div class="flex items-center justify-between py-3.5">
              <span class="text-muted-foreground">角色</span>
              <Badge :variant="auth.isAdmin ? 'info' : 'muted'">
                {{ auth.isAdmin ? '管理员' : '普通用户' }}
              </Badge>
            </div>
            <div class="flex items-center justify-between py-3.5">
              <span class="text-muted-foreground">邮箱</span>
              <span>{{ auth.user?.email || '—' }}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>快捷入口</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="grid gap-2">
            <RouterLink
              v-for="link in quickLinks"
              :key="link.name"
              :to="{ name: link.name }"
              class="hover:bg-accent flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors"
            >
              <span class="bg-muted text-muted-foreground flex size-8 items-center justify-center rounded-md">
                <component :is="link.icon" class="size-4" />
              </span>
              <span class="flex-1">{{ link.label }}</span>
              <span class="text-muted-foreground">→</span>
            </RouterLink>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
