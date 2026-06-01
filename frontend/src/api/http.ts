import axios from 'axios'
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { Result } from '@/types'
import { TOKEN_KEY } from '@/utils/constants'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/composables/useToast'

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

const http = axios.create({
  baseURL: '/',
  timeout: 60000,
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function forceLogout(): void {
  useAuthStore().clear()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
}

http.interceptors.response.use(
  (response: AxiosResponse<Result<unknown>>) => {
    const body = response.data
    // 控制台接口统一 Result 包裹;业务失败时 HTTP 仍可能是 200,需看 body.code
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
      if (body.code === 401) forceLogout()
      toast.error(body.message || '请求失败')
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return response
  },
  (error: AxiosError<Result<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data
    const message =
      body && typeof body === 'object' && 'message' in body && body.message
        ? String(body.message)
        : error.message || '网络错误,请稍后重试'
    if (status === 401) forceLogout()
    toast.error(message)
    return Promise.reject(new ApiError(status ?? -1, message))
  },
)

// 仅作为 axios params 透传,不在内部索引,用 object 以兼容具名接口类型
type Params = object | undefined

export async function get<T>(url: string, params?: Params): Promise<T> {
  const resp = await http.get<Result<T>>(url, { params })
  return resp.data.data
}

export async function post<T>(url: string, body?: unknown, params?: Params): Promise<T> {
  const resp = await http.post<Result<T>>(url, body, { params })
  return resp.data.data
}

export async function put<T>(url: string, body?: unknown, params?: Params): Promise<T> {
  const resp = await http.put<Result<T>>(url, body, { params })
  return resp.data.data
}

export async function del<T>(url: string, params?: Params): Promise<T> {
  const resp = await http.delete<Result<T>>(url, { params })
  return resp.data.data
}

export default http
