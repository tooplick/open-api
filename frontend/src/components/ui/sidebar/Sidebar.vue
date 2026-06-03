<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { cn } from '@/lib/utils'
import { SIDEBAR_WIDTH_MOBILE, useSidebar } from './utils'

const props = withDefaults(
  defineProps<{
    side?: 'left' | 'right'
    collapsible?: 'offcanvas' | 'icon' | 'none'
    class?: HTMLAttributes['class']
  }>(),
  { side: 'left', collapsible: 'icon' },
)

const { isMobile, state, openMobile, setOpenMobile } = useSidebar()

const mobileStyle: Record<string, string> = { '--sidebar-width': SIDEBAR_WIDTH_MOBILE }
</script>

<template>
  <div
    v-if="collapsible === 'none'"
    data-slot="sidebar"
    :class="cn('bg-sidebar text-sidebar-foreground flex h-full w-(--sidebar-width) flex-col', props.class)"
  >
    <slot />
  </div>

  <Sheet v-else-if="isMobile" :open="openMobile" @update:open="setOpenMobile">
    <SheetContent
      data-slot="sidebar"
      data-mobile="true"
      side="left"
      :style="mobileStyle"
      class="bg-sidebar text-sidebar-foreground w-(--sidebar-width) p-0 [&>button]:hidden"
    >
      <SheetHeader class="sr-only">
        <SheetTitle>Sidebar</SheetTitle>
        <SheetDescription>导航菜单</SheetDescription>
      </SheetHeader>
      <div class="flex h-full w-full flex-col">
        <slot />
      </div>
    </SheetContent>
  </Sheet>

  <div
    v-else
    class="group peer text-sidebar-foreground hidden md:block"
    :data-state="state"
    :data-collapsible="state === 'collapsed' ? collapsible : ''"
    :data-side="side"
    data-slot="sidebar"
  >
    <div
      :class="cn(
        'relative w-(--sidebar-width) bg-transparent transition-[width] duration-200 ease-linear',
        'group-data-[collapsible=offcanvas]:w-0',
        'group-data-[collapsible=icon]:w-(--sidebar-width-icon)',
      )"
    />
    <div
      :class="cn(
        'fixed inset-y-0 z-10 hidden h-svh w-(--sidebar-width) transition-[left,right,width] duration-200 ease-linear md:flex',
        side === 'left'
          ? 'left-0 group-data-[collapsible=offcanvas]:left-[calc(var(--sidebar-width)*-1)]'
          : 'right-0 group-data-[collapsible=offcanvas]:right-[calc(var(--sidebar-width)*-1)]',
        'group-data-[collapsible=icon]:w-(--sidebar-width-icon)',
        side === 'left' ? 'border-r' : 'border-l',
        'border-sidebar-border',
        props.class,
      )"
    >
      <div
        data-sidebar="sidebar"
        class="bg-sidebar flex h-full w-full flex-col"
      >
        <slot />
      </div>
    </div>
  </div>
</template>
