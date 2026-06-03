<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { PanelLeftIcon } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useSidebar } from './utils'

const props = defineProps<{ class?: HTMLAttributes['class'] }>()
const { toggleSidebar } = useSidebar()
</script>

<template>
  <Button
    data-slot="sidebar-trigger"
    data-sidebar="trigger"
    variant="ghost"
    size="icon"
    :class="cn('size-8', props.class)"
    @click="toggleSidebar"
  >
    <PanelLeftIcon />
    <span class="sr-only">Toggle Sidebar</span>
  </Button>
</template>
