<script setup lang="ts">
import type { DialogRootEmits, DialogRootProps } from 'reka-ui'
import { VisuallyHidden, useForwardPropsEmits } from 'reka-ui'
import { reactiveOmit } from '@vueuse/core'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog'
import Command from './Command.vue'

const props = withDefaults(
  defineProps<DialogRootProps & { title?: string, description?: string }>(),
  { title: '命令面板', description: '搜索并快速跳转到任意页面' },
)
const emits = defineEmits<DialogRootEmits>()

const delegatedProps = reactiveOmit(props, 'title', 'description')
const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <Dialog v-bind="forwarded">
    <DialogContent class="overflow-hidden p-0 sm:max-w-[480px]" :show-close-button="false">
      <VisuallyHidden>
        <DialogTitle>{{ title }}</DialogTitle>
        <DialogDescription>{{ description }}</DialogDescription>
      </VisuallyHidden>
      <Command
        class="[&_[data-slot=command-group]]:px-2 [&_[data-slot=command-input-wrapper]_svg]:size-4 [&_[data-slot=command-item]]:px-2 [&_[data-slot=command-item]]:py-2"
      >
        <slot />
      </Command>
    </DialogContent>
  </Dialog>
</template>
