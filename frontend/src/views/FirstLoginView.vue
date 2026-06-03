<script setup lang="ts">
import { useRouter } from 'vue-router'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { ShieldAlertIcon } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'

const auth = useAuthStore()
const router = useRouter()

const schema = toTypedSchema(
  z.object({
    username: z.string().min(3, '用户名 3-50 位').max(50, '用户名 3-50 位'),
    newPassword: z.string().min(6, '密码 6-50 位').max(50, '密码 6-50 位'),
    confirm: z.string().min(1, '请再次输入密码'),
  }).refine(v => v.newPassword === v.confirm, {
    message: '两次输入的密码不一致',
    path: ['confirm'],
  }),
)

const initial = { username: auth.user?.username ?? '', newPassword: '', confirm: '' }

async function onSubmit(values: any): Promise<void> {
  try {
    await auth.completeFirstLogin(values.username, values.newPassword)
    toast.success('已更新账号与密码')
    await router.replace({ name: 'dashboard' })
  }
  catch {
    // 拦截器已提示
  }
}

function logout(): void {
  auth.clear()
  void router.replace({ name: 'login' })
}
</script>

<template>
  <div
    class="app-auth-bg flex min-h-screen items-center justify-center p-6"
  >
    <Card class="w-full max-w-[440px] shadow-lg">
      <CardHeader>
        <div class="mb-1 flex items-center gap-3">
          <span class="bg-warning-soft text-warning flex size-11 items-center justify-center rounded-2xl">
            <ShieldAlertIcon class="size-5" />
          </span>
          <div>
            <CardTitle class="text-lg">
              首次登录
            </CardTitle>
            <CardDescription>为安全起见,请修改默认账号与密码</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <Form
          v-slot="{ isSubmitting }"
          :initial-values="initial"
          :validation-schema="schema"
          class="space-y-4"
          @submit="onSubmit"
        >
          <FormField v-slot="{ componentField }" name="username">
            <FormItem>
              <FormLabel>新用户名</FormLabel>
              <FormControl>
                <Input v-bind="componentField" placeholder="3-50 位" autocomplete="username" />
              </FormControl>
              <FormDescription>替换默认的 admin 账号名</FormDescription>
              <FormMessage />
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField }" name="newPassword">
            <FormItem>
              <FormLabel>新密码</FormLabel>
              <FormControl>
                <Input v-bind="componentField" type="password" placeholder="6-50 位" autocomplete="new-password" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField }" name="confirm">
            <FormItem>
              <FormLabel>确认新密码</FormLabel>
              <FormControl>
                <Input v-bind="componentField" type="password" placeholder="再次输入新密码" autocomplete="new-password" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
          <Button type="submit" class="w-full" :disabled="isSubmitting">
            <Spinner v-if="isSubmitting" />
            保存并进入
          </Button>
        </Form>

        <Button variant="ghost" size="sm" class="text-muted-foreground mt-3 w-full" @click="logout">
          退出登录
        </Button>
      </CardContent>
    </Card>
  </div>
</template>
