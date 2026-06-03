<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed, provide, ref, watch } from 'vue'
import { useEventListener, useMediaQuery } from '@vueuse/core'
import { TooltipProvider } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import {
  SIDEBAR_STORAGE_KEY,
  SIDEBAR_INJECTION_KEY,
  SIDEBAR_KEYBOARD_SHORTCUT,
  SIDEBAR_WIDTH,
  SIDEBAR_WIDTH_ICON,
} from './utils'

const props = withDefaults(
  defineProps<{
    defaultOpen?: boolean
    open?: boolean
    class?: HTMLAttributes['class']
  }>(),
  { defaultOpen: true },
)

const emits = defineEmits<{ 'update:open': [value: boolean] }>()

const isMobile = useMediaQuery('(max-width: 767px)')
const openMobile = ref(false)

function readStored(): boolean {
  const v = localStorage.getItem(SIDEBAR_STORAGE_KEY)
  return v === null ? props.defaultOpen : v === 'true'
}

const open = ref(typeof props.open === 'boolean' ? props.open : readStored())

function setOpen(value: boolean): void {
  open.value = value
  emits('update:open', value)
  localStorage.setItem(SIDEBAR_STORAGE_KEY, String(value))
}

watch(
  () => props.open,
  (v) => {
    if (typeof v === 'boolean') open.value = v
  },
)

function setOpenMobile(value: boolean): void {
  openMobile.value = value
}

function toggleSidebar(): void {
  return isMobile.value ? setOpenMobile(!openMobile.value) : setOpen(!open.value)
}

useEventListener('keydown', (e: KeyboardEvent) => {
  if (e.key === SIDEBAR_KEYBOARD_SHORTCUT && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    toggleSidebar()
  }
})

const state = computed(() => (open.value ? 'expanded' : 'collapsed'))

const wrapperStyle: Record<string, string> = {
  '--sidebar-width': SIDEBAR_WIDTH,
  '--sidebar-width-icon': SIDEBAR_WIDTH_ICON,
}

provide(SIDEBAR_INJECTION_KEY, {
  state,
  open,
  setOpen,
  isMobile,
  openMobile,
  setOpenMobile,
  toggleSidebar,
})
</script>

<template>
  <TooltipProvider :delay-duration="0">
    <div
      data-slot="sidebar-wrapper"
      :style="wrapperStyle"
      :class="cn('group/sidebar-wrapper flex min-h-screen w-full', props.class)"
    >
      <slot />
    </div>
  </TooltipProvider>
</template>
