<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import {
  createChannel,
  deleteChannel,
  fetchChannelModels,
  pageChannels,
  updateChannel,
  updateChannelStatus,
} from '@/api/channel'
import { getPublicSettings } from '@/api/setting'
import type { Channel, ChannelRequest } from '@/types'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Spinner } from '@/components/ui/spinner'
import { Checkbox } from '@/components/ui/checkbox'
import { Skeleton } from '@/components/ui/skeleton'
import { MoreHorizontalIcon } from '@lucide/vue'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'

const list = ref<Channel[]>([])
const loading = ref(true)
const total = ref(0)
const current = ref(1)
const size = ref(10)
const search = ref('')

const showForm = ref(false)
const editingId = ref<number | null>(null)
const channelType = ref('openai')
const fetchedModels = ref<string[]>([])
const fetchingModels = ref(false)

const deleteTarget = ref<Channel | null>(null)
const showDelete = ref(false)
const defaultGroup = ref('default')
const keyGroupsStr = ref('default')

const groupList = computed(() =>
  keyGroupsStr.value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
)

const formSchema = computed(() =>
  toTypedSchema(
    z.object({
      name: z.string().min(1, '请输入渠道名称'),
      baseUrl: z.string().min(1, '请输入上游地址'),
      apiKey: editingId.value ? z.string().optional() : z.string().min(1, '请填写上游密钥'),
      models: z.string().min(1, '请填写支持的模型'),
      group: z.string().min(1, '请选择分组'),
      modelMapping: z.string().optional(),
      weight: z.coerce.number().min(1, '权重至少为 1'),
      priority: z.coerce.number(),
      status: z.string(),
    }),
  ),
)

const defaults = {
  name: '',
  baseUrl: '',
  apiKey: '',
  models: '',
  group: 'default',
  modelMapping: '',
  weight: 1,
  priority: 0,
  status: '1',
}

const { values, setFieldValue, handleSubmit, isSubmitting, resetForm } = useForm({
  validationSchema: formSchema,
  initialValues: { ...defaults },
})

function parseModels(csv: string): string[] {
  return csv
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

const selectedSet = computed(() => new Set(parseModels(values.models ?? '')))
const allFetchedSelected = computed(
  () => fetchedModels.value.length > 0 && fetchedModels.value.every((m) => selectedSet.value.has(m)),
)

function toggleModel(m: string): void {
  const set = new Set(parseModels(values.models ?? ''))
  if (set.has(m)) set.delete(m)
  else set.add(m)
  setFieldValue('models', Array.from(set).join(','))
}

function toggleSelectAll(): void {
  const set = new Set(parseModels(values.models ?? ''))
  if (allFetchedSelected.value) fetchedModels.value.forEach((m) => set.delete(m))
  else fetchedModels.value.forEach((m) => set.add(m))
  setFieldValue('models', Array.from(set).join(','))
}

async function doFetchModels(): Promise<void> {
  if (!values.baseUrl?.trim()) {
    toast.error('请先填写上游地址')
    return
  }
  if (!editingId.value && !values.apiKey?.trim()) {
    toast.error('请先填写上游密钥')
    return
  }
  fetchingModels.value = true
  try {
    const models = await fetchChannelModels({
      baseUrl: values.baseUrl.trim(),
      apiKey: values.apiKey?.trim() || undefined,
      id: editingId.value ?? undefined,
    })
    fetchedModels.value = models
    if (models.length === 0) toast.info('上游未返回模型列表')
    else toast.success(`获取到 ${models.length} 个模型,勾选需要的模型`)
  } catch {
    // 拦截器已提示
  } finally {
    fetchingModels.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await pageChannels({
      current: current.value,
      size: size.value,
      name: search.value.trim() || undefined,
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

async function loadSettings(): Promise<void> {
  try {
    const s = await getPublicSettings()
    defaultGroup.value = s.defaultKeyGroup || 'default'
    keyGroupsStr.value = s.keyGroups || 'default'
  } catch {
    // 拦截器已提示
  }
}

async function openCreate(): Promise<void> {
  editingId.value = null
  channelType.value = 'openai'
  fetchedModels.value = []
  await loadSettings()
  resetForm({ values: { ...defaults, group: defaultGroup.value } })
  showForm.value = true
}

function openEdit(c: Channel): void {
  editingId.value = c.id
  channelType.value = c.type
  fetchedModels.value = []
  resetForm({
    values: {
      name: c.name,
      baseUrl: c.baseUrl,
      apiKey: '',
      models: c.models,
      group: c.group,
      modelMapping: c.modelMapping ?? '',
      weight: c.weight,
      priority: c.priority,
      status: String(c.status),
    },
  })
  showForm.value = true
}

const submit = handleSubmit(async (v) => {
  const payload: ChannelRequest = {
    name: v.name.trim(),
    type: channelType.value,
    baseUrl: v.baseUrl.trim(),
    apiKey: (v.apiKey ?? '').trim(),
    models: v.models.trim(),
    group: v.group?.trim() || defaultGroup.value,
    modelMapping: v.modelMapping?.trim() || undefined,
    weight: Number(v.weight) || 1,
    priority: Number(v.priority) || 0,
    status: Number(v.status),
  }
  try {
    if (editingId.value) {
      await updateChannel(editingId.value, payload)
      toast.success('已更新')
    } else {
      await createChannel(payload)
      toast.success('已创建')
    }
    showForm.value = false
    await load()
  } catch {
    // 拦截器已提示
  }
})

async function toggle(c: Channel): Promise<void> {
  const next = c.status === 1 ? 0 : 1
  try {
    await updateChannelStatus(c.id, next)
    c.status = next
    toast.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    // 拦截器已提示
  }
}

function askDelete(c: Channel): void {
  deleteTarget.value = c
  showDelete.value = true
}

async function confirmDelete(): Promise<void> {
  const c = deleteTarget.value
  if (!c) return
  try {
    await deleteChannel(c.id)
    toast.success('已删除')
    if (list.value.length === 1 && current.value > 1) current.value -= 1
    await load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => {
  void load()
  void loadSettings()
})
</script>

<template>
  <div>
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <form class="flex items-center gap-2" @submit.prevent="doSearch">
        <Input v-model="search" class="w-[220px]" placeholder="搜索渠道名称" />
        <Button variant="outline" type="submit">搜索</Button>
      </form>
      <Button @click="openCreate">+ 新建渠道</Button>
    </div>

    <Card class="gap-0 overflow-hidden p-0">
      <div v-if="loading" class="space-y-3 p-4">
        <Skeleton v-for="i in 6" :key="i" class="h-12 w-full" />
      </div>
      <div v-else-if="list.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        暂无渠道,点击右上角接入上游服务商
      </div>
      <template v-else>
        <div class="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>上游地址</TableHead>
                <TableHead>支持模型</TableHead>
                <TableHead>分组</TableHead>
                <TableHead>权重</TableHead>
                <TableHead>优先级</TableHead>
                <TableHead>状态</TableHead>
                <TableHead class="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="c in list" :key="c.id">
                <TableCell>{{ c.name }}</TableCell>
                <TableCell><Badge variant="muted">{{ c.type }}</Badge></TableCell>
                <TableCell class="font-mono text-muted-foreground">{{ c.baseUrl }}</TableCell>
                <TableCell class="max-w-[320px]">
                  <div
                    v-if="parseModels(c.models).length"
                    class="flex flex-wrap items-center gap-1"
                    :title="c.models"
                  >
                    <Badge
                      v-for="m in parseModels(c.models).slice(0, 3)"
                      :key="m"
                      variant="muted"
                      class="font-mono text-xs font-normal"
                    >
                      {{ m }}
                    </Badge>
                    <span v-if="parseModels(c.models).length > 3" class="text-muted-foreground text-xs">
                      +{{ parseModels(c.models).length - 3 }} 个
                    </span>
                  </div>
                  <span v-else class="text-muted-foreground">—</span>
                </TableCell>
                <TableCell class="max-w-[160px] whitespace-normal">{{ c.group }}</TableCell>
                <TableCell>{{ c.weight }}</TableCell>
                <TableCell>{{ c.priority }}</TableCell>
                <TableCell>
                  <Badge :variant="c.status === 1 ? 'success' : 'muted'">
                    {{ c.status === 1 ? '启用' : '禁用' }}
                  </Badge>
                </TableCell>
                <TableCell class="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger as-child>
                      <Button variant="ghost" size="icon-sm">
                        <MoreHorizontalIcon />
                        <span class="sr-only">操作</span>
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem @click="toggle(c)">
                        {{ c.status === 1 ? '禁用' : '启用' }}
                      </DropdownMenuItem>
                      <DropdownMenuItem @click="openEdit(c)">
                        编辑
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem variant="destructive" @click="askDelete(c)">
                        删除
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
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

    <Dialog v-model:open="showForm">
      <DialogContent class="max-h-[85vh] overflow-y-auto sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{{ editingId ? '编辑渠道' : '新建渠道' }}</DialogTitle>
        </DialogHeader>

        <form class="space-y-4" @submit="submit">
          <FormField v-slot="{ componentField }" name="name">
            <FormItem>
              <FormLabel>渠道名称</FormLabel>
              <FormControl><Input v-bind="componentField" placeholder="如:OpenAI 官方" /></FormControl>
              <FormDescription>渠道按 OpenAI 兼容协议接入上游(/v1/chat/completions)</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="baseUrl">
            <FormItem>
              <FormLabel>上游地址</FormLabel>
              <FormControl>
                <Input v-bind="componentField" placeholder="https://api.openai.com" />
              </FormControl>
              <FormDescription>填根地址(不含 /v1),平台转发时自动拼接请求路径</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="apiKey">
            <FormItem>
              <FormLabel>上游密钥</FormLabel>
              <FormControl>
                <Textarea
                  v-bind="componentField"
                  :placeholder="editingId ? '留空则沿用库中原密钥' : 'sk-...'"
                />
              </FormControl>
              <FormDescription>
                {{
                  editingId
                    ? '出于安全后端不返回原密钥;留空则沿用库中原密钥,如需更换或重新获取模型请填写'
                    : '上游服务商的真实密钥;可换行填写多个 key(随机轮换)'
                }}
              </FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="models">
            <FormItem>
              <FormLabel>支持的模型</FormLabel>
              <div class="flex flex-wrap items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  type="button"
                  :disabled="fetchingModels"
                  @click="doFetchModels"
                >
                  <Spinner v-if="fetchingModels" />
                  获取模型
                </Button>
                <Button
                  v-if="fetchedModels.length"
                  variant="outline"
                  size="sm"
                  type="button"
                  @click="toggleSelectAll"
                >
                  {{ allFetchedSelected ? '取消全选' : '全选' }}
                </Button>
                <span v-if="fetchedModels.length" class="text-xs text-muted-foreground">
                  已选 {{ selectedSet.size }} / 获取 {{ fetchedModels.length }}
                </span>
              </div>
              <div
                v-if="fetchedModels.length"
                class="grid max-h-[220px] grid-cols-[repeat(auto-fill,minmax(190px,1fr))] gap-x-[18px] gap-y-2.5 overflow-y-auto rounded-lg border border-border bg-background p-4"
              >
                <label
                  v-for="m in fetchedModels"
                  :key="m"
                  class="text-secondary-foreground flex cursor-pointer items-center gap-2 overflow-hidden text-[13px]"
                >
                  <Checkbox
                    :model-value="selectedSet.has(m)"
                    @update:model-value="() => toggleModel(m)"
                  />
                  <span class="truncate font-mono">{{ m }}</span>
                </label>
              </div>
              <FormControl>
                <Textarea
                  v-bind="componentField"
                  placeholder="gpt-4o,gpt-4o-mini(可“获取模型”后多选/全选,或在此手动逗号分隔)"
                />
              </FormControl>
              <FormDescription>逗号分隔;同一模型多渠道时按优先级取最高,再按权重随机</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="group">
            <FormItem>
              <FormLabel>分组</FormLabel>
              <Select v-bind="componentField">
                <FormControl>
                  <SelectTrigger class="w-full"><SelectValue placeholder="选择分组" /></SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem v-for="g in groupList" :key="g" :value="g">{{ g }}</SelectItem>
                </SelectContent>
              </Select>
              <FormDescription>仅同分组的 API Key 能路由到该渠道</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="modelMapping">
            <FormItem>
              <FormLabel>模型重命名映射</FormLabel>
              <FormControl>
                <Textarea v-bind="componentField" placeholder='{"gpt-4":"gpt-4o"}(可选,JSON)' />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <div class="grid grid-cols-3 gap-4 max-[560px]:grid-cols-1">
            <FormField v-slot="{ componentField }" name="weight">
              <FormItem>
                <FormLabel>权重</FormLabel>
                <FormControl><Input v-bind="componentField" type="number" min="1" /></FormControl>
                <FormMessage />
              </FormItem>
            </FormField>
            <FormField v-slot="{ componentField }" name="priority">
              <FormItem>
                <FormLabel>优先级</FormLabel>
                <FormControl><Input v-bind="componentField" type="number" /></FormControl>
                <FormMessage />
              </FormItem>
            </FormField>
            <FormField v-slot="{ componentField }" name="status">
              <FormItem>
                <FormLabel>状态</FormLabel>
                <Select v-bind="componentField">
                  <FormControl>
                    <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="1">启用</SelectItem>
                    <SelectItem value="0">禁用</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            </FormField>
          </div>

          <DialogFooter>
            <Button variant="outline" type="button" @click="showForm = false">取消</Button>
            <Button type="submit" :disabled="isSubmitting">
              <Spinner v-if="isSubmitting" />
              保存
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <AlertDialog v-model:open="showDelete">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>删除渠道</AlertDialogTitle>
          <AlertDialogDescription>确定删除渠道「{{ deleteTarget?.name }}」?</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            class="bg-destructive text-white hover:bg-destructive/90"
            @click="confirmDelete"
          >
            删除
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>
