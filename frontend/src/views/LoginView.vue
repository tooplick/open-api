<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { register as registerApi } from '@/api/auth'
import { toast } from '@/composables/useToast'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', email: '' })

async function doLogin(): Promise<void> {
  if (!loginForm.username || !loginForm.password) {
    toast.error('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    toast.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || { name: 'dashboard' })
  } catch {
    // 错误提示已由 axios 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

async function doRegister(): Promise<void> {
  if (!regForm.username || !regForm.password) {
    toast.error('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await registerApi({
      username: regForm.username,
      password: regForm.password,
      email: regForm.email || undefined,
    })
    toast.success('注册成功,请登录')
    loginForm.username = regForm.username
    loginForm.password = ''
    regForm.password = ''
    mode.value = 'login'
  } catch {
    // 同上
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="auth-card card">
      <div class="auth-brand">
        <span class="auth-mark">AI</span>
        <div>
          <div class="auth-name">AI Open Platform</div>
          <div class="auth-sub muted">大模型聚合开放平台 · 控制台</div>
        </div>
      </div>

      <div class="auth-tabs">
        <button :class="['auth-tab', { active: mode === 'login' }]" type="button" @click="mode = 'login'">
          登录
        </button>
        <button
          :class="['auth-tab', { active: mode === 'register' }]"
          type="button"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <form v-if="mode === 'login'" @submit.prevent="doLogin">
        <div class="field">
          <label class="field-label">用户名</label>
          <input v-model.trim="loginForm.username" class="input" placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="field">
          <label class="field-label">密码</label>
          <input
            v-model="loginForm.password"
            class="input"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>
        <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
          <span v-if="loading" class="spinner" />
          <span v-else>登录</span>
        </button>
      </form>

      <form v-else @submit.prevent="doRegister">
        <div class="field">
          <label class="field-label">用户名<span class="req">*</span></label>
          <input v-model.trim="regForm.username" class="input" placeholder="3-50 位" autocomplete="username" />
        </div>
        <div class="field">
          <label class="field-label">密码<span class="req">*</span></label>
          <input
            v-model="regForm.password"
            class="input"
            type="password"
            placeholder="6-50 位"
            autocomplete="new-password"
          />
        </div>
        <div class="field">
          <label class="field-label">邮箱</label>
          <input v-model.trim="regForm.email" class="input" type="email" placeholder="可选" autocomplete="email" />
        </div>
        <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
          <span v-if="loading" class="spinner" />
          <span v-else>注册</span>
        </button>
      </form>

      <p class="auth-tip muted">默认管理员:admin / admin(首次启动自动创建)</p>
    </div>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(160deg, #fbf9f8 0%, #eae7e7 55%, #e1e2e8 100%);
}

.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 32px;
  box-shadow: var(--shadow-lg);
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.auth-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius);
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
}

.auth-name {
  font-size: 18px;
  font-weight: 600;
}

.auth-sub {
  font-size: 12px;
}

.auth-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}

.auth-tab {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--color-text-soft);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.auth-tab.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 500;
}

.auth-tip {
  margin-top: 18px;
  font-size: 12px;
  text-align: center;
}
</style>
