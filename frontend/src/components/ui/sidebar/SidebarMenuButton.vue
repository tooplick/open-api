<script setup lang="ts">
import type { PrimitiveProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { Primitive } from 'reka-ui'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { useSidebar } from './utils'

const props = withDefaults(
  defineProps<PrimitiveProps & {
    class?: HTMLAttributes['class']
    isActive?: boolean
    tooltip?: string
  }>(),
  { as: 'button' },
)

const { isMobile, state } = useSidebar()

const buttonClass
  = 'peer/menu-button flex w-full items-center gap-2 overflow-hidden rounded-md p-2 text-left text-sm outline-hidden ring-sidebar-ring transition-[width,height,padding] hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 active:bg-sidebar-accent active:text-sidebar-accent-foreground disabled:pointer-events-none disabled:opacity-50 group-has-[[data-sidebar=menu-action]]/menu-item:pr-8 aria-disabled:pointer-events-none aria-disabled:opacity-50 data-[active=true]:bg-sidebar-primary data-[active=true]:font-medium data-[active=true]:text-sidebar-primary-foreground group-data-[collapsible=icon]:size-8! group-data-[collapsible=icon]:p-2! [&>span:last-child]:truncate [&>svg]:size-4 [&>svg]:shrink-0'
</script>

<template>
  <Tooltip v-if="tooltip">
    <TooltipTrigger as-child>
      <Primitive
        data-slot="sidebar-menu-button"
        data-sidebar="menu-button"
        :data-active="isActive"
        :as="as"
        :as-child="asChild"
        :class="cn(buttonClass, props.class)"
      >
        <slot />
      </Primitive>
    </TooltipTrigger>
    <TooltipContent
      v-if="state === 'collapsed' && !isMobile"
      side="right"
      align="center"
    >
      {{ tooltip }}
    </TooltipContent>
  </Tooltip>

  <Primitive
    v-else
    data-slot="sidebar-menu-button"
    data-sidebar="menu-button"
    :data-active="isActive"
    :as="as"
    :as-child="asChild"
    :class="cn(buttonClass, props.class)"
  >
    <slot />
  </Primitive>
</template>
