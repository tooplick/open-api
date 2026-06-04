<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { getSettings, updateSettings } from '@/api/setting'
import type { Settings } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
import { XIcon } from '@lucide/vue'

const loading = ref(true)
const saving = ref(false)

// 分组管理
const showAddGroup = ref(false)
const newGroupName = ref('')
const deleteGroupTarget = ref<string | null>(null)
const showDeleteGroup = ref(false)

const groupList = computed(() =>
  (form.keyGroups || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
)

function openAddGroup(): void {
  newGroupName.value = ''
  showAddGroup.value = true
}

async function confirmAddGroup(): Promise<void> {
  const name = newGroupName.value.trim()
  if (!name) {
    toast.error('请输入分组名称')
    return
  }
  if (groupList.value.includes(name)) {
    toast.error('该分组已存在')
    return
  }
  const groups = [...groupList.value, name]
  form.keyGroups = groups.join(',')
  showAddGroup.value = false
  await doSave()
  toast.success(`已添加分组「${name}」`)
}

function askDeleteGroup(name: string): void {
  if (groupList.value.length <= 1) {
    toast.error('至少保留一个分组')
    return
  }
  deleteGroupTarget.value = name
  showDeleteGroup.value = true
}

async function confirmDeleteGroup(): Promise<void> {
  const name = deleteGroupTarget.value
  if (!name) return
  const groups = groupList.value.filter((g) => g !== name)
  form.keyGroups = groups.join(',')
  if (form.defaultKeyGroup === name) {
    form.defaultKeyGroup = groups[0] || 'default'
  }
  showDeleteGroup.value = false
  await doSave()
  toast.success(`已删除分组「${name}」`)
}

const form = reactive<Settings>({
  siteName: '',
  siteSubtitle: '',
  siteFooter: '',
  loginAnnouncement: '',
  defaultKeyGroup: 'default',
  keyGroups: 'default',
  registerEnabled: true,
  passwordRegisterEnabled: true,
  emailRegisterEnabled: false,
  githubRegisterEnabled: false,
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpFrom: '',
  smtpSslEnabled: false,
})

async function load(): Promise<void> {
  loading.value = true
  try {
    Object.assign(form, await getSettings())
  }
  catch {
    // 拦截器已提示
  }
  finally {
    loading.value = false
  }
}

async function doSave(): Promise<void> {
  saving.value = true
  try {
    await updateSettings({ ...form })
  }
  catch {
    // 拦截器已提示
  }
  finally {
    saving.value = false
  }
}

async function save(): Promise<void> {
  await doSave()
  toast.success('设置已保存')
}

onMounted(() => void load())
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold tracking-tight">
          系统设置
        </h2>
        <p class="text-muted-foreground mt-1 text-sm">
          站点信息、注册方式与默认分组,保存后即时生效
        </p>
      </div>
      <Button :disabled="saving || loading" @click="save">
        <Spinner v-if="saving" />
        保存设置
      </Button>
    </div>

    <template v-if="loading">
      <Skeleton class="h-56 w-full" />
      <Skeleton class="h-32 w-full" />
    </template>

    <template v-else>
      <Card>
        <CardHeader>
          <CardTitle>站点信息</CardTitle>
          <CardDescription>显示在登录页、侧边栏与浏览器标题</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-1.5">
            <Label>站点名称</Label>
            <Input v-model="form.siteName" placeholder="AI Open Platform" />
          </div>
          <div class="space-y-1.5">
            <Label>副标题</Label>
            <Input v-model="form.siteSubtitle" placeholder="大模型聚合开放平台" />
          </div>
          <div class="space-y-1.5">
            <Label>页脚标语</Label>
            <Input v-model="form.siteFooter" placeholder="AI 模型聚合开放平台" />
          </div>
          <div class="space-y-1.5">
            <Label>登录页公告</Label>
            <Textarea v-model="form.loginAnnouncement" placeholder="可选,展示在登录页(如维护通知)" />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>分组管理</CardTitle>
          <CardDescription>管理可用的 API Key / 渠道分组,新建 Key 和渠道时从这些分组中选择</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-1.5">
            <Label>新建 Key 默认分组</Label>
            <Select v-model="form.defaultKeyGroup">
              <SelectTrigger class="w-full"><SelectValue placeholder="选择默认分组" /></SelectTrigger>
              <SelectContent>
                <SelectItem v-for="g in groupList" :key="g" :value="g">{{ g }}</SelectItem>
              </SelectContent>
            </Select>
            <p class="text-muted-foreground text-xs">
              创建 API Key 和渠道表单的默认分组取值
            </p>
          </div>

          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <Label>可用分组列表</Label>
              <Button variant="outline" size="sm" @click="openAddGroup">+ 新增分组</Button>
            </div>
            <div class="flex flex-wrap gap-2">
              <div
                v-for="g in groupList"
                :key="g"
                class="border-border bg-muted flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-sm"
              >
                <span>{{ g }}</span>
                <Badge v-if="g === form.defaultKeyGroup" variant="success" class="text-[10px] px-1 py-0">默认</Badge>
                <button
                  type="button"
                  class="text-muted-foreground hover:text-foreground ml-1 cursor-pointer"
                  @click="askDeleteGroup(g)"
                >
                  <XIcon class="size-3.5" />
                </button>
              </div>
              <span v-if="groupList.length === 0" class="text-muted-foreground text-sm">暂无分组</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>注册设置</CardTitle>
          <CardDescription>控制是否开放注册及可用的注册方式</CardDescription>
        </CardHeader>
        <CardContent class="divide-border divide-y">
          <div class="flex items-center justify-between py-3.5">
            <div>
              <div class="text-sm font-medium">
                开放注册
              </div>
              <div class="text-muted-foreground text-xs">
                总开关;关闭后隐藏注册入口并拒绝注册请求
              </div>
            </div>
            <Switch v-model="form.registerEnabled" />
          </div>
          <div class="flex items-center justify-between py-3.5">
            <div>
              <div class="text-sm font-medium">
                账号密码注册
              </div>
              <div class="text-muted-foreground text-xs">
                用户名 + 密码注册
              </div>
            </div>
            <Switch v-model="form.passwordRegisterEnabled" :disabled="!form.registerEnabled" />
          </div>
          <div class="flex items-center justify-between py-3.5">
            <div>
              <div class="text-sm font-medium">
                邮箱验证码注册
              </div>
              <div class="text-muted-foreground text-xs">
                需先在下方配置 SMTP 邮件服务
              </div>
            </div>
            <Switch v-model="form.emailRegisterEnabled" :disabled="!form.registerEnabled" />
          </div>
          <div class="flex items-center justify-between py-3.5">
            <div>
              <div class="text-sm font-medium">
                GitHub 注册
              </div>
              <div class="text-muted-foreground text-xs">
                需配置 OAuth 应用,具体实现见后续版本
              </div>
            </div>
            <Switch v-model="form.githubRegisterEnabled" :disabled="!form.registerEnabled" />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>SMTP 邮件服务</CardTitle>
          <CardDescription>用于邮箱验证码注册;密码仅保存、不回显</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-1.5">
            <Label>SMTP 服务器</Label>
            <Input v-model="form.smtpHost" placeholder="如 smtp.qq.com" />
          </div>
          <div class="space-y-1.5">
            <Label>端口</Label>
            <Input v-model.number="form.smtpPort" type="number" placeholder="587" />
          </div>
          <div class="space-y-1.5">
            <Label>账号</Label>
            <Input v-model="form.smtpUsername" placeholder="发信邮箱账号" autocomplete="off" />
          </div>
          <div class="space-y-1.5">
            <Label>密码 / 授权码</Label>
            <Input
              v-model="form.smtpPassword"
              type="password"
              placeholder="留空表示不修改"
              autocomplete="new-password"
            />
          </div>
          <div class="space-y-1.5">
            <Label>发件人</Label>
            <Input v-model="form.smtpFrom" placeholder="留空则用账号地址" />
          </div>
          <div class="flex items-center justify-between pt-1">
            <div>
              <div class="text-sm font-medium">
                使用 SSL
              </div>
              <div class="text-muted-foreground text-xs">
                开启 = 465 隐式 SSL;关闭 = 587 STARTTLS
              </div>
            </div>
            <Switch v-model="form.smtpSslEnabled" />
          </div>
        </CardContent>
      </Card>
    </template>

    <Dialog v-model:open="showAddGroup">
      <DialogContent class="sm:max-w-[380px]">
        <DialogHeader>
          <DialogTitle>新增分组</DialogTitle>
        </DialogHeader>
        <div class="space-y-2 py-2">
          <Label>分组名称</Label>
          <Input v-model="newGroupName" placeholder="如: production" @keyup.enter="confirmAddGroup" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showAddGroup = false">取消</Button>
          <Button @click="confirmAddGroup">确定</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <AlertDialog v-model:open="showDeleteGroup">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>删除分组</AlertDialogTitle>
          <AlertDialogDescription>
            确定删除分组「{{ deleteGroupTarget }}」?使用该分组的已有 API Key 和渠道不受影响。
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            class="bg-destructive text-white hover:bg-destructive/90"
            @click="confirmDeleteGroup"
          >
            删除
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>
