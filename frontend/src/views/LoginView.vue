<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { useAuthStore } from '@/stores/auth'
import { emailRegister, register as registerApi, sendEmailCode } from '@/api/auth'
import { getPublicSettings } from '@/api/setting'
import type { PublicSettings } from '@/types'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const tab = ref('login')
const registerMethod = ref<'password' | 'email'>('password')
const loginInitial = ref({ username: '', password: '' })

const pub = reactive<PublicSettings>({
  siteName: 'AI Open Platform',
  siteSubtitle: '大模型聚合开放平台 · 控制台',
  loginAnnouncement: '',
  defaultKeyGroup: 'default',
  keyGroups: 'default',
  registerEnabled: true,
  passwordRegisterEnabled: true,
  emailRegisterEnabled: false,
  githubRegisterEnabled: false,
})

const showRegister = computed(() => pub.registerEnabled && (pub.passwordRegisterEnabled || pub.emailRegisterEnabled))
const bothRegister = computed(() => pub.passwordRegisterEnabled && pub.emailRegisterEnabled)

const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try {
    Object.assign(pub, await getPublicSettings())
    if (pub.siteName) document.title = pub.siteName
    // 默认选中首个可用的注册方式
    if (!pub.passwordRegisterEnabled && pub.emailRegisterEnabled) {
      registerMethod.value = 'email'
    }
  }
  catch {
    // 设置接口不可用时使用默认值,登录仍可用
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const loginSchema = toTypedSchema(
  z.object({
    username: z.string().min(1, '请输入用户名'),
    password: z.string().min(1, '请输入密码'),
  }),
)
const registerSchema = toTypedSchema(
  z.object({
    username: z.string().min(3, '用户名 3-50 位').max(50, '用户名 3-50 位'),
    password: z.string().min(6, '密码 6-50 位').max(50, '密码 6-50 位'),
  }),
)
const emailRegisterSchema = toTypedSchema(
  z.object({
    username: z.string().min(3, '用户名 3-50 位').max(50, '用户名 3-50 位'),
    password: z.string().min(6, '密码 6-50 位').max(50, '密码 6-50 位'),
    email: z.string().email('邮箱格式不正确'),
    code: z.string().regex(/^\d{6}$/, '验证码为 6 位数字'),
  }),
)

async function onLogin(values: any): Promise<void> {
  try {
    await auth.login(values.username, values.password)
    toast.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || { name: 'dashboard' })
  }
  catch {
    // 错误提示已由 axios 拦截器统一弹出
  }
}

async function onRegister(values: any): Promise<void> {
  try {
    await registerApi({
      username: values.username,
      password: values.password,
    })
    toast.success('注册成功,请登录')
    loginInitial.value = { username: values.username, password: '' }
    tab.value = 'login'
  }
  catch {
    // 同上
  }
}

function startCountdown(): void {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function onSendCode(email: string): Promise<void> {
  if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    toast.error('请先填写正确的邮箱')
    return
  }
  sending.value = true
  try {
    await sendEmailCode({ email })
    toast.success('验证码已发送,请查收邮箱')
    startCountdown()
  }
  catch {
    // 同上
  }
  finally {
    sending.value = false
  }
}

async function onEmailRegister(values: any): Promise<void> {
  try {
    await emailRegister({
      username: values.username,
      password: values.password,
      email: values.email,
      code: values.code,
    })
    toast.success('注册成功,请登录')
    loginInitial.value = { username: values.username, password: '' }
    tab.value = 'login'
  }
  catch {
    // 同上
  }
}
</script>

<template>
  <div
    class="app-auth-bg flex min-h-screen items-center justify-center p-6"
  >
    <Card class="w-full max-w-[420px] shadow-lg">
      <CardHeader>
        <div class="mb-1 flex items-center gap-3">
          <span
            class="bg-primary text-primary-foreground flex size-11 items-center justify-center rounded-2xl text-base font-bold"
          >AI</span>
          <div>
            <CardTitle class="text-lg">
              {{ pub.siteName }}
            </CardTitle>
            <CardDescription>{{ pub.siteSubtitle }}</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div
          v-if="pub.loginAnnouncement"
          class="bg-muted text-muted-foreground mb-5 rounded-lg border px-3.5 py-2.5 text-sm whitespace-pre-line"
        >
          {{ pub.loginAnnouncement }}
        </div>
        <Tabs v-model="tab">
          <TabsList v-if="showRegister" class="mb-5 grid w-full grid-cols-2">
            <TabsTrigger value="login">
              登录
            </TabsTrigger>
            <TabsTrigger value="register">
              注册
            </TabsTrigger>
          </TabsList>

          <TabsContent value="login">
            <Form
              v-slot="{ isSubmitting }"
              :initial-values="loginInitial"
              :validation-schema="loginSchema"
              class="space-y-4"
              @submit="onLogin"
            >
              <FormField v-slot="{ componentField }" name="username">
                <FormItem>
                  <FormLabel>用户名</FormLabel>
                  <FormControl>
                    <Input v-bind="componentField" placeholder="请输入用户名" autocomplete="username" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              </FormField>
              <FormField v-slot="{ componentField }" name="password">
                <FormItem>
                  <FormLabel>密码</FormLabel>
                  <FormControl>
                    <Input
                      v-bind="componentField"
                      type="password"
                      placeholder="请输入密码"
                      autocomplete="current-password"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              </FormField>
              <Button type="submit" class="w-full" :disabled="isSubmitting">
                <Spinner v-if="isSubmitting" />
                登录
              </Button>
            </Form>
          </TabsContent>

          <TabsContent v-if="showRegister" value="register">
            <Tabs v-model="registerMethod">
              <TabsList v-if="bothRegister" class="mb-4 grid w-full grid-cols-2">
                <TabsTrigger value="password">
                  账号密码
                </TabsTrigger>
                <TabsTrigger value="email">
                  邮箱验证码
                </TabsTrigger>
              </TabsList>

              <TabsContent v-if="pub.passwordRegisterEnabled" value="password">
                <Form
                  v-slot="{ isSubmitting }"
                  :validation-schema="registerSchema"
                  class="space-y-4"
                  @submit="onRegister"
                >
                  <FormField v-slot="{ componentField }" name="username">
                    <FormItem>
                      <FormLabel>用户名</FormLabel>
                      <FormControl>
                        <Input v-bind="componentField" placeholder="3-50 位" autocomplete="username" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                  <FormField v-slot="{ componentField }" name="password">
                    <FormItem>
                      <FormLabel>密码</FormLabel>
                      <FormControl>
                        <Input
                          v-bind="componentField"
                          type="password"
                          placeholder="6-50 位"
                          autocomplete="new-password"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                  <Button type="submit" class="w-full" :disabled="isSubmitting">
                    <Spinner v-if="isSubmitting" />
                    注册
                  </Button>
                </Form>
              </TabsContent>

              <TabsContent v-if="pub.emailRegisterEnabled" value="email">
                <Form
                  v-slot="{ isSubmitting, values }"
                  :validation-schema="emailRegisterSchema"
                  class="space-y-4"
                  @submit="onEmailRegister"
                >
                  <FormField v-slot="{ componentField }" name="username">
                    <FormItem>
                      <FormLabel>用户名</FormLabel>
                      <FormControl>
                        <Input v-bind="componentField" placeholder="3-50 位" autocomplete="username" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                  <FormField v-slot="{ componentField }" name="password">
                    <FormItem>
                      <FormLabel>密码</FormLabel>
                      <FormControl>
                        <Input
                          v-bind="componentField"
                          type="password"
                          placeholder="6-50 位"
                          autocomplete="new-password"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                  <FormField v-slot="{ componentField }" name="email">
                    <FormItem>
                      <FormLabel>邮箱</FormLabel>
                      <FormControl>
                        <Input v-bind="componentField" type="email" placeholder="用于接收验证码" autocomplete="email" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                  <FormField v-slot="{ componentField }" name="code">
                    <FormItem>
                      <FormLabel>验证码</FormLabel>
                      <div class="flex gap-2">
                        <FormControl>
                          <Input v-bind="componentField" placeholder="6 位数字" inputmode="numeric" autocomplete="one-time-code" />
                        </FormControl>
                        <Button
                          type="button"
                          variant="outline"
                          class="shrink-0"
                          :disabled="countdown > 0 || sending"
                          @click="onSendCode(values.email)"
                        >
                          <Spinner v-if="sending" />
                          {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
                        </Button>
                      </div>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                  <Button type="submit" class="w-full" :disabled="isSubmitting">
                    <Spinner v-if="isSubmitting" />
                    注册
                  </Button>
                </Form>
              </TabsContent>
            </Tabs>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  </div>
</template>
