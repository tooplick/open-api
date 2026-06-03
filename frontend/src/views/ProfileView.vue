<script setup lang="ts">
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { useAuthStore } from '@/stores/auth'
import { changePassword } from '@/api/user'
import { formatDateTime } from '@/utils/format'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'

const auth = useAuthStore()

const formSchema = toTypedSchema(
  z
    .object({
      oldPassword: z.string().min(1, '请填写原密码'),
      newPassword: z.string().min(6, '新密码至少 6 位').max(50, '新密码最多 50 位'),
      confirm: z.string().min(1, '请再次输入新密码'),
    })
    .refine((d) => d.newPassword === d.confirm, {
      message: '两次输入的新密码不一致',
      path: ['confirm'],
    }),
)

const { handleSubmit, isSubmitting, resetForm } = useForm({ validationSchema: formSchema })

const onSubmit = handleSubmit(async (values) => {
  try {
    await changePassword({ oldPassword: values.oldPassword, newPassword: values.newPassword })
    toast.success('密码修改成功')
    resetForm()
  } catch {
    // 拦截器已提示
  }
})
</script>

<template>
  <div class="grid max-w-[860px] grid-cols-2 items-start gap-6 max-md:grid-cols-1">
    <Card>
      <CardHeader>
        <CardTitle>账户信息</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="divide-y divide-border">
          <div class="flex items-center justify-between py-3.5">
            <span class="text-muted-foreground">用户名</span>
            <span>{{ auth.user?.username }}</span>
          </div>
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
          <div class="flex items-center justify-between py-3.5">
            <span class="text-muted-foreground">注册时间</span>
            <span>{{ formatDateTime(auth.user?.createTime) }}</span>
          </div>
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <CardTitle>修改密码</CardTitle>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit="onSubmit">
          <FormField v-slot="{ componentField }" name="oldPassword">
            <FormItem>
              <FormLabel>原密码</FormLabel>
              <FormControl>
                <Input type="password" autocomplete="current-password" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField }" name="newPassword">
            <FormItem>
              <FormLabel>新密码</FormLabel>
              <FormControl>
                <Input
                  type="password"
                  placeholder="6-50 位"
                  autocomplete="new-password"
                  v-bind="componentField"
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField }" name="confirm">
            <FormItem>
              <FormLabel>确认新密码</FormLabel>
              <FormControl>
                <Input type="password" autocomplete="new-password" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
          <Button type="submit" :disabled="isSubmitting">
            <Spinner v-if="isSubmitting" />
            保存
          </Button>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
