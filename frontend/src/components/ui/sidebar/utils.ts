import type { ComputedRef, InjectionKey, Ref } from 'vue'
import { inject } from 'vue'

export const SIDEBAR_STORAGE_KEY = 'sidebar_state'
export const SIDEBAR_WIDTH = '16rem'
export const SIDEBAR_WIDTH_MOBILE = '18rem'
export const SIDEBAR_WIDTH_ICON = '3.25rem'
export const SIDEBAR_KEYBOARD_SHORTCUT = 'b'

export interface SidebarContext {
  state: ComputedRef<'expanded' | 'collapsed'>
  open: Ref<boolean>
  setOpen: (value: boolean) => void
  isMobile: Ref<boolean>
  openMobile: Ref<boolean>
  setOpenMobile: (value: boolean) => void
  toggleSidebar: () => void
}

export const SIDEBAR_INJECTION_KEY = Symbol('sidebar') as InjectionKey<SidebarContext>

export function useSidebar(): SidebarContext {
  const context = inject(SIDEBAR_INJECTION_KEY)
  if (!context)
    throw new Error('useSidebar must be used within a SidebarProvider.')
  return context
}
