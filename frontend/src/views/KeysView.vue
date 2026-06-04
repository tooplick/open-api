<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { createKey, deleteKey, listKeys, updateKeyStatus } from '@/api/apikey'
import { useKeyGroups } from '@/composables/useKeyGroups'
import type { ApiKey } from '@/types'
import { formatDateTime, localInputToIso } from '@/utils/format'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Spinner } from '@/components/ui/spinner'
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
  DialogDescription,
  DialogFooter,
  DialogHeader,
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

const keys = ref<ApiKey[]>([])
const loading = ref(true)
const showCreate = ref(false)
const newlyCreated = ref<ApiKey | null>(null)

const deleteTarget = ref<ApiKey | null>(null)
const showDelete = ref(false)
const { defaultGroup, groupList, loadKeyGroups } = useKeyGroups()

const formSchema = toTypedSchema(
  z.object({
    name: z.string().min(1, '请输入名称'),
    group: z.string().optional(),
    models: z.string().optional(),
    expireTime: z.string().optional(),
  }),
)

const { handleSubmit, isSubmitting, resetForm } = useForm({
  validationSchema: formSchema,
  initialValues: { name: '', group: 'default', models: '', expireTime: '' },
})

async function load(): Promise<void> {
  loading.value = true
  try {
    keys.value = await listKeys()
  } catch {
    keys.value = []
  } finally {
    loading.value = false
  }
}

async function openCreate(): Promise<void> {
  await loadKeyGroups()
  resetForm({ values: { name: '', group: defaultGroup.value, models: '', expireTime: '' } })
  newlyCreated.value = null
  showCreate.value = true
}

const submitCreate = handleSubmit(async (values) => {
  try {
    const k = await createKey({
      name: values.name.trim(),
      group: values.group?.trim() || 'default',
      models: values.models?.trim() || undefined,
      expireTime: values.expireTime ? localInputToIso(values.expireTime) : null,
    })
    toast.success('创建成功')
    newlyCreated.value = k
    await load()
  } catch {
    // 拦截器已提示
  }
})

async function toggle(k: ApiKey): Promise<void> {
  const next = k.status === 1 ? 0 : 1
  try {
    await updateKeyStatus(k.id, next)
    k.status = next
    toast.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    // 拦截器已提示
  }
}

function askDelete(k: ApiKey): void {
  deleteTarget.value = k
  showDelete.value = true
}

async function confirmDelete(): Promise<void> {
  const k = deleteTarget.value
  if (!k) return
  try {
    await deleteKey(k.id)
    toast.success('已删除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

function mask(k: string): string {
  return k.length > 14 ? `${k.slice(0, 8)}••••••${k.slice(-4)}` : k
}

async function copy(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败,请手动复制')
  }
}

onMounted(() => {
  void load()
  void loadKeyGroups()
})
</script>

<template>
  <div>
    <div class="mb-6 flex items-center justify-between gap-3">
      <span class="text-sm text-muted-foreground">用于调用 OpenAI 兼容接口(/v1/**)的密钥</span>
      <Button @click="openCreate">+ 创建 API Key</Button>
    </div>

    <Card class="gap-0 overflow-hidden p-0">
      <div v-if="loading" class="space-y-3 p-4">
        <Skeleton v-for="i in 5" :key="i" class="h-12 w-full" />
      </div>
      <div v-else-if="keys.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        还没有 API Key,点击右上角创建一个吧
      </div>
      <div v-else class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>分组</TableHead>
              <TableHead>模型限制</TableHead>
              <TableHead>过期时间</TableHead>
              <TableHead>创建时间</TableHead>
              <TableHead class="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="k in keys" :key="k.id">
              <TableCell>{{ k.name }}</TableCell>
              <TableCell>
                <div class="flex items-center gap-1.5">
                  <span class="font-mono">{{ mask(k.apiKey) }}</span>
                  <Button variant="ghost" size="sm" @click="copy(k.apiKey)">复制</Button>
                </div>
              </TableCell>
              <TableCell>
                <Badge :variant="k.status === 1 ? 'success' : 'muted'">
                  {{ k.status === 1 ? '启用' : '禁用' }}
                </Badge>
              </TableCell>
              <TableCell>{{ k.group }}</TableCell>
              <TableCell class="max-w-[240px] whitespace-normal text-muted-foreground">
                {{ k.models || '不限' }}
              </TableCell>
              <TableCell>{{ k.expireTime ? formatDateTime(k.expireTime) : '永不过期' }}</TableCell>
              <TableCell>{{ formatDateTime(k.createTime) }}</TableCell>
              <TableCell class="text-right">
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button variant="ghost" size="icon-sm">
                      <MoreHorizontalIcon />
                      <span class="sr-only">操作</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click="toggle(k)">
                      {{ k.status === 1 ? '禁用' : '启用' }}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem variant="destructive" @click="askDelete(k)">
                      删除
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>

    <Dialog v-model:open="showCreate">
      <DialogContent class="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>创建 API Key</DialogTitle>
          <DialogDescription v-if="!newlyCreated">
            创建后请立即复制保存,Key 仅在此完整展示一次。
          </DialogDescription>
        </DialogHeader>

        <form v-if="!newlyCreated" class="space-y-4" @submit="submitCreate">
          <FormField v-slot="{ componentField }" name="name">
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl>
                <Input v-bind="componentField" placeholder="如:生产环境 / 我的应用" />
              </FormControl>
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
              <FormDescription>仅能路由到声明了同一分组的渠道</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField }" name="models">
            <FormItem>
              <FormLabel>模型限制</FormLabel>
              <FormControl>
                <Textarea v-bind="componentField" placeholder="gpt-4o,gpt-4o-mini(可选)" />
              </FormControl>
              <FormDescription>逗号分隔的模型白名单;留空表示不限制</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField }" name="expireTime">
            <FormItem>
              <FormLabel>过期时间</FormLabel>
              <FormControl>
                <Input v-bind="componentField" type="datetime-local" />
              </FormControl>
              <FormDescription>留空表示永不过期</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>
          <DialogFooter>
            <Button variant="outline" type="button" @click="showCreate = false">取消</Button>
            <Button type="submit" :disabled="isSubmitting">
              <Spinner v-if="isSubmitting" />
              创建
            </Button>
          </DialogFooter>
        </form>

        <div v-else>
          <p class="mb-2.5 text-sm text-muted-foreground">创建成功!请立即复制保存你的 Key:</p>
          <div
            class="flex items-center justify-between gap-2.5 rounded-2xl border border-input bg-primary-soft px-3.5 py-3 break-all"
          >
            <span class="font-mono">{{ newlyCreated.apiKey }}</span>
            <Button variant="outline" size="sm" @click="copy(newlyCreated.apiKey)">复制</Button>
          </div>
          <p class="mt-2.5 text-xs text-muted-foreground">
            把它配置到 OpenAI SDK 的 api_key,base_url 指向本平台的 /v1 即可调用。
          </p>
          <DialogFooter class="mt-4">
            <Button @click="showCreate = false">完成</Button>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>

    <AlertDialog v-model:open="showDelete">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>删除 API Key</AlertDialogTitle>
          <AlertDialogDescription>
            确定删除 API Key「{{ deleteTarget?.name }}」?删除后使用该 Key 的调用将立即失败。
          </AlertDialogDescription>
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
