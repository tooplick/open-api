import { reactive } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

interface ConfirmState extends Required<ConfirmOptions> {
  open: boolean
  resolve: ((ok: boolean) => void) | null
}

const state = reactive<ConfirmState>({
  open: false,
  title: '请确认',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
  resolve: null,
})

/** 弹出确认框,返回 Promise<boolean>。任意模块可直接调用。 */
export function confirmDialog(opts: ConfirmOptions): Promise<boolean> {
  state.title = opts.title ?? '请确认'
  state.message = opts.message
  state.confirmText = opts.confirmText ?? '确定'
  state.cancelText = opts.cancelText ?? '取消'
  state.danger = opts.danger ?? false
  state.open = true
  return new Promise<boolean>((resolve) => {
    state.resolve = resolve
  })
}

function settle(ok: boolean): void {
  state.open = false
  state.resolve?.(ok)
  state.resolve = null
}

/** 供 ConfirmHost 组件使用 */
export function useConfirmState() {
  return {
    state,
    accept: () => settle(true),
    cancel: () => settle(false),
  }
}
