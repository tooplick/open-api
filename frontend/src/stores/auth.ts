import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { User } from '@/types'
import { TOKEN_KEY, USER_KEY } from '@/utils/constants'
import * as authApi from '@/api/auth'
import { getMe } from '@/api/user'

function readUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<User | null>(readUser())

  const isLoggedIn = computed(() => token.value.length > 0)
  const isAdmin = computed(() => user.value?.role === 'admin')

  function setSession(t: string, u: User): void {
    token.value = t
    user.value = u
    localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  function setUser(u: User): void {
    user.value = u
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  async function login(username: string, password: string): Promise<void> {
    const resp = await authApi.login({ username, password })
    setSession(resp.token, resp.user)
  }

  async function refreshMe(): Promise<void> {
    setUser(await getMe())
  }

  function clear(): void {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLoggedIn, isAdmin, login, refreshMe, setUser, clear }
})
