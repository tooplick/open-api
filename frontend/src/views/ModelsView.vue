<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { listModels } from '@/api/model'
import { getPublicSettings } from '@/api/setting'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Card } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const ALL_GROUP = '__all__'
const route = useRoute()
const router = useRouter()
const models = ref<string[]>([])
const groups = ref<string[]>([])
const loading = ref(true)
const search = ref('')
const selectedGroup = ref(readRouteGroup())
const initialized = ref(false)

const activeGroup = computed(() => selectedGroup.value === ALL_GROUP ? '' : selectedGroup.value)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return models.value
  return models.value.filter((m) => m.toLowerCase().includes(q))
})

const groupOptions = computed(() => {
  const options = [{ value: ALL_GROUP, label: '全部分组' }]
  const knownGroups = new Set<string>()
  for (const group of groups.value) {
    knownGroups.add(group)
    options.push({ value: group, label: group })
  }
  if (activeGroup.value && !knownGroups.has(activeGroup.value)) {
    options.push({ value: activeGroup.value, label: activeGroup.value })
  }
  return options
})

const totalText = computed(() => {
  if (selectedGroup.value === ALL_GROUP) {
    return `共 ${models.value.length} 个可用模型(由各渠道聚合得到,不可手动增删)`
  }
  return `${selectedGroup.value} 分组下共 ${models.value.length} 个可用模型`
})

const emptyText = computed(() => {
  if (selectedGroup.value === ALL_GROUP) {
    return '暂无可用模型,请先在「渠道管理」中接入渠道并配置其支持的模型'
  }
  return '当前分组暂无可用模型,请检查渠道分组与模型配置'
})

function readRouteGroup(): string {
  const raw = route.query.group
  return typeof raw === 'string' && raw.trim() ? raw.trim() : ALL_GROUP
}

function parseGroups(raw: string | null | undefined): string[] {
  return (raw || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

async function loadSettings(): Promise<void> {
  try {
    const pub = await getPublicSettings()
    groups.value = parseGroups(pub.keyGroups)
  } catch {
    groups.value = []
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    models.value = await listModels(activeGroup.value || undefined)
  } catch {
    models.value = []
  } finally {
    loading.value = false
  }
}

async function syncRouteGroup(): Promise<void> {
  const query: Record<string, string | string[] | undefined> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (key === 'group') continue
    if (typeof value === 'string') {
      query[key] = value
    } else if (Array.isArray(value)) {
      query[key] = value.filter((v): v is string => typeof v === 'string')
    }
  }
  if (selectedGroup.value !== ALL_GROUP) {
    query.group = selectedGroup.value
  }
  if (readRouteGroup() !== selectedGroup.value) {
    await router.replace({ query })
  }
}

async function copyModel(model: string): Promise<void> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(model)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = model
      textarea.setAttribute('readonly', 'true')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    toast.success('模型标识已复制')
  } catch {
    toast.error('复制失败')
  }
}

watch(selectedGroup, async () => {
  if (!initialized.value) return
  await syncRouteGroup()
  await load()
})

watch(() => route.query.group, () => {
  if (!initialized.value) return
  const next = readRouteGroup()
  if (selectedGroup.value !== next) {
    selectedGroup.value = next
  }
})

onMounted(async () => {
  await loadSettings()
  initialized.value = true
  await syncRouteGroup()
  await load()
})
</script>

<template>
  <div>
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div class="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
        <Input v-model="search" class="w-full sm:w-64" placeholder="搜索模型" />
        <Select v-model="selectedGroup">
          <SelectTrigger class="w-full sm:w-48">
            <SelectValue placeholder="选择分组" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem v-for="option in groupOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>
      <span class="text-sm text-muted-foreground">
        {{ totalText }}
      </span>
    </div>

    <Card class="gap-0 overflow-hidden p-0" data-tour="model-list">
      <div v-if="loading" class="space-y-3 p-4">
        <Skeleton v-for="i in 8" :key="i" class="h-10 w-full" />
      </div>
      <div v-else-if="models.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        {{ emptyText }}
      </div>
      <div v-else-if="filtered.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        没有匹配的模型
      </div>
      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead>模型标识</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="m in filtered" :key="m">
            <TableCell>
              <button
                type="button"
                class="hover:text-primary focus-visible:ring-ring rounded-sm font-mono focus-visible:ring-2 focus-visible:outline-none"
                title="复制模型标识"
                @click="copyModel(m)"
              >
                {{ m }}
              </button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </Card>
  </div>
</template>
