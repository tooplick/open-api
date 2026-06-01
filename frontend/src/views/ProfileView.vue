<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { changePassword } from '@/api/user'
import { toast } from '@/composables/useToast'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const loading = ref(false)

async function submit(): Promise<void> {
  if (!form.oldPassword || !form.newPassword) {
    toast.error('请填写原密码与新密码')
    return
  }
  if (form.newPassword.length < 6) {
    toast.error('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirm) {
    toast.error('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    toast.success('密码修改成功')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirm = ''
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="profile">
    <div class="card card-pad">
      <h3 class="block-title">账户信息</h3>
      <div class="kv">
        <span class="muted">用户名</span><span>{{ auth.user?.username }}</span>
      </div>
      <div class="kv">
        <span class="muted">角色</span>
        <span :class="['badge', auth.isAdmin ? 'badge-indigo' : 'badge-gray']">
          {{ auth.isAdmin ? '管理员' : '普通用户' }}
        </span>
      </div>
      <div class="kv">
        <span class="muted">邮箱</span><span>{{ auth.user?.email || '—' }}</span>
      </div>
      <div class="kv">
        <span class="muted">注册时间</span><span>{{ formatDateTime(auth.user?.createTime) }}</span>
      </div>
    </div>

    <div class="card card-pad">
      <h3 class="block-title">修改密码</h3>
      <form @submit.prevent="submit">
        <div class="field">
          <label class="field-label">原密码</label>
          <input v-model="form.oldPassword" class="input" type="password" autocomplete="current-password" />
        </div>
        <div class="field">
          <label class="field-label">新密码</label>
          <input v-model="form.newPassword" class="input" type="password" placeholder="6-50 位" autocomplete="new-password" />
        </div>
        <div class="field">
          <label class="field-label">确认新密码</label>
          <input v-model="form.confirm" class="input" type="password" autocomplete="new-password" />
        </div>
        <button class="btn btn-primary" type="submit" :disabled="loading">
          <span v-if="loading" class="spinner" />
          <span v-else>保存</span>
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.profile {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
  max-width: 860px;
}
.block-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 18px;
}
.kv {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}
.kv:last-child {
  border-bottom: none;
}
@media (max-width: 820px) {
  .profile {
    grid-template-columns: 1fr;
  }
}
</style>
