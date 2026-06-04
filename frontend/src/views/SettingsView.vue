<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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

const loading = ref(true)
const saving = ref(false)

const form = reactive<Settings>({
  siteName: '',
  siteSubtitle: '',
  siteFooter: '',
  loginAnnouncement: '',
  defaultKeyGroup: 'default',
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

async function save(): Promise<void> {
  saving.value = true
  try {
    await updateSettings({ ...form })
    toast.success('设置已保存')
  }
  catch {
    // 拦截器已提示
  }
  finally {
    saving.value = false
  }
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
          <CardTitle>API Key</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="space-y-1.5">
            <Label>新建 Key 默认分组</Label>
            <Input v-model="form.defaultKeyGroup" placeholder="default" />
            <p class="text-muted-foreground text-xs">
              创建 API Key 表单的默认 group 取值
            </p>
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
  </div>
</template>
