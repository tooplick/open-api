import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let seq = 0

function remove(id: number): void {
  const idx = state.items.findIndex((t) => t.id === id)
  if (idx !== -1) state.items.splice(idx, 1)
}

function push(type: ToastType, message: string, duration: number): void {
  const id = ++seq
  state.items.push({ id, type, message })
  window.setTimeout(() => remove(id), duration)
}

/** 全局单例:任意模块(含 axios 拦截器)可直接调用 */
export const toast = {
  success: (m: string) => push('success', m, 3000),
  error: (m: string) => push('error', m, 4000),
  info: (m: string) => push('info', m, 3000),
}

/** 供 ToastHost 组件渲染 */
export function useToasts() {
  return { items: state.items, remove }
}
